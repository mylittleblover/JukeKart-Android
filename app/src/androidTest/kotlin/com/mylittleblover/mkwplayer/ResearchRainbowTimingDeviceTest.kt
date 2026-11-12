package com.mylittleblover.mkwplayer

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mylittleblover.mkwplayer.audio.AudioEngine
import com.mylittleblover.mkwplayer.interactive.InteractivePlaybackPlan
import com.mylittleblover.mkwplayer.interactive.MinfCatalog
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Exact Android Native render checks; this does not claim speaker/listening acceptance. */
@RunWith(AndroidJUnit4::class)
class ResearchRainbowTimingDeviceTest {
    @Test fun rainbowCrossfadeAndCorrectedFinalEntryUseTheirActualSampleBoundaries() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val root=File(context.filesDir,"research-sources/Rainbow")
        assumeTrue("Provide the real Rainbow fixtures",root.isDirectory)
        val bytes=root.listFiles()!!.filter { it.extension=="bwav" }.associate { it.name to it.readBytes() }
        val catalog=context.assets.open("interactive_minf.json").bufferedReader().use { MinfCatalog.parse(it.readText()) }
        val plan=InteractivePlaybackPlan.rainbow(bytes,catalog)
        fun id(role: String)=plan.labels.entries.single { it.value==role }.key
        for((from,to) in listOf("Area3" to "Final_Pre","Final_Pre" to "Final")) {
            val engine=AudioEngine.create() ?: error("Native allocation failed")
            try {
                assertEquals(0,engine.loadGraph(plan.packet))
                plan.assetPaths.forEachIndexed { i,path -> assertEquals(0,engine.bindStreamedBwav(i,bytes.getValue(path),path)) }
                var frame=0L
                fun until(end: Long) {
                    while(frame<end) {
                        val count=minOf(512L,end-frame).toInt()
                        val pcm=engine.render(count) ?: error("Render failed")
                        assertTrue(pcm.all { it.isFinite() && kotlin.math.abs(it)<=1f })
                        frame+=count
                    }
                }
                assertEquals(0,engine.play(id(from)))
                until(1)
                assertEquals(0,engine.play(id(to)))
                until(2)
                val transition=plan.spec.transitions.single { it.sources==listOf(id(from)) && it.destinations==listOf(id(to)) }
                val cue=plan.spec.segments.single { it.index==id(from) }.cues.first { it.name==transition.sourceCue && it.frame>2 }.frame
                if(from=="Area3") {
                    until(cue+1)
                    assertEquals(id(to).toLong(),engine.snapshot()!![11])
                    assertEquals("Both sources must sound during the crossfade",2L,engine.snapshot()!![3])
                    until(cue+24000)
                    assertEquals(2L,engine.snapshot()!![3])
                    until(cue+48001)
                    assertEquals("Outgoing voice must end after exactly one second",1L,engine.snapshot()!![3])
                } else {
                    until(cue+1)
                    assertEquals("Pre stops and Fanfare starts at the source cue",1L,engine.snapshot()!![3])
                    val handover=cue+(30_914_669L-30_695_935L)
                    until(handover)
                    assertEquals(id(from).toLong(),engine.snapshot()!![11])
                    until(handover+1)
                    assertEquals("Full enters at the independently measured reference interval",id(to).toLong(),engine.snapshot()!![11])
                    assertEquals("Retain the real Fanfare decay",2L,engine.snapshot()!![3])
                    until(cue+338825)
                    assertEquals("The finite Fanfare source must end naturally",1L,engine.snapshot()!![3])
                }
                assertEquals(0L,engine.snapshot()!![6])
                assertEquals(0L,engine.snapshot()!![7])
            } finally { engine.close() }
        }
        File(context.filesDir,"research-rainbow-timing-result.txt").writeText(
            "PASS: Area3→Final_Pre has two voices for 48000 frames, then one; Final source 0 enters 218734 frames after Fanfare starts and retains its finite decay; Android Native PCM, no speaker claim.\n")
    }
}
