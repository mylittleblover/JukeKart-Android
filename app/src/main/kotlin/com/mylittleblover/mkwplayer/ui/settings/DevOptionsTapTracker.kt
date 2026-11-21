package com.mylittleblover.mkwplayer.ui.settings

/**
 * Pure tap-window tracker for unlocking developer options after repeated taps on the version row.
 */
internal class DevOptionsTapTracker(
    private val requiredTaps: Int = 7,
    private val windowMs: Long = 2_500L,
) {
    sealed interface Result {
        data object Ignored : Result
        data class Progress(val remaining: Int) : Result
        data object UnlockedNow : Result
        data object AlreadyUnlocked : Result
    }

    private var count: Int = 0
    private var lastTapMs: Long = 0L
    var unlocked: Boolean = false
        private set

    fun seedUnlocked(value: Boolean) {
        if (value) unlocked = true
    }

    fun tap(nowMs: Long): Result {
        if (unlocked) return Result.AlreadyUnlocked
        if (count > 0 && nowMs - lastTapMs > windowMs) {
            count = 0
        }
        lastTapMs = nowMs
        count += 1
        return if (count >= requiredTaps) {
            unlocked = true
            Result.UnlockedNow
        } else {
            val remaining = requiredTaps - count
            if (count >= 3) Result.Progress(remaining) else Result.Ignored
        }
    }
}
