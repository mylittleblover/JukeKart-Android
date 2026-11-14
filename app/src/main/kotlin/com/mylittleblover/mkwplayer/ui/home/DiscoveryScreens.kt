package com.mylittleblover.mkwplayer.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mylittleblover.mkwplayer.R
import com.mylittleblover.mkwplayer.library.BrowseCatalog
import com.mylittleblover.mkwplayer.routes.OfficialRoute
import com.mylittleblover.mkwplayer.routes.OfficialRouteKind
import com.mylittleblover.mkwplayer.ui.art.StageArt

@Composable
private fun CourseTile(code: String, name: String, favorite: Boolean, onOpen: () -> Unit, onFavorite: () -> Unit) {
    Card(onClick = onOpen, modifier = Modifier.width(310.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                StageArt.resolve(code)?.let { Image(painterResource(it), null, Modifier.fillMaxSize()) }
            }
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            Text(stringResource(R.string.discovery_placeholder), style = MaterialTheme.typography.labelSmall)
            TextButton(onClick = onFavorite) { Text(stringResource(if (favorite) R.string.discovery_favorite_remove else R.string.discovery_favorite_add)) }
        }
    }
}

@Composable
internal fun DiscoveryHome(catalog: BrowseCatalog?, state: DiscoveryViewModel.State, language: String, unlocked: Boolean,
                           onOpen: (String) -> Unit, onFavorite: (String) -> Unit,
                           onImport: () -> Unit, onPlayer: () -> Unit, hasPlayer: Boolean) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(stringResource(R.string.discovery_daily), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        if (catalog == null || state.dailyIds.none { it in catalog.courses(unlocked) }) item { Text(stringResource(R.string.discovery_empty)) }
        else item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.dailyIds.filter { it in catalog.courses(unlocked) }, key = { it }) { id ->
                    CourseTile(id, catalog.displayName(id, language) ?: id, id in state.favorites, { onOpen(id) }, { onFavorite(id) })
                }
            }
        }
        item { OutlinedButton(onClick = onImport, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.discovery_import)) } }
        if (hasPlayer) item { Button(onClick = onPlayer, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.discovery_open_player)) } }
        val favorites = state.favorites.filter { it in catalog?.courses(unlocked).orEmpty() }
        if (favorites.isNotEmpty()) {
            item { Text(stringResource(R.string.discovery_favorites), style = MaterialTheme.typography.titleLarge) }
            item { LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(favorites, key = { it }) { id -> CourseTile(id, catalog?.displayName(id, language) ?: id, true, { onOpen(id) }, { onFavorite(id) }) }
            } }
        }
    }
}

@Composable
internal fun DiscoveryMedia(catalog: BrowseCatalog?, unlocked: Boolean, language: String,
                            onCourse: (String) -> Unit, onCup: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableIntStateOf(0) }
    val categories = listOf(R.string.discovery_gp, R.string.discovery_rally, R.string.discovery_vs,
        R.string.discovery_free, R.string.discovery_other, R.string.discovery_sounds)
    val routes = when (category) {
        0 -> catalog?.routes(OfficialRouteKind.GRAND_PRIX, unlocked, query).orEmpty()
        1 -> catalog?.routes(OfficialRouteKind.RALLY, unlocked, query).orEmpty()
        else -> emptyList()
    }
    val courses = if (category == 0 && query.isNotBlank()) catalog?.searchCourses(query, unlocked).orEmpty() else emptyList()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.discovery_search)) }, singleLine = true) }
        item { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories.size) { i -> FilterChip(selected = category == i, onClick = { category = i }, label = { Text(stringResource(categories[i])) }) }
        } }
        items(routes, key = { it.id }) { route -> RouteRow(route, language) { onCup(route.id) } }
        items(courses, key = { "course:$it" }) { code ->
            Card(onClick = { onCourse(code) }, modifier = Modifier.fillMaxWidth()) {
                Text(catalog?.displayName(code, language) ?: code, Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
            }
        }
        if (routes.isEmpty() && courses.isEmpty()) item { Text(stringResource(R.string.discovery_empty)) }
    }
}

@Composable
private fun RouteRow(route: OfficialRoute, language: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text(route.displayName(language), style = MaterialTheme.typography.titleMedium)
            Text("${route.stops.size}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun DiscoveryCup(route: OfficialRoute?, catalog: BrowseCatalog?, language: String, onCourse: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (route == null) item { Text(stringResource(R.string.discovery_empty)) }
        else {
            item { Text(route.displayName(language), style = MaterialTheme.typography.headlineSmall) }
            items(route.stops, key = { it.id }) { stop ->
                Card(onClick = { onCourse(stop.stageCode) }, modifier = Modifier.fillMaxWidth()) {
                    Text("${stop.ordinal}. ${catalog?.displayName(stop.stageCode, language) ?: stop.stageCode}", Modifier.padding(18.dp))
                }
            }
        }
    }
}

