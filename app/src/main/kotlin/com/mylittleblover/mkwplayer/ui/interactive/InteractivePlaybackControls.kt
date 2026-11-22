package com.mylittleblover.mkwplayer.ui.interactive

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mylittleblover.mkwplayer.R
import com.mylittleblover.mkwplayer.audio.EngineSnapshot
import com.mylittleblover.mkwplayer.graph.MusicGraphBuilder
import com.mylittleblover.mkwplayer.player.PlayerViewModel
import com.mylittleblover.mkwplayer.ui.AppMotion
import com.mylittleblover.mkwplayer.ui.SmoothProgressIndicator
import com.mylittleblover.mkwplayer.ui.kit.MkwBadge
import com.mylittleblover.mkwplayer.ui.kit.MkwBadgeTone
import com.mylittleblover.mkwplayer.ui.kit.MkwBottomSheet
import com.mylittleblover.mkwplayer.ui.kit.MkwButtonSize
import com.mylittleblover.mkwplayer.ui.kit.MkwCard
import com.mylittleblover.mkwplayer.ui.kit.MkwDialog
import com.mylittleblover.mkwplayer.ui.kit.MkwDivider
import com.mylittleblover.mkwplayer.ui.kit.MkwGhostButton
import com.mylittleblover.mkwplayer.ui.kit.MkwKitTokens
import com.mylittleblover.mkwplayer.ui.kit.MkwOptionRow
import com.mylittleblover.mkwplayer.ui.kit.MkwPrimaryButton
import com.mylittleblover.mkwplayer.ui.kit.MkwRadioMark
import com.mylittleblover.mkwplayer.ui.kit.MkwRoseButton
import com.mylittleblover.mkwplayer.ui.kit.MkwSecondaryButton
import com.mylittleblover.mkwplayer.ui.kit.MkwSurfaceTone
import com.mylittleblover.mkwplayer.ui.rememberPressScale
import com.mylittleblover.mkwplayer.ui.rememberReducedMotion
import java.util.Locale
import kotlinx.coroutines.flow.StateFlow

