package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.graph.MusicGraphBuilder as G
import java.nio.ByteBuffer
import kotlin.test.*

class TransitionTimelineTest {
    private fun spec(timeline: G.TransitionTimeline?) = G.Spec(
        assets=listOf(G.Asset("a",48000,2,1000000,ByteArray(32))),
        segments=listOf(G.Segment(1,exit=1000000),G.Segment(2,exit=1000000)),
        tracks=listOf(G.Track(3,1,listOf(G.Clip(4,0,sourceLength=1000000))),G.Track(5,2,listOf(G.Clip(6,0,sourceLength=1000000)))),
        transitions=listOf(G.Transition(7,listOf(1),listOf(2),timeline=timeline)),
    )
    @Test fun explicitTimelineUsesItsOwnVersionAndPreservesLegacyBytes() {
        assertEquals(6,ByteBuffer.wrap(G.build(spec(null))).getInt(4))
        assertEquals(14,ByteBuffer.wrap(G.build(spec(G.TransitionTimeline(-49921,-5624,1,0)))).getInt(4))
    }
    @Test fun destinationCannotCommitBeforeItsAudioStarts() {
        assertFailsWith<IllegalArgumentException> { G.build(spec(G.TransitionTimeline(0,100,0,0))) }
    }
    @Test fun destinationSourceCannotBeNegative() {
        assertFailsWith<IllegalArgumentException> { G.build(spec(G.TransitionTimeline(0,0,0,-1))) }
    }
}
