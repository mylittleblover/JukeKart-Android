package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.graph.MusicGraphBuilder as G
import com.mylittleblover.mkwplayer.library.StageFlowDiscovery
import com.mylittleblover.mkwplayer.library.StageFlowReader
import java.io.File
import kotlin.test.*
import org.junit.Assume.assumeTrue

class DirectBridgePlanTest {
    @Test fun directBridgePreservesTheRealHandoverAndPhysicalTail() {
        val root = File("/workspace/mkw-audio/sources")
        assumeTrue(root.isDirectory)
        val catalog = MinfCatalog.parse(File("src/main/assets/interactive_minf.json").readText())
        for ((stage, exit, start, source) in listOf(
            listOf("FirstCircuit", 1L, -1L, 9389L),
            listOf("IceBuildings", 68571L, 0L, 34286L),
        )) {
            val dir = File(root, "Stage_$stage")
            val bytes = dir.listFiles()!!.filter { it.extension == "bwav" }.associate { it.name to it.readBytes() }
            val groups = StageFlowDiscovery.group(bytes.map { (n, b) -> StageFlowReader.readPart(n, b).getOrThrow() })
            val flow = StageFlowDiscovery.flowFor(StageFlowDiscovery.dominant(groups)!!).getOrThrow()
            val plan = InteractivePlaybackPlan.normal(flow, bytes, catalog)
            val bridge = assertNotNull(plan.directBridge)
            assertTrue(bridge in plan.initialSegments)
            assertEquals(bridge, plan.startingAt("Bridge").entry)
            assertFailsWith<IllegalStateException> { plan.startingAt("Unavailable") }
            val target = plan.entryAutomaticNext.getValue(bridge)
            val edge = plan.spec.transitions.single { it.sources == listOf(bridge) && it.destinations == listOf(target) }
            assertEquals(G.SyncPoint.ExitCue, edge.sync)
            assertTrue(edge.postExit)
            assertEquals(G.TransitionTimeline(0, start as Long, 0, source as Long), edge.timeline)
            val segment = plan.spec.segments.single { it.index == bridge }
            assertEquals(exit as Long, segment.exit)
            assertTrue(plan.spec.tracks.filter { it.segment == bridge }.flatMap { it.clips }.all { it.sourceLength >= segment.exit })
            assertTrue(plan.entryPreparedAssets.getValue(bridge).isNotEmpty())
        }
    }
}
