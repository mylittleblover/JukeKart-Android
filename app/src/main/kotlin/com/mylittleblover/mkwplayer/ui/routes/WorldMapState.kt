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

    /**
     * Why the last picked connection was refused, or null.
     *
     * The reason is shown rather than the tap being silently ignored: a listener who taps two pins and sees
     * nothing happen cannot tell "the game has no road here" from "the app is broken".
     */
    var editRefusal by mutableStateOf<String?>(null)
        private set

    fun enterEdit() { editing = true; editFrom = null; draft = emptyList(); editRefusal = null; selectedRouteId = null }
    fun exitEdit() { editing = false; editFrom = null; draft = emptyList(); editRefusal = null }

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
        draft = draft + (a to b)
        editFrom = b
        editRefusal = null
    }

    fun editRefuse(reason: String) { editRefusal = reason }

    fun editUndo() {
        draft = draft.dropLast(1)
        editFrom = draft.lastOrNull()?.second
        editRefusal = null
    }

    fun editClear() { draft = emptyList(); editFrom = null; editRefusal = null }

    /** Every stage the draft visits, in order, including the first. */
    fun draftStops(): List<String> =
        if (draft.isEmpty()) emptyList() else listOf(draft.first().first) + draft.map { it.second }

    fun configure(width: Float, height: Float, imageAspect: Float, padding: Float): MapTransform {
        this.width = width; this.height = height; this.aspect = imageAspect; this.padding = padding
        if (width > 0f && height > 0f) pendingFit?.let { points ->
            pendingFit = null
            camera = transform().fitTo(points, padding * 2f)
        }
        return transform()
    }
    private fun transform() = MapTransform.create(width, height, aspect, camera, padding)
    fun gesture(centroid: Offset, pan: Offset, zoom: Float) { camera = transform().gesture(centroid, pan, zoom) }
    fun zoomBy(factor: Float) = gesture(Offset(width / 2f, height / 2f), Offset.Zero, factor)
    fun reset() { camera = MapCamera(1f) }
    fun selectCourse(code: String) { selectedCourseCode = code.removeSuffix("_00") }
    fun clearCourse() { selectedCourseCode = null }
    fun clearSelection() { selectedCourseCode = null; selectedRouteId = null }
    fun selectRoute(id: String, points: List<Offset> = emptyList()) {
        selectedRouteId = id
        selectedCourseCode = null
        if (width > 0f && height > 0f) camera = transform().fitTo(points, padding * 2f)
        else pendingFit = points
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