@Composable
internal fun DiscoveryPlaylists(catalog: BrowseCatalog?, unlocked: Boolean, language: String,
    state: DiscoveryViewModel.State, onCreate: (String) -> Unit,
    onPlaylist: (String) -> Unit, onCup: (String) -> Unit) {
    val routes = catalog?.routes(OfficialRouteKind.GRAND_PRIX, unlocked).orEmpty() + catalog?.routes(OfficialRouteKind.RALLY, unlocked).orEmpty()
    var creating by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var showAll by rememberSaveable { mutableStateOf(false) }
    if (creating) AlertDialog(onDismissRequest = { creating = false },
        title = { Text(stringResource(R.string.discovery_create_playlist)) },
        text = { OutlinedTextField(name, { name = it.take(120) }, label = { Text(stringResource(R.string.discovery_playlist_name)) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onCreate(name); name = ""; creating = false }, enabled = name.isNotBlank()) { Text(stringResource(R.string.discovery_create)) } },
        dismissButton = { TextButton(onClick = { creating = false }) { Text(stringResource(R.string.discovery_cancel)) } })
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(R.string.discovery_ordinary_lists), style = MaterialTheme.typography.titleLarge) }
        item { OutlinedButton(onClick = { creating = true }) { Text(stringResource(R.string.discovery_create_playlist)) } }
        if (state.playlistError != null) item { Text(stringResource(R.string.discovery_save_failed), color = MaterialTheme.colorScheme.error) }
        if (state.playlists.isEmpty()) item { Text(stringResource(R.string.discovery_no_saved)) }
        items(if (showAll) state.playlists else state.playlists.take(5), key = { "playlist:${it.id}" }) { playlist ->
            Card(onClick = { onPlaylist(playlist.id) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) { Text(playlist.name, style = MaterialTheme.typography.titleMedium); Text(playlist.courseIds.size.toString()) }
            }
        }
        if (!showAll && state.playlists.size > 5) item { TextButton(onClick = { showAll = true }) { Text(stringResource(R.string.discovery_show_all)) } }
        item { Text(stringResource(R.string.discovery_my_routes), style = MaterialTheme.typography.titleLarge) }
        item { Text(stringResource(R.string.discovery_no_saved)) }
        item { Text(stringResource(R.string.discovery_gp), style = MaterialTheme.typography.titleLarge) }
        items(routes.filter { it.kind == OfficialRouteKind.GRAND_PRIX }, key = { it.id }) { route -> RouteRow(route, language) { onCup(route.id) } }
        item { Text(stringResource(R.string.discovery_rally), style = MaterialTheme.typography.titleLarge) }
        items(routes.filter { it.kind == OfficialRouteKind.RALLY }, key = { it.id }) { route -> RouteRow(route, language) { onCup(route.id) } }
    }
}

@Composable
internal fun DiscoveryPlaylist(playlist: com.mylittleblover.mkwplayer.library.UserPlaylist?, catalog: BrowseCatalog?,
    unlocked: Boolean, language: String, error: Boolean, onAdd: (String) -> Unit, onRemove: (Int) -> Unit,
    onRename: (String) -> Unit, onMove: (Int, Int) -> Unit, onDelete: () -> Unit, onCourse: (String) -> Unit) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var newName by rememberSaveable { mutableStateOf("") }
    val visible = catalog?.courses(unlocked).orEmpty()
    if (choosing) AlertDialog(onDismissRequest = { choosing = false },
        title = { Text(stringResource(R.string.discovery_choose_course)) },
        text = { LazyColumn(Modifier.heightIn(max = 400.dp)) {
            items(visible, key = { it }) { code -> TextButton(onClick = { onAdd(code); choosing = false }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(catalog?.displayName(code, language) ?: code)
            } }
        } },
        confirmButton = { TextButton(onClick = { choosing = false }) { Text(stringResource(R.string.discovery_cancel)) } })
    if (renaming) AlertDialog(onDismissRequest = { renaming = false },
        title = { Text(stringResource(R.string.discovery_rename)) },
        text = { OutlinedTextField(newName, { newName = it.take(120) }, label = { Text(stringResource(R.string.discovery_playlist_name)) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onRename(newName); renaming = false }, enabled = newName.isNotBlank()) { Text(stringResource(R.string.discovery_rename)) } },
        dismissButton = { TextButton(onClick = { renaming = false }) { Text(stringResource(R.string.discovery_cancel)) } })
    if (deleting) AlertDialog(onDismissRequest = { deleting = false },
        title = { Text(stringResource(R.string.discovery_delete_playlist)) }, text = { Text(playlist?.name.orEmpty()) },
        confirmButton = { TextButton(onClick = { deleting = false; onDelete() }) { Text(stringResource(R.string.discovery_delete_playlist)) } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.discovery_cancel)) } })
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (playlist == null) item { Text(stringResource(R.string.discovery_empty)) }
        else {
            item { Row(verticalAlignment = Alignment.CenterVertically) {
                Text(playlist.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = { newName = playlist.name; renaming = true }) { Text(stringResource(R.string.discovery_rename)) }
            } }
            if (error) item { Text(stringResource(R.string.discovery_save_failed), color = MaterialTheme.colorScheme.error) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { choosing = true }) { Text(stringResource(R.string.discovery_add_course)) }
                OutlinedButton(onClick = { deleting = true }) { Text(stringResource(R.string.discovery_delete_playlist)) }
            } }
            if (playlist.courseIds.isEmpty()) item { Text(stringResource(R.string.discovery_playlist_empty)) }
            items(playlist.courseIds.size) { index ->
                val code = playlist.courseIds[index]
                Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onCourse(code) }, enabled = code in visible, modifier = Modifier.weight(1f)) {
                        Text(if (code in visible) catalog?.displayName(code, language) ?: code else stringResource(R.string.discovery_empty))
                    }
                    Column {
                        TextButton(onClick = { onMove(index, index - 1) }, enabled = index > 0) { Text(stringResource(R.string.discovery_move_up)) }
                        TextButton(onClick = { onMove(index, index + 1) }, enabled = index + 1 < playlist.courseIds.size) { Text(stringResource(R.string.discovery_move_down)) }
                        TextButton(onClick = { onRemove(index) }) { Text(stringResource(R.string.discovery_remove)) }
                    }
                } }
            }
        }
    }
}
