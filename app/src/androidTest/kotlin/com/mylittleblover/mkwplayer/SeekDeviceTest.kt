package com.mylittleblover.mkwplayer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mylittleblover.mkwplayer.audio.AudioEngine
import com.mylittleblover.mkwplayer.audio.EngineSnapshot
import com.mylittleblover.mkwplayer.audio.SeekTarget
import com.mylittleblover.mkwplayer.graph.GraphBuilder
import com.mylittleblover.player2.platform.audio.NativeAudioLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Seek on the real engine, with the real track.
 *
 * The JVM tests prove the arithmetic and the state machine; this proves the native
 * call actually repositions the transport. Both matter: a correct fraction mapping
 * into a seek the engine ignores would look right in unit tests and do nothing on the
 * device.
 */
@RunWith(AndroidJUnit4::class)
class SeekDeviceTest {

    private val bwavFile = File("/data/local/tmp/coaster.bwav")
    private val assetId = "BGM_Stage_Coaster.bwav"

    private fun buildPacket(bytes: ByteArray, frames: Int): ByteArray =
        GraphBuilder.build(
            GraphBuilder.Spec(
                asset = GraphBuilder.Asset(
                    path = assetId,
                    sampleRate = 48000,
                    channels = 2,
                    frames = frames.toLong(),
                    sha256 = java.security.MessageDigest.getInstance("SHA-256").digest(bytes),
                    loop = GraphBuilder.Loop(493_714, 3_373_711),
                ),
            ),
        )

    @Test
    fun seekRepositionsTheTransport() {
        assertTrue("missing ${bwavFile.path}", bwavFile.exists())
        val bytes = bwavFile.readBytes()
        val frames = 3_373_711

        val engine = NativeAudioLibrary.createEngine()
        try {
            assertEquals(0, NativeAudioLibrary.loadCompiledGraph(engine, buildPacket(bytes, frames)))
            assertEquals(
                0,
                NativeAudioLibrary.bindSource(engine, 0, "native.bwav", bytes, 0, assetId, null, null, null),
            )
            assertEquals(0, NativeAudioLibrary.command(engine, 0, 1, -1, 0.0, -1L))

            // Render a little so the transport is genuinely moving first.
            val before = FloatArray(512 * 2)
            assertEquals(0, NativeAudioLibrary.renderRealtime(engine, before, 512))

            val target = SeekTarget.fractionToFrame(0.5f, frames.toLong())
            assertEquals("seek must return Ok(0)", 0, NativeAudioLibrary.seek(engine, target))

            val fields = EngineSnapshot.read(NativeAudioLibrary.snapshot(engine))
            assertTrue("snapshot unreadable after seek", fields != null)
            assertEquals(
                "transport did not move to the seek target",
                target,
                fields!!.transportFrame,
            )
        } finally {
            NativeAudioLibrary.destroyEngine(engine)
        }
    }

    @Test
    fun audioContinuesAfterSeek() {
        assertTrue("missing ${bwavFile.path}", bwavFile.exists())
        val bytes = bwavFile.readBytes()
        val frames = 3_373_711

        val engine = NativeAudioLibrary.createEngine()
        try {
            assertEquals(0, NativeAudioLibrary.loadCompiledGraph(engine, buildPacket(bytes, frames)))
            assertEquals(
                0,
                NativeAudioLibrary.bindSource(engine, 0, "native.bwav", bytes, 0, assetId, null, null, null),
            )
            assertEquals(0, NativeAudioLibrary.command(engine, 0, 1, -1, 0.0, -1L))

            val target = SeekTarget.fractionToFrame(0.25f, frames.toLong())
            assertEquals(0, NativeAudioLibrary.seek(engine, target))

            var peak = 0f
            var nonZero = 0
            repeat(4) {
                val buffer = FloatArray(512 * 2)
                assertEquals(0, NativeAudioLibrary.renderRealtime(engine, buffer, 512))
                for (sample in buffer) {
                    val magnitude = kotlin.math.abs(sample)
                    if (magnitude > peak) peak = magnitude
                    if (sample != 0f) nonZero++
                }
            }
            assertTrue(
                "playback went silent after a seek: peak=$peak nonzero=$nonZero",
                nonZero > 0,
            )
        } finally {
            NativeAudioLibrary.destroyEngine(engine)
        }
    }

