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
    private var width = 0f
    private var height = 0f
    private var aspect = 1f
    private var padding = 0f

    fun configure(width: Float, height: Float, imageAspect: Float, padding: Float): MapTransform {
        this.width = width; this.height = height; this.aspect = imageAspect; this.padding = padding
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
        camera = transform().fitTo(points, padding * 2f)
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
