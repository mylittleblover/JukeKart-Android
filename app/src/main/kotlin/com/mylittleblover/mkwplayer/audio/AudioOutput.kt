package com.mylittleblover.mkwplayer.audio

import com.mylittleblover.player2.platform.audio.NativeAudioLibrary

/**
 * Thin Kotlin owner for the native audio output.
 *
 * Ownership rules come straight from the engine's contract and are enforced
 * here rather than assumed by callers:
 *  - one engine has at most one output consumer at a time
 *  - mutations, binding and seek require the consumer to be stopped
 *  - each handle is destroyed exactly once
 *
 * This class does not decode, schedule or resample. The native Engine owns all
 * audio semantics; anything here that looked like policy would be a second
 * implementation of it.
 */
internal class AudioOutput private constructor(
    private var engine: Long,
    private val handle: Long,
    sampleRate: Int,
) : SessionAudioOutput {
    var sampleRate: Int = sampleRate
        private set
    private var closed = false
    private var suspended = false

    companion object {
        /**
         * Creates and starts the device output for [engine].
         *
         * Returns null when the device cannot be opened, which is a normal
         * condition on emulators and on devices with no audio route. Callers must
         * treat null as "no device output" and fall back to control rendering,
         * not as a fatal error.
         */
        fun open(engine: Long, sampleRate: Int): AudioOutput? {
            if (engine == 0L) return null
            val handle = NativeAudioLibrary.createOutput(engine)
            if (handle == 0L) return null
            if (!NativeAudioLibrary.startOutput(handle, sampleRate)) {
                NativeAudioLibrary.destroyOutput(handle)
                return null
            }
            return AudioOutput(engine, handle, sampleRate)
        }
    }

    /** Bytes-per-frame the callback writes: stereo interleaved float. */
    val channelCount: Int = 2

    /**
     * True once the stream reports a non-zero device rate.
     *
     * `outputSnapshot` returns `[rate, lastError, rebuilds, callbacks]`; a zero
     * rate means the device never opened, not that audio is silent.
     */
    fun isRunning(): Boolean = !closed && !suspended && (snapshotRate() ?: 0) > 0

    private fun snapshotRate(): Int? =
        NativeAudioLibrary.outputSnapshot(handle)?.takeIf { it.isNotEmpty() }?.get(0)?.toInt()

    /** Oboe-reported fields: rate, last error, rebuild count, callback count. */
    fun snapshot(): LongArray? = if (closed) null else NativeAudioLibrary.outputSnapshot(handle)

    /**
     * Callback timing packet: `[wire, capacity, captured, total, missed,
     * clockFailures, clipped, saturated, cpu p50/p95/p99/p999/max, wall p50/p95/
     * p99/p999/max, xruns, budgetNanos]`.
     *
     * Returns null once closed, or when the native side has no packet. Callers pass
     * this to [TimingDiagnosis.read], which refuses an unexpected wire version.
     */
    override fun timingSnapshot(): LongArray? =
        if (closed) null else NativeAudioLibrary.outputTimingSnapshot(handle)

    /**
     * Rebuilds the stream after a device change. Returns false when the device
     * refused; the caller should then stop and reopen rather than render into a
     * dead stream.
     */
    fun rebuild(rate: Int): Boolean = !closed && !suspended && NativeAudioLibrary.rebuildOutput(handle, rate)

    /**
     * Stops the stream and releases the engine output lease without destroying the
     * underlying native output object or its recovery thread.
     */
    override fun suspendStream(): Boolean {
        if (closed) return false
        if (suspended) return true
        NativeAudioLibrary.stopOutput(handle)
        suspended = true
        return true
    }

    /** Restarts a previously suspended stream on the same native output handle. */
    override fun resumeStream(sampleRate: Int): Boolean {
        if (closed) return false
        if (!suspended) return true
        if (!NativeAudioLibrary.startOutput(handle, sampleRate)) {
            return false
        }
        suspended = false
        return true
    }

    /** Hot-swaps the bound native engine without stopping the active Oboe hardware stream when compatible. */
    override fun rebindEngine(engineHandle: Long, sampleRate: Int): Boolean {
        if (closed || engineHandle == 0L) return false
        if (!NativeAudioLibrary.rebindOutput(handle, engineHandle, sampleRate)) {
            return false
        }
        engine = engineHandle
        this.sampleRate = sampleRate
        suspended = false
        return true
    }

    /** Stops and releases the consumer. Safe to call more than once. */
    override fun close() {
        if (closed) return
        closed = true
        if (!suspended) {
            NativeAudioLibrary.stopOutput(handle)
        }
        suspended = false
        NativeAudioLibrary.destroyOutput(handle)
    }
}