    @Test
    fun seekBeforeLoadIsRejected() {
        // With no runtime, the engine must refuse rather than move a nonexistent clock.
        val engine = NativeAudioLibrary.createEngine()
        try {
            assertTrue(
                "seek on a bare engine must not report success",
                NativeAudioLibrary.seek(engine, 1000) != 0,
            )
        } finally {
            NativeAudioLibrary.destroyEngine(engine)
        }
    }

    private data class BwavInfo(
        val rate: Int,
        val channels: Int,
        val frames: Int,
        val sha256: ByteArray,
        val bytes: ByteArray,
    )

    private fun readBwavInfo(file: java.io.File): BwavInfo {
        assertTrue("missing ${file.path}", file.exists())
        val bytes = file.readBytes()
        fun u16(at: Int) = (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8)
        fun u32(at: Int) = (bytes[at].toInt() and 0xFF) or
            ((bytes[at + 1].toInt() and 0xFF) shl 8) or
            ((bytes[at + 2].toInt() and 0xFF) shl 16) or
            ((bytes[at + 3].toInt() and 0xFF) shl 24)
        val channels = u16(14)
        val rate = u32(20)
        val frames = u32(28)
        val sha = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
        return BwavInfo(rate, channels, frames, sha, bytes)
    }

    private fun verifyStaticGraphSeekParity(
        spec: com.mylittleblover.mkwplayer.graph.MusicGraphBuilder.Spec,
        assets: List<Pair<String, BwavInfo>>,
        seekTargets: List<Long>,
        preparation: Int,
        compareFrames: Int = 2048,
    ) {
        val packet = com.mylittleblover.mkwplayer.graph.MusicGraphBuilder.build(spec)
        fun createBoundEngine(): Long {
            val engine = NativeAudioLibrary.createEngine()
            assertTrue("createEngine failed", engine != 0L)
            assertEquals("loadCompiledGraph", 0, NativeAudioLibrary.loadCompiledGraph(engine, packet))
            assets.forEachIndexed { index, (assetId, info) ->
                val rc = NativeAudioLibrary.bindSource(
                    engine, index, "native.bwav", info.bytes, preparation,
                    assetId, null, null, null,
                )
                assertEquals("bindSource[$index] prep=$preparation", 0, rc)
            }
            assertEquals("play(100)", 0, NativeAudioLibrary.command(engine, 0, 100, 0, 0.0, 0L))
            return engine
        }

        val seekEngine = createBoundEngine()
        try {
            val warmup = FloatArray(512 * 2)
            assertEquals("warmup render", 0, NativeAudioLibrary.render(seekEngine, warmup, 512))
            for (target in seekTargets) {
                val t0 = System.nanoTime()
                assertEquals("seek($target) prep=$preparation", 0, NativeAudioLibrary.seek(seekEngine, target))
                val elapsedMs = (System.nanoTime() - t0) / 1_000_000.0
                val snap = NativeAudioLibrary.snapshot(seekEngine)
                assertEquals("position after seek($target)", target, snap[1])
                assertTrue("O(1) seek($target) prep=$preparation took ${elapsedMs}ms", elapsedMs < 250.0)

                val actual = FloatArray(compareFrames * 2)
                assertEquals("render after seek($target)", 0, NativeAudioLibrary.render(seekEngine, actual, compareFrames))

                val refEngine = createBoundEngine()
                try {
                    var cursor = 0L
                    val discard = FloatArray(512 * 2)
                    while (cursor < target) {
                        val step = minOf(512L, target - cursor).toInt()
                        assertEquals(0, NativeAudioLibrary.render(refEngine, discard, step))
                        cursor += step
                    }
                    val expected = FloatArray(compareFrames * 2)
                    assertEquals(0, NativeAudioLibrary.render(refEngine, expected, compareFrames))
                    var maxDiff = 0f
                    var nonZero = 0
                    for (i in actual.indices) {
                        val diff = kotlin.math.abs(actual[i] - expected[i])
                        if (diff > maxDiff) maxDiff = diff
                        if (expected[i] != 0f) nonZero++
                    }
                    assertTrue("reference silent at target=$target", nonZero > 0)
                    assertTrue(
                        "PCM mismatch at seek($target) prep=$preparation maxDiff=$maxDiff",
                        maxDiff <= 1e-5f,
                    )
                } finally {
                    NativeAudioLibrary.destroyEngine(refEngine)
                }
            }
        } finally {
            NativeAudioLibrary.destroyEngine(seekEngine)
        }
    }

