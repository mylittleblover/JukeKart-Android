package com.mylittleblover.mkwplayer.interactive

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ResearchTimingTest {
    @Test fun westCityStartsBothTargetsBeforeThePreExit() {
        val p = ResearchTiming.ordinary(2_936_953, 49_921, 49_922, 5_625)
        assertEquals(2_887_032, p.bridgeStart)
        assertEquals(2_931_329, p.fullStart)
        assertEquals(2_936_954, p.bridgeHandover)
    }
    @Test fun absentBridgeMarkerIsNotZero() {
        assertFailsWith<IllegalArgumentException> { ResearchTiming.ordinary(1_000_000, 0, null, 38_400) }
    }
    @Test fun startsCanBeEarlierThanTheBridgeForLayeredCandidates() {
        val p = ResearchTiming.ordinary(1_000_000, 0, 0, 9_389)
        assertEquals(990_611, p.fullStart)
    }
    @Test fun integerSourceFramesRemainExactBeyondThirtyTwoBits() {
        val p = ResearchTiming.ordinary(5_000_000_000, 49_921, 49_922, 5_625)
        assertEquals(4_999_994_376, p.fullStart)
    }
    @Test fun skipEntryCannotUseFullFromZeroFormula() {
        assertFailsWith<IllegalArgumentException> { ResearchTiming.ordinary(1_000_000, 0, 0, 0, 420_438) }
    }
}
