package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.audio.BwavHeader
import com.mylittleblover.mkwplayer.graph.MusicGraphBuilder as G
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Opt-in real Native export for every source Outro_ToNextPre into the next track's Pre. */
class OutroNextPrePreviewExportTest {
    @Test
    fun exportsAll174MarkersAndPreservesNegativeDeadlineFullContext() {
        assumeTrue(
            "set MKW_EXPORT_OUTRO_NEXTPRE=1 to generate Native previews",
            System.getenv("MKW_EXPORT_OUTRO_NEXTPRE") == "1",
        )

        val manifest = exportAllOutroNextPrePreviews()
        assertEquals(174, manifest.getInt("caseCount"))
        assertEquals(174, manifest.getJSONArray("cases").length())
        assertEquals(0, manifest.getInt("failedCount"))

        val negative = manifest.getJSONArray("cases").objects().single {
            it.getString("caseId") == "MooMooFarm-BGM_Stage_MooMooFarm_Outro06-Outro_ToNextPre-01"
        }
        assertEquals(1_501_875L, negative.getLong("fullCueFrame"))
        assertEquals(157_500L, negative.getLong("sourceMarkerFrame"))
        assertEquals(191_400L, negative.getLong("targetPrePhase"))
        assertEquals(-33_900L, negative.getLong("candidateEarlyStartFrame"))
        assertEquals(1_501_875L, negative.getLong("expectedSync"))
        assertEquals(22_905L, negative.getLong("naturalTailFramesAfterMarker"))
        assertTrue(negative.getBoolean("renderedWithFullContext"))
        assertEquals(3, negative.getJSONArray("assets").length())
        assertEquals(0L, negative.origin("full").getLong("source"))
        assertEquals(0L, negative.origin("outro").getLong("source"))
        assertEquals(0L, negative.origin("targetPre").getLong("source"))
        assertEquals(-33_900L, negative.getJSONObject("timeline").getLong("destinationStart"))
        assertEquals(157_500L, negative.getJSONObject("timeline").getLong("destinationCommit"))
        assertTrue(negative.getBoolean("postExit"))

        val first = manifest.getJSONArray("cases").getJSONObject(0)
        assertTrue(first.has("sourceMarkerFrame"))
        assertTrue(first.has("targetPrePhase"))
        assertTrue(first.has("naturalTailFramesAfterMarker"))
        assertEquals("PASS", first.getString("status"))
    }

    private data class Audio(
        val file: File,
        val bytes: ByteArray,
        val info: BwavHeader.Info,
        val digest: ByteArray,
    ) {
        val sha256: String get() = digest.joinToString("") { "%02x".format(it) }
    }

    private data class Origin(val role: String, val segment: Int, val start: Long, val source: Long = 0L)

    private data class Plan(
        val stage: String,
        val nextStage: String,
        val caseId: String,
        val logicalName: String,
        val outroRecord: MinfCatalog.Record,
        val marker: Long,
        val naturalTailFrames: Long,
        val targetRecord: MinfCatalog.Record,
        val outro: Audio,
        val targetPre: Audio,
        val candidateEarlyStart: Long,
        val fullContext: Boolean = false,
        val fullRecord: MinfCatalog.Record? = null,
        val fullCue: Long? = null,
        val full: Audio? = null,
    )

    private data class BuiltCase(
        val plan: Plan,
        val graph: File,
        val job: JSONObject,
        val manifestFields: JSONObject,
        val expectedSync: Long,
        val durationFrames: Long,
        val recordStart: Long,
        val requestFrame: Long,
        val expectedOrigins: List<Origin>,
        val entryFrame: Long,
        val timeline: G.TransitionTimeline,
        val postExit: Boolean,
        val assets: List<Audio>,
        val initialActions: List<String>,
    )

    private data class WavInfo(val channels: Int, val sampleRate: Long, val bits: Int, val frames: Long)

