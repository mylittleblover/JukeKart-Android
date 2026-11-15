package com.mylittleblover.mkwplayer.routes

import com.mylittleblover.mkwplayer.library.MetadataFile
import com.mylittleblover.mkwplayer.library.PresetRefs
import com.mylittleblover.mkwplayer.library.StageConnections
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

internal data class CustomRouteStop(
    val stageCode: String,
    val presetId: String? = null,
)

internal data class CustomRouteEdgeConfig(
    val from: String,
    val to: String,
    val musicEnabled: Boolean = true,
    val selectedJukeboxKey: String? = null,
)

internal data class CustomRoute(
    val id: String,
    val name: String,
    val stops: List<CustomRouteStop>,
    val edges: List<CustomRouteEdgeConfig>,
    val loopWholeRoute: Boolean = false,
    val updatedAtEpochMs: Long = 0L,
    val lastUsedEpochMs: Long = 0L,
) {
    fun edgeFor(index: Int): CustomRouteEdgeConfig? = edges.getOrNull(index)

    fun effectivePresetId(stopIndex: Int): String =
        stops.getOrNull(stopIndex)?.presetId?.takeIf { it.isNotBlank() } ?: PresetRefs.SYSTEM_DEFAULT

    /** True only when every adjacent stop pair matches a directed edge in [connections] (#74-#76, #87). */
    fun hasValidTopology(connections: StageConnections.Catalog?): Boolean {
        if (stops.size < 2 || connections == null) return false
        for (i in 0 until stops.lastIndex) {
            val from = stops[i].stageCode.removeSuffix("_00")
            val to = stops[i + 1].stageCode.removeSuffix("_00")
            if (from == to || !connections.connects(from, to)) return false
        }
        return true
    }
}

/**
 * Atomic JSON storage for Map Custom Routes (#74-#81, #87).
 *
 * Enforces tail-only topology edits (#79):
 * - Appending a connected tail stop, replacing the tail stop, or removing the tail stop are allowed.
 * - Deleting or inserting intermediate nodes ahead of an existing continuation is refused.
 * - Music parameters (`presetId`, `musicEnabled`, `selectedJukeboxKey`) remain editable at any stop/edge (#79).
 */
internal class CustomRouteStore(private val file: File) {

    fun read(): Result<List<CustomRoute>> = synchronized(MetadataFile.lock(file)) {
        runCatching { decode() }
    }

    fun find(id: String): Result<CustomRoute?> = read().map { list -> list.firstOrNull { it.id == id } }

    fun create(
        name: String,
        stops: List<CustomRouteStop>,
        edges: List<CustomRouteEdgeConfig>,
        connections: StageConnections.Catalog,
        loopWholeRoute: Boolean = false,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): Result<CustomRoute> {
        val createdId = UUID.randomUUID().toString()
        return update { current ->
            val cleanName = name.trim().also { require(it.isNotEmpty() && it.length <= 120) }
            val normalizedStops = stops.map {
                CustomRouteStop(it.stageCode.trim().removeSuffix("_00"), it.presetId?.takeIf { p -> p.isNotBlank() })
            }
            val normalizedEdges = validateTopologyAndEdges(normalizedStops, edges, connections)
            val route = CustomRoute(
                id = createdId,
                name = cleanName,
                stops = normalizedStops,
                edges = normalizedEdges,
                loopWholeRoute = loopWholeRoute,
                updatedAtEpochMs = nowEpochMs,
                lastUsedEpochMs = nowEpochMs,
            )
            listOf(route) + current
        }.map { list -> list.first { it.id == createdId } }
    }

