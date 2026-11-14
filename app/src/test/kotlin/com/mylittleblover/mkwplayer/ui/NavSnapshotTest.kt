package com.mylittleblover.mkwplayer.ui

import kotlin.test.*

class NavSnapshotTest {
    @Test fun destinationsAndDetailIdentitySurviveRecreation() {
        for (tab in Tab.ordered()) {
            for (detail in listOf(null, NavState.Detail.Course("FirstCircuit"), NavState.Detail.Flow, NavState.Detail.Settings)) {
                val state = NavState(tab, detail)
                val restored = NavState.restore(state.snapshot())
                assertEquals(tab, restored.tab)
                assertEquals(detail, restored.detail)
            }
        }
    }
    @Test fun malformedSavedNavigationReturnsToAnAvailableRoot() {
        assertEquals(Tab.default(), NavState.restore(emptyList()).tab)
        assertEquals(Tab.default(), NavState.restore(listOf("REMOVED_TAB", "course", "FirstCircuit")).tab)
        assertNull(NavState.restore(listOf(Tab.WORLD_MAP.name, "course", "")).detail)
        assertNull(NavState.restore(listOf(Tab.WORLD_MAP.name, "unknown-detail")).detail)
    }
    @Test fun nestedPathSurvivesRecreationAndBackUnwindsInOrder() {
        val original = NavState(Tab.MEDIA).open(NavState.Detail.Cup("mushroom_cup"))
            .open(NavState.Detail.Course("FirstCircuit")).open(NavState.Detail.Settings)
        val restored = NavState.restore(original.snapshot())
        assertEquals(NavState.Detail.Settings, restored.detail)
        val course = restored.back()!!
        assertEquals(NavState.Detail.Course("FirstCircuit"), course.detail)
        val cup = course.back()!!
        assertEquals(NavState.Detail.Cup("mushroom_cup"), cup.detail)
        assertNull(cup.back()!!.detail)
    }
    @Test fun legacyTabsMigrateWithoutLosingInteractiveEntry() {
        assertEquals(Tab.MEDIA, NavState.restore(listOf("LIBRARY", "root")).tab)
        assertEquals(Tab.PLAYLISTS, NavState.restore(listOf("MY_ROUTES", "root")).tab)
        val interactive = NavState.restore(listOf("INTERACTIVE", "root"))
        assertEquals(Tab.MEDIA, interactive.tab)
        assertEquals(NavState.Detail.Flow, interactive.detail)
    }
}
