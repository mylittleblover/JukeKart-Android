package com.mylittleblover.mkwplayer

import android.app.UiAutomation
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfficialMapDeviceTest {
    @Test fun officialRouteSelectionAndCameraSurviveRotation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val ui = InstalledUi()
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        ui.tapText("Map")
        ui.awaitText("Official routes", timeout = 45_000)
        ui.tapText("Official routes")
        ui.tapText("Mushroom Cup", contains = true)
        ui.awaitText("Start: Mario Bros. Circuit")
        ui.awaitText("Finish: DK Spaceport")
        ui.tapDescription("Zoom out")
        ui.tapDescription("Zoom in")
        ui.capture("official-map-portrait.png")
        instrumentation.uiAutomation.setRotation(UiAutomation.ROTATION_FREEZE_90)
        try {
            ui.awaitOrientation(landscape = true)
            ui.awaitText("Start: Mario Bros. Circuit")
            ui.awaitText("Finish: DK Spaceport")
            ui.capture("official-map-landscape.png")
            ui.tapDescription("Reset view")
            instrumentation.uiAutomation.setRotation(UiAutomation.ROTATION_FREEZE_0)
            ui.awaitOrientation(landscape = false)
            ui.tapText("View stops")
            ui.tapText("1 · Mario Bros. Circuit", contains = true)
            ui.tapText("View course details")
            ui.awaitText("Mario Bros. Circuit")
            ui.tapText("Back")
            ui.tapText("Back to route")
            ui.awaitText("Start: Mario Bros. Circuit")
        } finally {
            instrumentation.uiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE)
        }
    }
}
