package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.graph.MusicGraphBuilder as G
import kotlin.test.*

class NativeInteractiveDriverTest {
    private val spec=G.Spec(emptyList(),listOf(
        G.Segment(100,exit=1000,loop=G.Loop(100,1000)),
        G.Segment(101,exit=1000,loop=G.Loop(100,1000),cues=listOf(G.Cue(10,"outro.0",300),G.Cue(11,"outro.1",700))),
        G.Segment(102,exit=500),G.Segment(103,exit=500),G.Segment(104,exit=1000)),emptyList(),listOf(
        G.Transition(200,listOf(100),listOf(101)),G.Transition(201,listOf(101),listOf(102),sourceCue="outro.0"),
        G.Transition(202,listOf(101),listOf(103),sourceCue="outro.1"),G.Transition(203,listOf(101),listOf(104))))
    private fun driver(request: (Int)->String?={null})=NativeInteractiveDriver(spec,100,mapOf(100 to 101),
        mapOf(0 to 102,1 to 103),104,request)
    @Test fun rainbowSecondLapAdvanceSelectsTheNextRealOutroCue() {
        val requested = mutableListOf<Int>()
        val d = NativeInteractiveDriver(spec, 101, emptyMap(), mapOf(0 to 102, 1 to 103), null,
            { requested += it; null }, advanceViaOutro = setOf(101))
        d.observe(NativeInteractiveDriver.Observation(101, 500))
        assertTrue(d.canAdvance)
        assertFalse(d.canEnd)
        assertNull(d.advance())
        assertEquals(listOf(103), requested)
    }
    @Test fun requiredAutomaticBridgeHandoverCannotBeCancelled() {
        var cancellations = 0
        val d = NativeInteractiveDriver(spec, 100, emptyMap(), emptyMap(), null, { null },
            cancelRequest = { cancellations++; null }, mandatoryAutomaticSources = setOf(100))
        d.observe(NativeInteractiveDriver.Observation(100, 10, transitioning = true, destination = 101, canCancel = true))
        assertFalse(d.canCancelPending)
        assertNotNull(d.cancelPending())
        assertEquals(0, cancellations)
        d.observe(NativeInteractiveDriver.Observation(101, 0))
        assertEquals(101, d.current)
        assertNull(d.pending)
    }
    @Test fun fadeStopCannotBeCancelledOrReplacedByASectionRequest() {
        var requests=0
        val d=driver { requests++; null }
        d.observe(NativeInteractiveDriver.Observation(100,100,transportFrame=4800))
        d.markFadeRequested()
        assertFalse(d.canAdvance)
        assertFalse(d.canCancelPending)
        assertNotNull(d.advance())
        assertEquals(0,requests)
        d.observe(NativeInteractiveDriver.Observation(-1,null,voices=0,transportFrame=52800,fadeEndFrame=52800))
        assertTrue(d.finished)
    }
    @Test fun acceptedRequestStaysPendingUntilNativeCommitsDestination() {
        val d=driver();d.observe(NativeInteractiveDriver.Observation(100,100))
        assertNull(d.advance());assertEquals(100,d.current);assertEquals(101,d.pending)
        d.observe(NativeInteractiveDriver.Observation(100,350,true))
        assertEquals(100,d.current);assertFalse(d.canAdvance)
        d.observe(NativeInteractiveDriver.Observation(101,50,false))
        assertEquals(101,d.current);assertNull(d.pending);assertTrue(d.canEnd)
    }
    @Test fun outroChoiceUsesNextConfirmedSourceCueNotTransportTimeOrOutroLength() {
        val targets=mutableListOf<Int>();val d=driver {targets+=it;null}
        d.observe(NativeInteractiveDriver.Observation(101,500))
        assertNull(d.takeOutro());assertEquals(listOf(103),targets)
    }
    @Test fun outroSelectionWrapsOnlyThroughTheActualFullLoop() {
        val targets=mutableListOf<Int>();val d=driver {targets+=it;null}
        d.observe(NativeInteractiveDriver.Observation(101,900))
        assertNull(d.takeOutro());assertEquals(listOf(102),targets)
    }
    @Test fun failedRequestKeepsControlsAndCurrentSegment() {
        val d=driver {"Native rejected request"};d.observe(NativeInteractiveDriver.Observation(100,200))
        assertEquals("Native rejected request",d.advance());assertNull(d.pending);assertTrue(d.canAdvance)
    }
    @Test fun fastHasNoInventedReverseOrOutroTransition() {
        val d=driver();d.observe(NativeInteractiveDriver.Observation(101,200))
        assertNull(d.enterFast());d.observe(NativeInteractiveDriver.Observation(104,100))
        assertTrue(d.isFast);assertFalse(d.canEnd);assertNotNull(d.enterFast())
    }
    @Test fun pauseKeepsThePendingTransitionWithoutSubmittingAnotherRequest() {
        var requests=0;val d=driver {requests++;null}
        d.observe(NativeInteractiveDriver.Observation(100,200));d.advance()
        repeat(3) { d.observe(NativeInteractiveDriver.Observation(100,200,true,paused=true)) }
        assertEquals(101,d.pending);assertNotNull(d.advance());assertEquals(1,requests)
    }
    @Test fun queuedInitialPlayIsNotMisreportedAsNaturalCompletion() {
        val d=driver();d.observe(NativeInteractiveDriver.Observation(100,null,voices=0))
        assertFalse(d.finished)
        d.observe(NativeInteractiveDriver.Observation(100,50,voices=1))
        d.observe(NativeInteractiveDriver.Observation(102,500,voices=0,stopped=true))
        assertTrue(d.finished)
    }
    @Test fun persistentNativeErrorDoesNotPreventRetryOrEraseTheAcceptedRetry() {
        val d=driver()
        val failed=NativeInteractiveDriver.Observation(100,200,error="MissingCue")
        d.observe(failed)
        assertTrue(d.canAdvance)
        assertNull(d.advance())
        d.observe(failed)
        assertEquals(101,d.pending)
    }
    @Test fun pausingWithoutAPendingTransitionDoesNotAllowAnImmediateSectionCut() {
        var requests=0;val d=driver { requests++;null }
        d.observe(NativeInteractiveDriver.Observation(100,200,paused=true))
        assertFalse(d.canAdvance)
        assertNotNull(d.advance())
        assertEquals(0,requests)
    }
    @Test fun aSessionQueuedTargetIsVisibleWithoutPretendingItHasCommitted() {
        val d=driver();d.observe(NativeInteractiveDriver.Observation(100,200,destination=101))
        assertEquals(100,d.current);assertEquals(101,d.pending);assertFalse(d.canAdvance)
        d.observe(NativeInteractiveDriver.Observation(101,1))
        assertNull(d.pending);assertEquals(101,d.current)
    }
    @Test fun anExhaustedVoiceAtLoopBoundaryDoesNotFinishAStillPlayingTransport() {
        val d=driver();d.observe(NativeInteractiveDriver.Observation(101,999,voices=1))
        d.observe(NativeInteractiveDriver.Observation(101,null,voices=0,stopped=false))
        assertFalse(d.finished)
        d.observe(NativeInteractiveDriver.Observation(101,101,voices=1))
        assertFalse(d.finished)
        d.observe(NativeInteractiveDriver.Observation(102,500,voices=0,stopped=true))
        assertTrue(d.finished)
    }
    @Test fun fadeStopPausesAndFreezesCountdownWithoutFinishingAndCannotBeCancelled() {
        val d=driver()
        d.observe(NativeInteractiveDriver.Observation(100,100,voices=1,transportFrame=1000))
        assertNull(d.advance())
        assertEquals(101,d.pending)
        assertTrue(d.apply { observe(NativeInteractiveDriver.Observation(100,120,voices=1,destination=101,canCancel=true,transportFrame=1200)) }.canCancelPending)

        // Starting fade clears pending transitions and forbids cancellation (#47).
        d.markFadeRequested()
        assertTrue(d.isFading)
        assertNull(d.pending)
        assertFalse(d.canCancelPending)
        assertFalse(d.canAdvance)
        assertFalse(d.canEnd)

        // Pausing mid-fade freezes transportFrame/remaining countdown and does not finish prematurely (#47).
        val frozenPaused = NativeInteractiveDriver.Observation(100, 140, paused = true, voices = 0, transportFrame = 48000, fadeEndFrame = 144000)
        d.observe(frozenPaused)
        assertFalse(d.finished)
        assertFalse(d.canCancelPending)
        assertEquals(96000L, frozenPaused.fadeEndFrame!! - frozenPaused.transportFrame!!)

        // Resuming advances until voices reach 0 at fadeEndFrame and completes.
        d.observe(NativeInteractiveDriver.Observation(-1, null, paused = false, voices = 0, transportFrame = 144000, fadeEndFrame = 144000))
        assertTrue(d.finished)
    }

}
