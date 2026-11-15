package com.mylittleblover.mkwplayer.interactive

import kotlin.test.*

class InitialIdentityScopeTest {
    @Test fun patternSourcesBelongToTheirActualCourseInPreplayPickers() {
        for (stage in listOf("Cinema", "SnowMountain", "DonkeyKong")) {
            val alternate = InteractiveAudioIdentities.Identity("${stage}_Pattern2", "Main", "alternate.bwav")
            assertTrue(alternate.belongsToCourse(stage))
            assertFalse(alternate.belongsToCourse("FirstCircuit"))
        }
        assertFalse(InteractiveAudioIdentities.Identity("Cinema_PatternOops", "Main", "bad").belongsToCourse("Cinema"))
    }
}
