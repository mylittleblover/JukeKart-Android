package com.mylittleblover.mkwplayer.routes

import com.mylittleblover.mkwplayer.library.StageConnections
import com.mylittleblover.mkwplayer.pack.PackManifest
import com.mylittleblover.mkwplayer.registry.AudioAssetId
import com.mylittleblover.mkwplayer.registry.ResourceKind
import com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RoadJukeboxCatalogTest {

    private fun connectionsCatalog(): StageConnections.Catalog {
        val file = File("src/main/assets/stage_connections.json")
        return StageConnections.parse(file.readText())!!
    }

    private fun trackItem(
        key: String,
        hexDigit: Char,
        jukeboxCode: String,
        name: String,
    ): JukeboxCatalog.TrackItem {
        val sha = hexDigit.toString().repeat(64)
        val assetId = AudioAssetId(sha)
        val track = PackManifest.Track(
            key = key,
            kind = ResourceKind.JUKEBOX,
            title = key,
            primary = assetId,
            isFlow = false,
            parts = listOf(PackManifest.Part(asset = assetId, role = PackManifest.Role.MAIN)),
        )
        return JukeboxCatalog.TrackItem(
            track = track,
            gameId = "mkw",
            gameOrder = 1,
            jukeboxOrder = 1,
            ostIndex = 1,
            title = name,
            arrangement = null,
            displayName = name,
            jukeboxCode = jukeboxCode,
            frames = 48_000,
            sampleRate = 48_000,
            durationMs = 1_000L,
            searchAliases = listOf("alias_$key"),
        )
    }

    @Test
    fun candidatePoolReplacementEnforcesMonotonicVersionAndDisallowsConflictingSameVersion() {
        val v1 = RoadJukeboxCatalog.CandidatePool(
            dataVersion = 1,
            entries = mapOf(RoadJukeboxCatalog.EdgeKey("A", "B") to listOf("jb_1")),
        )
        val v1Same = RoadJukeboxCatalog.CandidatePool.parse(v1.toJsonString())!!
        val sameMerged = assertIs<RoadJukeboxCatalog.MergeResult.Replaced>(v1.mergeReplace(v1Same))
        assertEquals(1, sameMerged.pool.dataVersion)
        assertFalse(sameMerged.changed)

        val v1Conflict = RoadJukeboxCatalog.CandidatePool(
            dataVersion = 1,
            entries = mapOf(RoadJukeboxCatalog.EdgeKey("A", "B") to listOf("jb_other")),
        )
        assertIs<RoadJukeboxCatalog.MergeResult.RejectedConflict>(v1.mergeReplace(v1Conflict))

        val v0Lower = RoadJukeboxCatalog.CandidatePool(dataVersion = 0)
        assertIs<RoadJukeboxCatalog.MergeResult.IgnoredOlderVersion>(v1.mergeReplace(v0Lower))

        val v2Higher = RoadJukeboxCatalog.CandidatePool(
            dataVersion = 2,
            entries = mapOf(RoadJukeboxCatalog.EdgeKey("B", "C") to listOf("jb_2")),
        )
        val replaced = assertIs<RoadJukeboxCatalog.MergeResult.Replaced>(v1.mergeReplace(v2Higher))
        assertTrue(replaced.changed)
        assertEquals(2, replaced.pool.dataVersion)
        assertFalse(replaced.pool.entries.containsKey(RoadJukeboxCatalog.EdgeKey("A", "B")))
        assertEquals(listOf("jb_2"), replaced.pool.entries[RoadJukeboxCatalog.EdgeKey("B", "C")])
    }

    @Test
    fun customRouteInspectionEnforcesTopologyIncompletePoolBlockAndCustomSubstitute() {
        val connections = connectionsCatalog()
        val item1 = trackItem("track_1", '1', "JB01", "Road Tune 1")
        val item2 = trackItem("track_2", '2', "JB02", "Road Tune 2")
        val subItem = trackItem("sub_track", '3', "JB99", "Substitute Tune")
        val installed = listOf(item1, item2, subItem)

        // Known pool on DonkeyKong -> FirstCircuit requires ALL candidates present & readable (#82).
        val pool = RoadJukeboxCatalog.CandidatePool(
            dataVersion = 1,
            entries = mapOf(
                RoadJukeboxCatalog.EdgeKey("DonkeyKong", "FirstCircuit") to listOf("JB01", "JB02"),
            ),
        )

        // Verified edges: DonkeyKong -> FirstCircuit (known pool) + FirstCircuit -> WestCity (no pool data).
        assertTrue(connections.connects("FirstCircuit", "WestCity"))
        val customRoute = CustomRoute(
            id = "cr1",
            name = "DK to WestCity",
            stops = listOf(
                CustomRouteStop("DonkeyKong"),
                CustomRouteStop("FirstCircuit"),
                CustomRouteStop("WestCity"),
            ),
            edges = listOf(
                CustomRouteEdgeConfig("DonkeyKong", "FirstCircuit", musicEnabled = true, selectedJukeboxKey = null),
                CustomRouteEdgeConfig("FirstCircuit", "WestCity", musicEnabled = true, selectedJukeboxKey = "JB99"), // #73 CustomSubstitute
            ),
        )

        val okReport = RoadJukeboxCatalog.inspectCustomRoute(
            route = customRoute,
            connections = connections,
            pool = pool,
            installedJukebox = installed,
            isStagePlayable = { true },
            isStageHidden = { false },
            isJukeboxReadable = { true },
        )
        assertTrue(okReport.canPlay)
        val leg0 = assertIs<RoadJukeboxCatalog.EdgeMusicResolution.KnownPoolComplete>(okReport.legs[0].resolution)
        assertEquals(2, leg0.candidates.size)
        val leg1 = assertIs<RoadJukeboxCatalog.EdgeMusicResolution.CustomSubstitute>(okReport.legs[1].resolution)
        assertEquals("sub_track", leg1.track.key)

        // Missing/unreadable ANY candidate on DonkeyKong -> FirstCircuit blocks the whole route (#82).
        val blockedReport = RoadJukeboxCatalog.inspectCustomRoute(
            route = customRoute,
            connections = connections,
            pool = pool,
            installedJukebox = installed,
            isStagePlayable = { true },
            isStageHidden = { false },
            isJukeboxReadable = { it.key != "track_2" },
        )
        assertFalse(blockedReport.canPlay)
        assertTrue(blockedReport.incompletePoolEdges.contains(RoadJukeboxCatalog.EdgeKey("DonkeyKong", "FirstCircuit")))

        // Reversed one-way edge FirstCircuit -> DonkeyKong is rejected as invalid topology (#74, #75).
        val reversedRoute = CustomRoute(
            id = "bad_rev",
            name = "Invalid Reverse",
            stops = listOf(CustomRouteStop("FirstCircuit"), CustomRouteStop("DonkeyKong")),
            edges = listOf(CustomRouteEdgeConfig("FirstCircuit", "DonkeyKong")),
        )
        val badReport = RoadJukeboxCatalog.inspectCustomRoute(
            route = reversedRoute,
            connections = connections,
            pool = pool,
            installedJukebox = installed,
            isStagePlayable = { true },
            isStageHidden = { false },
        )
        assertFalse(badReport.canPlay)
        assertEquals(listOf(RoadJukeboxCatalog.EdgeKey("FirstCircuit", "DonkeyKong")), badReport.invalidTopologyEdges)
    }

    @Test
    fun officialRouteNeverAcceptsCustomSubstituteAndCleanlySkipsWhenPoolMissing() {
        val connections = connectionsCatalog()
        val route = OfficialRoute(
            id = "mushroom_cup",
            kind = OfficialRouteKind.GRAND_PRIX,
            names = mapOf("en" to "Mushroom Cup"),
            stops = listOf(
                OfficialStop("s1", 1, "FirstCircuit"),
                OfficialStop("s2", 2, "WestCity"),
            ),
            requiresSpecialUnlock = false,
            sourceUrl = "https://www.mariowiki.com/Mushroom_Cup",
        )
        val report = RoadJukeboxCatalog.inspectOfficialRoute(
            route = route,
            connections = connections,
            pool = RoadJukeboxCatalog.CandidatePool(),
            installedJukebox = emptyList(),
            isStagePlayable = { true },
            isStageHidden = { false },
        )
        assertTrue(report.canPlay)
        assertIs<RoadJukeboxCatalog.EdgeMusicResolution.MissingCandidateData>(report.legs.single().resolution)
    }

    @Test
    fun bundledRouteMusicCatalogMatchesWikiStructureAndJukeboxCatalog() {
        val routeCatalogText = File("src/main/assets/route_music_catalog.json").readText()
        val jukeboxJson = org.json.JSONObject(File("src/main/assets/jukebox_catalog.json").readText())
        val jukeboxCodes = buildSet {
            val tracks = jukeboxJson.getJSONArray("tracks")
            for (t in 0 until tracks.length()) {
                val code = tracks.getJSONObject(t).optString("jukeboxCode").trim()
                if (code.isNotEmpty()) add(code)
            }
        }
        val wiki = RoadJukeboxCatalog.RouteMusicCatalog.parse(routeCatalogText)!!
        assertEquals(23, wiki.gpEdges.size)
        assertEquals(1, wiki.specialEdges.size)
        assertEquals(133, wiki.tracks.size)
        assertEquals("BGM_Rainbow_Area1_Pre", wiki.specialFor("Central", "Rainbow")?.stem)
        for (rule in wiki.tracks) {
            assertTrue(rule.jukeboxCode in jukeboxCodes, "Missing jukebox code ${rule.jukeboxCode}")
        }
        for ((edge, candidates) in wiki.gpEdges) {
            assertTrue(candidates.isNotEmpty(), "GP edge $edge has no candidates")
            for (code in candidates) {
                assertTrue(code in jukeboxCodes, "GP edge $edge references unknown code $code")
            }
        }
    }

    @Test
    fun officialAndCustomRoutesFollowGpVsDayVsNightAndKnockoutDirectRules() {
        val connections = connectionsCatalog()
        val wiki = RoadJukeboxCatalog.RouteMusicCatalog.parse(
            File("src/main/assets/route_music_catalog.json").readText(),
        )!!
        val installed = wiki.tracks.map { rule ->
            trackItem(
                key = "jb_${rule.jukeboxCode}",
                hexDigit = 'a',
                jukeboxCode = rule.jukeboxCode,
                name = "Track ${rule.jukeboxCode}",
            )
        }

        // 1. Official GP: Special Cup includes BigTree -> SFCMarioCircuit -> Central -> Rainbow
        val specialCup = OfficialRoute(
            id = "special_cup",
            kind = OfficialRouteKind.GRAND_PRIX,
            names = mapOf("en" to "Special Cup"),
            stops = listOf(
                OfficialStop("s1", 1, "BigTree"),
                OfficialStop("s2", 2, "SFCMarioCircuit"),
                OfficialStop("s3", 3, "Central"),
                OfficialStop("s4", 4, "Rainbow"),
            ),
            requiresSpecialUnlock = true,
            sourceUrl = "https://www.mariowiki.com/Special_Cup",
        )
        val gpReport = RoadJukeboxCatalog.inspectOfficialRoute(
            route = specialCup,
            connections = connections,
            pool = RoadJukeboxCatalog.CandidatePool(),
            installedJukebox = installed,
            isStagePlayable = { true },
            isStageHidden = { false },
            wikiCatalog = wiki,
            ruleMode = RoadJukeboxCatalog.RoadMusicRuleMode.AUTO,
        )
        assertTrue(gpReport.canPlay)
        val leg0 = assertIs<RoadJukeboxCatalog.EdgeMusicResolution.KnownPoolComplete>(gpReport.legs[0].resolution)
        assertEquals(RoadJukeboxCatalog.EffectiveRuleMode.GRAND_PRIX, leg0.ruleModeUsed)
        assertTrue(leg0.candidates.isNotEmpty())
        val leg1 = assertIs<RoadJukeboxCatalog.EdgeMusicResolution.KnownPoolComplete>(gpReport.legs[1].resolution)
        assertEquals(RoadJukeboxCatalog.EffectiveRuleMode.GRAND_PRIX, leg1.ruleModeUsed)
        val leg2Special = assertIs<RoadJukeboxCatalog.EdgeMusicResolution.SpecialRoadApproach>(gpReport.legs[2].resolution)
        assertEquals("BGM_Rainbow_Area1_Pre", leg2Special.stem)

        // 2. Official Rally in AUTO uses KNOCKOUT_DIRECT; switching to VS_DAY yields weighted VS Day candidates
        val goldenRally = OfficialRoute(
            id = "golden_rally",
            kind = OfficialRouteKind.RALLY,
            names = mapOf("en" to "Golden Rally"),
            stops = listOf(
                OfficialStop("r1", 1, "DesertHills"),
                OfficialStop("r2", 2, "ShyGuyBazaar"),
            ),
            requiresSpecialUnlock = false,
            sourceUrl = "https://www.mariowiki.com/Golden_Rally",
        )
        val rallyAuto = RoadJukeboxCatalog.inspectOfficialRoute(
            route = goldenRally,
            connections = connections,
            pool = RoadJukeboxCatalog.CandidatePool(),
            installedJukebox = installed,
            isStagePlayable = { true },
            isStageHidden = { false },
            wikiCatalog = wiki,
            ruleMode = RoadJukeboxCatalog.RoadMusicRuleMode.AUTO,
        )
        assertIs<RoadJukeboxCatalog.EdgeMusicResolution.KnockoutTourDirect>(rallyAuto.legs.single().resolution)

        val rallyVsDay = RoadJukeboxCatalog.inspectOfficialRoute(
            route = goldenRally,
            connections = connections,
            pool = RoadJukeboxCatalog.CandidatePool(),
            installedJukebox = installed,
            isStagePlayable = { true },
            isStageHidden = { false },
            wikiCatalog = wiki,
            ruleMode = RoadJukeboxCatalog.RoadMusicRuleMode.VS_DAY,
        )
        val desertDay = assertIs<RoadJukeboxCatalog.EdgeMusicResolution.KnownPoolComplete>(rallyVsDay.legs.single().resolution)
        assertEquals(RoadJukeboxCatalog.EffectiveRuleMode.VS_DAY, desertDay.ruleModeUsed)
        assertTrue(desertDay.candidateTiers.values.any { it == RoadJukeboxCatalog.CandidateTier.EXCLUSIVE_ORDER })
        assertTrue(desertDay.candidateTiers.values.any { it == RoadJukeboxCatalog.CandidateTier.THEMED_BOOSTED })
        assertTrue(desertDay.candidateTiers.values.any { it == RoadJukeboxCatalog.CandidateTier.MAIN_ORDER })
        assertTrue(desertDay.candidateWeights.zip(desertDay.candidates).all { (w, item) ->
            val tier = desertDay.candidateTiers[item.key]
            if (tier == RoadJukeboxCatalog.CandidateTier.EXCLUSIVE_ORDER || tier == RoadJukeboxCatalog.CandidateTier.THEMED_BOOSTED) {
                w == 4
            } else {
                w == 1
            }
        })

        // 3. Custom route in VS_NIGHT uses NIGHT_ORDER exclusively
        val customNight = CustomRoute(
            id = "custom_night",
            name = "Night Cruise",
            stops = listOf(
                CustomRouteStop("DesertHills"),
                CustomRouteStop("ShyGuyBazaar"),
            ),
            edges = listOf(CustomRouteEdgeConfig("DesertHills", "ShyGuyBazaar")),
            roadRuleMode = RoadJukeboxCatalog.RoadMusicRuleMode.VS_NIGHT,
        )
        val nightReport = RoadJukeboxCatalog.inspectCustomRoute(
            route = customNight,
            connections = connections,
            pool = RoadJukeboxCatalog.CandidatePool(),
            installedJukebox = installed,
            isStagePlayable = { true },
            isStageHidden = { false },
            wikiCatalog = wiki,
        )
        val nightRes = assertIs<RoadJukeboxCatalog.EdgeMusicResolution.KnownPoolComplete>(nightReport.legs.single().resolution)
        assertEquals(RoadJukeboxCatalog.EffectiveRuleMode.VS_NIGHT, nightRes.ruleModeUsed)
        assertEquals(wiki.tracks.count { it.anyOriginNight }, nightRes.candidates.size)
        assertTrue(nightRes.candidateTiers.values.all { it == RoadJukeboxCatalog.CandidateTier.NIGHT_ORDER })
    }

    @Test
    fun linearQueueHonorsWeightedRoadCandidateDistribution() {
        val heavy = com.mylittleblover.mkwplayer.player.LinearQueue.Entry(key = "heavy", title = "Heavy")
        val light = com.mylittleblover.mkwplayer.player.LinearQueue.Entry(key = "light", title = "Light")
        val template = heavy.copy(
            roadCandidateVariants = listOf(heavy, light),
            roadCandidateWeights = listOf(4, 1),
        )
        val queue = com.mylittleblover.mkwplayer.player.LinearQueue(kotlin.random.Random(12345))
        var heavyCount = 0
        var lightCount = 0
        repeat(500) {
            val picked = queue.startSession(listOf(template), 0)!!
            if (picked.key == "heavy") heavyCount++ else if (picked.key == "light") lightCount++
        }
        assertTrue(heavyCount > 350 && lightCount > 50, "Expected ~4:1 distribution, got heavy=$heavyCount light=$lightCount")
    }
}