    /** Replaces tail from an edited map draft while enforcing strictly tail-only topology rules (#79). */
    fun saveDraftOver(
        existingId: String,
        name: String,
        draftStops: List<CustomRouteStop>,
        draftEdges: List<CustomRouteEdgeConfig>,
        connections: StageConnections.Catalog,
        loopWholeRoute: Boolean,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): Result<CustomRoute> = update { current ->
        val existing = current.firstOrNull { it.id == existingId }
            ?: error("Custom route '$existingId' not found")
        val cleanName = name.trim().also { require(it.isNotEmpty() && it.length <= 120) }
        val normalizedStops = draftStops.map {
            CustomRouteStop(it.stageCode.trim().removeSuffix("_00"), it.presetId?.takeIf { p -> p.isNotBlank() })
        }
        val sharedInteriorCount = minOf(existing.stops.lastIndex, normalizedStops.lastIndex)
        for (i in 0 until sharedInteriorCount) {
            if (existing.stops[i].stageCode.removeSuffix("_00") != normalizedStops[i].stageCode) {
                error("Cannot modify interior stop at index $i while keeping continuation (#79)")
            }
        }
        val normalizedEdges = validateTopologyAndEdges(normalizedStops, draftEdges, connections)
        val updated = existing.copy(
            name = cleanName,
            stops = normalizedStops,
            edges = normalizedEdges,
            loopWholeRoute = loopWholeRoute,
            updatedAtEpochMs = nowEpochMs,
        )
        current.map { if (it.id == existingId) updated else it }
    }.map { list -> list.first { it.id == existingId } }

    fun appendStop(
        id: String,
        nextStageCode: String,
        connections: StageConnections.Catalog,
        presetId: String? = null,
        musicEnabled: Boolean = true,
        selectedJukeboxKey: String? = null,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): Result<List<CustomRoute>> = update { current ->
        val next = nextStageCode.trim().removeSuffix("_00")
        require(next.isNotBlank())
        current.map { route ->
            if (route.id != id) route
            else {
                val tail = route.stops.last().stageCode.removeSuffix("_00")
                require(tail != next && connections.connects(tail, next)) {
                    "No directed road connection from $tail to $next (#74/#75)"
                }
                route.copy(
                    stops = route.stops + CustomRouteStop(next, presetId?.takeIf { it.isNotBlank() }),
                    edges = route.edges + CustomRouteEdgeConfig(tail, next, musicEnabled, selectedJukeboxKey),
                    updatedAtEpochMs = nowEpochMs,
                )
            }
        }
    }

    fun canRemoveStopAt(route: CustomRoute, stopIndex: Int): Boolean =
        stopIndex == route.stops.lastIndex && route.stops.size >= 3

    /** Removes only the tail stop; refuses interior removal or dropping below 2 stops (#74, #79). */
    fun removeStopAt(
        id: String,
        stopIndex: Int,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): Result<List<CustomRoute>> = update { current ->
        val target = current.firstOrNull { it.id == id } ?: error("Custom route '$id' not found")
        require(stopIndex in target.stops.indices) { "Stop index out of range" }
        require(stopIndex == target.stops.lastIndex) {
            "Topology edits are permitted only at the end of the route (#79)"
        }
        require(target.stops.size >= 3) {
            "Cannot remove tail stop below 2 stops (#74)"
        }
        current.map { route ->
            if (route.id != id) route
            else route.copy(
                stops = route.stops.dropLast(1),
                edges = route.edges.dropLast(1),
                updatedAtEpochMs = nowEpochMs,
            )
        }
    }

    fun removeLastStop(id: String, nowEpochMs: Long = System.currentTimeMillis()): Result<List<CustomRoute>> =
        update { current ->
            val target = current.firstOrNull { it.id == id } ?: error("Custom route '$id' not found")
            require(target.stops.size >= 3) { "Cannot remove tail stop below 2 stops (#74)" }
            current.map { route ->
                if (route.id != id) route
                else route.copy(
                    stops = route.stops.dropLast(1),
                    edges = route.edges.dropLast(1),
                    updatedAtEpochMs = nowEpochMs,
                )
            }
        }

    fun replaceTailStop(
        id: String,
        newTailStage: String,
        connections: StageConnections.Catalog,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): Result<List<CustomRoute>> = update { current ->
        val next = newTailStage.trim().removeSuffix("_00")
        current.map { route ->
            if (route.id != id) route
            else {
                require(route.stops.size >= 2)
                val prev = route.stops[route.stops.lastIndex - 1].stageCode.removeSuffix("_00")
                require(prev != next && connections.connects(prev, next)) {
                    "No directed road connection from $prev to $next (#74/#75)"
                }
                val newStops = route.stops.dropLast(1) + CustomRouteStop(next, presetId = null)
                val newEdges = route.edges.dropLast(1) + CustomRouteEdgeConfig(prev, next)
                route.copy(stops = newStops, edges = newEdges, updatedAtEpochMs = nowEpochMs)
            }
        }
    }

