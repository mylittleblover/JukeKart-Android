package com.mylittleblover.mkwplayer

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mylittleblover.mkwplayer.player.PlayerViewModel
import com.mylittleblover.mkwplayer.player.PlaybackHolder
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FullVersionControlsDeviceTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private var observedModel: PlayerViewModel?=null
    private fun await(reason: String,timeout: Long=120000,predicate: ()->Boolean) {
        val end=System.currentTimeMillis()+timeout
        var nextRecord=0L
        while(System.currentTimeMillis()<end) {
            if(predicate())return
            if(System.currentTimeMillis()>=nextRecord) {
                var native="unavailable"
                instrumentation.runOnMainSync { native=PlaybackHolder.get().interactiveObservation().toString() }
                val ui=observedModel?.ui?.value
                File(instrumentation.targetContext.filesDir,"full-version-diagnostic.log").appendText(
                    "${System.currentTimeMillis()} waiting=$reason title=${ui?.title} detail=${ui?.detail} flow=${ui?.flow} native=$native\n")
                nextRecord=System.currentTimeMillis()+5000
            }
            Thread.sleep(100)
        }
        fail(reason)
    }
    @Test fun importedCinemaAndSnowMountainSwitchVersionsInFullAndFast() {
        val context=instrumentation.targetContext
        val pack=File(context.filesDir,"research-pack.mkwpack")
        assumeTrue(pack.isFile)
        val report=JSONArray()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: PlayerViewModel
            scenario.onActivity { activity ->
                model=ViewModelProvider(activity,ViewModelProvider.AndroidViewModelFactory.getInstance(context.applicationContext as Application))[PlayerViewModel::class.java]
                observedModel=model
                File(context.filesDir,"full-version-diagnostic.log").writeText("begin\n")
                model.importPack(Uri.fromFile(pack))
            }
            await("Pack import did not complete",900000) { !model.ui.value.busy && model.ui.value.pack!=null }
            for(stage in listOf("Cinema","SnowMountain")) {
                instrumentation.runOnMainSync { model.selectStage(stage) }
                await("$stage version sources unavailable") { model.ui.value.title==stage && model.ui.value.flow?.versionNumbers==listOf(1,2) && model.ui.value.outputActive }
                instrumentation.runOnMainSync { model.startPerformance();model.advancePerformance() }
                await("$stage did not reach base Full") { model.ui.value.flow?.selectedVersion==1 && 2 in model.ui.value.flow!!.availableVersions }
                instrumentation.runOnMainSync { model.selectRecordingVersion(2) }
                await("$stage alternate Full failed") { model.ui.value.flow?.selectedVersion==2 && 1 in model.ui.value.flow!!.availableVersions }
                assertFalse(model.ui.value.flow!!.fastPlaying)
                instrumentation.runOnMainSync { model.toggleFast() }
                await("$stage lost selected version on Fast") { model.ui.value.flow?.fastPlaying==true && model.ui.value.flow?.selectedVersion==2 && 1 in model.ui.value.flow!!.availableVersions }
                instrumentation.runOnMainSync { model.selectRecordingVersion(1) }
                await("$stage Fast return version failed") { model.ui.value.flow?.fastPlaying==true && model.ui.value.flow?.selectedVersion==1 && 2 in model.ui.value.flow!!.availableVersions }
                report.put(JSONObject().put("stage",stage).put("fullVersion2",true).put("fastVersion2",true).put("fastReturnVersion1",true).put("outputActive",model.ui.value.outputActive))
                File(context.filesDir,"full-version-controls-results.json").writeText(report.toString(2))
            }
            instrumentation.runOnMainSync { model.stop() }
        }
        assertEquals(2,report.length())
    }
}
