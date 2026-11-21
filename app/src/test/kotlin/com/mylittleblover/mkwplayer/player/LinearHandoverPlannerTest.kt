package com.mylittleblover.mkwplayer.player

import com.mylittleblover.mkwplayer.graph.GraphProbe
import com.mylittleblover.mkwplayer.interactive.CourseMedleyCatalog
import com.mylittleblover.mkwplayer.interactive.CourseMedleyChainCompiler
import com.mylittleblover.mkwplayer.interactive.CourseMedleyCompiler
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinearHandoverPlannerTest {
    private fun syntheticBwav(frames: Int, sampleRate: Int = 48_000, channels: Int = 2): ByteArray {
        val bytes = ByteArray(16 + channels * 76)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        bytes[0] = 'B'.code.toByte()
        bytes[1] = 'W'.code.toByte()
        bytes[2] = 'A'.code.toByte()
        bytes[3] = 'V'.code.toByte()
        buf.putShort(4, 0xFEFF.toShort())
        buf.putShort(14, channels.toShort())
        for (ch in 0 until channels) {
            val base = 16 + ch * 76
            buf.putShort(base, 1)
            buf.putInt(base + 4, sampleRate)
            buf.putInt(base + 12, frames)
            buf.putInt(base + 60, -1)
            buf.putInt(base + 64, 0)
        }
        return bytes
    }

    private fun medleyEntry(
        key: String,
        course: String,
        firstStem: String,
        handoverFrame: Long?,
        nativeEnabled: Boolean,
        incomingPhase: Long = 24_000L,
    ): LinearQueue.Entry {
        val clips = listOf(
            CourseMedleyCatalog.MedleyClip(firstStem, 0L, 0L, 120_000L),
            CourseMedleyCatalog.MedleyClip("BGM_Stage_${course}_Outro01", 120_000L, 0L, 120_000L),
        )
        val medley = CourseMedleyCatalog.Medley(
            id = key,
            index = 1,
            courseCode = course,
            variant = "standard",
            totalFrames = 240_000L,
            durationMs = 5_000L,
            clips = clips,
            uniqueStems = clips.map { it.stem }.distinct(),
        )
        val resolved = CourseMedleyCompiler.ResolvedMedley(
            medley = medley,
            sampleRate = 48_000,
            stemDigests = medley.uniqueStems.associateWith { "sha-$it" },
            outroHandoverFrame = handoverFrame,
            incomingPrePhase = incomingPhase,
        )
        return LinearQueue.Entry(
            key = key,
            title = course,
            courseCode = course,
            nativeTransitionEnabled = nativeEnabled,
            courseMedley = resolved,
        )
    }

    @Test
    fun chainsOnlyVerifiedOutroToNextPreWhenBothSidesEnableNativeTransition() {
        val e1 = medleyEntry("e1", "FirstCircuit", "BGM_Stage_FirstCircuit_Pre_Short", 210_000L, true)
        val e2 = medleyEntry("e2", "CrownCity", "BGM_Stage_CrownCity_Pre", 200_000L, true)
        val e3NoNative = medleyEntry("e3", "SnowMountain", "BGM_Stage_SnowMountain_Pre", 200_000L, false)
        val e4FullOnly = medleyEntry("e4", "MarioCircuit", "BGM_Stage_MarioCircuit_Full", 200_000L, true)

        assertTrue(LinearHandoverPlanner.canHandoverNatively(e1, e2))
        assertFalse(LinearHandoverPlanner.canHandoverNatively(e2, e3NoNative))
        assertFalse(LinearHandoverPlanner.canHandoverNatively(e1, e4FullOnly))

        val chained = LinearHandoverPlanner.collectChainEntries(e1, listOf(e2, e3NoNative, e4FullOnly))
        assertEquals(listOf("e1", "e2"), chained.map { it.key })

        val bytes = (e1.courseMedley!!.medley.uniqueStems + e2.courseMedley!!.medley.uniqueStems)
            .associateWith { syntheticBwav(300_000) }
        val compiled = CourseMedleyChainCompiler.compileChain(
            listOf("e1" to e1.courseMedley!!, "e2" to e2.courseMedley!!),
            bytes,
        )
        assertEquals(mapOf(100 to 101), compiled.autoAdvance)
        assertEquals(2, compiled.windows.size)
        assertEquals(0L, compiled.windows[0].originTransportFrame)
        assertEquals(210_000L, compiled.windows[1].originTransportFrame)
        assertTrue(GraphProbe.inspect(compiled.packet) is GraphProbe.Result.Ok)
    }
}
