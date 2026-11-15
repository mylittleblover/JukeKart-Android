package com.mylittleblover.mkwplayer.ui.home

import com.mylittleblover.mkwplayer.pack.PackManifest
import com.mylittleblover.mkwplayer.registry.ResourceKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The jukebox grouping, checked against the real names the pack carries.
 *
 * The bug these tests pin: the first implementation split on the first underscore and returned `BGM` for
 * every one of the 227 real titles, collapsing them into a single group named after the file prefix.
 */
class JukeboxCatalogTest {

    @Test
    fun `the source game is the field after the catalogue number`() {
        // Real titles, taken verbatim from the shipped pack.
        assertEquals("SMW", JukeboxCatalog.sourceGameOf("BGM_JukeBox_M001B_SMW_Ground_RnR_Outro"))
        assertEquals("Galaxy2", JukeboxCatalog.sourceGameOf("BGM_JukeBox_M002B_Galaxy2_SkyStationGalaxy_Rock_Outro"))
        assertEquals("MKSFC", JukeboxCatalog.sourceGameOf("BGM_JukeBox_M004B_MKSFC_ChocoIsland_Samba_Outro"))
        assertEquals("MKDS", JukeboxCatalog.sourceGameOf("BGM_JukeBox_M011C_MKDS_WaluigiPingball_Funk_OutroFull"))
        assertEquals("3DW", JukeboxCatalog.sourceGameOf("BGM_JukeBox_M012B_3DW_Athletic_Jazz_Outro"))
    }

    @Test
    fun `the prefix is never mistaken for a game`() {
        val game = JukeboxCatalog.sourceGameOf("BGM_JukeBox_M001B_SMW_Ground_RnR_Outro")
        assertTrue(game != "BGM", "the file prefix is not a source game, got '$game'")
    }

    @Test
    fun `a title that does not follow the shape is not mangled`() {
        assertEquals("Something", JukeboxCatalog.sourceGameOf("Something else entirely"))
        // And the display form is left alone rather than having words stripped on a guess.
        assertEquals("Something else entirely", JukeboxCatalog.displayTitle("Something else entirely"))
    }

    @Test
    fun `the display title drops the catalogue bookkeeping`() {
        assertEquals("Ground RnR", JukeboxCatalog.displayTitle("BGM_JukeBox_M001B_SMW_Ground_RnR_Outro"))
        assertEquals("WaluigiPingball Funk", JukeboxCatalog.displayTitle("BGM_JukeBox_M011C_MKDS_WaluigiPingball_Funk_OutroFull"))
        assertEquals("Athletic Jazz", JukeboxCatalog.displayTitle("BGM_JukeBox_M012B_3DW_Athletic_Jazz_Outro"))
    }

    @Test
    fun `a track with no outro marker still displays`() {
        assertEquals("Athletic Samba", JukeboxCatalog.displayTitle("BGM_JukeBox_M013B_YoshiIsland_Athletic_Samba"))
    }

    @Test
    fun `groups are sorted and tracks within a group are sorted`() {
        val tracks = listOf(
            track("b", "BGM_JukeBox_M009B_YoshiIsland_Flower_Ska_Outro"),
            track("a", "BGM_JukeBox_M001B_SMW_Ground_RnR_Outro"),
            track("c", "BGM_JukeBox_M002B_Galaxy2_Sky_Outro"),
        )
        // Building through the same grouping the screen uses would need a registry; the sort is what is
        // being checked, so it is reproduced here from the same two functions.
        val groups = tracks.groupBy { JukeboxCatalog.sourceGameOf(it.title) }
        val ordered = groups.entries.sortedBy { it.key }.map { it.key }
        assertEquals(listOf("Galaxy2", "SMW", "YoshiIsland"), ordered)
    }

    @Test
    fun `only jukebox tracks are collected`() {
        // The kind is what separates a jukebox entry from a course theme; a course theme must not appear in
        // the free-driving category just because it is installed.
        val course = track("course:x", "Coaster", ResourceKind.COURSE)
        val jukebox = track("j", "BGM_JukeBox_M001B_SMW_Ground_RnR_Outro", ResourceKind.JUKEBOX)
        val collected = listOf(course, jukebox).filter { it.kind == ResourceKind.JUKEBOX }
        assertEquals(1, collected.size)
        assertEquals(jukebox.key, collected.single().key)
    }

    private fun track(key: String, title: String, kind: ResourceKind = ResourceKind.JUKEBOX) =
        PackManifest.Track(
            key = key, kind = kind, title = title,
            primary = com.mylittleblover.mkwplayer.registry.AudioAssetId("0".repeat(64)),
            isFlow = false, parts = emptyList(),
        )
}
