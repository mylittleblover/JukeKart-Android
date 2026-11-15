package com.mylittleblover.mkwplayer.ui.interactive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mylittleblover.mkwplayer.R
import com.mylittleblover.mkwplayer.audio.EngineSnapshot
import com.mylittleblover.mkwplayer.interactive.CourseFlowRecipeBuilder
import com.mylittleblover.mkwplayer.library.CoursePreset
import com.mylittleblover.mkwplayer.library.FlowStep
import com.mylittleblover.mkwplayer.library.PresetRefs
import java.util.UUID

/**
 * Guided course flow preset builder (#48-#50).
 *
 * All section choices, loop bounds, beat exit cues, and Outro handover indicators
 * come exclusively from measured BWAV headers and verified MINF timing metadata in
 * [CourseFlowRecipeBuilder.CourseInventory].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FlowEditorScreen(
    courseTitle: String,
    inventory: CourseFlowRecipeBuilder.CourseInventory,
    existingPreset: CoursePreset?,
    onAudition: (CoursePreset) -> Unit,
    onSave: (CoursePreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val defaultName = remember(courseTitle, existingPreset) {
        existingPreset?.name ?: "$courseTitle Custom"
    }
    var presetName by rememberSaveable(existingPreset?.id, inventory.courseCode) {
        mutableStateOf(defaultName)
    }
    var steps by remember(existingPreset?.id, inventory.courseCode) {
        mutableStateOf(existingPreset?.steps ?: inventory.defaultEditorSteps())
    }
    var outroNumber by rememberSaveable(existingPreset?.id, inventory.courseCode) {
        mutableStateOf<Int?>(existingPreset?.outroNumber ?: inventory.outros.firstOrNull()?.number)
    }
    var fadeSeconds by rememberSaveable(existingPreset?.id, inventory.courseCode) {
        mutableIntStateOf((existingPreset?.fadeOutMs ?: 0) / 1000)
    }

    fun draftPreset(): CoursePreset {
        val now = System.currentTimeMillis()
        return CoursePreset(
            id = existingPreset?.id ?: "${PresetRefs.USER_PREFIX}${UUID.randomUUID()}",
            courseCode = inventory.courseCode,
            name = presetName.trim().ifEmpty { "$courseTitle Custom" },
            steps = steps,
            outroNumber = outroNumber,
            fadeOutMs = if (outroNumber == null) fadeSeconds * 1000 else 0,
            outroToNextPre = outroNumber != null,
            updatedAtEpochMs = now,
        )
    }

    val selectedOutro = inventory.outros.firstOrNull { it.number == outroNumber }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "$courseTitle · ${stringResource(R.string.flow_editor_title)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    OutlinedTextField(
                        value = presetName,
                        onValueChange = { presetName = it.take(48) },
                        label = { Text(stringResource(R.string.flow_editor_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (selectedOutro?.supportsNativeHandover == true) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.flow_editor_outro_native_ready),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.flow_editor_steps_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (steps.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.flow_editor_no_steps),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            itemsIndexed(steps, key = { index, step -> "$index:${step.roleKey}:${step.loopCount}:${step.exitFrame}" }) { index, step ->
                val part = inventory.part(step.roleKey)
                FlowStepEditorCard(
                    index = index,
                    step = step,
                    part = part,
                    canMoveUp = index > 0,
                    canMoveDown = index < steps.lastIndex,
                    onMoveUp = {
                        val updated = steps.toMutableList()
                        val item = updated.removeAt(index)
                        updated.add(index - 1, item)
                        steps = updated
                    },
                    onMoveDown = {
                        val updated = steps.toMutableList()
                        val item = updated.removeAt(index)
                        updated.add(index + 1, item)
                        steps = updated
                    },
                    onRemove = {
                        steps = steps.toMutableList().apply { removeAt(index) }
                    },
                    onUpdate = { nextStep ->
                        steps = steps.toMutableList().apply { set(index, nextStep) }
                    },
                )
            }
        }

        item {
            ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.flow_editor_add_step),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        inventory.parts.forEach { part ->
                            AssistChip(
                                onClick = {
                                    steps = steps + FlowStep(
                                        roleKey = part.roleKey,
                                        loopCount = if (part.supportsLoop && part.roleKey in setOf("Main", "Fast", "Area2", "Area3", "Final")) 1 else 0,
                                        exitFrame = part.defaultExitFrame,
                                    )
                                },
                                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                                label = { Text(roleDisplayLabel(part.roleKey)) },
                            )
                        }
                    }
                }
            }
        }

        item {
            ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.flow_editor_outro_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(0, 1, 3, 5).forEach { fade ->
                            val selected = outroNumber == null && fadeSeconds == fade
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    outroNumber = null
                                    fadeSeconds = fade
                                },
                                label = { Text(stringResource(R.string.flow_editor_outro_none_fade, fade)) },
                            )
                        }
                        inventory.outros.forEach { outro ->
                            val selected = outroNumber == outro.number
                            val label = if (outro.supportsNativeHandover) {
                                "${stringResource(R.string.flow_editor_outro_item, outro.number)} ★"
                            } else {
                                stringResource(R.string.flow_editor_outro_item, outro.number)
                            }
                            FilterChip(
                                selected = selected,
                                onClick = { outroNumber = outro.number },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = { onAudition(draftPreset()) },
                    enabled = steps.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.discovery_audition))
                }
                Button(
                    onClick = { onSave(draftPreset()) },
                    enabled = steps.isNotEmpty() && presetName.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.flow_editor_save))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowStepEditorCard(
    index: Int,
    step: FlowStep,
    part: CourseFlowRecipeBuilder.StagePartInfo?,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onUpdate: (FlowStep) -> Unit,
) {
    ElevatedCard(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${index + 1}. ${roleDisplayLabel(step.roleKey)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (part != null) {
                        Text(
                            text = EngineSnapshot.formatPosition(part.frames, part.sampleRate),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.flow_editor_move_up))
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.flow_editor_move_down))
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.flow_editor_remove))
                }
            }

            if (part != null && part.supportsLoop) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.flow_editor_loop_count, step.loopCount),
                        style = MaterialTheme.typography.bodySmall,
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

            val exits = part?.exitCandidates.orEmpty()
            if (exits.size > 1) {
                Text(
                    text = stringResource(R.string.flow_editor_exit_cue),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val activeFrame = step.exitFrame ?: part?.defaultExitFrame
                    exits.forEach { candidate ->
                        FilterChip(
                            selected = activeFrame == candidate.frame,
                            onClick = { onUpdate(step.copy(exitFrame = candidate.frame)) },
                            label = { Text(candidate.label) },
                        )
                    }
                }
            }
        }
    }
}

private fun roleDisplayLabel(roleKey: String): String = when (roleKey) {
    "Pre_Short" -> "Pre Short"
    "Pre" -> "Pre (Intro)"
    "Bridge" -> "Bridge"
    "Main" -> "Main Loop"
    "Fast" -> "Final Lap (Fast)"
    "Pattern2" -> "Pattern 2"
    "Fast_Pattern2" -> "Fast Pattern 2"
    "Area1_Pre" -> "Area 1 Pre"
    "Area1_Bridge" -> "Area 1 Bridge"
    "Area1" -> "Area 1"
    "Area2" -> "Area 2"
    "Area3_Pre" -> "Area 3 Pre"
    "Area3" -> "Area 3"
    "Final_Pre" -> "Final Lap Pre"
    "Final" -> "Final Lap"
    else -> roleKey.replace('_', ' ')
}
