package com.mylittleblover.mkwplayer.unlock

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.*

class UnlockTestStoreTest {
    @Test fun absentSettingIsNormalAndEveryModeSurvivesRestart() = withFile { file ->
        assertEquals(UnlockTestMode.NORMAL, UnlockTestStore(file).read().getOrThrow())
        UnlockTestMode.entries.forEach { mode ->
            UnlockTestStore(file).write(mode).getOrThrow()
            assertEquals(mode, UnlockTestStore(file).read().getOrThrow())
        }
        assertEquals(listOf(file.name), file.parentFile.list()!!.toList())
    }
    @Test fun corruptOrUnknownRecordsAreReportedAndPreserved() = withFile { file ->
        listOf("broken", "", "{}", "{\"schemaVersion\":2,\"mode\":\"NORMAL\"}",
            "{\"schemaVersion\":1,\"mode\":\"OTHER\"}",
            "{\"schemaVersion\":\"1\",\"mode\":\"NORMAL\"}").forEach { original ->
            file.writeText(original)
            val store = UnlockTestStore(file)
            assertTrue(store.read().isFailure, original)
            assertTrue(store.write(UnlockTestMode.FORCE_UNLOCKED).isFailure, original)
            assertEquals(original, file.readText())
        }
    }
    @Test fun settingsDoNotTouchTheNaturalProgressFile() = withFile { file ->
        val progress = File(file.parentFile, "unlock.json").apply { writeText("natural-progress") }
        UnlockTestStore(file).write(UnlockTestMode.FORCE_UNLOCKED).getOrThrow()
        UnlockTestStore(file).write(UnlockTestMode.NORMAL).getOrThrow()
        assertEquals("natural-progress", progress.readText())
    }
    private fun withFile(block: (File) -> Unit) {
        val root = createTempDirectory("unlock-test-").toFile()
        try { block(File(root, "unlock-test.json")) } finally { root.deleteRecursively() }
    }
}
