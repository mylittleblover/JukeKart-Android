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
}
