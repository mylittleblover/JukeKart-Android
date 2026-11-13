package com.mylittleblover.mkwplayer.player

import com.mylittleblover.mkwplayer.audio.SessionAudioEngine
import com.mylittleblover.mkwplayer.graph.MusicGraphBuilder as G
import kotlin.test.*

class InteractiveObservationTest {
    private class Device : SessionAudioEngine {
        val requests=mutableListOf<Int>()
        var cancellations=0
        override fun cancelPendingTransition(): Int { cancellations++;return 0 }
        var raw=LongArray(65).apply { this[0]=8;this[1]=9999999;this[2]=1;this[3]=1;this[11]=100 }
        var source: LongArray?=LongArray(207).apply { this[0]=4;this[1]=1;this[4]=100;this[10]=12345 }
        override val sampleRate=48000
        override fun nativeHandle()=1L
        override fun sourceSnapshot(slot: Int)=if(slot==0) source else null
        override fun snapshot()=raw
        override fun loadGraph(packet: ByteArray)=0
        override fun bindBwav(assetIndex: Int,bytes: ByteArray,assetId: String)=0
        override fun play(target: Int): Int { requests+=target;return 0 }
        override fun pause()=0
        override fun resume()=0
        override fun stop()=0
        override fun seek(frame: Long)=0
        override fun close() {}
    }
    private fun session(d: Device,autoAdvance: Map<Int,Int> = emptyMap())=PlaybackSession({d},{_,_->null}).apply {
        val packet=G.build(G.Spec(listOf(G.Asset("a",48000,2,20000,ByteArray(32))),
            listOf(G.Segment(100,exit=20000)),listOf(G.Track(200,100,listOf(G.Clip(300,0,sourceLength=20000))))))
        loadFlow("stage",packet,listOf("a" to byteArrayOf(1)),20000,100,autoAdvance=autoAdvance)
    }
    @Test fun observationReadsCommittedSegmentAndSourcePositionIndependentlyOfTransport() {
        val d=Device();val s=session(d);val value=s.interactiveObservation()!!
        assertEquals(100,value.segment);assertEquals(12345L,value.sourceFrame)
    }
    @Test fun absentSourceDoesNotFallBackToTransportFrames() {
        val d=Device();val s=session(d);d.source=null
        assertNull(s.interactiveObservation()!!.sourceFrame)
    }
    @Test fun malformedWireIsRejectedAndTransitionAndPauseAreRetained() {
        val d=Device();val s=session(d);d.raw[24]=1;d.raw[2]=2
        assertTrue(s.interactiveObservation()!!.transitioning);assertTrue(s.interactiveObservation()!!.paused)
        d.raw[0]=7;assertNull(s.interactiveObservation())
    }
    @Test fun aCommittedOutroSchedulesItsNativeNextPreExactlyOnceWithoutAViewModel() {
        val d=Device();val s=session(d,mapOf(100 to 101))
        repeat(3) { s.poll() }
        assertEquals(listOf(100,101),d.requests)
        assertEquals(101,s.interactiveObservation()!!.destination)
    }
    @Test fun automaticOutroHandoverWaitsForResumeAndCurrentTransitionCommit() {
        val d=Device();val s=session(d,mapOf(100 to 101))
        d.raw[2]=2;s.poll();assertEquals(listOf(100),d.requests)
        d.raw[2]=1;d.raw[24]=1;s.poll();assertEquals(listOf(100),d.requests)
        d.raw[24]=0;s.poll();assertEquals(listOf(100,101),d.requests)
    }
    @Test fun aRejectedAutomaticCommandDoesNotLeaveAnInventedPendingTarget() {
        val d=Device();val s=session(d,mapOf(100 to 101));s.poll()
        d.raw[6]=1;d.raw[7]=1
        assertNull(s.interactiveObservation()!!.destination)
        s.poll();assertEquals(listOf(100,101),d.requests)
    }
    @Test fun cancellationUsesNativePhaseAndDoesNotResubmitAnAutomaticTarget() {
        val d=Device();val s=session(d,mapOf(100 to 101));s.poll()
        d.raw[24]=1;d.raw[27]=101;d.raw[37]=0
        assertTrue(s.interactiveObservation()!!.canCancel)
        assertNull(s.cancelPendingTransition());assertEquals(1,d.cancellations)
        d.raw[24]=0
        assertNull(s.interactiveObservation()!!.destination)
        s.poll();assertEquals(listOf(100,101),d.requests)
        d.raw[24]=1;d.raw[37]=1
        assertFalse(s.interactiveObservation()!!.canCancel)
    }

}
