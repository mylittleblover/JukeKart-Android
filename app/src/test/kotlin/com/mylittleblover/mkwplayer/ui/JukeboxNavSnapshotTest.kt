package com.mylittleblover.mkwplayer.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JukeboxNavSnapshotTest {

    @Test
    fun jukeboxGameDetailRoundTripsSnapshotAndRestoresCleanly() {
        val state = NavState(Tab.MEDIA).open(NavState.Detail.JukeboxGame("smw"))
        assertTrue(state.canGoBack)

        val snap = state.snapshot()
        assertTrue(snap.contains("jukebox-game"))
        assertTrue(snap.contains("smw"))

        val restored = NavState.restore(snap)
        assertEquals(Tab.MEDIA, restored.tab)
        assertEquals(NavState.Detail.JukeboxGame("smw"), restored.detail)
        assertTrue(restored.canGoBack)

        val back = restored.back()
        assertEquals(Tab.MEDIA, back?.tab)
        assertNull(back?.detail)
        assertFalse(back?.canGoBack ?: true)
    }
}
