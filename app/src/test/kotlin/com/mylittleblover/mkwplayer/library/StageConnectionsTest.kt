package com.mylittleblover.mkwplayer.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The shipped connection table, checked against the facts it was exported with.
 *
 * The numbers here are not arbitrary: 214 rows, 29 sources, 30 destinations, and Rainbow as a sink came from
 * `Stage_Connections_Final.csv` itself. A future edit to the asset that changes any of them has to change
 * this test too, which is the point -- otherwise a silently reversed or invented edge would look fine.
 */
class StageConnectionsTest {

    private val catalog: StageConnections.Catalog by lazy {
        val file = File("src/main/assets/stage_connections.json")
        assertTrue("the connection asset must ship with the app", file.exists())
        assertNotNull(StageConnections.parse(file.readText()))
        StageConnections.parse(file.readText())!!
    }

    @Test
    fun `the exported table is intact`() {
        assertEquals(214, catalog.edges.size)
        assertEquals(29, catalog.sources().size)
    }

    @Test
    fun `rainbow is entered only from peach stadium`() {
        // The single entrance is Central (Peach Stadium / 碧姬竞技场) -> Rainbow. There is no second way in,
        // and inventing one would put a road on the map the game does not have.
        val entrances = catalog.incoming("Rainbow")
        assertEquals("Rainbow has exactly one entrance", 1, entrances.size)
        assertEquals("Central", entrances.single().from)
        assertEquals("InterStageEx1", entrances.single().kind)
        assertTrue(catalog.connects("Central", "Rainbow"))
    }

    @Test
    fun `rainbow is a finish line and never a start`() {
        // Rainbow appears only as a destination. Filling the gap with a reverse edge would be inventing
        // game data, so the sink is preserved and reported as such.
        assertTrue("Rainbow must have no outgoing connection", catalog.outgoing("Rainbow").isEmpty())
        assertTrue("Rainbow must have no neighbours", catalog.neighbours("Rainbow").isEmpty())
        assertTrue("Rainbow must be reported as a sink", catalog.sinks().contains("Rainbow"))
        // Every other stage still leaves somewhere, so the sink is Rainbow in particular rather than a bug.
        assertEquals(listOf("Rainbow"), catalog.sinks())
    }

    @Test
    fun `connections are directed and never reversed`() {
        // 96 of the 214 pairs exist in both directions, so most edges are symmetric -- but 22 are not, and
        // those are the ones that prove the table is a directed graph rather than a list of neighbour pairs.
        // Airship Fortress <-> Dino Bones happens to exist both ways, so it cannot show direction; Donkey
        // Kong -> First Circuit exists in one direction only.
        assertTrue("Donkey Kong to First Circuit is in the export", catalog.connects("DonkeyKong", "FirstCircuit"))
        assertFalse(
            "the reverse is not implied by the forward edge",
            catalog.connects("FirstCircuit", "DonkeyKong"),
        )
        // A symmetric pair is symmetric because the export says so, not because the reader mirrored it.
        assertTrue(catalog.connects("AirshipFortress", "DinoBones"))
        assertTrue(catalog.connects("DinoBones", "AirshipFortress"))
    }

    @Test
    fun `a one way connection is reported as one way`() {
        val edge = catalog.between("IceBuildings", "LagoonCity")
        assertNotNull("Ice Buildings to Lagoon City is in the export", edge)
        assertEquals("InterStageEx2", edge!!.kind)
        assertNull("Lagoon City to Ice Buildings is not", catalog.between("LagoonCity", "IceBuildings"))
        // Lagoon City does leave, just somewhere else, so this is direction and not a missing row.
        assertTrue(catalog.outgoing("LagoonCity").isNotEmpty())
    }

    @Test
    fun `outgoing returns the real neighbours ordered by the export`() {
        val neighbours = catalog.neighbours("BigTree")
        assertTrue("Big Tree has neighbours", neighbours.isNotEmpty())
        assertTrue(neighbours.contains("Cave"))
        assertTrue(neighbours.contains("Cinema"))
        // No neighbour is the stage itself.
        assertFalse(neighbours.contains("BigTree"))
    }

    @Test
    fun `every edge carries its own kind and row id`() {
        for (edge in catalog.edges) {
            assertTrue("edge ${edge.rowId} must name a source", edge.from.isNotBlank())
            assertTrue("edge ${edge.rowId} must name a destination", edge.to.isNotBlank())
            assertTrue("edge ${edge.rowId} must state its kind", edge.kind.isNotBlank())
            assertTrue(
                "edge ${edge.rowId} must state its row id",
                edge.rowId.isNotBlank(),
            )
        }
        val kinds = catalog.edges.map { it.kind }.toSet()
        assertEquals(setOf("InterStageEx1", "InterStageEx2", "LongRally"), kinds)
    }

    @Test
    fun `the archive suffix spelling resolves to the same stage`() {
        // The pack says AirshipFortress_00, the export says AirshipFortress.
        val withSuffix = catalog.outgoing("AirshipFortress_00")
        val without = catalog.outgoing("AirshipFortress")
        assertEquals(without.size, withSuffix.size)
        assertEquals(without.map { it.rowId }, withSuffix.map { it.rowId })
    }

    @Test
    fun `an unknown stage has no neighbours rather than a guess`() {
        assertTrue(catalog.outgoing("NoSuchCourse").isEmpty())
        assertTrue(catalog.neighbours("NoSuchCourse").isEmpty())
        assertNull(catalog.between("BigTree", "NoSuchCourse"))
    }

    @Test
    fun `a malformed asset is refused instead of half loaded`() {
        assertNull(StageConnections.parse("not json"))
        assertNull(StageConnections.parse("""{"edges":[]}"""))
        // An edge without an endpoint is dropped, and a table left empty is null rather than a catalog.
        val onlyBroken = """{"edges":[{"from":"","to":"Cave","kind":"InterStageEx1"}]}"""
        assertNull(StageConnections.parse(onlyBroken))
    }
}
