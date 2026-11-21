package com.mylittleblover.mkwplayer.ui.settings

import android.os.SystemClock
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mylittleblover.mkwplayer.BuildConfig
import com.mylittleblover.mkwplayer.R
import com.mylittleblover.mkwplayer.i18n.AppLanguage
import com.mylittleblover.mkwplayer.unlock.UnlockTestMode

@OptIn(ExperimentalMaterial3Api::class)
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
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val cardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    val cardShape = RoundedCornerShape(20.dp)
    val rowColors = ListItemDefaults.colors(containerColor = Color.Transparent)

    if (choosingLanguage) ModalBottomSheet(sheetState = sheet, onDismissRequest = { choosingLanguage = false }) {
        Column(Modifier.fillMaxWidth().selectableGroup().padding(bottom = 24.dp)) {
            Text(stringResource(R.string.app_language_title), Modifier.padding(24.dp), style = MaterialTheme.typography.headlineSmall)
            AppLanguage.entries.forEach { choice ->
                ListItem(headlineContent = { Text(stringResource(choice.labelResource)) },
                    trailingContent = { RadioButton(selected = language == choice, onClick = null) },
                    modifier = Modifier.selectable(selected = language == choice, role = Role.RadioButton,
                        onClick = { scope.launch { sheet.hide(); choosingLanguage = false; if (language != choice) onLanguage(choice) } }).heightIn(min = 56.dp))
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item("library") {
            Card(Modifier.fillMaxWidth(), shape = cardShape, colors = cardColors) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.settings_library), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.settings_library_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.settings_no_delete_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    sourceName?.let { Text(it, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
                    if (sourceCount != null && playableCount != null) {
                        Text(
                            stringResource(R.string.settings_library_counts, sourceCount, playableCount),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
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
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else if (busy && importProgress.isNotBlank()) {
                        Text(importProgress, style = MaterialTheme.typography.bodySmall)
                    }
                    importDetail?.let { detail ->
                        var expanded by rememberSaveable(detail) { mutableStateOf(false) }
                        TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                            Text(stringResource(R.string.settings_import_details))
                        }
                        AnimatedVisibility(expanded) {
                            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            Text(stringResource(R.string.settings_importing), style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = onCancelImport, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.settings_import_cancel), maxLines = 1)
                            }
                        } else {
                            Text(
                                stringResource(R.string.settings_import_committing),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else if (importState.phase == com.mylittleblover.mkwplayer.pack.PackImportPhase.RESUMABLE && importState.checkpoint != null) {
                        val cp = importState.checkpoint
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    stringResource(
                                        R.string.settings_import_resumable_hint,
                                        cp.displayName,
                                        cp.completedMembers.size,
                                        cp.totalMembers,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = onResumeImport, modifier = Modifier.weight(1f)) {
                                        Text(stringResource(R.string.settings_import_resume), maxLines = 1)
                                    }
                                    OutlinedButton(onClick = onCancelImport, modifier = Modifier.weight(1f)) {
                                        Text(stringResource(R.string.settings_import_discard), maxLines = 1)
                                    }
                                }
                            }
                        }
                    } else if (importState.phase == com.mylittleblover.mkwplayer.pack.PackImportPhase.PAUSED_NEEDS_REPICK && importState.checkpoint != null) {
                        val cp = importState.checkpoint
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    stringResource(
                                        R.string.settings_import_repick_hint,
                                        cp.displayName,
                                        cp.completedMembers.size,
                                        cp.totalMembers,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = onRepickImport, modifier = Modifier.weight(1f)) {
                                        Text(stringResource(R.string.settings_import_repick), maxLines = 1)
                                    }
                                    OutlinedButton(onClick = onCancelImport, modifier = Modifier.weight(1f)) {
                                        Text(stringResource(R.string.settings_import_discard), maxLines = 1)
                                    }
                                }
                            }
                        }
                    } else if (importState.phase == com.mylittleblover.mkwplayer.pack.PackImportPhase.COMPLETED && importState.changelog != null) {
                        val cl = importState.changelog
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    stringResource(R.string.settings_import_summary_title, cl.packName, cl.packVersion),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
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
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                if (cl.poolStatus == com.mylittleblover.mkwplayer.pack.PoolUpdateStatus.REPLACED && cl.poolDataVersion != null) {
                                    Text(
                                        stringResource(R.string.settings_import_pool_replaced, cl.poolDataVersion, cl.poolEdgeCount),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                if (cl.invalidatedCustomRoutesCount > 0) {
                                    Text(
                                        stringResource(R.string.settings_import_invalidated_routes, cl.invalidatedCustomRoutesCount),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                                if (cl.stoppedActivePlayback) {
                                    Text(
                                        stringResource(R.string.settings_import_stopped_playback),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (cl.clearedContinueListening) {
                                    Text(
                                        stringResource(R.string.settings_import_cleared_continue),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = onDismissChangelog, contentPadding = PaddingValues(horizontal = 0.dp)) {
                                    Text(stringResource(R.string.settings_import_dismiss))
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onOpenPack, enabled = !busy, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_import_pack), maxLines = 1)
                        }
                        OutlinedButton(onClick = onOpenFiles, enabled = !busy, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_open_files), maxLines = 1)
                        }
                    }
                }
            }
        }
        item("output-trim") {
            Card(Modifier.fillMaxWidth(), shape = cardShape, colors = cardColors) {
                Column(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.settings_output_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.settings_output_hint), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(Modifier.fillMaxWidth().selectableGroup()) {
                        com.mylittleblover.mkwplayer.audio.OutputTrim.choices.forEach { choice ->
                            ListItem(
                                colors = rowColors,
                                headlineContent = { Text(choice.summary) },
                                supportingContent = {
                                    Text(stringResource(
                                        if (choice.limits) R.string.settings_output_limited else R.string.settings_output_flat))
                                },
                                trailingContent = { RadioButton(selected = choice == outputTrim, onClick = null) },
                                modifier = Modifier.selectable(selected = choice == outputTrim, role = Role.RadioButton,
                                    onClick = { if (choice != outputTrim) onOutputTrim(choice) }).heightIn(min = 56.dp),
                            )
                        }
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
            Card(Modifier.fillMaxWidth(), shape = cardShape, colors = cardColors) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.settings_switch_fade_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.settings_switch_fade_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (playbackBehavior.segmentSwitchFadeOutMs <= 0) {
                            stringResource(R.string.settings_switch_fade_off)
                        } else {
                            stringResource(R.string.settings_switch_fade_seconds_fmt, initialSecondsText)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(0 to "0s", 500 to "0.5s", 1000 to "1s", 2000 to "2s", 5000 to "5s").forEach { (ms, label) ->
                            FilterChip(
                                selected = playbackBehavior.segmentSwitchFadeOutMs == ms,
                                onClick = { onSegmentSwitchFadeOutMs(ms) },
                                label = { Text(label) },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = customInput,
                            onValueChange = { raw ->
                                customInput = raw.filter { ch -> ch.isDigit() || ch == '.' }.take(6)
                            },
                            label = { Text(stringResource(R.string.settings_switch_fade_custom_label)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                        )
                        FilledTonalButton(
                            onClick = { parsedMs?.let(onSegmentSwitchFadeOutMs) },
                            enabled = parsedMs != null && parsedMs != playbackBehavior.segmentSwitchFadeOutMs,
                        ) {
                            Text(stringResource(R.string.settings_switch_fade_apply))
                        }
                    }
                }
            }
        }
        item("perfect-beat") {
            Card(
                onClick = { onPerfectBeat(!playbackBehavior.perfectBeatEnabled) },
                modifier = Modifier.fillMaxWidth(),
                shape = cardShape,
                colors = cardColors,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.settings_perfect_beat_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            stringResource(R.string.settings_perfect_beat_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = playbackBehavior.perfectBeatEnabled,
                        onCheckedChange = onPerfectBeat,
                    )
                }
            }
        }
        item("live-visualizer") {
            Card(
                onClick = { onLiveVisualizer(!playbackBehavior.liveVisualizerEnabled) },
                modifier = Modifier.fillMaxWidth(),
                shape = cardShape,
                colors = cardColors,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.settings_visualizer_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            stringResource(R.string.settings_visualizer_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = playbackBehavior.liveVisualizerEnabled,
                        onCheckedChange = onLiveVisualizer,
                    )
                }
            }
        }
        item("equalizer-entry") {
            val eqActive = dspConfig.eqEnabled || dspConfig.bassBoostPercent > 0 || dspConfig.loudnessDb >= 0.05f
            val presetLabel = com.mylittleblover.mkwplayer.ui.components.EqualizerPresetLabel(dspConfig.preset)
            Card(
                onClick = onOpenEqualizer,
                modifier = Modifier.fillMaxWidth(),
                shape = cardShape,
                colors = cardColors,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.settings_eq_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            stringResource(R.string.settings_eq_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            if (eqActive) stringResource(R.string.settings_eq_summary_on, presetLabel)
                            else stringResource(R.string.settings_eq_summary_off),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (eqActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilledTonalButton(onClick = onOpenEqualizer) {
                        Text(stringResource(R.string.settings_eq_title).substringBefore(" "))
                    }
                }
            }
        }
        item("backup-restore") {
            Card(Modifier.fillMaxWidth(), shape = cardShape, colors = cardColors) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.settings_backup_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.settings_backup_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onExportBackup, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_backup_export), maxLines = 1)
                        }
                        FilledTonalButton(onClick = onImportBackup, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_backup_import), maxLines = 1)
                        }
                    }
                    OutlinedButton(onClick = onExportM3u, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.settings_backup_m3u), maxLines = 1)
                    }
                    if (!backupStatusMessage.isNullOrBlank()) {
                        Text(
                            backupStatusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        item("language-heading") {
            Card(
                onClick = { choosingLanguage = true },
                modifier = Modifier.fillMaxWidth(),
                shape = cardShape,
                colors = cardColors,
            ) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.app_language_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.settings_language_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FilledTonalButton(onClick = { choosingLanguage = true }) {
                        Text(stringResource(language.labelResource))
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(18.dp))
                    }
                }
            }
        }
        if (devUnlocked || testMode != UnlockTestMode.NORMAL) {
            item("unlock-test") {
                Card(
                    Modifier.fillMaxWidth().animateContentSize(tween(200, easing = com.mylittleblover.mkwplayer.ui.AppMotion.easeOut)),
                    shape = cardShape,
                    colors = cardColors,
                ) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        ListItem(
                            colors = rowColors,
                            headlineContent = {
                                Text(stringResource(R.string.settings_dev_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            },
                            supportingContent = {
                                Text(stringResource(R.string.settings_unlock_test_title), style = MaterialTheme.typography.bodySmall)
                            },
                            trailingContent = {
                                Icon(if (devExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                            },
                            modifier = Modifier.selectable(selected = devExpanded, role = Role.Button, onClick = { devExpanded = !devExpanded }),
                        )
                        AnimatedVisibility(devExpanded) {
                            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(stringResource(R.string.settings_unlock_test_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(stringResource(when (testMode) {
                                        UnlockTestMode.NORMAL -> R.string.settings_unlock_test_normal
                                        UnlockTestMode.FORCE_UNLOCKED -> R.string.settings_unlock_test_unlocked
                                        UnlockTestMode.FORCE_LOCKED -> R.string.settings_unlock_test_locked
                                    }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    Text(stringResource(if (testUnlockVisible) R.string.settings_unlock_test_visible else R.string.settings_unlock_test_hidden), style = MaterialTheme.typography.bodySmall)
                                }
                                Column(Modifier.fillMaxWidth().selectableGroup()) {
                                    listOf(
                                        UnlockTestMode.NORMAL to R.string.settings_unlock_test_restore,
                                        UnlockTestMode.FORCE_UNLOCKED to R.string.settings_unlock_test_unlock,
                                        UnlockTestMode.FORCE_LOCKED to R.string.settings_unlock_test_lock,
                                    ).forEach { (mode, label) ->
                                        ListItem(
                                            colors = rowColors,
                                            headlineContent = { Text(stringResource(label)) },
                                            trailingContent = { RadioButton(selected = mode == testMode, onClick = null) },
                                            modifier = Modifier.selectable(selected = mode == testMode, role = Role.RadioButton,
                                                onClick = { if (mode != testMode) onTestUnlock(mode) }).heightIn(min = 52.dp),
                                        )
                                    }
                                }
                                OutlinedButton(
                                    onClick = onTestCeremony,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                                ) {
                                    Text(stringResource(R.string.settings_unlock_test_ceremony_btn), maxLines = 1)
                                }
                                OutlinedButton(
                                    onClick = onTestUnlockVfx,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                                ) {
                                    Text(stringResource(R.string.settings_unlock_test_vfx_btn), maxLines = 1)
                                }
                                OutlinedButton(
                                    onClick = onTestUnlockNotification,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                                ) {
                                    Text(stringResource(R.string.settings_unlock_test_notif_btn), maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
        item("about") {
            Card(Modifier.fillMaxWidth(), shape = cardShape, colors = cardColors) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
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
                    Text(stringResource(R.string.map_art_notice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
