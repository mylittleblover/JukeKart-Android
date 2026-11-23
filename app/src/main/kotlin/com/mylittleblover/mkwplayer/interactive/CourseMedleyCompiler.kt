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

    sealed interface AssetSource {
        val path: String
        val sha256: ByteArray

        data class Bytes(
            override val path: String,
            override val sha256: ByteArray,
            val bytes: ByteArray,
        ) : AssetSource

        data class DiskFile(
            override val path: String,
            override val sha256: ByteArray,
            val file: File,
        ) : AssetSource
    }

    data class CompiledFileLinearGraph(
        val packet: ByteArray,
        val assets: List<AssetSource>,
        val totalFrames: Long,
    )

    fun compileGraph(
        medley: CourseMedleyCatalog.Medley,
        sampleRate: Int,
        bytesByStem: Map<String, ByteArray>,
        knownDigestsHex: Map<String, String> = emptyMap(),
    ): ExtraInteractiveCompilers.CompiledLinearGraph {
        require(medley.clips.isNotEmpty()) { "Medley '${medley.id}' has no clips" }

        val uniqueAssets = linkedMapOf<String, Pair<G.Asset, ByteArray>>()
        val assetIndexByStem = linkedMapOf<String, Int>()
        var md: MessageDigest? = null

        medley.uniqueStems.forEach { stem ->
            val bytes = bytesByStem[stem] ?: error("Missing stem '$stem' for medley '${medley.id}'")
            val info = when (val parsed = BwavHeader.read(bytes)) {
                is BwavHeader.Result.Ok -> parsed.info
                is BwavHeader.Result.Rejected -> error("Unreadable stem '$stem': ${parsed.reason}")
            }
            val shaBytes = knownDigestsHex[stem]?.let(::decodeHexSha256) ?: run {
                val digest = md ?: MessageDigest.getInstance("SHA-256").also { md = it }
                digest.digest(bytes)
            }
            val spec = buildAssetSpec(stem, info, shaBytes)
            assetIndexByStem[stem] = uniqueAssets.size
            uniqueAssets[stem] = spec to bytes
        }

        val assetSpecs = uniqueAssets.values.map { it.first }
        val (packet, totalFrames) = buildGraphPacket(medley, sampleRate, assetSpecs, assetIndexByStem)
        return ExtraInteractiveCompilers.CompiledLinearGraph(
            packet = packet,
            assets = uniqueAssets.values.map { it.first.path to it.second },
            totalFrames = totalFrames,
        )
    }

    fun compileGraphFromFiles(
        medley: CourseMedleyCatalog.Medley,
        sampleRate: Int,
        filesByStem: Map<String, File>,
        knownDigestsHex: Map<String, String> = emptyMap(),
        fallbackBytesByStem: Map<String, ByteArray> = emptyMap(),
    ): CompiledFileLinearGraph {
        require(medley.clips.isNotEmpty()) { "Medley '${medley.id}' has no clips" }

        val uniqueAssets = linkedMapOf<String, Pair<G.Asset, AssetSource>>()
        val assetIndexByStem = linkedMapOf<String, Int>()

        medley.uniqueStems.forEach { stem ->
            val file = filesByStem[stem]
            if (file != null) {
                val info = when (val parsed = BwavHeader.readFile(file)) {
                    is BwavHeader.Result.Ok -> parsed.info
                    is BwavHeader.Result.Rejected -> error("Unreadable stem '$stem': ${parsed.reason}")
                }
                val shaBytes = knownDigestsHex[stem]?.let(::decodeHexSha256) ?: sha256File(file)
                val spec = buildAssetSpec(stem, info, shaBytes)
                assetIndexByStem[stem] = uniqueAssets.size
                uniqueAssets[stem] = spec to AssetSource.DiskFile(spec.path, shaBytes, file)
            } else {
                val bytes = fallbackBytesByStem[stem]
                    ?: error("Missing stem '$stem' for medley '${medley.id}'")
                val info = when (val parsed = BwavHeader.read(bytes)) {
                    is BwavHeader.Result.Ok -> parsed.info
                    is BwavHeader.Result.Rejected -> error("Unreadable stem '$stem': ${parsed.reason}")
                }
                val shaBytes = knownDigestsHex[stem]?.let(::decodeHexSha256)
                    ?: MessageDigest.getInstance("SHA-256").digest(bytes)
                val spec = buildAssetSpec(stem, info, shaBytes)
                assetIndexByStem[stem] = uniqueAssets.size
                uniqueAssets[stem] = spec to AssetSource.Bytes(spec.path, shaBytes, bytes)
            }
        }

        val assetSpecs = uniqueAssets.values.map { it.first }
        val (packet, totalFrames) = buildGraphPacket(medley, sampleRate, assetSpecs, assetIndexByStem)
        return CompiledFileLinearGraph(
            packet = packet,
            assets = uniqueAssets.values.map { it.second },
            totalFrames = totalFrames,
        )
    }

    private fun buildAssetSpec(stem: String, info: BwavHeader.Info, shaBytes: ByteArray): G.Asset {
        val frames = info.frames.toLong()
        require(frames > 0L) { "Stem '$stem' has non-positive frame count $frames" }
        return G.Asset(
            path = "$stem.bwav",
            sampleRate = info.sampleRate,
            channels = if (info.channels > 2) 2 else info.channels.coerceAtLeast(1),
            frames = frames,
            sha256 = shaBytes,
            lookAheadFrames = info.sampleRate * 2L,
        )
    }

    private fun buildGraphPacket(
        medley: CourseMedleyCatalog.Medley,
        sampleRate: Int,
        assetSpecs: List<G.Asset>,
        assetIndexByStem: Map<String, Int>,
    ): Pair<ByteArray, Long> {
        var maxEnd = 0L
        val tracks = medley.clips.mapIndexed { idx, clip ->
            val assetIdx = assetIndexByStem.getValue(clip.stem)
            val assetFrames = assetSpecs[assetIdx].frames
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
        val spec = G.Spec(
            assets = assetSpecs,
            segments = listOf(segment),
            tracks = tracks,
            transitions = emptyList(),
            transportSampleRate = sampleRate,
            clipEnvelopes = clipEnvelopes,
        )
        return G.build(spec) to totalFrames
    }

    internal fun sha256File(file: File): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(16 * 1024)
        file.inputStream().use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest()
    }

    internal fun decodeHexSha256(hex: String): ByteArray? {
        if (hex.length != 64) return null
        val out = ByteArray(32)
        for (i in 0 until 32) {
            val hi = hexDigit(hex[i * 2])
            val lo = hexDigit(hex[i * 2 + 1])
            if (hi < 0 || lo < 0) return null
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    private fun hexDigit(c: Char): Int = when (c) {
        in '0'..'9' -> c.code - '0'.code
        in 'a'..'f' -> c.code - 'a'.code + 10
        in 'A'..'F' -> c.code - 'A'.code + 10
        else -> -1
    }
}
