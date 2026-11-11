package com.mylittleblover.mkwplayer.interactive

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MinfCatalogTest {
    private fun catalog() = MinfCatalog.parse(File("src/main/assets/interactive_minf.json").readText())
    @Test fun everySourceRecordAndNegativeBeatIsRetained() {
        val c = catalog()
        assertEquals(328, c.records.size)
        assertEquals(5114, c.records.filter { it.role.contains("Pre") }.sumOf { it.beats.size })
        assertTrue(c.records.any { r -> r.beats.any { it.label < 0 } })
        assertEquals(442, c.records.sumOf { it.events.size })
    }
    @Test fun westCityUsesAllThirtyThreeSourceNodesWithoutBpmRounding() {
        val r = catalog().part("WestCity", "Pre")!!
        assertEquals(33, r.loopCandidates.size)
        assertEquals(416953L, r.loopCandidates.first())
        assertEquals(3296953L, r.loopCandidates.last())
    }
    @Test fun chocoBridgeMissingPositionIsNotSampleZero() {
        val e = catalog().part("ChocoMountain", "Bridge")!!.events.first { it.name == "Bridge_ToFull" }
        assertTrue(e.frames.isEmpty())
        assertEquals(listOf(38400L), catalog().part("ChocoMountain", "Full")!!.event("Full_SkipForBridge"))
    }
    @Test fun multipleTempoRecordsDoNotHideTheFullGrid() {
        assertTrue(catalog().part("ShyGuyBazaar","Full")!!.beats.isNotEmpty())
        assertTrue(catalog().part("DKPass","Full")!!.beats.isNotEmpty())
        assertTrue(catalog().part("Cave","Full")!!.beats.isNotEmpty())
        assertTrue(catalog().part("Cave","Fast")!!.beats.isNotEmpty())
    }
    @Test fun repeatedLoopVariantsRemainSeparate() {
        assertTrue(catalog().records.groupBy { it.name }.values.any { rs -> rs.map { it.loopStart to it.loopEnd }.distinct().size > 1 })
        assertNotNull(catalog().part("Rainbow", "Final_Pre"))
    }
}
