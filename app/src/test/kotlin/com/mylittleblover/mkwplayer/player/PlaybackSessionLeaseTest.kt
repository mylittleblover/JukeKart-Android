package com.mylittleblover.mkwplayer.player

import com.mylittleblover.mkwplayer.audio.SessionAudioEngine
import com.mylittleblover.mkwplayer.audio.SessionAudioOutput
import com.mylittleblover.mkwplayer.graph.MusicGraphBuilder as G
import kotlin.test.*

class PlaybackSessionLeaseTest {
    private class Device : SessionAudioEngine {
        var leased=false; var creates=0; var closes=0;var rejected=0
        var streamedBinds=0
        override val sampleRate=48000
        override fun nativeHandle()=1L
        override fun sourceSnapshot(slot: Int): LongArray?=null
        override fun loadGraph(packet: ByteArray): Int = if(leased) { rejected++; 1 } else 0
        override fun bindBwav(assetIndex: Int,bytes: ByteArray,assetId: String): Int = if(leased) { rejected++;1 } else 0
        override fun bindStreamedBwav(assetIndex: Int,bytes: ByteArray,assetId: String): Int { streamedBinds++;return bindBwav(assetIndex,bytes,assetId) }
        override fun cancelPendingTransition()=0
        override fun play(target: Int)=0
        override fun stop()=0
        override fun pause()=0
        override fun resume()=0
        override fun seek(frame: Long)=0
        override fun snapshot()=LongArray(65).apply { this[0]=8;this[2]=1 }
        override fun close() { assertFalse(leased) }
        fun output(): SessionAudioOutput { assertFalse(leased);leased=true;creates++
            return object : SessionAudioOutput {
                override fun timingSnapshot(): LongArray?=null
                override fun close() { assertTrue(leased);leased=false;closes++ }
            }
        }
    }
    private fun packet()=G.build(G.Spec(assets=listOf(G.Asset("a",48000,2,1000,ByteArray(32))),
        segments=listOf(G.Segment(100,exit=1000)),tracks=listOf(G.Track(200,100,listOf(G.Clip(300,0,sourceLength=1000))))))
    @Test fun reloadingAFlowClosesTheConsumerBeforeGraphMutation() {
        val d=Device();val session=PlaybackSession({d},{_,_->d.output()})
        repeat(2) { assertNull(session.loadFlow("Flow",packet(),listOf("a" to byteArrayOf(1)),1000,100).detail) }
        assertEquals(0,d.rejected);assertEquals(2,d.creates);assertEquals(1,d.closes)
        session.release();assertEquals(2,d.closes)
    }
    @Test fun loadingASingleTrackAfterAFlowAlsoReleasesTheConsumer() {
        val d=Device();val session=PlaybackSession({d},{_,_->d.output()})
        session.loadFlow("Flow",packet(),listOf("a" to byteArrayOf(1)),1000,100)
        assertNull(session.load("Track",packet(),byteArrayOf(1),"a",1000).detail)
        assertEquals(0,d.rejected);assertEquals(1,d.closes)
    }
    @Test fun largeLayeredFlowsCanUseTheNativeStreamCache() {
        val d=Device();val session=PlaybackSession({d},{_,_->d.output()})
        session.loadFlow("Rainbow",packet(),listOf("a" to byteArrayOf(1)),1000,100,streamed=true)
        assertEquals(1,d.streamedBinds)
    }
    @Test fun immediateVersionSourcesCanBePreparedWhileOtherPartsRemainStreamed() {
        val d=Device();val session=PlaybackSession({d},{_,_->d.output()})
        session.loadFlow("mixed",packet(),listOf("a" to byteArrayOf(1)),1000,100,streamed=true,preparedAssets=setOf("a"))
        assertEquals(0,d.streamedBinds)
    }

}
