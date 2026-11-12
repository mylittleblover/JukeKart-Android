package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.audio.BwavHeader
import com.mylittleblover.mkwplayer.graph.MusicGraphBuilder as G
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import java.io.File
import java.security.MessageDigest
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
        val roles = listOf(
            "Area1_Pre", "Area1_Bridge", "Area1_Inst", "Area1_Melody", "Area2",
            "Area2_Outro00", "Area2_Outro01", "Area2_Outro02", "Area2_Outro03", "Area2_Outro04",
            "Area3_Pre", "Area3", "Final_Pre", "Final", "FinalLapFanfare",
        )
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
        val assetIndex = ordered.mapIndexed { i, s -> s.role to i }.toMap()
        val segmentRole = linkedMapOf(
            "Area1_Pre" to 1, "Area1_Bridge" to 2, "Area1" to 3, "Area2" to 4,
            "Area2_Outro00" to 5, "Area2_Outro01" to 6, "Area2_Outro02" to 7,
            "Area2_Outro03" to 8, "Area2_Outro04" to 9, "Area3_Pre" to 10,
            "Area3" to 11, "Final_Pre" to 12, "Final" to 13, "FinalLapFanfare" to 14,
        ).mapValues { it.value+1000 }
        fun sourceForSegment(name: String) = when (name) {
            "Area1" -> sources.getValue("Area1_Inst")
            else -> sources.getValue(name)
        }
        var nextCue=1500
        val segments = segmentRole.map { (name, id) ->
            val s = sourceForSegment(name)
            val loopStart = s.info.loopStart?.toLong()
            val loopEnd = s.info.loopEnd?.toLong()
            val loop = if (loopStart != null && loopEnd != null && loopEnd <= s.info.frames && loopEnd > loopStart)
                G.Loop(loopStart, loopEnd) else null
            val cues = if(name.endsWith("_Pre")) s.meta.loopCandidates.mapIndexed { i, frame -> G.Cue(nextCue++,"pre.exit",frame) } else if (name == "Area2") (0..4).map { n ->
                val cueName = "Full_ToOutro${n.toString().padStart(2, '0')}"
                val frame = minf("Area2").event(cueName)?.singleOrNull()
                    ?: error("Area2 needs exactly one MINF $cueName event")
                G.Cue(index=40+n, name=cueName, frame=frame, kind=G.CueKind.Custom)
            } else emptyList()
            val exit = if (name.startsWith("Area2_Outro")) 382784L else s.info.frames.toLong()
            G.Segment(id, exit=exit, loop=loop, cues=cues)
        }
        val tracks = mutableListOf<G.Track>()
        var clipId = 200
        var trackId = 100
        fun addTrack(segmentName: String, sourceRole: String, initialSource: Long = 0L) {
            val s = sources.getValue(sourceRole)
            val length = s.info.frames.toLong() - initialSource
            require(length > 0) { "$sourceRole phase $initialSource exceeds BWAV frames ${s.info.frames}" }
            val loop = s.info.loopStart?.toLong()?.let { start -> s.info.loopEnd?.toLong()?.let { end ->
                if (start >= initialSource && end <= s.info.frames && end > start) G.Loop(start, end) else null
            } }
            tracks += G.Track(trackId++, segmentRole.getValue(segmentName), listOf(
                G.Clip(clipId++, assetIndex.getValue(sourceRole), sourceOffset=initialSource, sourceLength=length),
            ))
        }
        for (name in segmentRole.keys) when (name) {
            "Area1" -> { addTrack(name, "Area1_Melody"); addTrack(name, "Area1_Inst") }
            else -> addTrack(name, name)
        }
        val bridgeToFull = minf("Area1_Bridge").event("Bridge_ToFull")?.singleOrNull()
            ?: error("Area1_Bridge needs exactly one MINF Bridge_ToFull event")
        require(minf("Area1_Bridge").phase == 0L && bridgeToFull == 218734L)
        var transitionId = 300
        val transitions = mutableListOf<G.Transition>()
        fun timelineTransition(source: Int, destination: Int, bridge: Int = -1, timeline: G.TransitionTimeline,
                               sync: G.SyncPoint = G.SyncPoint.NextCustomCue, cue: String? = "pre.exit",
                               postExit: Boolean = bridge >= 0) {
            transitions += G.Transition(transitionId++, listOf(source), listOf(destination), sync=sync,
                sourceCue=cue, bridge=bridge, timeline=timeline,postExit=postExit)
        }
        timelineTransition(segmentRole.getValue("Area1_Pre"), segmentRole.getValue("Area1"),
            bridge=segmentRole.getValue("Area1_Bridge"), timeline=G.TransitionTimeline(0, bridgeToFull-18227, bridgeToFull, 0))
        timelineTransition(segmentRole.getValue("Area3_Pre"), segmentRole.getValue("Area3"),
            timeline=G.TransitionTimeline(0, 0, 0, 0))
        timelineTransition(segmentRole.getValue("Final_Pre"), segmentRole.getValue("Final"),
            timeline=G.TransitionTimeline(0, 0, 0, 0))
        val outroPoints = (0..4).associateWith { n ->
            val event = minf("Area2").event("Full_ToOutro${n.toString().padStart(2, '0')}")
                ?: error("Missing source MINF Full_ToOutro$n label")
            require(event.size == 1) { "Expected one Full_ToOutro$n label, got $event" }
            event.single()
        }
        for ((n, point) in outroPoints) {
            val cue = "Full_ToOutro${n.toString().padStart(2, '0')}"
            timelineTransition(segmentRole.getValue("Area2"), segmentRole.getValue("Area2_Outro${n.toString().padStart(2, '0')}"),
                timeline=G.TransitionTimeline(0, 0, 0, 0), sync=G.SyncPoint.NextCustomCue, cue=cue)
            require(point < sources.getValue("Area2").info.frames.toLong()) { "$cue lies outside Area2 BWAV" }
        }
        val outroToNextPre = minf("Area2_Outro00").event("Outro_ToNextPre")?.singleOrNull()
            ?: error("Area2_Outro00 needs exactly one MINF Outro_ToNextPre event")
        require(outroToNextPre == 382784L) { "Expected Outro_ToNextPre=382784, got $outroToNextPre" }
        require((0..4).all { n ->
            minf("Area2_Outro${n.toString().padStart(2, '0')}").event("Outro_ToNextPre")?.singleOrNull() == outroToNextPre
        }) { "All five Outro_ToNextPre markers must match" }
        for (n in 0..4) {
            val outro = segmentRole.getValue("Area2_Outro${n.toString().padStart(2, '0')}")
            timelineTransition(outro, segmentRole.getValue("Area3_Pre"),
                timeline=G.TransitionTimeline(0, 0, 0, 0), sync=G.SyncPoint.ExitCue, cue=null, postExit=true)
        }
        val spec = G.Spec(
            assets=ordered.map { s -> G.Asset(s.file.name, s.info.sampleRate, s.info.channels, s.info.frames.toLong(), s.hash) },
            segments=segments, tracks=tracks, transitions=transitions,
            bpm=minf("Area2").bpm,
        )
        val packet = compiled.packet
        val graph = File(output, "interactive.graph").apply { writeBytes(packet) }
        val graphHash = hex(digest(packet))
        val cases = JSONArray()
        fun addCase(id: String, sourceRole: String, sync: Long, sourceStart: Long, origins: List<Triple<Int, Long, Long>>, basis: String, actions: List<String>) {
            val wav = File(recordings, "$id.wav")
            cases.put(JSONObject()
                .put("id", id).put("wav", wav.absolutePath).put("log", File(recordings, "$id.log").absolutePath)
                .put("command", JSONArray(listOf(runner!!, graph.absolutePath, wav.absolutePath,
                    (sync + 480000L).toString(), ordered.size.toString()) + ordered.map { it.file.absolutePath } + actions))
                .put("env", JSONObject().put("MKW_RECORD_START", maxOf(0L, sourceStart).toString()).put("MKW_SOURCE_PREPARATION","streamed"))
                .put("expectedSync", sync).put("basis", basis)
                .put("sourceOrigins", JSONArray(origins.map { (segment, at, source) ->
                    JSONObject().put("segment", segment).put("start", at).put("source", source)
                }))
                .put("sourceEvidence", JSONObject().put("wav", sources.getValue(sourceRole).file.absolutePath)
                    .put("log", "src/main/assets/interactive_minf.json").put("minfRole", sourceRole)))
        }
        fun preExit(role: String, minLead: Long = 0): Long {
            val record = minf(role)
            return record.loopCandidates.firstOrNull { it > minLead }
                ?: error("$role has no sufficiently late label-1 loop candidate")
        }
        val area1G = preExit("Area1_Pre", 18227)
        addCase("Area1-Pre-Bridge-Area1DualLayer", "Area1_Pre", area1G, area1G-144000,
            listOf(Triple(segmentRole.getValue("Area1"), area1G+bridgeToFull-18227, 0),
                Triple(segmentRole.getValue("Area1_Bridge"), area1G, 0)),
            "SOURCE_MINF_CANDIDATE_NOT_GAMEPLAY_VERIFIED; Area1_Pre label-1 loop candidate; Bridge_ToFull=218734 from MINF event, not bridge loopEnd=291646; both Area1 layers start at source zero, G+218734-18227",
            listOf("0,${segmentRole.getValue("Area1_Pre")}", "1,${segmentRole.getValue("Area1")}"))
        val area3G = preExit("Area3_Pre")
        addCase("Area3-Pre-Area3-Phase0", "Area3_Pre", area3G, area3G-144000,
            listOf(Triple(segmentRole.getValue("Area3"), area3G, 0)),
            "SOURCE_MINF_CANDIDATE_NOT_GAMEPLAY_VERIFIED; Area3_Pre label-1 loop candidate; phase 0 direct entry",
            listOf("0,${segmentRole.getValue("Area3_Pre")}", "1,${segmentRole.getValue("Area3")}"))
        val finalG = preExit("Final_Pre")
        val finalTransition = compiled.spec.transitions.single { it.sources == listOf(segmentRole.getValue("Final_Pre")) &&
            it.destinations == listOf(segmentRole.getValue("Final")) }
        val fanfareStart = finalG + finalTransition.timeline!!.bridgeStart
        val finalStart = finalG + finalTransition.timeline!!.destinationStart
        addCase("Final-Pre-Fanfare-Final-$finalG", "Final_Pre", finalG, finalG-144000,
            listOf(Triple(segmentRole.getValue("FinalLapFanfare"), fanfareStart, 0),
                Triple(segmentRole.getValue("Final"), finalStart, 0)),
            "USER_CONFIRMED_FINAL_SEQUENCE; Final_Pre exit candidate=$finalG; FinalLapFanfare then Final at source 0; source timing/listening not verified",
            listOf("0,${segmentRole.getValue("Final_Pre")}", "${maxOf(1,finalG-48000)},${segmentRole.getValue("Final")}"))
        val fanfareEnd = sources.getValue("FinalLapFanfare").info.frames.toLong()
        addCase("FinalLapFanfare-Final-ExitCue-$fanfareEnd", "FinalLapFanfare", fanfareEnd, 0,
            listOf(Triple(segmentRole.getValue("FinalLapFanfare"), 0, 0),
                Triple(segmentRole.getValue("Final"), fanfareEnd, 0)),
            "USER_CONFIRMED_FINAL_SEQUENCE; independent Native FinalLapFanfare ExitCue→Final at EOF=$fanfareEnd; source timing/listening not verified",
            listOf("0,${segmentRole.getValue("FinalLapFanfare")}", "1,${segmentRole.getValue("Final")}"))
        for ((n, point) in outroPoints) {
            val role = "Area2_Outro${n.toString().padStart(2, '0')}"
            addCase("Area2-Full-ToOutro${n.toString().padStart(2, '0')}-$point", role, point, point-144000,
                listOf(Triple(segmentRole.getValue(role), point, 0)),
                "SOURCE_MINF_CANDIDATE_NOT_GAMEPLAY_VERIFIED; source MINF Full_ToOutro${n.toString().padStart(2, '0')} event frame=$point; Rainbow mapping not user-confirmed",
                listOf("0,${segmentRole.getValue("Area2")}", "${maxOf(1,point-48000)},${segmentRole.getValue(role)}"))
        }
        for ((n, point) in outroPoints) {
            val role = "Area2_Outro${n.toString().padStart(2, '0')}"
            val outroExit = outroToNextPre
            val frames = sources.getValue(role).info.frames.toLong()
            addCase("${role}-Area3-Pre-$outroExit", role, outroExit, 0,
                listOf(Triple(segmentRole.getValue(role), 0, 0),
                    Triple(segmentRole.getValue("Area3_Pre"), outroExit, 0)),
                "SOURCE_MINF_WITH_USER_CONFIRMED_BAR_PHASE_POLICY; $role Outro_ToNextPre=$outroToNextPre; phase 0 starts Area3_Pre at source 0; source clip continues to frame $frames; candidate transition, listening/gameplay not verified",
                listOf("0,${segmentRole.getValue(role)}", "1,${segmentRole.getValue("Area3_Pre")}"))
        }
        fun barExit(role: String): Long = minf(role).beats.firstOrNull { it.label == 1 && it.frame > 144000 }
            ?.frame ?: error("$role has no suitable label-1 bar exit")
        val area1Bar = barExit("Area1_Inst")
        val area1ToArea2 = compiled.spec.transitions.single { it.sources == listOf(segmentRole.getValue("Area1")) &&
            it.destinations == listOf(segmentRole.getValue("Area2")) }
        addCase("Area1-Area2-BarExit-$area1Bar", "Area2", area1Bar, area1Bar-144000,
            listOf(Triple(segmentRole.getValue("Area1"), 0, 0),
                Triple(segmentRole.getValue("Area2"), area1Bar+area1ToArea2.timeline!!.destinationStart, 0)),
            "SOURCE_MINF_WITH_USER_CONFIRMED_BAR_PHASE_POLICY; Area1 label-1 bar exit=$area1Bar; Area2 phase=${minf("Area2").phase}; source MINF candidate, listening not verified",
            listOf("0,${segmentRole.getValue("Area1")}", "${maxOf(1,area1Bar-48000)},${segmentRole.getValue("Area2")}"))

        val area3Bar = barExit("Area3")
        val area3FinalPre = compiled.spec.transitions.single { it.sources == listOf(segmentRole.getValue("Area3")) &&
            it.destinations == listOf(segmentRole.getValue("Final_Pre")) }
        addCase("Area3-Final-Pre-BarExit-$area3Bar", "Final_Pre", area3Bar, area3Bar-144000,
            listOf(Triple(segmentRole.getValue("Area3"), 0, 0),
                Triple(segmentRole.getValue("Final_Pre"), area3Bar+area3FinalPre.timeline!!.destinationStart, 0)),
            "SOURCE_MINF_WITH_USER_CONFIRMED_BAR_PHASE_POLICY; Area3 label-1 bar exit=$area3Bar; Final_Pre phase=${minf("Final_Pre").phase}; source MINF candidate, listening not verified",
            listOf("0,${segmentRole.getValue("Area3")}", "${maxOf(1,area3Bar-48000)},${segmentRole.getValue("Final_Pre")}"))

        val blockers = JSONArray(listOf(
            "Area1→Area2 and Area3→Final_Pre use user-confirmed label-1 bar/phase candidates; original-game listening remains unverified.",
            "Area2 Outro_ToNextPre=382784 produces candidate outro-tail-to-Area3_Pre previews; source transition policy has not been gameplay verified.",
            "Final_Pre→FinalLapFanfare→Final is user-confirmed; source-timing/listening evidence remains unverified.",
            "Every Rainbow transition and Area2 Outro binding remains a source candidate, not gameplay verified; user-confirmed Rainbow mapping table is absent.",
        ))
        val sourceList = JSONArray(ordered.map { s -> JSONObject().put("role", s.role)
            .put("path", s.file.absolutePath).put("frames", s.info.frames).put("sampleRate", s.info.sampleRate)
            .put("channels", s.info.channels).put("loopStart", s.info.loopStart).put("loopEnd", s.info.loopEnd)
            .put("sha256", hex(s.hash)).put("minfLogicalName", s.meta.logicalName).put("minfArchive", s.meta.archive)
            .put("minfOffset", s.meta.offset).put("minfHash", s.meta.hash)
            .put("minfPhase", s.meta.phase).put("minfLoopStart", s.meta.loopStart).put("minfLoopEnd", s.meta.loopEnd) })
        val status = JSONObject().put("stage", "Rainbow").put("status", "SOURCE_MINF_USER_POLICY_NOT_LISTENED")
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
        val outroNextPre = compiled.spec.transitions.filter { it.sources.singleOrNull() in (5..9).map { i -> segmentRole.getValue("Area2_Outro${(i-5).toString().padStart(2, '0')}") } }
        assertEquals(5, outroNextPre.size)
        assertTrue(outroNextPre.all { it.destinations == listOf(segmentRole.getValue("Area3_Pre")) &&
            it.sync == G.SyncPoint.ExitCue && it.postExit && it.timeline == G.TransitionTimeline(0, 0, 0, 0) })
        assertTrue((0..4).all { n -> segments.single { it.index == segmentRole.getValue("Area2_Outro${n.toString().padStart(2, '0')}") }.exit == 382784L })
        assertEquals(2,tracks.count { it.segment==segmentRole.getValue("Area1") })
        assertTrue(tracks.filter { it.segment==segmentRole.getValue("Area1") }.all { it.clips.single().sourceOffset==0L })
        assertEquals(200507L,transitions.first().timeline!!.destinationStart)
    }
}
