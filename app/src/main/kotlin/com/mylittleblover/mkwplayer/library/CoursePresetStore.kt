package com.mylittleblover.mkwplayer.library

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

internal object PresetRefs {
    const val SYSTEM_DEFAULT = "sys:default"
    const val SYSTEM_KNOCKOUT = "sys:knockout"
    const val USER_PREFIX = "user:"

    fun isUser(ref: String?): Boolean = ref != null && ref.startsWith(USER_PREFIX)
}

internal data class FlowStep(
    val roleKey: String,
    val loopCount: Int = 0,
    val exitFrame: Long? = null,
) {
    init {
        require(roleKey.isNotBlank()) { "FlowStep roleKey must not be blank" }
        require(loopCount in 0..8) { "FlowStep loopCount must be 0..8, got $loopCount" }
        require(exitFrame == null || exitFrame >= 0L) { "FlowStep exitFrame must be >= 0, got $exitFrame" }
    }
}

internal data class CoursePreset(
    val id: String,
    val courseCode: String,
    val name: String,
    val steps: List<FlowStep>,
    val outroNumber: Int? = null,
    val fadeOutMs: Int = 0,
    val outroToNextPre: Boolean = true,
    val updatedAtEpochMs: Long = 0L,
) {
    init {
        require(id.isNotBlank()) { "CoursePreset id must not be blank" }
        require(courseCode.isNotBlank()) { "CoursePreset courseCode must not be blank" }
        require(name.trim().isNotEmpty()) { "CoursePreset name must not be blank" }
        require(steps.isNotEmpty()) { "CoursePreset must contain at least one step" }
        require(fadeOutMs in 0..10_000) { "CoursePreset fadeOutMs must be 0..10000" }
    }
}

/**
 * Persists user-authored per-course segmented flow presets (#48-#50).
 *
 * Uses atomic metadata writes with strict schema validation so corrupt files are
 * never silently overwritten.
 */
internal class CoursePresetStore(private val file: File) {

    fun read(): Result<List<CoursePreset>> = synchronized(MetadataFile.lock(file)) {
        runCatching { decode() }
    }

    fun forCourse(courseCode: String): Result<List<CoursePreset>> = synchronized(MetadataFile.lock(file)) {
        runCatching {
            val logical = courseCode.removeSuffix("_00")
            decode().filter { it.courseCode.equals(logical, ignoreCase = true) }
        }
    }

    fun find(id: String): Result<CoursePreset?> = synchronized(MetadataFile.lock(file)) {
        runCatching { decode().firstOrNull { it.id == id } }
    }

    fun save(preset: CoursePreset, nowEpochMs: Long = System.currentTimeMillis()): Result<List<CoursePreset>> =
        update { current ->
            val trimmedName = preset.name.trim()
            require(trimmedName.isNotEmpty() && trimmedName.length <= 120) {
                "Preset name must be 1..120 characters"
            }
            val normalizedId = if (preset.id.startsWith(PresetRefs.USER_PREFIX)) {
                preset.id
            } else {
                "${PresetRefs.USER_PREFIX}${preset.id}"
            }
            val clean = preset.copy(
                id = normalizedId,
                courseCode = preset.courseCode.removeSuffix("_00"),
                name = trimmedName,
                updatedAtEpochMs = if (preset.updatedAtEpochMs > 0L) preset.updatedAtEpochMs else nowEpochMs,
            )
            val idx = current.indexOfFirst { it.id == clean.id }
            if (idx >= 0) {
                current.toMutableList().apply { set(idx, clean) }
            } else {
                current + clean
            }
        }

    fun create(
        courseCode: String,
        name: String,
        steps: List<FlowStep>,
        outroNumber: Int? = null,
        fadeOutMs: Int = 0,
        outroToNextPre: Boolean = true,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): Result<CoursePreset> {
        val created = CoursePreset(
            id = "${PresetRefs.USER_PREFIX}${UUID.randomUUID()}",
            courseCode = courseCode.removeSuffix("_00"),
            name = name.trim(),
            steps = steps,
            outroNumber = outroNumber,
            fadeOutMs = fadeOutMs,
            outroToNextPre = outroToNextPre,
            updatedAtEpochMs = nowEpochMs,
        )
        return save(created, nowEpochMs).map { list ->
            list.first { it.id == created.id }
        }
    }

    fun rename(id: String, name: String, nowEpochMs: Long = System.currentTimeMillis()): Result<List<CoursePreset>> =
        update { current ->
            val trimmed = name.trim()
            require(trimmed.isNotEmpty() && trimmed.length <= 120)
            current.map { preset ->
                if (preset.id == id) preset.copy(name = trimmed, updatedAtEpochMs = nowEpochMs) else preset
            }
        }

