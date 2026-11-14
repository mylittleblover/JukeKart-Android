package com.mylittleblover.mkwplayer.library

import java.nio.file.Files
import kotlin.test.*

class RecentCourseStoreTest {
    @Test fun recentCoursesPersistNewestFirstWithoutDuplicateEntries() {
        val file = Files.createTempDirectory("mkw-recent").resolve("recent.json").toFile()
        val store = RecentCourseStore(file)
        store.record("FirstCircuit").getOrThrow()
        store.record("SnowMountain").getOrThrow()
        store.record("FirstCircuit").getOrThrow()
        assertEquals(listOf("FirstCircuit", "SnowMountain"), RecentCourseStore(file).read().getOrThrow())
    }
    @Test fun malformedHistoryIsNotOverwritten() {
        val file = Files.createTempDirectory("mkw-recent-corrupt").resolve("recent.json").toFile()
        file.writeText("{invalid")
        assertTrue(RecentCourseStore(file).record("FirstCircuit").isFailure)
        assertEquals("{invalid", file.readText())
    }
}
