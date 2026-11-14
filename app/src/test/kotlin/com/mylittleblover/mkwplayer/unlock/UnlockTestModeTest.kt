package com.mylittleblover.mkwplayer.unlock

import com.mylittleblover.mkwplayer.player.UnlockUi
import kotlin.test.*

class UnlockTestModeTest {
    private val natural = UnlockUi("3 of 8 stages heard", "5 still to hear", "rule", true,
        false, false, false, false, false, "hidden")

    @Test fun normalPreservesTheNaturalStateIncludingMissingState() {
        assertSame(natural, UnlockTestMode.NORMAL.apply(natural))
        assertNull(UnlockTestMode.NORMAL.apply(null))
    }
    @Test fun forcedUnlockRevealsCoursesWithoutGrantingUnearnedCeremony() {
        val projected = requireNotNull(UnlockTestMode.FORCE_UNLOCKED.apply(natural))
        assertEquals(natural.copy(visible = true, hiddenReason = null), projected)
        assertFalse(natural.visible)
    }
    @Test fun forcedLockHidesAnEarnedCupWithoutErasingNaturalCeremonyState() {
        val earned = natural.copy(visible = true, canEnter = true, ceremonyCompleted = true)
        val projected = requireNotNull(UnlockTestMode.FORCE_LOCKED.apply(earned))
        assertFalse(projected.visible)
        assertFalse(projected.canEnter)
        assertTrue(projected.ceremonyCompleted)
        assertSame(earned, UnlockTestMode.NORMAL.apply(earned))
    }
    @Test fun overridesWorkBeforeNaturalProgressExists() {
        val unlocked = requireNotNull(UnlockTestMode.FORCE_UNLOCKED.apply(null))
        assertTrue(unlocked.visible)
        assertFalse(unlocked.canEnter)
        assertFalse(unlocked.ceremonyCompleted)
        assertFalse(unlocked.congratulationOwed)
        assertFalse(requireNotNull(UnlockTestMode.FORCE_LOCKED.apply(null)).visible)
    }
}
