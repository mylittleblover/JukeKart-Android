package com.mylittleblover.mkwplayer

import com.mylittleblover.mkwplayer.audio.AudioEngine
import com.mylittleblover.mkwplayer.audio.EngineSnapshot
import com.mylittleblover.mkwplayer.audio.SessionAudioEngine
import com.mylittleblover.mkwplayer.audio.SessionAudioOutput
import com.mylittleblover.mkwplayer.graph.GraphProbe
import com.mylittleblover.mkwplayer.graph.MusicGraphBuilder as G
import com.mylittleblover.mkwplayer.interactive.CourseMedleyCatalog
import com.mylittleblover.mkwplayer.interactive.CourseMedleyCompiler
import com.mylittleblover.mkwplayer.player.LinearPlaybackCoordinator
import com.mylittleblover.mkwplayer.player.LinearQueue
import com.mylittleblover.mkwplayer.player.LinearSessionStore
import com.mylittleblover.mkwplayer.player.PlaybackService
import com.mylittleblover.mkwplayer.player.PlaybackSession
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class Beta05AudioStabilityTest {

    private val rate = 48_000

    private fun syntheticBwav(frames: Int = 480_000, sampleRate: Int = 48_000, channels: Int = 2): ByteArray {
        val bytes = ByteArray(16 + channels * 76)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        bytes[0] = 'B'.code.toByte()
        bytes[1] = 'W'.code.toByte()
        bytes[2] = 'A'.code.toByte()
        bytes[3] = 'V'.code.toByte()
        buf.putShort(4, 0xFEFF.toShort())
        buf.putShort(14, channels.toShort())
        for (ch in 0 until channels) {
            val base = 16 + ch * 76
            buf.putShort(base, 1) // DSP-ADPCM
            buf.putInt(base + 4, sampleRate)
            buf.putInt(base + 12, frames)
            buf.putInt(base + 60, -1)
            buf.putInt(base + 64, 0)
        }
        return bytes
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun dummyPacket(): ByteArray = G.build(
        G.Spec(
            assets = listOf(G.Asset("a.bwav", rate, 2, 1_000L, ByteArray(32))),
            segments = listOf(G.Segment(100, exit = 1_000L)),
            tracks = listOf(G.Track(200, 100, listOf(G.Clip(300, 0, sourceLength = 1_000L)))),
        ),
    )

    private class RouteAndRebindOutput(
        private var currentHandle: Long,
        private val rebuildResult: Boolean?,
        private val onLeasedChange: (Long, Boolean) -> Unit,
        private val onRebind: () -> Unit = {},
        private val onRebuild: () -> Unit = {},
        private val onClose: () -> Unit = {},
    ) : SessionAudioOutput {
        var rebuilds = 0
        var rebinds = 0
        var closes = 0
        var active = true

        override fun timingSnapshot(): LongArray? = null

        override fun suspendStream(): Boolean {
            if (!active) return false
            active = false
            onLeasedChange(currentHandle, false)
            return true
        }

        override fun resumeStream(sampleRate: Int): Boolean {
            if (active) return false
            active = true
            onLeasedChange(currentHandle, true)
            return true
        }

        override fun rebuildStream(sampleRate: Int): Boolean? {
            if (rebuildResult == true) {
                rebuilds++
                onRebuild()
            }
            return rebuildResult
        }

        override fun rebindEngine(engineHandle: Long, sampleRate: Int): Boolean {
            if (engineHandle == 0L) return false
            rebinds++
            onRebind()
            if (currentHandle != engineHandle) {
                onLeasedChange(currentHandle, false)
                currentHandle = engineHandle
            }
            active = true
            onLeasedChange(currentHandle, true)
            return true
        }

        override fun close() {
            closes++
            active = false
            onLeasedChange(currentHandle, false)
            onClose()
        }
    }

    private class FakeStabilityEngine(val id: Long) : SessionAudioEngine {
        var outputLeased = false
        var transportStatus = EngineSnapshot.STOPPED
        var transportFrame = 0L
        var closed = false

        override val sampleRate: Int = 48_000
        override fun nativeHandle(): Long = id
        override fun cancelPendingTransition(): Int = AudioEngine.STATUS_OK
        override fun loadGraph(packet: ByteArray): Int = if (outputLeased) 1 else AudioEngine.STATUS_OK
        override fun bindBwav(assetIndex: Int, bytes: ByteArray, assetId: String): Int =
            if (outputLeased) 1 else AudioEngine.STATUS_OK
        override fun bindStreamedBwav(assetIndex: Int, bytes: ByteArray, assetId: String): Int =
            if (outputLeased) 1 else AudioEngine.STATUS_OK
        override fun bindBwavFile(
            assetIndex: Int,
            file: File,
            assetId: String,
            streamed: Boolean,
            expectedSha256: ByteArray?,
        ): Int = if (outputLeased) 1 else AudioEngine.STATUS_OK
        override fun play(target: Int): Int {
            transportStatus = EngineSnapshot.PLAYING
            return AudioEngine.STATUS_OK
        }
        override fun stop(): Int {
            transportStatus = EngineSnapshot.STOPPED
            return AudioEngine.STATUS_OK
        }
        override fun pause(): Int {
            transportStatus = EngineSnapshot.PAUSED
            return AudioEngine.STATUS_OK
        }
        override fun resume(): Int {
            transportStatus = EngineSnapshot.PLAYING
            return AudioEngine.STATUS_OK
        }
        override fun seek(frame: Long): Int {
            if (outputLeased) return 1
            transportFrame = frame
            return AudioEngine.STATUS_OK
        }
        override fun snapshot(): LongArray = LongArray(65).apply {
            this[0] = EngineSnapshot.WIRE_VERSION
            this[1] = transportFrame
            this[2] = transportStatus
            this[3] = if (transportStatus == EngineSnapshot.PLAYING) 1L else 0L
        }
        override fun sourceSnapshot(slot: Int): LongArray? = null
        override fun close() {
            closed = true
            outputLeased = false
        }
    }

    @Test
    fun musicGraphBuilderSerializesLookAheadFramesAndDefaultsToZero() {
        val defaultAsset = G.Asset("a.bwav", rate, 2, 240_000L, ByteArray(32))
        assertEquals(0L, defaultAsset.lookAheadFrames)

        val zeroPacket = G.build(
            G.Spec(
                assets = listOf(defaultAsset),
                segments = listOf(G.Segment(100, exit = 240_000L)),
                tracks = listOf(G.Track(200, 100, listOf(G.Clip(300, 0, sourceLength = 240_000L)))),
            ),
        )
        val lookAheadPacket = G.build(
            G.Spec(
                assets = listOf(defaultAsset.copy(lookAheadFrames = 96_000L)),
                segments = listOf(G.Segment(100, exit = 240_000L)),
                tracks = listOf(G.Track(200, 100, listOf(G.Clip(300, 0, sourceLength = 240_000L)))),
            ),
        )

        val probe = assertIs<GraphProbe.Result.Ok>(GraphProbe.inspect(lookAheadPacket))
        assertEquals(G.VERSION, probe.version)
        assertFalse(zeroPacket.contentEquals(lookAheadPacket))

        assertFailsWith<IllegalArgumentException> {
            G.build(
                G.Spec(
                    assets = listOf(defaultAsset.copy(lookAheadFrames = -1L)),
                    segments = listOf(G.Segment(100, exit = 240_000L)),
                    tracks = listOf(G.Track(200, 100, listOf(G.Clip(300, 0, sourceLength = 240_000L)))),
                ),
            )
        }
    }

    @Test
    fun courseMedleyCompilerSetsTwoSecondLookAheadOnCompiledAssets() {
        val pre = syntheticBwav(frames = 240_000, sampleRate = rate)
        val outro = syntheticBwav(frames = 240_000, sampleRate = rate)
        val shaPre = MessageDigest.getInstance("SHA-256").digest(pre)
        val shaOutro = MessageDigest.getInstance("SHA-256").digest(outro)
        val medley = CourseMedleyCatalog.Medley(
            id = "m_lookahead",
            index = 1,
            courseCode = "FirstCircuit",
            variant = "a",
            totalFrames = 360_000L,
            durationMs = 7_500L,
            clips = listOf(
                CourseMedleyCatalog.MedleyClip("BGM_Stage_FirstCircuit_Pre", 0L, 0L, 120_000L),
                CourseMedleyCatalog.MedleyClip("BGM_Stage_FirstCircuit_Outro00", 120_000L, 0L, 240_000L),
            ),
            uniqueStems = listOf("BGM_Stage_FirstCircuit_Pre", "BGM_Stage_FirstCircuit_Outro00"),
        )

        val compiled = CourseMedleyCompiler.compileGraph(
            medley = medley,
            sampleRate = rate,
            bytesByStem = mapOf(
                "BGM_Stage_FirstCircuit_Pre" to pre,
                "BGM_Stage_FirstCircuit_Outro00" to outro,
            ),
        )
        assertIs<GraphProbe.Result.Ok>(GraphProbe.inspect(compiled.packet))

        fun buildReference(lookAheadFrames: Long): ByteArray = G.build(
            G.Spec(
                assets = listOf(
                    G.Asset("BGM_Stage_FirstCircuit_Pre.bwav", rate, 2, 240_000L, shaPre, lookAheadFrames),
                    G.Asset("BGM_Stage_FirstCircuit_Outro00.bwav", rate, 2, 240_000L, shaOutro, lookAheadFrames),
                ),
                segments = listOf(G.Segment(100, entry = 0L, exit = 360_000L)),
                tracks = listOf(
                    G.Track(200, 100, listOf(G.Clip(300, 0, start = 0L, sourceOffset = 0L, sourceLength = 120_000L))),
                    G.Track(201, 100, listOf(G.Clip(301, 1, start = 120_000L, sourceOffset = 0L, sourceLength = 240_000L))),
                ),
                transportSampleRate = rate,
            ),
        )

        assertTrue(compiled.packet.contentEquals(buildReference(96_000L)))
        assertFalse(compiled.packet.contentEquals(buildReference(0L)))
    }

    @Test
    fun playbackServicePollDelayScalesWithSpeedAndAbLoopEnd() {
        val total = 480_000L // 10s at 48 kHz
        val fourSecondsRemainingPos = total - 4L * rate

        assertEquals(
            PlaybackService.POLL_INTERVAL_MS,
            PlaybackService.computePollDelayMs(
                linear = true,
                playing = true,
                positionFrames = fourSecondsRemainingPos,
                totalFrames = total,
                sampleRate = rate,
                loadingOrSeeking = false,
                playbackSpeed = 1.0,
                abLoopEndFrame = null,
            ),
        )
        assertEquals(
            PlaybackService.PAUSED_POLL_INTERVAL_MS,
            PlaybackService.computePollDelayMs(
                linear = true,
                playing = false,
                positionFrames = fourSecondsRemainingPos,
                totalFrames = total,
                sampleRate = rate,
                loadingOrSeeking = false,
                playbackSpeed = 1.0,
                abLoopEndFrame = null,
            ),
        )
        assertEquals(
            PlaybackService.FAST_POLL_INTERVAL_MS,
            PlaybackService.computePollDelayMs(
                linear = true,
                playing = true,
                positionFrames = 0L,
                totalFrames = total,
                sampleRate = rate,
                loadingOrSeeking = true,
                playbackSpeed = 1.0,
                abLoopEndFrame = null,
            ),
        )
        assertEquals(
            PlaybackService.FAST_POLL_INTERVAL_MS,
            PlaybackService.computePollDelayMs(
                linear = true,
                playing = true,
                positionFrames = fourSecondsRemainingPos,
                totalFrames = total,
                sampleRate = rate,
                loadingOrSeeking = false,
                playbackSpeed = 2.0,
                abLoopEndFrame = null,
            ),
        )
        assertEquals(
            PlaybackService.FAST_POLL_INTERVAL_MS,
            PlaybackService.computePollDelayMs(
                linear = true,
                playing = true,
                positionFrames = 48_000L,
                totalFrames = total,
                sampleRate = rate,
                loadingOrSeeking = false,
                playbackSpeed = 1.0,
                abLoopEndFrame = 48_000L + 72_000L, // 1.5s ahead
            ),
        )
    }

    @Test
    fun playbackSessionOnAudioRouteChangedRebuildsActiveOutputOrReopensOnFailure() {
        // Case 1: rebuildStream returns true -> rebuilds in place without closing/reopening.
        val engine1 = FakeStabilityEngine(1L)
        var openCount1 = 0
        var out1: RouteAndRebindOutput? = null
        val session1 = PlaybackSession(
            createEngine = { engine1 },
            openOutput = { handle, _ ->
                openCount1++
                engine1.outputLeased = true
                RouteAndRebindOutput(
                    currentHandle = handle,
                    rebuildResult = true,
                    onLeasedChange = { _, leased -> engine1.outputLeased = leased },
                ).also { out1 = it }
            },
        )
        assertFalse(session1.onAudioRouteChanged(), "Inactive session must not rebuild")
        val prep1 = assertIs<PlaybackSession.PrepareResult.Ok>(
            session1.prepareFlow("Track 1", dummyPacket(), emptyList(), 1_000L, 100, linear = true),
        )
        session1.commitPrepared(prep1.prepared)
        assertEquals(1, openCount1)
        assertTrue(session1.onAudioRouteChanged())
        assertEquals(1, out1!!.rebuilds)
        assertEquals(0, out1!!.closes)
        assertEquals(1, openCount1)

        // Paused session must return false and not rebuild.
        session1.pause()
        assertFalse(session1.onAudioRouteChanged())
        assertEquals(1, out1!!.rebuilds)
        session1.release()

        // Case 2: rebuildStream returns false -> closes and reopens output.
        val engine2 = FakeStabilityEngine(2L)
        var openCount2 = 0
        val createdOutputs = mutableListOf<RouteAndRebindOutput>()
        val session2 = PlaybackSession(
            createEngine = { engine2 },
            openOutput = { handle, _ ->
                openCount2++
                engine2.outputLeased = true
                RouteAndRebindOutput(
                    currentHandle = handle,
                    rebuildResult = false,
                    onLeasedChange = { _, leased -> engine2.outputLeased = leased },
                ).also { createdOutputs += it }
            },
        )
        val prep2 = assertIs<PlaybackSession.PrepareResult.Ok>(
            session2.prepareFlow("Track 2", dummyPacket(), emptyList(), 1_000L, 100, linear = true),
        )
        session2.commitPrepared(prep2.prepared)
        assertEquals(1, openCount2)
        assertTrue(session2.onAudioRouteChanged())
        assertEquals(2, openCount2)
        assertEquals(1, createdOutputs[0].closes)
        assertTrue(engine2.outputLeased)
        session2.release()
    }

    @Test
    fun linearCoordinatorPreloadsSingleRepeatCourseMedleyForInstantHandover() {
        val tmpDir = kotlin.io.path.createTempDirectory("beta05-medley-repeat-").toFile()
        try {
            val audioDir = File(tmpDir, "audio").apply { mkdirs() }
            val preBytes = syntheticBwav(frames = 240_000, sampleRate = rate)
            val outroBytes = syntheticBwav(frames = 240_000, sampleRate = rate)
            val shaPre = sha256Hex(preBytes)
            val shaOutro = sha256Hex(outroBytes)
            File(audioDir, shaPre).writeBytes(preBytes)
            File(audioDir, shaOutro).writeBytes(outroBytes)

            val medley = CourseMedleyCatalog.Medley(
                id = "m_repeat",
                index = 1,
                courseCode = "FirstCircuit",
                variant = "a",
                totalFrames = 360_000L,
                durationMs = 7_500L,
                clips = listOf(
                    CourseMedleyCatalog.MedleyClip("BGM_Stage_FirstCircuit_Pre", 0L, 0L, 120_000L),
                    CourseMedleyCatalog.MedleyClip("BGM_Stage_FirstCircuit_Outro00", 120_000L, 0L, 240_000L),
                ),
                uniqueStems = listOf("BGM_Stage_FirstCircuit_Pre", "BGM_Stage_FirstCircuit_Outro00"),
            )
            val resolved = CourseMedleyCompiler.ResolvedMedley(
                medley = medley,
                sampleRate = rate,
                stemDigests = mapOf(
                    "BGM_Stage_FirstCircuit_Pre" to shaPre,
                    "BGM_Stage_FirstCircuit_Outro00" to shaOutro,
                ),
            )
            val entry = LinearQueue.Entry(
                key = "medley:m_repeat",
                title = "First Circuit Medley",
                sourceType = LinearSessionStore.SourceType.MEDLEY,
                courseCode = "FirstCircuit",
                courseMedley = resolved,
            )

            val engines = mutableListOf<FakeStabilityEngine>()
            var openCount = 0
            var rebindCount = 0
            val session = PlaybackSession(
                createEngine = { FakeStabilityEngine((engines.size + 1).toLong()).also { engines += it } },
                openOutput = { handle, _ ->
                    openCount++
                    val eng = engines.first { it.id == handle }
                    eng.outputLeased = true
                    RouteAndRebindOutput(
                        currentHandle = handle,
                        rebuildResult = true,
                        onLeasedChange = { targetHandle, leased ->
                            engines.firstOrNull { it.id == targetHandle }?.outputLeased = leased
                        },
                        onRebind = { rebindCount++ },
                    )
                },
            )
            val coordinator = LinearPlaybackCoordinator(
                sessionProvider = { session },
                mainDispatcher = Dispatchers.Unconfined,
                ioDispatcher = Dispatchers.IO,
            )

            coordinator.startExtraQueue(
                entries = listOf(entry),
                startIndex = 0,
                packDir = tmpDir,
                repeatMode = LinearQueue.RepeatMode.SINGLE,
                shuffleOverride = false,
            )

            val deadline = System.currentTimeMillis() + 5_000L
            while (System.currentTimeMillis() < deadline &&
                (coordinator.state.value.loading ||
                    coordinator.state.value.current?.key != entry.key ||
                    engines.size < 2 ||
                    engines[1].transportStatus != EngineSnapshot.PLAYING)
            ) {
                Thread.sleep(25L)
            }
            Thread.sleep(50L)
            assertEquals(entry.key, coordinator.state.value.current?.key)
            assertFalse(coordinator.state.value.loading)
            assertEquals(2, engines.size, "Repeat-Single CourseMedley must preload a second engine after 200ms")

            assertTrue(coordinator.handleNaturalEnd())
            assertEquals(entry.key, coordinator.state.value.current?.key)
            assertFalse(coordinator.state.value.loading, "Preloaded repeat handover must commit immediately without loading")
            assertEquals(1, openCount, "Active output must be reused via rebindEngine")
            assertEquals(1, rebindCount)
            assertTrue(engines[0].closed)
            assertTrue(engines[1].outputLeased)

            coordinator.clearSession()
            session.release()
            assertTrue(engines.all { it.closed })
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    @Test
    fun playbackServiceMediaStateRefreshAvoidsPeriodicAvrcpSpamAndResyncsOnDiscontinuities() {
        // Steady 1.0x playback after 1s, 10s, 60s: MediaSession extrapolates smoothly, no AVRCP spam.
        assertFalse(
            PlaybackService.shouldRefreshMediaState(
                active = true,
                refreshKeyChanged = false,
                seekSequenceChanged = false,
                speedChanged = false,
                playing = true,
                actualPositionMs = 11_050L,
                lastPublishedPositionMs = 10_000L,
                elapsedSincePublishMs = 1_000L,
                lastPublishedSpeed = 1.0f,
            ),
        )
        assertFalse(
            PlaybackService.shouldRefreshMediaState(
                active = true,
                refreshKeyChanged = false,
                seekSequenceChanged = false,
                speedChanged = false,
                playing = true,
                actualPositionMs = 70_200L,
                lastPublishedPositionMs = 10_000L,
                elapsedSincePublishMs = 60_000L,
                lastPublishedSpeed = 1.0f,
            ),
        )
        // Steady 1.5x playback: 10s wall time = 15s media time -> no resync needed.
        assertFalse(
            PlaybackService.shouldRefreshMediaState(
                active = true,
                refreshKeyChanged = false,
                seekSequenceChanged = false,
                speedChanged = false,
                playing = true,
                actualPositionMs = 25_100L,
                lastPublishedPositionMs = 10_000L,
                elapsedSincePublishMs = 10_000L,
                lastPublishedSpeed = 1.5f,
            ),
        )
        // Native loop wrap (jumped from ~90s back to 15s): must resync immediately.
        assertTrue(
            PlaybackService.shouldRefreshMediaState(
                active = true,
                refreshKeyChanged = false,
                seekSequenceChanged = false,
                speedChanged = false,
                playing = true,
                actualPositionMs = 15_000L,
                lastPublishedPositionMs = 89_000L,
                elapsedSincePublishMs = 1_000L,
                lastPublishedSpeed = 1.0f,
            ),
        )
        // Seek, speed change, state key change, or stop must always trigger refresh.
        assertTrue(
            PlaybackService.shouldRefreshMediaState(
                active = true,
                refreshKeyChanged = false,
                seekSequenceChanged = true,
                speedChanged = false,
                playing = true,
                actualPositionMs = 11_000L,
                lastPublishedPositionMs = 10_000L,
                elapsedSincePublishMs = 1_000L,
                lastPublishedSpeed = 1.0f,
            ),
        )
        assertTrue(
            PlaybackService.shouldRefreshMediaState(
                active = true,
                refreshKeyChanged = false,
                seekSequenceChanged = false,
                speedChanged = true,
                playing = true,
                actualPositionMs = 11_000L,
                lastPublishedPositionMs = 10_000L,
                elapsedSincePublishMs = 1_000L,
                lastPublishedSpeed = 1.0f,
            ),
        )
    }

    @Test
    fun playbackSessionInteractiveObservationCachesSourceSlotAndAvoids128SlotScan() {
        var sourceSnapshotCalls = 0
        val engine = object : SessionAudioEngine {
            override val sampleRate: Int = 48_000
            override fun nativeHandle(): Long = 99L
            override fun cancelPendingTransition(): Int = AudioEngine.STATUS_OK
            override fun loadGraph(packet: ByteArray): Int = AudioEngine.STATUS_OK
            override fun bindBwav(assetIndex: Int, bytes: ByteArray, assetId: String): Int = AudioEngine.STATUS_OK
            override fun play(target: Int): Int = AudioEngine.STATUS_OK
            override fun stop(): Int = AudioEngine.STATUS_OK
            override fun pause(): Int = AudioEngine.STATUS_OK
            override fun resume(): Int = AudioEngine.STATUS_OK
            override fun seek(frame: Long): Int = AudioEngine.STATUS_OK
            override fun close() {}
            override fun snapshot(): LongArray = LongArray(66).apply {
                this[0] = EngineSnapshot.WIRE_VERSION
                this[1] = 48_000L
                this[2] = EngineSnapshot.PLAYING
                this[3] = 1L // 1 active voice
                this[11] = 100L // playing segment 100
            }
            override fun sourceSnapshot(slot: Int): LongArray? {
                sourceSnapshotCalls++
                if (slot != 5) return LongArray(207)
                return LongArray(207).apply {
                    this[0] = 3L
                    this[1] = 1L // active
                    this[4] = 100L // segment 100
                    this[10] = 12_345L // sourceFrame
                }
            }
        }
        val session = PlaybackSession(
            createEngine = { engine },
            openOutput = { _, _ -> null },
        )
        val prep = assertIs<PlaybackSession.PrepareResult.Ok>(
            session.prepareFlow("Interactive", dummyPacket(), emptyList(), 96_000L, 100, linear = false),
        )
        session.commitPrepared(prep.prepared)
        sourceSnapshotCalls = 0

        // First observation scans up to slot 5 (6 calls) and caches slot 5.
        val first = session.interactiveObservation()
        assertEquals(12_345L, first?.sourceFrame)
        assertEquals(6, sourceSnapshotCalls)

        // Subsequent observations hit cached slot 5 in exactly 1 JNI call (instead of scanning up to 128).
        sourceSnapshotCalls = 0
        val second = session.interactiveObservation()
        assertEquals(12_345L, second?.sourceFrame)
        assertEquals(1, sourceSnapshotCalls)

        session.release()
    }
}
