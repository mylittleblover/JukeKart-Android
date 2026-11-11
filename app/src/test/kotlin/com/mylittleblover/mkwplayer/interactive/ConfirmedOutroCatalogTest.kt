package com.mylittleblover.mkwplayer.interactive

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConfirmedOutroCatalogTest {
    @Test fun multipleExitPointsBelongToOneAudioResource() {
        assertEquals(listOf(545625L,905625L,1085625L,1445625L,1805625L,2165625L,2885625L,3065625L),
            ConfirmedOutroCatalog.forStage("WestCity")!!.getValue(0))
        assertEquals(5, ConfirmedOutroCatalog.forStage("WestCity")!!.size)
    }
    @Test fun patternHasItsOwnConfirmedMapping() {
        assertEquals(8, ConfirmedOutroCatalog.forStage("SnowMountain")!!.size)
        assertEquals(6, ConfirmedOutroCatalog.forStage("SnowMountain_Pattern2")!!.size)
        assertNull(ConfirmedOutroCatalog.forStage("SnowMountain_Pattern99"))
    }
    @Test fun sourceFramesAreNotReconstructedFromRoundedTime() {
        assertEquals(listOf(578571L,732857L,887142L), ConfirmedOutroCatalog.forStage("BigTree")!!.getValue(1))
    }
    @Test fun unknownStageCannotBorrowAnotherStagesEnding() {
        assertNull(ConfirmedOutroCatalog.forStage("Rainbow"))
        assertNull(ConfirmedOutroCatalog.forStage("WestCityExtra"))
    }
}
