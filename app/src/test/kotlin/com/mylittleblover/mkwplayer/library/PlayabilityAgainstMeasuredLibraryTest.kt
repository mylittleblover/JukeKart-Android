package com.mylittleblover.mkwplayer.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Checks the playability rule against the measured shape of the real library.
 *
 * The measured counts are facts about the corpus and do not change: 650 entries, 397 kind 1, 253 kind 2.
 * What changed is which of them this build can decode. Codec 2 used to be refused wholesale, which greyed
 * out the entire jukebox; the reader was fixed and all 227 jukebox files now decode (probed individually
 * through the real `openBwav`). So the rule is no longer "kind 1 only" but "kind 1 or kind 2", and the
 * mixed-category argument that motivated the original per-entry rule still holds -- it is now simply that a
 * category's capability cannot be assumed either way.
 */
class PlayabilityAgainstMeasuredLibraryTest {

    @Test
    fun totalCountsMatchTheMeasuredIndex() {
        assertEquals(650, MeasuredLibrary.totalEntries)
        assertEquals(397, MeasuredLibrary.totalKindOne)
        assertEquals(253, MeasuredLibrary.totalKindTwo)
    }

    @Test
    fun bothMeasuredCodecsArePlayable() {
        for ((category, mix) in MeasuredLibrary.mix) {
            if (mix.kindOne > 0) {
                val result = Playability.evaluate(1)
                assertTrue(result.playable, "$category has kind 1 entries that must play")
            }
            if (mix.kindTwo > 0) {
                val result = Playability.evaluate(2)
                assertTrue(result.playable, "$category has kind 2 entries that must play")
            }
        }
    }

    @Test
    fun courseThemesAreAllPlayable() {
        val course = MeasuredLibrary.mix.getValue(TrackCategory.COURSE)
        assertEquals(338, course.total)
        assertEquals(338, course.kindOne)
        assertEquals(0, course.kindTwo)
        assertEquals(1.0, course.playableShare)
    }

    @Test
    fun theJukeboxIsNoLongerBlocked() {
        // 227 entries, every one of them codec 2. They were the single largest blocked group, and the
        // reason the media page's free-driving category had nothing to offer.
        val jukebox = MeasuredLibrary.mix.getValue(TrackCategory.JUKEBOX)
        assertEquals(227, jukebox.total)
        assertEquals(0, jukebox.kindOne)
        assertEquals(227, jukebox.kindTwo)
        assertTrue(Playability.evaluate(2).playable, "jukebox entries are codec 2 and must play")
    }

    @Test
    fun mixedCategoriesAreExactlySequenceAndDemo() {
        // Measured: SEQUENCE has 33 kind 1 + 1 kind 2, DEMO has 1 + 3.
        assertEquals(
            setOf(TrackCategory.SEQUENCE, TrackCategory.DEMO),
            MeasuredLibrary.mixedCategories.toSet(),
        )
    }

    @Test
    fun perCategoryRulesWouldBeWrongForMixedCategories() {
        // The mistake this file exists to prevent: a category's codec mix is not uniform, so neither
        // "always playable" nor "always blocked" is a safe assumption for SEQUENCE or DEMO.
        val sequence = MeasuredLibrary.mix.getValue(TrackCategory.SEQUENCE)
        val demo = MeasuredLibrary.mix.getValue(TrackCategory.DEMO)

        assertTrue(sequence.kindOne > 0 && sequence.kindTwo > 0, "SEQUENCE mixes both codecs")
        assertTrue(demo.kindOne > 0 && demo.kindTwo > 0, "DEMO mixes both codecs")
    }

    @Test
    fun anUnknownCodecIsStillRefused() {
        // The decoder supports kinds 1 and 2 and nothing else, so a file claiming a third kind must be
        // refused rather than attempted.
        assertFalse(Playability.evaluate(3).playable)
        assertFalse(Playability.evaluate(0).playable)
    }

    @Test
    fun everyMeasuredCategoryMapsBackFromAnIndexName() {
        // Guards against a rename in either place without the other following.
        for (category in MeasuredLibrary.mix.keys) {
            if (category == TrackCategory.OTHER) continue
            val roundTrip = TrackCategory.fromIndexName(category.name)
            assertEquals(category, roundTrip, "category ${category.name} does not round trip")
        }
    }
}
