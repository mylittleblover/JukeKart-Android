package com.mylittleblover.mkwplayer.ui.interactive

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.ui.unit.dp
import com.mylittleblover.mkwplayer.R
import com.mylittleblover.mkwplayer.audio.EngineSnapshot
import com.mylittleblover.mkwplayer.player.PlayerViewModel
import java.util.Locale

/** A read-only view of Native source/transport clocks; no seek gesture or UI timer. */
@Composable
internal fun InteractivePlaybackTimeline(position: String, flow: PlayerViewModel.FlowUi) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val frame = flow.sourceFrame
        val length = flow.sourceFrames
        if (frame != null && frame >= 0 && length != null && length > 0) {
            LinearProgressIndicator(progress = { (frame.toDouble() / length).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun InteractiveEntryChooser(choices: Map<Int, String>, selected: Int?, enabled: Boolean, onSelect: (Int) -> Unit) {
    if (choices.size < 2) return
    Column {
        Text(stringResource(R.string.interactive_entry_hint), style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((id, label) in choices) {
                val name = stageText(label)
                FilterChip(selected = id == selected, onClick = { if(id != selected) onSelect(id) }, enabled = enabled, label = { Text(name) })
            }
        }
    }
}
