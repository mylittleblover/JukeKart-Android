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

    @Test fun entryConfigsAndClearPresetReferencesPersistPerSlot() {
        val file = Files.createTempDirectory("mkw-playlists-cfg").resolve("playlists.json").toFile()
        val store = PlaylistStore(file)
        val plId = store.create("Suite Mix").getOrThrow().single().id
        store.addCourse(plId, "FirstCircuit").getOrThrow()
        store.addCourse(plId, "FirstCircuit").getOrThrow()
        store.setNativeTransitionDefault(plId, true).getOrThrow()
        store.setEntryPreset(plId, 0, "user:custom-1").getOrThrow()
        store.setEntryPreset(plId, 1, PresetRefs.SYSTEM_KNOCKOUT).getOrThrow()
        store.setEntryNativeTransition(plId, 1, false).getOrThrow()

        val loaded = PlaylistStore(file).read().getOrThrow().single()
        assertTrue(loaded.nativeTransitionDefault)
        assertEquals("user:custom-1", loaded.configAt(0).presetId)
        assertNull(loaded.configAt(0).nativeTransitionEnabled)
        assertEquals(PresetRefs.SYSTEM_KNOCKOUT, loaded.configAt(1).presetId)
        assertEquals(false, loaded.configAt(1).nativeTransitionEnabled)

        val refs = store.playlistsReferencingPreset("user:custom-1").getOrThrow()
        assertEquals(listOf(plId), refs.map { it.id })

        store.clearPresetReferences("user:custom-1").getOrThrow()
        val afterClear = PlaylistStore(file).read().getOrThrow().single()
        assertNull(afterClear.configAt(0).presetId)
        assertEquals(PresetRefs.SYSTEM_KNOCKOUT, afterClear.configAt(1).presetId)
    }
}
