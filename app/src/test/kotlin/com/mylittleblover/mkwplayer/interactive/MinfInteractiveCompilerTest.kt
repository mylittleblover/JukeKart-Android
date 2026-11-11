package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.graph.MusicGraphBuilder
import com.mylittleblover.mkwplayer.library.StageFlow
import java.io.File
import kotlin.test.*

class MinfInteractiveCompilerTest {
    private fun prepare(): MinfInteractiveCompiler.Prepared {
        val catalog = MinfCatalog.parse(File("src/main/assets/interactive_minf.json").readText())
        val parts = listOf(
            StageFlow.Part(StageFlow.Role.Pre, "Pre", 4_000_000,48000,2,416953,3296953),
            StageFlow.Part(StageFlow.Role.PreShort, "Short", 1_000_000,48000,2,169453,889453),
            StageFlow.Part(StageFlow.Role.Bridge, "Bridge", 100000,48000,2,null,null),
            StageFlow.Part(StageFlow.Role.Main, "Main", 4_000_000,48000,2,725625,3245625),
        ) + (0..4).map { n -> StageFlow.Part(StageFlow.Role.Outro,"Outro$n",500000,48000,2,null,null,n) }
        val flow=StageFlow("BGM_Stage_WestCity",parts,StageFlow.DEFAULT_TIMING)
        return MinfInteractiveCompiler.prepare(flow,parts.associate { it.fileName to ByteArray(32) },catalog)
    }
    @Test fun preludeSyncUsesEveryOriginalCandidateAndNeverNextBar() {
        val p=prepare();val pre=p.spec.segments.first { it.index==p.compiled.segmentByRole[StageFlow.Role.Pre] }
        assertEquals(33,pre.cues.size)
        assertEquals(416953L,pre.cues.first().frame)
        val t=p.spec.transitions.first { it.sources==listOf(pre.index) }
        assertEquals(MusicGraphBuilder.SyncPoint.NextCustomCue,t.sync)
        assertEquals(-49921L,t.timeline!!.bridgeStart)
        assertEquals(-5624L,t.timeline!!.destinationStart)
    }
    @Test fun bridgeEntryAndHandoverExpressTheTwoDistinctMinfCoordinates() {
        val p=prepare();val b=p.spec.segments.first { it.index==p.compiled.segmentByRole[StageFlow.Role.Bridge] }
        assertEquals(0L,b.entry)
        assertEquals(100000L,b.exit)
        assertNull(b.loop)
        assertEquals(0L,p.spec.segments.first { it.index==p.compiled.segmentByRole[StageFlow.Role.Main] }.entry)
    }
    @Test fun eachOutroHasAllConfirmedCuePositionsAndNoOutroReplacementAfterStart() {
        val p=prepare();val main=p.compiled.segmentByRole.getValue(StageFlow.Role.Main)
        val cues=p.spec.segments.first { it.index==main }.cues
        assertEquals(13,cues.size)
        assertEquals(8,cues.count { it.name=="outro.0" })
        val outroIds=p.compiled.segmentByOutro.values
        assertTrue(p.spec.transitions.none { it.sources.any { n -> n in outroIds } })
        assertTrue(p.spec.transitions.filter { it.destinations.any { n -> n in outroIds } }.all {
            it.sync==MusicGraphBuilder.SyncPoint.NextCustomCue && it.sourceCue!!.startsWith("outro.")
        })
    }
}
