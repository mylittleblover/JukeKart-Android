package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.library.StageFlowDiscovery
import com.mylittleblover.mkwplayer.library.StageFlowReader
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import kotlin.test.*
import org.junit.Assume.assumeTrue

class DonkeyRegionPreviewExportTest {
    @Test fun exportsForwardProgressionAndItsLoopBoundaryCases() {
        val output=System.getenv("MKW_DONKEY_PREVIEW_ROOT")
        assumeTrue(output!=null)
        val root=File(output!!).apply { mkdirs() }
        val source=File("/workspace/mkw-audio/sources/Stage_DonkeyKong")
        val bytes=source.listFiles()!!.filter { it.extension=="bwav" }.associate { it.name to it.readBytes() }
        val catalog=MinfCatalog.parse(File("src/main/assets/interactive_minf.json").readText())
        val groups=StageFlowDiscovery.group(bytes.map { (n,b)->StageFlowReader.readPart(n,b).getOrThrow() })
        val p=InteractivePlaybackPlan.normal(StageFlowDiscovery.flowFor(StageFlowDiscovery.dominant(groups)!!).getOrThrow(),bytes,catalog)
        assertEquals(5,p.regions.size)
        val graph=File(root,"production.graph").apply { writeBytes(p.packet) }
        val jobs=JSONArray()
        for((number,from) in p.regions) {
            val segment=p.spec.segments.single { it.index==from }
            for((targetNumber,to) in (if(number==5) mapOf(6 to p.fast!!) else p.regions.filterKeys { it==number+1 })) {
                for((edge,at) in listOf("early" to 100000L,"loop-end" to segment.loop!!.end-1024)) {
                    val g=segment.cues.filter { it.name=="region.exit" && it.frame>at }.minOf { it.frame }
                    val id="Region$number-Region$targetNumber-$edge"
                    val duration=g+240000
                    val command=listOf("/workspace/mkw-host-build/mkw_interactive_preview",graph.absolutePath,File(root,"$id.wav").absolutePath,duration.toString(),p.assetPaths.size.toString())+
                        p.assetPaths.map { File(source,it).absolutePath }+listOf("0,$from","$at,$to")
                    jobs.put(JSONObject().put("stage","DonkeyKong").put("id",id).put("command",JSONArray(command))
                        .put("sourcePreparation",if(p.streamed) "streamed" else "prepared").put("source",from).put("target",to)
                        .put("log",File(root,"$id.log").absolutePath).put("request",at).put("expectedSync",g)
                        .put("expectedSource",0).put("recordStart",maxOf(0,g-144000)).put("fromRegion",number).put("toRegion",targetNumber))
                }
            }
        }
        File(root,"jobs.json").writeText(JSONObject().put("cases",jobs).toString(2))
        assertEquals(10,jobs.length())
    }
}
