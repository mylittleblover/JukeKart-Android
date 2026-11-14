package com.mylittleblover.mkwplayer.library

import java.io.File
import java.util.Collections
import org.json.JSONArray
import org.json.JSONObject

internal class FavoriteStore(private val file: File) {
    fun read(): Result<Set<String>> = synchronized(MetadataFile.lock(file)) {
        runCatching {
            if (!file.exists()) return@runCatching emptySet()
            check(file.isFile) { "Favorites record is not a file" }
            val root = JSONObject(file.readText())
            require(root.getInt("schemaVersion") == 1)
            val ids = root.getJSONArray("ids")
            val result = linkedSetOf<String>()
            for (index in 0 until ids.length()) result += ids.getString(index).also { require(it.isNotBlank()) }
            Collections.unmodifiableSet(result)
        }
    }
    fun toggle(id: String): Result<Set<String>> = synchronized(MetadataFile.lock(file)) {
        runCatching {
            require(id.isNotBlank())
            val updated = read().getOrThrow().toMutableSet()
            if (!updated.remove(id)) updated.add(id)
            val root = JSONObject().put("schemaVersion", 1).put("ids", JSONArray(updated.toList()))
            MetadataFile.write(file, root.toString())
            Collections.unmodifiableSet(updated.toSet())
        }
    }
}
