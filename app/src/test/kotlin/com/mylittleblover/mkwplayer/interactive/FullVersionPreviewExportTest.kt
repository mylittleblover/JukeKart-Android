package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.library.StageFlowDiscovery
import com.mylittleblover.mkwplayer.library.StageFlowReader
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import kotlin.test.*
import org.junit.Assume.assumeTrue

/** Export the exact production packet; never reconstruct transitions in preview tooling. */
class FullVersionPreviewExportTest {
    @Test fun exportSameSpeedVersionTransitionsFromProductionPlan() {
        val output=System.getenv("MKW_VERSION_PREVIEW_ROOT")
        assumeTrue(output!=null)
        val root=File(output!!).apply { mkdirs() }
        val catalog=MinfCatalog.parse(File("src/main/assets/interactive_minf.json").readText())
        val jobs=JSONArray()
        for(stage in listOf("Cinema","SnowMountain")) {
            val source=File("/workspace/mkw-audio/sources/Stage_$stage")
            val bytes=source.listFiles()!!.filter { it.extension=="bwav" }.associate { it.name to it.readBytes() }
            val groups=StageFlowDiscovery.group(bytes.map { (n,b)->StageFlowReader.readPart(n,b).getOrThrow() })
            val p=InteractivePlaybackPlan.normal(StageFlowDiscovery.flowFor(StageFlowDiscovery.dominant(groups)!!).getOrThrow(),bytes,catalog)
            val dir=File(root,stage).apply { mkdirs() }
            val graph=File(dir,"production.graph").apply { writeBytes(p.packet) }
            assertEquals(2,p.versions.size)
            for(fast in listOf(false,true)) {
                val a=if(fast) p.versions[0].fast!! else p.versions[0].main
                val b=if(fast) p.versions[1].fast!! else p.versions[1].main
                val segment=p.spec.segments.single { it.index==a }
                val loop=segment.loop!!
                for(reverse in listOf(false,true)) for((edge,at) in listOf("middle" to 120000L,"before-loop" to loop.end-24000,"after-loop" to loop.end+1)) {
                    val from=if(reverse)b else a;val to=if(reverse)a else b
                    val id="${if(fast) "Fast" else "Full"}-${if(reverse) "Pattern2-base" else "base-Pattern2"}-$edge"
                    val duration=at+96000
                    val command=listOf("/workspace/mkw-host-build/mkw_interactive_preview",graph.absolutePath,File(dir,"$id.wav").absolutePath,duration.toString(),p.assetPaths.size.toString())+
                        p.assetPaths.map { File(source,it).absolutePath }+listOf("0,$from","$at,$to")
                    val expected=if(at>=loop.end)loop.start+(at-loop.end) else at
                    jobs.put(JSONObject().put("stage",stage).put("id",id).put("command",JSONArray(command))
                        .put("sourcePreparation",if(p.streamed) "streamed" else "prepared")
                        .put("log",File(dir,"$id.log").absolutePath).put("request",at).put("target",to)
                        .put("expectedSource",expected).put("recordStart",maxOf(0,at-144000)).put("fadeFrames",48000))
                }
            }
        }
        File(root,"jobs.json").writeText(JSONObject().put("cases",jobs).toString(2))
        assertEquals(24,jobs.length())
    }
}
