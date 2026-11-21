package com.mylittleblover.mkwplayer.interactive

import android.content.Context
import com.mylittleblover.mkwplayer.graph.MusicGraphBuilder
import java.util.Locale
import org.json.JSONObject

/**
 * Bundled arrangement recipes for each course's Complete Medley ("完整组曲").
 *
 * Every clip plays at unity gain (`gainDb = 0.0`) inside a single linear graph segment,
 * reproducing the complete opening (`Pre` -> optional `Bridge` -> `Main`/patterns -> `Outro`)
 * measured against the reference soundtrack with closest-event MINF snapping where needed.
 */
internal object CourseMedleyCatalog {
    data class MedleyClip(
        val stem: String,
        val start: Long,
        val sourceOffset: Long,
        val sourceLength: Long,
        val fadeIn: Long = 0L,
        val fadeOut: Long = 0L,
        val volumePoints: List<MusicGraphBuilder.EnvelopePoint> = emptyList(),
    )

    data class Medley(
        val id: String,
        val index: Int,
        val courseCode: String,
        val variant: String,
        val totalFrames: Long,
        val durationMs: Long,
        val clips: List<MedleyClip>,
        val uniqueStems: List<String>,
    ) {
        val isKnockoutVariant: Boolean get() = variant.equals("knockout", ignoreCase = true)
    }

    data class Catalog(
        val schemaVersion: Int,
        val sampleRate: Int,
        val medleys: List<Medley>,
    ) {
        fun forCourse(code: String): List<Medley> {
            val logical = code.removeSuffix("_00")
            return medleys.filter { it.courseCode.equals(logical, ignoreCase = true) }
        }

        fun byId(id: String): Medley? = medleys.firstOrNull { it.id == id }
    }

    fun load(context: Context): Catalog =
        context.assets.open("course_medley_catalog.json").bufferedReader().use { parse(it.readText()) }

    fun parse(text: String): Catalog {
        val root = JSONObject(text)
        val version = root.getInt("schemaVersion")
        val sampleRate = root.optInt("sampleRate", 48_000)
        val arr = root.getJSONArray("medleys")
        val medleys = (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            val index = obj.getInt("index")
            val courseCode = obj.getString("courseCode")
            val variant = obj.optString("variant", "medley")
            val id = obj.optString("id").ifBlank {
                "%02d_%s_%s".format(Locale.ROOT, index, courseCode, variant)
            }
            val clipsJson = obj.getJSONArray("clips")
            val clips = (0 until clipsJson.length()).map { cIdx ->
                val c = clipsJson.getJSONObject(cIdx)
                MedleyClip(
                    stem = c.getString("stem"),
                    start = c.getLong("start"),
                    sourceOffset = c.getLong("sourceOffset"),
                    sourceLength = c.getLong("sourceLength"),
                    fadeIn = c.optLong("fadeIn", 0L),
                    fadeOut = c.optLong("fadeOut", 0L),
                )
            }
            val stems = clips.map { it.stem }.distinct()
            Medley(
                id = id,
                index = index,
                courseCode = courseCode,
                variant = variant,
                totalFrames = obj.getLong("totalFrames"),
                durationMs = obj.getLong("durationMs"),
                clips = clips,
                uniqueStems = stems,
            )
        }
        return Catalog(schemaVersion = version, sampleRate = sampleRate, medleys = medleys)
    }
}
