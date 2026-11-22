package com.mylittleblover.mkwplayer.ui.interactive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mylittleblover.mkwplayer.R
import com.mylittleblover.mkwplayer.routes.LocalizedNames
import com.mylittleblover.mkwplayer.ui.kit.MkwDialog
import com.mylittleblover.mkwplayer.ui.kit.MkwGhostButton
import com.mylittleblover.mkwplayer.ui.kit.MkwKitTokens
import com.mylittleblover.mkwplayer.ui.kit.MkwOptionRow
import com.mylittleblover.mkwplayer.ui.kit.MkwSecondaryButton
import com.mylittleblover.mkwplayer.ui.kit.MkwTextField
import org.json.JSONObject

/** Opening/searching the picker never opens an audio source. Only the explicit play action does. */
@Composable
internal fun InteractiveCoursePicker(
    names: LocalizedNames?,
    specialUnlocked: Boolean,
    busy: Boolean,
    available: (String) -> Boolean,
    onPlay: (String) -> Unit,
) {
    val assets = LocalContext.current.assets
    val language = LocalConfiguration.current.locales[0].toLanguageTag()
    val courses = remember(assets) {
        runCatching {
            assets.open("official_routes.json").bufferedReader().use { reader ->
                val root = JSONObject(reader.readText())
                val rows = root.getJSONArray("stages")
                val routes = root.getJSONArray("routes")
                val ordinary = mutableSetOf<String>()
                for (i in 0 until routes.length()) {
                    val route = routes.getJSONObject(i)
                    if (!route.getBoolean("requiresSpecialUnlock")) {
                        val stops = route.getJSONArray("stops")
                        for (j in 0 until stops.length()) ordinary += stops.getJSONObject(j).getString("stage")
                    }
                }
                (0 until rows.length()).map {
                    rows.getJSONObject(it).getString("id") to (rows.getJSONObject(it).getString("id") in ordinary)
                }
            }
        }.getOrDefault(emptyList())
    }
    var open by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    MkwSecondaryButton(
        text = stringResource(R.string.interactive_choose_course),
        onClick = { open = true },
        modifier = Modifier.fillMaxWidth(),
    )

    if (open) {
        MkwDialog(
            onDismissRequest = { open = false },
            title = stringResource(R.string.interactive_choose_course),
            confirmButton = {
                MkwGhostButton(
                    text = stringResource(R.string.interactive_close),
                    onClick = { open = false },
                )
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MkwTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(R.string.interactive_search_course),
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MkwKitTokens.Palette.inkSecondary) },
                    singleLine = true,
                )
                val filtered = courses.filter { (id, ordinary) ->
                    (ordinary || specialUnlocked) && (query.isBlank() || names?.matches(id, query) == true)
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(filtered, key = { it.first }) { (id, _) ->
                        val isAvailable = available(id)
                        MkwOptionRow(
                            headline = names?.name(id, language) ?: id,
                            supporting = if (!isAvailable) stringResource(R.string.interactive_audio_missing) else null,
                            enabled = !busy && isAvailable,
                            onClick = {
                                open = false
                                onPlay(id)
                            },
                        )
                    }
                    if (filtered.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.interactive_no_results),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MkwKitTokens.Palette.inkSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}
