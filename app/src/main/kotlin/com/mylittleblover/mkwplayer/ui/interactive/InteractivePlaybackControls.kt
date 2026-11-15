package com.mylittleblover.mkwplayer.ui.interactive

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
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
            com.mylittleblover.mkwplayer.ui.SmoothProgressIndicator((frame.toDouble() / length).toFloat(), modifier = Modifier.fillMaxWidth())
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (frame != null && frame >= 0) EngineSnapshot.formatPosition(frame, flow.transportRate) else position, style = MaterialTheme.typography.labelMedium)
            if (length != null && length > 0) Text(EngineSnapshot.formatPosition(length, flow.transportRate), style = MaterialTheme.typography.labelMedium)
        }
        if (flow.voiceCount > 0 && flow.loopStart != null && flow.loopEnd != null && !flow.fading) {
            Text(stringResource(R.string.interactive_loop_range,
                EngineSnapshot.formatPosition(flow.loopStart, flow.transportRate),
                EngineSnapshot.formatPosition(flow.loopEnd, flow.transportRate)), style = MaterialTheme.typography.labelMedium)
        }
        if (flow.fading) {
            val remaining = flow.fadeRemainingFrames
            Text(if (remaining == null) stringResource(R.string.interactive_fading)
                else stringResource(R.string.interactive_fade_remaining,
                    String.format(Locale.ROOT, "%.1f", remaining.toDouble() / flow.transportRate)),
                color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
internal fun InteractiveStopBar(canStop: Boolean, canFade: Boolean, fading: Boolean, onStop: () -> Unit, onFade: (Int) -> Unit) {
    var showFade by rememberSaveable { mutableStateOf(false) }
    Surface(tonalElevation = 3.dp) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(12.dp).heightIn(min = 48.dp)
                .combinedClickable(enabled = canStop, role = Role.Button, onClick = onStop,
                    onLongClickLabel = stringResource(R.string.interactive_fade_choose),
                    onLongClick = if (canFade && !fading) ({ showFade = true }) else null),
            shape = MaterialTheme.shapes.extraLarge,
            color = if (canStop) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (canStop) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Stop, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.interactive_stop))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InteractiveEntryChooser(choices: Map<Int, String>, selected: Int?, enabled: Boolean, onSelect: (Int) -> Unit) {
    if (choices.isEmpty()) return
    var showSheet by rememberSaveable { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    LaunchedEffect(enabled) { if (!enabled) showSheet = false }
    val selectedName = stageText(choices[selected] ?: choices.values.first())
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.stage_start_label), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { showSheet = true }, enabled = enabled) {
                Text(selectedName, maxLines = 1)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
        }
    }
    if (showSheet && enabled) ModalBottomSheet(sheetState = sheet, onDismissRequest = { showSheet = false }) {
        Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState())
            .selectableGroup().padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.stage_start_choose), Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                style = MaterialTheme.typography.headlineSmall)
            choices.forEach { (id, label) ->
                ListItem(headlineContent = { Text(stageText(label)) },
                    trailingContent = { RadioButton(selected = id == selected, onClick = null) },
                    modifier = Modifier.selectable(selected = id == selected, role = Role.RadioButton,
                        onClick = { if (id != selected) onSelect(id); scope.launch { sheet.hide(); showSheet = false } }).heightIn(min = 56.dp))
            }
        }
    }
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

/** Only currently available events occupy space; a single row remains stable during playback. */
@Composable
internal fun InteractiveActionBar(flow: PlayerViewModel.FlowUi, onAdvance: () -> Unit,
    onFast: () -> Unit, onCancel: () -> Unit, onEnd: () -> Unit) {
    val actions = listOf(
        Triple(Icons.Filled.SkipNext, R.string.interactive_action_next, onAdvance) to (flow.canAdvance && flow.selectedRegion != 5),
        Triple(Icons.Filled.Speed, if (flow.isBattleTrack) R.string.stage_battle_fast else R.string.interactive_action_fast, onFast) to flow.canFast,
        Triple(Icons.Filled.Flag, R.string.interactive_action_end, onEnd) to (flow.canEnd && flow.selectedRegion != 5),
        Triple(Icons.AutoMirrored.Filled.Undo, R.string.interactive_action_cancel, onCancel) to flow.canCancelPending)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.forEach { (actionInfo, visible) ->
            key(actionInfo.second) {
                val weight by animateFloatAsState(if (visible) 1f else 0f,
                    tween(180, easing = com.mylittleblover.mkwplayer.ui.AppMotion.easeOut), label = "interactive-action")
                if (visible || weight > .001f) {
                    val (icon, label, action) = actionInfo
                    OutlinedButton(onClick = action, enabled = visible,
                        modifier = Modifier.weight(weight.coerceAtLeast(.001f)).fillMaxHeight().heightIn(min = 56.dp)
                            .graphicsLayer { alpha = weight; clip = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)) {
                        Icon(icon, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(label), style = MaterialTheme.typography.labelLarge, maxLines = 2)
                    }
                }
            }
        }
    }
}