    @Test
    fun staticOverlaidOpeningSeekMatchesSequentialRender() {
        val pre = readBwavInfo(java.io.File("/data/local/tmp/coaster-Pre.bwav"))
        val main = readBwavInfo(java.io.File("/data/local/tmp/coaster.bwav"))
        val mainLen = minOf(main.frames.toLong(), 360_000L)
        val overlapStart = (pre.frames.toLong() - 48_000L).coerceAtLeast(24_000L)
        val exitFrame = overlapStart + mainLen
        val spec = com.mylittleblover.mkwplayer.graph.MusicGraphBuilder.Spec(
            assets = listOf(
                com.mylittleblover.mkwplayer.graph.MusicGraphBuilder.Asset("pre.bwav", pre.rate, pre.channels, pre.frames.toLong(), pre.sha256),
                com.mylittleblover.mkwplayer.graph.MusicGraphBuilder.Asset("main.bwav", main.rate, main.channels, main.frames.toLong(), main.sha256),
            ),
            segments = listOf(com.mylittleblover.mkwplayer.graph.MusicGraphBuilder.Segment(index = 100, entry = 0L, exit = exitFrame)),
            tracks = listOf(
                com.mylittleblover.mkwplayer.graph.MusicGraphBuilder.Track(
                    index = 200,
                    segment = 100,
                    clips = listOf(
                        com.mylittleblover.mkwplayer.graph.MusicGraphBuilder.Clip(
                            index = 300, asset = 0, start = 0L,
                            sourceLength = pre.frames.toLong(), fadeOut = 24_000L, gainDb = -1.5,
                        ),
                        com.mylittleblover.mkwplayer.graph.MusicGraphBuilder.Clip(
                            index = 301, asset = 1, start = overlapStart,
                            sourceLength = mainLen, fadeIn = 24_000L, fadeOut = 48_000L,
                        ),
                    ),
                ),
            ),
        )
        val targets = listOf(
            overlapStart,               // c.start == target boundary
            overlapStart + 12_000L,     // inside overlap (fadeOut + fadeIn)
            pre.frames.toLong() + 24_000L, // clip 0 finished, inside clip 1 body
            exitFrame - 24_000L,        // inside clip 1 tail fadeOut
            overlapStart / 2L,          // backward seek inside clip 0
            0L,                         // backward seek to origin
        )
        for (prep in listOf(0, 1)) {
            verifyStaticGraphSeekParity(spec, listOf("pre.bwav" to pre, "main.bwav" to main), targets, prep)
        }
    }

    @Test
    fun staticChainedSequenceSeekMatchesSequentialRender() {
        val bridge = readBwavInfo(java.io.File("/data/local/tmp/coaster-Bridge.bwav"))
        val outro = readBwavInfo(java.io.File("/data/local/tmp/coaster-Outro00.bwav"))
        val c0Len = bridge.frames.toLong()
        val c1Len = outro.frames.toLong()
        val exitFrame = c0Len + c1Len
        val spec = com.mylittleblover.mkwplayer.graph.MusicGraphBuilder.Spec(
            assets = listOf(
                com.mylittleblover.mkwplayer.graph.MusicGraphBuilder.Asset("bridge.bwav", bridge.rate, bridge.channels, bridge.frames.toLong(), bridge.sha256),
                com.mylittleblover.mkwplayer.graph.MusicGraphBuilder.Asset("outro.bwav", outro.rate, outro.channels, outro.frames.toLong(), outro.sha256),
            ),
            segments = listOf(com.mylittleblover.mkwplayer.graph.MusicGraphBuilder.Segment(index = 100, entry = 0L, exit = exitFrame)),
            tracks = listOf(
                com.mylittleblover.mkwplayer.graph.MusicGraphBuilder.Track(
                    index = 200,
                    segment = 100,
                    clips = listOf(
                        com.mylittleblover.mkwplayer.graph.MusicGraphBuilder.Clip(index = 300, asset = 0, start = 0L, sourceLength = c0Len),
                        com.mylittleblover.mkwplayer.graph.MusicGraphBuilder.Clip(index = 301, asset = 1, start = c0Len, sourceLength = c1Len, fadeOut = 48_000L),
                    ),
                ),
            ),
        )
        val targets = listOf(
            c0Len,                  // exact clip-to-clip boundary
            c0Len + c1Len / 2L,     // mid second clip
            exitFrame - 24_000L,    // linear tail fadeOut
            c0Len / 2L,             // backward seek into clip 0
        )
        for (prep in listOf(0, 1)) {
            verifyStaticGraphSeekParity(spec, listOf("bridge.bwav" to bridge, "outro.bwav" to outro), targets, prep)
        }
    }
}
