package com.mylittleblover.mkwplayer

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mylittleblover.mkwplayer.audio.AudioEngine
import com.mylittleblover.mkwplayer.audio.AudioOutput
import com.mylittleblover.mkwplayer.interactive.InteractivePlaybackPlan
import com.mylittleblover.mkwplayer.interactive.MinfCatalog
import com.mylittleblover.mkwplayer.player.PlaybackSession
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ResearchAutoOutroDeviceTest {
    @Test fun rainbowOutroAutomaticallyReachesTheNextPreAtItsNativeMarkerWithoutAViewModel() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val root=File(context.filesDir,"research-sources/Rainbow")
        assumeTrue("Provide real Rainbow sources",root.isDirectory)
        val bytes=root.listFiles()!!.filter { it.extension=="bwav" }.associate { it.name to it.readBytes() }
        val catalog=context.assets.open("interactive_minf.json").bufferedReader().use { MinfCatalog.parse(it.readText()) }
        val plan=InteractivePlaybackPlan.rainbow(bytes,catalog)
        val source=plan.outros.getValue(0);val target=plan.automaticNext.getValue(source)
        val engine=AudioEngine.create() ?: error("Native allocation failed")
        val session=PlaybackSession({engine},{handle,rate -> AudioOutput.open(handle,rate)})
        try {
            assertNull(session.loadFlow("Rainbow marker",plan.packet,plan.assetPaths.map { it to bytes.getValue(it) },
                1000000,source,streamed=true,autoAdvance=plan.automaticNext).detail)
            assertTrue("Device output did not open",session.hasOutput)
            val deadline=System.currentTimeMillis()+120000
            var nativeSync: Long?=null
            var reached=false
            while(System.currentTimeMillis()<deadline) {
                session.poll()
                val raw=engine.snapshot()!!
                if(raw[24]!=0L) nativeSync=raw[30]
                val observation=session.interactiveObservation()!!
                assertNull(observation.error)
                if(observation.segment==target && (observation.sourceFrame ?: -1)>0) { reached=true;break }
                Thread.sleep(50)
            }
            assertTrue("The authored Outro marker did not reach Area3_Pre automatically",reached)
            assertEquals(382784L,nativeSync)
            File(context.filesDir,"research-auto-outro-result.txt").writeText("PASS: Native sourceSync=382784, target=Area3_Pre, no ViewModel or user advance event\n")
        } finally { session.release() }
    }
}
