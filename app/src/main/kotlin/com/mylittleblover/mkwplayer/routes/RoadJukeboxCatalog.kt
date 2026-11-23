package com.mylittleblover.mkwplayer.routes

import com.mylittleblover.mkwplayer.library.StageConnections
import com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * Resolves directed road edges to automatically identified Jukebox music candidates (#70-#73, #82-#83).
 *
 * Strictly enforces:
 * - Directed connections only (`StageConnections.connects(from, to)`, #74-#75).
 * - If a road has an established candidate pool and any track in that pool is missing or
 *   unreadable, launching any route containing that road is forbidden (#82).
 * - Automatic Jukebox recognition only (`sha256` / `trackKey` / `bwavStem` / `jukeboxCode`, #83).
 * - Missing candidate pool allows "自定义替代" on custom routes without mutating official pools (#73).
 */
internal object RoadJukeboxCatalog {

    data class EdgeKey(
        val from: String,
        val to: String,
    ) {
        val canonicalFrom: String = from.trim().removeSuffix("_00")
        val canonicalTo: String = to.trim().removeSuffix("_00")
        fun normalized(): EdgeKey = EdgeKey(canonicalFrom, canonicalTo)
    }

    sealed interface MergeResult {
        data class Replaced(val pool: CandidatePool, val changed: Boolean) : MergeResult
        data class IgnoredOlderVersion(val pool: CandidatePool) : MergeResult
        data class RejectedConflict(val reason: String) : MergeResult
    }

    data class CandidatePool(
        val schemaVersion: Int = 1,
        val dataVersion: Int = 0,
        val entries: Map<EdgeKey, List<String>> = emptyMap(),
    ) {
        fun candidatesFor(from: String, to: String): List<String>? =
            entries[EdgeKey(from.trim().removeSuffix("_00"), to.trim().removeSuffix("_00"))]

        /** Higher valid dataVersion replaces the older pool wholesale; never takes union (#86). */
        fun mergeReplace(incoming: CandidatePool): MergeResult {
            if (incoming.schemaVersion != 1 || incoming.dataVersion < 0) {
                return MergeResult.RejectedConflict("Invalid road candidate pool version")
            }
            return when {
                incoming.dataVersion > dataVersion -> MergeResult.Replaced(incoming, changed = true)
                incoming.dataVersion == dataVersion -> {
                    if (incoming.entries == entries) {
                        MergeResult.Replaced(this, changed = false)
                    } else {
                        MergeResult.RejectedConflict(
                            "Road candidate pool version ${incoming.dataVersion} conflicts with existing data (#86).",
                        )
                    }
                }
                else -> MergeResult.IgnoredOlderVersion(this)
            }
        }

        fun toJsonString(): String {
            val arr = JSONArray()
            entries.entries.sortedWith(compareBy({ it.key.from }, { it.key.to })).forEach { (key, cands) ->
                arr.put(
                    JSONObject()
                        .put("from", key.from)
                        .put("to", key.to)
                        .put("candidates", JSONArray(cands)),
                )
            }
            return JSONObject()
                .put("schemaVersion", schemaVersion)
                .put("dataVersion", dataVersion)
                .put("roads", arr)
                .toString()
        }

        companion object {
            fun parse(text: String): CandidatePool? = runCatching {
                val root = JSONObject(text)
                val schemaVersion = root.getInt("schemaVersion")
                require(schemaVersion == 1)
                val dataVersion = root.optInt("dataVersion", 0)
                require(dataVersion >= 0)
                val roads = root.optJSONArray("roads") ?: JSONArray()
                val map = linkedMapOf<EdgeKey, List<String>>()
                for (i in 0 until roads.length()) {
                    val obj = roads.getJSONObject(i)
                    val from = obj.getString("from").trim().removeSuffix("_00")
                    val to = obj.getString("to").trim().removeSuffix("_00")
                    require(from.isNotBlank() && to.isNotBlank() && from != to)
                    val candArr = obj.getJSONArray("candidates")
                    val cands = (0 until candArr.length())
                        .map { candArr.getString(it).trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()
                    val key = EdgeKey(from, to)
                    require(!map.containsKey(key)) { "Duplicate candidate entry for ${key.from}->${key.to}" }
                    if (cands.isNotEmpty()) {
                        map[key] = cands
                    }
                }
                CandidatePool(schemaVersion, dataVersion, map)
            }.getOrNull()
        }
    }

    /** Playback preset selection strategy across stops in a route playlist. */
    enum class RouteSuiteMode {
        GAME_SUITE,
        COMPLETE_SUITE;

        companion object {
            fun fromStored(raw: String?): RouteSuiteMode = when (raw?.trim()?.uppercase(Locale.ROOT)) {
                COMPLETE_SUITE.name -> COMPLETE_SUITE
                else -> GAME_SUITE
            }
        }
    }

    /**
     * Resolves the target built-in suite variant (`"a"`, `"a_alt"`, `"b"`, `"c"`, `"d"`) for stop
     * [stopIndex] of [stopCount] under [suiteMode].
     */
    fun resolveSuiteVariantForStop(
        officialKind: OfficialRouteKind?,
        suiteMode: RouteSuiteMode,
        stopIndex: Int,
        stopCount: Int,
        stageCode: String,
    ): String {
        if (suiteMode == RouteSuiteMode.COMPLETE_SUITE) return "a"
        val canonical = stageCode.trim().removeSuffix("_00")
        return when (officialKind) {
            OfficialRouteKind.RALLY -> when {
                stopIndex == 0 -> "b"
                stopCount > 1 && stopIndex == stopCount - 1 -> "d"
                else -> "a"
            }
            OfficialRouteKind.GRAND_PRIX, null -> when {
                stopIndex == 0 -> "c"
                stopCount > 1 &&
                    stopIndex == stopCount - 1 &&
                    canonical.equals("DonkeyKong", ignoreCase = true) -> "a_alt"
                else -> "a"
            }
        }
    }

    enum class RoadMusicRuleMode {
        AUTO,
        GRAND_PRIX,
        VS_DAY,
        VS_NIGHT,
        ;

        companion object {
            fun fromStored(raw: String?): RoadMusicRuleMode =
                entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: AUTO
        }
    }

    enum class EffectiveRuleMode {
        GRAND_PRIX,
        VS_DAY,
        VS_NIGHT,
        KNOCKOUT_DIRECT,
    }

    enum class CandidateTier {
        GRAND_PRIX,
        EXCLUSIVE_ORDER,
        THEMED_BOOSTED,
        MAIN_ORDER,
        NIGHT_ORDER,
    }

    /**
     * Bundled MarioWiki Route music ruleset (`assets/route_music_catalog.json`).
     *
     * Models:
     * - Grand Prix per-edge pools (23 Jukebox edges + `Central -> Rainbow` special approach)
     * - VS Race Daytime (Main order interlaced 4:1 with origin theme / course-exclusive order)
     * - VS Race Nighttime (independent Night order across all routes)
     */
    data class RouteMusicCatalog(
        val schemaVersion: Int = 1,
        val themesByStage: Map<String, String> = emptyMap(),
        val exclusiveOriginCourses: Set<String> = emptySet(),
        val specialEdges: Map<EdgeKey, SpecialEdgeRule> = emptyMap(),
        val gpEdges: Map<EdgeKey, List<String>> = emptyMap(),
        val tracks: List<TrackRule> = emptyList(),
    ) {
        data class SpecialEdgeRule(
            val from: String,
            val to: String,
            val stem: String,
            val title: String,
        )

        data class TrackRule(
            val jukeboxCode: String,
            val anyOriginDay: Boolean,
            val anyOriginNight: Boolean,
            val dayThemes: Set<String>,
            val dayCourses: Set<String>,
            val likelyDayThemes: Set<String>,
            val likelyDayCourses: Set<String>,
        )

        data class RankedCandidate(
            val jukeboxCode: String,
            val tier: CandidateTier,
            val weight: Int,
        )

        fun specialFor(from: String, to: String): SpecialEdgeRule? =
            specialEdges[EdgeKey(from.trim().removeSuffix("_00"), to.trim().removeSuffix("_00"))]

        fun resolveRuleCandidates(
            from: String,
            to: String,
            mode: EffectiveRuleMode,
        ): List<RankedCandidate> {
            val cleanFrom = from.trim().removeSuffix("_00")
            val cleanTo = to.trim().removeSuffix("_00")
            return when (mode) {
                EffectiveRuleMode.KNOCKOUT_DIRECT -> emptyList()
                EffectiveRuleMode.GRAND_PRIX -> {
                    val refs = gpEdges[EdgeKey(cleanFrom, cleanTo)].orEmpty()
                    refs.map { RankedCandidate(it, CandidateTier.GRAND_PRIX, weight = 1) }
                }
                EffectiveRuleMode.VS_DAY -> {
                    val originTheme = themesByStage[cleanFrom]
                    val ranked = mutableListOf<RankedCandidate>()
                    for (t in tracks) {
                        val isExclusive = (originTheme != null && originTheme in t.dayThemes) ||
                            (cleanFrom in t.dayCourses)
                        val isLikelyBoost = (originTheme != null && originTheme in t.likelyDayThemes) ||
                            (cleanFrom in t.likelyDayCourses)
                        when {
                            isExclusive -> ranked += RankedCandidate(t.jukeboxCode, CandidateTier.EXCLUSIVE_ORDER, weight = 4)
                            isLikelyBoost -> ranked += RankedCandidate(t.jukeboxCode, CandidateTier.THEMED_BOOSTED, weight = 4)
                            t.anyOriginDay -> ranked += RankedCandidate(t.jukeboxCode, CandidateTier.MAIN_ORDER, weight = 1)
                        }
                    }
                    ranked.sortedBy { it.tier.ordinal }
                }
                EffectiveRuleMode.VS_NIGHT -> {
                    tracks
                        .filter { it.anyOriginNight }
                        .map { RankedCandidate(it.jukeboxCode, CandidateTier.NIGHT_ORDER, weight = 1) }
                }
            }
        }

        companion object {
            fun parse(text: String): RouteMusicCatalog? = runCatching {
                val root = JSONObject(text)
                val schemaVersion = root.getInt("schemaVersion")
                require(schemaVersion == 1)
                val themesByStage = linkedMapOf<String, String>()
                val themesObj = root.optJSONObject("themes")
                if (themesObj != null) {
                    val keys = themesObj.keys()
                    while (keys.hasNext()) {
                        val themeName = keys.next()
                        val arr = themesObj.getJSONArray(themeName)
                        for (i in 0 until arr.length()) {
                            themesByStage[arr.getString(i).trim().removeSuffix("_00")] = themeName
                        }
                    }
                }
                val exclArr = root.optJSONArray("exclusiveOriginCourses") ?: JSONArray()
                val exclusiveCourses = (0 until exclArr.length())
                    .map { exclArr.getString(it).trim().removeSuffix("_00") }
                    .toSet()

                val specialMap = linkedMapOf<EdgeKey, SpecialEdgeRule>()
                val specialArr = root.optJSONArray("specialEdges") ?: JSONArray()
                for (i in 0 until specialArr.length()) {
                    val o = specialArr.getJSONObject(i)
                    val from = o.getString("from").trim().removeSuffix("_00")
                    val to = o.getString("to").trim().removeSuffix("_00")
                    specialMap[EdgeKey(from, to)] = SpecialEdgeRule(
                        from = from,
                        to = to,
                        stem = o.getString("stem"),
                        title = o.getString("title"),
                    )
                }

                val gpMap = linkedMapOf<EdgeKey, List<String>>()
                val gpArr = root.optJSONArray("gpEdges") ?: JSONArray()
                for (i in 0 until gpArr.length()) {
                    val o = gpArr.getJSONObject(i)
                    val from = o.getString("from").trim().removeSuffix("_00")
                    val to = o.getString("to").trim().removeSuffix("_00")
                    val cArr = o.getJSONArray("candidates")
                    val cands = (0 until cArr.length()).map { cArr.getString(it).trim() }.filter { it.isNotEmpty() }.distinct()
                    gpMap[EdgeKey(from, to)] = cands
                }

                val tracksArr = root.optJSONArray("tracks") ?: JSONArray()
                val trackRules = (0 until tracksArr.length()).map { i ->
                    val o = tracksArr.getJSONObject(i)
                    fun strSet(key: String): Set<String> {
                        val a = o.optJSONArray(key) ?: return emptySet()
                        return (0 until a.length()).map { a.getString(it).trim() }.filter { it.isNotEmpty() }.toSet()
                    }
                    TrackRule(
                        jukeboxCode = o.getString("jukeboxCode").trim(),
                        anyOriginDay = o.optBoolean("anyOriginDay", false),
                        anyOriginNight = o.optBoolean("anyOriginNight", false),
                        dayThemes = strSet("dayThemes"),
                        dayCourses = strSet("dayCourses"),
                        likelyDayThemes = strSet("likelyDayThemes"),
                        likelyDayCourses = strSet("likelyDayCourses"),
                    )
                }

                RouteMusicCatalog(
                    schemaVersion = schemaVersion,
                    themesByStage = themesByStage,
                    exclusiveOriginCourses = exclusiveCourses,
                    specialEdges = specialMap,
                    gpEdges = gpMap,
                    tracks = trackRules,
                )
            }.getOrNull()
        }
    }

    sealed interface EdgeMusicResolution {
        val edge: StageConnections.Edge?

        data class KnownPoolComplete(
            override val edge: StageConnections.Edge?,
            val candidates: List<JukeboxCatalog.TrackItem>,
            val selectedOverride: JukeboxCatalog.TrackItem?,
            val candidateTiers: Map<String, CandidateTier> = emptyMap(),
            val candidateWeights: List<Int> = List(candidates.size) { 1 },
            val ruleModeUsed: EffectiveRuleMode? = null,
        ) : EdgeMusicResolution

        data class KnownPoolIncomplete(
            override val edge: StageConnections.Edge?,
            val missingCandidateRefs: List<String>,
        ) : EdgeMusicResolution

        data class SpecialRoadApproach(
            override val edge: StageConnections.Edge?,
            val stem: String,
            val title: String,
        ) : EdgeMusicResolution

        data class KnockoutTourDirect(
            override val edge: StageConnections.Edge?,
        ) : EdgeMusicResolution

        data class CustomSubstitute(
            override val edge: StageConnections.Edge?,
            val track: JukeboxCatalog.TrackItem,
        ) : EdgeMusicResolution

        data class SubstituteMissing(
            override val edge: StageConnections.Edge?,
            val missingRef: String,
        ) : EdgeMusicResolution

        data class MissingCandidateData(
            override val edge: StageConnections.Edge?,
        ) : EdgeMusicResolution

        data class DisabledByUser(
            override val edge: StageConnections.Edge?,
        ) : EdgeMusicResolution
    }

    data class RouteLegInspection(
        val index: Int,
        val from: String,
        val to: String,
        val edge: StageConnections.Edge?,
        val connectionValid: Boolean,
        val musicEnabled: Boolean,
        val resolution: EdgeMusicResolution,
    )

    data class RouteValidationReport(
        val canPlay: Boolean,
        val invalidTopologyEdges: List<EdgeKey>,
        val unplayableStops: List<String>,
        val incompletePoolEdges: List<EdgeKey>,
        val missingSubstituteEdges: List<EdgeKey>,
        val legs: List<RouteLegInspection>,
        val blockingReason: String? = null,
    )

    fun effectiveModeForOfficial(routeKind: OfficialRouteKind, mode: RoadMusicRuleMode): EffectiveRuleMode =
        when (mode) {
            RoadMusicRuleMode.AUTO -> when (routeKind) {
                OfficialRouteKind.GRAND_PRIX -> EffectiveRuleMode.GRAND_PRIX
                OfficialRouteKind.RALLY -> EffectiveRuleMode.KNOCKOUT_DIRECT
            }
            RoadMusicRuleMode.GRAND_PRIX -> EffectiveRuleMode.GRAND_PRIX
            RoadMusicRuleMode.VS_DAY -> EffectiveRuleMode.VS_DAY
            RoadMusicRuleMode.VS_NIGHT -> EffectiveRuleMode.VS_NIGHT
        }

    fun effectiveModeForCustom(mode: RoadMusicRuleMode): EffectiveRuleMode =
        when (mode) {
            RoadMusicRuleMode.AUTO,
            RoadMusicRuleMode.VS_DAY -> EffectiveRuleMode.VS_DAY
            RoadMusicRuleMode.VS_NIGHT -> EffectiveRuleMode.VS_NIGHT
            RoadMusicRuleMode.GRAND_PRIX -> EffectiveRuleMode.GRAND_PRIX
        }

    /** Auto-identifies installed Jukebox track strictly by verified metadata (#83). */
    fun matchJukeboxItem(
        ref: String,
        installedItems: List<JukeboxCatalog.TrackItem>,
        readableCheck: (JukeboxCatalog.TrackItem) -> Boolean = { true },
    ): JukeboxCatalog.TrackItem? {
        val norm = ref.trim()
        if (norm.isEmpty()) return null
        val lower = norm.lowercase(Locale.ROOT)
        val match = installedItems.firstOrNull { item ->
            item.track.key == norm ||
                item.track.primary.sha256.equals(lower, ignoreCase = true) ||
                item.jukeboxCode?.equals(norm, ignoreCase = true) == true ||
                item.track.title.equals(norm, ignoreCase = true) ||
                item.searchAliases.any { it.equals(norm, ignoreCase = true) }
        } ?: return null
        return match.takeIf(readableCheck)
    }

    fun inspectCustomRoute(
        route: CustomRoute,
        connections: StageConnections.Catalog?,
        pool: CandidatePool,
        installedJukebox: List<JukeboxCatalog.TrackItem>,
        isStagePlayable: (String) -> Boolean,
        isStageHidden: (String) -> Boolean,
        isJukeboxReadable: (JukeboxCatalog.TrackItem) -> Boolean = { true },
        wikiCatalog: RouteMusicCatalog? = null,
        ruleModeOverride: RoadMusicRuleMode? = null,
    ): RouteValidationReport {
        val stopCodes = route.stops.map { it.stageCode.removeSuffix("_00") }
        val unplayable = stopCodes.filter { isStageHidden(it) || !isStagePlayable(it) }.distinct()
        val invalidEdges = mutableListOf<EdgeKey>()
        val incompletePools = mutableListOf<EdgeKey>()
        val missingSubstitutes = mutableListOf<EdgeKey>()
        val legs = mutableListOf<RouteLegInspection>()
        val effectiveMode = effectiveModeForCustom(ruleModeOverride ?: route.roadRuleMode)

        if (stopCodes.size >= 2) {
            for (i in 0 until stopCodes.lastIndex) {
                val from = stopCodes[i]
                val to = stopCodes[i + 1]
                val edgeCfg = route.edges.getOrNull(i)
                val conn = connections?.between(from, to)
                val connValid = connections != null && conn != null && from != to
                if (!connValid) {
                    invalidEdges += EdgeKey(from, to)
                }
                val musicEnabled = edgeCfg?.musicEnabled ?: true
                val resolution = resolveEdge(
                    from = from,
                    to = to,
                    edge = conn,
                    musicEnabled = musicEnabled,
                    selectedRef = edgeCfg?.selectedJukeboxKey,
                    allowCustomSubstitute = true,
                    pool = pool,
                    installedJukebox = installedJukebox,
                    isJukeboxReadable = isJukeboxReadable,
                    wikiCatalog = wikiCatalog,
                    effectiveRuleMode = effectiveMode,
                )
                if (resolution is EdgeMusicResolution.KnownPoolIncomplete) {
                    incompletePools += EdgeKey(from, to)
                } else if (resolution is EdgeMusicResolution.SubstituteMissing) {
                    missingSubstitutes += EdgeKey(from, to)
                }
                legs += RouteLegInspection(i, from, to, conn, connValid, musicEnabled, resolution)
            }
        }

        val reason = when {
            stopCodes.size < 2 -> "Route needs at least 2 stage stops (#74)."
            invalidEdges.isNotEmpty() -> "Invalid directed road connection: ${invalidEdges.joinToString { "${it.from}->${it.to}" }} (#74/#87)."
            incompletePools.isNotEmpty() -> "Missing required candidate track(s) on ${incompletePools.joinToString { "${it.from}->${it.to}" }} (#82)."
            missingSubstitutes.isNotEmpty() -> "Selected custom substitute track unavailable on ${missingSubstitutes.joinToString { "${it.from}->${it.to}" }}."
            unplayable.isNotEmpty() -> "Missing or locked stage audio: ${unplayable.joinToString()}."
            else -> null
        }

        return RouteValidationReport(
            canPlay = reason == null,
            invalidTopologyEdges = invalidEdges,
            unplayableStops = unplayable,
            incompletePoolEdges = incompletePools,
            missingSubstituteEdges = missingSubstitutes,
            legs = legs,
            blockingReason = reason,
        )
    }

    fun inspectOfficialRoute(
        route: OfficialRoute,
        connections: StageConnections.Catalog?,
        pool: CandidatePool,
        installedJukebox: List<JukeboxCatalog.TrackItem>,
        sessionOverrides: Map<Int, String> = emptyMap(),
        isStagePlayable: (String) -> Boolean,
        isStageHidden: (String) -> Boolean,
        isJukeboxReadable: (JukeboxCatalog.TrackItem) -> Boolean = { true },
        wikiCatalog: RouteMusicCatalog? = null,
        ruleMode: RoadMusicRuleMode = RoadMusicRuleMode.AUTO,
    ): RouteValidationReport {
        val stopCodes = route.stops.map { it.stageCode.removeSuffix("_00") }
        val unplayable = stopCodes.filter { isStageHidden(it) || !isStagePlayable(it) }.distinct()
        val incompletePools = mutableListOf<EdgeKey>()
        val legs = mutableListOf<RouteLegInspection>()
        val effectiveMode = effectiveModeForOfficial(route.kind, ruleMode)

        for (i in 0 until stopCodes.lastIndex) {
            val from = stopCodes[i]
            val to = stopCodes[i + 1]
            val conn = connections?.between(from, to)
            val resolution = resolveEdge(
                from = from,
                to = to,
                edge = conn,
                musicEnabled = true,
                selectedRef = sessionOverrides[i],
                allowCustomSubstitute = false, // Official routes only pick valid pool candidates (#71)
                pool = pool,
                installedJukebox = installedJukebox,
                isJukeboxReadable = isJukeboxReadable,
                wikiCatalog = wikiCatalog,
                effectiveRuleMode = effectiveMode,
            )
            if (resolution is EdgeMusicResolution.KnownPoolIncomplete) {
                incompletePools += EdgeKey(from, to)
            }
            legs += RouteLegInspection(i, from, to, conn, connectionValid = true, musicEnabled = true, resolution = resolution)
        }

        val reason = when {
            stopCodes.isEmpty() -> "Official route has no stops."
            incompletePools.isNotEmpty() -> "Missing required candidate track(s) on ${incompletePools.joinToString { "${it.from}->${it.to}" }} (#82)."
            unplayable.isNotEmpty() -> "Missing or locked stage audio: ${unplayable.joinToString()}."
            else -> null
        }

        return RouteValidationReport(
            canPlay = reason == null,
            invalidTopologyEdges = emptyList(),
            unplayableStops = unplayable,
            incompletePoolEdges = incompletePools,
            missingSubstituteEdges = emptyList(),
            legs = legs,
            blockingReason = reason,
        )
    }

    private fun resolveEdge(
        from: String,
        to: String,
        edge: StageConnections.Edge?,
        musicEnabled: Boolean,
        selectedRef: String?,
        allowCustomSubstitute: Boolean,
        pool: CandidatePool,
        installedJukebox: List<JukeboxCatalog.TrackItem>,
        isJukeboxReadable: (JukeboxCatalog.TrackItem) -> Boolean,
        wikiCatalog: RouteMusicCatalog?,
        effectiveRuleMode: EffectiveRuleMode,
    ): EdgeMusicResolution {
        if (effectiveRuleMode == EffectiveRuleMode.KNOCKOUT_DIRECT) {
            return EdgeMusicResolution.KnockoutTourDirect(edge)
        }
        val poolRefs = pool.candidatesFor(from, to)
        if (!poolRefs.isNullOrEmpty()) {
            // Known candidate pool from pack: #82 requires ALL candidates to exist & be readable, even if toggled off.
            val matched = mutableListOf<JukeboxCatalog.TrackItem>()
            val missing = mutableListOf<String>()
            for (ref in poolRefs) {
                val item = matchJukeboxItem(ref, installedJukebox, isJukeboxReadable)
                if (item != null) matched += item else missing += ref
            }
            if (missing.isNotEmpty()) {
                return EdgeMusicResolution.KnownPoolIncomplete(edge, missing)
            }
            if (!musicEnabled) {
                return EdgeMusicResolution.DisabledByUser(edge)
            }
            val selected = selectedRef?.let { ref ->
                matched.firstOrNull {
                    it.track.key == ref ||
                        it.track.primary.sha256.equals(ref, ignoreCase = true) ||
                        it.jukeboxCode?.equals(ref, ignoreCase = true) == true
                }
            }
            return EdgeMusicResolution.KnownPoolComplete(edge, matched, selected)
        }

        if (wikiCatalog != null) {
            val special = wikiCatalog.specialFor(from, to)
            if (special != null) {
                if (!musicEnabled) return EdgeMusicResolution.DisabledByUser(edge)
                if (allowCustomSubstitute && !selectedRef.isNullOrBlank()) {
                    val found = matchJukeboxItem(selectedRef, installedJukebox, isJukeboxReadable)
                        ?: return EdgeMusicResolution.SubstituteMissing(edge, selectedRef)
                    return EdgeMusicResolution.CustomSubstitute(edge, found)
                }
                return EdgeMusicResolution.SpecialRoadApproach(edge, special.stem, special.title)
            }

            val ranked = wikiCatalog.resolveRuleCandidates(from, to, effectiveRuleMode)
            if (ranked.isNotEmpty()) {
                val matchedItems = mutableListOf<JukeboxCatalog.TrackItem>()
                val matchedTiers = linkedMapOf<String, CandidateTier>()
                val matchedWeights = mutableListOf<Int>()
                for (cand in ranked) {
                    val item = matchJukeboxItem(cand.jukeboxCode, installedJukebox, isJukeboxReadable)
                    if (item != null && !matchedTiers.containsKey(item.key)) {
                        matchedItems += item
                        matchedTiers[item.key] = cand.tier
                        matchedWeights += cand.weight
                    }
                }
                if (matchedItems.isNotEmpty()) {
                    if (!musicEnabled) {
                        return EdgeMusicResolution.DisabledByUser(edge)
                    }
                    val selectedInPool = selectedRef?.let { ref ->
                        matchedItems.firstOrNull {
                            it.track.key == ref ||
                                it.track.primary.sha256.equals(ref, ignoreCase = true) ||
                                it.jukeboxCode?.equals(ref, ignoreCase = true) == true
                        }
                    }
                    if (selectedInPool == null && allowCustomSubstitute && !selectedRef.isNullOrBlank()) {
                        val sub = matchJukeboxItem(selectedRef, installedJukebox, isJukeboxReadable)
                            ?: return EdgeMusicResolution.SubstituteMissing(edge, selectedRef)
                        return EdgeMusicResolution.CustomSubstitute(edge, sub)
                    }
                    return EdgeMusicResolution.KnownPoolComplete(
                        edge = edge,
                        candidates = matchedItems,
                        selectedOverride = selectedInPool,
                        candidateTiers = matchedTiers,
                        candidateWeights = matchedWeights,
                        ruleModeUsed = effectiveRuleMode,
                    )
                }
            }
        }

        if (!musicEnabled) {
            return EdgeMusicResolution.DisabledByUser(edge)
        }
        if (allowCustomSubstitute && !selectedRef.isNullOrBlank()) {
            val found = matchJukeboxItem(selectedRef, installedJukebox, isJukeboxReadable)
                ?: return EdgeMusicResolution.SubstituteMissing(edge, selectedRef)
            return EdgeMusicResolution.CustomSubstitute(edge, found)
        }
        return EdgeMusicResolution.MissingCandidateData(edge)
    }

    fun loadInternalPool(filesDir: File): CandidatePool {
        val file = File(filesDir, "road-jukebox-pool.json")
        if (!file.isFile) return CandidatePool()
        return runCatching { CandidatePool.parse(file.readText()) }.getOrNull() ?: CandidatePool()
    }
}
