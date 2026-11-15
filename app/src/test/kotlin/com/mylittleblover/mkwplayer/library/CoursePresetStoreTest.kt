package com.mylittleblover.mkwplayer.library

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoursePresetStoreTest {
    @Test
    fun crudDuplicateAndForCoursePersistAtomically() {
        val file = Files.createTempDirectory("mkw-presets").resolve("course-presets.json").toFile()
        val store = CoursePresetStore(file)

        val first = store.create(
            courseCode = "FirstCircuit_00",
            name = "Lap 3 Sprint",
            steps = listOf(FlowStep("Pre_Short"), FlowStep("Main", loopCount = 2, exitFrame = 48_000L)),
            outroNumber = 1,
            fadeOutMs = 0,
            outroToNextPre = true,
            nowEpochMs = 1000L,
        ).getOrThrow()
        assertEquals(1, store.read().getOrThrow().size)
        assertTrue(PresetRefs.isUser(first.id))
        assertEquals("FirstCircuit", first.courseCode)
        assertEquals(listOf("FirstCircuit"), store.forCourse("FirstCircuit").getOrThrow().map { it.courseCode })

        val dup = store.duplicate(first.id, "Sprint Copy", nowEpochMs = 2000L).getOrThrow()
        assertEquals("Sprint Copy", dup.name)
        assertEquals(2, store.read().getOrThrow().size)
        assertNotNull(store.find(dup.id).getOrThrow())

        store.delete(first.id).getOrThrow()
        assertNull(store.find(first.id).getOrThrow())
        assertEquals(1, CoursePresetStore(file).read().getOrThrow().size)
    }

    @Test
    fun corruptFileIsNotOverwrittenOnWrite() {
        val file = Files.createTempDirectory("mkw-presets-corrupt").resolve("course-presets.json").toFile()
        file.writeText("{corrupt json")
        val store = CoursePresetStore(file)
        assertTrue(store.create("FirstCircuit", "Test", listOf(FlowStep("Main"))).isFailure)
        assertEquals("{corrupt json", file.readText())
    }
}
