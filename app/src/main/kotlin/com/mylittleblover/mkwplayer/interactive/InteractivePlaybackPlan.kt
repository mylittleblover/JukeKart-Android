package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.graph.MusicGraphBuilder as G
import com.mylittleblover.mkwplayer.graph.StageFlowCompiler
import com.mylittleblover.mkwplayer.library.StageFlow
import com.mylittleblover.mkwplayer.audio.BwavHeader
import java.security.MessageDigest

/** One graph and binding order shared by the Android session and its Native preview. */
internal data class InteractivePlaybackPlan(
    val name: String,val spec: G.Spec,val packet: ByteArray,val assetPaths: List<String>,
    val entry: Int,val next: Map<Int,Int>,val outros: Map<Int,Int>,val fast: Int?,
    val labels: Map<Int,String>,val standard: StageFlowCompiler.Compiled?=null,val streamed: Boolean=false,
    val versions: List<Version> = emptyList(),
    val fastByMain: Map<Int,Int> = emptyMap(),
    val outroVariants: Map<Int,List<Int>> = emptyMap(),
    val regions: Map<Int,Int> = emptyMap(),
    val preparedAssets: Set<String> = emptySet(),
) {
    data class Version(val number: Int,val main: Int,val fast: Int?)

    /** Authored Outro markers lead into the next looping prelude without another user event. */
    val automaticNext: Map<Int,Int> get()=outros.values.mapNotNull { from -> next[from]?.let { from to it } }.toMap()
    companion object {
        fun normal(flow: StageFlow,bytes: Map<String,ByteArray>,catalog: MinfCatalog): InteractivePlaybackPlan {
            val prepared=MinfInteractiveCompiler.prepare(flow,flow.parts.associate { part ->
                part.fileName to MessageDigest.getInstance("SHA-256").digest(bytes.getValue(part.fileName))
            },catalog)
            val compiled=prepared.compiled
            val roles=compiled.segmentByRole
            val main=roles.getValue(StageFlow.Role.Main)
            val entry=roles[StageFlow.Role.Pre] ?: roles[StageFlow.Role.PreShort] ?: main
            val next=listOf(StageFlow.Role.Pre,StageFlow.Role.PreShort).mapNotNull { roles[it]?.let { id -> id to main } }.toMap()
            val labels=roles.entries.associate { it.value to it.key.name }+
                compiled.segmentByOutro.entries.associate { it.value to "Outro%02d".format(it.key) }
            val base=InteractivePlaybackPlan(flow.name,prepared.spec,compiled.packet,compiled.assetPaths,entry,next,
                compiled.segmentByOutro,roles[StageFlow.Role.Fast],labels,compiled)
            return DonkeyRegionCompiler.extend(FullVersionCompiler.extend(base,flow,bytes,catalog),flow,bytes,catalog)
        }
        fun rainbow(bytes: Map<String,ByteArray>,catalog: MinfCatalog): InteractivePlaybackPlan {
            val sources=RainbowInteractiveCompiler.roles.map { role ->
                val name="BGM_Rainbow_$role.bwav";val data=bytes[name] ?: error("Missing $name")
                val info=(BwavHeader.read(data) as? BwavHeader.Result.Ok)?.info ?: error("Unreadable $name")
                RainbowInteractiveCompiler.Source(name,info.sampleRate,info.channels,info.frames.toLong(),
                    info.loopStart?.toLong(),info.loopEnd?.toLong(),MessageDigest.getInstance("SHA-256").digest(data))
            }
            val p=RainbowInteractiveCompiler.prepare(sources,catalog)
            val pairs=listOf("Area1_Pre" to "Area1","Area1" to "Area2","Area3_Pre" to "Area3",
                "Area3" to "Final_Pre","Final_Pre" to "Final","FinalLapFanfare" to "Final")+
                (0..4).map { "Area2_Outro%02d".format(it) to "Area3_Pre" }
            return InteractivePlaybackPlan("Rainbow",p.spec,p.packet,p.assets.map { it.fileName },p.segments.getValue("Area1_Pre"),
                pairs.associate { p.segments.getValue(it.first) to p.segments.getValue(it.second) },
                (0..4).associateWith { p.segments.getValue("Area2_Outro%02d".format(it)) },null,
                p.segments.entries.associate { it.value to it.key },streamed=true)
        }
    }
}
