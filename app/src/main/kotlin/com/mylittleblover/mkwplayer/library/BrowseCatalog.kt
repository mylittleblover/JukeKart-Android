package com.mylittleblover.mkwplayer.library

import com.mylittleblover.mkwplayer.routes.LocalizedNames
import com.mylittleblover.mkwplayer.routes.OfficialRoute
import com.mylittleblover.mkwplayer.routes.OfficialRouteCatalog
import com.mylittleblover.mkwplayer.routes.OfficialRouteKind
import java.util.Collections

/** Music discovery uses game identities and published ordering, never labels or map proximity. */
internal class BrowseCatalog private constructor(
    private val official: OfficialRouteCatalog,
    private val names: LocalizedNames,
    private val orderedCourses: List<String>,
) {
    fun courses(specialUnlocked: Boolean): List<String> {
        val allowed = official.stageCodes(specialUnlocked)
        return Collections.unmodifiableList(orderedCourses.filter { it in allowed })
    }
    fun searchCourses(query: String, specialUnlocked: Boolean): List<String> =
        Collections.unmodifiableList(courses(specialUnlocked).filter { names.matches(it, query) })
    fun displayName(id: String, languageTag: String): String? = names.name(id, languageTag)
    fun route(id: String, specialUnlocked: Boolean): OfficialRoute? = official.visible(specialUnlocked).firstOrNull { it.id == id }
    fun routes(kind: OfficialRouteKind, specialUnlocked: Boolean, query: String = ""): List<OfficialRoute> =
        Collections.unmodifiableList(official.visible(specialUnlocked).filter { it.kind == kind && names.matches(it.id, query) })

    companion object {
        fun create(official: OfficialRouteCatalog, names: LocalizedNames, anchors: MapAnchors.Catalog): BrowseCatalog? = runCatching {
            val ordered = official.routes.filter { it.kind == OfficialRouteKind.GRAND_PRIX }
                .flatMap { it.stops }.map { it.stageCode }.distinct()
            val all = official.routes.flatMap { it.stops }.map { it.stageCode }.distinct()
            require(ordered.toSet() == all.toSet())
            require(ordered.all { anchors.stageTolerant(it) != null && names.name(it, "en") != null })
            BrowseCatalog(official, names, Collections.unmodifiableList(ordered.toList()))
        }.getOrNull()
    }
}
