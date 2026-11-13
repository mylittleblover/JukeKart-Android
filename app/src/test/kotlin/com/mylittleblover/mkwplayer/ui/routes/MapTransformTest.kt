package com.mylittleblover.mkwplayer.ui.routes

import androidx.compose.ui.geometry.Offset
import kotlin.test.*

class MapTransformTest {
    @Test fun projectionAndInverseAgreeAcrossOrientationZoomAndImageAspects() {
        for ((width, height) in listOf(360f to 720f, 840f to 600f, 1280f to 800f)) {
            for (aspect in listOf(2500f / 2400f, 2f, .5f)) {
                for (zoom in listOf(1f, 2.4f, 6f)) {
                    val transform = MapTransform.create(width, height, aspect, MapCamera(zoom, .42f, .62f))
                    for (point in listOf(Offset.Zero, Offset(.216f, .364f), Offset(.5f, .5f), Offset(1f, 1f))) {
                        val result = transform.inverse(transform.project(point.x, point.y))
                        assertEquals(point.x, result.x, .00001f)
                        assertEquals(point.y, result.y, .00001f)
                    }
                }
            }
        }
    }
    @Test fun fitUsesTheActualImageAspectRatherThanAHardcodedMapRatio() {
        val wide = MapTransform.create(1000f, 2000f, 2f, MapCamera(1f))
        assertEquals(1000f, wide.drawnWidth, .001f)
        assertEquals(500f, wide.drawnHeight, .001f)
        assertEquals(750f, wide.top, .001f)
        val tall = MapTransform.create(1000f, 500f, .5f, MapCamera(1f))
        assertEquals(250f, tall.drawnWidth, .001f)
        assertEquals(375f, tall.left, .001f)
    }
    @Test fun panningCannotLoseTheMapAtEitherEdge() {
        val leftBottom = MapTransform.create(600f, 600f, 1f, MapCamera(3f, -100f, 100f))
        assertEquals(0f, leftBottom.left, .001f)
        assertEquals(-1200f, leftBottom.top, .001f)
        val rightTop = MapTransform.create(600f, 600f, 1f, MapCamera(3f, 100f, -100f))
        assertEquals(-1200f, rightTop.left, .001f)
        assertEquals(0f, rightTop.top, .001f)
    }
    @Test fun pinchKeepsTheGeographicalPointUnderItsCentroid() {
        val before = MapTransform.create(600f, 600f, 1f, MapCamera(3f))
        val centroid = Offset(350f, 290f)
        val geographic = before.inverse(centroid)
        val next = before.gesture(centroid, Offset.Zero, 1.3f)
        val after = MapTransform.create(600f, 600f, 1f, next)
        val projected = after.project(geographic.x, geographic.y)
        assertEquals(centroid.x, projected.x, .001f)
        assertEquals(centroid.y, projected.y, .001f)
    }
    @Test fun dragAndPinchUseOneCoordinateTransform() {
        val before = MapTransform.create(600f, 600f, 1f, MapCamera(3f))
        val centroid = Offset(310f, 330f)
        val geographic = before.inverse(centroid)
        val pan = Offset(27f, -19f)
        val after = MapTransform.create(600f, 600f, 1f, before.gesture(centroid, pan, 1.1f))
        val projected = after.project(geographic.x, geographic.y)
        assertEquals(centroid.x + pan.x, projected.x, .001f)
        assertEquals(centroid.y + pan.y, projected.y, .001f)
    }
    @Test fun resizeRetainsTheNormalizedCameraCenterWhenThereIsRoom() {
        val camera = MapCamera(4f, .45f, .55f)
        val portrait = MapTransform.create(360f, 720f, 2500f / 2400f, camera)
        val landscape = MapTransform.create(840f, 600f, 2500f / 2400f, portrait.camera)
        assertEquals(.45f, landscape.camera.centerU, .00001f)
        assertEquals(.55f, landscape.camera.centerV, .00001f)
        val center = landscape.project(.45f, .55f)
        assertEquals(420f, center.x, .001f)
        assertEquals(300f, center.y, .001f)
    }
    @Test fun densityScalesTheBitmapAndEveryAnchorTogether() {
        val first = MapTransform.create(360f, 720f, 1f, MapCamera(3f))
        val third = MapTransform.create(1080f, 2160f, 1f, MapCamera(3f))
        val a = first.project(.216f, .364f)
        val b = third.project(.216f, .364f)
        assertEquals(a.x * 3f, b.x, .001f)
        assertEquals(a.y * 3f, b.y, .001f)
    }
    @Test fun invalidRestoredStateAndZeroViewportNeverProduceNaN() {
        val invalid = MapTransform.create(360f, 720f, 1f, MapCamera(Float.NaN, Float.NaN, Float.POSITIVE_INFINITY))
        assertEquals(1f, invalid.camera.zoom)
        assertEquals(.5f, invalid.camera.centerU)
        assertEquals(.5f, invalid.camera.centerV)
        assertEquals(6f, MapTransform.create(360f, 720f, 1f, MapCamera(100f)).camera.zoom)
        val empty = MapTransform.create(0f, 0f, 1f, MapCamera())
        assertTrue(empty.project(.2f, .3f).x.isFinite())
        assertEquals(Offset(.5f, .5f), empty.inverse(Offset.Zero))
    }
    @Test fun wholeMapFitCanLeaveRoomForTheEntireTouchTargetAtTheEdges() {
        val transform = MapTransform.create(360f, 720f, 1f, MapCamera(1f), fitPadding = 24f)
        val first = transform.project(0f, 0f)
        val last = transform.project(1f, 1f)
        assertTrue(first.x >= 24f && first.y >= 24f)
        assertTrue(last.x <= 336f && last.y <= 696f)
    }

}
