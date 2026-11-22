package com.mylittleblover.mkwplayer.interactive

import android.content.Context
import org.json.JSONObject
import java.util.Locale

/**
 * Extensible complete-medley catalog for all non-course interactive music:
 * - Interactive Title Theme (`targetType = "title"`, `targetId = "title_theme"`)
 * - Two-state retro/SFC courses (`targetType = "two_state_course"`, `targetId = "sfc_mario_circuit"`, ...)
 * - Two-state battle tracks (`targetType = "battle"`, `targetId = "battle_smk"`, ...)
 * - Other multi-state/special interactive cues (`targetType = "other"`, `targetId = ...`)
 *
 * Uses the exact same clip schema as [CourseMedleyCatalog] so [CourseMedleyCompiler] can
 * compile and play any appended medley entry seamlessly.
 */
internal object ExtraMedleyCatalog {

    enum class TargetType {
        TITLE,
        TWO_STATE_COURSE,
        BATTLE,
        OTHER,
        ;

        companion object {
            fun parse(raw: String): TargetType = when (raw.trim().lowercase(Locale.ROOT)) {
                "title", "interactive_title" -> TITLE
                "two_state_course", "sfc", "sfc_course" -> TWO_STATE_COURSE
                "battle", "battle_track" -> BATTLE
                else -> OTHER
            }
        }
    }

    data class ExtraMedleyEntry(
        val targetType: TargetType,
        val targetId: String,
        val titleOverrides: Map<String, String>,
        val artwork: String?,
        val medley: CourseMedleyCatalog.Medley,
    ) {
        fun localizedLabel(languageTag: String, fallback: String): String {
            if (titleOverrides.isEmpty()) return fallback
            val lang = com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.normalizeLanguage(languageTag)
            return titleOverrides[lang] ?: titleOverrides["en"] ?: titleOverrides.values.firstOrNull() ?: fallback
        }
    }

    data class Catalog(
        val schemaVersion: Int = 1,
        val sampleRate: Int = 48_000,
        val entries: List<ExtraMedleyEntry> = emptyList(),
    ) {
        fun forTarget(targetId: String): List<ExtraMedleyEntry> =
            entries.filter { it.targetId.equals(targetId, ignoreCase = true) }

        fun byMedleyId(medleyId: String): ExtraMedleyEntry? =
            entries.firstOrNull { it.medley.id == medleyId }
    }

    fun load(context: Context): Catalog = runCatching {
        context.assets.open("extra_medley_catalog.json").bufferedReader().use { parse(it.readText()) }
    }.getOrElse { Catalog() }

    fun parse(text: String): Catalog {
        val root = JSONObject(text)
        val version = root.optInt("schemaVersion", 1)
        val sampleRate = root.optInt("sampleRate", 48_000)
        val arr = root.optJSONArray("medleys") ?: return Catalog(version, sampleRate, emptyList())
        val entries = (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            val index = obj.optInt("index", i + 1)
            val targetId = obj.getString("targetId")
            val targetType = TargetType.parse(obj.optString("targetType", "other"))
            val variant = obj.optString("variant", "medley")
            val id = obj.optString("id").ifBlank {
                "extra_%02d_%s_%s".format(Locale.ROOT, index, targetId, variant)
            }
            val clipsJson = obj.getJSONArray("clips")
            val clips = (0 until clipsJson.length()).map { cIdx ->
                CourseMedleyCatalog.parseClip(clipsJson.getJSONObject(cIdx))
            }
            val namesObj = obj.optJSONObject("names")
            val names = buildMap {
                if (namesObj != null) {
                    val keys = namesObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        put(k, namesObj.optString(k))
                    }
                }
            }
            val medley = CourseMedleyCatalog.Medley(
                id = id,
                index = index,
                courseCode = obj.optString("courseCode").ifBlank { targetId },
                variant = variant,
                totalFrames = obj.getLong("totalFrames"),
                durationMs = obj.getLong("durationMs"),
                clips = clips,
                uniqueStems = clips.map { it.stem }.distinct(),
            )
            ExtraMedleyEntry(
                targetType = targetType,
                targetId = targetId,
                titleOverrides = names,
                artwork = obj.optString("artwork").takeIf { it.isNotBlank() && !obj.isNull("artwork") },
                medley = medley,
            )
        }
        return Catalog(schemaVersion = version, sampleRate = sampleRate, entries = entries)
    }
}
