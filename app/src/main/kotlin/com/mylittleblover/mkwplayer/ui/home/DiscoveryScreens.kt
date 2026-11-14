package com.mylittleblover.mkwplayer.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
private fun CourseTile(code: String, name: String, favorite: Boolean, map: ImageBitmap?,
    anchors: com.mylittleblover.mkwplayer.library.MapAnchors.Catalog?, onOpen: () -> Unit, onFavorite: () -> Unit) {
    Card(onClick = onOpen, modifier = Modifier.width(310.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                val anchor = anchors?.stageTolerant(code)
                if (map != null && anchor != null) Canvas(Modifier.fillMaxSize()) {
                    val cropW = (map.width * .24f).toInt().coerceAtLeast(1)
                    val cropH = (map.height * .18f).toInt().coerceAtLeast(1)
                    val left = (anchor.u * map.width - cropW / 2).toInt().coerceIn(0, map.width - cropW)
                    val top = (anchor.v * map.height - cropH / 2).toInt().coerceIn(0, map.height - cropH)
                    drawImage(map, srcOffset = IntOffset(left, top), srcSize = IntSize(cropW, cropH),
                        dstSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)))
                }
                StageArt.resolve(code)?.let { Image(painterResource(it), null, Modifier.size(92.dp)) }
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
    val context = LocalContext.current
    val map by produceState<ImageBitmap?>(null, context) {
        value = withContext(Dispatchers.IO) {
            android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.world_map)?.asImageBitmap()
        }
    }
    val anchors = remember(context) { runCatching {
        com.mylittleblover.mkwplayer.library.MapAnchors.parse(context.assets.open("map_anchors.json").bufferedReader().use { it.readText() })
    }.getOrNull() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.discovery_home_eyebrow), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.discovery_home_headline), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.discovery_home_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (state.favoritesError != null || state.dailyError != null) item {
            Text(stringResource(R.string.discovery_save_failed), color = MaterialTheme.colorScheme.error)
        }
        val visibleDaily = state.dailyIds.filter { it in catalog?.courses(unlocked).orEmpty() }
        if (catalog == null || visibleDaily.isEmpty()) item { Text(stringResource(R.string.discovery_empty)) }
        else item {
            val pager = rememberPagerState(pageCount = { visibleDaily.size })
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HorizontalPager(pager, Modifier.fillMaxWidth().height(264.dp)) { index ->
                    val id = visibleDaily[index]
                    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp))) {
                        val anchor = anchors?.stageTolerant(id)
                        if (map != null && anchor != null) {
                            val image = map!!
                            Canvas(Modifier.fillMaxSize()) {
                                val cropW = (image.width * .24f).toInt().coerceAtLeast(1)
                                val cropH = (image.height * .18f).toInt().coerceAtLeast(1)
                                val left = (anchor.u * image.width - cropW / 2).toInt().coerceIn(0, image.width - cropW)
                                val top = (anchor.v * image.height - cropH / 2).toInt().coerceIn(0, image.height - cropH)
                                drawImage(image, srcOffset = IntOffset(left, top), srcSize = IntSize(cropW, cropH),
                                    dstSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)))
                            }
                        }
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x33000000), Color(0xCC000000)))))
                        Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.SpaceBetween) {
                            Text("${stringResource(R.string.discovery_daily)} · ${index + 1} / ${visibleDaily.size}", color = Color.White, style = MaterialTheme.typography.labelMedium)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.discovery_home_today), color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Text(catalog.displayName(id, language) ?: id, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.discovery_home_daily_hint), color = Color.White, style = MaterialTheme.typography.bodySmall)
                                androidx.compose.material3.FilledTonalButton(onClick = { onOpen(id) }) { Text(stringResource(R.string.discovery_home_open)) }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    repeat(visibleDaily.size) { index ->
                        Box(Modifier.padding(horizontal = 2.dp).width(if (index == pager.currentPage) 18.dp else 6.dp).height(5.dp)
                            .clip(RoundedCornerShape(50)).background(if (index == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
                    }
                }
            }
        }
        item { OutlinedButton(onClick = onImport, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.discovery_import)) } }
        if (hasPlayer) item { Button(onClick = onPlayer, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.discovery_open_player)) } }
        val favorites = state.favorites.filter { it in catalog?.courses(unlocked).orEmpty() }
        if (favorites.isNotEmpty()) {
            item { Text(stringResource(R.string.discovery_favorites), style = MaterialTheme.typography.titleLarge) }
            item { LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(favorites, key = { it }) { id -> CourseTile(id, catalog?.displayName(id, language) ?: id, true, map, anchors, { onOpen(id) }, { onFavorite(id) }) }
            } }
        }
    }
}

@Composable
internal fun DiscoveryMedia(catalog: BrowseCatalog?, unlocked: Boolean, language: String,
                            onCourse: (String) -> Unit, onCup: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableIntStateOf(-1) }
    androidx.activity.compose.BackHandler(enabled = category >= 0) { category = -1 }
    val categories = listOf(R.string.discovery_gp, R.string.discovery_rally, R.string.discovery_vs,
        R.string.discovery_free, R.string.discovery_other, R.string.discovery_sounds)
    val routes = when (category) {
        0 -> catalog?.routes(OfficialRouteKind.GRAND_PRIX, unlocked, query).orEmpty()
        1 -> catalog?.routes(OfficialRouteKind.RALLY, unlocked, query).orEmpty()
        -1 -> if (query.isBlank()) emptyList() else catalog?.routes(OfficialRouteKind.GRAND_PRIX, unlocked, query).orEmpty() + catalog?.routes(OfficialRouteKind.RALLY, unlocked, query).orEmpty()
        else -> emptyList()
    }
    val courses = if ((category == 0 || category == -1) && query.isNotBlank()) catalog?.searchCourses(query, unlocked).orEmpty() else emptyList()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.discovery_search)) }, singleLine = true) }
        if (category < 0) {
            item { Text(stringResource(R.string.discovery_media), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
            for (row in 0..2) item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (column in 0..1) {
                        val index = row * 2 + column
                        Card(onClick = { category = index }, modifier = Modifier.weight(1f).height(112.dp),
                            shape = RoundedCornerShape(20.dp)) {
                            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomStart) {
                                val art = when (index) { 0 -> R.drawable.mode_grand_prix; 1 -> R.drawable.mode_knockout; 2 -> R.drawable.mode_vs; else -> null }
                                art?.let { Image(painterResource(it), null, Modifier.align(Alignment.TopEnd).size(52.dp)) }
                                Text(stringResource(categories[index]), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { category = -1 }) { Text(stringResource(R.string.common_back)) }
                Text(stringResource(categories[category]), style = MaterialTheme.typography.headlineSmall)
            }
        }
        items(routes, key = { it.id }) { route -> RouteRow(route, language) { onCup(route.id) } }
        items(courses, key = { "course:$it" }) { code ->
            Card(onClick = { onCourse(code) }, modifier = Modifier.fillMaxWidth()) {
                Text(catalog?.displayName(code, language) ?: code, Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
            }
        }
        if (category >= 0 && routes.isEmpty() && courses.isEmpty()) item { Text(stringResource(R.string.discovery_empty)) }
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
