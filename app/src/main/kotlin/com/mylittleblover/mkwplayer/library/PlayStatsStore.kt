package com.mylittleblover.mkwplayer.library

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Collections

internal enum class TrackSortOrder {
    DEFAULT,
    NAME_ASC,
    DURATION_DESC,
    MOST_PLAYED,
    OST_INDEX,
}

internal data class TrackPlayStat(
    val trackId: String,
    val playCount: Int,
    val lastPlayedEpochMs: Long,
)

/**
 * Atomic JSON store tracking per-track play counts and last-played timestamps
 * across courses, jukebox tracks, two-state retro/battle music, and extras.
 */
internal class PlayStatsStore(private val file: File) {

    fun read(): Result<Map<String, TrackPlayStat>> = synchronized(MetadataFile.lock(file)) {
        runCatching { decode() }
    }

    fun recordPlay(
        trackId: String,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): Result<Map<String, TrackPlayStat>> = synchronized(MetadataFile.lock(file)) {
        runCatching {
            val cleanId = trackId.trim()
            require(cleanId.isNotEmpty()) { "trackId must not be blank" }
            val current = decode().toMutableMap()
            val prev = current[cleanId]
            current[cleanId] = TrackPlayStat(
                trackId = cleanId,
                playCount = (prev?.playCount ?: 0) + 1,
                lastPlayedEpochMs = nowEpochMs.coerceAtLeast(prev?.lastPlayedEpochMs ?: 0L),
            )
            writeMap(current)
            Collections.unmodifiableMap(current.toMap())
        }
    }

    fun merge(incoming: Map<String, TrackPlayStat>): Result<Map<String, TrackPlayStat>> =
        synchronized(MetadataFile.lock(file)) {
            runCatching {
                val merged = decode().toMutableMap()
                incoming.values.forEach { stat ->
                    val existing = merged[stat.trackId]
                    merged[stat.trackId] = if (existing == null) {
                        stat
                    } else {
                        TrackPlayStat(
                            trackId = stat.trackId,
                            playCount = maxOf(existing.playCount, stat.playCount),
                            lastPlayedEpochMs = maxOf(existing.lastPlayedEpochMs, stat.lastPlayedEpochMs),
                        )
                    }
                }
                writeMap(merged)
                Collections.unmodifiableMap(merged.toMap())
            }
        }

    fun replaceAll(next: Map<String, TrackPlayStat>): Result<Map<String, TrackPlayStat>> =
        synchronized(MetadataFile.lock(file)) {
            runCatching {
                val clean = next.filter { (k, v) -> k.isNotBlank() && v.playCount > 0 }
                writeMap(clean)
                Collections.unmodifiableMap(clean.toMap())
            }
        }

    private fun decode(): Map<String, TrackPlayStat> {
        if (!file.exists()) return emptyMap()
        val root = JSONObject(file.readText())
        require(root.getInt("schemaVersion") == SCHEMA_VERSION)
        val arr = root.optJSONArray("stats") ?: JSONArray()
        val map = linkedMapOf<String, TrackPlayStat>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val id = obj.getString("trackId").trim()
            if (id.isEmpty()) continue
            val count = obj.optInt("playCount", 0).coerceAtLeast(0)
            val last = obj.optLong("lastPlayedEpochMs", 0L).coerceAtLeast(0L)
            if (count > 0) {
                map[id] = TrackPlayStat(id, count, last)
            }
        }
        return Collections.unmodifiableMap(map)
    }

    private fun writeMap(map: Map<String, TrackPlayStat>) {
        val arr = JSONArray()
        map.values
            .filter { it.playCount > 0 }
            .sortedByDescending { it.playCount }
            .forEach { stat ->
                arr.put(
                    JSONObject()
                        .put("trackId", stat.trackId)
                        .put("playCount", stat.playCount)
                        .put("lastPlayedEpochMs", stat.lastPlayedEpochMs),
                )
            }
        MetadataFile.write(
            file,
            JSONObject()
                .put("schemaVersion", SCHEMA_VERSION)
                .put("stats", arr)
                .toString(),
        )
    }

    companion object {
        private const val SCHEMA_VERSION = 1
    }
}
