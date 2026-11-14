package com.mylittleblover.mkwplayer.library

import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.io.path.createTempDirectory
import kotlin.test.*

class DailySelectionStoreTest {
    @Test fun aFreshProcessKeepsTheSameDaySelectionWhenNewMusicArrives() {
        val root = createTempDirectory("mkw-daily-").toFile()
        try {
            val file = File(root, "daily.json")
            val day = LocalDate.of(2026, 11, 14)
            val initial = (0 until 20).map { RecommendationCandidate("course:$it", RecommendationKind.COURSE, true) }
            val first = DailySelectionStore(file).select(day, initial).getOrThrow()
            val newMusic = (0 until 20).map { RecommendationCandidate("road:$it", RecommendationKind.FREE_DRIVING, true) }
            assertEquals(first, DailySelectionStore(file).select(day, initial + newMusic).getOrThrow())
            val next = DailySelectionStore(file).select(day.plusDays(1), initial + newMusic).getOrThrow()
            assertEquals(5, next.count { it.startsWith("road:") })
        } finally { root.deleteRecursively() }
    }
    @Test fun nextMidnightUsesCalendarDaysAcrossDaylightSavingChanges() {
        val zone = ZoneId.of("America/New_York")
        assertEquals(23L * 60 * 60 * 1000, millisUntilNextMidnight(ZonedDateTime.of(2026, 3, 8, 0, 0, 0, 0, zone)))
        assertEquals(25L * 60 * 60 * 1000, millisUntilNextMidnight(ZonedDateTime.of(2026, 11, 1, 0, 0, 0, 0, zone)))
        assertEquals(1000L, millisUntilNextMidnight(ZonedDateTime.of(2026, 11, 14, 23, 59, 59, 0, zone)))
    }
}
