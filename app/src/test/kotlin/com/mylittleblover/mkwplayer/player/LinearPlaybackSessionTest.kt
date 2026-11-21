package com.mylittleblover.mkwplayer.player

import com.mylittleblover.mkwplayer.audio.AudioEngine
import com.mylittleblover.mkwplayer.audio.EngineSnapshot
import com.mylittleblover.mkwplayer.audio.SessionAudioEngine
import com.mylittleblover.mkwplayer.audio.SessionAudioOutput
import kotlinx.coroutines.launch
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

    @Test
    fun seekAsyncCoalescesRapidSeeksCancelsPendingTransitionAndGuardsMidSeekPoll() = kotlinx.coroutines.runBlocking<Unit> {
        var snapshotCalls = 0
        var cancelCalls = 0
        val seekTargets = mutableListOf<Long>()
        val firstSeekEntered = kotlinx.coroutines.CompletableDeferred<Unit>()
        val releaseFirstSeek = kotlinx.coroutines.CompletableDeferred<Unit>()

        val engine = object : SessionAudioEngine {
            var outputActive = false
            var transportFrame = 0L
            var currentSegment = 100L
            override val sampleRate: Int = 48_000
            override fun nativeHandle(): Long = 202L
            override fun cancelPendingTransition(): Int { cancelCalls++; return AudioEngine.STATUS_OK }
            override fun loadGraph(packet: ByteArray): Int = AudioEngine.STATUS_OK
            override fun bindBwav(assetIndex: Int, bytes: ByteArray, assetId: String): Int = AudioEngine.STATUS_OK
            override fun play(target: Int): Int = AudioEngine.STATUS_OK
            override fun stop(): Int = AudioEngine.STATUS_OK
            override fun pause(): Int = AudioEngine.STATUS_OK
            override fun resume(): Int = AudioEngine.STATUS_OK
            override fun seek(frame: Long): Int {
                assertFalse(outputActive, "output must stay closed across async seek")
                seekTargets += frame
                if (seekTargets.size == 1) {
                    firstSeekEntered.complete(Unit)
                    while (!releaseFirstSeek.isCompleted) {
                        Thread.sleep(5)
                    }
                }
                transportFrame = frame
                return AudioEngine.STATUS_OK
            }
            override fun snapshot(): LongArray {
                snapshotCalls++
                return LongArray(65).also { arr ->
                    arr[0] = EngineSnapshot.WIRE_VERSION
                    arr[1] = transportFrame
                    arr[2] = EngineSnapshot.PLAYING
                    arr[3] = 1L
                    arr[11] = currentSegment
                }
            }
            override fun sourceSnapshot(slot: Int): LongArray? = null
            override fun close() { outputActive = false }
        }

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

        val prep = session.prepareFlow(
            title = "Knockout Tour",
            packet = byteArrayOf(1),
            assets = emptyList(),
            frames = 480_000L,
            startSegment = 100,
            autoAdvance = mapOf(100 to 101),
            prequeueInitial = true,
            linear = true,
        )
        assertTrue(prep is PlaybackSession.PrepareResult.Ok)
        session.commitPrepared((prep as PlaybackSession.PrepareResult.Ok).prepared)
        assertEquals(1, outputOpenCount)

        val job1 = launch(kotlinx.coroutines.Dispatchers.Unconfined) {
            session.seekAsync(100_000L, kotlinx.coroutines.Dispatchers.IO)
        }
        firstSeekEntered.await()
        assertTrue(session.isSeeking)
        val snapshotsBeforePoll = snapshotCalls
        session.poll()
        assertEquals(snapshotsBeforePoll, snapshotCalls, "poll() must skip native snapshots while seek is active")

        val job2 = launch(kotlinx.coroutines.Dispatchers.Unconfined) {
            session.seekAsync(200_000L, kotlinx.coroutines.Dispatchers.IO)
        }
        val job3 = launch(kotlinx.coroutines.Dispatchers.Unconfined) {
            session.seekAsync(350_000L, kotlinx.coroutines.Dispatchers.IO)
        }
        releaseFirstSeek.complete(Unit)
        job1.join()
        job2.join()
        job3.join()

        assertFalse(session.isSeeking)
        assertEquals(1, cancelCalls)
        assertEquals(listOf(100_000L, 350_000L), seekTargets)
        assertEquals(350_000L, session.state.value.positionFrames)
        assertEquals(1, outputCloseCount)
        assertEquals(2, outputOpenCount)
        assertTrue(session.hasOutput)
    }

    @Test
    fun prepareFlowWithInitialSeekSkipsPrequeueBeforeSeekAndIgnoresDeadlineMissOnPoll() {
        val playCalls = mutableListOf<Int>()
        var failNextTransitionWithDeadline = false
        val engine = object : SessionAudioEngine {
            var transportFrame = 0L
            override val sampleRate: Int = 48_000
            override fun nativeHandle(): Long = 303L
            override fun cancelPendingTransition(): Int = AudioEngine.STATUS_OK
            override fun loadGraph(packet: ByteArray): Int = AudioEngine.STATUS_OK
            override fun bindBwav(assetIndex: Int, bytes: ByteArray, assetId: String): Int = AudioEngine.STATUS_OK
            override fun play(target: Int): Int {
                playCalls += target
                return if (failNextTransitionWithDeadline && target == 101) {
                    AudioEngine.STATUS_DEADLINE_MISS
                } else {
                    AudioEngine.STATUS_OK
                }
            }
            override fun stop(): Int = AudioEngine.STATUS_OK
            override fun pause(): Int = AudioEngine.STATUS_OK
            override fun resume(): Int = AudioEngine.STATUS_OK
            override fun seek(frame: Long): Int {
                transportFrame = frame
                return AudioEngine.STATUS_OK
            }
            override fun snapshot(): LongArray = LongArray(65).also { arr ->
                arr[0] = EngineSnapshot.WIRE_VERSION
                arr[1] = transportFrame
                arr[2] = EngineSnapshot.PLAYING
                arr[3] = 1L
                arr[11] = 100L
            }
            override fun sourceSnapshot(slot: Int): LongArray? = null
            override fun close() {}
        }

        val session = PlaybackSession(createEngine = { engine }, openOutput = { _, _ -> null })
        val prep = session.prepareFlow(
            title = "Chain Stop 2",
            packet = byteArrayOf(1),
            assets = emptyList(),
            frames = 400_000L,
            startSegment = 100,
            autoAdvance = mapOf(100 to 101),
            prequeueInitial = true,
            linear = true,
            initialSeekFrames = 380_000L,
        )
        assertTrue(prep is PlaybackSession.PrepareResult.Ok)
        assertEquals(listOf(100), playCalls, "must seek before queuing next chain segment")

        failNextTransitionWithDeadline = true
        val committed = session.commitPrepared((prep as PlaybackSession.PrepareResult.Ok).prepared)
        assertEquals(380_000L, committed.positionFrames)

        val afterFirstPoll = session.poll()
        assertEquals(null, afterFirstPoll.detail, "DeadlineMiss must not surface an error in linear chain mode")
        assertTrue(afterFirstPoll.loaded)
        assertEquals(380_000L, afterFirstPoll.positionFrames)
        assertEquals(listOf(100, 101), playCalls)

        session.poll()
        assertEquals(listOf(100, 101), playCalls, "must not retry DeadlineMiss segment on every subsequent poll")
    }
}
