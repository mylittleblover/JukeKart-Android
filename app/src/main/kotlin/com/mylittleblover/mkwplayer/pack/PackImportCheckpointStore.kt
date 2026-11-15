package com.mylittleblover.mkwplayer.pack

import com.mylittleblover.mkwplayer.library.MetadataFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Atomic persistence for interrupted `.mkwpack` import checkpoints and staging directories (#95, #97).
 *
 * Explicit user cancellation deletes both checkpoint JSON and `.staging/<id>` cleanly (#91, #93),
 * whereas an unexpected process termination preserves verified staged files so the next launch
 * can resume after verifying file identity (`sourceByteSize` + `archiveFingerprint`).
 */
internal class PackImportCheckpointStore(private val libraryRoot: File) {

    private val file: File = File(libraryRoot, ".import-checkpoint.json")
    private val stagingRoot: File = File(libraryRoot, ".staging")

    fun stagingDir(checkpointId: String): File = File(stagingRoot, checkpointId)

    fun read(): Result<ImportCheckpoint?> = synchronized(MetadataFile.lock(file)) {
        runCatching {
            if (!file.isFile) return@runCatching null
            decode(JSONObject(file.readText()))
        }
    }

    fun write(checkpoint: ImportCheckpoint): Result<Unit> = synchronized(MetadataFile.lock(file)) {
        runCatching {
            MetadataFile.write(file, encode(checkpoint).toString())
        }
    }

    fun clearAll() {
        synchronized(MetadataFile.lock(file)) {
            if (file.exists()) file.delete()
            stagingRoot.deleteRecursively()
        }
    }

    fun clearExcept(keepCheckpointId: String) {
        stagingRoot.listFiles()?.forEach { child ->
            if (child.name != keepCheckpointId) child.deleteRecursively()
        }
    }

    private fun encode(cp: ImportCheckpoint): JSONObject = JSONObject()
        .put("schemaVersion", 1)
        .put("checkpointId", cp.checkpointId)
        .put("sourceUri", cp.sourceUri ?: JSONObject.NULL)
        .put("displayName", cp.displayName)
        .put("sourceByteSize", cp.sourceByteSize)
        .put("archiveFingerprint", cp.archiveFingerprint)
        .put("packId", cp.packId)
        .put("packVersion", cp.packVersion)
        .put("packName", cp.packName)
        .put("totalMembers", cp.totalMembers)
        .put("completedMembers", JSONArray(cp.completedMembers.sorted()))
        .put("bytesProcessed", cp.bytesProcessed)
        .put("verified", cp.verified)
        .put("committing", cp.committing)
        .put("updatedAtEpochMs", cp.updatedAtEpochMs)

    private fun decode(root: JSONObject): ImportCheckpoint {
        val completedArr = root.optJSONArray("completedMembers") ?: JSONArray()
        val completed = LinkedHashSet<String>(completedArr.length())
        for (i in 0 until completedArr.length()) {
            val item = completedArr.optString(i, "").trim()
            if (item.isNotEmpty()) completed += item
        }
        return ImportCheckpoint(
            checkpointId = root.getString("checkpointId"),
            sourceUri = if (root.isNull("sourceUri")) null else root.optString("sourceUri", "").takeIf { it.isNotEmpty() },
            displayName = root.getString("displayName"),
            sourceByteSize = root.getLong("sourceByteSize"),
            archiveFingerprint = root.getString("archiveFingerprint"),
            packId = root.getString("packId"),
            packVersion = root.getInt("packVersion"),
            packName = root.optString("packName", ""),
            totalMembers = root.optInt("totalMembers", 0),
            completedMembers = completed,
            bytesProcessed = root.optLong("bytesProcessed", 0L),
            verified = root.optBoolean("verified", false),
            committing = root.optBoolean("committing", false),
            updatedAtEpochMs = root.optLong("updatedAtEpochMs", 0L),
        )
    }
}