    private fun exportAllOutroNextPrePreviews(): JSONObject {
        val audioRoot = File(System.getenv("MKW_AUDIO_ROOT") ?: "/workspace/mkw-audio/sources")
        val jobsRoot = File(System.getenv("MKW_PREVIEW_JOBS") ?: "/workspace/mkw-preview-jobs", "OutroNextPre")
        val recordingsRoot = File(System.getenv("MKW_PREVIEW_OUTPUT") ?: "/workspace/mkw-recordings", "OutroNextPre")
        val deliveryRoot = File(System.getenv("MKW_DELIVERY") ?: "/workspace/mkw-delivery")
        val catalog = MinfCatalog.parse(File("src/main/assets/interactive_minf.json").readText())
        val nativePins = File(jobsRoot, "native").listFiles().orEmpty()
            .filter { it.isFile && it.name.startsWith("mkw_interactive_preview-") }
        require(nativePins.size == 1) { "Expected one immutable Native pin, found ${nativePins.map { it.name }}" }
        val native = nativePins.single()
        val nativeSha = sha256(native.readBytes())
        require(native.name == "mkw_interactive_preview-$nativeSha") {
            "Immutable Native pin name/hash mismatch: ${native.name} != $nativeSha"
        }

        val order = listOf(
            "FirstCircuit", "WestCity", "Coaster", "DonkeyKong", "DesertHills", "ShyGuyBazaar",
            "WarioStadium", "AirshipFortress", "DKPass", "SnowMountain", "IceBuildings", "WarioShipyard",
            "KoopaTroopaBeach", "DKJungleParkway", "Central", "PeachBeach", "LagoonCity", "DinoDinoJungle",
            "StormClouds", "RaftingRiver", "Cave", "Cinema", "DinoBones", "MooMooFarm", "ChocoMountain",
            "ToadFactory", "Lava", "BigTree", "SFCMarioCircuit", "Rainbow",
        )
        val audioCache = mutableMapOf<String, Audio>()
        fun audio(file: File): Audio = audioCache.getOrPut(file.absolutePath) {
            val bytes = file.readBytes()
            val info = (BwavHeader.read(bytes) as? BwavHeader.Result.Ok)?.info
                ?: error("Unreadable source BWAV: ${file.absolutePath}")
            require(info.sampleRate == 48_000 && info.channels in 1..2) {
                "Unsupported source format for ${file.absolutePath}: ${info.sampleRate} Hz / ${info.channels} ch"
            }
            Audio(file, bytes, info, MessageDigest.getInstance("SHA-256").digest(bytes))
        }
        fun exactSource(logicalName: String, stage: String): Audio {
            val dir = File(audioRoot, if (stage == "Rainbow") "Rainbow" else "Stage_$stage")
            val files = dir.listFiles().orEmpty().filter {
                it.isFile && it.extension == "bwav" && it.nameWithoutExtension == logicalName
            }
            require(files.size == 1) {
                "Expected one exact BWAV for logicalName=$logicalName in ${dir.absolutePath}; found ${files.map { it.name }}"
            }
            return audio(files.single())
        }

        val plans = mutableListOf<Plan>()
        for ((trackIndex, stage) in order.withIndex()) {
            val nextStage = order[(trackIndex + 1) % order.size]
            val targetLogical = if (nextStage == "Rainbow") "BGM_Rainbow_Area1_Pre" else "BGM_Stage_${nextStage}_Pre"
            val target = catalog.records.singleOrNull { it.stage == nextStage && it.logicalName == targetLogical }
                ?: error("Missing unique target Pre identity $targetLogical for $stage -> $nextStage")
            val targetAudio = exactSource(targetLogical, nextStage)
            val dir = File(audioRoot, if (stage == "Rainbow") "Rainbow" else "Stage_$stage")
            val physicalOutros = dir.listFiles().orEmpty().filter {
                it.isFile && it.extension == "bwav" && it.nameWithoutExtension.contains("Outro")
            }.sortedBy { it.name }
            require(physicalOutros.isNotEmpty()) { "No physical Outro BWAVs for $stage" }
            for (physical in physicalOutros) {
                val logical = physical.nameWithoutExtension
                val record = catalog.records.singleOrNull { it.stage == stage && it.logicalName == logical }
                    ?: error("No unique MINF record for exact physical Outro logicalName=$logical")
                val eventRows = record.events.filter { it.name == "Outro_ToNextPre" }
                require(eventRows.size == 1) {
                    "Expected one exact Outro_ToNextPre event record for $logical, found ${eventRows.size}"
                }
                val markers = eventRows.single().frames
                require(markers.isNotEmpty()) { "Outro_ToNextPre exists with no frames for $logical" }
                val outroAudio = audio(physical)
                for ((pointIndex, marker) in markers.withIndex()) {
                    require(marker in 1 until outroAudio.info.frames.toLong()) {
                        "$logical Outro_ToNextPre=$marker is outside BWAV frames=${outroAudio.info.frames}"
                    }
                    val candidateStart = marker - target.phase
                    val special = stage == "MooMooFarm" && logical == "BGM_Stage_MooMooFarm_Outro06"
                    if (candidateStart < 0) {
                        require(special && candidateStart == -33_900L) {
                            "Unmapped negative deadline $stage/$logical marker=$marker targetPhase=${target.phase}; refusing to guess"
                        }
                    }
                    val fullRecord = if (special) catalog.part("MooMooFarm", "Full")
                        ?: error("Missing exact MooMooFarm Full MINF record for negative deadline context") else null
                    val fullCue = if (special) {
                        val frames = fullRecord!!.event("Full_ToOutro06")
                            ?: error("Missing exact MooMooFarm Full_ToOutro06 source marker")
                        require(frames.size == 1) { "MooMooFarm Full_ToOutro06 must have exactly one source marker: $frames" }
                        frames.single()
                    } else null
                    val fullAudio = if (special) exactSource(fullRecord!!.logicalName, "MooMooFarm") else null
                    val suffix = "Outro_ToNextPre-${(pointIndex + 1).toString().padStart(2, '0')}"
                    plans += Plan(
                        stage = stage,
                        nextStage = nextStage,
                        caseId = "$stage-$logical-$suffix",
                        logicalName = logical,
                        outroRecord = record,
                        marker = marker,
                        naturalTailFrames = outroAudio.info.frames - marker,
                        targetRecord = target,
                        outro = outroAudio,
                        targetPre = targetAudio,
                        candidateEarlyStart = candidateStart,
                        fullContext = special,
                        fullRecord = fullRecord,
                        fullCue = fullCue,
                        full = fullAudio,
                    )
                }
            }
        }
        require(plans.size == 174) {
            "Expected all 174 exact Outro marker points; discovered ${plans.size}. No source identity or event was skipped."
        }
        require(plans.map { it.caseId }.toSet().size == plans.size) { "Duplicate generated case identifiers" }

        val outputByStage = order.associateWith { stage ->
            File(recordingsRoot, stage).apply { mkdirs() }
        }
        val jobByStage = order.associateWith { stage ->
            File(jobsRoot, stage).apply { mkdirs() }
        }
        val built = plans.map { plan -> buildCase(plan, native, jobByStage.getValue(plan.stage)) }

        val preparedJobsByStage = order.associateWith { JSONArray() }.toMutableMap()
        for (case in built) preparedJobsByStage.getValue(case.plan.stage).put(case.job)
        for (stage in order) {
            val stageCases = built.filter { it.plan.stage == stage }
            val stageJobs = JSONObject()
                .put("stage", stage)
                .put("status", "JOBS_READY_NOT_RENDERED")
                .put("native", native.absolutePath)
                .put("nativeSha256", nativeSha)
                .put("caseCount", stageCases.size)
                .put("cases", preparedJobsByStage.getValue(stage))
            File(jobByStage.getValue(stage), "jobs.json").writeText(stageJobs.toString(2))
        }

        val executor = Executors.newFixedThreadPool(2)
        val results = try {
            val futures = built.map { item -> executor.submit(Callable { renderCase(item, outputByStage.getValue(item.plan.stage)) }) }
            futures.map { it.get() }
        } finally {
            executor.shutdown()
        }
        val resultById = results.associateBy { it.getString("caseId") }
        val allCases = JSONArray(results)
        val successful = results.count { it.getString("status") == "PASS" }

        val limitations = "由固定 Native 实际渲染器生成；不是 Android 设备录音，也未进行人工听感验收。候选依据只说明素材/源 MINF 同步预览，不代表原版游戏行为已验证。普通 case 从 Outro 源帧 0 起播，若 marker 前可用音频不足 3 秒则保留全部可用上下文并记录实际帧数。Rainbow→FirstCircuit Pre 是跨赛道压力测试，不代表游戏路线事实。"
        val stageSummaries = JSONArray()
        for (stage in order) {
            val stageResults = built.filter { it.plan.stage == stage }.map { resultById.getValue(it.plan.caseId) }
            val stageArray = JSONArray(stageResults)
            val passCount = stageResults.count { it.getString("status") == "PASS" }
            val completedJobs = JSONArray(built.filter { it.plan.stage == stage }.map { item ->
                val result = resultById.getValue(item.plan.caseId)
                JSONObject(item.job.toString())
                    .put("status", result.getString("status"))
                    .put("wavSha256", result.optString("wavSha256"))
                    .put("failures", result.getJSONArray("failures"))
            })
            val stageJobFile = File(jobByStage.getValue(stage), "jobs.json")
            stageJobFile.writeText(JSONObject()
                .put("stage", stage)
                .put("status", if (passCount == stageResults.size) "PASS" else "FAILED")
                .put("native", native.absolutePath)
                .put("nativeSha256", nativeSha)
                .put("caseCount", stageResults.size)
                .put("passed", passCount)
                .put("failed", stageResults.size - passCount)
                .put("cases", completedJobs)
                .toString(2))
            val stageManifest = JSONObject()
                .put("stage", stage)
                .put("nativeSha256", nativeSha)
                .put("caseCount", stageResults.size)
                .put("passed", passCount)
                .put("failed", stageResults.size - passCount)
                .put("cases", stageArray)
                .put("limitations", limitations)
            val recordingDir = outputByStage.getValue(stage)
            File(recordingDir, "render-manifest.json").writeText(stageManifest.toString(2))
            File(recordingDir, "LIMITATIONS.zh-CN.txt").writeText("$limitations\n")

            val stageJob = JSONObject()
                .put("stage", stage)
                .put("status", if (passCount == stageResults.size) "PASS" else "FAILED")
                .put("caseCount", stageResults.size)
                .put("passed", passCount)
                .put("failed", stageResults.size - passCount)
                .put("manifest", File(recordingDir, "render-manifest.json").absolutePath)
                .put("jobs", stageJobFile.absolutePath)
                .put("zip", File(deliveryRoot, "OutroNextPre-$stage.zip").absolutePath)
            stageSummaries.put(stageJob)
            val zip = File(deliveryRoot, "OutroNextPre-$stage.zip")
            writeStageZip(stage, jobByStage.getValue(stage), recordingDir, zip)
            stageJob.put("zipSha256", sha256(zip.readBytes())).put("zipSizeBytes", zip.length())
        }
        val failedCount = results.size - successful
        val manifest = JSONObject()
            .put("task", "Outro_ToNextPre to next track Pre Native preview")
            .put("createdAtUtc", java.time.Instant.now().toString())
            .put("status", if (failedCount == 0) "PASS" else "FAILED")
            .put("trackOrder", JSONArray(order))
            .put("caseCount", results.size)
            .put("passedCount", successful)
            .put("failedCount", failedCount)
            .put("nativeSha256", nativeSha)
            .put("nativePin", native.absolutePath)
            .put("minimumPreMarkerContextFrames", 144_000)
            .put("casesWithLimitedPreMarkerContext", results.count { !it.getBoolean("preMarkerContextRequirementMet") })
            .put("minimumPostEntryFrames", 240_000)
            .put("limitations", limitations)
            .put("stages", stageSummaries)
            .put("cases", allCases)
        recordingsRoot.mkdirs()
        File(recordingsRoot, "render-manifest.json").writeText(manifest.toString(2))
        File(jobsRoot, "index.json").writeText(
            JSONObject().put("nativeSha256", nativeSha).put("caseCount", plans.size).put("stages", stageSummaries).toString(2),
        )
        return manifest
    }

