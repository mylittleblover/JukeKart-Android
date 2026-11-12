package com.mylittleblover.mkwplayer

import android.app.Application
import android.app.NotificationManager
import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mylittleblover.mkwplayer.player.PlayerViewModel
import com.mylittleblover.mkwplayer.player.PlaybackHolder
import com.mylittleblover.mkwplayer.player.PlaybackService
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ResearchPackImportDeviceTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private fun await(reason: String,timeout: Long=120000,predicate: ()->Boolean) {
        val end=System.currentTimeMillis()+timeout
        while(System.currentTimeMillis()<end) { if(predicate()) return;Thread.sleep(100) }
        fail(reason)
    }
    @Test fun deliveredPackImportsAndAllThirtyStagesProduceRealOutput() {
        val context=instrumentation.targetContext
        val pack=File(context.filesDir,"research-pack.mkwpack")
        assumeTrue("Provide the exact deliverable pack",pack.isFile)
        val report=JSONArray()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: PlayerViewModel
            scenario.onActivity { activity ->
                model=ViewModelProvider(activity,ViewModelProvider.AndroidViewModelFactory.getInstance(context.applicationContext as Application))[PlayerViewModel::class.java]
                model.importPack(Uri.fromFile(pack))
            }
            await("Pack import failed or timed out",900000) { !model.ui.value.busy && model.ui.value.pack!=null }
            assertEquals(model.ui.value.detail.orEmpty(),81,model.ui.value.pack!!.trackCount)
            assertEquals(81,model.ui.value.pack!!.playableCount)
            await("Import playback did not publish its required foreground notification") {
                context.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id==PlaybackService.NOTIFICATION_ID }
            }
            val stages=listOf("FirstCircuit","WestCity","Coaster","DonkeyKong","DesertHills","ShyGuyBazaar",
                "WarioStadium","AirshipFortress","DKPass","SnowMountain","IceBuildings","WarioShipyard",
                "KoopaTroopaBeach","DKJungleParkway","Central","PeachBeach","LagoonCity","DinoDinoJungle",
                "StormClouds","RaftingRiver","Cave","Cinema","DinoBones","MooMooFarm","ChocoMountain",
                "ToadFactory","Lava","BigTree","SFCMarioCircuit","Rainbow")
            for(stage in stages) {
                instrumentation.runOnMainSync { model.selectStage(stage+"_00") }
                await("Installed $stage did not load: ${model.ui.value.detail}") {
                    model.ui.value.title==stage && model.ui.value.flow!=null && model.ui.value.outputActive
                }
                if(stage!="Rainbow") assertTrue("Fast missing after pack import: $stage",model.ui.value.flow!!.hasFast)
                var position: Long?=null
                await("$stage output never advanced") {
                    instrumentation.runOnMainSync { position=PlaybackHolder.get().interactiveObservation()?.sourceFrame }
                    (position ?: -1)>1024
                }
                report.put(JSONObject().put("stage",stage).put("sourceFrame",position).put("outputActive",true)
                    .put("hasFast",model.ui.value.flow!!.hasFast).put("status","IMPORTED_PACK_ANDROID_PLAYBACK_PASS"))
                File(context.filesDir,"research-pack-playback-results.json").writeText(report.toString(2))
                if(stage=="FirstCircuit") {
                    instrumentation.runOnMainSync { model.startPerformance();model.advancePerformance() }
                    await("Imported FirstCircuit never committed its Pre/Bridge→Full transition") { model.ui.value.flow!!.canEnd }
                }
            }
            instrumentation.runOnMainSync { model.stop() }
        }
        assertEquals(30,report.length())
    }
}
