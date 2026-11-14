package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.graph.MusicGraphBuilder as G

/** UI requests targets; the Native engine owns sample timing and committed playback state. */
internal class NativeInteractiveDriver(
    private val spec: G.Spec,
    entry: Int,
    private val next: Map<Int,Int>,
    private val outros: Map<Int,Int>,
    private val fast: Int?,
    private val request: (Int)->String?,
    private val versions: List<InteractivePlaybackPlan.Version> = emptyList(),
    private val outroVariants: Map<Int,List<Int>> = emptyMap(),
    private val regions: Map<Int,Int> = emptyMap(),
    private val cancelRequest: ()->String? = { "Cancellation is unavailable" },
    private val mandatoryAutomaticSources: Set<Int> = emptySet(),
    private val advanceViaOutro: Set<Int> = emptySet(),
) {
    data class Observation(val segment: Int,val sourceFrame: Long?,val transitioning: Boolean=false,
                           val paused: Boolean=false,val voices: Int=1,val error: String?=null,val failureCount: Long=0,
                           val destination: Int?=null,val canCancel: Boolean=false,val stopped: Boolean=false,
                           val transportFrame: Long?=null,val fadeEndFrame: Long?=null)
    var current: Int=entry; private set
    var pending: Int?=null; private set
    var diagnostic: String?=null; private set
    private var observation: Observation?=null
    val observationState: Observation? get()=observation
    var isFading: Boolean=false; private set
    fun markFadeRequested() { isFading=true;pending=null;cancellationRequested=false }
    private var cancellationRequested=false
    private var heardVoices=false
    private var previousError: String?=null
    private var previousFailures=0L
    private val available: Boolean get()=!isFading && pending==null && observation?.transitioning==false && observation?.paused==false && !finished &&
        !(currentVersion!=null && (observation?.voices ?: 0)>1)
    val canCancelPending: Boolean get()=current !in mandatoryAutomaticSources && !isFading && pending!=null && observation?.canCancel==true && !cancellationRequested
    fun cancelPending(): String? {
        if(!canCancelPending) return "The next section has already started or is not cancellable"
        val error=cancelRequest()
        diagnostic=error
        if(error==null) cancellationRequested=true
        return error
    }
    val canAdvance: Boolean get()=available && (next[current]!=null || current in advanceViaOutro && outroRules().isNotEmpty())
    val canEnd: Boolean get()=available && current !in advanceViaOutro && outroRules().isNotEmpty()
    val currentRegion: Int? get()=regions.entries.singleOrNull { it.value==current }?.key
    val availableRegions: Set<Int> get()=if(!available) emptySet() else regions.filter { (_,target) -> target!=current && rule(target)!=null }.keys
    fun selectRegion(number: Int): String? {
        val target=regions[number] ?: return "No such region"
        return submit(target)
    }
    val currentVersion: Int? get()=versions.singleOrNull { current==it.main || current==it.fast }?.number
    val isFast: Boolean get()=current==fast || versions.any { current==it.fast }
    private val fastTarget: Int? get()=versions.singleOrNull { current==it.main }?.fast ?: fast
    val availableVersions: Set<Int> get()=if(!available) emptySet() else versions.filter { v ->
        val target=if(isFast) v.fast else v.main
        target!=null && target!=current && rule(target)!=null
    }.map { it.number }.toSet()
    val canFast: Boolean get()=available && fastTarget?.let { rule(it)!=null }==true
    val finished: Boolean get()=heardVoices && observation?.let { it.voices==0 && (it.stopped || isFading && it.fadeEndFrame!=null) && !it.paused && !it.transitioning && pending==null }==true

    fun observe(value: Observation) {
        observation=value
        if(value.voices>0) heardVoices=true
        if(spec.segments.any { it.index==value.segment }) current=value.segment
        if(!isFading && value.destination!=null && value.destination!=current) pending=value.destination
        val newFailure=value.error!=null && (value.error!=previousError || value.failureCount!=previousFailures)
        if(newFailure) { diagnostic=value.error;cancellationRequested=false }
        if(cancellationRequested && !value.transitioning && value.destination==null) {
            pending=null;cancellationRequested=false
        }
        if((pending==current && !value.transitioning) || newFailure) pending=null
        previousError=value.error;previousFailures=value.failureCount
    }
    private fun rule(target: Int)=spec.transitions.singleOrNull { current in it.sources && target in it.destinations }
    private val outroTargets: Set<Int> get()=(outros.values+outroVariants.values.flatten()).toSet()
    private fun outroRules()=spec.transitions.filter { current in it.sources && it.destinations.singleOrNull() in outroTargets }
    private fun submit(target: Int): String? {
        if(!available) return "Waiting for the Native transition"
        if(rule(target)==null) return "No source transition for this section"
        val error=request(target)
        diagnostic=error
        if(error==null) pending=target
        return error
    }
    fun advance(): String? {
        if (current in advanceViaOutro) return takeOutro()
        val target=next[current] ?: return "No next section here"
        return submit(target)
    }
    fun takeOutro(): String? {
        if(!available) return "Waiting for the Native transition"
        val position=observation?.sourceFrame ?: return "Waiting for the source playback position"
        val segment=spec.segments.single { it.index==current }
        val options=outroRules().flatMap { transition ->
            segment.cues.filter { it.name==transition.sourceCue }.mapNotNull { cue ->
                val delay=if(cue.frame>position) cue.frame-position else segment.loop?.let { loop ->
                    if(cue.frame>=loop.start && cue.frame<loop.end) loop.end-position+cue.frame-loop.start else null
                }
                delay?.takeIf { it>0 }?.let { it to transition.destinations.single() }
            }
        }
        val target=options.minWithOrNull(compareBy<Pair<Long,Int>>({it.first},{it.second}))?.second
            ?: return "No reachable Outro exit from this source position"
        return submit(target)
    }
    fun takeOutro(number: Int): String? {
        val targets=listOfNotNull(outros[number])+outroVariants[number].orEmpty()
        val target=targets.singleOrNull { rule(it)!=null } ?: return "No confirmed Outro from this section"
        return submit(target)
    }
    fun selectVersion(number: Int): String? {
        val version=versions.singleOrNull { it.number==number } ?: return "No such recording version"
        val target=if(isFast) version.fast else version.main
        return if(target==null) "No matching version for this section" else submit(target)
    }
    fun enterFast(): String? {
        val target=fastTarget ?: return "No Fast arrangement"
        return submit(target)
    }
}
