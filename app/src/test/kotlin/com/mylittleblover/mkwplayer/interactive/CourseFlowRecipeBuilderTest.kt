package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.graph.GraphProbe
import com.mylittleblover.mkwplayer.library.CoursePreset
import com.mylittleblover.mkwplayer.library.FlowStep
import com.mylittleblover.mkwplayer.library.PresetRefs
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CourseFlowRecipeBuilderTest {
    private fun minf(): MinfCatalog =
        MinfCatalog.parse(File("src/main/assets/interactive_minf.json").readText())

    private fun medleyCatalog(): CourseMedleyCatalog.Catalog =
        CourseMedleyCatalog.parse(File("src/main/assets/course_medley_catalog.json").readText())

    private fun syntheticBwav(
        frames: Int,
        sampleRate: Int,
        channels: Int = 2,
        loopStart: Int? = null,
        loopEnd: Int? = null,
    ): ByteArray {
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
            if (loopStart != null && loopEnd != null) {
                buf.putInt(base + 56, 1)
                buf.putInt(base + 60, loopEnd - 1)
                buf.putInt(base + 64, loopStart)
            } else {
                buf.putInt(base + 60, -1)
                buf.putInt(base + 64, 0)
            }
        }
        return bytes
    }

    @Test
    fun compileUserPresetUsesMeasuredSampleRateAndVerifiedTransitions() {
        val sampleRate = 44_100
        val resolver = object : CourseFlowRecipeBuilder.SourceResolver {
            override fun findByFileName(fileName: String): CourseFlowRecipeBuilder.AssetHeader? = when {
                fileName == "BGM_Stage_FirstCircuit.bwav" ||
                    fileName.endsWith("_Pre_Short.bwav") || fileName.endsWith("_Pre.bwav") ||
                    fileName.endsWith("_Bridge.bwav") || fileName.endsWith("_Full.bwav") ||
                    fileName.endsWith("_Fast.bwav") || fileName.contains("_Outro") -> {
                    CourseFlowRecipeBuilder.AssetHeader(
                        stem = fileName.removeSuffix(".bwav"),
                        digest = "sha-${fileName.removeSuffix(".bwav")}",
                        frames = 200_000L,
                        sampleRate = sampleRate,
                        loopStart = 10_000L,
                        loopEnd = 150_000L,
                    )
                }
                else -> null
            }
        }

        val minfCatalog = minf()
        val inventory = CourseFlowRecipeBuilder.inspectCourse(
            courseCode = "FirstCircuit",
            minf = minfCatalog,
            medleyCatalog = medleyCatalog(),
            resolver = resolver,
        )
        assertEquals(sampleRate, inventory.sampleRate)
        assertTrue(inventory.parts.any { it.roleKey == "Pre_Short" })
        assertTrue(inventory.parts.any { it.roleKey == "Pre" })
        assertTrue(inventory.outros.any { it.supportsNativeHandover })

        val preset = CoursePreset(
            id = "user:fc-test",
            courseCode = "FirstCircuit",
            name = "Test Flow",
            steps = listOf(
                FlowStep("Pre_Short"),
                FlowStep("Pre"),
                FlowStep("Main", loopCount = 1),
            ),
            outroNumber = null,
            fadeOutMs = 2000,
        )
        val compiled = CourseFlowRecipeBuilder.compileUserPreset(preset, inventory, minfCatalog, resolver)
        assertNotNull(compiled)
        assertEquals(sampleRate, compiled.sampleRate)
        assertTrue(compiled.medley.clips.any { it.fadeOut == 2L * sampleRate })

        val bytesByStem = compiled.medley.uniqueStems.associateWith {
            syntheticBwav(frames = 250_000, sampleRate = sampleRate)
        }
        val packetResult = CourseMedleyCompiler.compileGraph(compiled.medley, compiled.sampleRate, bytesByStem)
        assertTrue(GraphProbe.inspect(packetResult.packet) is GraphProbe.Result.Ok)
    }

    @Test
    fun deletedUserPresetRefFallsBackToSystemDefault() {
        val resolver = object : CourseFlowRecipeBuilder.SourceResolver {
            override fun findByFileName(fileName: String): CourseFlowRecipeBuilder.AssetHeader =
                CourseFlowRecipeBuilder.AssetHeader(
                    stem = fileName.removeSuffix(".bwav"),
                    digest = "sha-${fileName.removeSuffix(".bwav")}",
                    frames = 2_000_000L,
                    sampleRate = 48_000,
                    loopStart = null,
                    loopEnd = null,
                )
        }
        val resolved = CourseFlowRecipeBuilder.resolvePresetRef(
            courseCode = "FirstCircuit",
            presetRef = "${PresetRefs.USER_PREFIX}deleted-id",
            userPresets = emptyList(),
            minf = minf(),
            medleyCatalog = medleyCatalog(),
            resolver = resolver,
        )
        assertNotNull(resolved)
        assertEquals("FirstCircuit", resolved.medley.courseCode)
    }
}
