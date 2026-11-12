package com.mylittleblover.mkwplayer

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mylittleblover.mkwplayer.player.PlayerViewModel
import com.mylittleblover.mkwplayer.player.PlaybackHolder
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ResearchViewModelDeviceTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private fun await(reason: String,predicate: ()->Boolean) {
        val deadline=System.currentTimeMillis()+120000
        while(System.currentTimeMillis()<deadline) { if(predicate()) return;Thread.sleep(100) }
        fail(reason)
    }
    @Test fun importedResearchFlowQueuesPausesCommitsAndReloadsThroughTheRealApp() {
        val context=instrumentation.targetContext
        val source=File(context.filesDir,"research-sources/Stage_IceBuildings")
        assumeTrue("Supply real IceBuildings audio fixtures",source.isDirectory)
        val folder=File(context.filesDir,"stages")
        folder.deleteRecursively();folder.mkdirs()
        source.listFiles()!!.filter { it.extension=="bwav" }.forEach { it.copyTo(File(folder,it.name),true) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Once playback polls the UI, ActivityScenario.onActivity can wait forever for idle
            // on a software emulator. Keep control actions on main without an idle prerequisite.
            lateinit var model: PlayerViewModel
            scenario.onActivity { activity ->
                model=ViewModelProvider(activity,ViewModelProvider.AndroidViewModelFactory.getInstance(context.applicationContext as Application))[PlayerViewModel::class.java]
                model.openFlowFromFolder()
            }
            await("Research folder failed: ${context.filesDir}") { model.ui.value.flow!=null && !model.ui.value.busy }
            assertTrue(model.ui.value.detail.orEmpty(),model.ui.value.flow!!.provenance.contains("MINF"))
            assertTrue("Fast was dropped by import",model.ui.value.flow!!.hasFast)
            assertTrue("Actual device output failed",model.ui.value.outputActive)
            instrumentation.runOnMainSync { model.startPerformance() }
            await("Prelude never became available") { model.ui.value.flow!!.canAdvance }
            val pre=model.ui.value.flow!!.activeSegment
            instrumentation.runOnMainSync {
                model.advancePerformance()
                assertEquals("A queued request must not pretend Full has started",pre,model.ui.value.flow!!.activeSegment)
                assertTrue(model.ui.value.flow!!.performanceStage.contains("queued"))
                model.pause()
            }
            await("Native pause did not take effect") { model.ui.value.canResume }
            var pausedFrame: Long?=null
            instrumentation.runOnMainSync { pausedFrame=PlaybackHolder.get().interactiveObservation()?.sourceFrame }
            assertNotNull("Native source voice position was unavailable",pausedFrame)
            Thread.sleep(500)
            instrumentation.runOnMainSync {
                assertEquals(pausedFrame,PlaybackHolder.get().interactiveObservation()?.sourceFrame)
                model.resume()
            }
            await("Full never committed after resume") { model.ui.value.flow!!.canEnd }
            val full=model.ui.value.flow!!.activeSegment
            assertNotEquals(pre,full)
            instrumentation.runOnMainSync {
                model.startStagePerformance("MissingCourse")
                assertEquals("Wrong course action changed the active track",full,model.ui.value.flow!!.activeSegment)
                assertTrue(model.ui.value.detail.orEmpty().contains("No playable source"))
                model.stop()
                model.startPerformance()
            }
            await("Stop/Start failed to reload its Native output lease") {
                model.ui.value.flow!!.canAdvance && model.ui.value.outputActive && PlaybackHolder.get().state.value.positionFrames>0
            }
            assertEquals(pre,model.ui.value.flow!!.activeSegment)
            instrumentation.runOnMainSync { model.stop() }
        }
    }
}
