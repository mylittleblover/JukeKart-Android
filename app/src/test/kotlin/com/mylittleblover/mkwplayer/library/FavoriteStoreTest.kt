package com.mylittleblover.mkwplayer.library

import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import kotlin.test.*

class FavoriteStoreTest {
    private fun directory() = createTempDirectory("mkw-favorites-").toFile()
    @Test fun favoritesSurviveANewStoreAndToggleWithoutDuplicateIds() {
        val root = directory()
        try {
            val file = File(root, "favorites.json")
            val store = FavoriteStore(file)
            assertEquals(emptySet(), store.read().getOrThrow())
            assertEquals(setOf("course:FirstCircuit"), store.toggle("course:FirstCircuit").getOrThrow())
            assertEquals(setOf("course:FirstCircuit"), FavoriteStore(file).read().getOrThrow())
            assertEquals(emptySet(), FavoriteStore(file).toggle("course:FirstCircuit").getOrThrow())
        } finally { root.deleteRecursively() }
    }
    @Test fun corruptedUserDataIsReportedAndNeverSilentlyOverwritten() {
        val root = directory()
        try {
            val file = File(root, "favorites.json").apply { writeText("corrupt user data") }
            val store = FavoriteStore(file)
            assertTrue(store.read().isFailure)
            assertTrue(store.toggle("course:FirstCircuit").isFailure)
            assertEquals("corrupt user data", file.readText())
        } finally { root.deleteRecursively() }
    }
    @Test fun concurrentTogglesAcrossStoreInstancesDoNotLoseAnOperation() {
        val root = directory()
        val executor = Executors.newFixedThreadPool(4)
        try {
            val file = File(root, "favorites.json")
            val stores = List(4) { FavoriteStore(file) }
            val tasks = (0 until 40).map { index -> executor.submit { stores[index % 4].toggle("course:FirstCircuit").getOrThrow() } }
            tasks.forEach { it.get(20, TimeUnit.SECONDS) }
            assertEquals(emptySet(), FavoriteStore(file).read().getOrThrow())
        } finally { executor.shutdownNow(); root.deleteRecursively() }
    }
    @Test fun invalidIdentityDoesNotCreateAUserRecord() {
        val root = directory()
        try {
            val file = File(root, "favorites.json")
            assertTrue(FavoriteStore(file).toggle(" ").isFailure)
            assertFalse(file.exists())
        } finally { root.deleteRecursively() }
    }
}
