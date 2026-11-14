package com.mylittleblover.mkwplayer

import android.app.Application
import android.app.NotificationManager
import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mylittleblover.mkwplayer.player.PlayerViewModel
import com.mylittleblover.mkwplayer.player.PlaybackService
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DonkeyRegionControlsDeviceTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private fun await(reason: String,timeout: Long=120000,predicate: ()->Boolean) {
        val end=System.currentTimeMillis()+timeout
        while(System.currentTimeMillis()<end) { if(predicate())return;Thread.sleep(50) }
        fail(reason)
    }
    @Test fun installedDonkeyRegionsFollowNativeAndCanCancelBeforeTargetStarts() {
        val context=instrumentation.targetContext
        val pack=File(context.filesDir,"research-pack.mkwpack")
        assumeTrue(pack.isFile)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: PlayerViewModel
            scenario.onActivity { activity ->
                model=ViewModelProvider(activity,ViewModelProvider.AndroidViewModelFactory.getInstance(context.applicationContext as Application))[PlayerViewModel::class.java]
                model.importPack(Uri.fromFile(pack))
            }
            await("Import failed",900000) { !model.ui.value.busy && model.ui.value.pack!=null }
            instrumentation.runOnMainSync { model.selectStage("DonkeyKong") }
            await("All five regions unavailable") { model.ui.value.flow?.regionNumbers==(1..5).toList() && model.ui.value.outputActive }
            instrumentation.runOnMainSync { model.startPerformance();model.advancePerformance() }
            await("Base Full not reached") { model.ui.value.flow?.selectedRegion==1 && 2 in model.ui.value.flow!!.availableRegions }
            instrumentation.runOnMainSync { model.selectMusicRegion(2) }
            await("Region request never became cancellable") { model.ui.value.flow?.canCancelPending==true }
            assertEquals(1,model.ui.value.flow!!.selectedRegion)
            instrumentation.runOnMainSync { model.cancelPendingPerformance() }
            await("Cancellation not acknowledged by Native") { model.ui.value.flow?.selectedRegion==1 && 2 in model.ui.value.flow!!.availableRegions }
            for(region in listOf(2,3,4,5)) {
                instrumentation.runOnMainSync { model.selectMusicRegion(region) }
                await("Region $region did not start") { model.ui.value.flow?.selectedRegion==region && model.ui.value.flow!!.canAdvance }
                assertEquals(if(region==5) emptySet<Int>() else setOf(region+1),model.ui.value.flow!!.availableRegions)
                assertEquals(region==5,model.ui.value.flow!!.canFast)
                assertFalse(model.ui.value.flow!!.canEnd)
            }
            instrumentation.runOnMainSync { model.advancePerformance() }
            await("Pattern 5 did not advance to Fast") { model.ui.value.flow?.fastPlaying==true && !model.ui.value.flow!!.canAdvance }
            assertTrue(model.ui.value.flow!!.availableRegions.isEmpty())
            instrumentation.runOnMainSync { model.stop() }
            assertTrue(model.ui.value.flow!!.availableRegions.isEmpty())
            assertFalse(model.ui.value.flow!!.canCancelPending)
            assertFalse(model.ui.value.flow!!.canFast)
            instrumentation.runOnMainSync { model.startPerformance() }
            await("Stopped course did not restart") { model.ui.value.flow?.canAdvance==true && model.ui.value.outputActive }
            Thread.sleep(2000) // Cross the old delayed service-stop window.
            assertTrue(context.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id==PlaybackService.NOTIFICATION_ID })
            File(context.filesDir,"donkey-region-controls-results.json").writeText(JSONObject().put("regions",5).put("cancelBeforeTarget",true).put("advancedToFast",true).put("restartKeepsForeground",true).put("outputActive",model.ui.value.outputActive).toString(2))
            instrumentation.runOnMainSync { model.stop() }
        }
    }
}
