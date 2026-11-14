package com.mylittleblover.mkwplayer.library

import java.security.MessageDigest
import java.time.LocalDate
import java.util.Collections

internal enum class RecommendationKind { COURSE, FREE_DRIVING }
internal data class RecommendationCandidate(val id: String, val kind: RecommendationKind, val available: Boolean)

internal object DailyRecommendations {
    const val LIMIT = 10
    fun select(day: LocalDate, eligible: List<RecommendationCandidate>, previous: List<String>): List<String> {
        val pool = eligible.filter { it.available && it.id.isNotBlank() }.groupBy { it.id }
            .values.filter { rows -> rows.map { it.kind }.distinct().size == 1 }.map { it.first() }
            .sortedWith(compareBy<RecommendationCandidate> { rank(day, it.id) }.thenBy { it.id })
        val byId = pool.associateBy { it.id }
        val selected = previous.distinct().filter { it in byId }.take(LIMIT).toMutableList()
        val remaining = pool.filterNot { it.id in selected }.toMutableList()
        var preferred = if (day.toEpochDay() % 2L == 0L) RecommendationKind.COURSE else RecommendationKind.FREE_DRIVING
        while (selected.size < LIMIT && remaining.isNotEmpty()) {
            val courseCount = selected.count { byId.getValue(it).kind == RecommendationKind.COURSE }
            val drivingCount = selected.size - courseCount
            val desired = when {
                courseCount < 5 && drivingCount < 5 -> when {
                    courseCount < drivingCount -> RecommendationKind.COURSE
                    drivingCount < courseCount -> RecommendationKind.FREE_DRIVING
                    else -> preferred
                }
                courseCount < 5 -> RecommendationKind.COURSE
                else -> RecommendationKind.FREE_DRIVING
            }
            val next = remaining.firstOrNull { it.kind == desired } ?: remaining.first()
            selected += next.id
            remaining.remove(next)
            preferred = if (next.kind == RecommendationKind.COURSE) RecommendationKind.FREE_DRIVING else RecommendationKind.COURSE
        }
        return Collections.unmodifiableList(selected.toList())
    }
    private fun rank(day: LocalDate, id: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("$day|$id".toByteArray(Charsets.UTF_8))
        val hex = "0123456789abcdef"
        return buildString(digest.size * 2) {
            for (value in digest) {
                val byte = value.toInt() and 255
                append(hex[byte ushr 4]); append(hex[byte and 15])
            }
        }
    }
}
