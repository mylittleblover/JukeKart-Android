package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.graph.StageFlowCompiler
import com.mylittleblover.mkwplayer.pack.PackInstaller
import com.mylittleblover.mkwplayer.pack.PackPlayback
import java.io.File
import java.nio.file.Files
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import kotlin.test.*

/** Opt-in acceptance of the exact artifact being delivered, using the actual installer. */
class InteractivePackAcceptanceTest {
    @Test fun aKnownStandalonePatternWithoutOutrosKeepsItsMeasuredLoop() {
        val catalog=MinfCatalog.parse(File("src/main/assets/interactive_minf.json").readText())
        val r=catalog.part("DonkeyKong_Pattern2","Full")!!
        val part=com.mylittleblover.mkwplayer.library.StageFlow.Part(
            com.mylittleblover.mkwplayer.library.StageFlow.Role.Main,"pattern.bwav",r.loopEnd,r.rate,2,r.loopStart,r.loopEnd)
        val flow=com.mylittleblover.mkwplayer.library.StageFlow("BGM_Stage_DonkeyKong_Pattern2",listOf(part),
            com.mylittleblover.mkwplayer.library.StageFlow.DEFAULT_TIMING)
        val p=MinfInteractiveCompiler.prepare(flow,mapOf(part.fileName to ByteArray(32)),catalog)
        assertTrue(p.spec.transitions.isEmpty());assertEquals(r.loopStart,p.spec.segments.single().loop!!.start)
    }
    @Test fun realPackInstallsAndEveryDeclaredTrackPreparesItsPlaybackGraph() {
        val path=System.getenv("MKW_TEST_PACK")
        assumeTrue(path!=null && File(path!!).isFile)
        val root=Files.createTempDirectory("mkw-real-pack").toFile()
        try {
            val installed=PackInstaller.install(File(path!!),root)
            assertIs<PackInstaller.Result.Installed>(installed,installed.toString())
            val manifest=installed.manifest
            assertEquals(30,manifest.stages.size);assertEquals(328,manifest.assets.size);assertEquals(81,manifest.tracks.size)
            val identities=InteractiveAudioIdentities.parse(File("src/main/assets/interactive_audio_identities.json").readText())
            val catalog=MinfCatalog.parse(File("src/main/assets/interactive_minf.json").readText())
            val report=JSONArray()
            for(track in manifest.tracks) {
                val prepared=PackPlayback.prepare(manifest,track,installed.directory)
                assertIs<PackPlayback.Result.Ok>(prepared,prepared.toString())
                val expanded=PackInteractiveSources.resolve(manifest,track,installed.directory,prepared.playable,identities)
                val packet=when(expanded) {
                    is PackInteractiveSources.Resolved.Rainbow -> InteractivePlaybackPlan.rainbow(expanded.bytes,catalog).packet
                    is PackInteractiveSources.Resolved.Normal -> {
                        if(catalog.part(expanded.flow.name.removePrefix("BGM_Stage_"),"Full")!=null)
                            InteractivePlaybackPlan.normal(expanded.flow,expanded.bytes,catalog).packet
                        else StageFlowCompiler.compileFromBytes(expanded.flow,expanded.bytes).packet
                    }
                    null -> StageFlowCompiler.compileFromBytes(prepared.playable.flow,prepared.playable.bytes).packet
                }
                assertTrue(packet.size>16,"Empty playback graph: ${track.title}")
                report.put(JSONObject().put("key",track.key).put("title",track.title).put("packetBytes",packet.size)
                    .put("kind",expanded?.javaClass?.simpleName ?: "Standalone").put("status","PACK_INSTALL_AND_GRAPH_PASS"))
            }
            File(System.getenv("MKW_PACK_REPORT") ?: "/workspace/mkw-planning/interactive-pack-81-tracks.json").writeText(report.toString(2))
        } finally { root.deleteRecursively() }
    }
}
