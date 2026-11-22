package com.mylittleblover.mkwplayer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mylittleblover.mkwplayer.audio.AudioEngine
import com.mylittleblover.mkwplayer.graph.GraphBuilder
import com.mylittleblover.mkwplayer.interactive.FinalLapFanfareCompiler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Verifies native 1-second perceptual linear-amplitude layer fades used by Koopa Troopa Beach
 * (`Speaker` overlay): smooth -96 dB <-> 0 dB ramp over 48,000 frames @ 48 kHz, freeze on pause,
 * and clean mid-fade reversal without jumps.
 */
@RunWith(AndroidJUnit4::class)
class SpeakerLayerFadeDeviceTest {

    private fun readBwavFacts(bytes: ByteArray): Triple<Int, Int, Int> {
        assertEquals("BWAV", String(bytes, 0, 4, Charsets.US_ASCII))
        fun u16(at: Int) = (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8)
        fun u32(at: Int) = (bytes[at].toInt() and 0xFF) or
            ((bytes[at + 1].toInt() and 0xFF) shl 8) or
            ((bytes[at + 2].toInt() and 0xFF) shl 16) or
            ((bytes[at + 3].toInt() and 0xFF) shl 24)
        val channels = u16(14)
        val rate = u32(16 + 4)
        val frames = u32(16 + 12)
        return Triple(rate, channels, frames)
    }

    private fun renderTailRms(engine: AudioEngine, totalFrames: Int): Double {
        var remaining = totalFrames
        var lastChunk: FloatArray? = null
        while (remaining > 0) {
            val step = minOf(2400, remaining)
            val chunk = engine.render(step)
            assertNotNull("engine.render($step) returned null", chunk)
            lastChunk = chunk
            remaining -= step
        }
        val samples = requireNotNull(lastChunk)
        var sumSq = 0.0
        for (sample in samples) {
            sumSq += sample * sample
        }
        return sqrt(sumSq / samples.size)
    }

    private fun layerTrimAndFinalDb(engine: AudioEngine, trackIndex: Int): Pair<Double, Double> {
        val snap = requireNotNull(engine.layerGainSnapshot(trackIndex)) { "Missing layerGainSnapshot" }
        assertEquals("Expected valid layer gain snapshot header", 1L, snap[0])
        assertEquals("Expected layer snapshot for trackIndex=$trackIndex", trackIndex.toLong(), snap[1])
        return Double.fromBits(snap[2]) to Double.fromBits(snap[5])
    }

