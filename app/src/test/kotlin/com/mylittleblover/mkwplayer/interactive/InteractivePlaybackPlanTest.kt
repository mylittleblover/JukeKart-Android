package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.library.StageFlowDiscovery
import com.mylittleblover.mkwplayer.library.StageFlowReader
import java.io.File
import kotlin.test.*
import org.junit.Assume.assumeTrue

class InteractivePlaybackPlanTest {
    @Test fun allThirtyRealSourceGroupsPrepareTheGraphsUsedByTheApplication() {
        val root=File(System.getenv("MKW_AUDIO_ROOT") ?: "/workspace/mkw-audio/sources")
        assumeTrue(root.isDirectory)
        val catalog=MinfCatalog.parse(File("src/main/assets/interactive_minf.json").readText())
        var count=0
        for(dir in root.listFiles()!!.filter { it.isDirectory && (it.name.startsWith("Stage_") || it.name=="Rainbow") }) {
            val bytes=dir.listFiles()!!.filter { it.extension=="bwav" }.associate { it.name to it.readBytes() }
            val p=if(dir.name=="Rainbow") InteractivePlaybackPlan.rainbow(bytes,catalog) else {
                val parts=bytes.map { (name,data) -> StageFlowReader.readPart(name,data).getOrThrow() }
                val group=StageFlowDiscovery.dominant(StageFlowDiscovery.group(parts))!!
                InteractivePlaybackPlan.normal(StageFlowDiscovery.flowFor(group).getOrThrow(),bytes,catalog)
            }
            assertTrue(p.packet.isNotEmpty(),dir.name)
            assertTrue(p.assetPaths.all { it in bytes },dir.name)
            assertTrue(p.entry in p.labels,dir.name)
            if(dir.name=="Rainbow") {
                assertTrue(p.streamed);assertEquals(15,p.assetPaths.size)
                assertEquals(5,p.automaticNext.size)
                assertTrue(p.next.values.all { it in p.labels })
            } else {
                assertNotNull(p.fast,"${dir.name}: importer lost Fast arrangement")
                assertNotNull(p.standard)
                assertTrue(p.automaticNext.isEmpty())
            }
            count++
        }
        assertEquals(30,count)
    }
}
