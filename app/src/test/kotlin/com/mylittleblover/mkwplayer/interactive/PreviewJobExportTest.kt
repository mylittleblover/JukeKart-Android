package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.audio.BwavHeader
import com.mylittleblover.mkwplayer.library.StageFlow
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import java.io.File
import java.security.MessageDigest
import kotlin.test.Test

/** Export immutable native jobs once; parallel workers never compete over Gradle/compiler state. */
class PreviewJobExportTest {
    @Test fun exportAllOrdinaryAndConfirmedPatternJobs() {
        val root=System.getenv("MKW_AUDIO_ROOT")
        val runner=System.getenv("MKW_NATIVE_PREVIEW")
        val output=System.getenv("MKW_PREVIEW_JOBS")
        assumeTrue(root!=null && runner!=null && output!=null)
        val c=MinfCatalog.parse(File("src/main/assets/interactive_minf.json").readText())
        val stages=c.records.map { it.stage }.distinct().filter { it!="Rainbow" } + listOf("Cinema_Pattern2","SnowMountain_Pattern2")
        val filter=System.getenv("MKW_STAGE_FILTER")?.split(",")?.toSet()
        val all=JSONArray()
        fun sha(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        for(stage in stages.filter { filter==null || it in filter }) {
            val status=JSONObject().put("stage",stage)
            try {
                val base=stage.substringBefore("_Pattern")
                val dir=File(root,"Stage_$base")
                val confirmed=ConfirmedOutroCatalog.forStage(stage) ?: error("No confirmed Outro mapping")
                val files=dir.listFiles()!!.filter { f ->
                    f.extension=="bwav" && when {
                        f.name.contains("_Outro") -> Regex("Outro(\\d+)").find(f.name)?.groupValues?.get(1)?.toInt() in confirmed
                        stage.contains("_Pattern") -> f.name=="BGM_Stage_$stage.bwav" ||
                            f.name=="BGM_Stage_${stage.substringBefore("_Pattern")}_Fast_Pattern${stage.substringAfter("_Pattern")}.bwav"
                        f.name.contains("_Pattern") -> false
                        else -> true
                    }
                }.sortedBy { it.name }
                val parts=files.map { f ->
                    val h=(BwavHeader.read(f.readBytes()) as BwavHeader.Result.Ok).info
                    val role=when {
                        f.name.contains("Pre_Short") -> StageFlow.Role.PreShort
                        f.name.contains("_Pre") -> StageFlow.Role.Pre
                        f.name.contains("_Bridge") -> StageFlow.Role.Bridge
                        f.name.contains("_Outro") -> StageFlow.Role.Outro
                        f.name.contains("_Fast") -> StageFlow.Role.Fast
                        else -> StageFlow.Role.Main
                    }
                    StageFlow.Part(role,f.name,h.frames.toLong(),h.sampleRate,h.channels,h.loopStart?.toLong(),h.loopEnd?.toLong(),
                        Regex("Outro(\\d+)").find(f.name)?.groupValues?.get(1)?.toInt())
                }
                var flow=StageFlow("BGM_Stage_$stage",parts,StageFlow.DEFAULT_TIMING)
                val digests=files.associate { it.name to MessageDigest.getInstance("SHA-256").digest(it.readBytes()) }
                val blockers=JSONArray()
                val prepared=try { MinfInteractiveCompiler.prepare(flow,digests,c) } catch(e:Exception) {
                    blockers.put("Entry graph: ${e.message}")
                    flow=flow.copy(parts=parts.filter { it.role!=StageFlow.Role.Pre && it.role!=StageFlow.Role.PreShort && it.role!=StageFlow.Role.Bridge })
                    MinfInteractiveCompiler.prepare(flow,digests,c)
                }
                val out=File(output,stage).apply { mkdirs() }
                val packet=File(out,"interactive.graph").apply { writeBytes(prepared.compiled.packet) }
                val compiled=prepared.compiled
                val roles=compiled.segmentByRole
                val paths=compiled.assetPaths.map { File(dir,it).absolutePath }
                val recordings=File(System.getenv("MKW_PREVIEW_OUTPUT"),stage).apply { mkdirs() }
                val cases=JSONArray()
                fun job(name:String,duration:Long,actions:List<String>,sync:Long,start:Long,origins:List<Triple<Int,Long,Long>>,basis:String) {
                    val wav=File(recordings,"$name.wav")
                    cases.put(JSONObject().put("id",name).put("wav",wav.absolutePath).put("log",File(recordings,"$name.log").absolutePath)
                        .put("command",JSONArray(listOf(runner,packet.absolutePath,wav.absolutePath,duration.toString(),paths.size.toString())+paths+actions))
                        .put("env",JSONObject().put("MKW_RECORD_START",maxOf(0,start).toString()))
                        .put("expectedSync",sync).put("basis",basis)
                        .put("sourceOrigins",JSONArray(origins.map { (segment,at,source) -> JSONObject().put("segment",segment).put("start",at).put("source",source) })))
                }
                for(role in listOf(StageFlow.Role.Pre,StageFlow.Role.PreShort)) if(role in roles) {
                    val meta=c.part(stage,if(role==StageFlow.Role.Pre) "Pre" else "Pre_Short")!!
                    val rule=prepared.spec.transitions.first { it.sources==listOf(roles.getValue(role)) }
                    val timeline=rule.timeline!!
                    val lead=maxOf(0,-timeline.destinationStart,if(rule.bridge>=0) -timeline.bridgeStart else 0)
                    val g=meta.loopCandidates.first { it>lead+1 }
                    val origins=mutableListOf(Triple(roles.getValue(StageFlow.Role.Main),g+timeline.destinationStart,timeline.destinationSource))
                    if(rule.bridge>=0)origins.add(Triple(rule.bridge,g+timeline.bridgeStart,0))
                    job("${role.name}-${if(rule.bridge>=0) "Bridge-" else ""}Full",g+timeline.destinationCommit+480000,
                        listOf("0,${roles.getValue(role)}","1,${roles.getValue(StageFlow.Role.Main)}"),g,
                        g+minOf(timeline.destinationStart,if(rule.bridge>=0)timeline.bridgeStart else 0)-144000,origins,
                        if(stage=="WestCity" && role==StageFlow.Role.Pre) "USER_REPORTED_WESTCITY_33" else prepared.entryBasis)
                }
                roles[StageFlow.Role.Fast]?.let { fast ->
                    val phase=c.part(stage,"Fast")!!.phase
                    val g=c.part(stage,"Full")!!.loopCandidates.first { it>phase+1 }
                    job("Full-Fast",g+480000,listOf("0,${roles.getValue(StageFlow.Role.Main)}","1,$fast"),g,g-phase-144000,
                        listOf(Triple(fast,g-phase,0)),"SOURCE_GRID_PHASE_CANDIDATE")
                }
                for((n,points) in confirmed) for((i,point) in points.withIndex()) {
                    val outro=flow.outro(n) ?: error("Confirmed Outro$n audio missing")
                    job("Full-Outro${n.toString().padStart(2,'0')}-point${i+1}-$point",point+outro.frames+48000,
                        listOf("0,${roles.getValue(StageFlow.Role.Main)}","${maxOf(1,point-48000)},${compiled.segmentByOutro.getValue(n)}"),point,point-144000,
                        listOf(Triple(compiled.segmentByOutro.getValue(n),point,0)),"USER_CONFIRMED_FULL_EXIT_MAPPING")
                }
                status.put("status",if(blockers.length()==0) "JOBS_READY_NOT_RENDERED" else "PARTIAL_JOBS_READY")
                    .put("blockers",blockers).put("graph",packet.absolutePath).put("graphSha256",sha(packet.readBytes()))
                    .put("nativeSha256",sha(File(runner!!).readBytes())).put("cases",cases)
                File(out,"jobs.json").writeText(status.toString(2))
            } catch(e:Exception) { status.put("status","BLOCKED_EXPORT").put("error",e.toString()) }
            all.put(status)
        }
        File(output,if(filter==null) "index.json" else "index-filtered.json").writeText(JSONObject().put("stages",all).toString(2))
    }
}
