package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.graph.MusicGraphBuilder as G
import com.mylittleblover.mkwplayer.graph.StageFlowCompiler
import com.mylittleblover.mkwplayer.library.StageFlow

/** Compiles original sample nodes. Unsupported/ambiguous research never falls back to guessed timing. */
internal object MinfInteractiveCompiler {
    data class Prepared(val spec: G.Spec, val compiled: StageFlowCompiler.Compiled)

    fun prepare(flow: StageFlow, digests: Map<String, ByteArray>, catalog: MinfCatalog): Prepared {
        val stage=flow.name.removePrefix("BGM_Stage_")
        require(stage!="Rainbow") { "Rainbow requires its Area/Final graph" }
        val full=catalog.part(stage,"Full") ?: error("No unambiguous Full MINF for $stage")
        val skip=full.event("Full_SkipForBridge")?.singleOrNull()
        val ordered=flow.parts.filter { it.role!=StageFlow.Role.Outro }.sortedBy { it.role.ordinal } + flow.outros().sortedBy { it.outroNumber }
        val indices=ordered.mapIndexed { i,p -> p.fileName to (100+i) }.toMap()
        val byRole=ordered.filter { it.role!=StageFlow.Role.Outro }.associate { it.role to indices.getValue(it.fileName) }
        val byOutro=flow.outros().associate { it.outroNumber!! to indices.getValue(it.fileName) }
        val main=byRole.getValue(StageFlow.Role.Main)
        val fast=catalog.part(stage,"Fast")
        val hasEntry=flow.parts.any { it.role==StageFlow.Role.Pre || it.role==StageFlow.Role.PreShort }
        val bridge=if(hasEntry) catalog.part(stage,"Bridge") else null
        val bridgePart=flow.part(StageFlow.Role.Bridge)
        require((bridge==null)==(bridgePart==null)) { "Bridge audio/MINF mismatch for $stage" }
        val bridgeExit=bridge?.event("Bridge_ToFull")?.singleOrNull()
        if(bridge!=null) require(bridgeExit!=null && bridgeExit>=0 && bridgeExit<bridgePart!!.frames) {
            "$stage requires a separately verified layered/missing-marker bridge plan"
        }
        val confirmed=ConfirmedOutroCatalog.forStage(stage) ?: error("No confirmed Outro mapping for $stage")
        var cueIndex=1000
        val segments=ordered.map { part ->
            val id=indices.getValue(part.fileName)
            when(part.role) {
                StageFlow.Role.Pre,StageFlow.Role.PreShort -> {
                    val role=if(part.role==StageFlow.Role.Pre) "Pre" else "Pre_Short"
                    val r=catalog.part(stage,role) ?: error("No $role MINF for $stage")
                    require(part.loopStart==r.loopStart && part.loopEnd==r.loopEnd) { "$stage $role source loop differs from MINF" }
                    require(r.loopCandidates.isNotEmpty())
                    G.Segment(id,exit=part.frames,loop=G.Loop(r.loopStart,r.loopEnd),
                        cues=r.loopCandidates.map { G.Cue(cueIndex++,"pre.exit",it) })
                }
                StageFlow.Role.Bridge -> G.Segment(id,exit=part.frames)
                StageFlow.Role.Main -> G.Segment(id,exit=part.frames,
                    loop=part.loopStart?.let { G.Loop(it,part.loopEnd!!) },
                    cues=(confirmed.flatMap { (n,points) -> points.map { point ->
                        require(point<part.frames) { "$stage Outro$n trigger outside Full source" }
                        G.Cue(cueIndex++,"outro.$n",point)
                    } } + if(fast!=null && byRole[StageFlow.Role.Fast]!=null) full.loopCandidates.map { G.Cue(cueIndex++,"fast.exit",it) } else emptyList()).sortedBy { it.frame })
                StageFlow.Role.Outro -> G.Segment(id,exit=part.frames)
                StageFlow.Role.Fast -> G.Segment(id,exit=part.frames,loop=part.loopStart?.let { G.Loop(it,part.loopEnd!!) })
            }
        }
        var rule=2000
        val transitions=buildList {
            for(role in listOf(StageFlow.Role.Pre,StageFlow.Role.PreShort)) byRole[role]?.let {
                add(G.Transition(rule++,listOf(it),listOf(main),sync=G.SyncPoint.NextCustomCue,
                    sourceCue="pre.exit",bridge=byRole[StageFlow.Role.Bridge] ?: -1,postExit=true,
                    timeline=if(bridge==null) G.TransitionTimeline(0,-full.phase,0,0) else {
                        val handover=bridgeExit!!-bridge.phase
                        G.TransitionTimeline(-bridge.phase,
                            if(skip==null) handover-full.phase else handover,
                            maxOf(0,handover),skip ?: 0)
                    }))
            }
            byRole[StageFlow.Role.Fast]?.let { target ->
                require(fast!=null && full.loopCandidates.isNotEmpty()) { "$stage has no source grid/Fast phase plan" }
                // Research phase-alignment preview. No rate change; Fast is its own real asset.
                add(G.Transition(rule++,listOf(main),listOf(target),sync=G.SyncPoint.NextCustomCue,
                    sourceCue="fast.exit",timeline=G.TransitionTimeline(0,-fast.phase,0,0)))
            }
            for((n,to) in byOutro) {
                require(n in confirmed) { "$stage Outro$n has no confirmed Full exit" }
                add(G.Transition(rule++,listOf(main),listOf(to),sync=G.SyncPoint.NextCustomCue,sourceCue="outro.$n"))
            }
        }
        val spec=G.Spec(
            assets=ordered.map { p -> G.Asset(p.fileName,p.sampleRate,p.channels,p.frames,digests.getValue(p.fileName)) },
            segments=segments,
            tracks=ordered.mapIndexed { i,p -> G.Track(300+i,indices.getValue(p.fileName),listOf(G.Clip(500+i,i,sourceLength=p.frames))) },
            transitions=transitions,bpm=full.bpm,
        )
        return Prepared(spec,StageFlowCompiler.Compiled(G.build(spec),flow,byRole,byOutro,ordered.map { it.fileName }))
    }
}
