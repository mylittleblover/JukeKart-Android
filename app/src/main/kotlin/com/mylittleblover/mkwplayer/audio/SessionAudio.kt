package com.mylittleblover.mkwplayer.audio

/** Resource ownership boundary used by the process-wide playback session. */
internal interface SessionAudioEngine : PlaybackEngine {
    fun nativeHandle(): Long
    fun fadeStop(beats: Double): Int = 1
    fun cancelPendingTransition(): Int
    override fun sourceSnapshot(slot: Int): LongArray?
    fun bindStreamedBwav(assetIndex: Int,bytes: ByteArray,assetId: String): Int = bindBwav(assetIndex,bytes,assetId)

    /**
     * Master output trim: lift the mix by [gainDb], then hold it under [ceilingDb].
     *
     * Lives on the engine rather than in the UI so it applies to every source the session plays, and so a
     * setting change can be applied to the running engine without reopening the track. Implementations that
     * cannot do it say so by returning a non-zero status rather than pretending.
     */
    fun setOutputGain(gainDb: Double, ceilingDb: Double): Int = 1

    /** Set dynamic gain (dB) for a compiled track layer node (`track.index`), optionally fading over [fadeMs]. */
    fun setLayerGain(trackIndex: Int, gainDb: Double, fadeMs: Int = 0): Int = 1

    fun setPlaybackRate(speed: Double, pitchSemitones: Double): Int = 1

    fun setMasterDsp(eqEnabled: Boolean, bands5: DoubleArray, bassBoostDb: Double, loudnessDb: Double): Int = 1

    fun setVisualizerEnabled(enabled: Boolean) {}

    fun visualizerSpectrum(outBands: FloatArray): Boolean = false
}

internal interface SessionAudioOutput {
    fun timingSnapshot(): LongArray?
    fun suspendStream(): Boolean = false
    fun resumeStream(sampleRate: Int): Boolean = false
    fun close()
}
