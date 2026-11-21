package com.mylittleblover.mkwplayer.ui.interactive

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mylittleblover.mkwplayer.R
import com.mylittleblover.mkwplayer.audio.BwavWaveformExtractor
import com.mylittleblover.mkwplayer.audio.EngineSnapshot
import com.mylittleblover.mkwplayer.interactive.CourseFlowRecipeBuilder
import com.mylittleblover.mkwplayer.library.CoursePreset
import com.mylittleblover.mkwplayer.library.FlowStep
import com.mylittleblover.mkwplayer.library.PresetRefs
import com.mylittleblover.mkwplayer.ui.DragHandlePill
import com.mylittleblover.mkwplayer.ui.rememberPressScale
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Event-driven course flow preset builder.
 *
 * Enforces authentic interactive graph transitions ([CourseFlowRecipeBuilder.CourseInventory.allowedNextRoles]),
 * keeps Bridge parts locked to MINF `Bridge_ToFull` handover markers, provides per-step expandable
 * zoomable BWAV waveforms with magnetic exit snapping, inline seam auditioning, and JSON export.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun FlowEditorScreen(
    courseTitle: String,
    inventory: CourseFlowRecipeBuilder.CourseInventory,
    existingPreset: CoursePreset?,
    onAudition: (CoursePreset) -> Unit,
    onSave: (CoursePreset) -> Unit,
    modifier: Modifier = Modifier,
    onLoadWaveform: suspend (stem: String) -> BwavWaveformExtractor.WaveformPeaks? = { null },
    onPreviewSeam: (CoursePreset, seamIndex: Int) -> Unit = { _, _ -> },
    onCompileExport: (CoursePreset) -> String? = { null },
) {
    val defaultName = remember(courseTitle, existingPreset) {
        existingPreset?.name ?: "$courseTitle Custom"
    }
    var presetName by rememberSaveable(existingPreset?.id, inventory.courseCode) {
        mutableStateOf(defaultName)
    }
    val stepsSaver = remember(inventory.courseCode) {
        androidx.compose.runtime.saveable.listSaver<List<FlowStep>, Any>(
            save = { list ->
                list.flatMap { step ->
                    listOf(step.roleKey, step.loopCount, step.exitFrame ?: -1L)
                }
            },
            restore = { flat ->
                flat.chunked(3).mapNotNull { chunk ->
                    if (chunk.size < 3) null
                    else {
                        val roleKey = chunk[0] as? String ?: return@mapNotNull null
                        val loopCount = (chunk[1] as? Number)?.toInt() ?: 0
                        val rawExit = (chunk[2] as? Number)?.toLong() ?: -1L
                        FlowStep(roleKey, loopCount, rawExit.takeIf { it > 0L })
                    }
                }
            },
        )
    }
    var steps by rememberSaveable(existingPreset?.id, inventory.courseCode, stateSaver = stepsSaver) {
        mutableStateOf(
            (existingPreset?.steps ?: inventory.defaultEditorSteps()).map { step ->
                val part = inventory.part(step.roleKey)
                if (part?.isBridgeLocked == true) {
                    step.copy(loopCount = 0, exitFrame = null)
                } else {
                    step
                }
            },
        )
    }
    var selectedOutroNumber by rememberSaveable(existingPreset?.id, inventory.courseCode) {
        mutableStateOf(existingPreset?.outroNumber)
    }
    var fadeOutMs by rememberSaveable(existingPreset?.id, inventory.courseCode) {
        mutableIntStateOf(existingPreset?.fadeOutMs ?: 3000)
    }
    var expandedWaveformStep by rememberSaveable(existingPreset?.id, inventory.courseCode) {
        mutableStateOf<Int?>(null)
    }

    val waveformCache = remember(inventory.courseCode) {
        mutableStateMapOf<String, BwavWaveformExtractor.WaveformPeaks?>()
    }
    val waveformLoaded = remember(inventory.courseCode) {
        mutableStateMapOf<String, Boolean>()
    }

    // Ensure outro selection remains strictly event-legal and locks/snaps the host exit point on courses
    LaunchedEffect(steps, selectedOutroNumber, inventory) {
        if (selectedOutroNumber != null && !inventory.supportsOutroEnding(steps)) {
            selectedOutroNumber = null
            return@LaunchedEffect
        }
        if (inventory.courseCode.startsWith("extra:")) return@LaunchedEffect
        val outroNum = selectedOutroNumber ?: return@LaunchedEffect
        val outro = inventory.outros.firstOrNull { it.number == outroNum } ?: return@LaunchedEffect
        if (outro.mainTriggerPoints.isEmpty() || steps.isEmpty()) return@LaunchedEffect
        val lastStep = steps.last()
        val lastPart = inventory.part(lastStep.roleKey)
        val validTriggers = if (lastStep.loopCount > 0 && lastPart?.loopStart != null) {
            outro.mainTriggerPoints.filter { it > lastPart.loopStart && it <= lastPart.frames }
                .ifEmpty { outro.mainTriggerPoints }
        } else {
            outro.mainTriggerPoints
        }.distinct().sorted()
        if (validTriggers.isNotEmpty() && lastStep.exitFrame !in validTriggers) {
            val snapped = if (validTriggers.size == 1) {
                validTriggers.single()
            } else {
                validTriggers.firstOrNull { it >= (lastStep.exitFrame ?: 0L) } ?: validTriggers.last()
            }
            steps = steps.dropLast(1) + lastStep.copy(exitFrame = snapped)
        }
    }

    // Preload waveform lazily whenever a step waveform panel is opened
    LaunchedEffect(expandedWaveformStep, steps) {
        val idx = expandedWaveformStep ?: return@LaunchedEffect
        val roleKey = steps.getOrNull(idx)?.roleKey ?: return@LaunchedEffect
        val stem = inventory.part(roleKey)?.stem ?: return@LaunchedEffect
        if (waveformLoaded[stem] != true) {
            waveformCache[stem] = onLoadWaveform(stem)
            waveformLoaded[stem] = true
        }
    }

    fun draftPreset(): CoursePreset {
        val stableId = existingPreset?.id ?: PresetRefs.USER_PREFIX + UUID.randomUUID().toString()
        val effectiveOutro = selectedOutroNumber.takeIf { inventory.supportsOutroEnding(steps) }
        return CoursePreset(
            id = stableId,
            courseCode = inventory.courseCode,
            name = presetName.trim().ifBlank { defaultName },
            steps = steps.map { step ->
                if (inventory.part(step.roleKey)?.isBridgeLocked == true) {
                    step.copy(loopCount = 0, exitFrame = null)
                } else {
                    step
                }
            },
            outroNumber = effectiveOutro,
            fadeOutMs = if (effectiveOutro == null) fadeOutMs else 0,
            outroToNextPre = effectiveOutro != null,
            updatedAtEpochMs = System.currentTimeMillis(),
        )
    }

    val compiledStats = remember(steps, selectedOutroNumber, fadeOutMs, inventory) {
        CourseFlowRecipeBuilder.estimateTimelineFrames(steps, selectedOutroNumber, fadeOutMs, inventory)
    }

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var exportedJson by remember { mutableStateOf<String?>(null) }

    val msgCopied = stringResource(R.string.flow_editor_export_copied)
    val msgSaved = stringResource(R.string.flow_editor_export_saved)
    val msgExportFailed = stringResource(R.string.flow_editor_export_failed)

    val saveJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val json = exportedJson ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    writer.write(json)
                }
            }.onSuccess {
                scope.launch { snackbarHostState.showSnackbar(msgSaved) }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = courseTitle,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = stringResource(
                                    R.string.flow_editor_timeline_total,
                                    EngineSnapshot.formatPosition(compiledStats, inventory.sampleRate),
                                    steps.size,
                                ),
                                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        val (exportScale, exportSrc) = rememberPressScale()
                        FilledTonalButton(
                            onClick = {
                                val json = onCompileExport(draftPreset())
                                if (json != null) {
                                    exportedJson = json
                                } else {
                                    scope.launch { snackbarHostState.showSnackbar(msgExportFailed) }
                                }
                            },
                            enabled = steps.isNotEmpty(),
                            interactionSource = exportSrc,
                            modifier = Modifier.heightIn(min = 42.dp).then(exportScale),
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.flow_editor_export_button))
                        }
                    }

                    OutlinedTextField(
                        value = presetName,
                        onValueChange = { presetName = it.take(64) },
                        label = { Text(stringResource(R.string.flow_editor_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Quick templates strictly adhering to event-driven flow rules
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.flow_editor_templates_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val (stdScale, stdSrc) = rememberPressScale()
                        AssistChip(
                            onClick = {
                                steps = inventory.standardMedleyTemplate()
                                selectedOutroNumber = inventory.outros.firstOrNull()?.number
                                expandedWaveformStep = null
                            },
                            interactionSource = stdSrc,
                            modifier = stdScale,
                            label = { Text(stringResource(R.string.flow_editor_template_standard)) },
                        )
                        if (inventory.courseCode != "Rainbow") {
                            val (fastScale, fastSrc) = rememberPressScale()
                            AssistChip(
                                onClick = {
                                    steps = inventory.fastRaceTemplate()
                                    selectedOutroNumber = null
                                    expandedWaveformStep = null
                                },
                                interactionSource = fastSrc,
                                modifier = fastScale,
                                label = { Text(stringResource(R.string.flow_editor_template_fast)) },
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.flow_editor_steps_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (steps.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.flow_editor_no_steps),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }

            itemsIndexed(steps, key = { idx, step -> "$idx:${step.roleKey}" }) { index, step ->
                val part = inventory.part(step.roleKey)
                val prevRole = steps.getOrNull(index - 1)?.roleKey
                val canRemoveStep = index == steps.lastIndex || canSafelyRemoveMiddle(steps, index, inventory)
                val isWaveformOpen = expandedWaveformStep == index
                val peaks = part?.stem?.let { waveformCache[it] }
                val waveformReady = part?.stem?.let { waveformLoaded[it] == true } ?: false
                val nextStep = steps.getOrNull(index + 1)
                val middlePairedOutro = if (inventory.courseCode == "Rainbow" &&
                    step.roleKey == "Area2" &&
                    nextStep?.roleKey?.startsWith("Area2_Outro") == true
                ) {
                    nextStep.roleKey.removePrefix("Area2_Outro").toIntOrNull()
                        ?.let { n -> inventory.outros.firstOrNull { it.number == n } }
                } else null
                val area2OutroStepInfo = if (inventory.courseCode == "Rainbow" && step.roleKey.startsWith("Area2_Outro")) {
                    step.roleKey.removePrefix("Area2_Outro").toIntOrNull()
                        ?.let { n -> inventory.outros.firstOrNull { it.number == n } }
                } else null
                val isArea2OutroHandover = area2OutroStepInfo != null &&
                    index < steps.lastIndex &&
                    nextStep?.roleKey == "Area3_Pre"
                val isTailOutroHost = !inventory.courseCode.startsWith("extra:") &&
                    index == steps.lastIndex &&
                    inventory.supportsOutroEnding(steps)
                val activeOutro = middlePairedOutro ?: if (isTailOutroHost) {
                    selectedOutroNumber?.let { n -> inventory.outros.firstOrNull { it.number == n } }
                } else null
                val outroTriggers = if (activeOutro != null && part != null) {
                    val l0 = part.loopStart
                    val base = activeOutro.mainTriggerPoints.distinct().sorted()
                    if (step.loopCount > 0 && l0 != null) {
                        base.filter { it > l0 && it <= part.frames }.ifEmpty { base }
                    } else {
                        base
                    }
                } else emptyList()

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowStepEventCard(
                        index = index,
                        step = step,
                        part = part,
                        prevRole = prevRole,
                        incomingEventLabel = inventory.describeTransitionEvent(prevRole, step.roleKey),
                        canRemove = canRemoveStep,
                        isWaveformExpanded = isWaveformOpen,
                        waveformReady = waveformReady,
                        peaks = peaks,
                        activeOutroNumber = activeOutro?.number,
                        outroTriggers = outroTriggers,
                        area2OutroHandoverFrame = if (isArea2OutroHandover) area2OutroStepInfo?.outroToNextPreFrame else null,
                        rainbowArea2Outros = if (inventory.courseCode == "Rainbow" && step.roleKey == "Area2") inventory.outros else emptyList(),
                        selectedRainbowArea2Outro = middlePairedOutro?.number,
                        onSelectRainbowArea2Outro = { chosenNum ->
                            val updatedSteps = steps.toMutableList()
                            val hasMiddleOutro = updatedSteps.getOrNull(index + 1)?.roleKey?.startsWith("Area2_Outro") == true
                            if (chosenNum == null) {
                                if (hasMiddleOutro) {
                                    updatedSteps.removeAt(index + 1)
                                }
                                updatedSteps[index] = step.copy(exitFrame = part?.defaultExitFrame)
                                if (index == updatedSteps.lastIndex) selectedOutroNumber = null
                            } else {
                                val targetRole = "Area2_Outro%02d".format(chosenNum)
                                val chosenOutro = inventory.outros.firstOrNull { it.number == chosenNum }
                                val outroPart = inventory.part(targetRole)
                                val lockedExit = chosenOutro?.mainTriggerPoints?.firstOrNull() ?: step.exitFrame ?: part?.defaultExitFrame
                                updatedSteps[index] = step.copy(exitFrame = lockedExit)
                                val outroStep = FlowStep(
                                    roleKey = targetRole,
                                    loopCount = 0,
                                    exitFrame = outroPart?.frames,
                                )
                                if (hasMiddleOutro) {
                                    updatedSteps[index + 1] = outroStep
                                } else {
                                    updatedSteps.add(index + 1, outroStep)
                                }
                                selectedOutroNumber = null
                            }
                            steps = updatedSteps
                        },
                        onToggleWaveform = {
                            expandedWaveformStep = if (isWaveformOpen) null else index
                        },
                        onRemove = {
                            if (canRemoveStep) {
                                steps = steps.toMutableList().apply { removeAt(index) }
                                if (expandedWaveformStep == index) expandedWaveformStep = null
                                else if ((expandedWaveformStep ?: -1) > index) expandedWaveformStep = expandedWaveformStep!! - 1
                            }
                        },
                        onUpdate = { updated ->
                            if (part?.isBridgeLocked != true && !isArea2OutroHandover) {
                                val nextStepVal = if (outroTriggers.size == 1) {
                                    updated.copy(exitFrame = outroTriggers.single())
                                } else {
                                    updated
                                }
                                steps = steps.toMutableList().apply { set(index, nextStepVal) }
                            }
                        },
                    )

                    // Seam connector & Seam Preview ("衔接预览") between adjacent steps
                    val updatedNext = steps.getOrNull(index + 1)
                    if (updatedNext != null) {
                        SeamTransitionRow(
                            eventLabel = inventory.describeTransitionEvent(step.roleKey, updatedNext.roleKey),
                            onPreviewSeam = { onPreviewSeam(draftPreset(), index) },
                        )
                    } else if (selectedOutroNumber != null && inventory.supportsOutroEnding(steps)) {
                        SeamTransitionRow(
                            eventLabel = "Outro %02d Handover".format(selectedOutroNumber),
                            onPreviewSeam = { onPreviewSeam(draftPreset(), index) },
                        )
                    }
                }
            }

            // Event-driven next transition picker
            item {
                val allowedNext = remember(steps, inventory) {
                    inventory.allowedNextRoles(steps)
                }
                ElevatedCard(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.flow_editor_next_event_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (allowedNext.isEmpty()) {
                            Text(
                                text = stringResource(R.string.flow_editor_no_next_event),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            val tailRole = steps.lastOrNull()?.roleKey
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                allowedNext.forEach { role ->
                                    val (chipScale, chipSrc) = rememberPressScale()
                                    val part = inventory.part(role)
                                    val eventText = if (tailRole == null) {
                                        roleDisplayLabel(role)
                                    } else {
                                        inventory.describeTransitionEvent(tailRole, role)
                                    }
                                    AssistChip(
                                        onClick = {
                                            val defaultLoop = if (part?.supportsLoop == true &&
                                                (role in setOf("Main", "Main_Speaker", "Pattern2", "Area1_Inst", "Area1", "Area2", "Area3", "Final") || role.startsWith("Region"))
                                            ) 1 else 0
                                            steps = steps + FlowStep(
                                                roleKey = role,
                                                loopCount = defaultLoop,
                                                exitFrame = if (part?.isBridgeLocked == true) null else part?.defaultExitFrame,
                                            )
                                        },
                                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        interactionSource = chipSrc,
                                        modifier = chipScale,
                                        label = { Text(eventText) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Verified Outro / Fade ending
            item {
                val outroAllowed = remember(steps, inventory) {
                    inventory.supportsOutroEnding(steps)
                }
                val noFadeLabel = stringResource(R.string.flow_editor_no_fade)
                val options = buildList<DrawerOption<Pair<Int?, Int>>> {
                    if (outroAllowed) {
                        inventory.outros.forEach { outro ->
                            add(
                                DrawerOption(
                                    value = outro.number to 0,
                                    label = stringResource(R.string.flow_editor_outro_item, outro.number),
                                    subtitle = if (outro.supportsNativeHandover) {
                                        stringResource(R.string.flow_editor_outro_native_ready)
                                    } else null,
                                ),
                            )
                        }
                    }
                    listOf(1000, 2000, 3000, 5000, 10000, 0).forEach { ms ->
                        add(
                            DrawerOption(
                                value = null to ms,
                                label = if (ms == 0) noFadeLabel
                                else stringResource(R.string.flow_editor_outro_none_fade, ms / 1000),
                            ),
                        )
                    }
                }
                val currentSelection: Pair<Int?, Int> =
                    if (outroAllowed && selectedOutroNumber != null) {
                        selectedOutroNumber to 0
                    } else {
                        null to fadeOutMs
                    }
                val currentLabel = options.firstOrNull { it.value == currentSelection }?.label
                    ?: stringResource(R.string.flow_editor_outro_none_fade, fadeOutMs / 1000)
                SelectionDrawerRow(
                    label = stringResource(R.string.flow_editor_outro_and_fade_title),
                    selectedLabel = currentLabel,
                    sheetTitle = stringResource(R.string.flow_editor_outro_and_fade_title),
                    options = options,
                    selectedValue = currentSelection,
                    onSelect = { (outroNum, fadeMsChosen) ->
                        selectedOutroNumber = outroNum
                        if (outroNum == null) {
                            fadeOutMs = fadeMsChosen
                        }
                    },
                )
            }

            // Bottom audition & save bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val (audScale, audSrc) = rememberPressScale()
                    OutlinedButton(
                        onClick = { onAudition(draftPreset()) },
                        enabled = steps.isNotEmpty(),
                        interactionSource = audSrc,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).then(audScale),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.flow_editor_audition_step))
                    }
                    val (saveScale, saveSrc) = rememberPressScale()
                    Button(
                        onClick = { onSave(draftPreset()) },
                        enabled = steps.isNotEmpty() && presetName.isNotBlank(),
                        interactionSource = saveSrc,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).then(saveScale),
                    ) {
                        Text(stringResource(R.string.flow_editor_save))
                    }
                }
            }
        }
    }

    // Export Flow Config Bottom Sheet
    val jsonToExport = exportedJson
    if (jsonToExport != null) {
        val exportSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { exportedJson = null },
            sheetState = exportSheetState,
            dragHandle = { DragHandlePill() },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.flow_editor_export_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                            .padding(12.dp),
                    ) {
                        Text(
                            text = jsonToExport,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val (copyScale, copySrc) = rememberPressScale()
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(jsonToExport))
                            scope.launch { snackbarHostState.showSnackbar(msgCopied) }
                        },
                        interactionSource = copySrc,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).then(copyScale),
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.flow_editor_export_copy))
                    }
                    val (dlScale, dlSrc) = rememberPressScale()
                    Button(
                        onClick = {
                            val fileName = "${inventory.courseCode.lowercase()}_medley_flow.json"
                            saveJsonLauncher.launch(fileName)
                        },
                        interactionSource = dlSrc,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).then(dlScale),
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.flow_editor_export_save_file))
                    }
                }
            }
        }
    }
}

