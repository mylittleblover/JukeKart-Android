package com.mylittleblover.mkwplayer.library

import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

internal object PlaylistConstants {
    /** Virtual identifier for the fixed/pinned My Favorites playlist in My Playlists. */
    const val PINNED_FAVORITES_ID = "__system_favorites__"
}

internal sealed interface PlaylistItemRef {
    val rawId: String
    val raw: String get() = rawId

    data class Course(val courseCode: String) : PlaylistItemRef {
        override val rawId: String = courseCode
        val logicalCode: String get() = courseCode.removeSuffix("_00")
    }

    data class Jukebox(val trackKey: String) : PlaylistItemRef {
        override val rawId: String = "$PREFIX_JUKEBOX$trackKey"
    }

    data class SfcTrack(val trackId: String) : PlaylistItemRef {
        override val rawId: String = "$PREFIX_SFC$trackId"
    }

    data class BattleTrack(val trackId: String) : PlaylistItemRef {
        override val rawId: String = "$PREFIX_BATTLE$trackId"
    }

    data class ExtraTrack(val extraId: String) : PlaylistItemRef {
        override val rawId: String = "$PREFIX_EXTRA$extraId"
    }

    companion object {
        const val PREFIX_JUKEBOX = "jb:"
        const val PREFIX_SFC = "sfc:"
        const val PREFIX_BATTLE = "battle:"
        const val PREFIX_EXTRA = "extra:"

        fun parse(rawId: String): PlaylistItemRef = when {
            rawId.startsWith(PREFIX_JUKEBOX) -> Jukebox(rawId.removePrefix(PREFIX_JUKEBOX))
            rawId.startsWith(PREFIX_SFC) -> SfcTrack(rawId.removePrefix(PREFIX_SFC))
            rawId.startsWith(PREFIX_BATTLE) -> BattleTrack(rawId.removePrefix(PREFIX_BATTLE))
            rawId.startsWith(PREFIX_EXTRA) -> ExtraTrack(rawId.removePrefix(PREFIX_EXTRA))
            else -> Course(rawId)
        }
    }
}

/** The ordered list stores occurrences, so the same track may be added more than once. */
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
    fun itemAt(index: Int): PlaylistItemRef? = courseIds.getOrNull(index)?.let(PlaylistItemRef::parse)

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
    fun createWithCourses(name: String, courseIds: List<String>): Result<List<UserPlaylist>> = update { current ->
        val title = name.trim().also { require(it.isNotEmpty() && it.length <= 120) }
        val clean = courseIds.filter { it.isNotBlank() }
        current + UserPlaylist(UUID.randomUUID().toString(), title, clean)
    }
    fun addCourse(id: String, courseId: String): Result<List<UserPlaylist>> = addCourses(id, listOf(courseId))
    fun addCourses(id: String, courseIds: List<String>): Result<List<UserPlaylist>> = update { current ->
        val clean = courseIds.filter { it.isNotBlank() }
        require(clean.isNotEmpty() && current.any { it.id == id })
        current.map { list ->
            if (list.id != id) list else {
                val nextCourses = list.courseIds + clean
                val nextConfigs = if (list.entryConfigs.isEmpty()) {
                    emptyList()
                } else {
                    normalizeConfigs(nextCourses.size, paddedConfigs(list) + List(clean.size) { PlaylistEntryConfig() })
                }
                list.copy(courseIds = nextCourses, entryConfigs = nextConfigs)
            }
        }
    }
    fun removeCourse(id: String, index: Int): Result<List<UserPlaylist>> = removeCourses(id, setOf(index))
    fun removeCourses(id: String, indices: Collection<Int>): Result<List<UserPlaylist>> = update { current ->
        val removeSet = indices.toSet()
        require(removeSet.isNotEmpty() && current.any { it.id == id && removeSet.all { idx -> idx in it.courseIds.indices } })
        current.map { list ->
            if (list.id != id) list else {
                val nextCourses = list.courseIds.filterIndexed { i, _ -> i !in removeSet }
                val nextConfigs = if (list.entryConfigs.isEmpty()) {
                    emptyList()
                } else {
                    normalizeConfigs(nextCourses.size, paddedConfigs(list).filterIndexed { i, _ -> i !in removeSet })
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
    fun replaceAll(playlists: List<UserPlaylist>): Result<List<UserPlaylist>> = update {
        val clean = ArrayList<UserPlaylist>(playlists.size)
        val seenIds = hashSetOf<String>()
        playlists.forEach { raw ->
            val title = raw.name.trim()
            if (title.isEmpty()) return@forEach
            val courses = raw.courseIds.filter { it.isNotBlank() }
            val uniqueId = if (raw.id.isNotBlank() && seenIds.add(raw.id)) {
                raw.id
            } else {
                UUID.randomUUID().toString().also { seenIds.add(it) }
            }
            clean += raw.copy(
                id = uniqueId,
                name = title.take(120),
                courseIds = courses,
                entryConfigs = normalizeConfigs(courses.size, raw.entryConfigs),
            )
        }
        clean
    }
    fun mergeAll(incoming: List<UserPlaylist>): Result<List<UserPlaylist>> = update { current ->
        val out = current.toMutableList()
        val byId = current.associateBy { it.id }.toMutableMap()
        incoming.forEach { raw ->
            val title = raw.name.trim().take(120)
            if (title.isEmpty()) return@forEach
            val courses = raw.courseIds.filter { it.isNotBlank() }
            val cleanCfg = normalizeConfigs(courses.size, raw.entryConfigs)
            val existing = byId[raw.id]
            if (existing == null && raw.id.isNotBlank()) {
                val added = raw.copy(name = title, courseIds = courses, entryConfigs = cleanCfg)
                out += added
                byId[added.id] = added
            } else if (existing != null && existing.name == title && existing.courseIds == courses) {
                // Identical playlist already exists: keep existing unchanged.
            } else {
                val newId = UUID.randomUUID().toString()
                val added = raw.copy(id = newId, name = title, courseIds = courses, entryConfigs = cleanCfg)
                out += added
                byId[newId] = added
            }
        }
        out
    }
    private fun paddedConfigs(playlist: UserPlaylist): List<PlaylistEntryConfig> =
        (0 until playlist.courseIds.size).map { i -> playlist.configAt(i) }
    private fun normalizeConfigs(size: Int, configs: List<PlaylistEntryConfig>): List<PlaylistEntryConfig> {
        val clipped = (0 until size).map { i -> configs.getOrNull(i) ?: PlaylistEntryConfig() }
        return if (clipped.all { it.isDefault }) emptyList() else clipped
    }
}
