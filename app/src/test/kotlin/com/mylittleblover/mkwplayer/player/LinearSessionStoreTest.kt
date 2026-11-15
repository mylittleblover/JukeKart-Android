package com.mylittleblover.mkwplayer.player

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinearSessionStoreTest {
    @Test
    fun coldRestartSnapshotRoundTripsAllQueueStructures() {
        val file = Files.createTempDirectory("mkw-session").resolve("linear-session.json").toFile()
        val store = LinearSessionStore(file)

        val e1 = LinearSessionStore.SavedEntry(
            key = "pl:1:0:FirstCircuit:user:p1",
            title = "Mario Bros. Circuit",
            subtitle = "My Mix · Custom",
            sourceType = LinearSessionStore.SourceType.PRESET,
            courseCode = "FirstCircuit",
            presetId = "user:p1",
            nativeTransitionOverride = true,
            durationMs = 123_456L,
            artworkName = "t_001.webp",
        )
        val e2 = LinearSessionStore.SavedEntry(
            key = "extra:credits",
            title = "Staff Credits",
            subtitle = "Other",
            sourceType = LinearSessionStore.SourceType.EXTRA,
            assetSha256 = "abc123",
            loop = true,
            loopStart = 100L,
            loopEnd = 900L,
        )
        val snap = LinearSessionStore.Snapshot(
            frozenBase = listOf(e1, e2),
            current = e1,
            currentBaseIndex = 0,
            pastHistory = listOf(LinearSessionStore.IndexedEntry(e2, 1)),
            forwardHistory = emptyList(),
            tempQueue = listOf(e2),
            upcomingBase = listOf(LinearSessionStore.IndexedEntry(e2, 1)),
            repeatMode = LinearQueue.RepeatMode.LIST,
            shuffle = false,
            nativeTransitionDefault = true,
            contextKind = LinearSessionStore.ContextKind.PLAYLIST,
            contextId = "pl-1",
            contextTitle = "My Mix",
            positionFrames = 96_000L,
            totalFrames = 480_000L,
            sampleRate = 48_000,
            packFingerprints = listOf("mkw-all:1"),
            savedAtEpochMs = 9999L,
        )

        store.write(snap).getOrThrow()
        val restored = LinearSessionStore(file).read().getOrThrow()
        assertNotNull(restored)
        assertEquals(snap, restored)

        store.clear().getOrThrow()
        assertNull(store.read().getOrThrow())
    }

    @Test
    fun corruptSessionFileReturnsFailureWithoutThrowing() {
        val file = Files.createTempDirectory("mkw-session-bad").resolve("linear-session.json").toFile()
        file.writeText("{not valid")
        val store = LinearSessionStore(file)
        assertTrue(store.read().isFailure)
        assertEquals("{not valid", file.readText())
    }
}
