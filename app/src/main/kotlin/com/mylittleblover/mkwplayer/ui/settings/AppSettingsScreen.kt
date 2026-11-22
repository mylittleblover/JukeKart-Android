package com.mylittleblover.mkwplayer.ui.settings

import android.os.SystemClock
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mylittleblover.mkwplayer.BuildConfig
import com.mylittleblover.mkwplayer.R
import com.mylittleblover.mkwplayer.i18n.AppLanguage
import com.mylittleblover.mkwplayer.ui.kit.MkwBottomSheet
import com.mylittleblover.mkwplayer.ui.kit.MkwButtonSize
import com.mylittleblover.mkwplayer.ui.kit.MkwCard
import com.mylittleblover.mkwplayer.ui.kit.MkwDangerButton
import com.mylittleblover.mkwplayer.ui.kit.MkwDialog
import com.mylittleblover.mkwplayer.ui.kit.MkwDivider
import com.mylittleblover.mkwplayer.ui.kit.MkwFilterPill
import com.mylittleblover.mkwplayer.ui.kit.MkwGhostButton
import com.mylittleblover.mkwplayer.ui.kit.MkwKitTokens
import com.mylittleblover.mkwplayer.ui.kit.MkwOptionRow
import com.mylittleblover.mkwplayer.ui.kit.MkwPrimaryButton
import com.mylittleblover.mkwplayer.ui.kit.MkwRadioMark
import com.mylittleblover.mkwplayer.ui.kit.MkwRoseButton
import com.mylittleblover.mkwplayer.ui.kit.MkwSecondaryButton
import com.mylittleblover.mkwplayer.ui.kit.MkwSurfaceTone
import com.mylittleblover.mkwplayer.ui.kit.MkwSwitch
import com.mylittleblover.mkwplayer.ui.kit.MkwTextField
import com.mylittleblover.mkwplayer.unlock.UnlockTestMode

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun AppSettingsScreen(
    language: AppLanguage, onLanguage: (AppLanguage) -> Unit,
    busy: Boolean, sourceName: String?, onOpenPack: () -> Unit, onOpenFiles: () -> Unit,
    modifier: Modifier = Modifier,
    testMode: UnlockTestMode = UnlockTestMode.NORMAL,
    testUnlockVisible: Boolean = false,
    onTestUnlock: (UnlockTestMode) -> Unit = {},
    onTestCeremony: () -> Unit = {},
    onTestUnlockVfx: () -> Unit = {},
    onTestUnlockNotification: () -> Unit = {},
    onResetUnlockProgress: () -> Unit = {},
    outputTrim: com.mylittleblover.mkwplayer.audio.OutputTrim = com.mylittleblover.mkwplayer.audio.OutputTrim.default,
    onOutputTrim: (com.mylittleblover.mkwplayer.audio.OutputTrim) -> Unit = {},
    playbackBehavior: com.mylittleblover.mkwplayer.audio.PlaybackBehaviorConfig = com.mylittleblover.mkwplayer.audio.PlaybackBehaviorConfig(),
    onSegmentSwitchFadeOutMs: (Int) -> Unit = {},
    onPerfectBeat: (Boolean) -> Unit = {},
    dspConfig: com.mylittleblover.mkwplayer.audio.MasterDspStateConfig = com.mylittleblover.mkwplayer.audio.MasterDspStateConfig(),
    onLiveVisualizer: (Boolean) -> Unit = {},
    onOpenEqualizer: () -> Unit = {},
    onExportBackup: () -> Unit = {},
    onImportBackup: () -> Unit = {},
    onExportM3u: () -> Unit = {},
    backupStatusMessage: String? = null,
    sourceCount: Int? = null, playableCount: Int? = null, importProgress: String = "", importDetail: String? = null,
    importState: com.mylittleblover.mkwplayer.pack.PackImportUiState = com.mylittleblover.mkwplayer.pack.PackImportUiState(),
    onCancelImport: () -> Unit = {},
    onResumeImport: () -> Unit = {},
    onRepickImport: () -> Unit = {},
    onDismissChangelog: () -> Unit = {},
) {
    var choosingLanguage by rememberSaveable { mutableStateOf(false) }
    var devUnlocked by rememberSaveable { mutableStateOf(testMode != UnlockTestMode.NORMAL) }
    var devExpanded by rememberSaveable { mutableStateOf(testMode != UnlockTestMode.NORMAL) }
    val tapTracker = remember { DevOptionsTapTracker() }
    LaunchedEffect(testMode) {
        if (testMode != UnlockTestMode.NORMAL) {
            devUnlocked = true
            tapTracker.seedUnlocked(true)
        }
    }
    LaunchedEffect(devUnlocked) {
        if (devUnlocked) tapTracker.seedUnlocked(true)
    }
    val context = LocalContext.current
    var activeToast by remember { mutableStateOf<Toast?>(null) }
    var confirmResetUnlock by rememberSaveable { mutableStateOf(false) }

    if (confirmResetUnlock) {
        MkwDialog(
            onDismissRequest = { confirmResetUnlock = false },
            title = stringResource(R.string.settings_unlock_reset_confirm_title),
            confirmButton = {
                MkwDangerButton(
                    text = stringResource(R.string.settings_unlock_reset_confirm_action),
                    onClick = {
                        confirmResetUnlock = false
                        onResetUnlockProgress()
                        activeToast?.cancel()
                        activeToast = Toast.makeText(
                            context,
                            context.getString(R.string.settings_unlock_reset_done),
                            Toast.LENGTH_SHORT,
                        ).also { it.show() }
                    },
                    size = MkwButtonSize.Compact,
                )
            },
            dismissButton = {
                MkwGhostButton(
                    text = stringResource(R.string.discovery_cancel),
                    onClick = { confirmResetUnlock = false },
                )
            },
        ) {
            Text(
                stringResource(R.string.settings_unlock_reset_confirm_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MkwKitTokens.Palette.inkSecondary,
            )
        }
    }

    if (choosingLanguage) {
        MkwBottomSheet(
            onDismissRequest = { choosingLanguage = false },
            title = stringResource(R.string.app_language_title),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AppLanguage.entries.forEach { choice ->
                    val selected = language == choice
                    MkwOptionRow(
                        headline = stringResource(choice.labelResource),
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = {
                            choosingLanguage = false
                            if (!selected) onLanguage(choice)
                        },
                        trailing = { MkwRadioMark(selected = selected) },
                    )
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 16.dp,
            end = 16.dp,
            bottom = com.mylittleblover.mkwplayer.ui.rememberMiniPlayerBottomClearance(16.dp),
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item("library") {
            MkwCard(tone = MkwSurfaceTone.Porcelain, contentPadding = PaddingValues(18.dp)) {
                Text(stringResource(R.string.settings_library), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MkwKitTokens.Palette.ink)
                Text(stringResource(R.string.settings_library_hint), style = MaterialTheme.typography.bodySmall, color = MkwKitTokens.Palette.inkSecondary)
                Text(stringResource(R.string.settings_no_delete_hint), style = MaterialTheme.typography.bodySmall, color = MkwKitTokens.Palette.inkSecondary)
                sourceName?.let { Text(it, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MkwKitTokens.Palette.ink) }
                if (sourceCount != null && playableCount != null && (!busy || sourceCount > 0)) {
                    Text(
                        stringResource(R.string.settings_library_counts, sourceCount, playableCount),
                        style = MkwKitTokens.tabular(MaterialTheme.typography.labelLarge),
                        color = MkwKitTokens.Palette.crimson,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (busy && importState.totalMembers > 0) {
                    Text(
                        stringResource(
                            R.string.settings_import_progress_members,
                            importState.processedMembers,
                            importState.totalMembers,
                            importState.currentMember.ifBlank { importState.displayName },
                        ),
                        style = MkwKitTokens.tabular(MaterialTheme.typography.bodySmall),
                        color = MkwKitTokens.Palette.inkSecondary,
                    )
                } else if (busy && importProgress.isNotBlank()) {
                    Text(importProgress, style = MaterialTheme.typography.bodySmall, color = MkwKitTokens.Palette.inkSecondary)
                }
                importDetail?.let { detail ->
                    var expanded by rememberSaveable(detail) { mutableStateOf(false) }
                    MkwGhostButton(
                        text = stringResource(R.string.settings_import_details),
                        onClick = { expanded = !expanded },
                    )
                    AnimatedVisibility(expanded) {
                        Text(detail, style = MaterialTheme.typography.bodySmall, color = MkwKitTokens.Palette.inkSecondary)
                    }
                }
                if (busy) {
                    val progress = if (importState.phase == com.mylittleblover.mkwplayer.pack.PackImportPhase.PROBING) {
                        0.15f
                    } else {
                        importState.fraction
                    }
                    com.mylittleblover.mkwplayer.ui.SmoothProgressIndicator(progress, Modifier.fillMaxWidth())
                    if (importState.canCancel) {
                        Text(stringResource(R.string.settings_importing), style = MaterialTheme.typography.bodySmall, color = MkwKitTokens.Palette.inkSecondary)
                        MkwSecondaryButton(
                            text = stringResource(R.string.settings_import_cancel),
                            onClick = onCancelImport,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(
                            stringResource(R.string.settings_import_committing),
                            style = MaterialTheme.typography.bodySmall,
                            color = MkwKitTokens.Palette.crimson,
                        )
                    }
                } else if (importState.phase == com.mylittleblover.mkwplayer.pack.PackImportPhase.RESUMABLE && importState.checkpoint != null) {
                    val cp = importState.checkpoint
                    MkwCard(
                        tone = MkwSurfaceTone.RoseTint,
                        selected = true,
                        shape = MkwKitTokens.Shape.control,
                        contentPadding = PaddingValues(12.dp),
                    ) {
                        Text(
                            stringResource(
                                R.string.settings_import_resumable_hint,
                                cp.displayName,
                                cp.completedMembers.size,
                                cp.totalMembers,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MkwKitTokens.Palette.crimsonDeep,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MkwPrimaryButton(
                                text = stringResource(R.string.settings_import_resume),
                                onClick = onResumeImport,
                                size = MkwButtonSize.Compact,
                                modifier = Modifier.weight(1f),
                            )
                            MkwSecondaryButton(
                                text = stringResource(R.string.settings_import_discard),
                                onClick = onCancelImport,
                                size = MkwButtonSize.Compact,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                } else if (importState.phase == com.mylittleblover.mkwplayer.pack.PackImportPhase.PAUSED_NEEDS_REPICK && importState.checkpoint != null) {
                    val cp = importState.checkpoint
                    MkwCard(
                        tone = MkwSurfaceTone.RoseTint,
                        selected = true,
                        shape = MkwKitTokens.Shape.control,
                        contentPadding = PaddingValues(12.dp),
                    ) {
                        Text(
                            stringResource(
                                R.string.settings_import_repick_hint,
                                cp.displayName,
                                cp.completedMembers.size,
                                cp.totalMembers,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MkwKitTokens.Palette.crimsonDeep,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MkwPrimaryButton(
                                text = stringResource(R.string.settings_import_repick),
                                onClick = onRepickImport,
                                size = MkwButtonSize.Compact,
                                modifier = Modifier.weight(1f),
                            )
                            MkwSecondaryButton(
                                text = stringResource(R.string.settings_import_discard),
                                onClick = onCancelImport,
                                size = MkwButtonSize.Compact,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                } else if (importState.phase == com.mylittleblover.mkwplayer.pack.PackImportPhase.COMPLETED && importState.changelog != null) {
                    val cl = importState.changelog
                    MkwCard(
                        tone = MkwSurfaceTone.White,
                        shape = MkwKitTokens.Shape.control,
                        contentPadding = PaddingValues(12.dp),
                    ) {
                        Text(
                            stringResource(R.string.settings_import_summary_title, cl.packName, cl.packVersion),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MkwKitTokens.Palette.ink,
                        )
                        Text(
                            stringResource(
                                R.string.settings_import_summary_body,
                                cl.addedOrUpdatedTracks,
                                cl.addedOrUpdatedAssets,
                                cl.playableLibraryTracks,
                                cl.totalLibraryTracks,
                                cl.jukeboxTrackCount,
                            ),
                            style = MkwKitTokens.tabular(MaterialTheme.typography.bodySmall),
                            color = MkwKitTokens.Palette.inkSecondary,
                        )
                        if (cl.poolStatus == com.mylittleblover.mkwplayer.pack.PoolUpdateStatus.REPLACED && cl.poolDataVersion != null) {
                            Text(
                                stringResource(R.string.settings_import_pool_replaced, cl.poolDataVersion, cl.poolEdgeCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MkwKitTokens.Palette.crimson,
                            )
                        }
                        if (cl.invalidatedCustomRoutesCount > 0) {
                            Text(
                                stringResource(R.string.settings_import_invalidated_routes, cl.invalidatedCustomRoutesCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MkwKitTokens.Palette.danger,
                            )
                        }
                        if (cl.stoppedActivePlayback) {
                            Text(
                                stringResource(R.string.settings_import_stopped_playback),
                                style = MaterialTheme.typography.bodySmall,
                                color = MkwKitTokens.Palette.inkSecondary,
                            )
                        }
                        if (cl.clearedContinueListening) {
                            Text(
                                stringResource(R.string.settings_import_cleared_continue),
                                style = MaterialTheme.typography.bodySmall,
                                color = MkwKitTokens.Palette.inkSecondary,
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            MkwGhostButton(
                                text = stringResource(R.string.settings_import_dismiss),
                                onClick = onDismissChangelog,
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MkwPrimaryButton(
                        text = stringResource(R.string.settings_import_pack),
                        onClick = onOpenPack,
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    )
                    MkwSecondaryButton(
                        text = stringResource(R.string.settings_open_files),
                        onClick = onOpenFiles,
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        item("output-trim") {
            MkwCard(
                tone = MkwSurfaceTone.Porcelain,
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 10.dp),
            ) {
                Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.settings_output_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MkwKitTokens.Palette.ink)
                    Text(
                        stringResource(R.string.settings_output_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MkwKitTokens.Palette.inkSecondary,
                    )
                }
                Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    com.mylittleblover.mkwplayer.audio.OutputTrim.choices.forEach { choice ->
                        val selected = choice == outputTrim
                        MkwOptionRow(
                            headline = choice.summary,
                            supporting = stringResource(
                                if (choice.limits) R.string.settings_output_limited else R.string.settings_output_flat,
                            ),
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { if (!selected) onOutputTrim(choice) },
                            trailing = { MkwRadioMark(selected = selected) },
                        )
                    }
                }
            }
        }
        item("segment-switch-fade") {
            val initialSecondsText = remember(playbackBehavior.segmentSwitchFadeOutMs) {
                val ms = playbackBehavior.segmentSwitchFadeOutMs
                when {
                    ms <= 0 -> "0"
                    ms % 1000 == 0 -> (ms / 1000).toString()
                    else -> String.format(java.util.Locale.ROOT, "%.2f", ms / 1000.0).trimEnd('0').trimEnd('.')
                }
            }
            var customInput by remember(playbackBehavior.segmentSwitchFadeOutMs) {
                mutableStateOf(initialSecondsText)
            }
            val parsedMs = remember(customInput) {
                val secs = customInput.trim().toDoubleOrNull()
                if (secs != null && secs.isFinite() && secs >= 0.0) {
                    kotlin.math.round(secs * 1000.0).toInt().coerceIn(
                        0,
                        com.mylittleblover.mkwplayer.audio.PlaybackBehaviorSettings.MAX_SEGMENT_SWITCH_FADEOUT_MS,
                    )
                } else null
            }
            MkwCard(tone = MkwSurfaceTone.Porcelain, contentPadding = PaddingValues(18.dp)) {
                Text(
                    stringResource(R.string.settings_switch_fade_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MkwKitTokens.Palette.ink,
                )
                Text(
                    stringResource(R.string.settings_switch_fade_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MkwKitTokens.Palette.inkSecondary,
                )
                Text(
                    text = if (playbackBehavior.segmentSwitchFadeOutMs <= 0) {
                        stringResource(R.string.settings_switch_fade_off)
                    } else {
                        stringResource(R.string.settings_switch_fade_seconds_fmt, initialSecondsText)
                    },
                    style = MkwKitTokens.tabular(MaterialTheme.typography.labelLarge),
                    color = MkwKitTokens.Palette.crimson,
                    fontWeight = FontWeight.SemiBold,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(0 to "0s", 500 to "0.5s", 1000 to "1s", 2000 to "2s", 5000 to "5s").forEach { (ms, label) ->
                        MkwFilterPill(
                            label = label,
                            selected = playbackBehavior.segmentSwitchFadeOutMs == ms,
                            onClick = { onSegmentSwitchFadeOutMs(ms) },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MkwTextField(
                        value = customInput,
                        onValueChange = { raw ->
                            customInput = raw.filter { ch -> ch.isDigit() || ch == '.' }.take(6)
                        },
                        label = stringResource(R.string.settings_switch_fade_custom_label),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    MkwRoseButton(
                        text = stringResource(R.string.settings_switch_fade_apply),
                        onClick = { parsedMs?.let(onSegmentSwitchFadeOutMs) },
                        enabled = parsedMs != null && parsedMs != playbackBehavior.segmentSwitchFadeOutMs,
                        size = MkwButtonSize.Hero,
                    )
                }
            }
        }
        item("perfect-beat") {
            MkwCard(
                onClick = { onPerfectBeat(!playbackBehavior.perfectBeatEnabled) },
                tone = MkwSurfaceTone.Porcelain,
                contentPadding = PaddingValues(18.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.settings_perfect_beat_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MkwKitTokens.Palette.ink,
                        )
                        Text(
                            stringResource(R.string.settings_perfect_beat_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MkwKitTokens.Palette.inkSecondary,
                        )
                    }
                    MkwSwitch(
                        checked = playbackBehavior.perfectBeatEnabled,
                        onCheckedChange = onPerfectBeat,
                    )
                }
            }
        }
        item("live-visualizer") {
            MkwCard(
                onClick = { onLiveVisualizer(!playbackBehavior.liveVisualizerEnabled) },
                tone = MkwSurfaceTone.Porcelain,
                contentPadding = PaddingValues(18.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.settings_visualizer_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MkwKitTokens.Palette.ink,
                        )
                        Text(
                            stringResource(R.string.settings_visualizer_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MkwKitTokens.Palette.inkSecondary,
                        )
                    }
                    MkwSwitch(
                        checked = playbackBehavior.liveVisualizerEnabled,
                        onCheckedChange = onLiveVisualizer,
                    )
                }
            }
        }
        item("equalizer-entry") {
            val eqActive = dspConfig.eqEnabled || dspConfig.bassBoostPercent > 0 || dspConfig.loudnessDb >= 0.05f
            val presetLabel = com.mylittleblover.mkwplayer.ui.components.EqualizerPresetLabel(dspConfig.preset)
            MkwCard(
                onClick = onOpenEqualizer,
                tone = MkwSurfaceTone.Porcelain,
                contentPadding = PaddingValues(18.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.settings_eq_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MkwKitTokens.Palette.ink,
                        )
                        Text(
                            stringResource(R.string.settings_eq_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MkwKitTokens.Palette.inkSecondary,
                        )
                        Text(
                            if (eqActive) stringResource(R.string.settings_eq_summary_on, presetLabel)
                            else stringResource(R.string.settings_eq_summary_off),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (eqActive) MkwKitTokens.Palette.crimson else MkwKitTokens.Palette.inkSecondary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    MkwRoseButton(
                        text = stringResource(R.string.eq_short_label),
                        onClick = onOpenEqualizer,
                        size = MkwButtonSize.Compact,
                    )
                }
            }
        }
        item("backup-restore") {
            MkwCard(tone = MkwSurfaceTone.Porcelain, contentPadding = PaddingValues(18.dp)) {
                Text(
                    stringResource(R.string.settings_backup_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MkwKitTokens.Palette.ink,
                )
                Text(
                    stringResource(R.string.settings_backup_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MkwKitTokens.Palette.inkSecondary,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MkwPrimaryButton(
                        text = stringResource(R.string.settings_backup_export),
                        onClick = onExportBackup,
                        modifier = Modifier.weight(1f),
                    )
                    MkwRoseButton(
                        text = stringResource(R.string.settings_backup_import),
                        onClick = onImportBackup,
                        modifier = Modifier.weight(1f),
                    )
                }
                MkwSecondaryButton(
                    text = stringResource(R.string.settings_backup_m3u),
                    onClick = onExportM3u,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!backupStatusMessage.isNullOrBlank()) {
                    Text(
                        backupStatusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MkwKitTokens.Palette.crimson,
                    )
                }
            }
        }
        item("language-heading") {
            MkwCard(
                onClick = { choosingLanguage = true },
                tone = MkwSurfaceTone.Porcelain,
                contentPadding = PaddingValues(18.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.app_language_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MkwKitTokens.Palette.ink)
                        Text(stringResource(R.string.settings_language_hint), style = MaterialTheme.typography.bodySmall, color = MkwKitTokens.Palette.inkSecondary)
                    }
                    MkwFilterPill(
                        label = stringResource(language.labelResource),
                        selected = true,
                        onClick = { choosingLanguage = true },
                        trailingIcon = Icons.Filled.ArrowDropDown,
                        role = Role.Button,
                    )
                }
            }
        }
        if (devUnlocked || testMode != UnlockTestMode.NORMAL) {
            item("unlock-test") {
                MkwCard(
                    modifier = Modifier.fillMaxWidth().animateContentSize(tween(200, easing = com.mylittleblover.mkwplayer.ui.AppMotion.easeOut)),
                    tone = MkwSurfaceTone.Porcelain,
                    contentPadding = PaddingValues(12.dp),
                ) {
                    MkwOptionRow(
                        headline = stringResource(R.string.settings_dev_title),
                        supporting = stringResource(R.string.settings_unlock_test_title),
                        selected = devExpanded,
                        onClick = { devExpanded = !devExpanded },
                        trailing = {
                            Icon(if (devExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, tint = MkwKitTokens.Palette.inkSecondary)
                        },
                    )
                    AnimatedVisibility(devExpanded) {
                        Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(stringResource(R.string.settings_unlock_test_hint), style = MaterialTheme.typography.bodySmall, color = MkwKitTokens.Palette.inkSecondary)
                                Text(
                                    stringResource(when (testMode) {
                                        UnlockTestMode.NORMAL -> R.string.settings_unlock_test_normal
                                        UnlockTestMode.FORCE_UNLOCKED -> R.string.settings_unlock_test_unlocked
                                        UnlockTestMode.FORCE_LOCKED -> R.string.settings_unlock_test_locked
                                    }),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MkwKitTokens.Palette.crimson,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    stringResource(if (testUnlockVisible) R.string.settings_unlock_test_visible else R.string.settings_unlock_test_hidden),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MkwKitTokens.Palette.inkSecondary,
                                )
                            }
                            Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(
                                    UnlockTestMode.NORMAL to R.string.settings_unlock_test_restore,
                                    UnlockTestMode.FORCE_UNLOCKED to R.string.settings_unlock_test_unlock,
                                    UnlockTestMode.FORCE_LOCKED to R.string.settings_unlock_test_lock,
                                ).forEach { (mode, label) ->
                                    val selected = mode == testMode
                                    MkwOptionRow(
                                        headline = stringResource(label),
                                        selected = selected,
                                        role = Role.RadioButton,
                                        onClick = { if (!selected) onTestUnlock(mode) },
                                        trailing = { MkwRadioMark(selected = selected) },
                                    )
                                }
                            }
                            MkwSecondaryButton(
                                text = stringResource(R.string.settings_unlock_test_ceremony_btn),
                                onClick = onTestCeremony,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            MkwSecondaryButton(
                                text = stringResource(R.string.settings_unlock_test_vfx_btn),
                                onClick = onTestUnlockVfx,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            MkwSecondaryButton(
                                text = stringResource(R.string.settings_unlock_test_notif_btn),
                                onClick = onTestUnlockNotification,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            MkwDivider(modifier = Modifier.padding(vertical = 2.dp))
                            MkwDangerButton(
                                text = stringResource(R.string.settings_unlock_reset_btn),
                                onClick = { confirmResetUnlock = true },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
        item("about") {
            MkwCard(tone = MkwSurfaceTone.Porcelain, contentPadding = PaddingValues(18.dp)) {
                Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MkwKitTokens.Palette.ink)
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleSmall, color = MkwKitTokens.Palette.ink)
                Text(
                    stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                    style = MkwKitTokens.tabular(MaterialTheme.typography.bodyMedium),
                    color = MkwKitTokens.Palette.inkSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MkwKitTokens.Shape.thumb)
                        .clickable {
                            val res = tapTracker.tap(SystemClock.elapsedRealtime())
                            val msg = when (res) {
                                DevOptionsTapTracker.Result.Ignored -> null
                                is DevOptionsTapTracker.Result.Progress ->
                                    context.getString(R.string.settings_dev_taps_remaining, res.remaining)
                                DevOptionsTapTracker.Result.UnlockedNow -> {
                                    devUnlocked = true
                                    devExpanded = true
                                    context.getString(R.string.settings_dev_unlocked)
                                }
                                DevOptionsTapTracker.Result.AlreadyUnlocked -> {
                                    devUnlocked = true
                                    context.getString(R.string.settings_dev_already_unlocked)
                                }
                            }
                            if (msg != null) {
                                activeToast?.cancel()
                                activeToast = Toast.makeText(context, msg, Toast.LENGTH_SHORT).also { it.show() }
                            }
                        }
                        .padding(vertical = 4.dp),
                )
                Text(stringResource(R.string.map_art_notice), style = MaterialTheme.typography.bodySmall, color = MkwKitTokens.Palette.inkSecondary)
            }
        }
    }
}
