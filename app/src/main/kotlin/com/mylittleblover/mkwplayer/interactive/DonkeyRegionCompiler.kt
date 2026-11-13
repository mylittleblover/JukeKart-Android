package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.graph.MusicGraphBuilder as G
import com.mylittleblover.mkwplayer.library.StageFlow
import com.mylittleblover.mkwplayer.library.StageFlowReader
import java.security.MessageDigest

/** Region changes use each departing source's real bar-one grid, never SameTime. */
internal object DonkeyRegionCompiler {
    fun extend(base: InteractivePlaybackPlan,flow: StageFlow,bytes: Map<String,ByteArray>,catalog: MinfCatalog): InteractivePlaybackPlan {
        if(flow.name.removePrefix("BGM_Stage_")!="DonkeyKong") return base
        val patterns=(2..5).mapNotNull { number ->
            val name="BGM_Stage_DonkeyKong_Pattern$number.bwav"
            bytes[name]?.let { number to StageFlowReader.readPart(name,it).getOrThrow() }
        }
        if(patterns.isEmpty()) return base
        var nextId=(base.spec.segments.map { it.index }+base.spec.tracks.map { it.index }+
            base.spec.transitions.map { it.index }+base.spec.segments.flatMap { s -> s.cues.map { it.index } }+
            base.spec.tracks.flatMap { t -> t.clips.map { it.index } }).max()+1
        val regions=mapOf(1 to base.standard!!.segmentByRole.getValue(StageFlow.Role.Main))+
            patterns.associate { (number,_) -> number to nextId++ }
        fun record(number: Int)=catalog.part(if(number==1) "DonkeyKong" else "DonkeyKong_Pattern$number","Full")
            ?: error("Missing DonkeyKong region $number MINF")
        fun cues(number: Int)=record(number).beats.filter { it.label==1 }.map { G.Cue(nextId++,"region.exit",it.frame) }
        val first=regions.getValue(1)
        val segments=base.spec.segments.map { if(it.index==first) it.copy(cues=(it.cues+cues(1)).sortedBy { cue -> cue.frame }) else it }+
            patterns.map { (number,part) ->
                val r=record(number)
                require(r.rate==part.sampleRate && r.bpm==base.spec.bpm && r.loopStart==part.loopStart && r.loopEnd==part.loopEnd) {
                    "DonkeyKong region $number source and MINF disagree"
                }
                G.Segment(regions.getValue(number),exit=part.frames,loop=G.Loop(r.loopStart,r.loopEnd),cues=cues(number))
            }
        val assets=base.spec.assets+patterns.map { (_,part) -> G.Asset(part.fileName,part.sampleRate,part.channels,part.frames,
            MessageDigest.getInstance("SHA-256").digest(bytes.getValue(part.fileName))) }
        val tracks=base.spec.tracks+patterns.mapIndexed { i,(number,part) ->
            G.Track(nextId++,regions.getValue(number),listOf(G.Clip(nextId++,base.spec.assets.size+i,sourceLength=part.frames)))
        }
        val transitions=base.spec.transitions+regions.flatMap { (number,source) ->
            require(record(number).beats.any { it.label==1 }) { "DonkeyKong region $number has no bar grid" }
            regions.filterKeys { it!=number }.map { (targetNumber,target) ->
                G.Transition(nextId++,listOf(source),listOf(target),sync=G.SyncPoint.NextCustomCue,sourceCue="region.exit",
                    timeline=G.TransitionTimeline(0,-record(targetNumber).phase,0,0))
            }
        }
        val spec=base.spec.copy(assets=assets,segments=segments,tracks=tracks,transitions=transitions)
        val packet=G.build(spec)
        val paths=base.assetPaths+patterns.map { it.second.fileName }
        return base.copy(spec=spec,packet=packet,assetPaths=paths,streamed=true,regions=regions,
            labels=base.labels+regions.filterKeys { it!=1 }.entries.associate { it.value to "Region ${it.key}" },
            standard=base.standard.copy(packet=packet,assetPaths=paths))
    }
}
