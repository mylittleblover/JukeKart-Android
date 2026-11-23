package com.mylittleblover.mkwplayer.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import com.mylittleblover.player2.platform.audio.NativeAudioLibrary
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thin Kotlin owner for the device audio output.
 *
 * Ownership rules come straight from the engine's contract and are enforced
 * here rather than assumed by callers:
 *  - one engine has at most one output consumer at a time
 *  - mutations, binding and seek require the consumer to be stopped
 *  - each handle is destroyed exactly once
 *
 * Uses Android's standard [AudioTrack] (`USAGE_MEDIA` / `CONTENT_TYPE_MUSIC` /
 * `CHANNEL_OUT_STEREO` / `ENCODING_PCM_16BIT` / `MODE_STREAM` / `PERFORMANCE_MODE_NONE`)
 * so AudioFlinger routes speaker, wired, USB-C, and Bluetooth TWS earbuds through the exact
 * same deep-buffer stereo pipeline as mainstream Android music players, avoiding OEM AAudio
 * single-ear / stutter quirks on MIUI/Qualcomm Bluetooth stacks. Falls back to Oboe only if
 * [AudioTrack] cannot initialize.
 */
internal class AudioOutput private constructor(
    private var engine: Long,
    private val handle: Long,
    sampleRate: Int,
    private var usingAudioTrack: Boolean,
) : SessionAudioOutput {
    var sampleRate: Int = sampleRate
        private set
    private var closed = false
    private var suspended = false

    private val trackLock = Any()
    @Volatile
    private var audioTrack: AudioTrack? = null
    private var writerThread: Thread? = null
    private val writerRunning = AtomicBoolean(false)
    @Volatile
    private var accumulatedUnderruns: Long = 0L

    companion object {
        private const val CHUNK_FRAMES = 2048
        private const val TARGET_BUFFER_FRAMES = 16384
        private const val PREFILL_CHUNKS = 2
        private const val STEREO_PCM16_FRAME_BYTES = 4
        private const val MAX_CONSECUTIVE_ZERO_WRITES = 25

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
            val targetRate = if (sampleRate in 8000..192_000) sampleRate else 48_000
            val handle = NativeAudioLibrary.createOutput(engine)
            if (handle == 0L) return null

            val out = AudioOutput(engine, handle, targetRate, usingAudioTrack = false)
            if (out.startAudioTrack(targetRate)) {
                return out
            }
            if (NativeAudioLibrary.startOutput(handle, targetRate)) {
                return out
            }
            NativeAudioLibrary.destroyOutput(handle)
            return null
        }

        private fun createTrack(sampleRate: Int, bufferBytes: Int, performanceMode: Int): AudioTrack? {
            return runCatching {
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build(),
                    )
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(bufferBytes)
                    .setPerformanceMode(performanceMode)
                    .build()
                if (track.state != AudioTrack.STATE_INITIALIZED) {
                    track.release()
                    null
                } else {
                    track
                }
            }.getOrNull()
        }

        private fun buildAudioTrack(sampleRate: Int): Pair<AudioTrack, Int>? {
            val minBytes = runCatching {
                AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT,
                )
            }.getOrDefault(AudioTrack.ERROR_BAD_VALUE)
            if (minBytes <= 0) return null
            val rawBytes = maxOf(minBytes * 4, TARGET_BUFFER_FRAMES * STEREO_PCM16_FRAME_BYTES)
            val bufferBytes = (rawBytes + STEREO_PCM16_FRAME_BYTES - 1) and (STEREO_PCM16_FRAME_BYTES - 1).inv()
            // Request deep-buffer output (AUDIO_OUTPUT_FLAG_DEEP_BUFFER) first for maximum
            // Bluetooth A2DP / TWS stability on Qualcomm/MIUI HALs; fall back to NONE if refused.
            val track = createTrack(sampleRate, bufferBytes, AudioTrack.PERFORMANCE_MODE_POWER_SAVING)
                ?: createTrack(sampleRate, bufferBytes, AudioTrack.PERFORMANCE_MODE_NONE)
                ?: return null
            return track to bufferBytes
        }
    }

    /**
     * Pre-fills [track] with rendered PCM before calling [AudioTrack.play] so AudioFlinger never
     * starts pulling from an empty ring buffer (which triggers immediate underruns during BT A2DP
     * stream startup). Returns the start offset of any unconsumed tail remaining in [pcm]
     * (`pcm.size` when all pre-filled chunks wrote completely), or -1 on fatal write/render error.
     */
    private fun prefillAndPlay(track: AudioTrack, bufferBytes: Int, pcm: ShortArray): Int {
        val maxPrefillShorts = ((bufferBytes / 4) and 1.inv()).coerceAtLeast(0)
        var prefilledShorts = 0
        var pendingOffset = pcm.size
        var chunks = 0
        while (chunks < PREFILL_CHUNKS && prefilledShorts + pcm.size <= maxPrefillShorts) {
            if (!NativeAudioLibrary.renderOutputPcm16(handle, pcm, CHUNK_FRAMES)) {
                return -1
            }
            val written = runCatching {
                track.write(pcm, 0, pcm.size, AudioTrack.WRITE_NON_BLOCKING)
            }.getOrDefault(AudioTrack.ERROR_DEAD_OBJECT)
            if (written < 0) return -1
            prefilledShorts += written
            chunks++
            if (written < pcm.size) {
                pendingOffset = written
                break
            }
        }
        val started = runCatching {
            track.play()
            true
        }.getOrDefault(false)
        return if (started) pendingOffset else -1
    }

    private fun startAudioTrack(rate: Int): Boolean {
        val targetRate = if (rate in 8000..192_000) rate else 48_000
        val built = buildAudioTrack(targetRate) ?: return false
        val (track, bufferBytes) = built
        if (!NativeAudioLibrary.attachExternalOutput(handle, targetRate)) {
            runCatching { track.release() }
            return false
        }
        val initialPcm = ShortArray(CHUNK_FRAMES * 2)
        val initialPendingOffset = prefillAndPlay(track, bufferBytes, initialPcm)
        if (initialPendingOffset < 0) {
            runCatching { track.release() }
            NativeAudioLibrary.stopOutput(handle)
            return false
        }
        synchronized(trackLock) {
            accumulatedUnderruns = 0L
            audioTrack = track
        }
        usingAudioTrack = true
        this.sampleRate = targetRate
        writerRunning.set(true)
        val thread = Thread(
            {
                runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO) }
                val pcm = initialPcm
                var activeTrack = track
                var pendingOffset = initialPendingOffset

                fun writeRemaining(startOffset: Int): Boolean {
                    var offset = startOffset
                    var zeroRetries = 0
                    while (offset < pcm.size && writerRunning.get()) {
                        val written = runCatching {
                            activeTrack.write(pcm, offset, pcm.size - offset, AudioTrack.WRITE_BLOCKING)
                        }.getOrDefault(AudioTrack.ERROR_DEAD_OBJECT)
                        when {
                            written < 0 -> {
                                if (!writerRunning.get()) return false
                                val aligned = offset and 1.inv()
                                val recovered = recoverTrack(activeTrack, targetRate, pcm, aligned) ?: return false
                                activeTrack = recovered.first
                                offset = recovered.second
                                zeroRetries = 0
                            }
                            written == 0 -> {
                                zeroRetries++
                                if (zeroRetries >= MAX_CONSECUTIVE_ZERO_WRITES) {
                                    if (!writerRunning.get()) return false
                                    val aligned = offset and 1.inv()
                                    val recovered = recoverTrack(activeTrack, targetRate, pcm, aligned) ?: return false
                                    activeTrack = recovered.first
                                    offset = recovered.second
                                    zeroRetries = 0
                                } else {
                                    try {
                                        Thread.sleep(2L)
                                    } catch (_: InterruptedException) {
                                        return false
                                    }
                                }
                            }
                            else -> {
                                offset += written
                                zeroRetries = 0
                            }
                        }
                    }
                    return writerRunning.get()
                }

                if (pendingOffset < pcm.size && !writeRemaining(pendingOffset)) {
                    return@Thread
                }
                while (writerRunning.get()) {
                    if (!NativeAudioLibrary.renderOutputPcm16(handle, pcm, CHUNK_FRAMES)) {
                        break
                    }
                    if (!writeRemaining(0)) {
                        break
                    }
                }
            },
            "mkw-audiotrack",
        ).apply {
            isDaemon = true
            start()
        }
        writerThread = thread
        return true
    }

    /**
     * Rebuilds a dead [AudioTrack] and pre-fills the in-flight [pendingPcm] slice from [pendingOffset]
     * before calling [AudioTrack.play], preserving chronological sample order without dropping data.
     */
    private fun recoverTrack(
        failed: AudioTrack,
        targetRate: Int,
        pendingPcm: ShortArray,
        pendingOffset: Int,
    ): Pair<AudioTrack, Int>? {
        synchronized(trackLock) {
            if (audioTrack === failed) {
                accumulatedUnderruns += runCatching { failed.underrunCount.toLong() }.getOrDefault(0L)
                audioTrack = null
            }
            runCatching { failed.stop() }
            runCatching { failed.release() }
        }
        val retryDelays = longArrayOf(120L, 200L, 350L, 500L)
        for (delayMs in retryDelays) {
            if (!writerRunning.get()) return null
            try {
                Thread.sleep(delayMs)
            } catch (_: InterruptedException) {
                return null
            }
            if (!writerRunning.get()) return null
            val rebuilt = buildAudioTrack(targetRate)?.first ?: continue
            var nextOffset = pendingOffset
            if (nextOffset < pendingPcm.size) {
                val prefilled = runCatching {
                    rebuilt.write(pendingPcm, nextOffset, pendingPcm.size - nextOffset, AudioTrack.WRITE_NON_BLOCKING)
                }.getOrDefault(AudioTrack.ERROR_DEAD_OBJECT)
                if (prefilled < 0) {
                    runCatching { rebuilt.release() }
                    continue
                }
                nextOffset += prefilled
            }
            val started = runCatching {
                rebuilt.play()
                true
            }.getOrDefault(false)
            if (!started) {
                runCatching { rebuilt.release() }
                continue
            }
            synchronized(trackLock) {
                if (!writerRunning.get()) {
                    runCatching { rebuilt.stop() }
                    runCatching { rebuilt.release() }
                    return null
                }
                audioTrack = rebuilt
            }
            return rebuilt to nextOffset
        }
        return null
    }

    private fun stopAudioTrack() {
        writerRunning.set(false)
        synchronized(trackLock) {
            val track = audioTrack
            if (track != null) {
                runCatching { track.pause() }
                runCatching { track.flush() }
                runCatching { track.stop() }
            }
        }
        val thread = writerThread
        if (thread != null && thread !== Thread.currentThread()) {
            thread.interrupt()
            runCatching { thread.join(1000L) }
        }
        writerThread = null
        synchronized(trackLock) {
            val track = audioTrack
            audioTrack = null
            if (track != null) {
                runCatching { track.stop() }
                runCatching { track.release() }
            }
        }
        usingAudioTrack = false
    }

    /** Bytes-per-frame the callback writes: stereo interleaved PCM. */
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

    /** Output-reported fields: rate, last error, rebuild count, callback count. */
    fun snapshot(): LongArray? = if (closed) null else NativeAudioLibrary.outputSnapshot(handle)

    /**
     * Callback timing packet: `[wire, capacity, captured, total, missed,
     * clockFailures, clipped, saturated, cpu p50/p95/p99/p999/max, wall p50/p95/
     * p99/p999/max, xruns, budgetNanos]`.
     *
     * Returns null once closed, or when the native side has no packet. Callers pass
     * this to [TimingDiagnosis.read], which refuses an unexpected wire version.
     */
    override fun timingSnapshot(): LongArray? {
        if (closed) return null
        val snapshot = NativeAudioLibrary.outputTimingSnapshot(handle) ?: return null
        if (usingAudioTrack && snapshot.size >= 19) {
            val totalUnderruns = synchronized(trackLock) {
                accumulatedUnderruns + runCatching { audioTrack?.underrunCount?.toLong() ?: 0L }.getOrDefault(0L)
            }
            snapshot[18] = totalUnderruns
        }
        return snapshot
    }

    /**
     * Rebuilds the stream explicitly. Returns false when the device refused;
     * the caller should then stop and reopen rather than render into a dead stream.
     */
    fun rebuild(rate: Int): Boolean {
        if (closed || suspended) return false
        if (usingAudioTrack) {
            val targetRate = if (rate in 8000..192_000) rate else sampleRate
            stopAudioTrack()
            NativeAudioLibrary.stopOutput(handle)
            return startAudioTrack(targetRate)
        }
        return NativeAudioLibrary.rebuildOutput(handle, rate)
    }

    /**
     * Route-change hook from [PlaybackService]. Standard [AudioTrack] is rerouted seamlessly by
     * AudioFlinger across speaker, wired, and Bluetooth TWS earbuds without tearing down the
     * stream (tearing down mid-TWS handshake causes single-ear dropouts); only Oboe fallback needs
     * an explicit stream rebuild.
     */
    override fun rebuildStream(sampleRate: Int): Boolean {
        if (closed || suspended) return false
        if (usingAudioTrack) return true
        return NativeAudioLibrary.rebuildOutput(handle, sampleRate)
    }

    /**
     * Stops the stream and releases the engine output lease without destroying the
     * underlying native output object or its recovery thread.
     */
    override fun suspendStream(): Boolean {
        if (closed) return false
        if (suspended) return true
        if (usingAudioTrack) {
            stopAudioTrack()
        }
        NativeAudioLibrary.stopOutput(handle)
        suspended = true
        return true
    }

    /** Restarts a previously suspended stream on the same native output handle. */
    override fun resumeStream(sampleRate: Int): Boolean {
        if (closed) return false
        if (!suspended) return true
        val targetRate = if (sampleRate in 8000..192_000) sampleRate else this.sampleRate
        if (startAudioTrack(targetRate)) {
            suspended = false
            return true
        }
        if (!NativeAudioLibrary.startOutput(handle, targetRate)) {
            return false
        }
        this.sampleRate = targetRate
        suspended = false
        return true
    }

    /** Hot-swaps the bound native engine without stopping the active AudioTrack or Oboe stream. */
    override fun rebindEngine(engineHandle: Long, sampleRate: Int): Boolean {
        if (closed || engineHandle == 0L) return false
        if (usingAudioTrack && !suspended) {
            if (!NativeAudioLibrary.rebindOutput(handle, engineHandle, sampleRate)) {
                return false
            }
            engine = engineHandle
            return true
        }
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
        if (usingAudioTrack) {
            stopAudioTrack()
        }
        if (!suspended) {
            NativeAudioLibrary.stopOutput(handle)
        }
        suspended = false
        NativeAudioLibrary.destroyOutput(handle)
    }
}
