package com.mylittleblover.mkwplayer.ui.onboarding

import android.content.Context
import com.mylittleblover.mkwplayer.library.MetadataFile
import org.json.JSONObject
import java.io.File

/**
 * Tracks whether the first-launch welcome & setup flow has been completed or dismissed.
 * Stored atomically via [MetadataFile] in `filesDir/onboarding.json`.
 */
internal object OnboardingStore {
    private const val FILE_NAME = "onboarding.json"

    fun file(context: Context): File = File(context.applicationContext.filesDir, FILE_NAME)

    fun isCompleted(context: Context): Boolean = isCompleted(file(context))

    fun isCompleted(file: File): Boolean = synchronized(MetadataFile.lock(file)) {
        if (!file.isFile) return@synchronized false
        runCatching {
            val root = JSONObject(file.readText())
            root.optInt("schemaVersion", 1) == 1 && root.optBoolean("completed", false)
        }.getOrDefault(false)
    }

    fun markCompleted(context: Context) {
        markCompleted(file(context))
    }

    fun markCompleted(file: File) {
        synchronized(MetadataFile.lock(file)) {
            runCatching {
                MetadataFile.write(
                    file,
                    JSONObject()
                        .put("schemaVersion", 1)
                        .put("completed", true)
                        .toString(),
                )
            }
        }
    }
}
