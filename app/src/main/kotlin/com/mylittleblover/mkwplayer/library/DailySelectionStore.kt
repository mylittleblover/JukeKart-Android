package com.mylittleblover.mkwplayer.library

import java.io.File
import java.time.LocalDate
import java.time.ZonedDateTime
import org.json.JSONArray
import org.json.JSONObject

internal class DailySelectionStore(private val file: File) {
    fun select(day: LocalDate, candidates: List<RecommendationCandidate>): Result<List<String>> = synchronized(MetadataFile.lock(file)) {
        runCatching {
            val saved = if (file.exists()) JSONObject(file.readText()).also { require(it.getInt("schemaVersion") == 1) } else null
            val previous = if (saved != null && LocalDate.parse(saved.getString("day")) == day) {
                val ids = saved.getJSONArray("ids")
                (0 until ids.length()).map { ids.getString(it) }
            } else emptyList()
            val selected = DailyRecommendations.select(day, candidates, previous)
            if (saved == null || saved.getString("day") != day.toString() || previous != selected) {
                MetadataFile.write(file, JSONObject().put("schemaVersion", 1).put("day", day.toString()).put("ids", JSONArray(selected)).toString())
            }
            selected
        }
    }
}

internal fun millisUntilNextMidnight(now: ZonedDateTime): Long =
    (now.toLocalDate().plusDays(1).atStartOfDay(now.zone).toInstant().toEpochMilli() - now.toInstant().toEpochMilli()).coerceAtLeast(1L)
