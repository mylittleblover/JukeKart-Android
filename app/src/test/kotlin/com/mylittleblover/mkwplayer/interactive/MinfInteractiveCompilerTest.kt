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
    @Test fun bridgeHandoverMayEqualTheLastSourceFrame() {
        val catalog=MinfCatalog.parse(File("src/main/assets/interactive_minf.json").readText())
        for(stage in listOf("DinoDinoJungle","ToadFactory")) {
            val pre=catalog.part(stage,"Pre")!!
            val bridge=catalog.part(stage,"Bridge")!!
            val full=catalog.part(stage,"Full")!!
            val e=bridge.event("Bridge_ToFull")!!.single()
            val confirmed=ConfirmedOutroCatalog.forStage(stage)!!
            val parts=listOf(
                StageFlow.Part(StageFlow.Role.Pre,"Pre",pre.loopEnd+1,48000,2,pre.loopStart,pre.loopEnd),
                StageFlow.Part(StageFlow.Role.Bridge,"Bridge",e,48000,2,null,null),
                StageFlow.Part(StageFlow.Role.Main,"Full",maxOf(full.loopEnd,confirmed.values.flatten().max())+1,48000,2,full.loopStart,full.loopEnd),
            )+confirmed.keys.map { n -> StageFlow.Part(StageFlow.Role.Outro,"Outro$n",100000,48000,2,null,null,n) }
            val flow=StageFlow("BGM_Stage_$stage",parts,StageFlow.DEFAULT_TIMING)
            val p=MinfInteractiveCompiler.prepare(flow,parts.associate { it.fileName to ByteArray(32) },catalog)
            val transition=p.spec.transitions.first()
            assertEquals(e-bridge.phase,transition.timeline!!.destinationStart)
            assertEquals(full.event("Full_SkipForBridge")!!.single(),transition.timeline!!.destinationSource)
        }
    }
    @Test fun chocoUsesTheUsersNaturalBridgeEndProposalWithoutChangingRawEvidence() {
        val catalog=MinfCatalog.parse(File("src/main/assets/interactive_minf.json").readText())
        val pre=catalog.part("ChocoMountain","Pre")!!
        val full=catalog.part("ChocoMountain","Full")!!
        val confirmed=ConfirmedOutroCatalog.forStage("ChocoMountain")!!
        val length=96000L
        val parts=listOf(
            StageFlow.Part(StageFlow.Role.Pre,"Pre",pre.loopEnd+1,48000,2,pre.loopStart,pre.loopEnd),
            StageFlow.Part(StageFlow.Role.Bridge,"Bridge",length,48000,2,null,null),
            StageFlow.Part(StageFlow.Role.Main,"Full",maxOf(full.loopEnd,confirmed.values.flatten().max())+1,48000,2,full.loopStart,full.loopEnd),
        )+confirmed.keys.map { n -> StageFlow.Part(StageFlow.Role.Outro,"Outro$n",100000,48000,2,null,null,n) }
        val flow=StageFlow("BGM_Stage_ChocoMountain",parts,StageFlow.DEFAULT_TIMING)
        val p=MinfInteractiveCompiler.prepare(flow,parts.associate { it.fileName to ByteArray(32) },catalog)
        assertTrue(catalog.part("ChocoMountain","Bridge")!!.event("Bridge_ToFull")!!.isEmpty())
        assertEquals(length,p.spec.transitions.first().timeline!!.destinationStart)
        assertEquals(38400L,p.spec.transitions.first().timeline!!.destinationSource)
        assertEquals("USER_PROPOSED_BRIDGE_END",p.entryBasis)
    }
    @Test fun explicitFullFromBridgeZeroIsAnEntryNotAnAbsentSkip() {
        val catalog=MinfCatalog.parse(File("src/main/assets/interactive_minf.json").readText())
        val stage="KoopaTroopaBeach"
        val pre=catalog.part(stage,"Pre")!!
        val full=catalog.part(stage,"Full")!!
        val confirmed=ConfirmedOutroCatalog.forStage(stage)!!
        val parts=listOf(
            StageFlow.Part(StageFlow.Role.Pre,"Pre",pre.loopEnd+1,48000,2,pre.loopStart,pre.loopEnd),
            StageFlow.Part(StageFlow.Role.Bridge,"Bridge",196600,48000,2,null,null),
            StageFlow.Part(StageFlow.Role.Main,"Full",maxOf(full.loopEnd,confirmed.values.flatten().max())+1,48000,2,full.loopStart,full.loopEnd),
        )+confirmed.keys.map { n -> StageFlow.Part(StageFlow.Role.Outro,"Outro$n",100000,48000,2,null,null,n) }
        val p=MinfInteractiveCompiler.prepare(StageFlow("BGM_Stage_$stage",parts,StageFlow.DEFAULT_TIMING),parts.associate { it.fileName to ByteArray(32) },catalog)
        assertNull(full.event("Full_SkipForBridge"))
        assertEquals(listOf(0L),full.event("Full_FromBridge"))
        assertEquals(23226L,p.spec.transitions.first().timeline!!.destinationStart)
        assertEquals(0L,p.spec.transitions.first().timeline!!.destinationSource)
        assertEquals(92903L,23226+full.phase) // one Bridge beat plus three Full pickup beats
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
