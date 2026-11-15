package com.mylittleblover.mkwplayer.audio

/**
 * The master output trim, as a setting.
 *
 * The game's own files are mastered quietly -- measured peaks on the shipped jukebox and course sources sit
 * well below full scale -- so a player that reproduces them faithfully sounds quiet next to anything else on
 * the phone. This lifts the whole mix and stops the lift from clipping.
 *
 * Two numbers, and their order matters:
 *
 * - [gainDb] is applied first, to the mixed result.
 * - [ceilingDb] is applied second, and only reduces what now exceeds it. Gaining after limiting would just
 *   clip, which is why the order is fixed in the native runtime rather than left to a caller.
 *
 * The values are a small closed set rather than a slider: a listener who wants "louder" should get a setting
 * that is known to be safe, not a range that invites +24 dB over an already hot track.
 */
internal data class OutputTrim(
    /** Decibels added to the mix. */
    val gainDb: Double,
    /**
     * Level the result is held under, in decibels relative to full scale.
     *
     * Zero means "no ceiling": the gain is applied and nothing is caught. A negative value means the limiter
     * is on. There is no positive value, because a ceiling above full scale is not a ceiling.
     */
    val ceilingDb: Double,
) {

    /** A ceiling below zero means "limiting is on". */
    val limits: Boolean get() = ceilingDb < 0.0

    /**
     * The trim as the settings screen should describe it.
     *
     * Written out rather than formatted from the numbers, because "+10 dB, peaks held under -1 dB" is what a
     * listener is choosing and "10.0/-1.0" is what the code happens to store.
     */
    val summary: String
        get() = when {
            gainDb == 0.0 && !limits -> "Unchanged"
            gainDb == 0.0 -> "Unchanged, peaks held under ${format(ceilingDb)}"
            limits -> "+${format(gainDb)}, peaks held under ${format(ceilingDb)}"
            else -> "+${format(gainDb)}"
        }

    private fun format(value: Double): String =
        if (value == value.toLong().toDouble()) "${value.toLong()} dB" else "$value dB"

    companion object {
        /** Nothing added: faithful to the source. */
        val Flat = OutputTrim(gainDb = 0.0, ceilingDb = 0.0)

        /** The default for quiet game rips: +10 dB, held under -1 dBFS. */
        val Boosted = OutputTrim(gainDb = 10.0, ceilingDb = -1.0)

        /** Every choice the settings screen offers, in the order it shows them. */
        val choices: List<OutputTrim> = listOf(Flat, Boosted)

        /** The one used when nothing has been chosen. */
        val default: OutputTrim get() = Boosted

        /** The trim matching stored values, or [default] when they are not one of the offered choices. */
        fun of(gainDb: Double, ceilingDb: Double): OutputTrim =
            choices.firstOrNull { it.gainDb == gainDb && it.ceilingDb == ceilingDb } ?: default
    }
}
