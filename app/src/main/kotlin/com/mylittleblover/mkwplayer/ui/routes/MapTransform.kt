package com.mylittleblover.mkwplayer.ui.routes

import androidx.compose.ui.geometry.Offset
import kotlin.math.min

/** Resolution-independent camera: save these values, never device-pixel pan offsets. */
internal data class MapCamera(val zoom: Float = 1f, val centerU: Float = .5f, val centerV: Float = .5f)

/** The bitmap, stage icons, route arrows and hit targets all use this transform. */
internal class MapTransform private constructor(
    val viewportWidth: Float,
    val viewportHeight: Float,
    private val imageAspect: Float,
    val camera: MapCamera,
    val drawnWidth: Float,
    val drawnHeight: Float,
    val left: Float,
    val top: Float,
    private val fitPadding: Float,
) {
    fun project(u: Float, v: Float): Offset = Offset(left + u * drawnWidth, top + v * drawnHeight)
    fun inverse(screen: Offset): Offset = if (drawnWidth > 0f && drawnHeight > 0f) {
        Offset((screen.x - left) / drawnWidth, (screen.y - top) / drawnHeight)
    } else Offset(.5f, .5f)

    fun gesture(centroid: Offset, pan: Offset, zoomFactor: Float): MapCamera {
        if (drawnWidth <= 0f || drawnHeight <= 0f) return camera
        val origin = Offset(centroid.x.finiteOr(viewportWidth / 2f), centroid.y.finiteOr(viewportHeight / 2f))
        val anchor = inverse(origin)
        val factor = zoomFactor.takeIf { it.isFinite() && it > 0f } ?: 1f
        val zoom = (camera.zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val width = drawnWidth * zoom / camera.zoom
        val height = drawnHeight * zoom / camera.zoom
        val next = MapCamera(
            zoom,
            anchor.x - (origin.x + pan.x.finiteOr(0f) - viewportWidth / 2f) / width,
            anchor.y - (origin.y + pan.y.finiteOr(0f) - viewportHeight / 2f) / height,
        )
        return create(viewportWidth, viewportHeight, imageAspect, next, fitPadding).camera
    }

    companion object {
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 6f

        fun create(
            viewportWidth: Float,
            viewportHeight: Float,
            imageAspect: Float,
            camera: MapCamera,
            fitPadding: Float = 0f,
        ): MapTransform {
            val width = viewportWidth.finiteOr(0f).coerceAtLeast(0f)
            val height = viewportHeight.finiteOr(0f).coerceAtLeast(0f)
            val padding = fitPadding.finiteOr(0f).coerceIn(0f, min(width, height) / 2f)
            val innerWidth = width - 2f * padding
            val innerHeight = height - 2f * padding
            val zoom = camera.zoom.finiteOr(1f).coerceIn(MIN_ZOOM, MAX_ZOOM)
            val validImage = imageAspect.isFinite() && imageAspect > 0f && innerWidth > 0f && innerHeight > 0f
            val fittedWidth = if (!validImage) 0f else min(innerWidth, innerHeight * imageAspect)
            val fittedHeight = if (!validImage) 0f else fittedWidth / imageAspect
            val drawnWidth = fittedWidth * zoom
            val drawnHeight = fittedHeight * zoom
            fun center(value: Float, viewport: Float, drawn: Float): Float {
                if (drawn <= viewport || drawn == 0f) return .5f
                val limit = viewport / (2f * drawn)
                return value.finiteOr(.5f).coerceIn(limit, 1f - limit)
            }
            val corrected = MapCamera(zoom, center(camera.centerU, width, drawnWidth), center(camera.centerV, height, drawnHeight))
            return MapTransform(
                width, height, imageAspect, corrected, drawnWidth, drawnHeight,
                width / 2f - corrected.centerU * drawnWidth,
                height / 2f - corrected.centerV * drawnHeight,
                padding,
            )
        }
    }
}

private fun Float.finiteOr(fallback: Float): Float = if (isFinite()) this else fallback
