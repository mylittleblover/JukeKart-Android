package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.audio.BwavHeader
import com.mylittleblover.mkwplayer.graph.MusicGraphBuilder as G
import java.security.MessageDigest

/**
 * Compiles a consecutive run of native-handover [CourseMedleyCompiler.ResolvedMedley]
 * entries into a single multi-segment graph packet (#51-#53).
 *
 * Each boundary `i -> i+1` transitions strictly on `Outro_ToNextPre` (`SyncPoint.ExitCue`,
 * `postExit = true`) into the next entry's verified `Pre` / `Pre_Short` / `Bridge` start
 * offset via `TransitionTimeline(0, -dest.incomingPrePhase, 0, 0)`, letting the outgoing
 * outro decay naturally without fabricated crossfades.
 */
internal object CourseMedleyChainCompiler {

    data class SegmentWindow(
        val segmentId: Int,
        val entryKey: String,
        val displayTotalFrames: Long,
        val handoverExitFrame: Long?,
        val originTransportFrame: Long,
        val incomingPrePhase: Long,
    )

    data class CompiledChain(
        val packet: ByteArray,
        val assets: List<Pair<String, ByteArray>>,
        val autoAdvance: Map<Int, Int>,
        val windows: List<SegmentWindow>,
        val firstTotalFrames: Long,
        val sampleRate: Int,
    )

    fun compileChain(
        entries: List<Pair<String, CourseMedleyCompiler.ResolvedMedley>>,
        bytesByStem: Map<String, ByteArray>,
    ): CompiledChain {
        require(entries.size >= 2) { "Native handover chain requires at least 2 entries" }
        val baseRate = entries.first().second.sampleRate
        val digest = MessageDigest.getInstance("SHA-256")
        val uniqueAssets = linkedMapOf<String, Pair<G.Asset, ByteArray>>()
        val assetIndexByStem = linkedMapOf<String, Int>()

        for ((_, resolved) in entries) {
            require(resolved.sampleRate == baseRate) {
                "All chained native entries must share sample rate $baseRate"
            }
            for (stem in resolved.medley.uniqueStems) {
                if (stem in assetIndexByStem) continue
                val bytes = bytesByStem[stem] ?: error("Missing stem '$stem' for chained medley '${resolved.medley.id}'")
                val header = (BwavHeader.read(bytes) as? BwavHeader.Result.Ok)?.info
                    ?: error("Unreadable BWAV '$stem'")
                val frames = header.frames.toLong()
                require(frames > 0L) { "Zero-frame BWAV '$stem'" }
                val specAsset = G.Asset(
                    path = "$stem.bwav",
                    sampleRate = header.sampleRate,
                    channels = header.channels.coerceIn(1, 2),
                    frames = frames,
                    sha256 = digest.digest(bytes),
                )
                assetIndexByStem[stem] = uniqueAssets.size
                uniqueAssets[stem] = specAsset to bytes
            }
        }

        val segments = mutableListOf<G.Segment>()
        val tracks = mutableListOf<G.Track>()
        val transitions = mutableListOf<G.Transition>()
        val autoAdvance = linkedMapOf<Int, Int>()
        val windows = mutableListOf<SegmentWindow>()

        var nextTrackId = 300
        var nextClipId = 500
        var nextTransitionId = 2000
        var transportCursor = 0L

        entries.forEachIndexed { idx, (entryKey, resolved) ->
            val segId = 100 + idx
            val isLast = idx == entries.lastIndex
            val exitFrame = if (isLast) {
                resolved.medley.totalFrames
            } else {
                val h = resolved.outroHandoverFrame
                    ?: error("Missing outroHandoverFrame for chained entry '${resolved.medley.id}'")
                require(h in 1L..resolved.medley.totalFrames) {
                    "Invalid handover frame $h outside 1..${resolved.medley.totalFrames}"
                }
                h
            }

            segments += G.Segment(
                index = segId,
                entry = 0L,
                exit = exitFrame,
            )

            resolved.medley.clips.forEach { clip ->
                val assetIdx = assetIndexByStem.getValue(clip.stem)
                val assetFrames = uniqueAssets.getValue(clip.stem).first.frames
                val offset = clip.sourceOffset.coerceIn(0L, assetFrames - 1L)
                val length = clip.sourceLength.coerceIn(1L, assetFrames - offset)
                val start = clip.start.coerceAtLeast(0L)
                val fadeIn = clip.fadeIn.coerceIn(0L, length)
                val fadeOut = clip.fadeOut.coerceIn(0L, length - fadeIn)
                tracks += G.Track(
                    index = nextTrackId++,
                    segment = segId,
                    clips = listOf(
                        G.Clip(
                            index = nextClipId++,
                            asset = assetIdx,
                            start = start,
                            sourceOffset = offset,
                            sourceLength = length,
                            fadeIn = fadeIn,
                            fadeOut = fadeOut,
                        ),
                    ),
                )
            }

            windows += SegmentWindow(
                segmentId = segId,
                entryKey = entryKey,
                displayTotalFrames = resolved.medley.totalFrames,
                handoverExitFrame = if (isLast) null else exitFrame,
                originTransportFrame = transportCursor,
                incomingPrePhase = resolved.incomingPrePhase,
            )

            if (!isLast) {
                val nextResolved = entries[idx + 1].second
                val nextSegId = segId + 1
                val destPhase = nextResolved.incomingPrePhase.coerceAtLeast(0L)
                transitions += G.Transition(
                    index = nextTransitionId++,
                    sources = listOf(segId),
                    destinations = listOf(nextSegId),
                    sync = G.SyncPoint.ExitCue,
                    sourceCue = null,
                    entry = G.EntryMode.EntryCue,
                    postExit = true,
                    timeline = G.TransitionTimeline(
                        bridgeStart = 0L,
                        destinationStart = -destPhase,
                        destinationCommit = 0L,
                        destinationSource = 0L,
                    ),
                )
                autoAdvance[segId] = nextSegId
                transportCursor = (transportCursor + exitFrame - destPhase).coerceAtLeast(transportCursor)
            }
        }

        val spec = G.Spec(
            assets = uniqueAssets.values.map { it.first },
            segments = segments,
            tracks = tracks,
            transitions = transitions,
            transportSampleRate = baseRate,
        )
        return CompiledChain(
            packet = G.build(spec),
            assets = uniqueAssets.values.map { (asset, bytes) -> asset.path to bytes },
            autoAdvance = autoAdvance,
            windows = windows,
            firstTotalFrames = entries.first().second.medley.totalFrames,
            sampleRate = baseRate,
        )
    }
}
