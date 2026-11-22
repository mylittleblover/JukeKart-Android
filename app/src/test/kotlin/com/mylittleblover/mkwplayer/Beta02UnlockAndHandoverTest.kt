package com.mylittleblover.mkwplayer

import com.mylittleblover.mkwplayer.audio.AudioEngine
import com.mylittleblover.mkwplayer.audio.EngineSnapshot
import com.mylittleblover.mkwplayer.audio.SessionAudioEngine
import com.mylittleblover.mkwplayer.audio.SessionAudioOutput
import com.mylittleblover.mkwplayer.graph.GraphProbe
import com.mylittleblover.mkwplayer.graph.MusicGraphBuilder as G
import com.mylittleblover.mkwplayer.interactive.CourseMedleyCatalog
import com.mylittleblover.mkwplayer.interactive.CourseMedleyCompiler
import com.mylittleblover.mkwplayer.library.RecentCourseStore
import com.mylittleblover.mkwplayer.player.LinearPlaybackCoordinator
import com.mylittleblover.mkwplayer.player.LinearQueue
import com.mylittleblover.mkwplayer.player.LinearSessionStore
import com.mylittleblover.mkwplayer.player.PlaybackSession
import com.mylittleblover.mkwplayer.player.UnlockProgressCoordinator
import com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog
import com.mylittleblover.mkwplayer.unlock.ListeningProgress
import com.mylittleblover.mkwplayer.unlock.SpecialCupRules
import com.mylittleblover.mkwplayer.unlock.SpecialCupUnlock
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Beta02UnlockAndHandoverTest {

    private val rate = 48_000
    private val required = SpecialCupUnlock.requiredFrames(rate)

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

    // ---------------------------------------------------------------- Part 1: Normalization & Retroactive Unlock

    @Test
    fun stageCodeNormalizationHandlesZeroZeroVariantsAndExclusions() {
        assertEquals("AirshipFortress", SpecialCupRules.normalizeStageCode("AirshipFortress_00"))
        assertEquals("AirshipFortress", SpecialCupRules.normalizeStageCode("BGM_Stage_AirshipFortress_00"))
        assertEquals("Cinema", SpecialCupRules.normalizeStageCode("Cinema_SFC"))
        assertEquals("Cinema", SpecialCupRules.normalizeStageCode("BGM_Stage_Cinema_SFC.bwav"))
        assertEquals("SnowMountain", SpecialCupRules.normalizeStageCode("SnowMountain_Pattern2"))
        assertEquals("DonkeyKong", SpecialCupRules.normalizeStageCode("BGM_Stage_DonkeyKong_Fast_Pattern5.bwav"))
        assertEquals("SFCMarioCircuit", SpecialCupRules.normalizeStageCode("SFCMarioCircuit"))

        assertTrue(SpecialCupRules.isExcludedFromUnlock("Rainbow_00"))
        assertTrue(SpecialCupRules.isExcludedFromUnlock("BGM_Stage_BigTree_00"))
        assertTrue(SpecialCupRules.isExcludedFromUnlock("SFCMarioCircuit_00"))
        assertFalse(SpecialCupRules.isExcludedFromUnlock("Cinema_SFC"))
    }

    @Test
    fun unlockResolvesZeroZeroAndVariantStageCodesIntoCanonicalEligibleStages() {
        val eligible = setOf("AirshipFortress", "Cinema", "SnowMountain", "Coaster")
        var unlock = SpecialCupUnlock(eligible)

        for ((inputStage, _) in listOf(
            "AirshipFortress_00" to 1,
            "Cinema_SFC" to 2,
            "SnowMountain_Pattern2" to 3,
            "Coaster" to 4,
        )) {
            unlock = unlock.withState(unlock.beginStage(inputStage, 10_000_000L, rate))
            var pos = 0L
            while (pos <= required) {
                unlock = unlock.withState(unlock.report(pos).state)
                pos += 48_000L
            }
        }

        assertEquals(eligible, unlock.snapshot.completedStages)
        assertTrue(unlock.isEntitled)
        assertEquals(SpecialCupUnlock.CeremonyPhase.PENDING, unlock.snapshot.ceremony)
    }

    @Test
    fun reconcileNormalizesLegacyKeysAndRetroactivelyGrantsEntitlement() {
        val eligible = setOf("AirshipFortress", "Cinema", "SnowMountain", "Coaster")
        val legacyState = SpecialCupUnlock.UnlockState(
            listenedFrames = mapOf(
                "AirshipFortress_00" to required,
                "Cinema_SFC" to required,
                "SnowMountain_Pattern2" to required,
                "Coaster" to required,
                "BigTree_00" to required,
            ),
            completedStages = setOf("AirshipFortress_00", "Cinema_SFC", "SnowMountain_Pattern2", "BigTree_00"),
            entitled = false,
            ceremony = SpecialCupUnlock.CeremonyPhase.NONE,
            congratulated = false,
            currentStage = null,
            lastReportedFrames = -1L,
            currentTrackFrames = 0L,
            currentSampleRate = 0,
        )

        val bridge = ListeningProgress(SpecialCupUnlock(eligible).withState(legacyState))
        val changed = bridge.reconcile(rate)

        assertTrue(changed)
        assertEquals(eligible, bridge.snapshot.completedStages)
        assertFalse("BigTree_00" in bridge.snapshot.completedStages)
        assertTrue(bridge.snapshot.entitled)
        assertEquals(SpecialCupUnlock.CeremonyPhase.PENDING, bridge.snapshot.ceremony)
    }

    @Test
    fun sampleContinuousHandlesFastPlaybackAndJitterWithoutBrickingOrAllowingScrubs() {
        val bridge = ListeningProgress(SpecialCupUnlock(setOf("Coaster")))
        bridge.begin("Coaster", 10_000_000L, rate)
        bridge.sampleContinuous(0L, playing = true, wallDeltaMs = 200L)

        // 2.0x speed across a 700ms poll interval advances 67,200 frames (> 1s single-step limit)
        val fastStep = bridge.sampleContinuous(67_200L, playing = true, wallDeltaMs = 700L, maxPlaybackSpeed = 2.0)
        assertNull(fastStep.ignoredReason)
        assertEquals(67_200L, bridge.snapshot.listenedFrames["Coaster"])

        // Sudden un-notified scrub jump (1,000,000 frames in 20ms) must credit 0 and re-anchor cleanly
        bridge.sampleContinuous(1_067_200L, playing = true, wallDeltaMs = 20L, maxPlaybackSpeed = 1.0)
        assertEquals(67_200L, bridge.snapshot.listenedFrames["Coaster"])

        // Real playback continuing from the re-anchored cursor still accumulates the remaining frames
        var pos = 1_067_200L
        while ((bridge.snapshot.listenedFrames["Coaster"] ?: 0L) < required) {
            pos += 24_000L
            bridge.sampleContinuous(pos, playing = true, wallDeltaMs = 500L, maxPlaybackSpeed = 1.0)
        }
        assertTrue("Coaster" in bridge.snapshot.completedStages)
    }

    @Test
    fun canonicalRefIdFormatsAllLinearSourcesAndRecentStorePersists() {
        val extraCatalog = ExtraMusicCatalog.parse(File("src/main/assets/extra_music_catalog.json").readText())

        val courseEntry = LinearQueue.Entry(
            key = "medley:01_FirstCircuit_a",
            title = "Mario Bros. Circuit",
            sourceType = LinearSessionStore.SourceType.MEDLEY,
            courseCode = "AirshipFortress_00",
        )
        val jukeboxEntry = LinearQueue.Entry(
            key = "route:edge:0:t_010",
            title = "Road Track",
            sourceType = LinearSessionStore.SourceType.JUKEBOX,
            sourceTrackKey = "t_010",
        )
        val sfcEntry = LinearQueue.Entry(
            key = "pl:1:0:extra:sfc_choco_island",
            title = "Choco Island",
            sourceType = LinearSessionStore.SourceType.MEDLEY,
            courseCode = "extra:sfc_choco_island",
        )
        val battleEntry = LinearQueue.Entry(
            key = "pl:1:1:extra:battle_delfino_pier",
            title = "Delfino Pier",
            sourceType = LinearSessionStore.SourceType.MEDLEY,
            courseCode = "extra:battle_delfino_pier",
        )
        val extraEntry = LinearQueue.Entry(
            key = "pl:1:2:extra:title",
            title = "Title",
            sourceType = LinearSessionStore.SourceType.EXTRA,
        )
        val ceremonyEntry = LinearQueue.Entry(
            key = "extra:special_cup_appears",
            title = "Special Cup Appears",
            sourceType = LinearSessionStore.SourceType.EXTRA,
        )

        assertEquals("AirshipFortress", UnlockProgressCoordinator.canonicalRefId(null, courseEntry, extraCatalog))
        assertEquals("jb:t_010", UnlockProgressCoordinator.canonicalRefId(null, jukeboxEntry, extraCatalog))
        assertEquals("sfc:sfc_choco_island", UnlockProgressCoordinator.canonicalRefId(null, sfcEntry, extraCatalog))
        assertEquals("battle:battle_delfino_pier", UnlockProgressCoordinator.canonicalRefId(null, battleEntry, extraCatalog))
        assertEquals("extra:title", UnlockProgressCoordinator.canonicalRefId(null, extraEntry, extraCatalog))
        assertNull(UnlockProgressCoordinator.canonicalRefId(null, ceremonyEntry, extraCatalog))

        val tmpDir = kotlin.io.path.createTempDirectory("recent-test-").toFile()
        try {
            val recent = RecentCourseStore(File(tmpDir, "recent-courses.json"))
            recent.record("AirshipFortress").getOrThrow()
            recent.record("jb:t_010").getOrThrow()
            assertEquals(listOf("jb:t_010", "AirshipFortress"), recent.read().getOrThrow())
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    // ---------------------------------------------------------------- Part 2: Zero-Rehash, Output Reuse & Background Preload

    @Test
    fun compileGraphUsesKnownDigestsHexAndMatchesFullDigest() {
        val bwavA = syntheticBwav(frames = 600_000)
        val bwavB = syntheticBwav(frames = 600_000)
        val medley = CourseMedleyCatalog.Medley(
            id = "test_medley",
            index = 1,
            courseCode = "FirstCircuit",
            variant = "a",
            clips = listOf(
                CourseMedleyCatalog.MedleyClip(stem = "StemA", start = 0L, sourceOffset = 0L, sourceLength = 300_000L),
                CourseMedleyCatalog.MedleyClip(stem = "StemB", start = 300_000L, sourceOffset = 0L, sourceLength = 300_000L),
            ),
            uniqueStems = listOf("StemA", "StemB"),
            totalFrames = 600_000L,
            durationMs = 12_500L,
        )
        val bytesByStem = mapOf("StemA" to bwavA, "StemB" to bwavB)
        val digestsHex = mapOf("StemA" to sha256Hex(bwavA), "StemB" to sha256Hex(bwavB))

        val compiledDefault = CourseMedleyCompiler.compileGraph(medley, rate, bytesByStem)
        val compiledFast = CourseMedleyCompiler.compileGraph(medley, rate, bytesByStem, knownDigestsHex = digestsHex)

        assertContentEquals(compiledDefault.packet, compiledFast.packet)
        assertTrue(GraphProbe.inspect(compiledFast.packet) is GraphProbe.Result.Ok)
    }

    private class ReusableOutput(
        private val onSuspend: () -> Unit = {},
        private val onResume: () -> Unit = {},
        private val onClose: () -> Unit,
    ) : SessionAudioOutput {
        var suspends = 0
        var resumes = 0
        var closes = 0
        var active = true

        override fun timingSnapshot(): LongArray? = null

        override fun suspendStream(): Boolean {
            if (!active) return false
            suspends++
            active = false
            onSuspend()
            return true
        }

        override fun resumeStream(sampleRate: Int): Boolean {
            if (active) return false
            resumes++
            active = true
            onResume()
            return true
        }

        override fun close() {
            closes++
            active = false
            onClose()
        }
    }

    private class ReusableEngine(val id: Long) : SessionAudioEngine {
        var outputLeased = false
        var transportStatus = EngineSnapshot.STOPPED
        var transportFrame = 0L
        var sourceFrame = 0L
        var closed = false

        override val sampleRate: Int = 48_000
        override fun nativeHandle(): Long = id
        override fun cancelPendingTransition(): Int = AudioEngine.STATUS_OK
        override fun loadGraph(packet: ByteArray): Int = if (outputLeased) 1 else AudioEngine.STATUS_OK
        override fun bindBwav(assetIndex: Int, bytes: ByteArray, assetId: String): Int =
            if (outputLeased) 1 else AudioEngine.STATUS_OK
        override fun loadLinearFile(file: File, assetId: String, loop: Boolean, streamed: Boolean): Int =
            if (outputLeased) 1 else AudioEngine.STATUS_OK
        override fun setLinearRepeat(enabled: Boolean, begin: Long, end: Long): Int =
            if (outputLeased) 1 else AudioEngine.STATUS_OK
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
            sourceFrame = frame
            return AudioEngine.STATUS_OK
        }
        override fun snapshot(): LongArray = LongArray(65).apply {
            this[0] = EngineSnapshot.WIRE_VERSION
            this[1] = transportFrame
            this[2] = transportStatus
            this[3] = if (transportStatus == EngineSnapshot.PLAYING) 1L else 0L
        }
        override fun sourceSnapshot(slot: Int): LongArray = LongArray(11).apply {
            this[1] = 1L
            this[10] = sourceFrame
        }
        override fun close() {
            closed = true
        }
    }

    @Test
    fun playbackSessionSuspendsAndResumesReusableOutputAcrossSeekAndReloadWithoutDestroy() {
        val engine = ReusableEngine(1L)
        var openCount = 0
        var lastOutput: ReusableOutput? = null
        val session = PlaybackSession(
            createEngine = { engine },
            openOutput = { _, _ ->
                openCount++
                engine.outputLeased = true
                ReusableOutput(
                    onSuspend = { engine.outputLeased = false },
                    onResume = { engine.outputLeased = true },
                    onClose = { engine.outputLeased = false },
                ).also { lastOutput = it }
            },
        )
        val pkt = G.build(
            G.Spec(
                assets = listOf(G.Asset("a", rate, 2, 1_000, ByteArray(32))),
                segments = listOf(G.Segment(100, exit = 1_000)),
                tracks = listOf(G.Track(200, 100, listOf(G.Clip(300, 0, sourceLength = 1_000)))),
            ),
        )

        assertNull(session.loadFlow("One", pkt, listOf("a" to byteArrayOf(1)), 1_000, 100).detail)
        val out = assertNotNull(lastOutput)
        assertEquals(1, openCount)

        // Reloading a flow & seeking on the same engine reuses the existing output handle.
        assertNull(session.loadFlow("Two", pkt, listOf("a" to byteArrayOf(1)), 1_000, 100).detail)
        session.seek(500L)
        assertEquals(1, openCount, "Reusable AudioOutput must not be destroyed and recreated")
        assertEquals(2, out.suspends)
        assertEquals(2, out.resumes)
        assertEquals(0, out.closes)

        session.release()
        assertEquals(1, out.closes)
    }

    @Test
    fun linearCoordinatorPreloadsNextTrackAndCommitsImmediatelyOnNaturalEnd() {
        val tmpDir = kotlin.io.path.createTempDirectory("linear-preload-test-").toFile()
        try {
            val audioDir = File(tmpDir, "audio").apply { mkdirs() }
            val bwav1 = syntheticBwav(frames = 480_000)
            val bwav2 = syntheticBwav(frames = 480_000)
            val sha1 = sha256Hex(bwav1)
            val sha2 = sha256Hex(bwav2)
            File(audioDir, sha1).writeBytes(bwav1)
            File(audioDir, sha2).writeBytes(bwav2)

            val engines = mutableListOf<ReusableEngine>()
            val session = PlaybackSession(
                createEngine = { ReusableEngine((engines.size + 1).toLong()).also { engines += it } },
                openOutput = { handle, _ ->
                    val eng = engines.first { it.id == handle }
                    eng.outputLeased = true
                    ReusableOutput(
                        onSuspend = { eng.outputLeased = false },
                        onResume = { eng.outputLeased = true },
                        onClose = { eng.outputLeased = false },
                    )
                },
            )
            val coordinator = LinearPlaybackCoordinator(
                sessionProvider = { session },
                mainDispatcher = Dispatchers.Unconfined,
                ioDispatcher = Dispatchers.IO,
            )

            val entry1 = LinearQueue.Entry(
                key = "jb:track1",
                title = "Track 1",
                sourceType = LinearSessionStore.SourceType.JUKEBOX,
                assetSha256 = sha1,
            )
            val entry2 = LinearQueue.Entry(
                key = "jb:track2",
                title = "Track 2",
                sourceType = LinearSessionStore.SourceType.JUKEBOX,
                assetSha256 = sha2,
            )

            coordinator.startExtraQueue(
                entries = listOf(entry1, entry2),
                startIndex = 0,
                packDir = tmpDir,
                repeatMode = LinearQueue.RepeatMode.OFF,
                shuffleOverride = false,
            )

            // Wait for Track 1 async open + background Track 2 preload (200ms settle + IO prepare).
            val deadline = System.currentTimeMillis() + 5_000L
            while (System.currentTimeMillis() < deadline &&
                (coordinator.state.value.loading || coordinator.state.value.current?.key != "jb:track1" || engines.size < 2)
            ) {
                Thread.sleep(25L)
            }
            assertEquals("jb:track1", coordinator.state.value.current?.key)
            assertFalse(coordinator.state.value.loading)
            assertEquals(2, engines.size, "Track 2 should be pre-prepared in background while Track 1 plays")
            val engineCountBeforeHandover = engines.size

            // Trigger natural end on Track 1: Track 2 must commit immediately without creating a 3rd engine or entering loading.
            engines[0].transportStatus = EngineSnapshot.STOPPED
            session.poll()

            assertEquals("jb:track2", coordinator.state.value.current?.key)
            assertFalse(coordinator.state.value.loading)
            assertEquals(engineCountBeforeHandover, engines.size)
            assertTrue(engines[0].closed)
            assertTrue(engines[1].outputLeased)

            // Clearing the session discards any preloaded/active engines cleanly.
            coordinator.clearSession()
            session.release()
            assertTrue(engines.all { it.closed })
        } finally {
            tmpDir.deleteRecursively()
        }
    }
}
