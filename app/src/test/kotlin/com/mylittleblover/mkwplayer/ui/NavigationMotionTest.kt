package com.mylittleblover.mkwplayer.ui

import kotlin.test.*

class NavigationMotionTest {
    @Test fun backReversesForwardDirectionAtEveryDepth() {
        val home = NavState()
        val course = home.open(NavState.Detail.Course("Cinema"))
        val interactive = course.open(NavState.Detail.Interactive("Cinema"))
        val settings = interactive.open(NavState.Detail.Settings)
        assertEquals(1, course.motionFrom(home))
        assertEquals(1, interactive.motionFrom(course))
        assertEquals(-1, settings.back()!!.motionFrom(settings))
        assertEquals(-1, interactive.back()!!.motionFrom(interactive))
        assertEquals(-1, course.back()!!.motionFrom(course))
    }
    @Test fun tabsFollowTheirVisualOrderAndSamePageDoesNotSlide() {
        val home = NavState()
        val map = home.select(Tab.WORLD_MAP)
        assertEquals(1, map.motionFrom(home))
        assertEquals(-1, home.motionFrom(map))
        assertEquals(0, NavState.restore(map.snapshot()).motionFrom(map))
    }
}
