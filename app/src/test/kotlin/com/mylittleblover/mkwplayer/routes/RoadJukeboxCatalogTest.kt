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
}
