package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.graph.MusicGraphBuilder as G
import kotlin.test.*

class NativeRegionDriverTest {
    @Test fun regionChoiceWaitsForNativeAndNeverBorrowsBaseFastOrOutro() {
        val regions=(1..5).associateWith { it }
        val spec=G.Spec(emptyList(),(1..7).map { G.Segment(it,exit=2000,cues=listOf(G.Cue(100+it,"region.exit",1000))) },emptyList(),
            regions.values.flatMap { source -> regions.values.filter { it!=source }.map { target ->
                G.Transition(200+source*10+target,listOf(source),listOf(target),sourceCue="region.exit")
            } }+listOf(G.Transition(300,listOf(1),listOf(6)),G.Transition(301,listOf(1),listOf(7),sourceCue="region.exit")))
        val requests=mutableListOf<Int>()
        val d=NativeInteractiveDriver(spec,1,emptyMap(),mapOf(0 to 7),6,{ requests+=it;null },regions=regions)
        d.observe(NativeInteractiveDriver.Observation(1,10))
        assertEquals(1,d.currentRegion);assertEquals(setOf(2,3,4,5),d.availableRegions)
        assertNull(d.selectRegion(2));assertEquals(1,d.currentRegion)
        assertNotNull(d.selectRegion(3));assertEquals(listOf(2),requests)
        d.observe(NativeInteractiveDriver.Observation(1,900,true,destination=2))
        assertEquals(1,d.currentRegion);assertTrue(d.availableRegions.isEmpty())
        d.observe(NativeInteractiveDriver.Observation(2,20))
        assertEquals(2,d.currentRegion);assertEquals(setOf(1,3,4,5),d.availableRegions)
        assertFalse(d.canFast);assertFalse(d.canEnd)
        assertNotNull(d.enterFast());assertNotNull(d.takeOutro());assertEquals(listOf(2),requests)
        d.observe(NativeInteractiveDriver.Observation(2,20,paused=true))
        assertNotNull(d.selectRegion(1));assertTrue(d.availableRegions.isEmpty())
        d.observe(NativeInteractiveDriver.Observation(2,30))
        assertNull(d.selectRegion(1));assertEquals(listOf(2,1),requests)
    }
}
