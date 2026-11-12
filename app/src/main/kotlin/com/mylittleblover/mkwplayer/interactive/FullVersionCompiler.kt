package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.graph.MusicGraphBuilder as G
import com.mylittleblover.mkwplayer.library.StageFlow
import com.mylittleblover.mkwplayer.library.StageFlowReader
import java.security.MessageDigest

/** Same-speed alternate recordings; a speed change keeps its existing MINF transition. */
internal object FullVersionCompiler {
    fun extend(base: InteractivePlaybackPlan,flow: StageFlow,bytes: Map<String,ByteArray>,catalog: MinfCatalog): InteractivePlaybackPlan {
        val stage=flow.name.removePrefix("BGM_Stage_")
        if(stage !in setOf("Cinema","SnowMountain")) return base
        val alternateName="${stage}_Pattern2"
        val mainName="BGM_Stage_${stage}_Pattern2.bwav"
        val fastName="BGM_Stage_${stage}_Fast_Pattern2.bwav"
        if(mainName !in bytes && fastName !in bytes) return base
        require(mainName in bytes && fastName in bytes) { "$stage requires both alternate Full and Fast sources" }
        for(role in listOf("Full","Fast")) {
            val a=catalog.part(stage,role) ?: error("Missing $stage $role MINF")
            val b=catalog.part(alternateName,role) ?: error("Missing $alternateName $role MINF")
            require(a.rate==b.rate && a.bpm==b.bpm && a.phase==b.phase && a.loopStart==b.loopStart && a.loopEnd==b.loopEnd && a.beats==b.beats) {
                "$stage $role versions do not share an exact source grid"
            }
        }
        val parts=listOf(mainName,fastName).map { name -> StageFlowReader.readPart(name,bytes.getValue(name)).getOrThrow() }+
            flow.outros().filter { part -> ConfirmedOutroCatalog.forStage(alternateName)?.containsKey(part.outroNumber)==true }
        val alternate=flow.copy(name="BGM_Stage_$alternateName",parts=parts)
        val prepared=MinfInteractiveCompiler.prepare(alternate,parts.associate { p ->
            p.fileName to MessageDigest.getInstance("SHA-256").digest(bytes.getValue(p.fileName))
        },catalog)
        val other=prepared.spec
        fun owned(s: G.Spec)=s.segments.map { it.index }+s.tracks.map { it.index }+
            s.transitions.map { it.index }+s.segments.flatMap { it.cues.map { c -> c.index } }+
            s.tracks.flatMap { it.clips.map { c -> c.index } }
        var nextId=(owned(base.spec)+listOf(base.spec.assets.size+other.assets.size)).max()+1
        val remap=owned(other).associateWith { nextId++ }
        val assetOffset=base.spec.assets.size
        fun id(n: Int)=if(n<0) n else remap.getValue(n)
        val segments=other.segments.map { s -> s.copy(index=id(s.index),cues=s.cues.map { it.copy(index=id(it.index)) }) }
        val tracks=other.tracks.map { t -> t.copy(index=id(t.index),segment=id(t.segment),clips=t.clips.map { c ->
            c.copy(index=id(c.index),asset=c.asset+assetOffset)
        }) }
        val transitions=other.transitions.map { t -> t.copy(index=id(t.index),sources=t.sources.map(::id),destinations=t.destinations.map(::id),bridge=id(t.bridge)) }
        val main=base.standard!!.segmentByRole.getValue(StageFlow.Role.Main)
        val fast=base.fast ?: error("$stage requires base Fast")
        val otherMain=id(prepared.compiled.segmentByRole.getValue(StageFlow.Role.Main))
        val otherFast=id(prepared.compiled.segmentByRole.getValue(StageFlow.Role.Fast))
        val fade=G.Fade(beats=base.spec.bpm/60.0)
        val switches=listOf(main to otherMain,otherMain to main,fast to otherFast,otherFast to fast).map { (a,b) ->
            G.Transition(nextId++,listOf(a),listOf(b),sync=G.SyncPoint.Immediate,entry=G.EntryMode.SameTime,
                fadeOut=fade,fadeIn=fade,postExit=true)
        }
        val spec=base.spec.copy(assets=base.spec.assets+other.assets,segments=base.spec.segments+segments,
            tracks=base.spec.tracks+tracks,transitions=base.spec.transitions+transitions+switches)
        val packet=G.build(spec)
        val paths=base.assetPaths+prepared.compiled.assetPaths
        val labels=base.labels+mapOf(otherMain to "Main Pattern2",otherFast to "Fast Pattern2")+
            prepared.compiled.segmentByOutro.entries.associate { id(it.value) to "Outro%02d Pattern2".format(it.key) }
        return base.copy(spec=spec,packet=packet,assetPaths=paths,labels=labels,streamed=true,
            standard=base.standard.copy(packet=packet,assetPaths=paths),
            versions=listOf(InteractivePlaybackPlan.Version(1,main,fast),InteractivePlaybackPlan.Version(2,otherMain,otherFast)),
            fastByMain=mapOf(main to fast,otherMain to otherFast),
            outroVariants=prepared.compiled.segmentByOutro.mapValues { listOf(id(it.value)) })
    }
}
