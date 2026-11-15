package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.audio.BwavHeader
import com.mylittleblover.mkwplayer.audio.EngineSnapshot
import com.mylittleblover.mkwplayer.library.CoursePreset
import com.mylittleblover.mkwplayer.library.FlowStep
import com.mylittleblover.mkwplayer.library.PresetRefs
import com.mylittleblover.mkwplayer.pack.PackManifest
import java.io.File

/**
 * Inspects verified per-course stage assets and compiles built-in or user-authored
 * [CoursePreset] flows strictly from measured BWAV headers, MINF beats/events,
 * [ConfirmedOutroCatalog], and [CourseMedleyCatalog] — never inventing transition points
 * or hardcoding 48 kHz timing (#48-#53).
 */
internal object CourseFlowRecipeBuilder {

    data class ExitCandidate(
        val frame: Long,
        val label: String,
    )

    data class StagePartInfo(
        val roleKey: String,
        val stem: String,
        val secondaryStem: String? = null,
        val digest: String,
        val frames: Long,
        val sampleRate: Int,
        val loopStart: Long?,
        val loopEnd: Long?,
        val supportsLoop: Boolean,
        val exitCandidates: List<ExitCandidate>,
    ) {
        val defaultExitFrame: Long
            get() = exitCandidates.lastOrNull()?.frame ?: loopEnd ?: frames
    }

    data class OutroOption(
        val number: Int,
        val stem: String,
        val digest: String,
        val frames: Long,
        val sampleRate: Int,
        val mainTriggerPoints: List<Long>,
        val outroToNextPreFrame: Long?,
    ) {
        val supportsNativeHandover: Boolean
            get() = outroToNextPreFrame != null && outroToNextPreFrame in 1L..frames
    }

    data class BuiltInPresetSummary(
        val ref: String,
        val medley: CourseMedleyCatalog.Medley,
        val isKnockout: Boolean,
        val outroToNextPreFrame: Long?,
        val incomingPrePhase: Long,
    )

    data class CourseInventory(
        val courseCode: String,
        val parts: List<StagePartInfo>,
        val outros: List<OutroOption>,
        val builtInPresets: List<BuiltInPresetSummary>,
        val hasFanfareAsset: Boolean,
        val sampleRate: Int,
    ) {
        fun part(roleKey: String): StagePartInfo? = parts.firstOrNull { it.roleKey == roleKey }

        fun defaultEditorSteps(): List<FlowStep> {
            val preferred = if (courseCode == "Rainbow") {
                listOf("Area1_Pre", "Area1_Bridge", "Area1", "Area2", "Area3_Pre", "Area3", "Final_Pre", "Final")
            } else {
                listOf("Pre_Short", "Pre", "Bridge", "Main", "Fast")
            }
            val present = preferred.mapNotNull { role -> part(role)?.let { FlowStep(roleKey = role) } }
            return present.ifEmpty {
                parts.firstOrNull()?.let { listOf(FlowStep(it.roleKey)) } ?: emptyList()
            }
        }
    }

    data class AssetHeader(
        val stem: String,
        val digest: String,
        val frames: Long,
        val sampleRate: Int,
        val loopStart: Long?,
        val loopEnd: Long?,
    )

    interface SourceResolver {
        fun findByFileName(fileName: String): AssetHeader?
    }

    fun packSourceResolver(
        manifest: PackManifest?,
        identities: InteractiveAudioIdentities,
        packDir: File,
    ): SourceResolver {
        val manifestAssets = manifest?.assets?.associateBy { it.digest.sha256 }.orEmpty()
        return object : SourceResolver {
            override fun findByFileName(fileName: String): AssetHeader? {
                val stem = fileName.removeSuffix(".bwav")
                val digest = identities.digestForFileName(fileName) ?: return null
                val audioFile = File(packDir, "audio/$digest")
                if (!audioFile.isFile) return null
                val header = runCatching {
                    audioFile.inputStream().use { input ->
                        val head = ByteArray(1024)
                        val count = input.read(head)
                        if (count <= 0) return@use null
                        (BwavHeader.read(head.copyOf(count)) as? BwavHeader.Result.Ok)?.info
                    }
                }.getOrNull()
                if (header != null) {
                    return AssetHeader(
                        stem = stem,
                        digest = digest,
                        frames = header.frames.toLong(),
                        sampleRate = header.sampleRate,
                        loopStart = header.loopStart?.toLong(),
                        loopEnd = header.loopEnd?.toLong(),
                    )
                }
                val asset = manifestAssets[digest] ?: return null
                return AssetHeader(
                    stem = stem,
                    digest = digest,
                    frames = asset.frames,
                    sampleRate = asset.sampleRate,
                    loopStart = null,
                    loopEnd = null,
                )
            }
        }
    }

