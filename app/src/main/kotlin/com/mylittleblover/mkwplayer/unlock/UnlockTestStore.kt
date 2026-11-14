package com.mylittleblover.mkwplayer.unlock

import com.mylittleblover.mkwplayer.library.MetadataFile
import java.io.File
import org.json.JSONObject

/** Stores only the test override. Natural unlock progress belongs to UnlockStore. */
internal class UnlockTestStore(private val file: File) {
    fun read(): Result<UnlockTestMode> = synchronized(MetadataFile.lock(file)) {
        runCatching {
            if (!file.exists()) return@runCatching UnlockTestMode.NORMAL
            check(file.isFile) { "Unlock test setting is not a file" }
            val root = JSONObject(file.readText())
            require(root.get("schemaVersion") == 1) { "Unsupported unlock test setting schema" }
            val mode = root.get("mode")
            require(mode is String) { "Invalid unlock test setting mode" }
            UnlockTestMode.valueOf(mode)
        }
    }

    fun write(mode: UnlockTestMode): Result<Unit> = synchronized(MetadataFile.lock(file)) {
        runCatching {
            // Preserve an unreadable record instead of silently discarding the user's data.
            read().getOrThrow()
            val root = JSONObject().put("schemaVersion", 1).put("mode", mode.name)
            MetadataFile.write(file, root.toString())
        }
    }
}
