package com.mylittleblover.mkwplayer.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mylittleblover.mkwplayer.BuildConfig
import com.mylittleblover.mkwplayer.R
import com.mylittleblover.mkwplayer.i18n.AppLanguage

@Composable
internal fun AppSettingsScreen(
    language: AppLanguage, onLanguage: (AppLanguage) -> Unit,
    busy: Boolean, sourceName: String?, onOpenPack: () -> Unit, onOpenFiles: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxSize().selectableGroup(), contentPadding = PaddingValues(vertical = 16.dp)) {
        item("language-heading") {
            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.app_language_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.settings_language_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
            }
        }
        items(AppLanguage.entries, key = { it.tag }) { choice ->
            ListItem(
                headlineContent = { Text(stringResource(choice.labelResource)) },
                trailingContent = { RadioButton(selected = language == choice, onClick = null) },
                modifier = Modifier.selectable(selected = language == choice, role = Role.RadioButton, onClick = { onLanguage(choice) }),
            )
        }
        item("library") {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HorizontalDivider()
                Text(stringResource(R.string.settings_library), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.settings_library_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                sourceName?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.settings_importing)) }
                Button(onClick = onOpenPack, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_import_pack)) }
                OutlinedButton(onClick = onOpenFiles, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_open_files)) }
            }
        }
        item("about") {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HorizontalDivider()
                Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.app_name))
                Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.map_art_notice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