    fun inspectCourse(
        courseCode: String,
        minf: MinfCatalog,
        medleyCatalog: CourseMedleyCatalog.Catalog,
        resolver: SourceResolver,
    ): CourseInventory {
        val logical = courseCode.removeSuffix("_00")
        val medleys = medleyCatalog.forCourse(logical)
        val builtIns = medleys.map { medley ->
            val ref = if (medley.isKnockoutVariant) PresetRefs.SYSTEM_KNOCKOUT else PresetRefs.SYSTEM_DEFAULT
            val handover = resolveMedleyHandoverFrame(medley, minf)
            val introPhase = resolveIncomingPhase(medley, minf)
            BuiltInPresetSummary(
                ref = ref,
                medley = medley,
                isKnockout = medley.isKnockoutVariant,
                outroToNextPreFrame = handover,
                incomingPrePhase = introPhase,
            )
        }

        val roleDefinitions = if (logical == "Rainbow") rainbowRoleDefinitions() else standardRoleDefinitions(logical)
        val parts = roleDefinitions.mapNotNull { def ->
            val primaryHeader = def.candidateFileNames.firstNotNullOfOrNull(resolver::findByFileName)
                ?: return@mapNotNull null
            val secondaryHeader = def.secondaryFileName?.let(resolver::findByFileName)
            val record = findMinfRecord(minf, def.minfStage, def.minfRoles)
            val loopStart = primaryHeader.loopStart ?: record?.loopStart?.takeIf { it < (record.loopEnd) }
            val loopEnd = primaryHeader.loopEnd ?: record?.loopEnd?.takeIf { it > (loopStart ?: Long.MAX_VALUE) && it <= primaryHeader.frames }
            val candidates = collectExitCandidates(
                roleKey = def.roleKey,
                record = record,
                frames = primaryHeader.frames,
                sampleRate = primaryHeader.sampleRate,
            )
            StagePartInfo(
                roleKey = def.roleKey,
                stem = primaryHeader.stem,
                secondaryStem = secondaryHeader?.stem,
                digest = primaryHeader.digest,
                frames = primaryHeader.frames,
                sampleRate = primaryHeader.sampleRate,
                loopStart = loopStart,
                loopEnd = loopEnd,
                supportsLoop = loopStart != null && loopEnd != null && loopEnd > loopStart &&
                    def.roleKey !in setOf("Bridge", "Area1_Bridge"),
                exitCandidates = candidates,
            )
        }

        val confirmedStage = if (logical == "Rainbow") "Rainbow" else logical
        val confirmedMap = ConfirmedOutroCatalog.forStage(confirmedStage).orEmpty()
        val outros = confirmedMap.keys.sorted().mapNotNull { n ->
            val fileName = if (logical == "Rainbow") {
                "BGM_Rainbow_Area2_Outro%02d.bwav".format(n)
            } else {
                "BGM_Stage_${logical}_Outro%02d.bwav".format(n)
            }
            val header = resolver.findByFileName(fileName) ?: return@mapNotNull null
            val handover = findOutroHandoverFrame(logical, n, header.stem, minf)
                ?.takeIf { it in 1L..header.frames }
            OutroOption(
                number = n,
                stem = header.stem,
                digest = header.digest,
                frames = header.frames,
                sampleRate = header.sampleRate,
                mainTriggerPoints = confirmedMap[n].orEmpty().sorted(),
                outroToNextPreFrame = handover,
            )
        }

        val fanfarePresent = resolver.findByFileName(FinalLapFanfareCompiler.ASSET) != null
        val sampleRate = parts.firstOrNull()?.sampleRate
            ?: builtIns.firstOrNull()?.let { medleyCatalog.sampleRate }
            ?: 48_000
        return CourseInventory(
            courseCode = logical,
            parts = parts,
            outros = outros,
            builtInPresets = builtIns,
            hasFanfareAsset = fanfarePresent,
            sampleRate = sampleRate,
        )
    }

