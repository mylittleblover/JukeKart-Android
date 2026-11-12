package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.audio.BwavHeader
import com.mylittleblover.mkwplayer.graph.MusicGraphBuilder as G
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import java.io.File
import java.security.MessageDigest
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Evidence-based Rainbow previews. Candidate timing is explicitly not gameplay verification. */
class RainbowPreviewJobExportTest {
    @Test fun exportEvidenceBackedRainbowCandidates() {
        val audioDir = File(System.getenv("MKW_AUDIO_ROOT") ?: "/workspace/mkw-audio/sources", "Rainbow")
        val runner = System.getenv("MKW_NATIVE_PREVIEW")
        assumeTrue(audioDir.isDirectory && runner != null && File(runner!!).isFile)
        val output = File(System.getenv("MKW_PREVIEW_JOBS") ?: "/workspace/mkw-preview-jobs", "Rainbow").apply { mkdirs() }
        val recordings = File(System.getenv("MKW_PREVIEW_OUTPUT") ?: "/workspace/mkw-recordings", "Rainbow").apply { mkdirs() }
        val catalog = MinfCatalog.parse(File("src/main/assets/interactive_minf.json").readText())
        val roles = RainbowInteractiveCompiler.roles
        fun minf(role: String) = catalog.part("Rainbow", role)
            ?: error("Missing unique Rainbow MINF logical record for $role")
        fun file(role: String) = File(audioDir, "BGM_Rainbow_$role.bwav").also {
            require(it.isFile) { "Missing Rainbow source asset: ${it.absolutePath}" }
        }
        fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
        data class Source(val role: String, val file: File, val meta: MinfCatalog.Record, val info: BwavHeader.Info, val bytes: ByteArray, val hash: ByteArray)
        val sources = roles.associateWith { role ->
            val f = file(role)
            val bytes = f.readBytes()
            val info = (BwavHeader.read(bytes) as? BwavHeader.Result.Ok)?.info
                ?: error("Unreadable Rainbow BWAV: $role")
            require(info.sampleRate == 48000 && info.channels in 1..2)
            val meta = minf(role)
            require(meta.rate == info.sampleRate) { "$role MINF/BWAV sample rates differ" }
            Source(role, f, meta, info, bytes, digest(bytes))
        }
        val inst = sources.getValue("Area1_Inst")
        val melody = sources.getValue("Area1_Melody")
        require(inst.info.frames == melody.info.frames && inst.info.loopStart == melody.info.loopStart &&
            inst.info.loopEnd == melody.info.loopEnd && inst.meta.loopStart == melody.meta.loopStart &&
            inst.meta.loopEnd == melody.meta.loopEnd && inst.meta.phase == 18227L && melody.meta.phase == 18227L) {
            "Area1 melody and inst loop metadata differ; refusing to guess a shared loop"
        }
        assertNotEquals(inst.hash.toList(), melody.hash.toList(), "Area1 layers must reference their actual distinct assets")

        val ordered = roles.map { sources.getValue(it) }
        val compiled = RainbowInteractiveCompiler.prepare(roles.map { role ->
            val s = sources.getValue(role)
            RainbowInteractiveCompiler.Source(s.file.name, s.info.sampleRate, s.info.channels, s.info.frames.toLong(),
                s.info.loopStart?.toLong(), s.info.loopEnd?.toLong(), s.hash)
        }, catalog)
        val spec = compiled.spec
        val segmentRole = compiled.segments
        fun segment(role: String) = spec.segments.singleOrNull { it.index == segmentRole.getValue(role) }
            ?: error("Compiled graph is missing segment $role")
        fun transition(from: String, to: String) = spec.transitions.singleOrNull {
            it.sources == listOf(segmentRole.getValue(from)) && it.destinations == listOf(segmentRole.getValue(to))
        } ?: error("Compiled graph is missing unique transition $from → $to")
        fun tracks(role: String) = spec.tracks.filter { it.segment == segmentRole.getValue(role) }
        fun origin(role: String, at: Long, source: Long = 0L): Triple<Int, Long, Long> {
            val clips = tracks(role).flatMap { it.clips }
            require(clips.any { source >= it.sourceOffset && source < it.sourceOffset + it.sourceLength }) {
                "$role compiled clips cannot play source frame $source"
            }
            return Triple(segmentRole.getValue(role), at, source)
        }
        fun transitionOrigins(plan: G.Transition, anchor: Long): List<Triple<Int, Long, Long>> {
            val timeline = plan.timeline ?: error("Compiled transition ${plan.index} has no timeline")
            val rows = mutableListOf<Triple<Int, Long, Long>>()
            if (plan.bridge >= 0) {
                val bridgeRole = segmentRole.entries.single { it.value == plan.bridge }.key
                rows += origin(bridgeRole, anchor + timeline.bridgeStart)
            }
            val destinationRole = segmentRole.entries.single { it.value == plan.destinations.single() }.key
            rows += origin(destinationRole, anchor + timeline.destinationStart, timeline.destinationSource)
            return rows
        }
        fun compiledPlanEvidence(plan: G.Transition) = JSONObject()
            .put("transitionIndex", plan.index)
            .put("sources", JSONArray(plan.sources))
            .put("destinations", JSONArray(plan.destinations))
            .put("bridge", plan.bridge)
            .put("sourceCue", plan.sourceCue)
            .put("sync", plan.sync.name)
            .put("postExit", plan.postExit)
            .put("fadeOut", JSONObject().put("beats", plan.fadeOut.beats).put("offset", plan.fadeOut.offset)
                .put("shape", plan.fadeOut.shape.name))
            .put("fadeIn", JSONObject().put("beats", plan.fadeIn.beats).put("offset", plan.fadeIn.offset)
                .put("shape", plan.fadeIn.shape.name))
            .put("timeline", plan.timeline?.let { JSONObject()
                .put("bridgeStart", it.bridgeStart).put("destinationStart", it.destinationStart)
                .put("destinationCommit", it.destinationCommit).put("destinationSource", it.destinationSource) })
        fun rawEventEvidence(role: String, name: String, frame: Long) = JSONObject()
            .put("kind", "RawEvent").put("minfRole", role).put("name", name).put("frame", frame)
        fun rawBeatEvidence(role: String, frame: Long) = JSONObject()
            .put("kind", "RawBeat").put("minfRole", role).put("label", 1).put("frame", frame)
        fun referenceEvidence(kind: String, frame: Long, detail: String) = JSONObject()
            .put("kind", kind).put("frame", frame).put("sampleRate", 48000).put("detail", detail)
        val finalReferenceEvidence = JSONObject()
            .put("kind", "REFERENCE_AUDIO_SOURCE_ZERO_ALIGNMENT")
            .put("referenceId", "1tiQumSMVC15TujrhDRvjRArCLe9LmPWJ")
            .put("referencePath", "/workspace/mkw-planning/rainbow-correct-reference/reference.flac")
            .put("sampleRate", 48000)
            .put("fanfareSourceZeroReferenceFrame", 30695935)
            .put("finalSourceZeroReferenceFrame", 30914669)
            .put("measuredSourceZeroDeltaFrames", 218734)
            .put("measuredSourceZeroDeltaSeconds", 4.556958333)
            .put("fanfareSourceOrigin", 0)
            .put("finalSourceOriginRelativeToFanfare", 218734)
            .put("correlation", 0.997)
        val bridgeToFull = minf("Area1_Bridge").event("Bridge_ToFull")?.singleOrNull()
            ?: error("Area1_Bridge needs exactly one MINF Bridge_ToFull event")
        require(minf("Area1_Bridge").phase == 0L)
        val outroPoints = (0..4).associateWith { n ->
            val event = minf("Area2").event("Full_ToOutro${n.toString().padStart(2, '0')}")
                ?: error("Missing source MINF Full_ToOutro$n label")
            require(event.size == 1) { "Expected one Full_ToOutro$n label, got $event" }
            event.single()
        }
        val outroToNextPre = minf("Area2_Outro00").event("Outro_ToNextPre")?.singleOrNull()
            ?: error("Area2_Outro00 needs exactly one MINF Outro_ToNextPre event")
        val outroExit = segment("Area2_Outro00").exit
        require(outroToNextPre == outroExit) { "Raw MINF Outro_ToNextPre=$outroToNextPre differs from compiled Outro exit=$outroExit" }
        require((0..4).all { n ->
            val role = "Area2_Outro${n.toString().padStart(2, '0')}"
            minf(role).event("Outro_ToNextPre")?.singleOrNull() == segment(role).exit
        }) { "Each compiled Outro exit must equal its own raw MINF Outro_ToNextPre event" }
        val area1TrackClips = tracks("Area1").flatMap { it.clips }
        require(area1TrackClips.size == 2 && area1TrackClips.all { it.sourceOffset == 0L }) {
            "Compiled Area1 must retain both zero-offset melody and inst clips"
        }
        val area1AssetNames = area1TrackClips.map { spec.assets[it.asset].path }.toSet()
        assertEquals(setOf("BGM_Rainbow_Area1_Melody.bwav", "BGM_Rainbow_Area1_Inst.bwav"), area1AssetNames)
        assertTrue(area1TrackClips.all { clip -> clip.sourceLength == spec.assets[clip.asset].frames })
        assertTrue(spec.tracks.flatMap { it.clips }.all { clip ->
            clip.sourceOffset == 0L && clip.sourceLength == spec.assets[clip.asset].frames
        })
        val area1Bridge = transition("Area1_Pre", "Area1")
        require(area1Bridge.bridge == segmentRole.getValue("Area1_Bridge") && area1Bridge.postExit)
        val area1Timeline = area1Bridge.timeline ?: error("Compiled Area1 bridge transition needs a timeline")
        require(area1Timeline.bridgeStart == 0L && area1Timeline.destinationCommit == bridgeToFull)
        require(area1Timeline.destinationStart == bridgeToFull - minf("Area1_Inst").phase - minf("Area1_Bridge").phase)
        val packet = compiled.packet
        val graph = File(output, "interactive.graph").apply { writeBytes(packet) }
        val graphHash = hex(digest(packet))
        val cases = JSONArray()
        fun addCase(id: String, sourceRole: String, sync: Long, sourceStart: Long,
                    origins: List<Triple<Int, Long, Long>>, basis: String, actions: List<String>,
                    rawSource: JSONObject? = null, plan: G.Transition? = null,
                    expectedOverlap: JSONObject? = null, timingReference: JSONObject? = null) {
            val wav = File(recordings, "$id.wav")
            val row = JSONObject()
                .put("id", id).put("wav", wav.absolutePath).put("log", File(recordings, "$id.log").absolutePath)
                .put("command", JSONArray(listOf(runner!!, graph.absolutePath, wav.absolutePath,
                    (sync + 480000L).toString(), ordered.size.toString()) + ordered.map { it.file.absolutePath } + actions))
                .put("env", JSONObject().put("MKW_RECORD_START", maxOf(0L, sourceStart).toString()).put("MKW_SOURCE_PREPARATION","streamed"))
                .put("expectedSync", sync).put("basis", basis)
                .put("sourceOrigins", JSONArray(origins.map { (segment, at, source) ->
                    JSONObject().put("segment", segment).put("start", at).put("source", source)
                }))
                .put("sourceEvidence", JSONObject().put("wav", sources.getValue(sourceRole).file.absolutePath)
                    .put("log", "src/main/assets/interactive_minf.json").put("minfRole", sourceRole))
            if (rawSource != null) row.put("rawSourceEvidence", rawSource)
            if (plan != null) row.put("compiledPlan", compiledPlanEvidence(plan))
            if (expectedOverlap != null) row.put("expectedOverlap", expectedOverlap)
            if (timingReference != null) row.put("timingReference", timingReference)
            cases.put(row)
        }
        fun preExit(role: String, minLead: Long = 0): Long {
            val record = minf(role)
            return record.loopCandidates.firstOrNull { it > minLead }
                ?: error("$role has no sufficiently late label-1 loop candidate")
        }
        val area1G = preExit("Area1_Pre", 18227)
        val area1ToFull = transition("Area1_Pre", "Area1")
        require(segment("Area1_Pre").cues.any { it.name == area1ToFull.sourceCue && it.frame == area1G })
        addCase("Area1-Pre-Bridge-Area1DualLayer", "Area1_Pre", area1G, area1G-144000,
            transitionOrigins(area1ToFull, area1G),
            "SOURCE_MINF_CANDIDATE_NOT_GAMEPLAY_VERIFIED; Area1_Pre label-1 loop candidate; Bridge_ToFull=$bridgeToFull from RawEvent, not bridge BWAV loopEnd=${sources.getValue("Area1_Bridge").info.loopEnd}; both Area1 layers start at source zero using compiled timeline",
            listOf("0,${segmentRole.getValue("Area1_Pre")}", "1,${segmentRole.getValue("Area1")}"),
            rawSource=JSONObject().put("kind", "RawBeat").put("minfRole", "Area1_Pre").put("label", 1).put("frame", area1G)
                .put("bridgeToFull", rawEventEvidence("Area1_Bridge", "Bridge_ToFull", bridgeToFull)), plan=area1ToFull)
        val area3G = preExit("Area3_Pre")
        val area3PreToFull = transition("Area3_Pre", "Area3")
        require(segment("Area3_Pre").cues.any { it.name == area3PreToFull.sourceCue && it.frame == area3G })
        addCase("Area3-Pre-Area3-Phase0", "Area3_Pre", area3G, area3G-144000,
            transitionOrigins(area3PreToFull, area3G),
            "SOURCE_MINF_CANDIDATE_NOT_GAMEPLAY_VERIFIED; Area3_Pre label-1 loop candidate; phase 0 direct entry",
            listOf("0,${segmentRole.getValue("Area3_Pre")}", "1,${segmentRole.getValue("Area3")}"),
            rawSource=rawBeatEvidence("Area3_Pre", area3G), plan=area3PreToFull)
        val finalG = preExit("Final_Pre")
        val finalTransition = compiled.spec.transitions.single { it.sources == listOf(segmentRole.getValue("Final_Pre")) &&
            it.destinations == listOf(segmentRole.getValue("Final")) }
        require(segment("Final_Pre").cues.any { it.name == finalTransition.sourceCue && it.frame == finalG })
        addCase("Final-Pre-Fanfare-Final-$finalG", "Final_Pre", finalG, finalG-144000,
            listOf(origin("Final_Pre", 0)) + transitionOrigins(finalTransition, finalG),
            "USER_CONFIRMED_FINAL_SEQUENCE; Final_Pre exit candidate=$finalG; Fanfare-to-Final uses current compiled WIP timing, known late and pending reference alignment",
            listOf("0,${segmentRole.getValue("Final_Pre")}", "${maxOf(1,finalG-48000)},${segmentRole.getValue("Final")}"),
            rawSource=rawBeatEvidence("Final_Pre", finalG), plan=finalTransition,
            timingReference=JSONObject(finalReferenceEvidence.toString())
                .put("compiledFanfareToFinalOffsetFrames", finalTransition.timeline!!.destinationStart)
                .put("compiledPlanMatchesMeasuredDelta", finalTransition.timeline!!.destinationStart == 218734L)
                .put("detail", "Independent source-zero alignment in the supplied reference FLAC; frame observations and delta are reference measurements, while compiled offset is read from production plan."))
        val fanfareEnd = segment("FinalLapFanfare").exit
        val fanfareClip = tracks("FinalLapFanfare").flatMap { it.clips }.single()
        val fanfareAssetFrames = sources.getValue("FinalLapFanfare").info.frames.toLong()
        require(fanfareEnd in 1..fanfareAssetFrames && fanfareClip.sourceOffset == 0L &&
            fanfareClip.sourceLength == fanfareAssetFrames) {
            "Compiled Fanfare exit must remain inside its source asset while its clip retains the full source"
        }
        val fanfareToFinal = transition("FinalLapFanfare", "Final")
        require(fanfareToFinal.sync == G.SyncPoint.ExitCue)
        require(fanfareEnd + 480000L >= fanfareAssetFrames) {
            "Independent Fanfare preview recording must include the full source clip tail after its compiled handover"
        }
        addCase("FinalLapFanfare-Final-ExitCue-$fanfareEnd", "FinalLapFanfare", fanfareEnd, 0,
            listOf(origin("FinalLapFanfare", 0)) + transitionOrigins(fanfareToFinal, fanfareEnd),
            "COMPILED_WIP_TIMING_CANDIDATE_NOT_VALIDATED; independent Native FinalLapFanfare ExitCue→Final at compiled segment exit=$fanfareEnd; asset frames=$fanfareAssetFrames; final alignment/listening pending",
            listOf("0,${segmentRole.getValue("FinalLapFanfare")}", "1,${segmentRole.getValue("Final")}"),
            plan=fanfareToFinal,
            timingReference=JSONObject(finalReferenceEvidence.toString())
                .put("compiledFanfareExitFrames", fanfareEnd)
                .put("fanfareAssetFrames", fanfareAssetFrames)
                .put("detail", "This Native case requests Final at the compiled Fanfare exit; its recording covers the full asset tail. The reference alignment separately measures the Final source-zero point."))
        for ((n, point) in outroPoints) {
            val role = "Area2_Outro${n.toString().padStart(2, '0')}"
            val fullToOutro = transition("Area2", role)
            val cue = fullToOutro.sourceCue ?: error("Compiled $role transition lacks source cue")
            require(segment("Area2").cues.any { it.name == cue && it.frame == point })
            addCase("Area2-Full-ToOutro${n.toString().padStart(2, '0')}-$point", role, point, point-144000,
                transitionOrigins(fullToOutro, point),
                "SOURCE_MINF_CANDIDATE_NOT_GAMEPLAY_VERIFIED; source MINF Full_ToOutro${n.toString().padStart(2, '0')} event frame=$point; Rainbow mapping not user-confirmed",
                listOf("0,${segmentRole.getValue("Area2")}", "${maxOf(1,point-48000)},${segmentRole.getValue(role)}"),
                rawSource=rawEventEvidence("Area2", cue, point), plan=fullToOutro)
        }
        for ((n, _) in outroPoints) {
            val role = "Area2_Outro${n.toString().padStart(2, '0')}"
            val rawExit = minf(role).event("Outro_ToNextPre")?.singleOrNull()
                ?: error("$role needs exactly one MINF Outro_ToNextPre event")
            val outroSegment = segment(role)
            val outroExit = outroSegment.exit
            require(outroExit == rawExit) { "$role compiled exit=$outroExit differs from RawEvent Outro_ToNextPre=$rawExit" }
            val outroToPre = transition(role, "Area3_Pre")
            require(outroToPre.sync == G.SyncPoint.ExitCue && outroToPre.postExit)
            require(outroSegment.cues.none { it.frame == outroExit && it.name != "Outro_ToNextPre" })
            val frames = sources.getValue(role).info.frames.toLong()
            addCase("${role}-Area3-Pre-$outroExit", role, outroExit, 0,
                listOf(origin(role, 0)) + transitionOrigins(outroToPre, outroExit),
                "SOURCE_MINF_WITH_USER_CONFIRMED_BAR_PHASE_POLICY; $role Outro_ToNextPre=$outroToNextPre; phase 0 starts Area3_Pre at source 0; source clip continues to frame $frames; candidate transition, listening/gameplay not verified",
                listOf("0,${segmentRole.getValue(role)}", "1,${segmentRole.getValue("Area3_Pre")}"),
                rawSource=rawEventEvidence(role, "Outro_ToNextPre", rawExit), plan=outroToPre)
        }
        fun barExit(role: String): Long = minf(role).beats.firstOrNull { it.label == 1 && it.frame > 144000 }
            ?.frame ?: error("$role has no suitable label-1 bar exit")
        val area1Bar = barExit("Area1_Inst")
        val area1ToArea2 = transition("Area1", "Area2")
        require(segment("Area1").cues.any { it.name == area1ToArea2.sourceCue && it.frame == area1Bar })
        addCase("Area1-Area2-BarExit-$area1Bar", "Area2", area1Bar, area1Bar-144000,
            listOf(origin("Area1", 0)) + transitionOrigins(area1ToArea2, area1Bar),
            "SOURCE_MINF_WITH_USER_CONFIRMED_BAR_PHASE_POLICY; Area1 label-1 bar exit=$area1Bar; Area2 phase=${minf("Area2").phase}; source MINF candidate, listening not verified",
            listOf("0,${segmentRole.getValue("Area1")}", "${maxOf(1,area1Bar-48000)},${segmentRole.getValue("Area2")}"),
            rawSource=rawBeatEvidence("Area1_Inst", area1Bar), plan=area1ToArea2)

        val area3Bar = barExit("Area3")
        val area3FinalPre = transition("Area3", "Final_Pre")
        fun fadeFrames(beats: Double) =
            (beats * 60.0 * spec.transportSampleRate / spec.bpm).roundToLong()
        require(area3FinalPre.postExit && area3FinalPre.fadeOut.offset == 0.0 && area3FinalPre.fadeIn.offset == 0.0) {
            "Area3→Final_Pre must retain the source and start both fades at its selected label-1 bar"
        }
        val sourceFadeFrames = fadeFrames(area3FinalPre.fadeOut.beats)
        val destinationFadeFrames = fadeFrames(area3FinalPre.fadeIn.beats)
        require(sourceFadeFrames == 48000L && destinationFadeFrames == 48000L) {
            "Compiled Area3→Final_Pre fades must each span 48000 transport samples; got $sourceFadeFrames/$destinationFadeFrames"
        }
        require(segment("Area3").cues.any { it.name == area3FinalPre.sourceCue && it.frame == area3Bar })
        addCase("Area3-Final-Pre-BarExit-$area3Bar", "Final_Pre", area3Bar, area3Bar-144000,
            listOf(origin("Area3", 0)) + transitionOrigins(area3FinalPre, area3Bar),
            "SOURCE_MINF_WITH_USER_CONFIRMED_BAR_PHASE_POLICY; Area3 label-1 bar exit=$area3Bar; Final_Pre phase=${minf("Final_Pre").phase}; source MINF candidate, listening not verified",
            listOf("0,${segmentRole.getValue("Area3")}", "${maxOf(1,area3Bar-48000)},${segmentRole.getValue("Final_Pre")}"),
            rawSource=rawBeatEvidence("Area3", area3Bar), plan=area3FinalPre,
            expectedOverlap=JSONObject().put("start", area3Bar).put("end", area3Bar + minOf(sourceFadeFrames, destinationFadeFrames))
                .put("sourceSegment", segmentRole.getValue("Area3"))
                .put("destinationSegment", segmentRole.getValue("Final_Pre")).put("minimumActiveVoices", 2))

        val blockers = JSONArray(listOf(
            "Area1→Area2 and Area3→Final_Pre use user-confirmed label-1 bar/phase candidates; original-game listening remains unverified.",
            "Area2 Outro_ToNextPre=$outroToNextPre produces candidate outro-tail-to-Area3_Pre previews; source transition policy has not been gameplay verified.",
            "Final_Pre→FinalLapFanfare→Final sequence is user-confirmed; supplied reference FLAC aligns Fanfare source zero at frame 30695935 and Final source zero at frame 30914669 (218734 frames apart, correlation ≈0.997). The compiled offset is exported for comparison; no listening or gameplay acceptance is claimed.",
            "Every Rainbow transition and Area2 Outro binding remains a source candidate, not gameplay verified; user-confirmed Rainbow mapping table is absent.",
        ))
        val sourceList = JSONArray(ordered.map { s -> JSONObject().put("role", s.role)
            .put("path", s.file.absolutePath).put("frames", s.info.frames).put("sampleRate", s.info.sampleRate)
            .put("channels", s.info.channels).put("loopStart", s.info.loopStart).put("loopEnd", s.info.loopEnd)
            .put("sha256", hex(s.hash)).put("minfLogicalName", s.meta.logicalName).put("minfArchive", s.meta.archive)
            .put("minfOffset", s.meta.offset).put("minfHash", s.meta.hash)
            .put("minfPhase", s.meta.phase).put("minfLoopStart", s.meta.loopStart).put("minfLoopEnd", s.meta.loopEnd) })
        val status = JSONObject().put("stage", "Rainbow").put("status", "SOURCE_AND_REFERENCE_POLICY_NOT_LISTENED")
            .put("graph", graph.absolutePath).put("graphSha256", graphHash).put("graphVersion", 14)
            .put("nativeSha256",hex(digest(File(runner!!).readBytes()))).put("assets", sourceList).put("cases", cases).put("evidenceLimits", blockers)
            .put("area1LoopEvidence", JSONObject().put("melodyFrames", melody.info.frames).put("instFrames", inst.info.frames)
                .put("melodyLoopStart", melody.info.loopStart).put("instLoopStart", inst.info.loopStart)
                .put("melodyLoopEnd", melody.info.loopEnd).put("instLoopEnd", inst.info.loopEnd)
                .put("sameLoop", true).put("bridgeToFullFrame", bridgeToFull)
                .put("bridgeBwavLoopEnd", sources.getValue("Area1_Bridge").info.loopEnd))
        File(output, "jobs.json").writeText(status.toString(2))
        assertTrue(graph.isFile && graph.length() > 0)
        assertEquals(16, cases.length())
        assertEquals(segmentRole.size, spec.segments.size)
        assertEquals(segmentRole.values.toSet(), spec.segments.map { it.index }.toSet())
        val outroRoles = (0..4).map { "Area2_Outro${it.toString().padStart(2, '0')}" }
        val outroIds = outroRoles.map { segmentRole.getValue(it) }.toSet()
        val outroNextPre = spec.transitions.filter { it.sources.singleOrNull() in outroIds }
        assertEquals(5, outroNextPre.size)
        assertTrue(outroNextPre.all { it.destinations == listOf(segmentRole.getValue("Area3_Pre")) &&
            it.sync == G.SyncPoint.ExitCue && it.postExit && it.timeline == G.TransitionTimeline(0, 0, 0, 0) })
        assertTrue(outroRoles.all { role ->
            val clip = tracks(role).flatMap { it.clips }.single()
            segment(role).exit == minf(role).event("Outro_ToNextPre")!!.single() &&
                clip.sourceOffset == 0L && clip.sourceLength == sources.getValue(role).info.frames.toLong()
        })
        assertEquals(2, tracks("Area1").flatMap { it.clips }.size)
        assertTrue(tracks("Area1").flatMap { it.clips }.all { it.sourceOffset == 0L })
        val compiledBridge = transition("Area1_Pre", "Area1")
        val bridgeTimeline = compiledBridge.timeline!!
        assertEquals(bridgeToFull - minf("Area1_Bridge").phase - minf("Area1_Inst").phase,
            bridgeTimeline.destinationStart)
        assertEquals(bridgeToFull - minf("Area1_Bridge").phase, bridgeTimeline.destinationCommit)
        assertEquals(segment("FinalLapFanfare").exit, fanfareEnd)
        assertEquals(G.SyncPoint.ExitCue, transition("FinalLapFanfare", "Final").sync)
    }
}
