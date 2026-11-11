package com.mylittleblover.mkwplayer.interactive

import org.json.JSONObject

/** Raw records stay distinct by archive/offset/hash, even when names repeat. */
internal class MinfCatalog(val records: List<Record>) {
    data class Beat(val frame: Long, val label: Int)
    data class Event(val name: String, val frames: List<Long>)
    data class Record(
        val stage: String, val role: String, val name: String,
        val phase: Long, val rate: Int, val bpm: Double,
        val loopStart: Long, val loopEnd: Long,
        val archive: String, val offset: Long, val hash: String,
        val beats: List<Beat>, val events: List<Event>,
    ) {
        val loopCandidates: List<Long> get() = beats.filter {
            it.label == 1 && it.frame in loopStart..loopEnd
        }.map { it.frame }
        fun event(name: String): List<Long>? = events.singleOrNull { it.name == name }?.frames
    }

    /** No fuzzy name fallback or arbitrary selection among duplicate records. */
    fun part(stage: String, role: String): Record? {
        val base = stage.substringBefore("_Pattern")
        val pattern = stage.substringAfter("_Pattern", "")
        val roles = when (role) {
            "Pre" -> setOf("Pre", "Pre_2")
            "Pre_Short" -> setOf("Pre_Short", "Pre_Short_2")
            "Bridge" -> setOf("Bridge", "Bridge_Short_1")
            else -> setOf(role)
        }
        return records.filter { r ->
            r.stage == base && r.role in roles &&
                if (pattern.isEmpty()) !r.name.contains("_Pattern") else r.name.endsWith("_Pattern$pattern")
        }.singleOrNull()
    }

    companion object {
        fun parse(text: String): MinfCatalog {
            val root = JSONObject(text)
            require(root.getString("version") == "2026-11-12")
            val rows = root.getJSONArray("records")
            return MinfCatalog((0 until rows.length()).map { i ->
                val r = rows.getJSONObject(i)
                fun number(key: String) = r.getLong(key).also { require(it >= 0) }
                val beats = r.getJSONArray("beats")
                val events = r.getJSONArray("events")
                Record(r.getString("stage"), r.getString("role"), r.getString("name"),
                    number("phase"), r.getInt("rate").also { require(it == 48000) },
                    r.getDouble("bpm").also { require(it.isFinite() && it > 0) },
                    number("loopStart"), number("loopEnd"), r.getString("archive"),
                    number("offset"), r.getString("minfHash"),
                    (0 until beats.length()).map { j -> beats.getJSONObject(j).let {
                        Beat(it.getLong("frame"), it.getInt("label"))
                    } },
                    (0 until events.length()).map { j -> events.getJSONObject(j).let {
                        val frames = it.getJSONArray("frames")
                        Event(it.getString("name"), (0 until frames.length()).map { k -> frames.getLong(k) })
                    } },
                )
            })
        }
    }
}