    private fun buildCase(plan: Plan, native: File, jobDir: File): BuiltCase {
        val sourceSegment = 100
        val bridgeSegment = 101
        val targetSegment = if (plan.fullContext) 102 else 101
        val transitionId = 400
        val sourceTrack = 200
        val bridgeTrack = 201
        val targetTrack = if (plan.fullContext) 202 else 201
        val sourceClip = 300
        val bridgeClip = 301
        val targetClip = if (plan.fullContext) 302 else 301
        val cueId = 500
        val assetList = if (plan.fullContext) listOf(plan.full!!, plan.outro, plan.targetPre)
            else listOf(plan.outro, plan.targetPre)
        val assetIndex = assetList.withIndex().associate { it.value.file.absolutePath to it.index }
        val graphAssets = assetList.map { audio ->
            G.Asset(audio.file.name, audio.info.sampleRate, audio.info.channels, audio.info.frames.toLong(), audio.digest)
        }
        val targetLoop = if (plan.targetPre.info.loopStart != null && plan.targetPre.info.loopEnd != null) {
            val start = plan.targetPre.info.loopStart!!.toLong()
            val end = plan.targetPre.info.loopEnd!!.toLong()
            require(start < end && end <= plan.targetPre.info.frames) {
                "Target Pre has invalid actual BWAV loop ${start}..${end} / ${plan.targetPre.info.frames}: ${plan.targetPre.file}"
            }
            G.Loop(start, end)
        } else null
        val timeline = if (plan.fullContext) {
            G.TransitionTimeline(
                bridgeStart = 0,
                destinationStart = plan.candidateEarlyStart,
                destinationCommit = plan.marker,
                destinationSource = 0,
            )
        } else {
            G.TransitionTimeline(
                bridgeStart = 0,
                destinationStart = -plan.targetRecord.phase,
                destinationCommit = 0,
                destinationSource = 0,
            )
        }
        val segments = if (plan.fullContext) {
            val fullCue = plan.fullCue!!
            listOf(
                G.Segment(sourceSegment, exit = fullCue, cues = listOf(
                    G.Cue(cueId, "Full_ToOutro06", fullCue, G.CueKind.Custom),
                )),
                G.Segment(bridgeSegment, exit = plan.marker),
                G.Segment(targetSegment, exit = plan.targetPre.info.frames.toLong(), loop = targetLoop),
            )
        } else {
            listOf(
                G.Segment(sourceSegment, exit = plan.marker),
                G.Segment(targetSegment, exit = plan.targetPre.info.frames.toLong(), loop = targetLoop),
            )
        }
        val tracks = if (plan.fullContext) {
            listOf(
                G.Track(sourceTrack, sourceSegment, listOf(G.Clip(sourceClip, assetIndex.getValue(plan.full!!.file.absolutePath), sourceLength = plan.full!!.info.frames.toLong()))),
                G.Track(bridgeTrack, bridgeSegment, listOf(G.Clip(bridgeClip, assetIndex.getValue(plan.outro.file.absolutePath), sourceLength = plan.outro.info.frames.toLong()))),
                G.Track(targetTrack, targetSegment, listOf(G.Clip(targetClip, assetIndex.getValue(plan.targetPre.file.absolutePath), sourceLength = plan.targetPre.info.frames.toLong()))),
            )
        } else {
            listOf(
                G.Track(sourceTrack, sourceSegment, listOf(G.Clip(sourceClip, assetIndex.getValue(plan.outro.file.absolutePath), sourceLength = plan.outro.info.frames.toLong()))),
                G.Track(targetTrack, targetSegment, listOf(G.Clip(targetClip, assetIndex.getValue(plan.targetPre.file.absolutePath), sourceLength = plan.targetPre.info.frames.toLong()))),
            )
        }
        val transition = if (plan.fullContext) {
            G.Transition(
                index = transitionId,
                sources = listOf(sourceSegment),
                destinations = listOf(targetSegment),
                sync = G.SyncPoint.NextCustomCue,
                sourceCue = "Full_ToOutro06",
                bridge = bridgeSegment,
                postExit = true,
                timeline = timeline,
            )
        } else {
            G.Transition(
                index = transitionId,
                sources = listOf(sourceSegment),
                destinations = listOf(targetSegment),
                sync = G.SyncPoint.ExitCue,
                postExit = true,
                timeline = timeline,
            )
        }
        val bpm = plan.fullRecord?.bpm ?: plan.outroRecord.bpm
        val spec = G.Spec(assets = graphAssets, segments = segments, tracks = tracks, transitions = listOf(transition), bpm = bpm)
        val packet = G.build(spec)
        val packetVersion = ByteBuffer.wrap(packet, 4, 4).order(ByteOrder.BIG_ENDIAN).int
        require(packetVersion == 14) { "Expected G.Spec graph v14, got $packetVersion for ${plan.caseId}" }
        val graph = File(jobDir, "${plan.caseId}.graph").apply { writeBytes(packet) }
        val graphSha = sha256(packet)

        val expectedSync: Long
        val requestFrame: Long
        val recordStart: Long
        val duration: Long
        val entryFrame: Long
        val origins: List<Origin>
        val actions: List<String>
        if (plan.fullContext) {
            val fullCue = plan.fullCue!!
            expectedSync = fullCue
            entryFrame = fullCue + plan.candidateEarlyStart
            requestFrame = entryFrame - 1
            require(requestFrame >= 0) { "Full context request falls before transport start for ${plan.caseId}: $requestFrame" }
            recordStart = maxOf(0L, requestFrame - 144_000L)
            duration = maxOf(
                fullCue + plan.outro.info.frames,
                entryFrame + 240_000L,
            )
            origins = listOf(
                Origin("full", sourceSegment, 0),
                Origin("outro", bridgeSegment, fullCue),
                Origin("targetPre", targetSegment, entryFrame),
            )
            actions = listOf("0,$sourceSegment", "$requestFrame,$targetSegment")
        } else {
            expectedSync = plan.marker
            entryFrame = plan.candidateEarlyStart
            requestFrame = 1
            require(entryFrame > requestFrame) {
                "Cannot meet the request-before-entry deadline for ${plan.caseId}: entry=$entryFrame request=$requestFrame"
            }
            recordStart = maxOf(0L, minOf(plan.marker - 144_000L, entryFrame))
            duration = maxOf(plan.outro.info.frames.toLong(), plan.marker + 240_000L)
            origins = listOf(
                Origin("outro", sourceSegment, 0),
                Origin("targetPre", targetSegment, entryFrame),
            )
            actions = listOf("0,$sourceSegment", "$requestFrame,$targetSegment")
        }
        require(duration > recordStart && entryFrame >= 0) { "Invalid render window for ${plan.caseId}" }
        val wav = File(File(System.getenv("MKW_PREVIEW_OUTPUT") ?: "/workspace/mkw-recordings", "OutroNextPre/${plan.stage}"), "${plan.caseId}.wav")
        val log = File(wav.parentFile, "${plan.caseId}.log")
        val assetPaths = assetList.map { it.file.absolutePath }
        val command = listOf(native.absolutePath, graph.absolutePath, wav.absolutePath, duration.toString(), assetPaths.size.toString()) + assetPaths + actions
        val originJson = JSONArray(origins.map { origin ->
            JSONObject().put("role", origin.role).put("segment", origin.segment).put("start", origin.start).put("source", origin.source)
        })
        val job = JSONObject()
            .put("id", plan.caseId)
            .put("wav", wav.absolutePath)
            .put("log", log.absolutePath)
            .put("graph", graph.absolutePath)
            .put("graphSha256", graphSha)
            .put("command", JSONArray(command))
            .put("env", JSONObject().put("MKW_RECORD_START", recordStart.toString()))
            .put("expectedSync", expectedSync)
            .put("basis", if (plan.fullContext) "SOURCE_MINF_FULL_TOOUTRO06_CUE_WITH_NEGATIVE_PRE_DEADLINE_CONTEXT" else "SOURCE_MINF_EXPLICIT_OUTRO_TO_NEXTPRE_WITH_TARGET_PRE_PHASE")
            .put("sourceOrigins", originJson)
        val manifestFields = JSONObject()
            .put("caseId", plan.caseId)
            .put("stage", plan.stage)
            .put("nextStage", plan.nextStage)
            .put("sourceOutroLogicalName", plan.logicalName)
            .put("sourceOutroPath", plan.outro.file.absolutePath)
            .put("sourceMarkerFrame", plan.marker)
            .put("fullCueFrame", plan.fullCue ?: JSONObject.NULL)
            .put("naturalTailFramesAfterMarker", plan.naturalTailFrames)
            .put("targetPreLogicalName", plan.targetRecord.logicalName)
            .put("targetPrePhase", plan.targetRecord.phase)
            .put("candidateEarlyStartFrame", plan.candidateEarlyStart)
            .put("expectedSync", expectedSync)
            .put("expectedEntryFrame", entryFrame)
            .put("requestFrame", requestFrame)
            .put("recordStartFrame", recordStart)
            .put("durationFrames", duration)
            .put("minimumPostEntryFrames", 240_000)
            .put("availablePreMarkerContextFrames", if (plan.fullContext) requestFrame - recordStart else plan.marker - recordStart)
            .put("preMarkerContextRequirementMet", if (plan.fullContext) requestFrame - recordStart >= 144_000 else plan.marker - recordStart >= 144_000)
            .put("assets", JSONArray(assetList.map { audio ->
                JSONObject().put("path", audio.file.absolutePath).put("sha256", audio.sha256)
                    .put("frames", audio.info.frames).put("sampleRate", audio.info.sampleRate)
                    .put("channels", audio.info.channels).put("loopStart", audio.info.loopStart)
                    .put("loopEnd", audio.info.loopEnd)
            }))
            .put("graph", graph.absolutePath)
            .put("graphSha256", graphSha)
            .put("graphVersion", packetVersion)
            .put("nativeSha256", sha256(native.readBytes()))
            .put("sourceOrigins", originJson)
            .put("timeline", JSONObject().put("bridgeStart", timeline.bridgeStart)
                .put("destinationStart", timeline.destinationStart)
                .put("destinationCommit", timeline.destinationCommit)
                .put("destinationSource", timeline.destinationSource))
            .put("postExit", true)
            .put("renderedWithFullContext", plan.fullContext)
            .put("rainbowCrossTrackPressureTest", plan.stage == "Rainbow")
        return BuiltCase(plan, graph, job, manifestFields, expectedSync, duration, recordStart, requestFrame, origins,
            entryFrame, timeline, postExit = true, assets = assetList, initialActions = actions)
    }

