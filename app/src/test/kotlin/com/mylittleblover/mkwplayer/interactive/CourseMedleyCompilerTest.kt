package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.graph.GraphProbe
import com.mylittleblover.mkwplayer.player.LinearQueue
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CourseMedleyCompilerTest {
    private fun catalog(): CourseMedleyCatalog.Catalog =
        CourseMedleyCatalog.parse(File("src/main/assets/course_medley_catalog.json").readText())

    private fun identities(): InteractiveAudioIdentities =
        InteractiveAudioIdentities.parse(File("src/main/assets/interactive_audio_identities.json").readText())

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
            buf.putShort(base, 1) // DSP-ADPCM
            buf.putInt(base + 4, sampleRate)
            buf.putInt(base + 12, frames)
            buf.putInt(base + 60, -1) // no loop (NO_LOOP sentinel at loopLast)
            buf.putInt(base + 64, 0)
        }
        return bytes
    }

    @Test
    fun catalogCoversAll31MedleysAndEveryStemResolvesInIdentities() {
        val cat = catalog()
        val ids = identities()
        assertEquals(31, cat.medleys.size)
        assertEquals(2, cat.forCourse("DonkeyKong").size)
        assertEquals(1, cat.forCourse("Rainbow").size)
        assertEquals(1, cat.forCourse("Lava").size)

        for (medley in cat.medleys) {
            assertTrue(medley.clips.isNotEmpty(), "medley ${medley.id} has no clips")
            assertTrue(medley.totalFrames > 0L, "medley ${medley.id} totalFrames <= 0")
            assertTrue(medley.durationMs > 0L, "medley ${medley.id} durationMs <= 0")
            for (stem in medley.uniqueStems) {
                val digest = ids.digestForFileName("$stem.bwav")
                assertNotNull(digest, "stem '$stem.bwav' in medley '${medley.id}' missing from identities")
            }
        }
    }

    @Test
    fun compileGraphProducesValidProbeablePacketsForAll31Medleys() {
        val cat = catalog()
        for (medley in cat.medleys) {
            val maxRequiredFrames = medley.clips.maxOf { (it.sourceOffset + it.sourceLength).toInt() } + 1000
            val bytesByStem = medley.uniqueStems.associateWith { syntheticBwav(frames = maxRequiredFrames) }
            val compiled = CourseMedleyCompiler.compileGraph(medley, cat.sampleRate, bytesByStem)
            assertEquals(medley.uniqueStems.size, compiled.assets.size)
            assertTrue(compiled.totalFrames >= medley.totalFrames)
            val probed = GraphProbe.inspect(compiled.packet)
            assertTrue(probed is GraphProbe.Result.Ok, "GraphProbe failed for ${medley.id}: $probed")
        }
    }

    @Test
    fun resolveMedleyRequiresAllStemFilesOnDisk() {
        val cat = catalog()
        val ids = identities()
        val first = cat.medleys.first()
        val tmpDir = kotlin.io.path.createTempDirectory("medley-test-").toFile()
        try {
            val audioDir = File(tmpDir, "audio").apply { mkdirs() }
            assertNull(CourseMedleyCompiler.resolveMedley(first, cat.sampleRate, ids, tmpDir))
            for (stem in first.uniqueStems) {
                val sha = ids.digestForFileName("$stem.bwav")!!
                File(audioDir, sha).writeBytes(byteArrayOf(1, 2, 3))
            }
            val resolved = CourseMedleyCompiler.resolveMedley(first, cat.sampleRate, ids, tmpDir)
            assertNotNull(resolved)
            assertEquals(first.uniqueStems.toSet(), resolved.stemDigests.keys)
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    @Test
    fun linearQueueSupportsNextAndPreviousAcrossMedleys() {
        val cat = catalog()
        val ids = identities()
        val tmpDir = kotlin.io.path.createTempDirectory("medley-queue-test-").toFile()
        try {
            val audioDir = File(tmpDir, "audio").apply { mkdirs() }
            val entries = cat.medleys.take(3).map { medley ->
                for (stem in medley.uniqueStems) {
                    val sha = ids.digestForFileName("$stem.bwav")!!
                    val file = File(audioDir, sha)
                    if (!file.exists()) file.writeBytes(byteArrayOf(1))
                }
                val resolved = CourseMedleyCompiler.resolveMedley(medley, cat.sampleRate, ids, tmpDir)!!
                LinearQueue.Entry(
                    key = "medley:${medley.id}",
                    title = medley.courseCode,
                    subtitle = "Complete suite",
                    durationMs = medley.durationMs,
                    courseMedley = resolved,
                )
            }
            val queue = LinearQueue()
            val start = queue.startSession(entries, startIndex = 0)
            assertEquals("medley:${cat.medleys[0].id}", start?.key)
            val next = queue.next() as LinearQueue.StepResult.Play
            assertEquals("medley:${cat.medleys[1].id}", next.entry.key)
            val prev = queue.previous(restartThresholdMs = 3000L, elapsedMs = 500L) as LinearQueue.StepResult.Play
            assertEquals("medley:${cat.medleys[0].id}", prev.entry.key)
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    @Test
    fun strictTransitionRulesAllowOnlyInteractiveFadesAndTrackEndFadeout() {
        val cat = catalog()
        assertEquals(31, cat.medleys.size)
        val nonZeroFades = mutableListOf<String>()
        for (medley in cat.medleys) {
            assertEquals(
                medley.clips.maxOf { it.start + it.sourceLength },
                medley.totalFrames,
                "totalFrames mismatch in ${medley.id}",
            )
            for (clip in medley.clips) {
                assertTrue(clip.start >= 0L, "negative start in ${medley.id}: ${clip.stem}")
                assertTrue(clip.sourceOffset >= 0L, "negative sourceOffset in ${medley.id}: ${clip.stem}")
                assertTrue(clip.sourceLength > 0L, "non-positive sourceLength in ${medley.id}: ${clip.stem}")
                assertTrue(clip.fadeIn >= 0L && clip.fadeOut >= 0L, "negative fade in ${medley.id}: ${clip.stem}")
                if (clip.fadeIn != 0L || clip.fadeOut != 0L) {
                    nonZeroFades += "${medley.courseCode}:${clip.stem}:${clip.fadeIn}:${clip.fadeOut}"
                }
            }
        }
        assertEquals(
            listOf(
                "Cinema:BGM_Stage_Cinema:0:48000",
                "Cinema:BGM_Stage_Cinema_Pattern2:48000:0",
                "Rainbow:BGM_Rainbow_Area3:0:48000",
                "Rainbow:BGM_Rainbow_Final_Pre:48000:0",
                "Rainbow:BGM_Rainbow_Final:0:480000",
            ),
            nonZeroFades,
        )
    }
}
