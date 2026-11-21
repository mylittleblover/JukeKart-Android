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

    @Test
    fun inspectExtraSupportsTwoStateAndInteractiveTitle() {
        val packDir = java.nio.file.Files.createTempDirectory("mkw-extra-pack").toFile()
        val audioDir = File(packDir, "audio").apply { mkdirs() }
        val mainBytes = syntheticBwav(frames = 240_000, sampleRate = 48_000, loopStart = 0, loopEnd = 240_000)
        val fastBytes = syntheticBwav(frames = 192_000, sampleRate = 48_000, loopStart = 0, loopEnd = 192_000)
        File(audioDir, "sha-main").writeBytes(mainBytes)
        File(audioDir, "sha-fast").writeBytes(fastBytes)
        val mainRef = com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.AssetRef("BGM_Battle_Main", "sha-main", 240_000L, 48_000, 2, true, 0L, 240_000L)
        val fastRef = com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.AssetRef("BGM_Battle_Fast", "sha-fast", 192_000L, 48_000, 2, true, 0L, 192_000L)
        val battleTrack = com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.TwoStateTrack(
            id = "battle_test",
            ostIndex = null,
            artwork = "",
            names = mapOf("en" to "Battle Test"),
            mainStem = mainRef.stem,
            fastStem = fastRef.stem,
            searchAliases = emptyList(),
            main = mainRef,
            fast = fastRef,
            durationMs = 5000L,
        )
        val catalog = com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.Catalog(battleTracks = listOf(battleTrack))
        val inv = CourseFlowRecipeBuilder.inspectExtra(
            extraId = "extra:battle_test",
            extraCatalog = catalog,
            extraMedleyCatalog = ExtraMedleyCatalog.Catalog(),
            packDir = packDir,
            hasFanfareAsset = false,
        )
        assertNotNull(inv)
        assertTrue(inv.parts.any { it.roleKey == "Main" })
        assertTrue(inv.parts.any { it.roleKey == "Fast" })

        val resolver = object : CourseFlowRecipeBuilder.SourceResolver {
            override fun findByFileName(fileName: String): CourseFlowRecipeBuilder.AssetHeader? = when (fileName) {
                "${mainRef.stem}.bwav" -> CourseFlowRecipeBuilder.AssetHeader(mainRef.stem, mainRef.sha256, mainRef.frames, mainRef.sampleRate, 0L, mainRef.frames)
                "${fastRef.stem}.bwav" -> CourseFlowRecipeBuilder.AssetHeader(fastRef.stem, fastRef.sha256, fastRef.frames, fastRef.sampleRate, 0L, fastRef.frames)
                else -> null
            }
        }
        val preset = CoursePreset(
            id = "user:extra-1",
            courseCode = "extra:battle_test",
            name = "Battle Mix",
            steps = listOf(FlowStep("Main", loopCount = 1), FlowStep("Fast", loopCount = 1)),
            outroNumber = null,
            fadeOutMs = 2000,
        )
        val compiled = CourseFlowRecipeBuilder.compileUserPreset(preset, inv, minf(), resolver)
        assertNotNull(compiled)
        val exported = org.json.JSONObject(CourseFlowRecipeBuilder.exportPresetConfigJson(preset, compiled))
        assertEquals("battle_test", exported.getString("targetId"))
        assertEquals("extra:battle_test", exported.getJSONObject("medley").getString("courseCode"))
    }

    @Test
    fun finalLapFanfareInsertsBeforeFastAndFollowsInteractiveOverlapRule() {
        val sampleRate = 48_000
        val resolver = object : CourseFlowRecipeBuilder.SourceResolver {
            override fun findByFileName(fileName: String): CourseFlowRecipeBuilder.AssetHeader? = when {
                fileName == FinalLapFanfareCompiler.ASSET ->
                    CourseFlowRecipeBuilder.AssetHeader(
                        stem = "BGM_Seq_FinalLapFF",
                        digest = FinalLapFanfareCompiler.SHA256,
                        frames = 268_759L,
                        sampleRate = sampleRate,
                        loopStart = null,
                        loopEnd = null,
                    )
                fileName == "BGM_Stage_FirstCircuit.bwav" || fileName == "BGM_Stage_FirstCircuit_Fast.bwav" ->
                    CourseFlowRecipeBuilder.AssetHeader(
                        stem = fileName.removeSuffix(".bwav"),
                        digest = "sha-${fileName.removeSuffix(".bwav")}",
                        frames = 400_000L,
                        sampleRate = sampleRate,
                        loopStart = 20_000L,
                        loopEnd = 380_000L,
                    )
                else -> null
            }
        }
        val minfCatalog = minf()
        val inv = CourseFlowRecipeBuilder.inspectCourse("FirstCircuit", minfCatalog, medleyCatalog(), resolver)
        assertTrue(inv.hasFanfareAsset)
        assertTrue(inv.describeTransitionEvent("Main", "Fast").contains("FinalLapFF"))

        val mainPart = inv.part("Main")!!
        val beatExit = inv.part("Main")!!.exitCandidates.first().frame
        val preset = CoursePreset(
            id = "user:fc-fast",
            courseCode = "FirstCircuit",
            name = "Main To Fast",
            steps = listOf(
                FlowStep("Main", loopCount = 0, exitFrame = beatExit),
                FlowStep("Fast", loopCount = 0),
            ),
            outroNumber = null,
            fadeOutMs = 2000,
        )
        val compiled = CourseFlowRecipeBuilder.compileUserPreset(preset, inv, minfCatalog, resolver)
        assertNotNull(compiled)
        val ffClip = compiled.medley.clips.firstOrNull { it.stem == "BGM_Seq_FinalLapFF" }
        val fastClip = compiled.medley.clips.firstOrNull { it.stem == "BGM_Stage_FirstCircuit_Fast" }
        assertNotNull(ffClip)
        assertNotNull(fastClip)
        assertEquals(beatExit, ffClip.start)
        assertEquals(268_759L, ffClip.sourceLength)
        assertEquals(beatExit + 188_880L, fastClip.start)
        assertEquals(0L, fastClip.sourceOffset)
        assertTrue(mainPart.beats.isNotEmpty())
    }

    @Test
    fun extraTrackEditedExitPointChangesSeamPreviewWhenLoopingOrBeforeOutro() {
        val sampleRate = 48_000
        val packDir = java.nio.file.Files.createTempDirectory("mkw-extra-seam").toFile()
        val audioDir = File(packDir, "audio").apply { mkdirs() }
        val mainFrames = 960_000L // 20s
        val fastFrames = 480_000L // 10s
        val outroFrames = 384_000L // 8s
        val harmFrames = 480_000L // 10s
        File(audioDir, "sha-main").writeBytes(syntheticBwav(mainFrames.toInt(), sampleRate, loopStart = 0, loopEnd = mainFrames.toInt()))
        File(audioDir, "sha-fast").writeBytes(syntheticBwav(fastFrames.toInt(), sampleRate, loopStart = 0, loopEnd = fastFrames.toInt()))
        File(audioDir, "sha-outro").writeBytes(syntheticBwav(outroFrames.toInt(), sampleRate))
        File(audioDir, "sha-harm").writeBytes(syntheticBwav(harmFrames.toInt(), sampleRate))

        val mainRef = com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.AssetRef("BGM_Battle_Main", "sha-main", mainFrames, sampleRate, 2, true, 0L, mainFrames)
        val fastRef = com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.AssetRef("BGM_Battle_Fast", "sha-fast", fastFrames, sampleRate, 2, true, 0L, fastFrames)
        val harmRef = com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.AssetRef("BGM_Title_Harm", "sha-harm", harmFrames, sampleRate, 2, false, null, null)
        val outroRef = com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.AssetRef("BGM_Title_Outro01", "sha-outro", outroFrames, sampleRate, 2, false, null, null)

        val battleTrack = com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.TwoStateTrack(
            id = "battle_seam",
            ostIndex = null,
            artwork = "",
            names = mapOf("en" to "Battle Seam"),
            mainStem = mainRef.stem,
            fastStem = fastRef.stem,
            searchAliases = emptyList(),
            main = mainRef,
            fast = fastRef,
            durationMs = 20_000L,
        )
        val titleTrack = com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.OtherMusicItem.InteractiveTitle(
            id = "title_seam",
            ostIndex = null,
            artwork = "",
            names = mapOf("en" to "Title Seam"),
            searchAliases = emptyList(),
            durationMs = 20_000L,
            harmonica = harmRef,
            main = mainRef,
            brassCut = fastRef,
            outros = listOf(
                com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.TitleOutroSpec(
                    index = 1,
                    cueName = "Outro01",
                    mainExitFrame = 720_000L,
                    brassCutExitFrame = 240_000L,
                    asset = outroRef,
                ),
            ),
        )
        val catalog = com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.Catalog(
            battleTracks = listOf(battleTrack),
            otherMusic = listOf(titleTrack),
        )
        val resolver = object : CourseFlowRecipeBuilder.SourceResolver {
            override fun findByFileName(fileName: String): CourseFlowRecipeBuilder.AssetHeader? = when (fileName) {
                "${mainRef.stem}.bwav" -> CourseFlowRecipeBuilder.AssetHeader(mainRef.stem, mainRef.sha256, mainRef.frames, sampleRate, 0L, mainFrames)
                "${fastRef.stem}.bwav" -> CourseFlowRecipeBuilder.AssetHeader(fastRef.stem, fastRef.sha256, fastRef.frames, sampleRate, 0L, fastFrames)
                "${harmRef.stem}.bwav" -> CourseFlowRecipeBuilder.AssetHeader(harmRef.stem, harmRef.sha256, harmRef.frames, sampleRate, null, null)
                "${outroRef.stem}.bwav" -> CourseFlowRecipeBuilder.AssetHeader(outroRef.stem, outroRef.sha256, outroRef.frames, sampleRate, null, null)
                else -> null
            }
        }

        // 1. Two-state extra track with loopCount = 1: editing Main exitFrame must move the seam preview slice.
        val battleInv = CourseFlowRecipeBuilder.inspectExtra("extra:battle_seam", catalog, ExtraMedleyCatalog.Catalog(), packDir)!!
        val earlyExit = 288_000L // 6s
        val lateExit = 624_000L // 13s
        val seamEarly = CourseFlowRecipeBuilder.compileSeamPreview(
            preset = CoursePreset("user:b1", "extra:battle_seam", "B1", listOf(FlowStep("Main", loopCount = 1, exitFrame = earlyExit), FlowStep("Fast", loopCount = 0))),
            seamIndex = 0,
            inventory = battleInv,
            minf = minf(),
            resolver = resolver,
        )!!
        val seamLate = CourseFlowRecipeBuilder.compileSeamPreview(
            preset = CoursePreset("user:b1", "extra:battle_seam", "B1", listOf(FlowStep("Main", loopCount = 1, exitFrame = lateExit), FlowStep("Fast", loopCount = 0))),
            seamIndex = 0,
            inventory = battleInv,
            minf = minf(),
            resolver = resolver,
        )!!
        val earlyMainClip = seamEarly.medley.clips.last { it.stem == mainRef.stem }
        val lateMainClip = seamLate.medley.clips.last { it.stem == mainRef.stem }
        assertEquals(earlyExit, earlyMainClip.sourceOffset + earlyMainClip.sourceLength)
        assertEquals(lateExit, lateMainClip.sourceOffset + lateMainClip.sourceLength)

        // 2. InteractiveTitle before Outro 01: editing Main exitFrame must not snap back to fixed mainExitFrame (720_000).
        val titleInv = CourseFlowRecipeBuilder.inspectExtra("extra:title_seam", catalog, ExtraMedleyCatalog.Catalog(), packDir)!!
        val titleSeamCustom = CourseFlowRecipeBuilder.compileSeamPreview(
            preset = CoursePreset(
                id = "user:t1",
                courseCode = "extra:title_seam",
                name = "T1",
                steps = listOf(FlowStep("Main", loopCount = 1, exitFrame = 384_000L)),
                outroNumber = 1,
            ),
            seamIndex = 0,
            inventory = titleInv,
            minf = minf(),
            resolver = resolver,
        )!!
        val titleMainClip = titleSeamCustom.medley.clips.last { it.stem == mainRef.stem }
        assertEquals(384_000L, titleMainClip.sourceOffset + titleMainClip.sourceLength)
    }
}