    fun duplicate(id: String, newName: String, nowEpochMs: Long = System.currentTimeMillis()): Result<CoursePreset> {
        val source = find(id).getOrThrow() ?: error("Preset '$id' not found")
        return create(
            courseCode = source.courseCode,
            name = newName,
            steps = source.steps,
            outroNumber = source.outroNumber,
            fadeOutMs = source.fadeOutMs,
            outroToNextPre = source.outroToNextPre,
            nowEpochMs = nowEpochMs,
        )
    }

    fun delete(id: String): Result<List<CoursePreset>> = update { current ->
        current.filterNot { it.id == id }
    }

    fun replaceAll(presets: List<CoursePreset>): Result<List<CoursePreset>> = update {
        normalizeImportedPresets(presets)
    }

    fun mergeAll(incoming: List<CoursePreset>): Result<List<CoursePreset>> = update { current ->
        val byId = LinkedHashMap<String, CoursePreset>()
        current.forEach { byId[it.id] = it }
        normalizeImportedPresets(incoming).forEach { preset ->
            val existing = byId[preset.id]
            if (existing == null || preset.updatedAtEpochMs >= existing.updatedAtEpochMs) {
                byId[preset.id] = preset
            }
        }
        byId.values.toList()
    }

    private fun normalizeImportedPresets(presets: List<CoursePreset>): List<CoursePreset> {
        val out = LinkedHashMap<String, CoursePreset>()
        presets.forEach { raw ->
            val trimmedName = raw.name.trim().take(120)
            val course = raw.courseCode.trim().removeSuffix("_00")
            if (trimmedName.isEmpty() || course.isEmpty() || raw.steps.isEmpty()) return@forEach
            val normId = if (raw.id.startsWith(PresetRefs.USER_PREFIX)) {
                raw.id
            } else {
                "${PresetRefs.USER_PREFIX}${raw.id.ifBlank { UUID.randomUUID().toString() }}"
            }
            out[normId] = raw.copy(
                id = normId,
                courseCode = course,
                name = trimmedName,
                fadeOutMs = raw.fadeOutMs.coerceIn(0, 10_000),
            )
        }
        return out.values.toList()
    }

    private fun update(transform: (List<CoursePreset>) -> List<CoursePreset>): Result<List<CoursePreset>> =
        synchronized(MetadataFile.lock(file)) {
            runCatching {
                val updated = transform(decode())
                val json = JSONObject()
                    .put("schemaVersion", SCHEMA_VERSION)
                    .put("presets", JSONArray(updated.map(::encodePreset)))
                MetadataFile.write(file, json.toString())
                updated
            }
        }

    private fun decode(): List<CoursePreset> {
        if (!file.exists()) return emptyList()
        val root = JSONObject(file.readText())
        require(root.getInt("schemaVersion") == SCHEMA_VERSION) { "Unsupported preset schemaVersion" }
        val arr = root.getJSONArray("presets")
        return (0 until arr.length()).map { idx -> decodePreset(arr.getJSONObject(idx)) }
    }

    private fun encodePreset(preset: CoursePreset): JSONObject {
        val stepsArr = JSONArray()
        preset.steps.forEach { step ->
            val obj = JSONObject()
                .put("roleKey", step.roleKey)
                .put("loopCount", step.loopCount)
            if (step.exitFrame != null) obj.put("exitFrame", step.exitFrame)
            stepsArr.put(obj)
        }
        return JSONObject()
            .put("id", preset.id)
            .put("courseCode", preset.courseCode)
            .put("name", preset.name)
            .put("steps", stepsArr)
            .put("outroNumber", preset.outroNumber ?: JSONObject.NULL)
            .put("fadeOutMs", preset.fadeOutMs)
            .put("outroToNextPre", preset.outroToNextPre)
            .put("updatedAtEpochMs", preset.updatedAtEpochMs)
    }

    private fun decodePreset(obj: JSONObject): CoursePreset {
        val stepsArr = obj.getJSONArray("steps")
        val steps = (0 until stepsArr.length()).map { idx ->
            val s = stepsArr.getJSONObject(idx)
            FlowStep(
                roleKey = s.getString("roleKey"),
                loopCount = s.optInt("loopCount", 0),
                exitFrame = if (s.has("exitFrame") && !s.isNull("exitFrame")) s.getLong("exitFrame") else null,
            )
        }
        return CoursePreset(
            id = obj.getString("id"),
            courseCode = obj.getString("courseCode"),
            name = obj.getString("name"),
            steps = steps,
            outroNumber = if (obj.has("outroNumber") && !obj.isNull("outroNumber")) obj.getInt("outroNumber") else null,
            fadeOutMs = obj.optInt("fadeOutMs", 0),
            outroToNextPre = obj.optBoolean("outroToNextPre", true),
            updatedAtEpochMs = obj.optLong("updatedAtEpochMs", 0L),
        )
    }

    companion object {
        private const val SCHEMA_VERSION = 1
    }
}
