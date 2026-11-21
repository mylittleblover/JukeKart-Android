package com.mylittleblover.mkwplayer.audio

import android.content.Context

internal data class PlaybackBehaviorConfig(
    val segmentSwitchFadeOutMs: Int = 0,
    val perfectBeatEnabled: Boolean = false,
    val liveVisualizerEnabled: Boolean = false,
)

/**
 * Device-wide interactive/flow playback preferences persisted outside tracks, packs, and routes.
 */
internal object PlaybackBehaviorSettings {
    private const val FILE = "mkw-playback-behavior"
    private const val KEY_SEGMENT_SWITCH_FADEOUT_MS = "segment-switch-fadeout-ms"
    private const val KEY_PERFECT_BEAT = "perfect-beat-enabled"
    private const val KEY_LIVE_VISUALIZER = "live-visualizer-enabled"
    const val MAX_SEGMENT_SWITCH_FADEOUT_MS = 30_000

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun current(context: Context): PlaybackBehaviorConfig {
        val prefs = preferences(context)
        val fadeMs = prefs.getInt(KEY_SEGMENT_SWITCH_FADEOUT_MS, 0)
            .coerceIn(0, MAX_SEGMENT_SWITCH_FADEOUT_MS)
        val perfectBeat = prefs.getBoolean(KEY_PERFECT_BEAT, false)
        val liveVisualizer = prefs.getBoolean(KEY_LIVE_VISUALIZER, false)
        return PlaybackBehaviorConfig(
            segmentSwitchFadeOutMs = fadeMs,
            perfectBeatEnabled = perfectBeat,
            liveVisualizerEnabled = liveVisualizer,
        )
    }

    fun setSegmentSwitchFadeOutMs(context: Context, ms: Int): PlaybackBehaviorConfig {
        val clamped = ms.coerceIn(0, MAX_SEGMENT_SWITCH_FADEOUT_MS)
        preferences(context).edit()
            .putInt(KEY_SEGMENT_SWITCH_FADEOUT_MS, clamped)
            .apply()
        return current(context)
    }

    fun setPerfectBeatEnabled(context: Context, enabled: Boolean): PlaybackBehaviorConfig {
        preferences(context).edit()
            .putBoolean(KEY_PERFECT_BEAT, enabled)
            .apply()
        return current(context)
    }

    fun setLiveVisualizerEnabled(context: Context, enabled: Boolean): PlaybackBehaviorConfig {
        preferences(context).edit()
            .putBoolean(KEY_LIVE_VISUALIZER, enabled)
            .apply()
        return current(context)
    }
}