    private fun renderCase(case: BuiltCase, recordingDir: File): JSONObject {
        val wav = File(case.job.getString("wav"))
        val logFile = File(case.job.getString("log"))
        wav.parentFile?.mkdirs()
        logFile.parentFile?.mkdirs()
        val failures = mutableListOf<String>()
        var exitCode = -999
        var logText = ""
        try {
            val process = ProcessBuilder(case.job.getJSONArray("command").strings())
                .redirectErrorStream(true)
                .apply { environment()["MKW_RECORD_START"] = case.recordStart.toString() }
                .start()
            logText = process.inputStream.bufferedReader().use { it.readText() }
            exitCode = process.waitFor()
            logFile.writeText(logText)
        } catch (e: Exception) {
            failures += "Native execution error: ${e.message}"
            logFile.writeText("NATIVE_EXECUTION_ERROR: ${e}\n")
        }
        if (exitCode != 0) failures += "Native exit code $exitCode"
        if (!Regex("sourceSync=${case.expectedSync}(?:\\s|$)").containsMatchIn(logText)) {
            failures += "expected sourceSync=${case.expectedSync} missing"
        }
        val originResults = JSONArray()
        for (origin in case.expectedOrigins) {
            val match = Regex("voice segment=${origin.segment} at=(-?\\d+) source=(-?\\d+)").find(logText)
            if (match == null) {
                failures += "missing source origin for ${origin.role} segment=${origin.segment}"
                originResults.put(JSONObject().put("role", origin.role).put("segment", origin.segment).put("observed", false))
                continue
            }
            val at = match.groupValues[1].toLong()
            val source = match.groupValues[2].toLong()
            val observedOrigin = source - (at - origin.start)
            val delta = abs(observedOrigin - origin.source)
            val ok = delta <= 1
            if (!ok) failures += "${origin.role} source origin delta=$delta expected=${origin.source} observed=$observedOrigin"
            originResults.put(JSONObject().put("role", origin.role).put("segment", origin.segment)
                .put("start", origin.start).put("expectedSource", origin.source)
                .put("at", at).put("source", source).put("observedOrigin", observedOrigin)
                .put("delta", delta).put("pass", ok))
        }
        val clipped = Regex("clipped=(\\d+)").find(logText)?.groupValues?.get(1)?.toIntOrNull()
        if (clipped == null) failures += "clipped count missing"
        else if (clipped != 0) failures += "clipped=$clipped"
        val underruns = Regex("streamUnderrunFrames=(\\d+)").find(logText)?.groupValues?.get(1)?.toLongOrNull()
        val decodeFailures = Regex("decodeFailures=(\\d+)").find(logText)?.groupValues?.get(1)?.toLongOrNull()
        if (underruns == null) failures += "streamUnderrunFrames missing"
        else if (underruns != 0L) failures += "stream underrun frames=$underruns"
        if (decodeFailures == null) failures += "decodeFailures missing"
        else if (decodeFailures != 0L) failures += "decode failures=$decodeFailures"
        val wavInfo = if (wav.isFile) try { inspectPcm16Wave(wav.readBytes()) } catch (e: Exception) {
            failures += "WAV parse/format error: ${e.message}"
            null
        } else {
            failures += "WAV missing"
            null
        }
        if (wavInfo != null) {
            if (wavInfo.channels != 2 || wavInfo.sampleRate != 48_000L || wavInfo.bits != 16) {
                failures += "WAV format ${wavInfo.sampleRate} Hz/${wavInfo.channels} ch/${wavInfo.bits} bit"
            }
            if (wavInfo.frames <= 0) failures += "WAV has no samples"
            val globalEnd = case.recordStart + wavInfo.frames
            if (globalEnd - case.entryFrame < 240_000L) failures += "less than 5 seconds after target Pre entry"
            val requiredSourceEnd = if (case.plan.fullContext) {
                case.expectedSync + case.plan.outro.info.frames
            } else {
                case.plan.outro.info.frames.toLong()
            }
            if (globalEnd < requiredSourceEnd) failures += "recording truncates source Outro natural tail at $globalEnd < $requiredSourceEnd"
        }
        val output = JSONObject(case.manifestFields.toString())
            .put("status", if (failures.isEmpty()) "PASS" else "FAILED")
            .put("exitCode", exitCode)
            .put("wav", wav.absolutePath)
            .put("log", logFile.absolutePath)
            .put("wavSha256", if (wav.isFile) sha256(wav.readBytes()) else JSONObject.NULL)
            .put("pcm", if (wavInfo == null) JSONObject.NULL else JSONObject()
                .put("sampleRate", wavInfo.sampleRate).put("channels", wavInfo.channels)
                .put("bitsPerSample", wavInfo.bits).put("frames", wavInfo.frames))
            .put("clipped", clipped ?: JSONObject.NULL)
            .put("streamUnderrunFrames", underruns ?: JSONObject.NULL)
            .put("decodeFailures", decodeFailures ?: JSONObject.NULL)
            .put("sourceOriginChecks", originResults)
            .put("logSha256", if (logFile.isFile) sha256(logFile.readBytes()) else JSONObject.NULL)
            .put("failures", JSONArray(failures))
        return output
    }

