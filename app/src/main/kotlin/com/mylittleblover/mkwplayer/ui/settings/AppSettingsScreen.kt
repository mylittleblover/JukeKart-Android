package com.mylittleblover.mkwplayer.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
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
    outputTrim: com.mylittleblover.mkwplayer.audio.OutputTrim = com.mylittleblover.mkwplayer.audio.OutputTrim.default,
    onOutputTrim: (com.mylittleblover.mkwplayer.audio.OutputTrim) -> Unit = {},
    sourceCount: Int? = null, playableCount: Int? = null, importProgress: String = "", importDetail: String? = null,
) {
    var choosingLanguage by rememberSaveable { mutableStateOf(false) }
    var devExpanded by rememberSaveable { mutableStateOf(testMode != UnlockTestMode.NORMAL) }
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
                    sourceName?.let { Text(it, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
                    if (sourceCount != null && playableCount != null) {
                        Text(
                            stringResource(R.string.settings_library_counts, sourceCount, playableCount),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (busy && importProgress.isNotBlank()) Text(importProgress, style = MaterialTheme.typography.bodySmall)
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
                        com.mylittleblover.mkwplayer.ui.SmoothProgressIndicator(0.5f, Modifier.fillMaxWidth())
                        Text(stringResource(R.string.settings_importing), style = MaterialTheme.typography.bodySmall)
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
                    Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.map_art_notice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
