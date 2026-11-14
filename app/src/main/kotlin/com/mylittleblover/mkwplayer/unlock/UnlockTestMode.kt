package com.mylittleblover.mkwplayer.unlock

import com.mylittleblover.mkwplayer.player.UnlockUi

/** A presentation override, deliberately separate from durable listening and ceremony progress. */
internal enum class UnlockTestMode {
    NORMAL,
    FORCE_UNLOCKED,
    FORCE_LOCKED;

    fun apply(natural: UnlockUi?): UnlockUi? {
        if (this == NORMAL) return natural
        val visible = this == FORCE_UNLOCKED
        val base = natural ?: UnlockUi(
            progress = "",
            remaining = null,
            ruleNotice = "",
            offered = false,
            ceremonyPlaying = false,
            ceremonyCompleted = false,
            congratulationOwed = false,
            canEnter = false,
            visible = false,
            hiddenReason = null,
        )
        return base.copy(
            visible = visible,
            canEnter = visible && base.canEnter,
            hiddenReason = if (visible) null else base.hiddenReason,
        )
    }
}
