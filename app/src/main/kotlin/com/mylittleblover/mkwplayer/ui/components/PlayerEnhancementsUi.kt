package com.mylittleblover.mkwplayer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mylittleblover.mkwplayer.R
import com.mylittleblover.mkwplayer.audio.EngineSnapshot
import com.mylittleblover.mkwplayer.audio.EqualizerPreset
import com.mylittleblover.mkwplayer.audio.MasterDspStateConfig
import com.mylittleblover.mkwplayer.audio.PlaybackRateConfig
import com.mylittleblover.mkwplayer.library.TrackSortOrder
import com.mylittleblover.mkwplayer.player.InteractiveTimedAction
import com.mylittleblover.mkwplayer.player.InteractiveTimerState
import com.mylittleblover.mkwplayer.player.PlayerViewModel
import com.mylittleblover.mkwplayer.player.SleepTimerMode
import com.mylittleblover.mkwplayer.player.SleepTimerState
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun formatCountdownMs(ms: Long): String {
    val totalSec = ((ms.coerceAtLeast(0L) + 999L) / 1000L)
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    return if (h > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", m, s)
    }
}

@Composable
internal fun LiveSpectrumVisualizer(
    bands: FloatArray,
    modifier: Modifier = Modifier,
    barColorPrimary: Color = MaterialTheme.colorScheme.primary,
    barColorSecondary: Color = MaterialTheme.colorScheme.tertiary,
) {
    val smoothed = remember { FloatArray(24) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(bands) {
        for (i in 0 until minOf(smoothed.size, bands.size)) {
            val target = bands[i].coerceIn(0f, 1f)
            val prev = smoothed[i]
            smoothed[i] = if (target >= prev) {
                prev + (target - prev) * 0.65f
            } else {
                prev * 0.78f + target * 0.22f
            }
        }
        tick++
    }
    Canvas(modifier = modifier) {
        @Suppress("UNUSED_EXPRESSION")
        tick
        val count = smoothed.size
        if (count == 0) return@Canvas
        val gap = (size.width / (count * 3.5f)).coerceIn(2f, 6f)
        val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(2f)
        val brush = Brush.verticalGradient(listOf(barColorSecondary, barColorPrimary))
        for (i in 0 until count) {
            val amp = smoothed[i].coerceIn(0.03f, 1f)
            val barHeight = (size.height * amp).coerceAtLeast(barWidth)
            val x = i * (barWidth + gap)
            val y = size.height - barHeight
            drawRoundRect(
                brush = brush,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}

@Composable
internal fun EqualizerPresetLabel(preset: EqualizerPreset): String = when (preset) {
    EqualizerPreset.FLAT -> stringResource(R.string.eq_preset_flat)
    EqualizerPreset.BASS_BOOST -> stringResource(R.string.eq_preset_bass_boost)
    EqualizerPreset.RACING -> stringResource(R.string.eq_preset_racing)
    EqualizerPreset.VOCAL -> stringResource(R.string.eq_preset_vocal)
    EqualizerPreset.ELECTRONIC -> stringResource(R.string.eq_preset_electronic)
    EqualizerPreset.ACOUSTIC -> stringResource(R.string.eq_preset_acoustic)
    EqualizerPreset.TREBLE -> stringResource(R.string.eq_preset_treble)
    EqualizerPreset.CUSTOM -> stringResource(R.string.eq_preset_custom)
}

@Composable
internal fun EqualizerContent(
    config: MasterDspStateConfig,
    onUpdate: (MasterDspStateConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    val freqLabels = remember { listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "12 kHz") }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                Modifier.fillMaxWidth().clickable { onUpdate(config.copy(eqEnabled = !config.eqEnabled)) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Equalizer, null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        stringResource(R.string.eq_enabled),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Switch(
                    checked = config.eqEnabled,
                    onCheckedChange = { enabled -> onUpdate(config.copy(eqEnabled = enabled)) },
                )
            }
        }

        // Compact frequency curve preview
        val lineColor = if (config.eqEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Canvas(
                Modifier.fillMaxWidth().height(84.dp).padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                val midY = size.height / 2f
                drawLine(gridColor, Offset(0f, midY), Offset(size.width, midY), strokeWidth = 2f)
                val path = Path()
                val pts = config.bandsDb.indices.map { idx ->
                    val x = size.width * (idx / (config.bandsDb.size - 1).coerceAtLeast(1).toFloat())
                    val dbNorm = (config.bandsDb[idx].coerceIn(-12f, 12f) / 12f)
                    val y = midY - dbNorm * (size.height * 0.44f)
                    Offset(x, y)
                }
                if (pts.isNotEmpty()) {
                    path.moveTo(pts.first().x, pts.first().y)
                    for (i in 1 until pts.size) {
                        val prev = pts[i - 1]
                        val cur = pts[i]
                        val cx = (prev.x + cur.x) / 2f
                        path.cubicTo(cx, prev.y, cx, cur.y, cur.x, cur.y)
                    }
                    drawPath(path, lineColor, style = Stroke(width = 5f, cap = StrokeCap.Round))
                    pts.forEach { p -> drawCircle(lineColor, radius = 7f, center = p) }
                }
            }
        }

        Text(
            stringResource(R.string.eq_preset_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val presets = EqualizerPreset.entries.filter { it != EqualizerPreset.CUSTOM || config.preset == EqualizerPreset.CUSTOM }
            items(presets, key = { it.name }) { preset ->
                FilterChip(
                    selected = config.preset == preset,
                    onClick = {
                        if (preset != EqualizerPreset.CUSTOM) {
                            onUpdate(
                                config.copy(
                                    eqEnabled = if (preset == EqualizerPreset.FLAT) config.eqEnabled else true,
                                    bandsDb = preset.bandsDb,
                                    preset = preset,
                                ),
                            )
                        }
                    },
                    label = { Text(EqualizerPresetLabel(preset)) },
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            config.bandsDb.forEachIndexed { idx, gainDb ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        freqLabels.getOrElse(idx) { "" },
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.width(56.dp),
                    )
                    Slider(
                        value = gainDb,
                        onValueChange = { raw ->
                            val snapped = (raw * 2f).roundToInt() / 2f
                            val nextBands = config.bandsDb.toMutableList().apply { set(idx, snapped.coerceIn(-12f, 12f)) }
                            onUpdate(
                                config.copy(
                                    eqEnabled = true,
                                    bandsDb = nextBands,
                                    preset = EqualizerPreset.matchingOrCustom(nextBands),
                                ),
                            )
                        },
                        valueRange = -12f..12f,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(R.string.eq_db_fmt, gainDb),
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(64.dp),
                    )
                }
            }
        }

        HorizontalDivider()

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.eq_bass_boost, config.bassBoostPercent),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Slider(
                value = config.bassBoostPercent.toFloat(),
                onValueChange = { raw ->
                    onUpdate(config.copy(bassBoostPercent = raw.roundToInt().coerceIn(0, 100)))
                },
                valueRange = 0f..100f,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.eq_loudness_gain, config.loudnessDb),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Slider(
                value = config.loudnessDb,
                onValueChange = { raw ->
                    val snapped = ((raw * 2f).roundToInt() / 2f).coerceIn(0f, 9f)
                    onUpdate(config.copy(loudnessDb = snapped))
                },
                valueRange = 0f..9f,
            )
        }

        OutlinedButton(
            onClick = { onUpdate(MasterDspStateConfig()) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.eq_reset))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EqualizerScreen(
    config: MasterDspStateConfig,
    onUpdate: (MasterDspStateConfig) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.eq_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(18.dp),
        ) {
            EqualizerContent(config = config, onUpdate = onUpdate)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EqualizerSheet(
    config: MasterDspStateConfig,
    onUpdate: (MasterDspStateConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(sheetState = sheet, onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.eq_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            EqualizerContent(config = config, onUpdate = onUpdate)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpeedPitchSheet(
    rate: PlaybackRateConfig,
    onUpdate: (PlaybackRateConfig) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(sheetState = sheet, onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Speed, null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.speed_pitch_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onReset) { Text(stringResource(R.string.speed_pitch_reset)) }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.speed_label), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        String.format(Locale.ROOT, "%.2fx", rate.speed),
                        style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Slider(
                    value = rate.speed,
                    onValueChange = { raw ->
                        val snapped = ((raw * 20f).roundToInt() / 20f).coerceIn(0.5f, 2.0f)
                        onUpdate(rate.copy(speed = snapped))
                    },
                    valueRange = 0.5f..2.0f,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)) { preset ->
                        FilterChip(
                            selected = abs(rate.speed - preset) < 0.01f,
                            onClick = { onUpdate(rate.copy(speed = preset)) },
                            label = { Text(String.format(Locale.ROOT, "%.2fx", preset)) },
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    Modifier.fillMaxWidth().clickable { onUpdate(rate.copy(preservePitch = !rate.preservePitch)) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(R.string.preserve_pitch_label),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )
                    Switch(
                        checked = rate.preservePitch,
                        onCheckedChange = { preserve -> onUpdate(rate.copy(preservePitch = preserve)) },
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.pitch_label), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.speed_pitch_semitones_fmt, rate.pitchSemitones),
                        style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Slider(
                    value = rate.pitchSemitones,
                    enabled = rate.preservePitch,
                    onValueChange = { raw ->
                        val snapped = ((raw * 2f).roundToInt() / 2f).coerceIn(-12f, 12f)
                        onUpdate(rate.copy(pitchSemitones = snapped))
                    },
                    valueRange = -12f..12f,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SleepTimerSheet(
    state: SleepTimerState,
    canStopAfterTrack: Boolean,
    onStartMinutes: (Int) -> Unit,
    onStartAfterTrack: () -> Unit,
    onExtend5Min: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var customInput by rememberSaveable { mutableStateOf("20") }
    val parsedMin = customInput.trim().toIntOrNull()?.takeIf { it in 1..480 }

    ModalBottomSheet(sheetState = sheet, onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.Bedtime, null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.sleep_timer_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }

            if (state.active) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            when (state.mode) {
                                SleepTimerMode.COUNTDOWN ->
                                    stringResource(R.string.sleep_timer_remaining_fmt, formatCountdownMs(state.remainingMs))
                                SleepTimerMode.AFTER_CURRENT_TRACK ->
                                    stringResource(R.string.sleep_timer_after_track)
                                SleepTimerMode.OFF -> stringResource(R.string.sleep_timer_off)
                            },
                            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (state.mode == SleepTimerMode.COUNTDOWN) {
                                FilledTonalButton(onClick = onExtend5Min) {
                                    Text(stringResource(R.string.sleep_timer_extend_5m))
                                }
                            }
                            OutlinedButton(onClick = { onCancel(); onDismiss() }) {
                                Text(stringResource(R.string.sleep_timer_cancel))
                            }
                        }
                    }
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf(10, 15, 30, 45, 60, 90)) { min ->
                    FilterChip(
                        selected = false,
                        onClick = { onStartMinutes(min); onDismiss() },
                        label = { Text(stringResource(R.string.sleep_timer_minutes_fmt, min)) },
                    )
                }
            }

            if (canStopAfterTrack) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (state.mode == SleepTimerMode.AFTER_CURRENT_TRACK) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    onClick = { onStartAfterTrack(); onDismiss() },
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.sleep_timer_after_track), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.sleep_timer_after_track_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = customInput,
                    onValueChange = { customInput = it.filter { ch -> ch.isDigit() }.take(3) },
                    label = { Text(stringResource(R.string.sleep_timer_custom_min)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { parsedMin?.let { onStartMinutes(it); onDismiss() } },
                    enabled = parsedMin != null,
                ) {
                    Text(stringResource(R.string.sleep_timer_start))
                }
            }
        }
    }
}

@Composable
internal fun interactiveTimedActionLabel(action: InteractiveTimedAction): String = when (action) {
    is InteractiveTimedAction.FadeStop ->
        if (action.fadeSeconds <= 0) stringResource(R.string.interactive_timer_action_stop_now)
        else stringResource(R.string.interactive_timer_action_fade_stop_fmt, action.fadeSeconds)
    InteractiveTimedAction.Advance -> stringResource(R.string.interactive_timer_action_advance)
    InteractiveTimedAction.ToggleFast -> stringResource(R.string.interactive_timer_action_fast)
    is InteractiveTimedAction.TakeOutro ->
        if (action.number != null) stringResource(R.string.interactive_timer_action_outro_num_fmt, action.number)
        else stringResource(R.string.interactive_timer_action_outro)
    is InteractiveTimedAction.SelectRegion -> stringResource(R.string.interactive_timer_action_region_fmt, action.label)
    is InteractiveTimedAction.SelectVersion -> stringResource(R.string.interactive_timer_action_version_fmt, action.label)
    InteractiveTimedAction.ToggleSpeakerOverlay -> stringResource(R.string.interactive_timer_action_speaker)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InteractiveTimerSheet(
    timer: InteractiveTimerState,
    flow: PlayerViewModel.FlowUi,
    onStart: (Long, InteractiveTimedAction) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val availableActions = remember(
        flow.title,
        flow.hasFast,
        flow.outros,
        flow.regionNumbers,
        flow.versionNumbers,
        flow.hasSpeakerOverlay,
        flow.isRainbowMelodyOverlay,
    ) {
        buildList<InteractiveTimedAction> {
            add(InteractiveTimedAction.FadeStop(3))
            add(InteractiveTimedAction.FadeStop(1))
            add(InteractiveTimedAction.FadeStop(5))
            add(InteractiveTimedAction.FadeStop(0))
            if (flow.canAdvance || flow.sections.size > 1) {
                add(InteractiveTimedAction.Advance)
            }
            if (flow.hasFast) {
                add(InteractiveTimedAction.ToggleFast)
            }
            if (flow.outros.size == 1) {
                add(InteractiveTimedAction.TakeOutro(null))
            } else if (flow.outros.size > 1) {
                add(InteractiveTimedAction.TakeOutro(null))
                flow.outros.forEach { num -> add(InteractiveTimedAction.TakeOutro(num)) }
            }
            flow.regionNumbers.forEach { r -> add(InteractiveTimedAction.SelectRegion(r, r.toString())) }
            flow.versionNumbers.forEach { v -> add(InteractiveTimedAction.SelectVersion(v, v.toString())) }
            if (flow.hasSpeakerOverlay) {
                add(InteractiveTimedAction.ToggleSpeakerOverlay)
            }
        }
    }

    var selectedIdx by rememberSaveable(flow.title) { mutableIntStateOf(0) }
    val safeSelected = availableActions.getOrElse(selectedIdx) { InteractiveTimedAction.FadeStop(3) }
    var delaySecInput by rememberSaveable { mutableStateOf("60") }
    var selectedPresetSec by rememberSaveable { mutableLongStateOf(60L) }
    val parsedSec = delaySecInput.trim().toLongOrNull()?.takeIf { it in 1L..10_800L }

    ModalBottomSheet(sheetState = sheet, onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.Schedule, null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.interactive_timer_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Text(
                stringResource(R.string.interactive_timer_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (timer.active && timer.action != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            val timeStr = formatCountdownMs(timer.remainingMs)
                            Text(
                                if (timer.pausedByTransport) {
                                    stringResource(R.string.interactive_timer_paused_badge, timeStr)
                                } else {
                                    stringResource(
                                        R.string.interactive_timer_remaining_fmt,
                                        timeStr,
                                        interactiveTimedActionLabel(timer.action),
                                    )
                                },
                                style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            if (timer.pausedByTransport) {
                                Text(
                                    interactiveTimedActionLabel(timer.action),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                        OutlinedButton(onClick = { onCancel(); onDismiss() }) {
                            Text(stringResource(R.string.interactive_timer_cancel))
                        }
                    }
                }
            }

            Text(stringResource(R.string.interactive_timer_duration_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val presets = listOf(15L to true, 30L to true, 60L to false, 120L to false, 300L to false, 600L to false)
                items(presets, key = { it.first }) { (sec, isSecLabel) ->
                    val label = if (isSecLabel) {
                        stringResource(R.string.interactive_timer_sec_fmt, sec.toInt())
                    } else {
                        stringResource(R.string.interactive_timer_min_fmt, (sec / 60L).toInt())
                    }
                    FilterChip(
                        selected = selectedPresetSec == sec && parsedSec == sec,
                        onClick = {
                            selectedPresetSec = sec
                            delaySecInput = sec.toString()
                        },
                        label = { Text(label) },
                    )
                }
            }

            OutlinedTextField(
                value = delaySecInput,
                onValueChange = { raw -> delaySecInput = raw.filter { ch -> ch.isDigit() }.take(5) },
                label = { Text(stringResource(R.string.interactive_timer_custom_seconds)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Text(stringResource(R.string.interactive_timer_action_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                availableActions.forEachIndexed { idx, candidate ->
                    val selected = idx == selectedIdx
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        onClick = { selectedIdx = idx },
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                interactiveTimedActionLabel(candidate),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.weight(1f),
                            )
                            RadioButton(selected = selected, onClick = null)
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val secs = parsedSec ?: 60L
                    onStart(secs, safeSelected)
                    onDismiss()
                },
                enabled = parsedSec != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.interactive_timer_start), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
internal fun AbLoopBar(
    aFrame: Long?,
    bFrame: Long?,
    sampleRate: Int,
    enabled: Boolean,
    onSetA: () -> Unit,
    onSetB: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rate = sampleRate.coerceAtLeast(1)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            if (aFrame != null && bFrame != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (aFrame != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Loop, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    val aText = EngineSnapshot.formatPosition(aFrame, rate)
                    val bText = bFrame?.let { EngineSnapshot.formatPosition(it, rate) }
                    Text(
                        if (bText != null) stringResource(R.string.ab_loop_badge_ab, aText, bText)
                        else stringResource(R.string.ab_loop_badge_a, aText),
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onSetA,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Text(stringResource(R.string.ab_loop_set_a), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = onSetB,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Text(stringResource(R.string.ab_loop_set_b), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (aFrame != null || bFrame != null) {
                    FilledTonalButton(
                        onClick = onClear,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(stringResource(R.string.ab_loop_clear), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
internal fun TrackSortBar(
    selected: TrackSortOrder,
    includeOstOrder: Boolean,
    onSelect: (TrackSortOrder) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = remember(includeOstOrder) {
        buildList {
            add(TrackSortOrder.DEFAULT to R.string.sort_default)
            if (includeOstOrder) add(TrackSortOrder.OST_INDEX to R.string.sort_ost_index)
            add(TrackSortOrder.NAME_ASC to R.string.sort_name_asc)
            add(TrackSortOrder.DURATION_DESC to R.string.sort_duration_desc)
            add(TrackSortOrder.MOST_PLAYED to R.string.sort_most_played)
        }
    }
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        items(options, key = { it.first.name }) { (order, labelRes) ->
            FilterChip(
                selected = selected == order,
                onClick = { onSelect(order) },
                label = { Text(stringResource(labelRes), maxLines = 1) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeToTrackActionBox(
    enabled: Boolean = true,
    onSwipeStartToEnd: (() -> Unit)? = null,
    startToEndIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
    startToEndLabel: String = stringResource(R.string.swipe_action_play_next),
    onSwipeEndToStart: (() -> Unit)? = null,
    endToStartIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Outlined.FavoriteBorder,
    endToStartLabel: String = stringResource(R.string.swipe_action_favorite),
    endToStartDestructive: Boolean = false,
    content: @Composable () -> Unit,
) {
    if (!enabled || (onSwipeStartToEnd == null && onSwipeEndToStart == null)) {
        content()
        return
    }
    val haptic = LocalHapticFeedback.current
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { target ->
            when (target) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSwipeStartToEnd?.invoke()
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSwipeEndToStart?.invoke()
                }
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false // Snap back cleanly instead of removing the row visually
        },
        positionalThreshold = { distance -> distance * 0.28f },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = onSwipeStartToEnd != null,
        enableDismissFromEndToStart = onSwipeEndToStart != null,
        backgroundContent = {
            val dir = dismissState.dismissDirection
            val bgColor by animateColorAsState(
                targetValue = when (dir) {
                    SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                    SwipeToDismissBoxValue.EndToStart ->
                        if (endToStartDestructive) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.secondaryContainer
                    SwipeToDismissBoxValue.Settled -> Color.Transparent
                },
                animationSpec = tween(140),
                label = "swipe-bg",
            )
            val align = if (dir == SwipeToDismissBoxValue.EndToStart) Alignment.CenterEnd else Alignment.CenterStart
            val contentTint = when (dir) {
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.onPrimaryContainer
                SwipeToDismissBoxValue.EndToStart ->
                    if (endToStartDestructive) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onSecondaryContainer
                SwipeToDismissBoxValue.Settled -> MaterialTheme.colorScheme.onSurface
            }
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(bgColor).padding(horizontal = 20.dp),
                contentAlignment = align,
            ) {
                when (dir) {
                    SwipeToDismissBoxValue.StartToEnd -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(startToEndIcon, null, tint = contentTint)
                        Text(startToEndLabel, color = contentTint, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                    SwipeToDismissBoxValue.EndToStart -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(endToStartLabel, color = contentTint, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Icon(endToStartIcon, null, tint = contentTint)
                    }
                    SwipeToDismissBoxValue.Settled -> Unit
                }
            }
        },
        content = { content() },
    )
}

internal class DragReorderController(
    val listState: LazyListState,
    private val sectionStartIndex: () -> Int = { 0 },
    private val itemCountProvider: () -> Int,
    private val onMove: (from: Int, to: Int) -> Unit,
) {
    var draggingIndex by mutableStateOf<Int?>(null)
        private set
    var dragDeltaY by mutableFloatStateOf(0f)
        private set

    fun onDragStart(itemIndex: Int) {
        draggingIndex = itemIndex
        dragDeltaY = 0f
    }

    fun onDrag(deltaY: Float) {
        val currentIdx = draggingIndex ?: return
        val maxIdx = itemCountProvider() - 1
        if (currentIdx !in 0..maxIdx) return
        dragDeltaY += deltaY
        val baseOffset = sectionStartIndex()
        val adapterIdx = baseOffset + currentIdx
        val items: List<LazyListItemInfo> = listState.layoutInfo.visibleItemsInfo
        val currentInfo = items.firstOrNull { it.index == adapterIdx } ?: return
        val center = currentInfo.offset + dragDeltaY + currentInfo.size / 2f
        if (dragDeltaY > 0f && currentIdx < maxIdx) {
            val nextInfo = items.firstOrNull { it.index == adapterIdx + 1 } ?: return
            if (center > nextInfo.offset + nextInfo.size / 2f) {
                onMove(currentIdx, currentIdx + 1)
                draggingIndex = currentIdx + 1
                dragDeltaY -= nextInfo.size
            }
        } else if (dragDeltaY < 0f && currentIdx > 0) {
            val prevInfo = items.firstOrNull { it.index == adapterIdx - 1 } ?: return
            if (center < prevInfo.offset + prevInfo.size / 2f) {
                onMove(currentIdx, currentIdx - 1)
                draggingIndex = currentIdx - 1
                dragDeltaY += prevInfo.size
            }
        }
    }

    fun onDragEnd() {
        draggingIndex = null
        dragDeltaY = 0f
    }
}

@Composable
internal fun DragHandleButton(
    index: Int,
    controller: DragReorderController,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val currentIdx by rememberUpdatedState(index)
    Icon(
        imageVector = Icons.Filled.DragHandle,
        contentDescription = stringResource(R.string.queue_drag_reorder_hint),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .size(36.dp)
            .padding(6.dp)
            .pointerInput(controller) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        controller.onDragStart(currentIdx)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        controller.onDrag(dragAmount.y)
                    },
                    onDragEnd = { controller.onDragEnd() },
                    onDragCancel = { controller.onDragEnd() },
                )
            },
    )
}