@Composable
private fun SeamTransitionRow(
    eventLabel: String,
    onPreviewSeam: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = eventLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        val (seamScale, seamSrc) = rememberPressScale()
        Surface(
            onClick = onPreviewSeam,
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            interactionSource = seamSrc,
            modifier = seamScale,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = stringResource(R.string.flow_editor_preview_seam),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowStepEventCard(
    index: Int,
    step: FlowStep,
    part: CourseFlowRecipeBuilder.StagePartInfo?,
    prevRole: String?,
    incomingEventLabel: String,
    canRemove: Boolean,
    isWaveformExpanded: Boolean,
    waveformReady: Boolean,
    peaks: BwavWaveformExtractor.WaveformPeaks?,
    activeOutroNumber: Int?,
    outroTriggers: List<Long>,
    area2OutroHandoverFrame: Long? = null,
    rainbowArea2Outros: List<CourseFlowRecipeBuilder.OutroOption> = emptyList(),
    selectedRainbowArea2Outro: Int? = null,
    onSelectRainbowArea2Outro: (Int?) -> Unit = {},
    onToggleWaveform: () -> Unit,
    onRemove: () -> Unit,
    onUpdate: (FlowStep) -> Unit,
) {
    val isLockedBridge = part?.isBridgeLocked == true
    val isOutroHandoverLocked = area2OutroHandoverFrame != null
    val isOutroSingleLocked = !isLockedBridge && !isOutroHandoverLocked && activeOutroNumber != null && outroTriggers.size == 1
    val isOutroMultiConstrained = !isLockedBridge && !isOutroHandoverLocked && activeOutroNumber != null && outroTriggers.size > 1
    val isExitLocked = isLockedBridge || isOutroHandoverLocked || isOutroSingleLocked
    val activeExitFrame = when {
        isLockedBridge -> part?.bridgeHandoverFrame ?: part?.frames ?: 1L
        isOutroHandoverLocked -> area2OutroHandoverFrame!!
        isOutroSingleLocked -> outroTriggers.single()
        isOutroMultiConstrained -> step.exitFrame?.takeIf { it in outroTriggers } ?: outroTriggers.last()
        else -> step.exitFrame ?: part?.defaultExitFrame ?: part?.frames ?: 1L
    }

    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "${index + 1}. ${roleDisplayLabel(step.roleKey)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (isExitLocked) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                    if (prevRole != null) {
                        Text(
                            text = incomingEventLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (part != null) {
                        val exitTime = EngineSnapshot.formatPosition(activeExitFrame, part.sampleRate)
                        val totalTime = EngineSnapshot.formatPosition(part.frames, part.sampleRate)
                        Text(
                            text = when {
                                isLockedBridge -> "Bridge_ToFull $exitTime / $totalTime"
                                isOutroHandoverLocked -> stringResource(R.string.flow_editor_area2_outro_handover, exitTime, totalTime)
                                isOutroSingleLocked -> "Outro %02d Exit %s / %s".format(activeOutroNumber, exitTime, totalTime)
                                isOutroMultiConstrained -> "Outro %02d Fixed Cue %s / %s".format(activeOutroNumber, exitTime, totalTime)
                                else -> "$exitTime / $totalTime"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (canRemove) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.flow_editor_remove))
                    }
                }
            }

            if (isLockedBridge) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Text(
                            text = stringResource(R.string.flow_editor_bridge_locked),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            } else {
                if (isOutroSingleLocked && activeOutroNumber != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Text(
                                text = stringResource(R.string.flow_editor_outro_locked, activeOutroNumber),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                } else if (isOutroMultiConstrained && activeOutroNumber != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.flow_editor_outro_multi_trigger_hint, activeOutroNumber, outroTriggers.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        )
                    }
                }

                if (rainbowArea2Outros.isNotEmpty()) {
                    val noneLabel = stringResource(R.string.flow_editor_area2_outro_none)
                    val area2Options = buildList<DrawerOption<Int?>> {
                        add(DrawerOption(value = null, label = noneLabel))
                        rainbowArea2Outros.forEach { outro ->
                            add(
                                DrawerOption(
                                    value = outro.number,
                                    label = stringResource(R.string.flow_editor_outro_item, outro.number),
                                    subtitle = if (outro.supportsNativeHandover) {
                                        stringResource(R.string.flow_editor_outro_native_ready)
                                    } else null,
                                ),
                            )
                        }
                    }
                    val chosenLabel = area2Options.firstOrNull { it.value == selectedRainbowArea2Outro }?.label ?: noneLabel
                    SelectionDrawerRow(
                        label = stringResource(R.string.flow_editor_area2_outro_title),
                        selectedLabel = chosenLabel,
                        sheetTitle = stringResource(R.string.flow_editor_area2_outro_title),
                        options = area2Options,
                        selectedValue = selectedRainbowArea2Outro,
                        onSelect = onSelectRainbowArea2Outro,
                    )
                }

                if (part != null && part.supportsLoop) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.flow_editor_loop_count, step.loopCount),
                            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { onUpdate(step.copy(loopCount = (step.loopCount - 1).coerceAtLeast(0))) },
                            enabled = step.loopCount > 0,
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "-")
                        }
                        IconButton(
                            onClick = { onUpdate(step.copy(loopCount = (step.loopCount + 1).coerceAtMost(8))) },
                            enabled = step.loopCount < 8,
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "+")
                        }
                    }
                }
            }

            if (part != null) {
                val (waveScale, waveSrc) = rememberPressScale()
                Surface(
                    onClick = onToggleWaveform,
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    interactionSource = waveSrc,
                    modifier = Modifier.fillMaxWidth().then(waveScale),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Default.GraphicEq,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(
                                if (isWaveformExpanded) R.string.flow_editor_waveform_hide else R.string.flow_editor_waveform_show,
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = if (isWaveformExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                AnimatedVisibility(
                    visible = isWaveformExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    val waveformCandidates = if (isOutroMultiConstrained) {
                        outroTriggers.map {
                            CourseFlowRecipeBuilder.ExitCandidate(
                                it,
                                EngineSnapshot.formatPosition(it, part.sampleRate),
                            )
                        }
                    } else {
                        part.exitCandidates
                    }
                    val waveformLockLabel = when {
                        isLockedBridge -> "Bridge_ToFull"
                        isOutroHandoverLocked -> "Outro_ToNextPre"
                        isOutroSingleLocked && activeOutroNumber != null -> "Outro %02d".format(activeOutroNumber)
                        else -> null
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ZoomableWaveformEditor(
                            peaks = if (waveformReady) peaks else null,
                            frames = part.frames,
                            sampleRate = part.sampleRate,
                            beats = part.beats,
                            exitCandidates = waveformCandidates,
                            selectedExitFrame = activeExitFrame,
                            bridgeLocked = isExitLocked,
                            loopStart = part.loopStart,
                            loopEnd = part.loopEnd,
                            constrainToCandidatesOnly = isOutroMultiConstrained,
                            lockLabel = waveformLockLabel,
                            onSelectExitFrame = { chosenFrame ->
                                if (!isExitLocked) {
                                    onUpdate(step.copy(exitFrame = chosenFrame))
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun canSafelyRemoveMiddle(
    steps: List<FlowStep>,
    index: Int,
    inventory: CourseFlowRecipeBuilder.CourseInventory,
): Boolean {
    if (steps.size <= 1) return true
    if (index == steps.lastIndex) return true
    val prefix = steps.subList(0, index)
    val nextRole = steps[index + 1].roleKey
    return nextRole in inventory.allowedNextRoles(prefix)
}

private fun roleDisplayLabel(roleKey: String): String = when {
    roleKey == "Pre_Short" -> "Pre Short"
    roleKey == "Pre" -> "Pre (Intro)"
    roleKey == "Bridge" -> "Bridge"
    roleKey == "Main" -> "Main Loop"
    roleKey == "Main_Speaker" -> "Main (Speaker)"
    roleKey == "Fast" -> "Final Lap (Fast)"
    roleKey == "Fast_Speaker" -> "Final Lap (Speaker)"
    roleKey == "Pattern2" -> "Pattern 2"
    roleKey == "Fast_Pattern2" -> "Fast Pattern 2"
    roleKey == "Area1_Pre" -> "Area 1 Pre"
    roleKey == "Area1_Bridge" -> "Area 1 Bridge"
    roleKey == "Area1_Inst" -> "Area 1 (Inst Only)"
    roleKey == "Area1" -> "Area 1"
    roleKey == "Area2" -> "Area 2"
    roleKey.startsWith("Area2_Outro") -> "Area 2 Outro ${roleKey.removePrefix("Area2_Outro")}"
    roleKey == "Area3_Pre" -> "Area 3 Pre"
    roleKey == "Area3" -> "Area 3"
    roleKey == "Final_Pre" -> "Final Lap Pre"
    roleKey == "Final" -> "Final Lap"
    else -> roleKey.replace('_', ' ')
}
