package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.graph.MusicGraphBuilder as G
import kotlin.test.*

class NativePendingCancelTest {
    private val spec=G.Spec(emptyList(),listOf(G.Segment(1,exit=1000),G.Segment(2,exit=1000)),emptyList(),listOf(G.Transition(10,listOf(1),listOf(2))))
    @Test fun cancellationStaysPendingUntilNativeAcknowledgesAndNeverRestartsSource() {
        val plays=mutableListOf<Int>();var cancellations=0
        val d=NativeInteractiveDriver(spec,1,mapOf(1 to 2),emptyMap(),null,{ plays+=it;null },cancelRequest={ cancellations++;null })
        d.observe(NativeInteractiveDriver.Observation(1,100));d.advance()
        d.observe(NativeInteractiveDriver.Observation(1,200,true,destination=2,canCancel=true))
        assertTrue(d.canCancelPending);assertNull(d.cancelPending())
        assertEquals(2,d.pending);assertFalse(d.canCancelPending);assertEquals(listOf(2),plays)
        d.observe(NativeInteractiveDriver.Observation(1,300,true,destination=2,canCancel=true))
        assertEquals(2,d.pending)
        d.observe(NativeInteractiveDriver.Observation(1,400,false))
        assertNull(d.pending);assertTrue(d.canAdvance);assertEquals(1,cancellations)
        d.advance();d.observe(NativeInteractiveDriver.Observation(1,600,true,destination=2,canCancel=false))
        assertFalse(d.canCancelPending);assertNotNull(d.cancelPending());assertEquals(1,cancellations)
    }
    @Test fun lateNativeRejectionKeepsTheActuallyStartedDestination() {
        val d=NativeInteractiveDriver(spec,1,mapOf(1 to 2),emptyMap(),null,{ null },cancelRequest={ null })
        d.observe(NativeInteractiveDriver.Observation(1,100));d.advance()
        d.observe(NativeInteractiveDriver.Observation(1,200,true,destination=2,canCancel=true));d.cancelPending()
        d.observe(NativeInteractiveDriver.Observation(2,1,false,error="Native cancellation rejected",failureCount=1))
        assertEquals(2,d.current);assertNull(d.pending);assertFalse(d.canCancelPending)
    }
}
