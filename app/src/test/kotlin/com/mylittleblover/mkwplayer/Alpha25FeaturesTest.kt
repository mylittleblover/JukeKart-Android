package com.mylittleblover.mkwplayer

import com.mylittleblover.mkwplayer.audio.EqualizerPreset
import com.mylittleblover.mkwplayer.audio.MasterDspStateConfig
import com.mylittleblover.mkwplayer.audio.PlaybackRateConfig
import com.mylittleblover.mkwplayer.library.BackupPreferencesSnapshot
import com.mylittleblover.mkwplayer.library.CoursePreset
import com.mylittleblover.mkwplayer.library.FlowStep
import com.mylittleblover.mkwplayer.library.PlayStatsStore
import com.mylittleblover.mkwplayer.library.PlaylistEntryConfig
import com.mylittleblover.mkwplayer.library.TrackPlayStat
import com.mylittleblover.mkwplayer.library.UserBackupManager
import com.mylittleblover.mkwplayer.library.UserPlaylist
import com.mylittleblover.mkwplayer.player.InteractiveTimedAction
import com.mylittleblover.mkwplayer.player.LinearQueue
import com.mylittleblover.mkwplayer.player.PlaybackTimerEngine
import com.mylittleblover.mkwplayer.player.SleepTimerMode
import com.mylittleblover.mkwplayer.routes.CustomRoute
import com.mylittleblover.mkwplayer.routes.CustomRouteEdgeConfig
import com.mylittleblover.mkwplayer.routes.CustomRouteStop
import com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog
import java.nio.file.Files
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Alpha25FeaturesTest {

    @Test
    fun playbackRateNormalizationClampsSpeedAndLinksPitchWhenUnpreserved() {
        val clamped = PlaybackRateConfig(
            speed = 2.75f,
            pitchSemitones = 19f,
            preservePitch = true,
        ).normalized()
        assertEquals(2.0f, clamped.speed, 0.001f)
        assertEquals(12.0f, clamped.pitchSemitones, 0.001f)
        assertTrue(clamped.preservePitch)

        val linkedHalfSpeed = PlaybackRateConfig(
            speed = 0.5f,
            pitchSemitones = 5.0f,
            preservePitch = false,
        ).normalized()
        assertEquals(0.5f, linkedHalfSpeed.speed, 0.001f)
        assertEquals(-12.0f, linkedHalfSpeed.pitchSemitones, 0.05f)
        assertFalse(linkedHalfSpeed.preservePitch)

        val linkedDoubleSpeed = PlaybackRateConfig(
            speed = 2.0f,
            pitchSemitones = 0f,
            preservePitch = false,
        ).normalized()
        assertEquals(12.0f, linkedDoubleSpeed.pitchSemitones, 0.05f)
    }

    @Test
    fun masterDspConfigPresetMatchingAndClampingWork() {
        assertEquals(EqualizerPreset.FLAT, EqualizerPreset.matchingOrCustom(listOf(0f, 0f, 0f, 0f, 0f)))
        assertEquals(EqualizerPreset.RACING, EqualizerPreset.matchingOrCustom(EqualizerPreset.RACING.bandsDb))
        assertEquals(EqualizerPreset.CUSTOM, EqualizerPreset.matchingOrCustom(listOf(3f, 0f, -2f, 1f, 4f)))

        val dsp = MasterDspStateConfig(
            eqEnabled = true,
            bandsDb = listOf(-20f, 5f, 18f, 0f, -3.5f),
            preset = EqualizerPreset.CUSTOM,
            bassBoostPercent = 150,
            loudnessDb = 6.5f,
        )
        assertEquals(9.0, dsp.bassBoostDb, 0.01)
        val doubles = dsp.bandArrayDoubles()
        assertEquals(5, doubles.size)
        assertEquals(-12.0, doubles[0], 0.001)
        assertEquals(5.0, doubles[1], 0.001)
        assertEquals(12.0, doubles[2], 0.001)
        assertEquals(0.0, doubles[3], 0.001)
        assertEquals(-3.5, doubles[4], 0.001)
    }

    @Test
    fun linearQueueReorderRemoveAndClearUpcomingPreserveCurrent() {
        val entries = (1..5).map { idx ->
            LinearQueue.Entry(key = "track-$idx", title = "Track $idx")
        }
        val queue = LinearQueue()
        queue.startSession(entries, startIndex = 0)
        queue.enqueueNext(LinearQueue.Entry(key = "next-b", title = "Next B"))
        queue.enqueueNext(LinearQueue.Entry(key = "next-a", title = "Next A"))

        assertTrue(queue.moveTemp(0, 1))
        assertFalse(queue.moveTemp(1, 1))
        assertFalse(queue.moveTemp(-1, 0))
        assertEquals(listOf("next-b", "next-a"), queue.snapshot().tempQueue.map { it.key })

        assertTrue(queue.moveUpcoming(0, 2))
        assertEquals(listOf("track-3", "track-4", "track-2", "track-5"), queue.snapshot().upNext.map { it.key })

        assertTrue(queue.removeUpcomingAt(1))
        assertEquals(listOf("track-3", "track-2", "track-5"), queue.snapshot().upNext.map { it.key })
        assertFalse(queue.removeUpcomingAt(99))

        assertTrue(queue.clearUpcoming())
        val afterClear = queue.snapshot()
        assertEquals("track-1", afterClear.current?.key)
        assertTrue(afterClear.tempQueue.isEmpty())
        assertTrue(afterClear.upNext.isEmpty())
        assertFalse(queue.clearUpcoming())
    }

    @Test
    fun playStatsStoreRecordMergeAndReplaceAll() {
        val dir = Files.createTempDirectory("mkw-play-stats-test").toFile()
        val file = dir.resolve("play-stats.json")
        val store = PlayStatsStore(file)

        val first = store.recordPlay("RainbowRoad", nowEpochMs = 1_000L).getOrThrow()
        assertEquals(1, first["RainbowRoad"]?.playCount)
        val second = store.recordPlay("RainbowRoad", nowEpochMs = 2_000L).getOrThrow()
        assertEquals(2, second["RainbowRoad"]?.playCount)
        assertEquals(2_000L, second["RainbowRoad"]?.lastPlayedEpochMs)

        val merged = store.merge(
            mapOf(
                "RainbowRoad" to TrackPlayStat("RainbowRoad", playCount = 1, lastPlayedEpochMs = 500L),
                "jb:mk8_bgm" to TrackPlayStat("jb:mk8_bgm", playCount = 7, lastPlayedEpochMs = 5_000L),
            ),
        ).getOrThrow()
        assertEquals(2, merged["RainbowRoad"]?.playCount)
        assertEquals(2_000L, merged["RainbowRoad"]?.lastPlayedEpochMs)
        assertEquals(7, merged["jb:mk8_bgm"]?.playCount)

        val replaced = store.replaceAll(
            mapOf(
                "sfx:coin" to TrackPlayStat("sfx:coin", playCount = 3, lastPlayedEpochMs = 9_000L),
                "ignored_zero" to TrackPlayStat("ignored_zero", playCount = 0, lastPlayedEpochMs = 9_000L),
            ),
        ).getOrThrow()
        assertEquals(1, replaced.size)
        assertEquals(3, replaced["sfx:coin"]?.playCount)
        assertNull(replaced["RainbowRoad"])
        assertEquals(3, PlayStatsStore(file).read().getOrThrow()["sfx:coin"]?.playCount)
    }

    @Test
    fun userBackupJsonRoundtripAndM3uExport() {
        val playlists = listOf(
            UserPlaylist(
                id = "pl-1",
                name = "Favorites Night",
                courseIds = listOf("MarioBrosCircuit", "jb:ost_01"),
                nativeTransitionDefault = true,
                entryConfigs = listOf(
                    PlaylistEntryConfig(presetId = "user:p1", nativeTransitionEnabled = false),
                    PlaylistEntryConfig(presetId = null, nativeTransitionEnabled = null),
                ),
            ),
        )
        val favorites = setOf("MarioBrosCircuit", "jb:ost_01")
        val presets = listOf(
            CoursePreset(
                id = "user:p1",
                courseCode = "MarioBrosCircuit",
                name = "Sprint Flow",
                steps = listOf(FlowStep("Area1", loopCount = 1), FlowStep("FinalPre", loopCount = 0)),
                outroNumber = 2,
                fadeOutMs = 1500,
                outroToNextPre = true,
                updatedAtEpochMs = 123_456L,
            ),
        )
        val customRoutes = listOf(
            CustomRoute(
                id = "route-1",
                name = "Desert Run",
                stops = listOf(CustomRouteStop("DesertHills", null), CustomRouteStop("ShyGuyBazaar", "user:p1")),
                edges = listOf(CustomRouteEdgeConfig("DesertHills", "ShyGuyBazaar", musicEnabled = true, selectedJukeboxKey = "ost_01")),
                loopWholeRoute = true,
                updatedAtEpochMs = 200L,
                lastUsedEpochMs = 300L,
                roadRuleMode = RoadJukeboxCatalog.RoadMusicRuleMode.AUTO,
            ),
        )
        val recent = listOf("MarioBrosCircuit", "DesertHills")
        val stats = mapOf(
            "MarioBrosCircuit" to TrackPlayStat("MarioBrosCircuit", playCount = 12, lastPlayedEpochMs = 456_789L),
        )
        val prefs = BackupPreferencesSnapshot(
            outputTrimGainDb = -3.0,
            outputTrimCeilingDb = -1.0,
            segmentSwitchFadeOutMs = 500,
            perfectBeatEnabled = true,
            liveVisualizerEnabled = true,
            dspConfig = MasterDspStateConfig(
                eqEnabled = true,
                bandsDb = EqualizerPreset.BASS_BOOST.bandsDb,
                preset = EqualizerPreset.BASS_BOOST,
                bassBoostPercent = 65,
                loudnessDb = 3.5f,
            ),
            rateConfig = PlaybackRateConfig(speed = 1.15f, pitchSemitones = 2.0f, preservePitch = true),
        )

        val json = UserBackupManager.exportToJson(
            playlists = playlists,
            favorites = favorites,
            presets = presets,
            customRoutes = customRoutes,
            recentCourses = recent,
            playStats = stats,
            preferences = prefs,
            appVersion = "0.1.0-alpha25",
            exportedAtEpochMs = 999_000L,
        )
        val parsed = UserBackupManager.parseBackupJson(json).getOrThrow()

        assertEquals(playlists, parsed.playlists)
        assertEquals(favorites, parsed.favorites.toSet())
        assertEquals(presets.single().name, parsed.presets.single().name)
        assertEquals(presets.single().steps, parsed.presets.single().steps)
        assertEquals(customRoutes.single().stops, parsed.customRoutes.single().stops)
        assertEquals(recent, parsed.recentCourses)
        assertEquals(12, parsed.playStats.single().playCount)
        assertTrue(parsed.preferences.liveVisualizerEnabled)
        assertTrue(parsed.preferences.perfectBeatEnabled)
        assertEquals(500, parsed.preferences.segmentSwitchFadeOutMs)
        assertTrue(parsed.preferences.dspConfig.eqEnabled)
        assertEquals(65, parsed.preferences.dspConfig.bassBoostPercent)
        assertTrue(abs(parsed.preferences.dspConfig.loudnessDb - 3.5f) < 0.05f)
        assertTrue(abs(parsed.preferences.rateConfig.speed - 1.15f) < 0.01f)

        val m3u = UserBackupManager.exportPlaylistsToM3u(playlists) { id ->
            when (id) {
                "MarioBrosCircuit" -> "Mario Bros. Circuit" to 145
                "jb:ost_01" -> "Title Screen" to 92
                else -> id to -1
            }
        }
        assertTrue(m3u.startsWith("#EXTM3U"))
        assertTrue("#PLAYLIST:Favorites Night" in m3u)
        assertTrue("#EXTINF:145,Mario Bros. Circuit" in m3u)
        assertTrue("mkwplayer://track/MarioBrosCircuit" in m3u)
        assertTrue("#EXTINF:92,Title Screen" in m3u)
    }

    @Test
    fun sleepAndInteractiveTimersTickPauseAndCancelAccurately() {
        var nowMs = 10_000L
        val engine = PlaybackTimerEngine(clockMs = { nowMs })

        // Sleep countdown + extend
        engine.startSleepCountdown(durationMs = 5_000L)
        nowMs += 2_000L
        var out = engine.tick(isSessionActive = true, isPlaying = true, isInteractive = false)
        assertFalse(out.sleepExpired)
        assertEquals(SleepTimerMode.COUNTDOWN, engine.sleep.value.mode)
        assertEquals(3_000L, engine.sleep.value.remainingMs)

        engine.extendSleep(5_000L)
        assertEquals(8_000L, engine.sleep.value.remainingMs)

        nowMs += 8_500L
        out = engine.tick(isSessionActive = true, isPlaying = true, isInteractive = false)
        assertTrue(out.sleepExpired)
        assertEquals(SleepTimerMode.OFF, engine.sleep.value.mode)

        // Sleep after current track
        engine.startSleepAfterCurrentTrack()
        assertEquals(SleepTimerMode.AFTER_CURRENT_TRACK, engine.sleep.value.mode)
        assertTrue(engine.consumeAfterCurrentTrackOnNaturalEnd())
        assertEquals(SleepTimerMode.OFF, engine.sleep.value.mode)
        assertFalse(engine.consumeAfterCurrentTrackOnNaturalEnd())

        // Interactive scheduled switch/stop timer: pauses when transport pauses, resumes, fires action
        engine.startInteractiveTimer(
            durationMs = 4_000L,
            action = InteractiveTimedAction.FadeStop(fadeSeconds = 3),
            courseKey = "RainbowRoad",
            currentlyPlaying = true,
        )
        nowMs += 1_500L
        out = engine.tick(isSessionActive = true, isPlaying = true, isInteractive = true)
        assertNull(out.triggeredInteractiveAction)
        assertEquals(2_500L, engine.interactive.value.remainingMs)
        assertFalse(engine.interactive.value.pausedByTransport)

        // Pause transport for 60 seconds: timer must freeze remainingMs
        nowMs += 60_000L
        out = engine.tick(isSessionActive = true, isPlaying = false, isInteractive = true)
        assertNull(out.triggeredInteractiveAction)
        assertTrue(engine.interactive.value.pausedByTransport)
        assertEquals(2_500L, engine.interactive.value.remainingMs)

        // Switching course cancels interactive timer
        engine.syncInteractiveContext(isInteractiveActive = true, courseKey = "MooMooMeadows")
        assertFalse(engine.interactive.value.active)

        // Arm again and fire ToggleFast action
        engine.startInteractiveTimer(
            durationMs = 3_000L,
            action = InteractiveTimedAction.ToggleFast,
            courseKey = "MooMooMeadows",
            currentlyPlaying = true,
        )
        nowMs += 3_200L
        out = engine.tick(isSessionActive = true, isPlaying = true, isInteractive = true)
        assertEquals(InteractiveTimedAction.ToggleFast, out.triggeredInteractiveAction)
        assertFalse(engine.interactive.value.active)
    }
}
