package com.mylittleblover.mkwplayer.routes

import java.util.Collections
import java.util.Locale
import java.text.Normalizer
import com.mylittleblover.mkwplayer.i18n.languageKey
import org.json.JSONObject

internal enum class OfficialRouteKind { GRAND_PRIX, RALLY }

/** An occurrence, rather than a unique stage: revisiting a course never merges two stops. */
internal data class OfficialStop(val id: String, val ordinal: Int, val stageCode: String)

/** Read-only published sequence. No editing, copying or custom-road operations are exposed. */
internal class OfficialRoute internal constructor(
    val id: String,
    val kind: OfficialRouteKind,
    names: Map<String, String>,
    stops: List<OfficialStop>,
    val requiresSpecialUnlock: Boolean,
    val sourceUrl: String,
) {
    private val names = names.toMap()
    val stops: List<OfficialStop> = Collections.unmodifiableList(stops.toList())
    val start: OfficialStop get() = stops.first()
    val end: OfficialStop get() = stops.last()
    fun displayName(languageTag: String): String = translatedName(names, languageTag)
}

internal class OfficialRouteCatalog private constructor(
    routes: List<OfficialRoute>,
    val sourceRevision: Int,
    val sourceSnapshotSha256: String,
) {
    val routes: List<OfficialRoute> = Collections.unmodifiableList(routes.toList())
    fun route(id: String): OfficialRoute? = routes.firstOrNull { it.id == id }
    fun visible(specialUnlocked: Boolean?): List<OfficialRoute> =
        Collections.unmodifiableList(routes.filter { !it.requiresSpecialUnlock || specialUnlocked == true })
    fun stageCodes(specialUnlocked: Boolean?): Set<String> =
        visible(specialUnlocked).flatMap { it.stops }.map { it.stageCode }.toSet()

    companion object {
        fun parse(text: String, knownStageCodes: Set<String>): OfficialRouteCatalog? = runCatching {
            val root = JSONObject(text)
            require(root.getInt("schemaVersion") == 1)
            require(root.getString("geometry") == "ordered_stops_only")
            val revision = root.getInt("sourceRevision").also { require(it > 0) }
            val digest = root.getString("sourceSnapshotSha256").also { require(it.matches(SOURCE_DIGEST)) }
            require(isSourceUrl(root.getString("sourceUrl")))
            val known = knownStageCodes.map { it.removeSuffix("_00") }.toSet()
            val rows = root.getJSONArray("routes")
            require(rows.length() > 0)
            val routes = (0 until rows.length()).map { index ->
                val row = rows.getJSONObject(index)
                val id = row.getString("id").also { require(it.matches(STABLE_ID)) }
                val kind = when (row.getString("kind")) {
                    "grand_prix" -> OfficialRouteKind.GRAND_PRIX
                    "rally" -> OfficialRouteKind.RALLY
                    else -> error("Unknown official route kind")
                }
                val names = readNames(row.getJSONObject("names"))
                val unlock = row.get("requiresSpecialUnlock").also { require(it is Boolean) } as Boolean
                require(id != "special_cup" || unlock)
                val url = row.getString("sourceUrl").also { require(isSourceUrl(it)) }
                val values = row.getJSONArray("stops")
                require(values.length() == if (kind == OfficialRouteKind.GRAND_PRIX) 4 else 6)
                val stops = (0 until values.length()).map { stopIndex ->
                    val stop = values.getJSONObject(stopIndex)
                    val ordinal = stop.getInt("ordinal").also { require(it == stopIndex + 1) }
                    val code = stop.getString("stage").removeSuffix("_00").also { require(it in known) }
                    OfficialStop("$id:$ordinal", ordinal, code)
                }
                OfficialRoute(id, kind, names, stops, unlock, url)
            }
            require(routes.map { it.id }.distinct().size == routes.size)
            OfficialRouteCatalog(routes, revision, digest)
        }.getOrNull()
    }
}

/** Name identities are independent of language, music keys and ordering. */
internal class LocalizedNames private constructor(private val entries: Map<String, Map<String, String>>) {
    fun name(id: String, languageTag: String): String? =
        entries[id.removeSuffix("_00")]?.let { translatedName(it, languageTag) }

    fun matches(id: String, query: String): Boolean {
        val canonical = id.removeSuffix("_00")
        val names = entries[canonical] ?: return false
        if (query.isBlank()) return true
        val key = searchKey(query)
        if (key.isEmpty()) return false
        return (names.values + canonical).any { searchKey(it).contains(key) }
    }

    companion object {
        fun parse(text: String): LocalizedNames? = runCatching {
            val root = JSONObject(text)
            require(root.getInt("schemaVersion") == 1)
            val rows = root.getJSONArray("entries")
            val entries = linkedMapOf<String, Map<String, String>>()
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                val id = row.getString("id").also { require(it.matches(STABLE_ID)) }
                require(id !in entries)
                require(isSourceUrl(row.getString("sourceUrl")))
                require(row.getString("sourceSha256").matches(SOURCE_DIGEST))
                entries[id] = readNames(row.getJSONObject("names"))
            }
            require(entries.isNotEmpty())
            LocalizedNames(entries.toMap())
        }.getOrNull()
    }
}


private fun translatedName(names: Map<String, String>, languageTag: String): String {
    val exact = Locale.forLanguageTag(languageTag.replace('_', '-')).toLanguageTag()
    return names[exact] ?: names[languageKey(languageTag)] ?: names.getValue("en")
}

private fun readNames(value: JSONObject): Map<String, String> {
    val result = value.keys().asSequence().associateWith { value.getString(it).trim() }.filterValues { it.isNotEmpty() }
    require(!result["en"].isNullOrBlank())
    return result
}

private fun isSourceUrl(value: String): Boolean = value.startsWith("https://www.mariowiki.com/")
private val SOURCE_DIGEST = Regex("[0-9a-f]{64}")
private val STABLE_ID = Regex("[A-Za-z][A-Za-z0-9_]*")

private fun searchKey(value: String): String {
    val normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
    return buildString {
        var offset = 0
        while (offset < normalized.length) {
            val codePoint = normalized.codePointAt(offset)
            if (Character.isLetterOrDigit(codePoint) || codePoint == 0x30FC) appendCodePoint(codePoint)
            offset += Character.charCount(codePoint)
        }
    }
}
