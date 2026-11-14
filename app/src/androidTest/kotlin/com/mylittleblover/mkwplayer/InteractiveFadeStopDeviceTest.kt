package com.mylittleblover.mkwplayer

import android.app.Application
import android.content.Intent
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mylittleblover.mkwplayer.player.PlayerViewModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InteractiveFadeStopDeviceTest {
    private fun await(reason: String, test: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 300000
        while (SystemClock.uptimeMillis() < end) { if (test()) return; SystemClock.sleep(100) }
        fail(reason)
    }
    @Test fun longPressStopFadesOnNativeClockAndPauseFreezesCountdown() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)).use { scenario ->
            lateinit var model: PlayerViewModel
            scenario.onActivity { activity ->
                model = ViewModelProvider(activity, ViewModelProvider.AndroidViewModelFactory.getInstance(context.applicationContext as Application))[PlayerViewModel::class.java]
                assertNotNull("Test requires the installed delivery pack", model.ui.value.pack)
                model.startStagePerformance("DonkeyKong")
            }
            await("Prelude did not become ready") { model.ui.value.flow?.canAdvance == true && model.ui.value.outputActive }
            val screen = InstalledUi()
            screen.awaitOrientation(false)
            screen.tapText("Interactive")
            screen.awaitText("Interactive playback")
            screen.longPressText("Stop")
            screen.awaitText("Fade out and stop")
            screen.tapText("10-second fade")
            await("Native fade deadline unavailable") { model.ui.value.flow?.fading == true && model.ui.value.flow?.fadeRemainingFrames != null }
            assertFalse(model.ui.value.flow!!.canCancelPending)
            assertFalse(model.ui.value.flow!!.canAdvance)
            instrumentation.runOnMainSync { model.startStagePerformance("FirstCircuit"); model.pause() }
            await("Fade did not pause") { model.ui.value.canResume && !model.ui.value.canPause }
            val remaining = model.ui.value.flow!!.fadeRemainingFrames
            SystemClock.sleep(1000)
            assertEquals("Pause advanced Native fade countdown", remaining, model.ui.value.flow!!.fadeRemainingFrames)
            assertEquals("DonkeyKong", model.currentStageCode)
            screen.capture("interactive-fade-paused.png")
            instrumentation.runOnMainSync { model.resume() }
            await("Fade failed to finish and release controls") { model.ui.value.flow?.fading == false && !model.ui.value.canStop }
            assertFalse(model.ui.value.flow!!.performing)
            val short = model.initialEntryChoices.entries.single { it.value == "PreShort" }.key
            instrumentation.runOnMainSync { model.selectInitialEntry(short) }
            assertFalse("Choosing an entry unexpectedly started playback", model.ui.value.canStop)
            assertEquals(short, model.selectedEntrySegment)
            instrumentation.runOnMainSync { model.startPerformance() }
            await("Short prelude did not start as the independent entry") {
                model.ui.value.flow?.activeSegment == short && model.ui.value.flow!!.canAdvance && model.ui.value.flow!!.voiceCount > 0
            }
            instrumentation.runOnMainSync { model.advancePerformance() }
            await("Short prelude did not reach Full") { model.ui.value.flow?.selectedRegion == 1 && model.ui.value.flow!!.canAdvance }
            instrumentation.runOnMainSync { model.stop() }
        }
    }
}
