package com.mylittleblover.mkwplayer.library

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** A recent entry is recorded only after the actual playback session begins. */
internal class RecentCourseStore(private val file: File) {
    fun read(): Result<List<String>> = synchronized(MetadataFile.lock(file)) {
        runCatching {
            if (!file.exists()) return@runCatching emptyList()
            val root = JSONObject(file.readText())
            require(root.getInt("schemaVersion") == 1)
            val rows = root.getJSONArray("courseIds")
            val ids = (0 until rows.length()).map { rows.getString(it).also { id -> require(id.isNotBlank()) } }
            require(ids.distinct().size == ids.size)
            ids
        }
    }
    fun record(id: String): Result<List<String>> = synchronized(MetadataFile.lock(file)) {
        runCatching {
            require(id.isNotBlank())
            val next = (listOf(id) + read().getOrThrow().filterNot { it == id }).take(50)
            MetadataFile.write(file, JSONObject().put("schemaVersion", 1).put("courseIds", JSONArray(next)).toString())
            next
        }
    }

    fun replaceAll(courseIds: List<String>): Result<List<String>> = synchronized(MetadataFile.lock(file)) {
        runCatching {
            val next = courseIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(50)
            MetadataFile.write(file, JSONObject().put("schemaVersion", 1).put("courseIds", JSONArray(next)).toString())
            next
        }
    }

    fun mergeAll(courseIds: List<String>): Result<List<String>> = synchronized(MetadataFile.lock(file)) {
        runCatching {
            val existing = read().getOrThrow()
            val incoming = courseIds.map { it.trim() }.filter { it.isNotEmpty() }
            val next = (existing + incoming).distinct().take(50)
            MetadataFile.write(file, JSONObject().put("schemaVersion", 1).put("courseIds", JSONArray(next)).toString())
            next
        }
    }
}