    /** Resolves a preset reference (`sys:default`, `sys:knockout`, or `user:<id>`) into a runnable medley. */
    fun resolvePresetRef(
        courseCode: String,
        presetRef: String?,
        userPresets: List<CoursePreset>,
        minf: MinfCatalog,
        medleyCatalog: CourseMedleyCatalog.Catalog,
        resolver: SourceResolver,
    ): CourseMedleyCompiler.ResolvedMedley? {
        val logical = courseCode.removeSuffix("_00")
        val inventory = inspectCourse(logical, minf, medleyCatalog, resolver)
        if (PresetRefs.isUser(presetRef)) {
            val found = userPresets.firstOrNull {
                it.id == presetRef && it.courseCode.equals(logical, ignoreCase = true)
            }
            if (found != null) {
                compileUserPreset(found, inventory, minf, resolver)?.let { return it }
            }
            // Deleted or unavailable user preset falls back cleanly to system default (#50).
        }
        val targetBuiltIn = when (presetRef) {
            PresetRefs.SYSTEM_KNOCKOUT -> inventory.builtInPresets.firstOrNull { it.isKnockout }
                ?: inventory.builtInPresets.firstOrNull()
            else -> inventory.builtInPresets.firstOrNull { !it.isKnockout }
                ?: inventory.builtInPresets.firstOrNull()
        } ?: return null

        val medley = targetBuiltIn.medley
        val digests = linkedMapOf<String, String>()
        for (stem in medley.uniqueStems) {
            val header = resolver.findByFileName("$stem.bwav") ?: return null
            digests[stem] = header.digest
        }
        return CourseMedleyCompiler.ResolvedMedley(
            medley = medley,
            sampleRate = targetBuiltIn.let { inventory.sampleRate },
            stemDigests = digests,
            outroHandoverFrame = targetBuiltIn.outroToNextPreFrame,
            incomingPrePhase = targetBuiltIn.incomingPrePhase,
        )
    }

