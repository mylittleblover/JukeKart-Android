package com.mylittleblover.mkwplayer.ui.routes

import androidx.compose.runtime.saveable.SaverScope
import kotlin.test.*

class WorldMapStateTest {
    @Test fun routeSelectedBeforeFirstLayoutFitsOnceAndKeepsLaterGestures() {
        val state = WorldMapState()
        val points = listOf(androidx.compose.ui.geometry.Offset(.1f, .2f), androidx.compose.ui.geometry.Offset(.9f, .8f))
        state.selectRoute("mushroom_cup", points)
        val transform = state.configure(400f, 800f, 1f, 16f)
        for (point in points) {
            val screen = transform.project(point.x, point.y)
            assertTrue(screen.x in 0f..400f && screen.y in 0f..800f)
        }
        state.zoomBy(1.5f)
        val camera = state.camera
        state.configure(400f, 800f, 1f, 16f)
        assertEquals(camera, state.camera)
    }
    @Test fun cameraAndSelectedIdentitiesSurviveSavedStateRestoration() {
        val original = WorldMapState(MapCamera(3.2f, .45f, .55f), "mushroom_cup", "WestCity")
        val scope = object : SaverScope { override fun canBeSaved(value: Any) = true }
        val saved = with(WorldMapState.Saver) { scope.save(original) }
        val restored = assertNotNull(WorldMapState.Saver.restore(assertNotNull(saved)))
        assertEquals(original.camera, restored.camera)
        assertEquals("mushroom_cup", restored.selectedRouteId)
        assertEquals("WestCity", restored.selectedCourseCode)
    }
    @Test fun selectingACourseNeverChangesTheOfficialRoute() {
        val state = WorldMapState(routeId = "mushroom_cup")
        state.selectCourse("FirstCircuit_00")
        assertEquals("FirstCircuit", state.selectedCourseCode)
        assertEquals("mushroom_cup", state.selectedRouteId)
        state.clearCourse()
        assertNull(state.selectedCourseCode)
        assertEquals("mushroom_cup", state.selectedRouteId)
        state.selectCourse("WestCity")
        state.selectRoute("flower_cup")
        assertNull(state.selectedCourseCode)
        assertEquals("flower_cup", state.selectedRouteId)
        state.reset()
        assertEquals(MapCamera(1f), state.camera)
        assertEquals("flower_cup", state.selectedRouteId)
    }
}