    /** Updating stop preset does not alter route topology and is permitted on any stop (#78, #79). */
    fun setStopPreset(
        id: String,
        stopIndex: Int,
        presetId: String?,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): Result<List<CustomRoute>> = update { current ->
        current.map { route ->
            if (route.id != id) route
            else {
                require(stopIndex in route.stops.indices)
                val cleanPreset = presetId?.takeIf { it.isNotBlank() && it != PresetRefs.SYSTEM_DEFAULT }
                val updatedStops = route.stops.mapIndexed { idx, s ->
                    if (idx == stopIndex) s.copy(presetId = cleanPreset) else s
                }
                route.copy(stops = updatedStops, updatedAtEpochMs = nowEpochMs)
            }
        }
    }

    /** Updating road music selection or mute flag does not alter topology and is permitted on any leg (#72, #73, #79). */
    fun setEdgeMusic(
        id: String,
        edgeIndex: Int,
        musicEnabled: Boolean,
        selectedJukeboxKey: String?,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): Result<List<CustomRoute>> = update { current ->
        current.map { route ->
            if (route.id != id) route
            else {
                require(edgeIndex in route.edges.indices)
                val updatedEdges = route.edges.mapIndexed { idx, edge ->
                    if (idx == edgeIndex) {
                        edge.copy(
                            musicEnabled = musicEnabled,
                            selectedJukeboxKey = selectedJukeboxKey?.takeIf { it.isNotBlank() },
                        )
                    } else edge
                }
                route.copy(edges = updatedEdges, updatedAtEpochMs = nowEpochMs)
            }
        }
    }

    fun setLoopWholeRoute(
        id: String,
        loop: Boolean,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): Result<List<CustomRoute>> = update { current ->
        current.map { route ->
            if (route.id != id) route else route.copy(loopWholeRoute = loop, updatedAtEpochMs = nowEpochMs)
        }
    }

    fun rename(
        id: String,
        name: String,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): Result<List<CustomRoute>> = update { current ->
        val clean = name.trim().also { require(it.isNotEmpty() && it.length <= 120) }
        current.map { route ->
            if (route.id != id) route else route.copy(name = clean, updatedAtEpochMs = nowEpochMs)
        }
    }

    fun touchLastUsed(id: String, nowEpochMs: Long = System.currentTimeMillis()): Result<List<CustomRoute>> = update { current ->
        val target = current.firstOrNull { it.id == id } ?: return@update current
        listOf(target.copy(lastUsedEpochMs = nowEpochMs)) + current.filterNot { it.id == id }
    }

    fun delete(id: String): Result<List<CustomRoute>> = update { current ->
        current.filterNot { it.id == id }
    }

    fun clearPresetReferences(presetId: String): Result<List<CustomRoute>> = update { current ->
        current.map { route ->
            val changed = route.stops.any { it.presetId == presetId }
            if (!changed) route
            else route.copy(
                stops = route.stops.map { s -> if (s.presetId == presetId) s.copy(presetId = null) else s },
            )
        }
    }

    private fun validateTopologyAndEdges(
        stops: List<CustomRouteStop>,
        rawEdges: List<CustomRouteEdgeConfig>,
        connections: StageConnections.Catalog,
    ): List<CustomRouteEdgeConfig> {
        require(stops.size >= 2) { "Route requires at least 2 stops (#74)" }
        val normalized = ArrayList<CustomRouteEdgeConfig>(stops.size - 1)
        for (i in 0 until stops.lastIndex) {
            val from = stops[i].stageCode.removeSuffix("_00")
            val to = stops[i + 1].stageCode.removeSuffix("_00")
            require(from.isNotBlank() && to.isNotBlank() && from != to) {
                "Adjacent stops must differ ($from -> $to)"
            }
            require(connections.connects(from, to)) {
                "No verified directed road from $from to $to (#74/#75)"
            }
            val cfg = rawEdges.getOrNull(i)
            normalized += CustomRouteEdgeConfig(
                from = from,
                to = to,
                musicEnabled = cfg?.musicEnabled ?: true,
                selectedJukeboxKey = cfg?.selectedJukeboxKey?.takeIf { it.isNotBlank() },
            )
        }
        return normalized
    }

