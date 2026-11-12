package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.graph.MusicGraphBuilder as G
import kotlin.test.*

class NativeVersionDriverTest {
    @Test fun versionChoiceWaitsForNativeAndFastRetainsCommittedVersion() {
        val spec=G.Spec(emptyList(),(1..5).map { G.Segment(it,exit=100000) },emptyList(),listOf(
            G.Transition(10,listOf(1),listOf(2)),G.Transition(11,listOf(2),listOf(1)),
            G.Transition(12,listOf(1),listOf(3)),G.Transition(13,listOf(2),listOf(4)),
            G.Transition(14,listOf(3),listOf(4)),G.Transition(15,listOf(4),listOf(3))))
        val requests=mutableListOf<Int>()
        val d=NativeInteractiveDriver(spec,1,emptyMap(),emptyMap(),3,{ requests+=it;null },
            versions=listOf(InteractivePlaybackPlan.Version(1,1,3),InteractivePlaybackPlan.Version(2,2,4)))
        d.observe(NativeInteractiveDriver.Observation(1,200))
        assertEquals(1,d.currentVersion)
        assertEquals(setOf(2),d.availableVersions)
        assertNull(d.selectVersion(2));assertEquals(1,d.currentVersion);assertEquals(2,d.pending)
        d.observe(NativeInteractiveDriver.Observation(2,300,transitioning=true,voices=2))
        assertEquals(2,d.currentVersion);assertTrue(d.availableVersions.isEmpty());assertNotNull(d.selectVersion(1))
        // Native commits an Immediate transition before its retained voice fade ends.
        d.observe(NativeInteractiveDriver.Observation(2,400,transitioning=false,voices=2))
        assertTrue(d.availableVersions.isEmpty(),"Retained fade voices must block a third source")
        assertFalse(d.canFast)
        d.observe(NativeInteractiveDriver.Observation(2,48200))
        assertNull(d.pending);assertNull(d.enterFast());assertEquals(4,requests.last())
        d.observe(NativeInteractiveDriver.Observation(4,10))
        assertTrue(d.isFast);assertEquals(2,d.currentVersion)
        assertNull(d.selectVersion(1));assertEquals(3,requests.last())
        d.observe(NativeInteractiveDriver.Observation(5,20))
        assertNull(d.currentVersion);assertTrue(d.availableVersions.isEmpty())
    }
    @Test fun alternateVersionUsesOnlyItsConfirmedOutroTargets() {
        val spec=G.Spec(emptyList(),listOf(G.Segment(2,exit=1000,loop=G.Loop(0,1000),cues=listOf(G.Cue(20,"outro.0",500))),G.Segment(5,exit=800)),emptyList(),
            listOf(G.Transition(10,listOf(2),listOf(5),sourceCue="outro.0")))
        val requests=mutableListOf<Int>()
        val d=NativeInteractiveDriver(spec,2,emptyMap(),mapOf(0 to 3,1 to 4),null,{ requests+=it;null },
            outroVariants=mapOf(0 to listOf(5)))
        d.observe(NativeInteractiveDriver.Observation(2,200))
        assertTrue(d.canEnd)
        assertNotNull(d.takeOutro(1));assertTrue(requests.isEmpty())
        assertNull(d.takeOutro(0));assertEquals(listOf(5),requests)
    }

}