    fun compileUserPreset(
        preset: CoursePreset,
        inventory: CourseInventory,
        minf: MinfCatalog,
        resolver: SourceResolver,
    ): CourseMedleyCompiler.ResolvedMedley? {
        val clips = mutableListOf<CourseMedleyCatalog.MedleyClip>()
        val digests = linkedMapOf<String, String>()
        val sampleRate = inventory.sampleRate
        var cursor = 0L

        preset.steps.forEachIndexed { idx, step ->
            val part = inventory.part(step.roleKey) ?: return null
            val prevRole = preset.steps.getOrNull(idx - 1)?.roleKey
            val isFirst = idx == 0

            val minfRole = mapRoleKeyToMinfRole(inventory.courseCode, step.roleKey)
            val record = findMinfRecord(minf, minfStageFor(inventory.courseCode, step.roleKey), minfRole)

            var clipStart = cursor
            var sourceOffset = 0L

            if (!isFirst && prevRole == "Pre_Short" && step.roleKey == "Pre") {
                // Strictly align Pre's verified entry bar after Pre_Short without crossfade (#6/#7).
                val refMedley = inventory.builtInPresets.firstOrNull { !it.isKnockout }?.medley
                val refPre = refMedley?.clips?.firstOrNull { it.stem == part.stem }
                sourceOffset = refPre?.sourceOffset
                    ?: record?.phase?.coerceIn(0L, part.frames - 1L)
                    ?: 0L
            } else if (!isFirst && step.roleKey in setOf("Bridge", "Area1_Bridge")) {
                val bridgePhase = record?.phase ?: 0L
                clipStart = (cursor - bridgePhase).coerceAtLeast(0L)
            } else if (!isFirst && prevRole in setOf("Bridge", "Area1_Bridge") && step.roleKey in setOf("Main", "Area1")) {
                val prevBridgePart = inventory.part(prevRole!!)
                val prevBridgeMinf = findMinfRecord(minf, inventory.courseCode, mapRoleKeyToMinfRole(inventory.courseCode, prevRole))
                val prevBridgeClip = clips.lastOrNull()
                val bridgeExit = prevBridgeMinf?.event("Bridge_ToFull")?.singleOrNull()
                    ?: prevBridgePart?.frames
                    ?: 0L
                val bridgeStart = prevBridgeClip?.start ?: cursor
                val handover = bridgeStart + bridgeExit
                val fromBridge = record?.event("Full_FromBridge")?.singleOrNull()
                    ?: record?.event("Full_SkipForBridge")?.singleOrNull()
                val fullPhase = record?.phase ?: 0L
                if (fromBridge != null) {
                    sourceOffset = fromBridge.coerceIn(0L, part.frames - 1L)
                    clipStart = handover.coerceAtLeast(0L)
                } else {
                    sourceOffset = 0L
                    clipStart = (handover - fullPhase).coerceAtLeast(0L)
                }
            } else if (!isFirst && step.roleKey in setOf("Main", "Area1", "Area2", "Area3", "Final", "Fast", "Pattern2", "Fast_Pattern2", "Region2", "Region3", "Region4", "Region5")) {
                val phase = record?.phase ?: 0L
                if (step.roleKey == "Fast" && prevRole == "Main" && inventory.hasFanfareAsset && inventory.courseCode != "Rainbow") {
                    val fanfareHeader = resolver.findByFileName(FinalLapFanfareCompiler.ASSET)
                    if (fanfareHeader != null) {
                        digests[fanfareHeader.stem] = fanfareHeader.digest
                        clips += CourseMedleyCatalog.MedleyClip(
                            stem = fanfareHeader.stem,
                            start = cursor,
                            sourceOffset = 0L,
                            sourceLength = fanfareHeader.frames,
                        )
                        // Median gameplay FinalLapFF -> Fast entry measured at 188,880 frames @ 48 kHz (FinalLapFanfareCompiler).
                        clipStart = cursor + (188_880L * sampleRate / 48_000L)
                    } else {
                        clipStart = (cursor - phase).coerceAtLeast(0L)
                    }
                } else {
                    clipStart = (cursor - phase).coerceAtLeast(0L)
                }
            }

            // Pick verified exit frame for this step.
            var firstExit = (step.exitFrame ?: part.defaultExitFrame).coerceIn(sourceOffset + 1L, part.frames)
            val isLastMainBeforeOutro = (idx == preset.steps.lastIndex && preset.outroNumber != null &&
                step.roleKey in setOf("Main", "Area2", "Region5", "Pattern2"))
            val selectedOutro = preset.outroNumber?.let { num -> inventory.outros.firstOrNull { it.number == num } }

            if (isLastMainBeforeOutro && selectedOutro != null && selectedOutro.mainTriggerPoints.isNotEmpty() && step.loopCount == 0) {
                val targetExit = step.exitFrame ?: firstExit
                val confirmedTrigger = selectedOutro.mainTriggerPoints.firstOrNull { it >= targetExit }
                    ?: selectedOutro.mainTriggerPoints.last()
                firstExit = confirmedTrigger.coerceIn(sourceOffset + 1L, part.frames)
            }

            val firstLength = (firstExit - sourceOffset).coerceAtLeast(1L)
            digests[part.stem] = part.digest
            clips += CourseMedleyCatalog.MedleyClip(
                stem = part.stem,
                start = clipStart,
                sourceOffset = sourceOffset,
                sourceLength = firstLength,
            )

            // Secondary layer for Rainbow Area1 (Area1_Inst + Area1_Melody stay sample-locked).
            part.secondaryStem?.let { secStem ->
                val secHeader = resolver.findByFileName("$secStem.bwav") ?: return null
                digests[secStem] = secHeader.digest
                clips += CourseMedleyCatalog.MedleyClip(
                    stem = secStem,
                    start = clipStart,
                    sourceOffset = sourceOffset,
                    sourceLength = firstLength.coerceAtMost(secHeader.frames - sourceOffset),
                )
            }

            var stepEnd = clipStart + firstLength
            if (part.supportsLoop && step.loopCount > 0) {
                val loopStart = part.loopStart!!
                val loopEnd = part.loopEnd!!
                repeat(step.loopCount) { loopIdx ->
                    val isFinalLoopBeforeOutro = isLastMainBeforeOutro && selectedOutro != null &&
                        loopIdx == step.loopCount - 1 && selectedOutro.mainTriggerPoints.isNotEmpty()
                    val cycleEnd = if (isFinalLoopBeforeOutro) {
                        val validTriggers = selectedOutro!!.mainTriggerPoints.filter { it > loopStart && it <= part.frames }
                        validTriggers.lastOrNull() ?: loopEnd
                    } else {
                        loopEnd
                    }
                    val len = (cycleEnd - loopStart).coerceAtLeast(1L)
                    clips += CourseMedleyCatalog.MedleyClip(
                        stem = part.stem,
                        start = stepEnd,
                        sourceOffset = loopStart,
                        sourceLength = len,
                    )
                    part.secondaryStem?.let { secStem ->
                        clips += CourseMedleyCatalog.MedleyClip(
                            stem = secStem,
                            start = stepEnd,
                            sourceOffset = loopStart,
                            sourceLength = len,
                        )
                    }
                    stepEnd += len
                }
            }

            cursor = if (step.roleKey in setOf("Bridge", "Area1_Bridge") && idx < preset.steps.lastIndex) {
                // Next step aligns against Bridge_ToFull rather than waiting for full Bridge file tail.
                clipStart + (record?.event("Bridge_ToFull")?.singleOrNull() ?: part.frames)
            } else {
                stepEnd
            }
        }

        val endingOutro = preset.outroNumber?.let { num -> inventory.outros.firstOrNull { it.number == num } }
        var outroHandoverFrame: Long? = null
        if (endingOutro != null) {
            digests[endingOutro.stem] = endingOutro.digest
            val outroStart = cursor
            clips += CourseMedleyCatalog.MedleyClip(
                stem = endingOutro.stem,
                start = outroStart,
                sourceOffset = 0L,
                sourceLength = endingOutro.frames,
            )
            if (preset.outroToNextPre) {
                endingOutro.outroToNextPreFrame?.let { evt ->
                    outroHandoverFrame = outroStart + evt
                }
            }
            cursor = outroStart + endingOutro.frames
        } else if (preset.fadeOutMs > 0 && clips.isNotEmpty()) {
            val fadeFrames = (preset.fadeOutMs.toLong() * sampleRate / 1000L)
            val lastIdx = clips.lastIndex
            val lastClip = clips[lastIdx]
            val clampedFade = fadeFrames.coerceIn(0L, (lastClip.sourceLength - lastClip.fadeIn).coerceAtLeast(0L))
            clips[lastIdx] = lastClip.copy(fadeOut = clampedFade)
            // Keep secondary dual-track clip aligned if ending on Area1 fadeout.
            if (lastIdx > 0 && clips[lastIdx - 1].start == lastClip.start && clips[lastIdx - 1].sourceLength == lastClip.sourceLength) {
                val sec = clips[lastIdx - 1]
                clips[lastIdx - 1] = sec.copy(fadeOut = clampedFade)
            }
        }

        val totalFrames = clips.maxOfOrNull { it.start + it.sourceLength } ?: return null
        val firstClip = clips.first()
        val firstRole = preset.steps.first().roleKey
        val firstMinf = findMinfRecord(
            minf,
            minfStageFor(inventory.courseCode, firstRole),
            mapRoleKeyToMinfRole(inventory.courseCode, firstRole),
        )
        val medley = CourseMedleyCatalog.Medley(
            id = "${inventory.courseCode}_${preset.id}",
            index = 0,
            courseCode = inventory.courseCode,
            variant = "custom",
            totalFrames = totalFrames,
            durationMs = EngineSnapshot.framesToMillis(totalFrames, sampleRate),
            clips = clips,
            uniqueStems = digests.keys.toList(),
        )
        return CourseMedleyCompiler.ResolvedMedley(
            medley = medley,
            sampleRate = sampleRate,
            stemDigests = digests,
            outroHandoverFrame = outroHandoverFrame?.takeIf { it in 1L..totalFrames },
            incomingPrePhase = if (firstRole in setOf("Pre_Short", "Pre", "Area1_Pre", "Bridge")) {
                firstMinf?.phase ?: 0L
            } else {
                0L
            },
        )
    }

