package com.mylittleblover.mkwplayer.player

import com.mylittleblover.mkwplayer.library.MetadataFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Cold-restart linear queue snapshot persistence (#62, #89).
 *
 * Stores enough queue structure, source context, and exact playback position in frames
 * to present "Continue listening" on the next app launch in a paused state, while keeping
 * resolution tied to the currently installed packs so stale removed assets are rejected cleanly.
 */
internal class LinearSessionStore(private val file: File) {

    enum class SourceType {
        JUKEBOX,
        EXTRA,
        MEDLEY,
        PRESET,
    }

    enum class ContextKind {
        NONE,
        JUKEBOX_GAME,
        JUKEBOX_ALL,
        PLAYLIST,
        OFFICIAL_ROUTE,
        CUSTOM_ROUTE,
        COURSE_MEDLEY,
        EXTRA,
    }

    data class SavedEntry(
        val key: String,
        val title: String,
        val subtitle: String = "",
        val sourceType: SourceType = SourceType.JUKEBOX,
        val courseCode: String? = null,
        val presetId: String? = null,
        val medleyId: String? = null,
        val nativeTransitionOverride: Boolean? = null,
        val gameId: String? = null,
        val gameTitle: String? = null,
        val yearLabel: String? = null,
        val arrangement: String? = null,
        val ostIndex: Int? = null,
        val durationMs: Long = 0L,
        val artworkName: String? = null,
        val assetSha256: String? = null,
        val loop: Boolean = false,
        val loopStart: Long? = null,
        val loopEnd: Long? = null,
    )

    data class IndexedEntry(
        val entry: SavedEntry,
        val baseIndex: Int,
    )

    data class Snapshot(
        val frozenBase: List<SavedEntry>,
        val current: SavedEntry,
        val currentBaseIndex: Int,
        val pastHistory: List<IndexedEntry> = emptyList(),
        val forwardHistory: List<IndexedEntry> = emptyList(),
        val tempQueue: List<SavedEntry> = emptyList(),
        val upcomingBase: List<IndexedEntry> = emptyList(),
        val repeatMode: LinearQueue.RepeatMode = LinearQueue.RepeatMode.LIST,
        val shuffle: Boolean = false,
        val nativeTransitionDefault: Boolean = false,
        val contextKind: ContextKind = ContextKind.NONE,
        val contextId: String? = null,
        val contextTitle: String? = null,
        val positionFrames: Long = 0L,
        val totalFrames: Long = 0L,
        val sampleRate: Int = 48_000,
        val packFingerprints: List<String> = emptyList(),
        val savedAtEpochMs: Long = 0L,
    )

    fun read(): Result<Snapshot?> = synchronized(MetadataFile.lock(file)) {
        runCatching {
            if (!file.exists()) return@runCatching null
            decode(JSONObject(file.readText()))
        }
    }

    fun write(snapshot: Snapshot): Result<Unit> = synchronized(MetadataFile.lock(file)) {
        runCatching {
            MetadataFile.write(file, encode(snapshot).toString())
        }
    }

    fun clear(): Result<Unit> = synchronized(MetadataFile.lock(file)) {
        runCatching {
            if (file.exists()) {
                file.delete()
            }
            Unit
        }
    }

    private fun encode(s: Snapshot): JSONObject = JSONObject()
        .put("schemaVersion", SCHEMA_VERSION)
        .put("frozenBase", JSONArray(s.frozenBase.map(::encodeEntry)))
        .put("current", encodeEntry(s.current))
        .put("currentBaseIndex", s.currentBaseIndex)
        .put("pastHistory", JSONArray(s.pastHistory.map(::encodeIndexed)))
        .put("forwardHistory", JSONArray(s.forwardHistory.map(::encodeIndexed)))
        .put("tempQueue", JSONArray(s.tempQueue.map(::encodeEntry)))
        .put("upcomingBase", JSONArray(s.upcomingBase.map(::encodeIndexed)))
        .put("repeatMode", s.repeatMode.name)
        .put("shuffle", s.shuffle)
        .put("nativeTransitionDefault", s.nativeTransitionDefault)
        .put("contextKind", s.contextKind.name)
        .put("contextId", s.contextId ?: JSONObject.NULL)
        .put("contextTitle", s.contextTitle ?: JSONObject.NULL)
        .put("positionFrames", s.positionFrames)
        .put("totalFrames", s.totalFrames)
        .put("sampleRate", s.sampleRate)
        .put("packFingerprints", JSONArray(s.packFingerprints))
        .put("savedAtEpochMs", s.savedAtEpochMs)

    private fun encodeIndexed(item: IndexedEntry): JSONObject = JSONObject()
        .put("baseIndex", item.baseIndex)
        .put("entry", encodeEntry(item.entry))

    private fun encodeEntry(e: SavedEntry): JSONObject = JSONObject()
        .put("key", e.key)
        .put("title", e.title)
        .put("subtitle", e.subtitle)
        .put("sourceType", e.sourceType.name)
        .put("courseCode", e.courseCode ?: JSONObject.NULL)
        .put("presetId", e.presetId ?: JSONObject.NULL)
        .put("medleyId", e.medleyId ?: JSONObject.NULL)
        .put("nativeTransitionOverride", e.nativeTransitionOverride ?: JSONObject.NULL)
        .put("gameId", e.gameId ?: JSONObject.NULL)
        .put("gameTitle", e.gameTitle ?: JSONObject.NULL)
        .put("yearLabel", e.yearLabel ?: JSONObject.NULL)
        .put("arrangement", e.arrangement ?: JSONObject.NULL)
        .put("ostIndex", e.ostIndex ?: JSONObject.NULL)
        .put("durationMs", e.durationMs)
        .put("artworkName", e.artworkName ?: JSONObject.NULL)
        .put("assetSha256", e.assetSha256 ?: JSONObject.NULL)
        .put("loop", e.loop)
        .put("loopStart", e.loopStart ?: JSONObject.NULL)
        .put("loopEnd", e.loopEnd ?: JSONObject.NULL)

    private fun decode(root: JSONObject): Snapshot {
        require(root.getInt("schemaVersion") == SCHEMA_VERSION) { "Unsupported linear session schemaVersion" }
        fun optStr(obj: JSONObject, key: String): String? =
            if (obj.has(key) && !obj.isNull(key)) obj.getString(key) else null

        fun decodeEntry(obj: JSONObject): SavedEntry = SavedEntry(
            key = obj.getString("key"),
            title = obj.getString("title"),
            subtitle = obj.optString("subtitle", ""),
            sourceType = SourceType.entries.firstOrNull { it.name == obj.optString("sourceType") }
                ?: SourceType.JUKEBOX,
            courseCode = optStr(obj, "courseCode"),
            presetId = optStr(obj, "presetId"),
            medleyId = optStr(obj, "medleyId"),
            nativeTransitionOverride = if (obj.has("nativeTransitionOverride") && !obj.isNull("nativeTransitionOverride")) {
                obj.getBoolean("nativeTransitionOverride")
            } else null,
            gameId = optStr(obj, "gameId"),
            gameTitle = optStr(obj, "gameTitle"),
            yearLabel = optStr(obj, "yearLabel"),
            arrangement = optStr(obj, "arrangement"),
            ostIndex = if (obj.has("ostIndex") && !obj.isNull("ostIndex")) obj.getInt("ostIndex") else null,
            durationMs = obj.optLong("durationMs", 0L),
            artworkName = optStr(obj, "artworkName"),
            assetSha256 = optStr(obj, "assetSha256"),
            loop = obj.optBoolean("loop", false),
            loopStart = if (obj.has("loopStart") && !obj.isNull("loopStart")) obj.getLong("loopStart") else null,
            loopEnd = if (obj.has("loopEnd") && !obj.isNull("loopEnd")) obj.getLong("loopEnd") else null,
        )

        fun decodeEntries(arr: JSONArray): List<SavedEntry> =
            (0 until arr.length()).map { decodeEntry(arr.getJSONObject(it)) }

        fun decodeIndexed(arr: JSONArray): List<IndexedEntry> =
            (0 until arr.length()).map {
                val obj = arr.getJSONObject(it)
                IndexedEntry(
                    entry = decodeEntry(obj.getJSONObject("entry")),
                    baseIndex = obj.getInt("baseIndex"),
                )
            }

        val fpArr = root.optJSONArray("packFingerprints") ?: JSONArray()
        return Snapshot(
            frozenBase = decodeEntries(root.getJSONArray("frozenBase")),
            current = decodeEntry(root.getJSONObject("current")),
            currentBaseIndex = root.optInt("currentBaseIndex", -1),
            pastHistory = decodeIndexed(root.optJSONArray("pastHistory") ?: JSONArray()),
            forwardHistory = decodeIndexed(root.optJSONArray("forwardHistory") ?: JSONArray()),
            tempQueue = decodeEntries(root.optJSONArray("tempQueue") ?: JSONArray()),
            upcomingBase = decodeIndexed(root.optJSONArray("upcomingBase") ?: JSONArray()),
            repeatMode = LinearQueue.RepeatMode.entries.firstOrNull { it.name == root.optString("repeatMode") }
                ?: LinearQueue.RepeatMode.LIST,
            shuffle = root.optBoolean("shuffle", false),
            nativeTransitionDefault = root.optBoolean("nativeTransitionDefault", false),
            contextKind = ContextKind.entries.firstOrNull { it.name == root.optString("contextKind") }
                ?: ContextKind.NONE,
            contextId = optStr(root, "contextId"),
            contextTitle = optStr(root, "contextTitle"),
            positionFrames = root.optLong("positionFrames", 0L).coerceAtLeast(0L),
            totalFrames = root.optLong("totalFrames", 0L).coerceAtLeast(0L),
            sampleRate = root.optInt("sampleRate", 48_000).coerceAtLeast(1),
            packFingerprints = (0 until fpArr.length()).map { fpArr.getString(it) },
            savedAtEpochMs = root.optLong("savedAtEpochMs", 0L),
        )
    }

    companion object {
        private const val SCHEMA_VERSION = 1
    }
}
