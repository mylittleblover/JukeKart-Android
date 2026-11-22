package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.audio.BwavHeader
import com.mylittleblover.mkwplayer.graph.MusicGraphBuilder as G
import com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog
import java.io.File
import java.security.MessageDigest

/** Compiles a [CourseMedleyCatalog.Medley] recipe into a unity-gain single-segment linear playback graph. */
internal object CourseMedleyCompiler {
    data class ResolvedMedley(
        val medley: CourseMedleyCatalog.Medley,
        val sampleRate: Int,
        val stemDigests: Map<String, String>,
        val outroHandoverFrame: Long? = null,
        val incomingPrePhase: Long = 0L,
    )

    fun resolveMedley(
        medley: CourseMedleyCatalog.Medley,
        sampleRate: Int,
        identities: InteractiveAudioIdentities,
        packDir: File,
        extraCatalog: ExtraMusicCatalog.Catalog? = null,
    ): ResolvedMedley? {
        if (medley.clips.isEmpty()) return null
        val digests = linkedMapOf<String, String>()
        for (stem in medley.uniqueStems) {
            if (stem.equals("BGM_Seq_FinalLapFF", ignoreCase = true)) {
                digests[stem] = FinalLapFanfareCompiler.SHA256
                continue
            }
            val sha = identities.digestForFileName("$stem.bwav")
                ?: extraCatalog?.digestForStem(stem)
                ?: return null
            if (!File(packDir, "audio/$sha").isFile) return null
            digests[stem] = sha
        }
        return ResolvedMedley(medley = medley, sampleRate = sampleRate, stemDigests = digests)
    }

    fun compileGraph(
        medley: CourseMedleyCatalog.Medley,
        sampleRate: Int,
        bytesByStem: Map<String, ByteArray>,
    ): ExtraInteractiveCompilers.CompiledLinearGraph {
        require(medley.clips.isNotEmpty()) { "Medley '${medley.id}' has no clips" }

        val uniqueAssets = linkedMapOf<String, Pair<G.Asset, ByteArray>>()
        val assetIndexByStem = linkedMapOf<String, Int>()
        val md = MessageDigest.getInstance("SHA-256")

        medley.uniqueStems.forEach { stem ->
            val bytes = bytesByStem[stem] ?: error("Missing stem '$stem' for medley '${medley.id}'")
            val info = when (val parsed = BwavHeader.read(bytes)) {
                is BwavHeader.Result.Ok -> parsed.info
                is BwavHeader.Result.Rejected -> error("Unreadable stem '$stem': ${parsed.reason}")
            }
            val frames = info.frames.toLong()
            require(frames > 0L) { "Stem '$stem' has non-positive frame count $frames" }
            val path = "$stem.bwav"
            val spec = G.Asset(
                path = path,
                sampleRate = info.sampleRate,
                channels = if (info.channels > 2) 2 else info.channels.coerceAtLeast(1),
                frames = frames,
                sha256 = md.digest(bytes),
            )
            assetIndexByStem[stem] = uniqueAssets.size
            uniqueAssets[stem] = spec to bytes
        }

        var maxEnd = 0L
        val tracks = medley.clips.mapIndexed { idx, clip ->
            val assetIdx = assetIndexByStem.getValue(clip.stem)
            val assetFrames = uniqueAssets.getValue(clip.stem).first.frames
            val offset = clip.sourceOffset.coerceIn(0L, assetFrames - 1L)
            val length = clip.sourceLength.coerceIn(1L, assetFrames - offset)
            val start = clip.start.coerceAtLeast(0L)
            val fadeIn = clip.fadeIn.coerceIn(0L, length)
            val fadeOut = clip.fadeOut.coerceIn(0L, length - fadeIn)
            maxEnd = maxOf(maxEnd, start + length)
            val gClip = G.Clip(
                index = 300 + idx,
                asset = assetIdx,
                start = start,
                sourceOffset = offset,
                sourceLength = length,
                gainDb = 0.0,
                fadeIn = fadeIn,
                fadeOut = fadeOut,
            )
            G.Track(index = 200 + idx, segment = 100, clips = listOf(gClip))
        }

        val clipEnvelopes = medley.clips.mapIndexedNotNull { idx, clip ->
            if (clip.volumePoints.isEmpty()) null
            else G.TimelineEnvelope(
                targetIndex = 300 + idx,
                target = G.EnvelopeTarget.VolumeDb,
                points = clip.volumePoints,
            )
        }

        val totalFrames = maxOf(medley.totalFrames, maxEnd)
        val segment = G.Segment(index = 100, entry = 0L, exit = totalFrames)
        val assetSpecs = uniqueAssets.values.map { it.first }
        val spec = G.Spec(
            assets = assetSpecs,
            segments = listOf(segment),
            tracks = tracks,
            transitions = emptyList(),
            transportSampleRate = sampleRate,
            clipEnvelopes = clipEnvelopes,
        )
        return ExtraInteractiveCompilers.CompiledLinearGraph(
            packet = G.build(spec),
            assets = uniqueAssets.values.map { it.first.path to it.second },
            totalFrames = totalFrames,
        )
    }
}
