package com.mylittleblover.mkwplayer.library

import java.time.LocalDate
import kotlin.test.*

class DailyRecommendationsTest {
    private val day = LocalDate.of(2026, 11, 14)
    private fun pool(kind: RecommendationKind, count: Int, available: Boolean = true) =
        (0 until count).map { RecommendationCandidate("${kind.name}:$it", kind, available) }
    @Test fun sufficientEligibleMusicProducesTenUniqueBalancedChoices() {
        val candidates = pool(RecommendationKind.COURSE, 20) + pool(RecommendationKind.FREE_DRIVING, 20)
        val result = DailyRecommendations.select(day, candidates, emptyList())
        assertEquals(10, result.size)
        assertEquals(10, result.toSet().size)
        assertEquals(5, result.count { it.startsWith("COURSE:") })
        assertEquals(5, result.count { it.startsWith("FREE_DRIVING:") })
    }
    @Test fun missingMusicIsNeverRecommendedAndAvailableFamilyFillsTheShortfall() {
        val courses = pool(RecommendationKind.COURSE, 20)
        val missing = pool(RecommendationKind.FREE_DRIVING, 20, available = false)
        val result = DailyRecommendations.select(day, courses + missing, emptyList())
        assertEquals(10, result.size)
        assertTrue(result.all { it.startsWith("COURSE:") })
        assertEquals(3, DailyRecommendations.select(day, courses.take(3) + missing, emptyList()).size)
        assertTrue(DailyRecommendations.select(day, missing, emptyList()).isEmpty())
    }
    @Test fun orderIsIndependentOfInputOrderAndChangesWithTheLocalDate() {
        val candidates = pool(RecommendationKind.COURSE, 30) + pool(RecommendationKind.FREE_DRIVING, 30)
        val first = DailyRecommendations.select(day, candidates, emptyList())
        assertEquals(first, DailyRecommendations.select(day, candidates.reversed(), emptyList()))
        assertNotEquals(first, DailyRecommendations.select(day.plusDays(1), candidates, emptyList()))
    }
    @Test fun duplicateSourcesDoNotBecomeDuplicateRecommendations() {
        val candidates = pool(RecommendationKind.COURSE, 12)
        val result = DailyRecommendations.select(day, candidates + candidates.reversed(), emptyList())
        assertEquals(10, result.size)
        assertEquals(10, result.toSet().size)
    }
    @Test fun sameDayKeepsExistingOrderAndOnlyReplacesUnavailableItems() {
        val candidates = pool(RecommendationKind.COURSE, 20)
        val first = DailyRecommendations.select(day, candidates, emptyList())
        val expanded = candidates + pool(RecommendationKind.FREE_DRIVING, 20)
        assertEquals(first, DailyRecommendations.select(day, expanded, first))
        val missing = first[3]
        val reduced = expanded.filterNot { it.id == missing }
        val repaired = DailyRecommendations.select(day, reduced, first)
        assertEquals(first.filterNot { it == missing }, repaired.take(9))
        assertEquals(10, repaired.size)
        assertFalse(missing in repaired)
    }
}
