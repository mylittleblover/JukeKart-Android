package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.graph.MusicGraphBuilder as G
import com.mylittleblover.mkwplayer.library.StageFlowDiscovery
import com.mylittleblover.mkwplayer.library.StageFlowReader
import java.io.File
import kotlin.test.*
import org.junit.Assume.assumeTrue

class FullVersionPlanTest {
    @Test fun sameSpeedVersionsPreservePositionAndFadeForOneSecondWithoutChangingFastOwnership() {
        assumeTrue(File("/workspace/mkw-audio/sources/Stage_Cinema").isDirectory)
        val catalog=MinfCatalog.parse(File("src/main/assets/interactive_minf.json").readText())
        for(stage in listOf("Cinema","SnowMountain")) {
            val bytes=File("/workspace/mkw-audio/sources/Stage_$stage").listFiles()!!.filter { it.extension=="bwav" }.associate { it.name to it.readBytes() }
            val groups=StageFlowDiscovery.group(bytes.map { (n,b)->StageFlowReader.readPart(n,b).getOrThrow() })
            val flow=StageFlowDiscovery.flowFor(StageFlowDiscovery.dominant(groups)!!).getOrThrow()
            val p=InteractivePlaybackPlan.normal(flow,bytes,catalog)
            val altMain=p.labels.entries.singleOrNull { it.value=="Main Pattern2" }?.key
            assertTrue(p.streamed,"Combined variants exceed the Native 128 MiB prepared budget")
            assertNotNull(altMain,"$stage alternate Full missing from production graph")
            val main=p.labels.entries.single { it.value=="Main" }.key
            val fast=p.labels.entries.single { it.value=="Fast" }.key
            val altFast=p.labels.entries.single { it.value=="Fast Pattern2" }.key
            for((a,b) in listOf(main to altMain,altMain to main,fast to altFast,altFast to fast)) {
                val rule=p.spec.transitions.single { it.sources==listOf(a) && it.destinations==listOf(b) }
                assertEquals(G.EntryMode.SameTime,rule.entry)
                assertEquals(G.SyncPoint.Immediate,rule.sync)
                assertEquals(48000.0,rule.fadeIn.beats*60/p.spec.bpm*p.spec.transportSampleRate,0.0001)
                assertEquals(rule.fadeIn,rule.fadeOut)
                assertTrue(rule.postExit)
                assertNull(rule.timeline,"A fixed destinationSource would defeat SameTime")
            }
            val speedRule=p.spec.transitions.single { it.sources==listOf(altMain) && it.destinations==listOf(altFast) }
            assertEquals(G.EntryMode.EntryCue,speedRule.entry)
            assertEquals(G.SyncPoint.NextCustomCue,speedRule.sync)
            assertTrue(p.assetPaths.all { it in bytes })
            assertContentEquals(p.packet,p.standard!!.packet)
            assertEquals(p.assetPaths,p.standard.assetPaths)
        }
    }
}