    private fun update(transform: (List<CustomRoute>) -> List<CustomRoute>): Result<List<CustomRoute>> =
        synchronized(MetadataFile.lock(file)) {
            runCatching {
                val updated = transform(decode())
                val arr = JSONArray(updated.map(::encodeRoute))
                val root = JSONObject()
                    .put("schemaVersion", SCHEMA_VERSION)
                    .put("routes", arr)
                MetadataFile.write(file, root.toString())
                updated
            }
        }

    private fun decode(): List<CustomRoute> {
        if (!file.exists()) return emptyList()
        val root = JSONObject(file.readText())
        require(root.getInt("schemaVersion") == SCHEMA_VERSION) { "Unsupported custom route schemaVersion" }
        val arr = root.getJSONArray("routes")
        return (0 until arr.length()).map { idx -> decodeRoute(arr.getJSONObject(idx)) }
    }

    private fun encodeRoute(route: CustomRoute): JSONObject {
        val stopsArr = JSONArray()
        route.stops.forEach { stop ->
            stopsArr.put(
                JSONObject()
                    .put("stageCode", stop.stageCode)
                    .put("presetId", stop.presetId ?: JSONObject.NULL),
            )
        }
        val edgesArr = JSONArray()
        route.edges.forEach { edge ->
            edgesArr.put(
                JSONObject()
                    .put("from", edge.from)
                    .put("to", edge.to)
                    .put("musicEnabled", edge.musicEnabled)
                    .put("selectedJukeboxKey", edge.selectedJukeboxKey ?: JSONObject.NULL),
            )
        }
        return JSONObject()
            .put("id", route.id)
            .put("name", route.name)
            .put("stops", stopsArr)
            .put("edges", edgesArr)
            .put("loopWholeRoute", route.loopWholeRoute)
            .put("updatedAtEpochMs", route.updatedAtEpochMs)
            .put("lastUsedEpochMs", route.lastUsedEpochMs)
    }

    private fun decodeRoute(obj: JSONObject): CustomRoute {
        val stopsArr = obj.getJSONArray("stops")
        val stops = (0 until stopsArr.length()).map { i ->
            val s = stopsArr.getJSONObject(i)
            CustomRouteStop(
                stageCode = s.getString("stageCode").removeSuffix("_00"),
                presetId = if (s.has("presetId") && !s.isNull("presetId")) s.getString("presetId").takeIf { it.isNotBlank() } else null,
            )
        }
        val edgesArr = obj.optJSONArray("edges") ?: JSONArray()
        val edges = (0 until edgesArr.length()).map { i ->
            val e = edgesArr.getJSONObject(i)
            CustomRouteEdgeConfig(
                from = e.getString("from").removeSuffix("_00"),
                to = e.getString("to").removeSuffix("_00"),
                musicEnabled = e.optBoolean("musicEnabled", true),
                selectedJukeboxKey = if (e.has("selectedJukeboxKey") && !e.isNull("selectedJukeboxKey")) {
                    e.getString("selectedJukeboxKey").takeIf { it.isNotBlank() }
                } else null,
            )
        }
        return CustomRoute(
            id = obj.getString("id"),
            name = obj.getString("name"),
            stops = stops,
            edges = edges,
            loopWholeRoute = obj.optBoolean("loopWholeRoute", false),
            updatedAtEpochMs = obj.optLong("updatedAtEpochMs", 0L),
            lastUsedEpochMs = obj.optLong("lastUsedEpochMs", 0L),
        )
    }

    companion object {
        const val SCHEMA_VERSION = 1

        fun canRemoveStopAt(stopCount: Int, index: Int): Boolean =
            stopCount >= 3 && index == stopCount - 1
    }
}
