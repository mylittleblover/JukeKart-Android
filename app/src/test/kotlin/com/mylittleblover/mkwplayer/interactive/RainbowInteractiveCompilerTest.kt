package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.graph.MusicGraphBuilder as G
import java.io.File
import kotlin.test.*

class RainbowInteractiveCompilerTest {
    private val catalog = MinfCatalog.parse(File("src/main/assets/interactive_minf.json").readText())
    private fun prepare(): RainbowInteractiveCompiler.Prepared {
        val sources = RainbowInteractiveCompiler.roles.map { role ->
            val r = catalog.part("Rainbow", role)!!
            RainbowInteractiveCompiler.Source("BGM_Rainbow_$role.bwav",48000,2,r.loopEnd+10000,
                if(r.loopStart<r.loopEnd) r.loopStart else null,
                if(r.loopStart<r.loopEnd) r.loopEnd else null,ByteArray(32))
        }
        return RainbowInteractiveCompiler.prepare(sources,catalog)
    }
    @Test fun area1UsesTwoDistinctSynchronizedLayers() {
        val p=prepare(); val id=p.segments.getValue("Area1")
        val tracks=p.spec.tracks.filter { it.segment==id }
        assertEquals(2,tracks.size)
        assertNotEquals(tracks[0].clips.single().asset,tracks[1].clips.single().asset)
        assertTrue(tracks.all { it.clips.single().sourceOffset==0L })
    }
    @Test fun areaChangesUseOriginalBarNodesAndDestinationPhaseLead() {
        val p=prepare()
        for ((source,target,phase) in listOf(Triple("Area1","Area2",11392L),Triple("Area3","Final_Pre",0L))) {
            val t=p.spec.transitions.single { it.sources==listOf(p.segments.getValue(source)) && it.destinations==listOf(p.segments.getValue(target)) }
            assertEquals(G.SyncPoint.NextCustomCue,t.sync)
            assertEquals("bar.exit",t.sourceCue)
            assertEquals(-phase,t.timeline!!.destinationStart)
            assertEquals(0L,t.timeline!!.destinationCommit)
            val meta=catalog.part("Rainbow",if(source=="Area1") "Area1_Inst" else source)!!
            val cues=p.spec.segments.single { it.index==p.segments.getValue(source) }.cues.filter { it.name=="bar.exit" }
            assertEquals(meta.beats.filter { it.label==1 }.map { it.frame },cues.map { it.frame })
        }
    }
    @Test fun everyOutroHasItsOwnSourceExitAndNaturalTail() {
        val p=prepare()
        for(n in 0..4) {
            val id=p.segments.getValue("Area2_Outro%02d".format(n))
            val segment=p.spec.segments.single { it.index==id }
            val t=p.spec.transitions.single { it.sources==listOf(id) }
            assertEquals(382784L,segment.exit)
            assertEquals(G.SyncPoint.ExitCue,t.sync)
            assertTrue(t.postExit)
            assertEquals(p.segments.getValue("Area3_Pre"),t.destinations.single())
            assertTrue(p.spec.tracks.single { it.segment==id }.clips.single().sourceLength>segment.exit)
        }
    }
    @Test fun finalPreAlwaysPassesThroughFanfareBeforeFull() {
        val p=prepare();val id=p.segments.getValue("FinalLapFanfare")
        val pre=p.spec.transitions.single { it.sources==listOf(p.segments.getValue("Final_Pre")) }
        assertEquals(id,pre.bridge)
        assertEquals(p.segments.getValue("Final"),pre.destinations.single())
        assertEquals(0L,pre.timeline!!.bridgeStart)
        val duration=p.assets.single { it.fileName=="BGM_Rainbow_FinalLapFanfare.bwav" }.frames
        assertEquals(duration,pre.timeline!!.destinationStart)
        assertEquals(duration,pre.timeline!!.destinationCommit)
        val t=p.spec.transitions.single { it.sources==listOf(id) }
        assertEquals(G.SyncPoint.ExitCue,t.sync)
        assertEquals(p.segments.getValue("Final"),t.destinations.single())
        assertFalse(p.spec.transitions.any { it.sources==listOf(p.segments.getValue("Area3")) && id in it.destinations })
        assertNull(p.spec.segments.single { it.index==id }.loop)
    }
}
