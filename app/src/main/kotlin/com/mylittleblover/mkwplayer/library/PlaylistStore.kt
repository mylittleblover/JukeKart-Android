package com.mylittleblover.mkwplayer.library

import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** The ordered list stores occurrences, so the same course may be added more than once. */
internal data class UserPlaylist(val id: String, val name: String, val courseIds: List<String>)

internal class PlaylistStore(private val file: File) {
    fun read(): Result<List<UserPlaylist>> = synchronized(MetadataFile.lock(file)) {
        runCatching {
            if (!file.exists()) return@runCatching emptyList()
            val root = JSONObject(file.readText())
            require(root.getInt("schemaVersion") == 1)
            val rows = root.getJSONArray("playlists")
            val result = (0 until rows.length()).map { i ->
                val row = rows.getJSONObject(i)
                val id = row.getString("id").also { require(it.isNotBlank()) }
                val name = row.getString("name").also { require(it.isNotBlank()) }
                val values = row.getJSONArray("courses")
                UserPlaylist(id, name, (0 until values.length()).map { values.getString(it).also { code -> require(code.isNotBlank()) } })
            }
            require(result.map { it.id }.distinct().size == result.size)
            result
        }
    }
    private fun update(change: (List<UserPlaylist>) -> List<UserPlaylist>): Result<List<UserPlaylist>> = synchronized(MetadataFile.lock(file)) {
        runCatching {
            val next = change(read().getOrThrow())
            val rows = JSONArray()
            next.forEach { rows.put(JSONObject().put("id", it.id).put("name", it.name).put("courses", JSONArray(it.courseIds))) }
            MetadataFile.write(file, JSONObject().put("schemaVersion", 1).put("playlists", rows).toString())
            next
        }
    }
    fun create(name: String): Result<List<UserPlaylist>> = update { current ->
        val title = name.trim().also { require(it.isNotEmpty() && it.length <= 120) }
        current + UserPlaylist(UUID.randomUUID().toString(), title, emptyList())
    }
    fun addCourse(id: String, courseId: String): Result<List<UserPlaylist>> = update { current ->
        require(courseId.isNotBlank() && current.any { it.id == id })
        current.map { if (it.id == id) it.copy(courseIds = it.courseIds + courseId) else it }
    }
    fun removeCourse(id: String, index: Int): Result<List<UserPlaylist>> = update { current ->
        require(current.any { it.id == id && index in it.courseIds.indices })
        current.map { if (it.id == id) it.copy(courseIds = it.courseIds.filterIndexed { i, _ -> i != index }) else it }
    }
    fun rename(id: String, name: String): Result<List<UserPlaylist>> = update { current ->
        val title = name.trim().also { require(it.isNotEmpty() && it.length <= 120) }
        require(current.any { it.id == id })
        current.map { if (it.id == id) it.copy(name = title) else it }
    }
    fun moveCourse(id: String, from: Int, to: Int): Result<List<UserPlaylist>> = update { current ->
        require(current.any { it.id == id && from in it.courseIds.indices && to in it.courseIds.indices })
        current.map { list ->
            if (list.id != id) list else {
                val entries = list.courseIds.toMutableList()
                entries.add(to, entries.removeAt(from))
                list.copy(courseIds = entries)
            }
        }
    }
    fun delete(id: String): Result<List<UserPlaylist>> = update { current ->
        require(current.any { it.id == id })
        current.filterNot { it.id == id }
    }
}
