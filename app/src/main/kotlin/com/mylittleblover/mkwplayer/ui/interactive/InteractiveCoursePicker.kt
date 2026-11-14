package com.mylittleblover.mkwplayer.ui.interactive

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mylittleblover.mkwplayer.R
import com.mylittleblover.mkwplayer.routes.LocalizedNames
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
                (0 until rows.length()).map { rows.getJSONObject(it).getString("id") to (rows.getJSONObject(it).getString("id") in ordinary) }
            }
        }.getOrDefault(emptyList())
    }
    var open by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.interactive_choose_course))
    }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(stringResource(R.string.interactive_choose_course)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
                    label = { Text(stringResource(R.string.interactive_search_course)) })
                val filtered = courses.filter { (id, ordinary) ->
                    (ordinary || specialUnlocked) && (query.isBlank() || names?.matches(id, query) == true)
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    items(filtered, key = { it.first }) { (id, _) ->
                        TextButton(onClick = { open = false; onPlay(id) }, enabled = !busy && available(id),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(names?.name(id, language) ?: id)
                                if (!available(id)) Text(stringResource(R.string.interactive_audio_missing), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    if (filtered.isEmpty()) item { Text(stringResource(R.string.interactive_no_results)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.interactive_close)) } },
    )
}
