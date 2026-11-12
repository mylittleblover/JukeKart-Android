package com.mylittleblover.mkwplayer

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mylittleblover.mkwplayer.audio.AudioEngine
import com.mylittleblover.mkwplayer.interactive.InteractivePlaybackPlan
import com.mylittleblover.mkwplayer.interactive.MinfCatalog
import com.mylittleblover.mkwplayer.library.StageFlowDiscovery
import com.mylittleblover.mkwplayer.library.StageFlowReader
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Uses supplied BWAVs and the exact preparation path used by PlayerViewModel. */
@RunWith(AndroidJUnit4::class)
class ResearchInteractiveDeviceTest {
    @Test fun allThirtyGraphsBindAndProducePcmOnAndroid() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val root=File(context.filesDir,"research-sources")
        assumeTrue("Provide real audio fixtures under $root",root.isDirectory)
        val catalog=context.assets.open("interactive_minf.json").bufferedReader().use { MinfCatalog.parse(it.readText()) }
        val directories=root.listFiles()!!.filter { it.isDirectory && (it.name.startsWith("Stage_") || it.name=="Rainbow") }.sortedBy { it.name }
        assertEquals(30,directories.size)
        val report=JSONArray()
        for(dir in directories) {
            val bytes=dir.listFiles()!!.filter { it.extension=="bwav" }.associate { it.name to it.readBytes() }
            val plan=if(dir.name=="Rainbow") InteractivePlaybackPlan.rainbow(bytes,catalog) else {
                val parts=bytes.map { (name,data) -> StageFlowReader.readPart(name,data).getOrThrow() }
                val group=StageFlowDiscovery.dominant(StageFlowDiscovery.group(parts))!!
                InteractivePlaybackPlan.normal(StageFlowDiscovery.flowFor(group).getOrThrow(),bytes,catalog)
            }
            val engine=AudioEngine.create() ?: error("Native allocation failed")
            try {
                assertEquals("${dir.name}: graph",0,engine.loadGraph(plan.packet))
                plan.assetPaths.forEachIndexed { i,path ->
                    val status=if(plan.streamed) engine.bindStreamedBwav(i,bytes.getValue(path),path)
                        else engine.bindBwav(i,bytes.getValue(path),path)
                    assertEquals("${dir.name}: $path",0,status)
                }
                assertEquals(0,engine.play(plan.entry))
                var peak=0f;var nonzero=0
                repeat(94) {
                    val block=engine.render(512) ?: error("${dir.name}: render failed")
                    block.forEach { sample ->
                        assertTrue(sample.isFinite());peak=maxOf(peak,kotlin.math.abs(sample));if(sample!=0f) nonzero++
                    }
                }
                val snapshot=engine.snapshot()!!
                assertEquals("${dir.name}: rejected commands",0L,snapshot[6])
                assertEquals("${dir.name}: Native status",0L,snapshot[7])
                assertTrue("${dir.name}: silent output",nonzero>0)
                assertTrue("${dir.name}: clipping $peak",peak<=1f)
                report.put(JSONObject().put("stage",dir.name).put("frames",48128).put("peak",peak.toDouble()).put("nonzeroSamples",nonzero)
                    .put("deviceOutput",false).put("status","ANDROID_NATIVE_PCM_PASS"))
            } finally { engine.close() }
        }
        File(context.filesDir,"research-android-pcm-results.json").writeText(report.toString(2))
    }
}
