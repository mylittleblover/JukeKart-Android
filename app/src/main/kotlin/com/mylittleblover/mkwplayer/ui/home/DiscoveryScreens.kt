package com.mylittleblover.mkwplayer.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import kotlinx.coroutines.launch
import com.mylittleblover.mkwplayer.R
import com.mylittleblover.mkwplayer.library.BrowseCatalog
import com.mylittleblover.mkwplayer.routes.OfficialRoute
import com.mylittleblover.mkwplayer.routes.OfficialRouteKind
import com.mylittleblover.mkwplayer.ui.art.StageArt

@Composable
private fun CourseTile(code: String, name: String, favorite: Boolean, map: ImageBitmap?,
    anchors: com.mylittleblover.mkwplayer.library.MapAnchors.Catalog?, onOpen: () -> Unit, onFavorite: () -> Unit) {
    Column(Modifier.width(165.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow).clickable(onClick = onOpen)) {
            com.mylittleblover.mkwplayer.ui.art.CoursePlaceholder(code, Modifier.fillMaxSize())
            FilledIconButton(onClick = onFavorite, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .94f),
                    contentColor = MaterialTheme.colorScheme.primary)) {
                Icon(if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    stringResource(if (favorite) R.string.discovery_favorite_remove else R.string.discovery_favorite_add), Modifier.size(20.dp))
            }
        }
        Column(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 2.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.discovery_course_music), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun DiscoveryHome(catalog: BrowseCatalog?, state: DiscoveryViewModel.State, language: String, unlocked: Boolean,
                           onOpen: (String) -> Unit, onFavorite: (String) -> Unit,
                           onImport: () -> Unit, onPlayer: () -> Unit, onToggle: () -> Unit, onMedia: () -> Unit,
                           session: com.mylittleblover.mkwplayer.player.PlayerViewModel.Ui, currentCode: String?) {
    val context = LocalContext.current
    val map by produceState(com.mylittleblover.mkwplayer.ui.art.MapArtworkCache.cached(), context) {
        value = com.mylittleblover.mkwplayer.ui.art.MapArtworkCache.load(context)
    }
    val anchors = remember(context) { runCatching {
        com.mylittleblover.mkwplayer.library.MapAnchors.parse(context.assets.open("map_anchors.json").bufferedReader().use { it.readText() })
    }.getOrNull() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.discovery_home_eyebrow), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.discovery_home_headline), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.discovery_home_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (state.favoritesError != null || state.dailyError != null || state.recentError != null) item {
            Text(stringResource(R.string.discovery_save_failed), color = MaterialTheme.colorScheme.error)
        }
        val visibleDaily = state.dailyIds.filter { it in catalog?.courses(unlocked).orEmpty() }
        if (catalog == null || visibleDaily.isEmpty()) item { Text(stringResource(R.string.discovery_empty)) }
        else item {
            val pager = rememberPagerState(pageCount = { visibleDaily.size })
            val scope = rememberCoroutineScope()
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HorizontalPager(pager, Modifier.fillMaxWidth().height(280.dp * androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f))) { index ->
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
                        StageArt.resolve(id)?.let { Image(painterResource(it), null,
                            Modifier.align(Alignment.TopEnd).padding(top = 34.dp, end = 12.dp).size(100.dp)) }
                        Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.SpaceBetween) {
                            Text("${stringResource(R.string.discovery_daily)} · ${index + 1} / ${visibleDaily.size}", color = Color.White, style = MaterialTheme.typography.labelMedium)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.discovery_home_today), color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Text(catalog.displayName(id, language) ?: id, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.discovery_home_daily_hint), color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.widthIn(max = 230.dp))
                                androidx.compose.material3.FilledTonalButton(onClick = { onOpen(id) }) { Text(stringResource(R.string.discovery_home_open)) }
                            }
                        }
                        Row(Modifier.align(Alignment.BottomEnd).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            val previousLabel = stringResource(R.string.discovery_previous_recommendation)
                            val nextLabel = stringResource(R.string.discovery_next_recommendation)
                            IconButton(onClick = { scope.launch { pager.animateScrollToPage((index - 1 + visibleDaily.size) % visibleDaily.size) } },
                                modifier = Modifier.semantics { contentDescription = previousLabel }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null, tint = Color.White) }
                            IconButton(onClick = { scope.launch { pager.animateScrollToPage((index + 1) % visibleDaily.size) } },
                                modifier = Modifier.semantics { contentDescription = nextLabel }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.White) }
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
        if (visibleDaily.isEmpty()) item { OutlinedButton(onClick = onImport, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.discovery_import)) } }
        if (session.canStop) {
            item { Text(stringResource(R.string.discovery_continue), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            item {
                Card(onClick = onPlayer, modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        com.mylittleblover.mkwplayer.ui.art.CoursePlaceholder(currentCode, Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)), showIcon = false)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(currentCode?.let { catalog?.displayName(it, language) } ?: session.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            val flow = session.flow
                            Text(flow?.let { com.mylittleblover.mkwplayer.ui.interactive.performanceStageText(it) }
                                ?: stringResource(if (session.canResume) R.string.discovery_paused else R.string.discovery_playing),
                                style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            val fraction = if (flow?.sourceFrame != null && flow.sourceFrames != null && flow.sourceFrames > 0)
                                (flow.sourceFrame.toDouble() / flow.sourceFrames).toFloat().coerceIn(0f, 1f) else session.progress.coerceIn(0f, 1f)
                            com.mylittleblover.mkwplayer.ui.SmoothProgressIndicator(fraction, modifier = Modifier.fillMaxWidth())
                        }
                        val label = stringResource(if (session.canPause) R.string.interactive_pause else R.string.interactive_resume)
                        FilledIconButton(onClick = onToggle, enabled = session.canPause || session.canResume,
                            modifier = Modifier.semantics { contentDescription = label }) {
                            Icon(if (session.canPause) Icons.Filled.Pause else Icons.Filled.PlayArrow, null)
                        }
                    }
                }
            }
        }
        val favorites = state.favorites.filter { it in catalog?.courses(unlocked).orEmpty() }
        item { Text(stringResource(R.string.discovery_favorites), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (favorites.isNotEmpty()) {
            item { LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(favorites, key = { it }) { id -> CourseTile(id, catalog?.displayName(id, language) ?: id, true, map, anchors, { onOpen(id) }, { onFavorite(id) }) }
            } }
        } else item {
            Card(onClick = onMedia, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(Icons.Outlined.FavoriteBorder, null)
                    Text(stringResource(R.string.discovery_favorites_empty), modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.discovery_media), color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        val recent = state.recentIds.filter { it in catalog?.courses(unlocked).orEmpty() }.take(5)
        if (recent.isNotEmpty()) {
            item { Text(stringResource(R.string.discovery_recent), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            items(recent, key = { "recent:$it" }) { id -> RecentCourseRow(id, catalog?.displayName(id, language) ?: id) { onOpen(id) } }
        }
    }
}

@Composable
internal fun DiscoveryMedia(catalog: BrowseCatalog?, unlocked: Boolean, language: String,
    category: Int, onCategory: (Int) -> Unit, query: String, onQuery: (String) -> Unit,
    recentIds: List<String>, onCourse: (String) -> Unit, onCup: (String) -> Unit) {
    val categoryStateHolder = rememberSaveableStateHolder()
    androidx.compose.animation.AnimatedContent(category, transitionSpec = {
        com.mylittleblover.mkwplayer.ui.AppMotion.page(if (targetState < 0) -1 else 1)
    }, label = "media-category") { shown ->
        categoryStateHolder.SaveableStateProvider(shown) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                DiscoveryMediaContent(catalog, unlocked, language, shown, onCategory, query, onQuery, recentIds, onCourse, onCup)
            }
        }
    }
}

@Composable
private fun DiscoveryMediaContent(catalog: BrowseCatalog?, unlocked: Boolean, language: String,
                            category: Int, onCategory: (Int) -> Unit, query: String, onQuery: (String) -> Unit,
                            recentIds: List<String>, onCourse: (String) -> Unit, onCup: (String) -> Unit) {
    androidx.activity.compose.BackHandler(enabled = category >= 0) { onCategory(-1) }
    val categories = listOf(R.string.discovery_gp, R.string.discovery_rally, R.string.discovery_vs,
        R.string.discovery_free, R.string.discovery_other, R.string.discovery_sounds)
    val routes = when (category) {
        0 -> catalog?.routes(OfficialRouteKind.GRAND_PRIX, unlocked, query).orEmpty()
        1 -> catalog?.routes(OfficialRouteKind.RALLY, unlocked, query).orEmpty()
        -1 -> if (query.isBlank()) emptyList() else catalog?.routes(OfficialRouteKind.GRAND_PRIX, unlocked, query).orEmpty() + catalog?.routes(OfficialRouteKind.RALLY, unlocked, query).orEmpty()
        else -> emptyList()
    }
    val courses = if (category == 2 || ((category == 0 || category == -1) && query.isNotBlank())) catalog?.searchCourses(query, unlocked).orEmpty() else emptyList()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { OutlinedTextField(query, onQuery, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.discovery_search)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) }, singleLine = true) }
        if (category < 0) {
            item { Text(stringResource(R.string.discovery_media), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
            for (row in 0..2) item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (column in 0..1) {
                        val index = row * 2 + column
                        Card(onClick = { onCategory(index) }, modifier = Modifier.weight(1f).heightIn(min = 112.dp),
                            shape = RoundedCornerShape(20.dp)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                val art = when (index) { 0 -> R.drawable.mode_grand_prix; 1 -> R.drawable.mode_knockout; 2 -> R.drawable.mode_vs; else -> null }
                                if (art != null) Image(painterResource(art), null, Modifier.align(Alignment.End).size(52.dp))
                                else Icon(when (index) { 3 -> Icons.Outlined.MusicNote; 4 -> Icons.Outlined.Album; else -> Icons.Outlined.GraphicEq },
                                    contentDescription = null, modifier = Modifier.align(Alignment.End).size(32.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(categories[index]), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onCategory(-1) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back)) }
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
        if (category < 0 && query.isBlank()) {
            item { Text(stringResource(R.string.discovery_course_music), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            items(catalog?.courses(unlocked).orEmpty(), key = { "browse:$it" }) { code ->
                RecentCourseRow(code, catalog?.displayName(code, language) ?: code) { onCourse(code) }
            }
        }
        if (query.isNotBlank() && routes.isEmpty() && courses.isEmpty()) item {
            Text(stringResource(R.string.discovery_search_empty), Modifier.padding(vertical = 16.dp))
        }
        val recent = if (category < 0 && query.isBlank()) recentIds.filter { it in catalog?.courses(unlocked).orEmpty() }.take(6) else emptyList()
        if (recent.isNotEmpty()) {
            item { Text(stringResource(R.string.discovery_recent), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            items(recent, key = { "recent:$it" }) { id -> RecentCourseRow(id, catalog?.displayName(id, language) ?: id) { onCourse(id) } }
        }
    }
}

@Composable
private fun RecentCourseRow(code: String, title: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            com.mylittleblover.mkwplayer.ui.art.CoursePlaceholder(code,
                Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)), showIcon = false)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.discovery_course_music), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RouteRow(route: OfficialRoute, language: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val cup = if (route.kind == OfficialRouteKind.GRAND_PRIX)
                com.mylittleblover.mkwplayer.ui.art.CupArt.all().firstOrNull { "${it.id}_cup" == route.id } else null
            cup?.let { Image(painterResource(it.drawable), null, Modifier.size(56.dp)) }
            Column(Modifier.weight(1f)) {
                Text(route.displayName(language), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.map_route_stops, route.stops.size), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
internal fun DiscoveryCup(route: OfficialRoute?, catalog: BrowseCatalog?, language: String,
    onMap: (String) -> Unit, onCourse: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (route == null) item { Text(stringResource(R.string.discovery_empty)) }
        else {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        val cup = com.mylittleblover.mkwplayer.ui.art.CupArt.all().firstOrNull { "${it.id}_cup" == route.id }
                        if (cup != null) Image(painterResource(cup.drawable), null, Modifier.size(104.dp))
                        else Icon(Icons.Outlined.GraphicEq, null, Modifier.size(64.dp))
                        Text(route.displayName(language), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.map_route_stops, route.stops.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = { onMap(route.id) }) { Text(stringResource(R.string.discovery_view_map)) }
                    }
                }
            }
            item { Text(stringResource(R.string.discovery_course_order), style = MaterialTheme.typography.titleLarge) }
            items(route.stops, key = { it.id }) { stop ->
                Card(onClick = { onCourse(stop.stageCode) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stop.ordinal.toString(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        StageArt.resolve(stop.stageCode)?.let { Image(painterResource(it), null, Modifier.size(56.dp)) }
                        Text(catalog?.displayName(stop.stageCode, language) ?: stop.stageCode, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    }
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
        item { OutlinedButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, contentDescription = null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.discovery_create_playlist)) } }
        if (state.playlistError != null) item { Text(stringResource(R.string.discovery_save_failed), color = MaterialTheme.colorScheme.error) }
        if (state.playlists.isEmpty()) item { Text(stringResource(R.string.discovery_no_saved)) }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(if (showAll) state.playlists else state.playlists.take(5), key = { "playlist:${it.id}" }) { playlist ->
                    Card(onClick = { onPlaylist(playlist.id) }, modifier = Modifier.width(164.dp)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.fillMaxWidth().height(112.dp), contentAlignment = Alignment.Center) {
                                val first = playlist.courseIds.firstOrNull { it in catalog?.courses(unlocked).orEmpty() }
                                val art = first?.let { StageArt.resolve(it) }
                                if (art != null) Image(painterResource(art), null, Modifier.size(90.dp))
                                else Icon(Icons.Outlined.MusicNote, null, Modifier.size(40.dp))
                            }
                            Text(playlist.name, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                            Text(playlist.courseIds.size.toString(), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        if (!showAll && state.playlists.size > 5) item { TextButton(onClick = { showAll = true }) { Text(stringResource(R.string.discovery_show_all)) } }
        item { Text(stringResource(R.string.discovery_my_routes), style = MaterialTheme.typography.titleLarge) }
        item { Text(stringResource(R.string.discovery_no_saved)) }
        item { Text(stringResource(R.string.discovery_gp), style = MaterialTheme.typography.titleLarge) }
        item { OfficialRouteStrip(routes.filter { it.kind == OfficialRouteKind.GRAND_PRIX }, language, onCup) }
        item { Text(stringResource(R.string.discovery_rally), style = MaterialTheme.typography.titleLarge) }
        item { OfficialRouteStrip(routes.filter { it.kind == OfficialRouteKind.RALLY }, language, onCup) }
    }
}

@Composable
private fun OfficialRouteStrip(routes: List<OfficialRoute>, language: String, onCup: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(routes, key = { it.id }) { route ->
            Card(onClick = { onCup(route.id) }, modifier = Modifier.width(164.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth().height(112.dp), contentAlignment = Alignment.Center) {
                        val cup = if (route.kind == OfficialRouteKind.GRAND_PRIX)
                            com.mylittleblover.mkwplayer.ui.art.CupArt.all().firstOrNull { "${it.id}_cup" == route.id } else null
                        if (cup != null) Image(painterResource(cup.drawable), null, Modifier.size(90.dp))
                        else Image(painterResource(R.drawable.mode_knockout), null, Modifier.size(80.dp))
                    }
                    Text(route.displayName(language), style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    Text(stringResource(R.string.map_route_stops, route.stops.size), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiscoveryPlaylist(playlist: com.mylittleblover.mkwplayer.library.UserPlaylist?, catalog: BrowseCatalog?,
    unlocked: Boolean, language: String, error: Boolean, onAdd: (String) -> Unit, onRemove: (Int) -> Unit,
    onRename: (String) -> Unit, onMove: (Int, Int) -> Unit, onDelete: () -> Unit, onCourse: (String) -> Unit) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var newName by rememberSaveable { mutableStateOf("") }
    val visible = catalog?.courses(unlocked).orEmpty()
    var courseQuery by rememberSaveable { mutableStateOf("") }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    if (choosing) ModalBottomSheet(sheetState = sheet, onDismissRequest = { choosing = false; courseQuery = "" }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.discovery_choose_course), style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(courseQuery, { courseQuery = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.discovery_search)) }, singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) })
            val matches = catalog?.searchCourses(courseQuery, unlocked).orEmpty()
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (matches.isEmpty()) item { Text(stringResource(R.string.discovery_empty), Modifier.padding(vertical = 16.dp)) }
                items(matches, key = { it }) { code ->
                    ListItem(headlineContent = { Text(catalog?.displayName(code, language) ?: code) },
                        trailingContent = { IconButton(onClick = { onAdd(code); scope.launch { sheet.hide(); choosing = false; courseQuery = "" } }) {
                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.discovery_add_course))
                        } })
                }
            }
        }
    }
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
                IconButton(onClick = { newName = playlist.name; renaming = true }) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.discovery_rename)) }
            } }
            if (error) item { Text(stringResource(R.string.discovery_save_failed), color = MaterialTheme.colorScheme.error) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { choosing = true }) { Icon(Icons.Filled.Add, contentDescription = null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.discovery_add_course)) }
                OutlinedIconButton(onClick = { deleting = true }) { Icon(Icons.Filled.DeleteOutline, contentDescription = stringResource(R.string.discovery_delete_playlist)) }
            } }
            if (playlist.courseIds.isEmpty()) item { Text(stringResource(R.string.discovery_playlist_empty)) }
            items(playlist.courseIds.size) { index ->
                val code = playlist.courseIds[index]
                val title = if (code in visible) catalog?.displayName(code, language) ?: code else stringResource(R.string.discovery_empty)
                Card(Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth().padding(8.dp)) {
                    TextButton(onClick = { onCourse(code) }, enabled = code in visible, modifier = Modifier.fillMaxWidth()) {
                        Text(title)
                    }
                    Row(Modifier.align(Alignment.End)) {
                        IconButton(onClick = { onMove(index, index - 1) }, enabled = index > 0) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "${stringResource(R.string.discovery_move_up)}: $title, ${index + 1}" ) }
                        IconButton(onClick = { onMove(index, index + 1) }, enabled = index + 1 < playlist.courseIds.size) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "${stringResource(R.string.discovery_move_down)}: $title, ${index + 1}" ) }
                        IconButton(onClick = { onRemove(index) }) { Icon(Icons.Filled.RemoveCircleOutline, contentDescription = "${stringResource(R.string.discovery_remove)}: $title, ${index + 1}" ) }
                    }
                } }
            }
        }
    }
}
