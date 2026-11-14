package com.mylittleblover.mkwplayer.library

import java.nio.file.Files
import kotlin.test.*

class PlaylistStoreTest {
    @Test fun duplicateOccurrencesPersistAndCanBeReorderedIndependently() {
        val file = Files.createTempDirectory("mkw-playlists").resolve("playlists.json").toFile()
        val store = PlaylistStore(file)
        val id = store.create("Race list").getOrThrow().single().id
        store.addCourse(id, "FirstCircuit").getOrThrow()
        store.addCourse(id, "SnowMountain").getOrThrow()
        store.addCourse(id, "FirstCircuit").getOrThrow()
        store.moveCourse(id, 2, 1).getOrThrow()
        store.removeCourse(id, 0).getOrThrow()
        store.rename(id, "Favorites").getOrThrow()
        assertEquals(UserPlaylist(id, "Favorites", listOf("FirstCircuit", "SnowMountain")), PlaylistStore(file).read().getOrThrow().single())
        assertTrue(store.delete(id).getOrThrow().isEmpty())
    }
    @Test fun corruptStorageIsNotOverwrittenOnCreate() {
        val file = Files.createTempDirectory("mkw-playlists-corrupt").resolve("playlists.json").toFile()
        file.writeText("{broken")
        val store = PlaylistStore(file)
        assertTrue(store.create("Anything").isFailure)
        assertEquals("{broken", file.readText())
    }
}
