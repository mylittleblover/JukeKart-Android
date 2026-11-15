package com.mylittleblover.mkwplayer.audio

import android.content.Context
import com.mylittleblover.mkwplayer.audio.OutputTrim

/**
 * Stores the chosen [OutputTrim] and applies it to engines as they are created.
 *
 * Kept next to the language setting in intent: it is a device-wide preference read before playback starts, not
 * part of a track's identity. It therefore never enters a pack, a graph or a saved route.
 */
internal object OutputTrimSettings {
    private const val FILE = "mkw-output-trim"
    private const val GAIN = "gain-db"
    private const val CEILING = "ceiling-db"

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** The stored trim, or the default when nothing was chosen or the stored pair is not offered. */
    fun current(context: Context): OutputTrim {
        val prefs = preferences(context)
        // `contains` rather than a sentinel default: 0 dB is a real choice and must not read as "unset".
        if (!prefs.contains(GAIN)) return OutputTrim.default
        val gain = prefs.getFloat(GAIN, OutputTrim.default.gainDb.toFloat()).toDouble()
        val ceiling = prefs.getFloat(CEILING, OutputTrim.default.ceilingDb.toFloat()).toDouble()
        return OutputTrim.of(gain, ceiling)
    }

    fun select(context: Context, trim: OutputTrim) {
        preferences(context).edit()
            .putFloat(GAIN, trim.gainDb.toFloat())
            .putFloat(CEILING, trim.ceilingDb.toFloat())
            .apply()
    }
}
