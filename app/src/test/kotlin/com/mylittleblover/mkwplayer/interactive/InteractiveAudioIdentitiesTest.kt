package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.library.StageFlow
import com.mylittleblover.mkwplayer.library.StageFlowReader
import java.io.File
import org.junit.Assume.assumeTrue
import kotlin.test.*

class InteractiveAudioIdentitiesTest {
    @Test fun packDisplayTitlesAndRenamedAssetsStillSelectTheirExactResearchGraph() {
        val dir=File("/workspace/mkw-audio/sources/Stage_KoopaTroopaBeach")
        assumeTrue(dir.isDirectory)
        val bytes=dir.listFiles()!!.associate { it.name to it.readBytes() }
        val parts=bytes.map { (name,data) -> StageFlowReader.readPart(name,data).getOrThrow() }
        val renamed=parts.mapIndexed { i,p -> p.copy(fileName="packed-$i.bwav") }
        val packed=parts.zip(renamed).associate { (original,copy) -> copy.fileName to bytes.getValue(original.fileName) }
        val flow=StageFlow("用户自定义标题",renamed,StageFlow.DEFAULT_TIMING)
        val identities=InteractiveAudioIdentities.parse(File("src/main/assets/interactive_audio_identities.json").readText())
        val resolved=identities.resolve(flow,packed)!!
        assertEquals("用户自定义标题",resolved.title)
        assertEquals("BGM_Stage_KoopaTroopaBeach",resolved.flow.name)
        assertEquals(parts.map { it.fileName }.toSet(),resolved.bytes.keys)
        val catalog=MinfCatalog.parse(File("src/main/assets/interactive_minf.json").readText())
        val plan=InteractivePlaybackPlan.normal(resolved.flow,resolved.bytes,catalog)
        assertEquals(23226L,plan.spec.transitions.first().timeline!!.destinationStart)
    }
}
