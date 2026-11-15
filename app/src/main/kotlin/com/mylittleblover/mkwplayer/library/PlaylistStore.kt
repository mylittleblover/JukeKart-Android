package com.mylittleblover.mkwplayer.library

import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** The ordered list stores occurrences, so the same course may be added more than once. */
internal data class PlaylistEntryConfig(
    val presetId: String? = null,
    val nativeTransitionEnabled: Boolean? = null,
) {
    val isDefault: Boolean
        get() = presetId == null && nativeTransitionEnabled == null
}

internal data class UserPlaylist(
    val id: String,
    val name: String,
    val courseIds: List<String>,
    val nativeTransitionDefault: Boolean = false,
    val entryConfigs: List<PlaylistEntryConfig> = emptyList(),
) {
    fun configAt(index: Int): PlaylistEntryConfig =
        entryConfigs.getOrNull(index) ?: PlaylistEntryConfig()

    fun effectiveNativeTransition(index: Int): Boolean =
        configAt(index).nativeTransitionEnabled ?: nativeTransitionDefault
}

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
                val courseIds = (0 until values.length()).map { values.getString(it).also { code -> require(code.isNotBlank()) } }
                val nativeDefault = row.optBoolean("nativeTransitionDefault", false)
                val cfgArr = row.optJSONArray("entryConfigs")
                val configs = if (cfgArr != null && cfgArr.length() > 0) {
                    (0 until courseIds.size).map { idx ->
                        val cObj = cfgArr.optJSONObject(idx)
                        if (cObj == null) {
                            PlaylistEntryConfig()
                        } else {
                            val presetId = if (cObj.has("presetId") && !cObj.isNull("presetId")) {
                                cObj.getString("presetId").takeIf { it.isNotBlank() }
                            } else null
                            val nativeOverride = if (cObj.has("nativeTransitionEnabled") && !cObj.isNull("nativeTransitionEnabled")) {
                                cObj.getBoolean("nativeTransitionEnabled")
                            } else null
                            PlaylistEntryConfig(presetId, nativeOverride)
                        }
                    }.let { list -> if (list.all { it.isDefault }) emptyList() else list }
                } else {
                    emptyList()
                }
                UserPlaylist(id, name, courseIds, nativeDefault, configs)
            }
            require(result.map { it.id }.distinct().size == result.size)
            result
        }
    }
    private fun update(change: (List<UserPlaylist>) -> List<UserPlaylist>): Result<List<UserPlaylist>> = synchronized(MetadataFile.lock(file)) {
        runCatching {
            val next = change(read().getOrThrow())
            val rows = JSONArray()
            next.forEach { playlist ->
                val obj = JSONObject()
                    .put("id", playlist.id)
                    .put("name", playlist.name)
                    .put("courses", JSONArray(playlist.courseIds))
                if (playlist.nativeTransitionDefault) {
                    obj.put("nativeTransitionDefault", true)
                }
                val normalizedCfg = normalizeConfigs(playlist.courseIds.size, playlist.entryConfigs)
                if (normalizedCfg.isNotEmpty()) {
                    val cfgArr = JSONArray()
                    normalizedCfg.forEach { c ->
                        val cObj = JSONObject()
                        if (c.presetId != null) cObj.put("presetId", c.presetId)
                        if (c.nativeTransitionEnabled != null) cObj.put("nativeTransitionEnabled", c.nativeTransitionEnabled)
                        cfgArr.put(cObj)
                    }
                    obj.put("entryConfigs", cfgArr)
                }
                rows.put(obj)
            }
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
        current.map { list ->
            if (list.id != id) list else {
                val nextCourses = list.courseIds + courseId
                val nextConfigs = if (list.entryConfigs.isEmpty()) {
                    emptyList()
                } else {
                    normalizeConfigs(nextCourses.size, paddedConfigs(list) + PlaylistEntryConfig())
                }
                list.copy(courseIds = nextCourses, entryConfigs = nextConfigs)
            }
        }
    }
    fun removeCourse(id: String, index: Int): Result<List<UserPlaylist>> = update { current ->
        require(current.any { it.id == id && index in it.courseIds.indices })
        current.map { list ->
            if (list.id != id) list else {
                val nextCourses = list.courseIds.filterIndexed { i, _ -> i != index }
                val nextConfigs = if (list.entryConfigs.isEmpty()) {
                    emptyList()
                } else {
                    normalizeConfigs(nextCourses.size, paddedConfigs(list).filterIndexed { i, _ -> i != index })
                }
                list.copy(courseIds = nextCourses, entryConfigs = nextConfigs)
            }
        }
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
                val nextConfigs = if (list.entryConfigs.isEmpty()) {
                    emptyList()
                } else {
                    val cfgs = paddedConfigs(list).toMutableList()
                    cfgs.add(to, cfgs.removeAt(from))
                    normalizeConfigs(entries.size, cfgs)
                }
                list.copy(courseIds = entries, entryConfigs = nextConfigs)
            }
        }
    }
    fun setNativeTransitionDefault(id: String, enabled: Boolean): Result<List<UserPlaylist>> = update { current ->
        require(current.any { it.id == id })
        current.map { list -> if (list.id == id) list.copy(nativeTransitionDefault = enabled) else list }
    }
    fun setEntryPreset(id: String, index: Int, presetId: String?): Result<List<UserPlaylist>> = update { current ->
        require(current.any { it.id == id && index in it.courseIds.indices })
        current.map { list ->
            if (list.id != id) list else {
                val cfgs = paddedConfigs(list).toMutableList()
                cfgs[index] = cfgs[index].copy(presetId = presetId?.takeIf { it.isNotBlank() })
                list.copy(entryConfigs = normalizeConfigs(list.courseIds.size, cfgs))
            }
        }
    }
    fun setEntryNativeTransition(id: String, index: Int, enabled: Boolean?): Result<List<UserPlaylist>> = update { current ->
        require(current.any { it.id == id && index in it.courseIds.indices })
        current.map { list ->
            if (list.id != id) list else {
                val cfgs = paddedConfigs(list).toMutableList()
                cfgs[index] = cfgs[index].copy(nativeTransitionEnabled = enabled)
                list.copy(entryConfigs = normalizeConfigs(list.courseIds.size, cfgs))
            }
        }
    }
    fun playlistsReferencingPreset(presetId: String): Result<List<UserPlaylist>> =
        read().map { list ->
            list.filter { playlist -> playlist.entryConfigs.any { it.presetId == presetId } }
        }
    fun clearPresetReferences(presetId: String): Result<List<UserPlaylist>> = update { current ->
        current.map { list ->
            if (list.entryConfigs.none { it.presetId == presetId }) list else {
                val next = list.entryConfigs.map { c ->
                    if (c.presetId == presetId) c.copy(presetId = null) else c
                }
                list.copy(entryConfigs = normalizeConfigs(list.courseIds.size, next))
            }
        }
    }
    fun delete(id: String): Result<List<UserPlaylist>> = update { current ->
        require(current.any { it.id == id })
        current.filterNot { it.id == id }
    }
    private fun paddedConfigs(playlist: UserPlaylist): List<PlaylistEntryConfig> =
        (0 until playlist.courseIds.size).map { i -> playlist.configAt(i) }
    private fun normalizeConfigs(size: Int, configs: List<PlaylistEntryConfig>): List<PlaylistEntryConfig> {
        val clipped = (0 until size).map { i -> configs.getOrNull(i) ?: PlaylistEntryConfig() }
        return if (clipped.all { it.isDefault }) emptyList() else clipped
    }
}