    private fun inspectPcm16Wave(bytes: ByteArray): WavInfo {
        require(bytes.size >= 44) { "WAV is shorter than its PCM header" }
        fun ascii(at: Int, size: Int) = String(bytes, at, size, Charsets.US_ASCII)
        fun u16(at: Int) = ByteBuffer.wrap(bytes, at, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xffff
        fun u32(at: Int) = ByteBuffer.wrap(bytes, at, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL
        require(ascii(0, 4) == "RIFF" && ascii(8, 4) == "WAVE") { "Not a RIFF/WAVE" }
        require(ascii(12, 4) == "fmt " && u32(16) == 16L && u16(20) == 1) { "Expected canonical PCM fmt chunk" }
        require(ascii(36, 4) == "data") { "Missing canonical data chunk" }
        val channels = u16(22)
        val sampleRate = u32(24)
        val bits = u16(34)
        val dataBytes = u32(40)
        require(bytes.size.toLong() == 44L + dataBytes) { "WAV data size $dataBytes disagrees with file size ${bytes.size}" }
        require(channels > 0 && dataBytes % (channels * bits / 8) == 0L) { "WAV data does not contain whole frames" }
        return WavInfo(channels, sampleRate, bits, dataBytes / (channels * bits / 8))
    }

    private fun writeStageZip(stage: String, jobDir: File, recordingDir: File, output: File) {
        output.parentFile?.mkdirs()
        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            fun addTree(root: File, prefix: String) {
                root.walkTopDown().filter { it.isFile }.sortedBy { it.absolutePath }.forEach { file ->
                    val relative = file.relativeTo(root).invariantSeparatorsPath
                    zip.putNextEntry(ZipEntry("$prefix/$stage/$relative"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            addTree(jobDir, "jobs")
            addTree(recordingDir, "recordings")
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

    private fun JSONObject.origin(role: String): JSONObject = getJSONArray("sourceOrigins").objects().single {
        it.getString("role") == role
    }
}
