package com.mylittleblover.mkwplayer.player

import com.mylittleblover.mkwplayer.audio.AudioEngine
import com.mylittleblover.mkwplayer.audio.EngineSnapshot
import com.mylittleblover.mkwplayer.audio.SessionAudioEngine
import com.mylittleblover.mkwplayer.audio.SessionAudioOutput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinearPlaybackSessionTest {

    private class FakeOutput(private val onClose: () -> Unit) : SessionAudioOutput {
        var closed = false
            private set

        override fun timingSnapshot(): LongArray? = null

        override fun close() {
            if (!closed) {
                closed = true
                onClose()
            }
        }
    }

    private class FakeLinearEngine : SessionAudioEngine {
        var outputActive = false
        var streamedUsed: Boolean? = null
        var repeatEnabled = false
        var seekRejectedForActiveOutput = false
        var repeatRejectedForActiveOutput = false
        var transportStatus = EngineSnapshot.STOPPED
        var transportFrame = 0L
        var sourceFrame = 0L
        var stopCalls = 0

        override val sampleRate: Int = 48_000

        override fun nativeHandle(): Long = 101L

        override fun cancelPendingTransition(): Int = 0

        override fun loadGraph(packet: ByteArray): Int = AudioEngine.STATUS_OK

        override fun bindBwav(assetIndex: Int, bytes: ByteArray, assetId: String): Int = AudioEngine.STATUS_OK

        override fun loadLinearBwav(
            bytes: ByteArray,
            assetId: String,
            loop: Boolean,
            streamed: Boolean,
        ): Int {
            streamedUsed = streamed
            repeatEnabled = loop
            return AudioEngine.STATUS_OK
        }

        override fun setLinearRepeat(enabled: Boolean, begin: Long, end: Long): Int {
            if (outputActive) {
                repeatRejectedForActiveOutput = true
                return 1
            }
            repeatEnabled = enabled
            return AudioEngine.STATUS_OK
        }

        override fun sourceSnapshot(slot: Int): LongArray {
            return LongArray(11).also { arr ->
                arr[1] = 1L
                arr[10] = sourceFrame
            }
        }

        override fun play(target: Int): Int {
            transportStatus = EngineSnapshot.PLAYING
            return AudioEngine.STATUS_OK
        }

        override fun stop(): Int {
            stopCalls++
            transportStatus = EngineSnapshot.STOPPED
            return AudioEngine.STATUS_OK
        }

        override fun pause(): Int {
            transportStatus = EngineSnapshot.PAUSED
            return AudioEngine.STATUS_OK
        }

        override fun resume(): Int {
            transportStatus = EngineSnapshot.PLAYING
            return AudioEngine.STATUS_OK
        }

        override fun seek(frame: Long): Int {
            if (outputActive) {
                seekRejectedForActiveOutput = true
                return 1
            }
            transportFrame = frame
            sourceFrame = frame
            return AudioEngine.STATUS_OK
        }

        override fun snapshot(): LongArray {
            return LongArray(65).also { arr ->
                arr[0] = EngineSnapshot.WIRE_VERSION
                arr[1] = transportFrame
                arr[2] = transportStatus
                arr[3] = if (transportStatus == EngineSnapshot.PLAYING) 1L else 0L
            }
        }

        override fun close() {
            outputActive = false
        }
    }

    @Test
    fun loadLinearStreamsSuspendsOutputAcrossSeekAndRepeatAndFiresNaturalEndOnce() {
        val engine = FakeLinearEngine()
        var outputOpenCount = 0
        var outputCloseCount = 0
        val session = PlaybackSession(
            createEngine = { engine },
            openOutput = { _, _ ->
                outputOpenCount++
                engine.outputActive = true
                FakeOutput {
                    outputCloseCount++
                    engine.outputActive = false
                }
            },
        )

        var completions = 0
        var keepLoadedOnCompletion = true
        session.onLinearCompleted = {
            completions++
            keepLoadedOnCompletion
        }

        val initial = session.loadLinear(
            title = "Super Mario World - Athletic",
            audio = byteArrayOf(1, 2, 3),
            assetId = "0".repeat(64),
            frames = 480_000L,
            loop = false,
            streamed = true,
        )
        assertTrue(initial.linear)
        assertTrue(initial.loaded)
        assertTrue(initial.playing)
        assertEquals(true, engine.streamedUsed)
        assertEquals(1, outputOpenCount)

        // Progress reads sourceSnapshot[10] so wrapped transportFrame during loops never overflows.
        engine.transportFrame = 600_000L
        engine.sourceFrame = 120_000L
        val polled = session.poll()
        assertEquals(120_000L, polled.positionFrames)

        // Repeat toggle & seek suspend and reopen AudioOutput safely around native calls.
        session.setLinearRepeat(true)
        assertFalse(engine.repeatRejectedForActiveOutput)
        assertTrue(engine.repeatEnabled)
        assertTrue(session.hasOutput)

        session.seek(240_000L)
        assertFalse(engine.seekRejectedForActiveOutput)
        assertEquals(240_000L, session.state.value.positionFrames)
        assertTrue(session.hasOutput)
        assertEquals(2, outputCloseCount)
        assertEquals(3, outputOpenCount)

        // Natural EOF triggers onLinearCompleted once and honors handoff decision.
        engine.transportStatus = EngineSnapshot.STOPPED
        val eofHandoff = session.poll()
        assertEquals(1, completions)
        assertTrue(eofHandoff.loaded)

        // Subsequent poll while still stopped does not re-fire completion.
        session.poll()
        assertEquals(1, completions)

        // Explicit stop suppresses naturalEnd & completion hook.
        engine.transportStatus = EngineSnapshot.PLAYING
        session.poll()
        keepLoadedOnCompletion = false
        val stopped = session.stop()
        assertFalse(stopped.naturalEnd)
        assertEquals(1, completions)

        // When queue declines handoff at next natural EOF, loaded flips false.
        session.loadLinear("Test", byteArrayOf(1), "1".repeat(64), 48_000L)
        session.poll()
        engine.transportStatus = EngineSnapshot.STOPPED
        val queueEnd = session.poll()
        assertEquals(2, completions)
        assertFalse(queueEnd.loaded)
        assertTrue(queueEnd.naturalEnd)
    }
}
