package com.mylittleblover.mkwplayer.ui.home

import com.mylittleblover.mkwplayer.pack.PackManifest
import com.mylittleblover.mkwplayer.registry.AudioAssetId
import com.mylittleblover.mkwplayer.registry.ResourceKind
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JukeboxMetadataCatalogTest {

    private fun bundledCatalogFile(): File {
        val candidates = listOf(
            File("src/main/assets/jukebox_catalog.json"),
            File("app/src/main/assets/jukebox_catalog.json"),
            File("MKW-Player/app/src/main/assets/jukebox_catalog.json"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("Could not locate jukebox_catalog.json from ${File(".").absolutePath}")
    }

    private fun fakeTrack(
        key: String,
        title: String,
        sha256: String,
    ): PackManifest.Track {
        val asset = AudioAssetId(sha256)
        return PackManifest.Track(
            key = key,
            kind = ResourceKind.JUKEBOX,
            title = title,
            primary = asset,
            isFlow = false,
            parts = listOf(PackManifest.Part(asset, PackManifest.Role.MAIN)),
            timing = null,
        )
    }

    @Test
    fun parsesAll36ChronologicalGamesAnd227TracksFromBundledAsset() {
        val metadata = JukeboxCatalog.parseMetadata(bundledCatalogFile().readText())
        assertNotNull(metadata)
        assertEquals(36, metadata.games.size)
        assertEquals((1..36).toList(), metadata.games.map { it.order })
        assertEquals(227, metadata.tracks.size)
        assertEquals((1..227).toList(), metadata.tracks.map { it.jukeboxOrder })

        val packTracks = metadata.tracks.map { t ->
            fakeTrack(key = t.packTrackKey, title = t.bwavStem, sha256 = t.sha256)
        }
        val catalog = JukeboxCatalog.buildCatalog(packTracks, emptyMap(), metadata)

        assertEquals(36, catalog.games.size)
        assertEquals(227, catalog.trackCount)
        assertEquals((1..36).toList(), catalog.games.map { it.order })
        assertEquals("smb", catalog.games.first().id)

        // Tracks inside each game preserve official OST sequence.
        for (game in catalog.games) {
            val orders = game.items.map { it.jukeboxOrder }
            assertEquals(orders.sorted(), orders, "OST track order broke inside ${game.id}")
        }
    }

    @Test
    fun showsOnlyGamesPresentInInstalledPacksWhilePreservingChronology() {
        val metadata = JukeboxCatalog.parseMetadata(bundledCatalogFile().readText())!!
        val last = metadata.tracks.last()
        val first = metadata.tracks.first()
        val catalog = JukeboxCatalog.buildCatalog(
            jukeboxTracks = listOf(
                fakeTrack(last.packTrackKey, last.bwavStem, last.sha256),
                fakeTrack(first.packTrackKey, first.bwavStem, first.sha256),
            ),
            metadata = metadata,
        )

        assertEquals(listOf(first.gameId, last.gameId).distinct(), catalog.games.map { it.id })
        assertEquals(first.gameOrder, catalog.games.first().order)
    }

    @Test
    fun routesCrossGamePrefixAnomaliesToCanonicalOstGames() {
        val metadata = JukeboxCatalog.parseMetadata(bundledCatalogFile().readText())!!
        val anomalyIndices = setOf(255, 258, 271, 324)
        val anomalyMeta = metadata.tracks.filter { it.ostIndex in anomalyIndices }
        assertEquals(4, anomalyMeta.size)
        assertTrue(anomalyMeta.any { it.bwavGameId.isNotBlank() && !it.bwavGameId.equals(it.gameId, ignoreCase = true) })

        val catalog = JukeboxCatalog.buildCatalog(
            jukeboxTracks = anomalyMeta.map { fakeTrack(it.packTrackKey, it.bwavStem, it.sha256) },
            metadata = metadata,
        )

        for (meta in anomalyMeta) {
            val resolvedGame = catalog.gameForTrack(meta.packTrackKey)
            assertNotNull(resolvedGame)
            assertEquals(meta.gameId, resolvedGame.id, "OST #${meta.ostIndex} must stay in ${meta.gameId}")
        }
    }

    @Test
    fun keepsDistinctArrangementsWithSharedTitleAsIndependentRows() {
        val metadata = JukeboxCatalog.parseMetadata(bundledCatalogFile().readText())!!
        val groupedByTitle = metadata.tracks.groupBy { it.gameId to it.ostTitle }
        val sharedGroup = groupedByTitle.values.firstOrNull { items ->
            items.size >= 2 && items.mapNotNull { it.ostArrangement }.distinct().size >= 2
        }
        assertNotNull(sharedGroup, "Expected at least one shared-title multi-arrangement pair in OST metadata")

        val catalog = JukeboxCatalog.buildCatalog(
            jukeboxTracks = sharedGroup.map { fakeTrack(it.packTrackKey, it.bwavStem, it.sha256) },
            metadata = metadata,
        )
        val game = catalog.gameById(sharedGroup.first().gameId)!!
        assertEquals(sharedGroup.size, game.items.size)
        assertEquals(sharedGroup.map { it.packTrackKey }, game.items.map { it.key })
    }

    @Test
    fun supportsLocalizedGameNamesMultiKeywordSearchAndFallbackTracks() {
        val metadata = JukeboxCatalog.parseMetadata(bundledCatalogFile().readText())!!
        val smwMeta = metadata.tracks.first { it.gameId == "smw" }
        val unknownTrack = fakeTrack(
            key = "jukebox:unknown:99",
            title = "BGM_Jukebox_Mystery_TestTheme",
            sha256 = "0123456789abcdef".repeat(4),
        )
        val catalog = JukeboxCatalog.buildCatalog(
            jukeboxTracks = listOf(
                fakeTrack(smwMeta.packTrackKey, smwMeta.bwavStem, smwMeta.sha256),
                unknownTrack,
            ),
            metadata = metadata,
        )

        val smw = catalog.gameById("smw")!!
        assertEquals("Super Mario World", smw.localizedName("en"))
        assertEquals("超级马力欧世界", smw.localizedName("zh-CN"))
        assertEquals("超級瑪利歐世界", smw.localizedName("zh-TW"))
        assertEquals("スーパーマリオワールド", smw.localizedName("ja-JP"))

        val searchHits = catalog.search("1990 马力欧世界")
        assertTrue(searchHits.games.any { it.id == "smw" })
        assertTrue(searchHits.trackHits.any { it.second.key == smwMeta.packTrackKey })

        // Unknown tracks still appear cleanly via fallback rather than dropping.
        val fallbackGame = catalog.gameForTrack(unknownTrack.key)
        assertNotNull(fallbackGame)
        assertFalse(catalog.search("Mystery TestTheme").trackHits.isEmpty())
    }
}
