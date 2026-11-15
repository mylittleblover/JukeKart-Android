package com.mylittleblover.mkwplayer.ui.interactive

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import com.mylittleblover.mkwplayer.R
import com.mylittleblover.mkwplayer.audio.EngineSnapshot
import com.mylittleblover.mkwplayer.player.PlayerViewModel
import java.util.Locale
import kotlinx.coroutines.launch

/** A read-only view of Native source/transport clocks; no seek gesture or UI timer. */
@Composable
internal fun InteractivePlaybackTimeline(position: String, flow: PlayerViewModel.FlowUi) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val frame = flow.sourceFrame
        val length = flow.sourceFrames
        if (frame != null && frame >= 0 && length != null && length > 0) {
            com.mylittleblover.mkwplayer.ui.SmoothProgressIndicator(
                progress = (frame.toDouble() / length).toFloat(),
                modifier = Modifier.fillMaxWidth(),
                barHeight = 5.dp,
            )
        }
        val numStyle = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                if (frame != null && frame >= 0) EngineSnapshot.formatPosition(frame, flow.transportRate) else position,
                style = numStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (length != null && length > 0) {
                Text(
                    EngineSnapshot.formatPosition(length, flow.transportRate),
                    style = numStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (flow.voiceCount > 0 && flow.loopStart != null && flow.loopEnd != null && !flow.fading) {
            Text(
                stringResource(
                    R.string.interactive_loop_range,
                    EngineSnapshot.formatPosition(flow.loopStart, flow.transportRate),
                    EngineSnapshot.formatPosition(flow.loopEnd, flow.transportRate),
                ),
                style = numStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (flow.fading) {
            val remaining = flow.fadeRemainingFrames
            Text(
                if (remaining == null) stringResource(R.string.interactive_fading)
                else stringResource(
                    R.string.interactive_fade_remaining,
                    String.format(Locale.ROOT, "%.1f", remaining.toDouble() / flow.transportRate),
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
internal fun InteractiveStageBanner(
    flow: PlayerViewModel.FlowUi,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = flow.performing && flow.voiceCount > 0 && flow.sourceFrame != null
    val pending = flow.pendingStageKey
    val currentLabel = if (flow.performing && flow.voiceCount > 0 && flow.sourceFrame == null) {
        stringResource(R.string.stage_transitioning)
    } else {
        stageText(flow.stageKey ?: flow.performanceStage)
    }
    val containerColor by animateColorAsState(
        targetValue = if (pending != null) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = tween(180, easing = com.mylittleblover.mkwplayer.ui.AppMotion.easeOut),
        label = "stage-banner-bg",
    )
    Surface(
        modifier = modifier.fillMaxWidth().animateContentSize(tween(200, easing = com.mylittleblover.mkwplayer.ui.AppMotion.easeOut)),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(
            1.dp,
            if (pending != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier.size(8.dp).background(
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = CircleShape,
                    ),
                )
                Text(
                    stringResource(R.string.interactive_current_stage_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    currentLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AnimatedVisibility(
                visible = pending != null,
                enter = fadeIn(tween(160)) + expandVertically(tween(180, easing = com.mylittleblover.mkwplayer.ui.AppMotion.easeOut)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(160)),
            ) {
                pending?.let { nextStage ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.interactive_pending_next_cue, stageText(nextStage)),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        if (flow.canCancelPending) {
                            FilledTonalButton(
                                onClick = onCancel,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.heightIn(min = 36.dp),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Undo, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.interactive_action_cancel), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun InteractiveStopBar(canStop: Boolean, canFade: Boolean, fading: Boolean, onStop: () -> Unit, onFade: (Int) -> Unit) {
    var showFade by rememberSaveable { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).heightIn(min = 48.dp)
                .combinedClickable(enabled = canStop, role = Role.Button, onClick = onStop,
                    onLongClickLabel = stringResource(R.string.interactive_fade_choose),
                    onLongClick = if (canFade && !fading) ({ showFade = true }) else null),
            shape = RoundedCornerShape(14.dp),
            color = if (canStop) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (canStop) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            border = if (canStop) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Stop,
                    contentDescription = null,
                    tint = if (canStop) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.interactive_stop),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
    if (showFade) AlertDialog(onDismissRequest = { showFade = false },
        title = { Text(stringResource(R.string.interactive_fade_choose)) },
        text = {
            Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.interactive_fade_hint))
                for (seconds in listOf(1, 3, 5, 10)) {
                    OutlinedButton(onClick = { showFade = false; onFade(seconds) }, modifier = Modifier.fillMaxWidth(), enabled = canStop && !fading) {
                        Text(stringResource(R.string.interactive_fade_seconds, seconds))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showFade = false }) { Text(stringResource(R.string.interactive_close)) } },
    )
}

private data class SelectorOption<T>(
    val value: T,
    val label: String,
    val enabled: Boolean = true,
    val selected: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> InteractiveSelectorBar(
    label: String,
    buttonText: String,
    sheetTitle: String,
    sheetSubtitle: String? = null,
    options: List<SelectorOption<T>>,
    enabled: Boolean,
    showSelectionRadio: Boolean,
    onSelect: (T) -> Unit,
) {
    if (options.isEmpty()) return
    var showSheet by rememberSaveable(sheetTitle) { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    LaunchedEffect(enabled) { if (!enabled) showSheet = false }

    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        OutlinedButton(
            onClick = { showSheet = true },
            enabled = enabled,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            modifier = Modifier.heightIn(min = 40.dp),
        ) {
            Text(buttonText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }

    if (showSheet && enabled) {
        ModalBottomSheet(sheetState = sheet, onDismissRequest = { showSheet = false }) {
            val listModifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
                .let { if (showSelectionRadio) it.selectableGroup() else it }
                .padding(bottom = 24.dp)
            Column(listModifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    sheetTitle,
                    Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                sheetSubtitle?.let { hint ->
                    Text(
                        hint,
                        Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                options.forEach { option ->
                    val itemModifier = if (showSelectionRadio) {
                        Modifier.selectable(
                            selected = option.selected,
                            enabled = option.enabled,
                            role = Role.RadioButton,
                            onClick = {
                                if (!option.selected) onSelect(option.value)
                                scope.launch { sheet.hide(); showSheet = false }
                            },
                        )
                    } else {
                        Modifier.clickable(
                            enabled = option.enabled,
                            role = Role.Button,
                            onClick = {
                                onSelect(option.value)
                                scope.launch { sheet.hide(); showSheet = false }
                            },
                        )
                    }
                    ListItem(
                        headlineContent = {
                            Text(
                                option.label,
                                color = if (option.enabled) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            )
                        },
                        trailingContent = if (showSelectionRadio) {
                            { RadioButton(selected = option.selected, enabled = option.enabled, onClick = null) }
                        } else {
                            {
                                Icon(
                                    Icons.Filled.Flag,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        },
                        modifier = itemModifier.heightIn(min = 54.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun InteractiveEntryChooser(choices: Map<Int, String>, selected: Int?, enabled: Boolean, onSelect: (Int) -> Unit) {
    if (choices.isEmpty()) return
    val selectedName = stageText(choices[selected] ?: choices.values.first())
    InteractiveSelectorBar(
        label = stringResource(R.string.stage_start_label),
        buttonText = selectedName,
        sheetTitle = stringResource(R.string.stage_start_choose),
        options = choices.map { (id, label) ->
            SelectorOption(
                value = id,
                label = stageText(label),
                enabled = true,
                selected = id == selected,
            )
        },
        enabled = enabled,
        showSelectionRadio = true,
        onSelect = onSelect,
    )
}

@Composable
internal fun InteractiveOutroChooser(outros: List<Int>, enabled: Boolean = true, onSelect: (Int) -> Unit) {
    if (outros.size <= 1) return
    val options = outros.map { number ->
        SelectorOption(
            value = number,
            label = stringResource(R.string.stage_outro_numbered, number + 1),
        )
    }
    InteractiveSelectorBar(
        label = stringResource(R.string.interactive_outro_label),
        buttonText = stringResource(R.string.interactive_outro_pick),
        sheetTitle = stringResource(R.string.interactive_outro_pick),
        sheetSubtitle = stringResource(R.string.interactive_ending_hint),
        options = options,
        enabled = enabled,
        showSelectionRadio = false,
        onSelect = onSelect,
    )
}

@Composable
internal fun InteractiveVersionChooser(
    versionNumbers: List<Int>,
    selectedVersion: Int?,
    availableVersions: Set<Int>,
    onSelect: (Int) -> Unit,
) {
    if (versionNumbers.isEmpty()) return
    val displayedVersion = selectedVersion ?: versionNumbers.first()
    val options = versionNumbers.map { number ->
        SelectorOption(
            value = number,
            label = stringResource(R.string.recording_version_number, number),
            enabled = number in availableVersions || number == selectedVersion,
            selected = number == selectedVersion,
        )
    }
    InteractiveSelectorBar(
        label = stringResource(R.string.recording_version),
        buttonText = stringResource(R.string.recording_version_number, displayedVersion),
        sheetTitle = stringResource(R.string.recording_version),
        sheetSubtitle = stringResource(R.string.recording_version_hint),
        options = options,
        enabled = true,
        showSelectionRadio = true,
        onSelect = onSelect,
    )
}

/** A read-only inventory of this graph's actual stages, without implying an invented sequence. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun InteractiveStructure(flow: PlayerViewModel.FlowUi) {
    if (flow.availableStageKeys.isEmpty()) return
    val names = flow.availableStageKeys.map { stageText(it) }.distinct()
    val current = if (flow.sourceFrame != null && flow.voiceCount > 0) flow.stageKey?.let { stageText(it) } else null
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.stage_available), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            names.forEach { name ->
                val active = current == name
                val background by animateColorAsState(
                    if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    animationSpec = tween(180), label = "stage-background")
                val foreground by animateColorAsState(
                    if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(180), label = "stage-text")
                Surface(modifier = Modifier.semantics { selected = active }, shape = MaterialTheme.shapes.extraLarge,
                    color = background, contentColor = foreground) {
                    Text(name, Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** Only currently available events occupy space; primary advancement leads secondary options cleanly. */
@Composable
internal fun InteractiveActionBar(
    flow: PlayerViewModel.FlowUi,
    onAdvance: () -> Unit,
    onFast: () -> Unit,
    onCancel: () -> Unit,
    onEnd: () -> Unit,
    showCancelInBar: Boolean = true,
) {
    val showNext = flow.canAdvance && flow.selectedRegion != 5
    val showFast = flow.canFast
    val showEnd = flow.canEnd && flow.selectedRegion != 5
    val showCancel = showCancelInBar && flow.canCancelPending

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showNext) {
            Button(
                onClick = onAdvance,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Filled.SkipNext, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.interactive_action_next),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        val secondaryActions = listOf(
            Triple(Icons.Filled.Speed, if (flow.isBattleTrack) R.string.stage_battle_fast else R.string.interactive_action_fast, onFast) to showFast,
            Triple(Icons.Filled.Flag, R.string.interactive_action_end, onEnd) to showEnd,
            Triple(Icons.AutoMirrored.Filled.Undo, R.string.interactive_action_cancel, onCancel) to showCancel,
        )
        if (secondaryActions.any { it.second }) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                secondaryActions.forEach { (actionInfo, visible) ->
                    key(actionInfo.second) {
                        val weight by animateFloatAsState(
                            if (visible) 1f else 0f,
                            tween(180, easing = com.mylittleblover.mkwplayer.ui.AppMotion.easeOut),
                            label = "interactive-action",
                        )
                        if (visible || weight > .001f) {
                            val (icon, label, action) = actionInfo
                            FilledTonalButton(
                                onClick = action,
                                enabled = visible,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(weight.coerceAtLeast(.001f)).fillMaxHeight().heightIn(min = 48.dp)
                                    .graphicsLayer { alpha = weight; clip = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            ) {
                                Icon(icon, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(label), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}
