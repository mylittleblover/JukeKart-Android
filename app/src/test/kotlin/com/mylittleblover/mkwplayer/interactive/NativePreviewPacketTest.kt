package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.audio.BwavHeader
import com.mylittleblover.mkwplayer.graph.MusicGraphBuilder
import com.mylittleblover.mkwplayer.library.StageFlow
import java.io.File
import java.security.MessageDigest
import kotlin.test.*
import org.junit.Assume.assumeTrue

/** Opt-in integration test: real user assets and the same Native Engine, never synthetic audio. */
class NativePreviewPacketTest {
    @Test fun recordWestCityPreShortBridgeAndEveryConfirmedOutroPoint() {
        val source=System.getenv("MKW_AUDIO_ROOT")
        val runner=System.getenv("MKW_NATIVE_PREVIEW")
        assumeTrue("Real audio/native executable not configured",source!=null && runner!=null)
        val dir=File(source!!,"Stage_WestCity")
        val files=dir.listFiles()!!.filter { it.extension=="bwav" }.sortedBy { it.name }
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
            val n=Regex("Outro(\\d+)").find(f.name)?.groupValues?.get(1)?.toInt()
            StageFlow.Part(role,f.name,h.frames.toLong(),h.sampleRate,h.channels,h.loopStart?.toLong(),h.loopEnd?.toLong(),n)
        }
        val flow=StageFlow("BGM_Stage_WestCity",parts,StageFlow.DEFAULT_TIMING)
        val digests=files.associate { it.name to MessageDigest.getInstance("SHA-256").digest(it.readBytes()) }
        val c=MinfCatalog.parse(File("src/main/assets/interactive_minf.json").readText())
        val p=MinfInteractiveCompiler.prepare(flow,digests,c)
        val out=File(System.getenv("MKW_PREVIEW_OUTPUT") ?: error("MKW_PREVIEW_OUTPUT required"),"WestCity").apply { mkdirs() }
        val packet=File(out,"interactive.graph").apply { writeBytes(p.compiled.packet) }
        val paths=p.compiled.assetPaths.map { File(dir,it).absolutePath }
        fun record(name:String,frames:Long,actions:List<String>,expectedSync:Long?=null) {
            val cmd=listOf(runner!!,packet.absolutePath,File(out,"$name.wav").absolutePath,frames.toString(),paths.size.toString())+paths+actions
            val builder=ProcessBuilder(cmd).redirectErrorStream(true)
            builder.environment()["MKW_RECORD_START"]=maxOf(0,(expectedSync ?: 0)-144000).toString()
            val process=builder.start()
            val log=process.inputStream.bufferedReader().readText()
            File(out,"$name.log").writeText(log)
            assertEquals(0,process.waitFor(),log)
            assertTrue(File(out,"$name.wav").length()>44)
            assertTrue(log.contains("clipped=0"),log)
            if(expectedSync!=null) assertTrue(log.contains("sourceSync=$expectedSync "),log)
        }
        val roles=p.compiled.segmentByRole
        for(role in listOf(StageFlow.Role.Pre,StageFlow.Role.PreShort)) {
            val r=c.part("WestCity",if(role==StageFlow.Role.Pre) "Pre" else "Pre_Short")!!
            val g=r.loopCandidates.first { it>100000 }
            record("${role.name}-Bridge-Full",g+480000,listOf("0,${roles.getValue(role)}","1,${roles.getValue(StageFlow.Role.Main)}"),g)
        }
        val fullPhase=0L
        val g=c.part("WestCity","Full")!!.loopCandidates.first { it>48000 }
        record("Full-Fast",g+480000,listOf("0,${roles.getValue(StageFlow.Role.Main)}","1,${roles.getValue(StageFlow.Role.Fast)}"),g)
        val late=c.part("WestCity","Pre")!!.loopCandidates.first()
        record("Pre-late-request-next-exit",late+90000+480000,listOf("0,${roles.getValue(StageFlow.Role.Pre)}","${late-1000},${roles.getValue(StageFlow.Role.Main)}"),late+90000)
        for((n,points) in ConfirmedOutroCatalog.forStage("WestCity")!!) for((i,point) in points.withIndex()) {
            val outro=flow.outro(n)!!
            // Issue before this particular confirmed point but after any earlier point of the same Outro.
            val request=maxOf(1,point-fullPhase-48000)
            record("Full-Outro${n.toString().padStart(2,'0')}-point${i+1}-$point",point-fullPhase+outro.frames+48000,
                listOf("0,${roles.getValue(StageFlow.Role.Main)}","$request,${p.compiled.segmentByOutro.getValue(n)}"),point)
        }
    }
}
