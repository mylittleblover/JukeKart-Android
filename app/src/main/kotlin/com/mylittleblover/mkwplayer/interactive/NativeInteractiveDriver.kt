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
) {
    data class Observation(val segment: Int,val sourceFrame: Long?,val transitioning: Boolean=false,
                           val paused: Boolean=false,val voices: Int=1,val error: String?=null,val failureCount: Long=0,
                           val destination: Int?=null)
    var current: Int=entry; private set
    var pending: Int?=null; private set
    var diagnostic: String?=null; private set
    private var observation: Observation?=null
    private var heardVoices=false
    private var previousError: String?=null
    private var previousFailures=0L
    private val available: Boolean get()=pending==null && observation?.transitioning==false && observation?.paused==false && !finished
    val canAdvance: Boolean get()=available && next[current]!=null
    val canEnd: Boolean get()=available && outroRules().isNotEmpty()
    val isFast: Boolean get()=current==fast
    val canFast: Boolean get()=available && fast!=null && rule(fast)!=null
    val finished: Boolean get()=heardVoices && observation?.let { it.voices==0 && !it.paused && !it.transitioning && pending==null }==true

    fun observe(value: Observation) {
        observation=value
        if(value.voices>0) heardVoices=true
        if(spec.segments.any { it.index==value.segment }) current=value.segment
        if(value.destination!=null && value.destination!=current) pending=value.destination
        val newFailure=value.error!=null && (value.error!=previousError || value.failureCount!=previousFailures)
        if(newFailure) diagnostic=value.error
        if((pending==current && !value.transitioning) || newFailure) pending=null
        previousError=value.error;previousFailures=value.failureCount
    }
    private fun rule(target: Int)=spec.transitions.singleOrNull { current in it.sources && target in it.destinations }
    private fun outroRules()=spec.transitions.filter { current in it.sources && it.destinations.singleOrNull() in outros.values }
    private fun submit(target: Int): String? {
        if(!available) return "Waiting for the Native transition"
        if(rule(target)==null) return "No source transition for this section"
        val error=request(target)
        diagnostic=error
        if(error==null) pending=target
        return error
    }
    fun advance(): String? {
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
        val target=outros[number] ?: return "No such Outro"
        return submit(target)
    }
    fun enterFast(): String? {
        val target=fast ?: return "No Fast arrangement"
        return submit(target)
    }
}