    fun resolveMedleyHandoverFrame(medley: CourseMedleyCatalog.Medley, minf: MinfCatalog): Long? {
        val lastClip = medley.clips.lastOrNull() ?: return null
        val outroEvt = findOutroHandoverFrameForStem(medley.courseCode, lastClip.stem, minf) ?: return null
        val timelineHandover = lastClip.start + (outroEvt - lastClip.sourceOffset)
        return timelineHandover.takeIf { it in 1L..medley.totalFrames }
    }

    fun resolveIncomingPhase(medley: CourseMedleyCatalog.Medley, minf: MinfCatalog): Long {
        val firstClip = medley.clips.firstOrNull() ?: return 0L
        val role = inferMinfRoleFromStem(medley.courseCode, firstClip.stem) ?: return 0L
        if (!role.startsWith("Pre") && role != "Area1_Pre" && !role.startsWith("Bridge")) return 0L
        val record = findMinfRecord(minf, medley.courseCode, listOf(role)) ?: return 0L
        return record.phase.coerceAtLeast(0L)
    }

    private fun inferMinfRoleFromStem(stage: String, stem: String): String? {
        if (stage == "Rainbow" && stem.startsWith("BGM_Rainbow_")) {
            return stem.removePrefix("BGM_Rainbow_")
        }
        val prefix = "BGM_Stage_${stage}_"
        if (stem == "BGM_Stage_$stage") return "Full"
        if (!stem.startsWith(prefix)) return null
        return when (val suffix = stem.removePrefix(prefix)) {
            "Pre_Short" -> if (stage == "IceBuildings") "Pre_Short_2" else "Pre_Short"
            "Pre" -> if (stage == "IceBuildings") "Pre_2" else "Pre"
            "FL" -> "FL"
            "Fast" -> "Fast"
            else -> suffix
        }
    }

