package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.library.StageFlowDiscovery
import com.mylittleblover.mkwplayer.library.StageFlowReader
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import kotlin.test.*

/** Exports the actual Android playback graph and prequeued commands, without splicing audio. */
class DirectBridgePreviewExportTest {
    @Test fun exportsEveryIndependentBridgeEntry() {
        val output = System.getenv("MKW_DIRECT_BRIDGE_PREVIEW_ROOT")
        assumeTrue(output != null)
        val root = File(output!!).apply { mkdirs() }
        val sources = File(System.getenv("MKW_AUDIO_ROOT") ?: "/workspace/mkw-audio/sources")
        val catalog = MinfCatalog.parse(File("src/main/assets/interactive_minf.json").readText())
        val ff = File("src/main/assets/audio/BGM_Seq_FinalLapFF.bwav")
        val jobs = JSONArray()
        for (directory in sources.listFiles()!!.filter { it.isDirectory && (it.name.startsWith("Stage_") || it.name == "Rainbow") }.sortedBy { it.name }) {
            val stageBytes = directory.listFiles()!!.filter { it.extension == "bwav" }.associate { it.name to it.readBytes() }
            val all = if (ff.isFile) stageBytes + ("BGM_Seq_FinalLapFF.bwav" to ff.readBytes()) else stageBytes
            val plan = if (directory.name == "Rainbow") InteractivePlaybackPlan.rainbow(all, catalog) else {
                val groups = StageFlowDiscovery.group(stageBytes.map { (n, b) -> StageFlowReader.readPart(n, b).getOrThrow() })
                InteractivePlaybackPlan.normal(StageFlowDiscovery.flowFor(StageFlowDiscovery.dominant(groups)!!).getOrThrow(), all, catalog)
            }
            val bridge = plan.directBridge ?: continue
            val destination = plan.entryAutomaticNext.getValue(bridge)
            val segment = plan.spec.segments.single { it.index == bridge }
            val dir = File(root, directory.name).apply { mkdirs() }
            val graph = File(dir, "production.graph").apply { writeBytes(plan.packet) }
            val result = File(dir, "Bridge-to-Main.wav")
            val command = listOf("/workspace/mkw-host-build/mkw_interactive_preview", graph.absolutePath, result.absolutePath,
                (segment.exit + 240000L).toString(), plan.assetPaths.size.toString()) +
                plan.assetPaths.map { name -> if (name == "BGM_Seq_FinalLapFF.bwav") ff.absolutePath else File(directory, name).absolutePath } +
                listOf("0,$bridge", "0,$destination")
            val prepared = plan.preparedAssets + plan.entryPreparedAssets[bridge].orEmpty()
            jobs.put(JSONObject().put("stage", directory.name).put("command", JSONArray(command))
                .put("sourcePreparation", if (plan.streamed) "streamed" else "prepared")
                .put("preparedIndices", JSONArray(plan.assetPaths.indices.filter { plan.assetPaths[it] in prepared }))
                .put("bridge", bridge).put("destination", destination).put("expectedSync", segment.exit)
                .put("output", result.absolutePath).put("log", File(dir, "Bridge-to-Main.log").absolutePath))
        }
        File(root, "jobs.json").writeText(JSONObject().put("cases", jobs).toString(2))
        assertTrue(jobs.length() > 0)
    }
}
