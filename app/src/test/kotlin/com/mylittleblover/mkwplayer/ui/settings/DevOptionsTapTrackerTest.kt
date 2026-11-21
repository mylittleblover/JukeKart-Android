package com.mylittleblover.mkwplayer.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DevOptionsTapTrackerTest {

    @Test
    fun unlocksOnSeventhTapWithinWindow() {
        val tracker = DevOptionsTapTracker(requiredTaps = 7, windowMs = 2_500L)
        assertIs<DevOptionsTapTracker.Result.Ignored>(tracker.tap(1_000L))
        assertIs<DevOptionsTapTracker.Result.Ignored>(tracker.tap(1_100L))
        assertEquals(DevOptionsTapTracker.Result.Progress(4), tracker.tap(1_200L))
        assertEquals(DevOptionsTapTracker.Result.Progress(3), tracker.tap(1_300L))
        assertEquals(DevOptionsTapTracker.Result.Progress(2), tracker.tap(1_400L))
        assertEquals(DevOptionsTapTracker.Result.Progress(1), tracker.tap(1_500L))
        assertFalse(tracker.unlocked)
        assertIs<DevOptionsTapTracker.Result.UnlockedNow>(tracker.tap(1_600L))
        assertTrue(tracker.unlocked)
        assertIs<DevOptionsTapTracker.Result.AlreadyUnlocked>(tracker.tap(1_700L))
    }

    @Test
    fun gapExceedingWindowResetsTapStreak() {
        val tracker = DevOptionsTapTracker(requiredTaps = 7, windowMs = 2_500L)
        repeat(6) { idx -> tracker.tap(1_000L + idx * 100L) }
        assertFalse(tracker.unlocked)

        // Pause longer than windowMs -> resets count to 1.
        assertIs<DevOptionsTapTracker.Result.Ignored>(tracker.tap(5_000L))
        assertIs<DevOptionsTapTracker.Result.Ignored>(tracker.tap(5_200L))
        assertEquals(DevOptionsTapTracker.Result.Progress(4), tracker.tap(5_400L))
        assertFalse(tracker.unlocked)
    }

    @Test
    fun seedUnlockedImmediatelyMarksAlreadyUnlocked() {
        val tracker = DevOptionsTapTracker()
        tracker.seedUnlocked(true)
        assertTrue(tracker.unlocked)
        assertIs<DevOptionsTapTracker.Result.AlreadyUnlocked>(tracker.tap(100L))
    }
}