    private fun findOutroHandoverFrame(stage: String, outroNumber: Int, stem: String, minf: MinfCatalog): Long? {
        findOutroHandoverFrameForStem(stage, stem, minf)?.let { return it }
        val explicit = "Outro%02d".format(outroNumber)
        return minf.records
            .firstOrNull { it.stage == stage && (it.role == explicit || it.role == "Area2_Outro") }
            ?.event("Outro_ToNextPre")
            ?.singleOrNull()
    }

    private fun findOutroHandoverFrameForStem(stage: String, stem: String, minf: MinfCatalog): Long? {
        val byLogical = minf.records.filter {
            it.stage == stage && it.logicalName.equals(stem, ignoreCase = true)
        }
        for (record in byLogical) {
            record.event("Outro_ToNextPre")?.singleOrNull()?.let { return it }
        }
        val role = inferMinfRoleFromStem(stage, stem) ?: return null
        val byRole = minf.records.filter { it.stage == stage && it.role == role }
        for (record in byRole) {
            record.event("Outro_ToNextPre")?.singleOrNull()?.let { return it }
        }
        // Handle grouped outro records like Outro / Outro00020305 / Outro010203
        val numMatch = Regex("Outro(\\d{2})$").find(stem)?.groupValues?.get(1)
        if (numMatch != null) {
            val grouped = minf.records.filter {
                it.stage == stage && it.role.startsWith("Outro") &&
                    (it.role == "Outro" || it.role.contains(numMatch))
            }
            for (record in grouped) {
                record.event("Outro_ToNextPre")?.singleOrNull()?.let { return it }
            }
        }
        return null
    }

    private fun findMinfRecord(minf: MinfCatalog, stage: String, roles: List<String>): MinfCatalog.Record? {
        for (role in roles) {
            minf.part(stage, role)?.let { return it }
            minf.records.firstOrNull { it.stage == stage && it.role == role }?.let { return it }
        }
        return null
    }

    private fun collectExitCandidates(
        roleKey: String,
        record: MinfCatalog.Record?,
        frames: Long,
        sampleRate: Int,
    ): List<ExitCandidate> {
        if (record == null) {
            return listOf(ExitCandidate(frames, formatTime(frames, sampleRate)))
        }
        val rawFrames = buildList {
            if (roleKey.startsWith("Bridge") || roleKey == "Area1_Bridge") {
                record.event("Bridge_ToFull")?.singleOrNull()?.let { add(it) }
            }
            addAll(record.loopCandidates)
            if (record.loopCandidates.isEmpty()) {
                addAll(record.beats.filter { it.label == 1 }.map { it.frame })
            }
            add(frames)
        }.filter { it in 1L..frames }.distinct().sorted()

        return if (rawFrames.size <= 16) {
            rawFrames.map { ExitCandidate(it, formatTime(it, sampleRate)) }
        } else {
            val step = (rawFrames.size - 1) / 14.0
            val sampled = (0..13).map { i -> rawFrames[(i * step).toInt()] } + rawFrames.last()
            sampled.distinct().map { ExitCandidate(it, formatTime(it, sampleRate)) }
        }
    }

