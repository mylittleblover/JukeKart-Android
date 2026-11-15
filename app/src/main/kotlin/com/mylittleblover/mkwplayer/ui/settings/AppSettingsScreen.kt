package com.mylittleblover.mkwplayer.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
    sourceCount: Int? = null, playableCount: Int? = null, importProgress: String = "", importDetail: String? = null,
) {
    var choosingLanguage by rememberSaveable { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
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
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item("language-heading") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.app_language_title), style = MaterialTheme.typography.titleLarge)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.settings_language_hint), Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { choosingLanguage = true }) {
                            Text(stringResource(language.labelResource))
                            Icon(Icons.Filled.ArrowDropDown, null)
                        }
                    }
                }
            }
        }
        item("unlock-test") {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HorizontalDivider()
                Text(stringResource(R.string.settings_unlock_test_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.settings_unlock_test_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(when (testMode) {
                    UnlockTestMode.NORMAL -> R.string.settings_unlock_test_normal
                    UnlockTestMode.FORCE_UNLOCKED -> R.string.settings_unlock_test_unlocked
                    UnlockTestMode.FORCE_LOCKED -> R.string.settings_unlock_test_locked
                }), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(if (testUnlockVisible) R.string.settings_unlock_test_visible else R.string.settings_unlock_test_hidden), style = MaterialTheme.typography.bodyMedium)
                Column(Modifier.fillMaxWidth().selectableGroup()) {
                    listOf(UnlockTestMode.NORMAL to R.string.settings_unlock_test_restore,
                        UnlockTestMode.FORCE_UNLOCKED to R.string.settings_unlock_test_unlock,
                        UnlockTestMode.FORCE_LOCKED to R.string.settings_unlock_test_lock).forEach { (mode, label) ->
                        ListItem(headlineContent = { Text(stringResource(label)) },
                            trailingContent = { RadioButton(selected = mode == testMode, onClick = null) },
                            modifier = Modifier.selectable(selected = mode == testMode, role = Role.RadioButton,
                                onClick = { if (mode != testMode) onTestUnlock(mode) }).heightIn(min = 56.dp))
                    }
                }
            }
        }
        item("library") {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HorizontalDivider()
                Text(stringResource(R.string.settings_library), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.settings_library_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                sourceName?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                if (sourceCount != null && playableCount != null) Text(stringResource(R.string.settings_library_counts, sourceCount, playableCount),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (busy && importProgress.isNotBlank()) Text(importProgress, style = MaterialTheme.typography.bodySmall)
                importDetail?.let { detail ->
                    var expanded by rememberSaveable(detail) { mutableStateOf(false) }
                    TextButton(onClick = { expanded = !expanded }) { Text(stringResource(R.string.settings_import_details)) }
                    androidx.compose.animation.AnimatedVisibility(expanded) {
                        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.settings_importing)) }
                Button(onClick = onOpenPack, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_import_pack)) }
                OutlinedButton(onClick = onOpenFiles, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_open_files)) }
            }
        }
        item("about") {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HorizontalDivider()
                Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.app_name))
                Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.map_art_notice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
