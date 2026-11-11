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
    @Test fun recordWestCityPreShortBridgeAndEveryConfirmedOutroPoint() = recordStage("WestCity")
    @Test fun recordIceBuildingsInternalEntryAndEveryTransition() = recordStage("IceBuildings")
    @Test fun recordFirstCircuitLayeredBridgeAndEveryTransition() = recordStage("FirstCircuit")

    private fun recordStage(stage:String) {
        val source=System.getenv("MKW_AUDIO_ROOT")
        val runner=System.getenv("MKW_NATIVE_PREVIEW")
        assumeTrue("Real audio/native executable not configured",source!=null && runner!=null)
        val dir=File(source!!,"Stage_$stage")
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
        val flow=StageFlow("BGM_Stage_$stage",parts,StageFlow.DEFAULT_TIMING)
        val digests=files.associate { it.name to MessageDigest.getInstance("SHA-256").digest(it.readBytes()) }
        val c=MinfCatalog.parse(File("src/main/assets/interactive_minf.json").readText())
        val p=MinfInteractiveCompiler.prepare(flow,digests,c)
        val out=File(System.getenv("MKW_PREVIEW_OUTPUT") ?: error("MKW_PREVIEW_OUTPUT required"),stage).apply { mkdirs() }
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
            if(stage=="FirstCircuit" && name.endsWith("-Bridge-Full")) {
                assertTrue(log.contains("destinationEntry=$expectedSync firstStart=${expectedSync!!-9389}"),log)
                val target=p.compiled.segmentByRole.getValue(StageFlow.Role.Main)
                val voice=Regex("voice segment=$target at=(\\d+) source=(\\d+)").find(log) ?: error(log)
                val observedOrigin=voice.groupValues[2].toLong()-(voice.groupValues[1].toLong()-(expectedSync-9389))
                assertTrue(kotlin.math.abs(observedOrigin)<=1,"Full must actually start at source zero: $log")
                val bridge=p.compiled.segmentByRole.getValue(StageFlow.Role.Bridge)
                assertTrue(log.contains("voice segment=$bridge "),"Layered Bridge must actually render: $log")
            }
            if(stage=="IceBuildings" && name.endsWith("-Bridge-Full")) {
                val target= p.compiled.segmentByRole.getValue(StageFlow.Role.Main)
                val voice=Regex("voice segment=$target at=(\\d+) source=(\\d+)").find(log) ?: error(log)
                val observedOrigin=voice.groupValues[2].toLong()-(voice.groupValues[1].toLong()-(expectedSync!!+68571))
                assertTrue(kotlin.math.abs(observedOrigin-34286)<=1,"Expected actual Full source 34286, got $observedOrigin: $log")
            }
        }
        val roles=p.compiled.segmentByRole
        for(role in listOf(StageFlow.Role.Pre,StageFlow.Role.PreShort)) {
            val r=c.part(stage,if(role==StageFlow.Role.Pre) "Pre" else "Pre_Short")!!
            val g=r.loopCandidates.first { it>100000 }
            val bridge=c.part(stage,"Bridge")!!
            val bridgeExit=bridge.event("Bridge_ToFull")!!.single()
            record("${role.name}-Bridge-Full",g+bridgeExit+480000,listOf("0,${roles.getValue(role)}","1,${roles.getValue(StageFlow.Role.Main)}"),g)
        }
        val fullPhase=0L
        val g=c.part(stage,"Full")!!.loopCandidates.first { it>48000 }
        record("Full-Fast",g+480000,listOf("0,${roles.getValue(StageFlow.Role.Main)}","1,${roles.getValue(StageFlow.Role.Fast)}"),g)
        if(stage=="WestCity") {
            val late=c.part(stage,"Pre")!!.loopCandidates.first()
            record("Pre-late-request-next-exit",late+90000+480000,listOf("0,${roles.getValue(StageFlow.Role.Pre)}","${late-1000},${roles.getValue(StageFlow.Role.Main)}"),late+90000)
        }
        for((n,points) in ConfirmedOutroCatalog.forStage(stage)!!) for((i,point) in points.withIndex()) {
            val outro=flow.outro(n)!!
            // Issue before this particular confirmed point but after any earlier point of the same Outro.
            val request=maxOf(1,point-fullPhase-48000)
            record("Full-Outro${n.toString().padStart(2,'0')}-point${i+1}-$point",point-fullPhase+outro.frames+48000,
                listOf("0,${roles.getValue(StageFlow.Role.Main)}","$request,${p.compiled.segmentByOutro.getValue(n)}"),point)
        }
    }
}