/** A read-only view of Native source/transport clocks; no seek gesture or UI timer. */
@Composable
internal fun InteractivePlaybackTimeline(
    position: String,
    flow: PlayerViewModel.FlowUi,
    progressFlow: StateFlow<PlayerViewModel.ProgressUi>? = null,
) {
    val progressUi = progressFlow?.collectAsState()?.value
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val frame = progressUi?.sourceFrame ?: flow.sourceFrame
        val length = flow.sourceFrames
        if (frame != null && frame >= 0 && length != null && length > 0) {
            SmoothProgressIndicator(
                progress = (frame.toDouble() / length).toFloat(),
                modifier = Modifier.fillMaxWidth(),
                barHeight = 5.dp,
            )
        }
        val numStyle = MkwKitTokens.tabular(MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                if (frame != null && frame >= 0) EngineSnapshot.formatPosition(frame, flow.transportRate)
                else (progressUi?.position ?: position),
                style = numStyle,
                color = MkwKitTokens.Palette.inkSecondary,
            )
            if (length != null && length > 0) {
                Text(
                    EngineSnapshot.formatPosition(length, flow.transportRate),
                    style = numStyle,
                    color = MkwKitTokens.Palette.inkSecondary,
                )
            }
        }
        if (flow.fading) {
            val remaining = progressUi?.fadeRemainingFrames ?: flow.fadeRemainingFrames
            Text(
                if (remaining == null) stringResource(R.string.interactive_fading)
                else stringResource(
                    R.string.interactive_fade_remaining,
                    String.format(Locale.ROOT, "%.1f", remaining.toDouble() / flow.transportRate),
                ),
                style = MkwKitTokens.tabular(MaterialTheme.typography.labelLarge),
                color = MkwKitTokens.Palette.crimson,
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

    MkwCard(
        modifier = modifier.fillMaxWidth().animateContentSize(tween(200, easing = AppMotion.easeOut)),
        selected = pending != null,
        shape = MkwKitTokens.Shape.card,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier.size(8.dp).background(
                    color = if (active) MkwKitTokens.Palette.crimson else MkwKitTokens.Palette.outlineInk,
                    shape = CircleShape,
                ),
            )
            Text(
                stringResource(R.string.interactive_current_stage_label),
                style = MaterialTheme.typography.labelMedium,
                color = MkwKitTokens.Palette.inkSecondary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                currentLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MkwKitTokens.Palette.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AnimatedVisibility(
            visible = pending != null,
            enter = fadeIn(tween(160)) + expandVertically(tween(180, easing = AppMotion.easeOut)),
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
                        color = MkwKitTokens.Palette.crimsonDeep,
                        modifier = Modifier.weight(1f),
                    )
                    if (flow.canCancelPending) {
                        MkwRoseButton(
                            text = stringResource(R.string.interactive_action_cancel),
                            onClick = onCancel,
                            size = MkwButtonSize.Compact,
                            leadingIcon = Icons.AutoMirrored.Filled.Undo,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Sticky bottom transport & long-press-to-fade dock built on unified `ui.kit` porcelain surfaces.
 * Keeping [leadingTransport] optional lets [ConsolePage] anchor Pause/Play beside Stop in one zone.
 */
@Composable
internal fun InteractiveStopBar(
    canStop: Boolean,
    canFade: Boolean,
    fading: Boolean,
    onStop: () -> Unit,
    onFade: (Int) -> Unit,
    paused: Boolean = false,
    fadeRemainingFrames: Long? = null,
    transportRate: Int = 48000,
    progressFlow: StateFlow<PlayerViewModel.ProgressUi>? = null,
    leadingTransport: (@Composable () -> Unit)? = null,
) {
    var showFade by rememberSaveable { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val holdProgress = remember { Animatable(0f) }
    val longPressTimeoutMs = LocalViewConfiguration.current.longPressTimeoutMillis.toInt().coerceAtLeast(300)
    val haptic = LocalHapticFeedback.current
    val reduceMotion = rememberReducedMotion()

    LaunchedEffect(interactionSource, canFade, fading) {
        if (!canFade || fading) {
            holdProgress.snapTo(0f)
            return@LaunchedEffect
        }
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    holdProgress.snapTo(0f)
                    holdProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = if (reduceMotion) 0 else longPressTimeoutMs,
                            easing = LinearEasing,
                        ),
                    )
                }
                is PressInteraction.Release,
                is PressInteraction.Cancel -> {
                    holdProgress.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(
                            durationMillis = if (reduceMotion) 0 else 140,
                            easing = AppMotion.easeOut,
                        ),
                    )
                }
            }
        }
    }

    val liveFadeRemaining = if (fading) {
        progressFlow?.collectAsState()?.value?.fadeRemainingFrames ?: fadeRemainingFrames
    } else {
        fadeRemainingFrames
    }
    val remainText = liveFadeRemaining?.let { frames ->
        val rate = transportRate.coerceAtLeast(1)
        String.format(Locale.ROOT, "%.1f", frames.toDouble() / rate.toDouble())
    }
    val holdFillColor = MkwKitTokens.Palette.danger.copy(alpha = 0.16f)

    Surface(
        color = MkwKitTokens.Palette.ground,
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            MkwDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (leadingTransport != null) {
                    leadingTransport()
                }
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = MkwKitTokens.Size.buttonHero)
                        .combinedClickable(
                            interactionSource = interactionSource,
                            indication = androidx.compose.foundation.LocalIndication.current,
                            enabled = canStop,
                            role = Role.Button,
                            onClick = onStop,
                            onLongClickLabel = stringResource(R.string.interactive_fade_choose),
                            onLongClick = if (canFade && !fading) ({
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showFade = true
                            }) else null,
                        ),
                    shape = MkwKitTokens.Shape.pill,
                    color = if (canStop) MkwKitTokens.Palette.card else MkwKitTokens.Palette.trackStrong,
                    contentColor = if (canStop) MkwKitTokens.Palette.ink else MkwKitTokens.Palette.inkSecondary,
                    border = BorderStroke(MkwKitTokens.Stroke.border, MkwKitTokens.Palette.hairline),
                ) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        val fillFraction = holdProgress.value.coerceIn(0f, 1f)
                        if (fillFraction > 0.005f) {
                            Surface(
                                modifier = Modifier
                                    .matchParentSize()
                                    .graphicsLayer {
                                        transformOrigin = TransformOrigin(0f, 0.5f)
                                        scaleX = fillFraction
                                    },
                                color = holdFillColor,
                                content = {},
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Stop,
                                contentDescription = null,
                                tint = if (canStop) MkwKitTokens.Palette.danger else MkwKitTokens.Palette.inkSecondary,
                            )
                            Spacer(Modifier.width(8.dp))
                            val stopLabel = when {
                                fading && paused && remainText != null -> stringResource(R.string.interactive_fade_paused, remainText)
                                fading && remainText != null -> stringResource(R.string.interactive_fade_remaining, remainText)
                                fading -> stringResource(R.string.interactive_fading)
                                else -> stringResource(R.string.interactive_stop)
                            }
                            Text(
                                text = stopLabel,
                                style = MkwKitTokens.tabular(MaterialTheme.typography.labelLarge),
                                fontWeight = FontWeight.SemiBold,
                                color = if (canStop) MkwKitTokens.Palette.ink else MkwKitTokens.Palette.inkSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showFade) {
        MkwDialog(
            onDismissRequest = { showFade = false },
            title = stringResource(R.string.interactive_fade_choose),
            confirmButton = {
                MkwGhostButton(
                    text = stringResource(R.string.interactive_close),
                    onClick = { showFade = false },
                )
            },
        ) {
            Text(
                stringResource(R.string.interactive_fade_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MkwKitTokens.Palette.inkSecondary,
            )
            Spacer(Modifier.height(4.dp))
            Column(
                modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (seconds in listOf(1, 3, 5, 10)) {
                    MkwSecondaryButton(
                        text = stringResource(R.string.interactive_fade_seconds, seconds),
                        onClick = {
                            showFade = false
                            onFade(seconds)
                        },
                        enabled = canStop && !fading,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
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
    LaunchedEffect(enabled) { if (!enabled) showSheet = false }
    val (pressMod, src) = rememberPressScale(pressedScale = 0.985f)

    Surface(
        onClick = { if (enabled) showSheet = true },
        enabled = enabled,
        interactionSource = src,
        shape = MkwKitTokens.Shape.control,
        color = MkwKitTokens.Palette.ground,
        border = BorderStroke(MkwKitTokens.Stroke.border, MkwKitTokens.Palette.hairline),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(if (enabled) pressMod else Modifier),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MkwKitTokens.Palette.inkSecondary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Surface(
                shape = MkwKitTokens.Shape.pill,
                color = if (enabled) MkwKitTokens.Palette.rose else MkwKitTokens.Palette.trackStrong,
                border = if (enabled) {
                    BorderStroke(MkwKitTokens.Stroke.hairline, MkwKitTokens.Palette.crimson.copy(alpha = 0.25f))
                } else null,
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = buttonText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (enabled) MkwKitTokens.Palette.crimsonDeep else MkwKitTokens.Palette.inkSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (enabled) MkwKitTokens.Palette.crimsonDeep else MkwKitTokens.Palette.inkSecondary,
                    )
                }
            }
        }
    }

    if (showSheet && enabled) {
        MkwBottomSheet(
            onDismissRequest = { showSheet = false },
            title = sheetTitle,
            subtitle = sheetSubtitle,
        ) {
            val listMod = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .let { if (showSelectionRadio) it.selectableGroup() else it }
                .padding(horizontal = 12.dp)
            Column(listMod, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                options.forEach { option ->
                    MkwOptionRow(
                        headline = option.label,
                        selected = option.selected,
                        enabled = option.enabled,
                        role = if (showSelectionRadio) Role.RadioButton else Role.Button,
                        onClick = {
                            if (!showSelectionRadio || !option.selected) onSelect(option.value)
                            showSheet = false
                        },
                        trailing = {
                            if (showSelectionRadio) {
                                if (option.selected) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = MkwKitTokens.Palette.crimson)
                                } else {
                                    MkwRadioMark(selected = false, enabled = option.enabled)
                                }
                            } else {
                                Icon(
                                    Icons.Filled.Flag,
                                    contentDescription = null,
                                    tint = MkwKitTokens.Palette.crimson,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        },
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

/** A read-only inventory of this graph's actual stages, rendered as unified MKW badges. */
@OptIn(ExperimentalLayoutApi::class)
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
                MkwBadge(
                    text = name,
                    tone = if (active) MkwBadgeTone.Rose else MkwBadgeTone.Neutral,
                    tabular = false,
                )
            }
        }
    }
}

/** Only currently available events occupy space; primary advancement leads secondary pill actions cleanly. */
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
            MkwPrimaryButton(
                text = stringResource(R.string.interactive_action_next),
                onClick = onAdvance,
                size = MkwButtonSize.Hero,
                leadingIcon = Icons.Filled.SkipNext,
                modifier = Modifier.fillMaxWidth(),
            )
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
                            tween(180, easing = AppMotion.easeOut),
                            label = "interactive-action",
                        )
                        if (visible || weight > .001f) {
                            val (icon, labelRes, action) = actionInfo
                            val isFast = labelRes == R.string.interactive_action_fast || labelRes == R.string.stage_battle_fast
                            val mod = Modifier
                                .weight(weight.coerceAtLeast(.001f))
                                .fillMaxHeight()
                                .graphicsLayer { alpha = weight; clip = true }
                            if (isFast) {
                                MkwRoseButton(
                                    text = stringResource(labelRes),
                                    onClick = action,
                                    enabled = visible,
                                    leadingIcon = icon,
                                    modifier = mod,
                                )
                            } else {
                                MkwSecondaryButton(
                                    text = stringResource(labelRes),
                                    onClick = action,
                                    enabled = visible,
                                    leadingIcon = icon,
                                    modifier = mod,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun syncPointText(sync: MusicGraphBuilder.SyncPoint?): String? = when (sync) {
    MusicGraphBuilder.SyncPoint.Immediate -> stringResource(R.string.interactive_sync_immediate)
    MusicGraphBuilder.SyncPoint.NextBeat -> stringResource(R.string.interactive_sync_next_beat)
    MusicGraphBuilder.SyncPoint.NextBar -> stringResource(R.string.interactive_sync_next_bar)
    MusicGraphBuilder.SyncPoint.NextGrid -> stringResource(R.string.interactive_sync_next_grid)
    MusicGraphBuilder.SyncPoint.ExitCue -> stringResource(R.string.interactive_sync_exit_cue)
    MusicGraphBuilder.SyncPoint.NextCue -> stringResource(R.string.interactive_sync_next_cue)
    MusicGraphBuilder.SyncPoint.NextCustomCue -> stringResource(R.string.interactive_sync_custom_cue)
    null -> null
}

/**
 * Focused live stage performance control zone grouping current section badge, queued transition
 * sync target + cancel trigger, active fade status, and transition actions cleanly together.
 */
@Composable
internal fun InteractivePhaseControlZone(
    flow: PlayerViewModel.FlowUi,
    paused: Boolean,
    onAdvance: () -> Unit,
    onFast: () -> Unit,
    onCancel: () -> Unit,
    onEnd: () -> Unit,
    progressFlow: StateFlow<PlayerViewModel.ProgressUi>? = null,
) {
    val active = flow.performing && flow.voiceCount > 0 && flow.sourceFrame != null
    val currentTitle = when {
        flow.performing && flow.voiceCount > 0 && flow.sourceFrame == null -> stringResource(R.string.stage_transitioning)
        else -> stageText(flow.stageKey ?: flow.performanceStage)
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier.size(8.dp).background(
                    color = if (active) MkwKitTokens.Palette.crimson else MkwKitTokens.Palette.outlineInk,
                    shape = CircleShape,
                ),
            )
            Text(
                stringResource(R.string.interactive_current_stage_label),
                style = MaterialTheme.typography.labelLarge,
                color = MkwKitTokens.Palette.inkSecondary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                currentTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MkwKitTokens.Palette.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (flow.pendingStageKey != null) {
            val pendingName = stageText(flow.pendingStageKey)
            val syncText = syncPointText(flow.pendingSyncPoint)
            val waitText = if (syncText != null) {
                stringResource(R.string.interactive_pending_with_sync, pendingName, syncText)
            } else {
                stringResource(R.string.interactive_pending_next_cue, pendingName)
            }
            MkwCard(
                tone = MkwSurfaceTone.RoseTint,
                selected = true,
                shape = MkwKitTokens.Shape.control,
                contentPadding = PaddingValues(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = null,
                        tint = MkwKitTokens.Palette.crimsonDeep,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        waitText,
                        style = MaterialTheme.typography.labelLarge,
                        color = MkwKitTokens.Palette.crimsonDeep,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (flow.canCancelPending) {
                        MkwSecondaryButton(
                            text = stringResource(R.string.interactive_action_cancel),
                            onClick = onCancel,
                            size = MkwButtonSize.Compact,
                            leadingIcon = Icons.AutoMirrored.Filled.Undo,
                        )
                    }
                }
            }
        }

        if (flow.fading) {
            val liveFade = progressFlow?.collectAsState()?.value?.fadeRemainingFrames ?: flow.fadeRemainingFrames
            val remainText = liveFade?.let { frames ->
                val rate = flow.transportRate.coerceAtLeast(1)
                String.format(Locale.ROOT, "%.1f", frames.toDouble() / rate.toDouble())
            }
            val fadeLabel = when {
                paused && remainText != null -> stringResource(R.string.interactive_fade_paused, remainText)
                remainText != null -> stringResource(R.string.interactive_fade_remaining, remainText)
                else -> stringResource(R.string.interactive_fading)
            }
            MkwCard(
                error = true,
                shape = MkwKitTokens.Shape.control,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    fadeLabel,
                    style = MkwKitTokens.tabular(MaterialTheme.typography.labelLarge),
                    fontWeight = FontWeight.SemiBold,
                    color = MkwKitTokens.Palette.danger,
                )
                Text(
                    stringResource(R.string.interactive_fade_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MkwKitTokens.Palette.inkSecondary,
                )
            }
        }

        InteractiveActionBar(
            flow = flow,
            onAdvance = onAdvance,
            onFast = onFast,
            onCancel = onCancel,
            onEnd = onEnd,
            showCancelInBar = false,
        )
    }
}
