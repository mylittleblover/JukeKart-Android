package com.mylittleblover.mkwplayer.ui.routes

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.geometry.Offset

@Stable
internal class WorldMapState(
    camera: MapCamera = MapCamera(2.4f),
    routeId: String? = null,
    courseCode: String? = null,
) {
    var camera by mutableStateOf(camera)
        private set
    var selectedRouteId by mutableStateOf(routeId)
        private set
    var selectedCourseCode by mutableStateOf(courseCode?.removeSuffix("_00"))
        private set
    private var pendingFit: List<Offset>? = null
    private var pendingFitMaxZoom: Float = MapTransform.MAX_ZOOM
    private var pendingFitScale: Float = 1f
    private var width = 0f
    private var height = 0f
    private var aspect = 1f
    private var padding = 0f

    /** True while the listener is composing a route from the game's own connections. */
    var editing by mutableStateOf(false)
        private set

    /** The stage the next connection starts from, while editing. */
    var editFrom by mutableStateOf<String?>(null)
        private set

    /** The connections chosen so far, in order. Each entry is one row of the game's table. */
    var draft by mutableStateOf<List<Pair<String, String>>>(emptyList())
        private set

    var editingRouteId by mutableStateOf<String?>(null)
        private set
    var editingRouteName by mutableStateOf("")
    var draftLoopWholeRoute by mutableStateOf(false)
    var draftRoadRuleMode by mutableStateOf(com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.RoadMusicRuleMode.AUTO)
    var draftStopPresets by mutableStateOf<Map<Int, String?>>(emptyMap())
        private set
    var draftEdgeConfigs by mutableStateOf<Map<Int, com.mylittleblover.mkwplayer.routes.CustomRouteEdgeConfig>>(emptyMap())
        private set

    /**
     * Why the last picked connection was refused, or null.
     *
     * The reason is shown rather than the tap being silently ignored: a listener who taps two pins and sees
     * nothing happen cannot tell "the game has no road here" from "the app is broken".
     */
    var editRefusal by mutableStateOf<String?>(null)
        private set

    fun enterEdit() {
        editing = true
        editingRouteId = null
        editingRouteName = ""
        draftLoopWholeRoute = false
        draftRoadRuleMode = com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.RoadMusicRuleMode.AUTO
        draftStopPresets = emptyMap()
        draftEdgeConfigs = emptyMap()
        editFrom = null
        draft = emptyList()
        editRefusal = null
        selectedRouteId = null
    }

    fun exitEdit() {
        editing = false
        editingRouteId = null
        editingRouteName = ""
        draftLoopWholeRoute = false
        draftRoadRuleMode = com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.RoadMusicRuleMode.AUTO
        draftStopPresets = emptyMap()
        draftEdgeConfigs = emptyMap()
        editFrom = null
        draft = emptyList()
        editRefusal = null
    }

    /** Loads a saved [CustomRoute] into the map editor while respecting tail-only topology edits (#79). */
    fun loadCustomRoute(route: com.mylittleblover.mkwplayer.routes.CustomRoute, points: List<Offset> = emptyList()) {
        val pairs = route.stops.zipWithNext { a, b ->
            a.stageCode.removeSuffix("_00") to b.stageCode.removeSuffix("_00")
        }
        editing = true
        editingRouteId = route.id
        editingRouteName = route.name
        draftLoopWholeRoute = route.loopWholeRoute
        draftRoadRuleMode = route.roadRuleMode
        draft = pairs
        editFrom = route.stops.lastOrNull()?.stageCode?.removeSuffix("_00")
        draftStopPresets = route.stops.mapIndexed { idx, s -> idx to s.presetId }.toMap()
        draftEdgeConfigs = pairs.indices.mapNotNull { idx ->
            route.edgeFor(idx)?.let { idx to it }
        }.toMap()
        editRefusal = null
        selectedRouteId = null
        selectedCourseCode = null
        if (points.isNotEmpty()) {
            if (width > 0f && height > 0f) {
                camera = transform().fitTo(points, padding * 2.4f, maxZoom = 2.8f, scaleMultiplier = 0.82f)
            } else {
                pendingFit = points
                pendingFitMaxZoom = 2.8f
                pendingFitScale = 0.82f
            }
        }
    }

    /** Records that [from] is the start of the next connection; null clears the pending start. */
    fun editStart(from: String?) { editFrom = from?.removeSuffix("_00"); editRefusal = null }

    /**
     * Appends [from] -> [to] to the draft.
     *
     * The caller has already checked the game defines it; this only keeps the list consistent, so a stage is
     * never followed by itself and the same connection is not appended twice in a row.
     */
    fun editAppend(from: String, to: String) {
        val a = from.removeSuffix("_00")
        val b = to.removeSuffix("_00")
        if (a == b) return
        if (draft.lastOrNull() == (a to b)) return
        val newIdx = draft.size
        draft = draft + (a to b)
        draftEdgeConfigs = draftEdgeConfigs + (
            newIdx to com.mylittleblover.mkwplayer.routes.CustomRouteEdgeConfig(
                from = a,
                to = b,
                musicEnabled = true,
                selectedJukeboxKey = null,
            )
            )
        editFrom = b
        editRefusal = null
    }

    fun editRefuse(reason: String) { editRefusal = reason }

    fun editUndo() {
        if (draft.isEmpty()) return
        val removedEdgeIndex = draft.lastIndex
        val removedStopIndex = draft.size
        draft = draft.dropLast(1)
        draftEdgeConfigs = draftEdgeConfigs - removedEdgeIndex
        draftStopPresets = draftStopPresets - removedStopIndex
        editFrom = draft.lastOrNull()?.second
        editRefusal = null
    }

    fun editClear() {
        editingRouteId = null
        draft = emptyList()
        draftRoadRuleMode = com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.RoadMusicRuleMode.AUTO
        draftEdgeConfigs = emptyMap()
        draftStopPresets = emptyMap()
        editFrom = null
        editRefusal = null
    }

    fun setEdgeMusic(edgeIndex: Int, musicEnabled: Boolean, selectedJukeboxKey: String?) {
        val pair = draft.getOrNull(edgeIndex) ?: return
        draftEdgeConfigs = draftEdgeConfigs + (
            edgeIndex to com.mylittleblover.mkwplayer.routes.CustomRouteEdgeConfig(
                from = pair.first,
                to = pair.second,
                musicEnabled = musicEnabled,
                selectedJukeboxKey = selectedJukeboxKey,
            )
            )
    }

    fun edgeConfigAt(edgeIndex: Int): com.mylittleblover.mkwplayer.routes.CustomRouteEdgeConfig {
        val existing = draftEdgeConfigs[edgeIndex]
        if (existing != null) return existing
        val pair = draft.getOrNull(edgeIndex)
        return com.mylittleblover.mkwplayer.routes.CustomRouteEdgeConfig(
            from = pair?.first.orEmpty(),
            to = pair?.second.orEmpty(),
        )
    }

    fun toCustomRouteStops(): List<com.mylittleblover.mkwplayer.routes.CustomRouteStop> =
        draftStops().mapIndexed { idx, code ->
            com.mylittleblover.mkwplayer.routes.CustomRouteStop(stageCode = code, presetId = draftStopPresets[idx])
        }

    fun toCustomRouteEdges(): List<com.mylittleblover.mkwplayer.routes.CustomRouteEdgeConfig> =
        draft.indices.map { idx -> edgeConfigAt(idx) }

    /** Every stage the draft visits, in order, including the first. */
    fun draftStops(): List<String> =
        if (draft.isEmpty()) emptyList() else listOf(draft.first().first) + draft.map { it.second }

    fun configure(width: Float, height: Float, imageAspect: Float, padding: Float): MapTransform {
        this.width = width; this.height = height; this.aspect = imageAspect; this.padding = padding
        if (width > 0f && height > 0f) pendingFit?.let { points ->
            pendingFit = null
            camera = transform().fitTo(points, padding * 3.0f, pendingFitMaxZoom, pendingFitScale)
        }
        return transform()
    }
    private fun transform() = MapTransform.create(width, height, aspect, camera, padding)
    fun gesture(centroid: Offset, pan: Offset, zoom: Float) { camera = transform().gesture(centroid, pan, zoom) }
    fun zoomBy(factor: Float) = gesture(Offset(width / 2f, height / 2f), Offset.Zero, factor)
    fun reset() { camera = MapCamera(1f) }
    fun selectCourse(code: String) { selectedCourseCode = code.removeSuffix("_00") }
    fun clearCourse() { selectedCourseCode = null }
    fun clearSelection() {
        val hadRoute = selectedRouteId != null
        selectedCourseCode = null
        selectedRouteId = null
        if (hadRoute && camera.zoom > 1.75f && width > 0f && height > 0f) {
            val zoomedOut = (camera.zoom * 0.78f).coerceAtLeast(1.25f)
            camera = MapTransform.create(width, height, aspect, MapCamera(zoomedOut, camera.centerU, camera.centerV), padding).camera
        }
    }
    fun selectRoute(id: String, points: List<Offset> = emptyList()) {
        selectedRouteId = id
        selectedCourseCode = null
        if (points.isNotEmpty()) {
            if (width > 0f && height > 0f) {
                camera = transform().fitTo(points, padding * 3.0f, maxZoom = 2.55f, scaleMultiplier = 0.78f)
            } else {
                pendingFit = points
                pendingFitMaxZoom = 2.55f
                pendingFitScale = 0.78f
            }
        }
    }

    companion object {
        val Saver = listSaver<WorldMapState, Any>(
            save = { listOf(it.camera.zoom, it.camera.centerU, it.camera.centerV, it.selectedRouteId.orEmpty(), it.selectedCourseCode.orEmpty()) },
            restore = {
                WorldMapState(
                    MapCamera(it.getOrNull(0) as? Float ?: 2.4f, it.getOrNull(1) as? Float ?: .5f, it.getOrNull(2) as? Float ?: .5f),
                    (it.getOrNull(3) as? String)?.takeIf(String::isNotBlank),
                    (it.getOrNull(4) as? String)?.takeIf(String::isNotBlank),
                )
            },
        )
    }
}
