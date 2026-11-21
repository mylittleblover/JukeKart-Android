package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.graph.MusicGraphBuilder as G

/** Rainbow's independent regions; all offsets are source sample coordinates. */
internal object RainbowInteractiveCompiler {
    // Corrected user reference: Fan source 0 at 30695935; Final source 0 at 30914669.
    // Multiple independent 48 kHz windows agree. This is not a named MINF event.
    private const val FINAL_FROM_FANFARE_FRAMES=218_734L
    val roles = listOf("Area1_Pre","Area1_Bridge","Area1_Inst","Area1_Melody","Area2",
        "Area2_Outro00","Area2_Outro01","Area2_Outro02","Area2_Outro03","Area2_Outro04",
        "Area3_Pre","Area3","Final_Pre","Final","FinalLapFanfare")
    data class Source(val fileName: String,val sampleRate: Int,val channels: Int,val frames: Long,
                      val loopStart: Long?,val loopEnd: Long?,val sha256: ByteArray)
    data class Prepared(val spec: G.Spec,val packet: ByteArray,val segments: Map<String,Int>,val assets: List<Source>)
    fun prepare(
        sources: List<Source>,
        catalog: MinfCatalog,
        allowPreludeToFast: Boolean = false,
        perfectBeatEnabled: Boolean = false,
    ): Prepared {
        require(sources.size==roles.size && sources.map { it.fileName }.distinct().size==roles.size)
        val assets=roles.map { role -> sources.single { it.fileName=="BGM_Rainbow_$role.bwav" } }
        val byRole=roles.zip(assets).toMap()
        fun meta(role: String)=catalog.part("Rainbow",role) ?: error("Missing Rainbow MINF: $role")
        assets.forEachIndexed { i,s ->
            val r=meta(roles[i])
            require(s.sampleRate==r.rate && s.channels in 1..2 && s.sha256.size==32 && s.frames>=r.loopEnd)
            if(r.loopStart<r.loopEnd) require(s.loopStart==r.loopStart && s.loopEnd==r.loopEnd)
        }
        val inst=byRole.getValue("Area1_Inst");val melody=byRole.getValue("Area1_Melody")
        require(inst.frames==melody.frames && inst.loopStart==melody.loopStart && inst.loopEnd==melody.loopEnd &&
            meta("Area1_Inst").phase==meta("Area1_Melody").phase)
        val names=listOf("Area1_Pre","Area1_Bridge","Area1","Area2","Area2_Outro00","Area2_Outro01",
            "Area2_Outro02","Area2_Outro03","Area2_Outro04","Area3_Pre","Area3","Final_Pre","Final","FinalLapFanfare")
        val ids=names.mapIndexed { i,n -> n to 1001+i }.toMap()
        fun source(name: String)=byRole.getValue(if(name=="Area1") "Area1_Inst" else name)
        fun record(name: String)=meta(if(name=="Area1") "Area1_Inst" else name)
        var cueId=1500
        val segments=names.map { name ->
            val s=source(name);val r=record(name)
            val cues=buildList {
                if(name.endsWith("_Pre")) {
                    r.loopCandidates.forEach { add(G.Cue(cueId++,"pre.exit",it)) }
                    if (allowPreludeToFast && perfectBeatEnabled) {
                        r.beats.filter { it.label == 1 && it.frame in 0L..s.frames }
                            .forEach { add(G.Cue(cueId++, "beat1.exit", it.frame)) }
                    }
                }
                if(name in listOf("Area1","Area3")) r.beats.filter { it.label==1 }.forEach { add(G.Cue(cueId++,"bar.exit",it.frame)) }
                if(name=="Area2") (0..4).forEach { n ->
                    val label="Full_ToOutro%02d".format(n)
                    add(G.Cue(cueId++,label,r.event(label)!!.single()))
                }
            }.sortedBy { it.frame }
            val exit=when {
                name.startsWith("Area2_Outro") -> r.event("Outro_ToNextPre")!!.single()
                name=="FinalLapFanfare" -> FINAL_FROM_FANFARE_FRAMES
                else -> s.frames
            }
            require(exit in 1..s.frames)
            G.Segment(ids.getValue(name),exit=exit,
                loop=s.loopStart?.let { G.Loop(it,s.loopEnd!!) },cues=cues)
        }
        var trackId=100;var clipId=200
        val tracks=names.flatMap { name ->
            (if(name=="Area1") listOf("Area1_Melody","Area1_Inst") else listOf(name)).map { role ->
                val asset=roles.indexOf(role)
                G.Track(trackId++,ids.getValue(name),listOf(G.Clip(clipId++,asset,sourceLength=assets[asset].frames)))
            }
        }
        var transitionId=300
        val transitions=mutableListOf<G.Transition>()
        fun link(from: String,to: String,cue: String?="bar.exit",bridge: String?=null,
                 sync: G.SyncPoint=G.SyncPoint.NextCustomCue,tail: Boolean=false,
                 crossfadeSeconds: Double=0.0,
                 timeline: G.TransitionTimeline=G.TransitionTimeline(0,-record(to).phase,0,0)) {
            val fade=G.Fade(beats=crossfadeSeconds*meta("Area1_Inst").bpm/60.0)
            transitions+=G.Transition(transitionId++,listOf(ids.getValue(from)),listOf(ids.getValue(to)),
                sync=sync,sourceCue=cue,bridge=bridge?.let { ids.getValue(it) } ?: -1,
                postExit=tail,fadeIn=fade,fadeOut=fade,timeline=timeline)
        }
        val bridgeExit=meta("Area1_Bridge").event("Bridge_ToFull")!!.single()
        val bridgePhase=meta("Area1_Bridge").phase
        link("Area1_Pre","Area1","pre.exit","Area1_Bridge",tail=true,
            timeline=G.TransitionTimeline(-bridgePhase,bridgeExit-bridgePhase-meta("Area1_Inst").phase,bridgeExit-bridgePhase,0))
        // User-confirmed region policy: leave at original label-1 bar nodes, start target by MINF phase.
        link("Area1","Area2")
        link("Area3_Pre","Area3","pre.exit")
        // User-confirmed: overlap both sources for one second from the source bar cue.
        link("Area3","Final_Pre",tail=true,crossfadeSeconds=1.0)
        // Pre stops at G; Fanfare starts at G; Full enters before the Fanfare decay ends.
        link("Final_Pre","Final","pre.exit","FinalLapFanfare",tail=true,
            timeline=G.TransitionTimeline(0,FINAL_FROM_FANFARE_FRAMES,FINAL_FROM_FANFARE_FRAMES,0))
        link("FinalLapFanfare","Final",null,sync=G.SyncPoint.ExitCue,tail=true)
        if (allowPreludeToFast) {
            for (preName in listOf("Area1_Pre", "Area3_Pre")) {
                link(
                    from = preName,
                    to = "Final",
                    cue = if (perfectBeatEnabled) "beat1.exit" else null,
                    bridge = "FinalLapFanfare",
                    sync = if (perfectBeatEnabled) G.SyncPoint.NextCustomCue else G.SyncPoint.Immediate,
                    tail = true,
                    timeline = G.TransitionTimeline(0, FINAL_FROM_FANFARE_FRAMES, FINAL_FROM_FANFARE_FRAMES, 0),
                )
            }
        }
        for(n in 0..4) {
            val outro="Area2_Outro%02d".format(n)
            link("Area2",outro,"Full_ToOutro%02d".format(n),timeline=G.TransitionTimeline(0,0,0,0))
            link(outro,"Area3_Pre",null,sync=G.SyncPoint.ExitCue,tail=true)
        }
        val spec=G.Spec(assets=assets.map { G.Asset(it.fileName,it.sampleRate,it.channels,it.frames,it.sha256) },
            segments=segments,tracks=tracks,transitions=transitions,bpm=meta("Area1_Inst").bpm)
        return Prepared(spec,G.build(spec),ids,assets)
    }
}