    private fun formatTime(frames: Long, sampleRate: Int): String =
        EngineSnapshot.formatPosition(frames, sampleRate)

    private data class RoleDef(
        val roleKey: String,
        val minfStage: String,
        val minfRoles: List<String>,
        val candidateFileNames: List<String>,
        val secondaryFileName: String? = null,
    )

    private fun standardRoleDefinitions(stage: String): List<RoleDef> = buildList {
        add(RoleDef("Pre_Short", stage, listOf("Pre_Short", "Pre_Short_2"), listOf("BGM_Stage_${stage}_Pre_Short.bwav")))
        add(RoleDef("Pre", stage, listOf("Pre", "Pre_2"), listOf("BGM_Stage_${stage}_Pre.bwav")))
        add(RoleDef("Bridge", stage, listOf("Bridge", "Bridge_Short_1"), listOf("BGM_Stage_${stage}_Bridge.bwav")))
        add(RoleDef("Main", stage, listOf("Full"), listOf("BGM_Stage_$stage.bwav")))
        if (stage == "DonkeyKong") {
            for (n in 2..5) {
                add(RoleDef("Region$n", "${stage}_Pattern$n", listOf("Full"), listOf("BGM_Stage_${stage}_Pattern$n.bwav")))
            }
        }
        if (stage in setOf("Cinema", "SnowMountain")) {
            add(RoleDef("Pattern2", "${stage}_Pattern2", listOf("Full"), listOf("BGM_Stage_${stage}_Pattern2.bwav")))
            add(RoleDef("Fast_Pattern2", "${stage}_Pattern2", listOf("Fast"), listOf("BGM_Stage_${stage}_Fast_Pattern2.bwav")))
        }
        add(RoleDef("Fast", stage, listOf("Fast", "FL"), listOf("BGM_Stage_${stage}_Fast.bwav", "BGM_Stage_${stage}_FL.bwav")))
    }

    private fun rainbowRoleDefinitions(): List<RoleDef> = listOf(
        RoleDef("Area1_Pre", "Rainbow", listOf("Area1_Pre"), listOf("BGM_Rainbow_Area1_Pre.bwav")),
        RoleDef("Area1_Bridge", "Rainbow", listOf("Area1_Bridge"), listOf("BGM_Rainbow_Area1_Bridge.bwav")),
        RoleDef("Area1", "Rainbow", listOf("Area1"), listOf("BGM_Rainbow_Area1_Inst.bwav"), "BGM_Rainbow_Area1_Melody.bwav"),
        RoleDef("Area2", "Rainbow", listOf("Area2"), listOf("BGM_Rainbow_Area2.bwav")),
        RoleDef("Area3_Pre", "Rainbow", listOf("Area3_Pre"), listOf("BGM_Rainbow_Area3_Pre.bwav")),
        RoleDef("Area3", "Rainbow", listOf("Area3"), listOf("BGM_Rainbow_Area3.bwav")),
        RoleDef("Final_Pre", "Rainbow", listOf("Final_Pre"), listOf("BGM_Rainbow_Final_Pre.bwav")),
        RoleDef("Final", "Rainbow", listOf("Final"), listOf("BGM_Rainbow_Final.bwav")),
    )

    private fun minfStageFor(courseCode: String, roleKey: String): String = when {
        courseCode == "Rainbow" -> "Rainbow"
        roleKey.startsWith("Region") -> "${courseCode}_Pattern${roleKey.removePrefix("Region")}"
        roleKey.contains("Pattern2") -> "${courseCode}_Pattern2"
        else -> courseCode
    }

    private fun mapRoleKeyToMinfRole(courseCode: String, roleKey: String): List<String> = when {
        courseCode == "Rainbow" -> listOf(roleKey)
        roleKey == "Pre_Short" -> listOf("Pre_Short", "Pre_Short_2")
        roleKey == "Pre" -> listOf("Pre", "Pre_2")
        roleKey == "Bridge" -> listOf("Bridge", "Bridge_Short_1")
        roleKey == "Main" || roleKey == "Pattern2" || roleKey.startsWith("Region") -> listOf("Full")
        roleKey == "Fast" || roleKey == "Fast_Pattern2" -> listOf("Fast", "FL")
        else -> listOf(roleKey)
    }
}
