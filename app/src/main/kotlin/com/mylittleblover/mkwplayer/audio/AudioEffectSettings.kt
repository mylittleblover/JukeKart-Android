package com.mylittleblover.mkwplayer.audio

import android.content.Context
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

internal enum class EqualizerPreset(val bandsDb: List<Float>) {
    FLAT(listOf(0f, 0f, 0f, 0f, 0f)),
    BASS_BOOST(listOf(6f, 4f, 0f, 0f, 1f)),
    RACING(listOf(4f, 1.5f, -1f, 3f, 4.5f)),
    VOCAL(listOf(-2f, 0f, 4f, 3.5f, 1f)),
    ELECTRONIC(listOf(5f, 2f, -1.5f, 2.5f, 4.5f)),
    ACOUSTIC(listOf(2.5f, 1f, 1.5f, 2f, 3f)),
    TREBLE(listOf(-1f, 0f, 1f, 4f, 6f)),
    CUSTOM(listOf(0f, 0f, 0f, 0f, 0f)),
    ;

    companion object {
        fun matchingOrCustom(bands: List<Float>): EqualizerPreset =
            entries.firstOrNull { preset ->
                preset != CUSTOM &&
                    preset.bandsDb.size == bands.size &&
                    preset.bandsDb.indices.all { i -> abs(preset.bandsDb[i] - bands[i]) < 0.05f }
            } ?: CUSTOM
    }
}

internal data class MasterDspStateConfig(
    val eqEnabled: Boolean = false,
    val bandsDb: List<Float> = listOf(0f, 0f, 0f, 0f, 0f),
    val preset: EqualizerPreset = EqualizerPreset.FLAT,
    val bassBoostPercent: Int = 0,
    val loudnessDb: Float = 0f,
) {
    val bassBoostDb: Double
        get() = (bassBoostPercent.coerceIn(0, 100) / 100.0) * 9.0

    fun bandArrayDoubles(): DoubleArray = DoubleArray(5) { idx ->
        bandsDb.getOrElse(idx) { 0f }.toDouble().coerceIn(-12.0, 12.0)
    }

    companion object {
        val BAND_FREQUENCIES_HZ = listOf(60, 230, 910, 3600, 12000)
    }
}

internal data class PlaybackRateConfig(
    val speed: Float = 1.0f,
    val pitchSemitones: Float = 0f,
    val preservePitch: Boolean = true,
) {
    fun normalized(): PlaybackRateConfig {
        val cleanSpeed = ((speed.coerceIn(0.5f, 2.0f) * 20f).roundToInt()) / 20f
        val cleanPitch = if (preservePitch) {
            (pitchSemitones.coerceIn(-12f, 12f) * 2f).roundToInt() / 2f
        } else {
            semitonesForSpeed(cleanSpeed)
        }
        return copy(speed = cleanSpeed, pitchSemitones = cleanPitch)
    }

    companion object {
        fun semitonesForSpeed(speed: Float): Float {
            val safe = speed.coerceIn(0.5f, 2.0f)
            return ((12.0 * ln(safe.toDouble()) / ln(2.0)).toFloat() * 10f).roundToInt() / 10f
        }
    }
}

internal object AudioEffectSettings {
    private const val FILE = "mkw-audio-effects"
    private const val KEY_EQ_ENABLED = "eq-enabled"
    private const val KEY_EQ_PRESET = "eq-preset"
    private const val KEY_BAND_PREFIX = "eq-band-"
    private const val KEY_BASS_BOOST = "bass-boost-pct"
    private const val KEY_LOUDNESS_DB = "loudness-db"
    private const val KEY_SPEED = "playback-speed"
    private const val KEY_PITCH = "playback-pitch"
    private const val KEY_PRESERVE_PITCH = "playback-preserve-pitch"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun currentDsp(context: Context): MasterDspStateConfig {
        val p = prefs(context)
        val bands = (0 until 5).map { idx ->
            p.getFloat("$KEY_BAND_PREFIX$idx", 0f).coerceIn(-12f, 12f)
        }
        val presetName = p.getString(KEY_EQ_PRESET, null)
        val preset = EqualizerPreset.entries.firstOrNull { it.name == presetName }
            ?: EqualizerPreset.matchingOrCustom(bands)
        return MasterDspStateConfig(
            eqEnabled = p.getBoolean(KEY_EQ_ENABLED, false),
            bandsDb = bands,
            preset = preset,
            bassBoostPercent = p.getInt(KEY_BASS_BOOST, 0).coerceIn(0, 100),
            loudnessDb = p.getFloat(KEY_LOUDNESS_DB, 0f).coerceIn(0f, 9f),
        )
    }

    fun saveDsp(context: Context, cfg: MasterDspStateConfig): MasterDspStateConfig {
        val normalizedBands = (0 until 5).map { cfg.bandsDb.getOrElse(it) { 0f }.coerceIn(-12f, 12f) }
        val cleanPreset = if (cfg.preset == EqualizerPreset.CUSTOM) {
            EqualizerPreset.matchingOrCustom(normalizedBands)
        } else cfg.preset
        val editor = prefs(context).edit()
            .putBoolean(KEY_EQ_ENABLED, cfg.eqEnabled)
            .putString(KEY_EQ_PRESET, cleanPreset.name)
            .putInt(KEY_BASS_BOOST, cfg.bassBoostPercent.coerceIn(0, 100))
            .putFloat(KEY_LOUDNESS_DB, cfg.loudnessDb.coerceIn(0f, 9f))
        normalizedBands.forEachIndexed { i, v -> editor.putFloat("$KEY_BAND_PREFIX$i", v) }
        editor.apply()
        return currentDsp(context)
    }

    fun currentRate(context: Context): PlaybackRateConfig {
        val p = prefs(context)
        return PlaybackRateConfig(
            speed = p.getFloat(KEY_SPEED, 1.0f).coerceIn(0.5f, 2.0f),
            pitchSemitones = p.getFloat(KEY_PITCH, 0f).coerceIn(-12f, 12f),
            preservePitch = p.getBoolean(KEY_PRESERVE_PITCH, true),
        ).normalized()
    }

    fun saveRate(context: Context, rate: PlaybackRateConfig): PlaybackRateConfig {
        val clean = rate.normalized()
        prefs(context).edit()
            .putFloat(KEY_SPEED, clean.speed)
            .putFloat(KEY_PITCH, clean.pitchSemitones)
            .putBoolean(KEY_PRESERVE_PITCH, clean.preservePitch)
            .apply()
        return clean
    }
}
