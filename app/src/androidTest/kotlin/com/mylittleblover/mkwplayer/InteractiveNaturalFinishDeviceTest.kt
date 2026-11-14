package com.mylittleblover.mkwplayer

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mylittleblover.mkwplayer.pack.PackRegistry
import com.mylittleblover.mkwplayer.player.PlayerViewModel
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the actual Native ending, including the output lifecycle after the tail finishes. */
@RunWith(AndroidJUnit4::class)
class InteractiveNaturalFinishDeviceTest {
    @Test fun outroFinishesAndStopsTheInteractiveSession() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(PackRegistry(File(context.filesDir, "packs")).load().isNotEmpty())
        fun await(message: String, timeoutMs: Long = 240_000, condition: () -> Boolean) {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                if (condition()) return
                Thread.sleep(100)
            }
            fail(message)
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: PlayerViewModel
            scenario.onActivity { activity ->
                model = ViewModelProvider(activity, ViewModelProvider.AndroidViewModelFactory.getInstance(
                    context.applicationContext as Application))[PlayerViewModel::class.java]
                assertTrue(model.canStartStage("FirstCircuit"))
                model.startStagePerformance("FirstCircuit")
            }
            await("Prelude did not become ready") { model.ui.value.flow?.canAdvance == true }
            instrumentation.runOnMainSync { model.advancePerformance() }
            await("Main did not become ready") { model.ui.value.flow?.canEnd == true }
            instrumentation.runOnMainSync { model.takeOutro() }
            await("The ending never finished and stopped the session") {
                val ui = model.ui.value
                ui.flow?.stageKey == "Finished" && !ui.canStop && !ui.canPause && !ui.canResume
            }
            Thread.sleep(1000)
            assertEquals("Finished", model.ui.value.flow?.stageKey)
            assertFalse(model.ui.value.flow!!.performing)
        }
    }
}