    @Test
    fun speakerLayerFadesSmoothlyOverOneSecondPausesAndReversesCleanly() {
        val audio = FinalLapFanfareCompiler.createPlanarPcm16Bwav(
            frames = 48_000,
            sampleRate = 48_000,
            channels = 2,
            toneHz = 440.0,
            amplitude = 0.3,
            looping = true,
        )
        val (rate, channels, frames) = readBwavFacts(audio)
        val sha256 = MessageDigest.getInstance("SHA-256").digest(audio)

        val packet = GraphBuilder.build(
            GraphBuilder.Spec(
                asset = GraphBuilder.Asset(
                    path = "synthetic_speaker_440hz.bwav",
                    sampleRate = rate,
                    channels = channels,
                    frames = frames.toLong(),
                    sha256 = sha256,
                    loop = GraphBuilder.Loop(0L, frames.toLong()),
                ),
                transportSampleRate = 48000,
            ),
        )

        // GraphBuilder assigns segment index = 1 and track index = 2.
        val segmentId = 1
        val trackId = 2
        val engine = requireNotNull(AudioEngine.create()) { "AudioEngine.create() returned null" }
        try {
            assertEquals(AudioEngine.STATUS_OK, engine.loadGraph(packet))
            assertEquals(AudioEngine.STATUS_OK, engine.bindBwav(0, audio, "synthetic_speaker_440hz.bwav"))
            assertEquals(AudioEngine.STATUS_OK, engine.setLayerGain(trackId, -96.0))
            assertEquals(AudioEngine.STATUS_OK, engine.play(segmentId))

            // Start muted (-96 dB).
            val mutedRms = renderTailRms(engine, 4800)
            val (mutedTrimDb, mutedFinalDb) = layerTrimAndFinalDb(engine, trackId)
            assertTrue("Expected near-silent muted layer before fade-in, got rms=$mutedRms", mutedRms <= 0.0005)
            assertTrue("Expected muted layer trimDb <= -95.9, got $mutedTrimDb (final=$mutedFinalDb)", mutedTrimDb <= -95.9)

            // 1000 ms linear-amplitude fade-in -> ~ -6.02 dB at 500 ms (24,000 frames), 0.0 dB at 1000 ms (48,000 frames).
            assertEquals(AudioEngine.STATUS_OK, engine.setLayerGain(trackId, 0.0, fadeMs = 1000))
            val midInRms = renderTailRms(engine, 24_000)
            val (midInDb, _) = layerTrimAndFinalDb(engine, trackId)
            assertTrue("Expected ~ -6 dB at 500ms of fade-in, got $midInDb dB", midInDb in -7.5..-4.5)
            assertTrue("Expected audible midpoint rms > muted rms ($mutedRms), got $midInRms", midInRms > mutedRms + 0.005)

            val fullInRms = renderTailRms(engine, 24_000)
            val (fullInDb, fullInFinalDb) = layerTrimAndFinalDb(engine, trackId)
            assertTrue("Expected 0.0 dB after 1000ms fade-in, got $fullInDb dB (final=$fullInFinalDb)", abs(fullInDb) < 0.05)
            assertTrue("Expected louder full rms ($fullInRms) than midpoint ($midInRms)", fullInRms > midInRms)

            // 1000 ms fade-out back to -96 dB.
            assertEquals(AudioEngine.STATUS_OK, engine.setLayerGain(trackId, -96.0, fadeMs = 1000))
            val midOutRms = renderTailRms(engine, 24_000)
            val (midOutDb, _) = layerTrimAndFinalDb(engine, trackId)
            assertTrue("Expected ~ -6 dB at 500ms of fade-out, got $midOutDb dB", midOutDb in -7.5..-4.5)
            assertTrue("Expected midpoint fade-out rms ($midOutRms) < full rms ($fullInRms)", midOutRms < fullInRms)

            val postOutFadeRms = renderTailRms(engine, 24_000)
            val (fullOutDb, _) = layerTrimAndFinalDb(engine, trackId)
            assertTrue("Expected -96 dB after 1000ms fade-out, got $fullOutDb dB", fullOutDb <= -95.9)
            val fullOutRms = renderTailRms(engine, 2_400)
            assertTrue("Expected near-silent output after fade-out (tail=$postOutFadeRms), got rms=$fullOutRms", fullOutRms <= 0.0005)

            // Freeze on pause & reverse mid-ramp without jumping.
            assertEquals(AudioEngine.STATUS_OK, engine.setLayerGain(trackId, 0.0, fadeMs = 1000))
            renderTailRms(engine, 12_000)
            val (pausedStartDb, _) = layerTrimAndFinalDb(engine, trackId)
            assertTrue("Expected partial fade-in in -15..-9 dB at 250ms, got $pausedStartDb dB", pausedStartDb in -15.0..-9.0)

            assertEquals(AudioEngine.STATUS_OK, engine.pause())
            renderTailRms(engine, 24_000)
            val (whilePausedDb, _) = layerTrimAndFinalDb(engine, trackId)
            assertEquals("Paused transport must freeze active layer fade", pausedStartDb, whilePausedDb, 0.01)

            assertEquals(AudioEngine.STATUS_OK, engine.resume())
            assertEquals(AudioEngine.STATUS_OK, engine.setLayerGain(trackId, -96.0, fadeMs = 1000))
            renderTailRms(engine, 48_000)
            val (reversedDb, _) = layerTrimAndFinalDb(engine, trackId)
            assertTrue("Expected -96 dB after interrupted reverse fade, got $reversedDb dB", reversedDb <= -95.9)
        } finally {
            engine.close()
        }
    }
}
