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

    @Test fun customRouteDraftSupportsEdgeMusicAndTailUndo() {
        val state = WorldMapState()
        state.enterEdit()
        state.editStart("FirstCircuit")
        state.editAppend("FirstCircuit", "CrownCity")
        state.editAppend("CrownCity", "Central")
        state.setEdgeMusic(0, musicEnabled = true, selectedJukeboxKey = "jb_a")
        state.setEdgeMusic(1, musicEnabled = false, selectedJukeboxKey = null)
        assertEquals(listOf("FirstCircuit", "CrownCity", "Central"), state.draftStops())
        assertEquals("jb_a", state.edgeConfigAt(0).selectedJukeboxKey)
        assertFalse(state.edgeConfigAt(1).musicEnabled)

        state.editUndo()
        assertEquals(listOf("FirstCircuit", "CrownCity"), state.draftStops())
        assertEquals("CrownCity", state.editFrom)
        assertEquals("jb_a", state.edgeConfigAt(0).selectedJukeboxKey)
        assertTrue(state.draftEdgeConfigs.containsKey(0))
        assertFalse(state.draftEdgeConfigs.containsKey(1))

        val savedRoute = com.mylittleblover.mkwplayer.routes.CustomRoute(
            id = "route_1",
            name = "My Scenic Drive",
            stops = listOf(
                com.mylittleblover.mkwplayer.routes.CustomRouteStop("FirstCircuit", "user:p1"),
                com.mylittleblover.mkwplayer.routes.CustomRouteStop("Central", null),
            ),
            edges = listOf(com.mylittleblover.mkwplayer.routes.CustomRouteEdgeConfig(from = "FirstCircuit", to = "Central", musicEnabled = true, selectedJukeboxKey = "jb_x")),
            loopWholeRoute = true,
        )
        state.loadCustomRoute(savedRoute)
        assertEquals("route_1", state.editingRouteId)
        assertEquals("My Scenic Drive", state.editingRouteName)
        assertTrue(state.draftLoopWholeRoute)
        assertEquals("user:p1", state.draftStopPresets[0])
        assertEquals("jb_x", state.edgeConfigAt(0).selectedJukeboxKey)
    }
}
