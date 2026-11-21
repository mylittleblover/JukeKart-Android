package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.graph.MusicGraphBuilder as G
import com.mylittleblover.mkwplayer.library.StageFlow

/** An independent bridge entry uses the same source, exit and overlap as the authored Pre→Bridge→Main edge. */
internal object DirectBridgeCompiler {
    fun normal(base: InteractivePlaybackPlan, flow: StageFlow, catalog: MinfCatalog): InteractivePlaybackPlan {
        val roles = base.standard?.segmentByRole ?: return base
        val bridge = roles[StageFlow.Role.Bridge] ?: return base
        val main = roles.getValue(StageFlow.Role.Main)
        val stage = flow.name.removePrefix("BGM_Stage_")
        val source = catalog.part(stage, "Bridge") ?: error("Missing $stage Bridge MINF")
        val target = catalog.part(stage, "Full") ?: error("Missing $stage Full MINF")
        val original = base.spec.segments.single { it.index == bridge }
        val marker = source.event("Bridge_ToFull")?.singleOrNull()
            ?: if (stage == "ChocoMountain" && source.event("Bridge_ToFull")?.isEmpty() == true) original.exit
               else error("Missing verified $stage Bridge exit")
        val entry = target.event("Full_FromBridge")?.singleOrNull() ?: target.event("Full_SkipForBridge")?.singleOrNull()
        return extend(base, bridge, main, marker, target.phase, entry, "Bridge")
    }

    fun rainbow(base: InteractivePlaybackPlan, catalog: MinfCatalog): InteractivePlaybackPlan {
        val bridge = base.labels.entries.single { it.value == "Area1_Bridge" }.key
        val main = base.labels.entries.single { it.value == "Area1" }.key
        val marker = catalog.part("Rainbow", "Area1_Bridge")?.event("Bridge_ToFull")?.singleOrNull()
            ?: error("Missing Rainbow Area1 Bridge exit")
        val phase = catalog.part("Rainbow", "Area1_Inst")?.phase ?: error("Missing Rainbow Area1 phase")
        return extend(base, bridge, main, marker, phase, null, "Area1_Bridge")
    }

    private fun extend(base: InteractivePlaybackPlan, bridge: Int, main: Int, marker: Long,
                       phase: Long, explicitEntry: Long?, label: String): InteractivePlaybackPlan {
        val original = base.spec.segments.single { it.index == bridge }
        require(marker in 0..original.exit && phase >= 0)
        val exit = maxOf(1L, marker)
        val targetStart = if (explicitEntry != null) marker else maxOf(0L, marker - phase)
        val targetSource = explicitEntry ?: maxOf(0L, phase - marker)
        var id = (base.spec.segments.map { it.index } + base.spec.tracks.map { it.index } +
            base.spec.transitions.map { it.index } + base.spec.segments.flatMap { it.cues.map { c -> c.index } } +
            base.spec.tracks.flatMap { it.clips.map { c -> c.index } }).max() + 1
        val direct = id++
        val directTracks = base.spec.tracks.filter { it.segment == bridge }.map { track ->
            track.copy(index = id++, segment = direct, clips = track.clips.map { it.copy(index = id++) })
        }
        require(directTracks.isNotEmpty())
        val transition = G.Transition(id++, listOf(direct), listOf(main), sync = G.SyncPoint.ExitCue,
            postExit = true, timeline = G.TransitionTimeline(0, targetStart - exit, 0, targetSource))
        val spec = base.spec.copy(segments = base.spec.segments + G.Segment(direct, exit = exit),
            tracks = base.spec.tracks + directTracks, transitions = base.spec.transitions + transition)
        val paths = spec.tracks.filter { it.segment == direct || it.segment == main }
            .flatMap { it.clips }.map { spec.assets[it.asset].path }.toSet()
        val budgetBytes = 128L * 1024 * 1024
        val directBytes = spec.assets.filter { it.path in paths }.sumOf { it.frames * it.channels * 4L }
        require(directBytes <= budgetBytes) {
            "Direct Bridge sources exceed the prepared audio budget"
        }
        val allPrepared = base.preparedAssets + paths
        val combinedFitsBudget = spec.assets.filter { it.path in allPrepared }.sumOf { it.frames * it.channels * 4L } <= budgetBytes
        val packet = G.build(spec)
        val updatedPreparedAssets = if (combinedFitsBudget) base.preparedAssets else emptySet()
        val updatedEntryPrepared = if (combinedFitsBudget || base.preparedAssets.isEmpty()) {
            base.entryPreparedAssets + (direct to paths)
        } else {
            val existingWithBase = base.entryPreparedAssets.mapValues { (segId, set) ->
                if (segId == direct) set else set + base.preparedAssets
            }
            val originalEntryWithBase = if (base.entry !in existingWithBase && base.entry != direct) {
                mapOf(base.entry to base.preparedAssets)
            } else emptyMap()
            existingWithBase + originalEntryWithBase + (direct to paths)
        }
        return base.copy(spec = spec, packet = packet, labels = base.labels + (direct to label), directBridge = direct, originalEntry = base.originalEntry ?: base.entry,
            preparedAssets = updatedPreparedAssets,
            entryPreparedAssets = updatedEntryPrepared,
            entryAutomaticNext = base.entryAutomaticNext + (direct to main),
            standard = base.standard?.copy(packet = packet))
    }
}
