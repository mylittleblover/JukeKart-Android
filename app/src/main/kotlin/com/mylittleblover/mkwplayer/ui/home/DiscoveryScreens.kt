package com.mylittleblover.mkwplayer.ui.home

import com.mylittleblover.mkwplayer.ui.art.PackArtwork
import com.mylittleblover.mkwplayer.ui.art.ResourceArtwork
import com.mylittleblover.mkwplayer.ui.art.CourseCover
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
private fun CourseTile(
    code: String,
    name: String,
    favorite: Boolean,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    packDirectory: java.io.File? = null,
    artworkName: String? = null,
) {
    val tileInteraction = remember { MutableInteractionSource() }
    val pressScale = com.mylittleblover.mkwplayer.ui.rememberPressScale(tileInteraction, pressedScale = 0.97f)
    Column(
        Modifier.width(160.dp).graphicsLayer { scaleX = pressScale; scaleY = pressScale },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable(
                    interactionSource = tileInteraction,
                    indication = LocalIndication.current,
                    onClick = onOpen,
                ),
        ) {
            com.mylittleblover.mkwplayer.ui.art.CourseCover(
                code = code,
                modifier = Modifier.fillMaxSize(),
                packDirectory = packDirectory,
                artworkName = artworkName,
            )
            FilledIconButton(
                onClick = onFavorite,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(44.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .94f),
                    contentColor = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Icon(
                    if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    stringResource(if (favorite) R.string.discovery_favorite_remove else R.string.discovery_favorite_add),
                    Modifier.size(20.dp),
                )
            }
        }
        Column(
            Modifier.fillMaxWidth().clickable(
                interactionSource = tileInteraction,
                indication = null,
                onClick = onOpen,
            ).padding(horizontal = 2.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
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
                           session: com.mylittleblover.mkwplayer.player.PlayerViewModel.Ui, currentCode: String?,
                           packDirectory: java.io.File? = null, artworkNames: List<String> = emptyList(),
                           courseArtwork: Map<String, String> = emptyMap(),
                           onQuickPlay: (String) -> Unit = {}) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxWidth().height(236.dp * androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceIn(1f, 1.15f)),
                    pageSpacing = 12.dp,
                ) { index ->
                    val id = visibleDaily[index]
                    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp))) {
                        CourseCover(
                            code = id,
                            modifier = Modifier.fillMaxSize(),
                            packDirectory = packDirectory,
                            artworkName = courseArtwork[id] ?: courseArtwork[id.removeSuffix("_00")],
                        )
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x33000000), Color(0xCC000000)))))
                        StageArt.resolve(id)?.let { ResourceArtwork(it, Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 12.dp).size(88.dp)) }
                        Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                "${stringResource(R.string.discovery_home_today)} · ${index + 1} / ${visibleDaily.size}",
                                color = Color.White.copy(alpha = .92f),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(catalog.displayName(id, language) ?: id, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(stringResource(R.string.discovery_home_daily_hint), color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 240.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    androidx.compose.material3.FilledTonalButton(onClick = { onOpen(id) }) { Text(stringResource(R.string.discovery_home_open)) }
                                    FilledIconButton(
                                        onClick = { onQuickPlay(id) },
                                        modifier = Modifier.size(40.dp),
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = Color.White.copy(alpha = 0.22f),
                                            contentColor = Color.White,
                                        ),
                                    ) {
                                        Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.discovery_start))
                                    }
                                }
                            }
                        }
                        Row(Modifier.align(Alignment.BottomEnd).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
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
                        PackArtwork.Thumbnail(packDirectory, artworkNames, Modifier.size(72.dp).clip(RoundedCornerShape(16.dp))) {
                            if (currentCode != null) {
                                com.mylittleblover.mkwplayer.ui.art.CourseCover(currentCode, Modifier.fillMaxSize(), showIcon = false)
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize()
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Outlined.MusicNote, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(30.dp))
                                }
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(currentCode?.let { catalog?.displayName(it, language) } ?: session.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val flow = session.flow
                            val subtitle = flow?.let { com.mylittleblover.mkwplayer.ui.interactive.performanceStageText(it) }
                                ?: session.linearTrack?.subtitle?.takeIf { it.isNotBlank() }?.let { sub ->
                                    "$sub · ${stringResource(if (session.canResume) R.string.discovery_paused else R.string.discovery_playing)}"
                                }
                                ?: stringResource(if (session.canResume) R.string.discovery_paused else R.string.discovery_playing)
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        val recent = state.recentIds.filter { it in catalog?.courses(unlocked).orEmpty() }.take(10)
        if (favorites.isEmpty() && recent.isNotEmpty()) {
            item { Text(stringResource(R.string.discovery_recent), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(recent, key = { "recent:$it" }) { id ->
                        CourseTile(
                            code = id,
                            name = catalog?.displayName(id, language) ?: id,
                            favorite = id in state.favorites,
                            onOpen = { onOpen(id) },
                            onFavorite = { onFavorite(id) },
                            packDirectory = packDirectory,
                            artworkName = courseArtwork[id] ?: courseArtwork[id.removeSuffix("_00")],
                        )
                    }
                }
            }
            item { Text(stringResource(R.string.discovery_favorites), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            item {
                Card(onClick = onMedia, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Icon(Icons.Outlined.FavoriteBorder, null)
                        Text(stringResource(R.string.discovery_favorites_empty), modifier = Modifier.weight(1f))
                        Text(stringResource(R.string.discovery_media), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        } else {
            item { Text(stringResource(R.string.discovery_favorites), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            if (favorites.isNotEmpty()) {
                item { LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(favorites, key = { it }) { id ->
                        CourseTile(
                            code = id,
                            name = catalog?.displayName(id, language) ?: id,
                            favorite = true,
                            onOpen = { onOpen(id) },
                            onFavorite = { onFavorite(id) },
                            packDirectory = packDirectory,
                            artworkName = courseArtwork[id] ?: courseArtwork[id.removeSuffix("_00")],
                        )
                    }
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
            if (recent.isNotEmpty()) {
                item { Text(stringResource(R.string.discovery_recent), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(recent, key = { "recent:$it" }) { id ->
                            CourseTile(
                                code = id,
                                name = catalog?.displayName(id, language) ?: id,
                                favorite = id in state.favorites,
                                onOpen = { onOpen(id) },
                                onFavorite = { onFavorite(id) },
                                packDirectory = packDirectory,
                                artworkName = courseArtwork[id] ?: courseArtwork[id.removeSuffix("_00")],
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DiscoveryMedia(catalog: BrowseCatalog?, unlocked: Boolean, language: String,
    category: Int, onCategory: (Int) -> Unit, query: String, onQuery: (String) -> Unit,
    onCourse: (String) -> Unit, onCup: (String) -> Unit,
    jukebox: JukeboxCatalog.Catalog = JukeboxCatalog.Catalog(emptyList(), emptyMap()),
    extraCatalog: ExtraMusicCatalog.Catalog = ExtraMusicCatalog.Catalog(),
    packDirectory: java.io.File? = null,
    activeTrackKey: String? = null,
    onJukeboxGame: (String) -> Unit = {},
    onPlayJukeboxTrack: (JukeboxCatalog.Game, Int) -> Unit = { _, _ -> },
    onPlayAllJukebox: (Boolean) -> Unit = {},
    onPlayJukeboxNext: (JukeboxCatalog.TrackItem, JukeboxCatalog.Game) -> Unit = { _, _ -> },
    onJukeboxTrack: (String) -> Unit = {},
    onSfcCourse: (ExtraMusicCatalog.TwoStateTrack) -> Unit = {},
    onBattleTrack: (ExtraMusicCatalog.TwoStateTrack) -> Unit = {},
    onOpenTitle: () -> Unit = {},
    onPlayExtraQueue: (List<com.mylittleblover.mkwplayer.player.LinearQueue.Entry>, Int, com.mylittleblover.mkwplayer.player.LinearQueue.RepeatMode, Boolean) -> Unit = { _, _, _, _ -> },
    onAuditionExtra: (com.mylittleblover.mkwplayer.player.LinearQueue.Entry) -> Unit = {}) {
    val categoryStateHolder = rememberSaveableStateHolder()
    androidx.compose.animation.AnimatedContent(category, transitionSpec = {
        com.mylittleblover.mkwplayer.ui.AppMotion.page(if (targetState < 0) -1 else 1)
    }, label = "media-category") { shown ->
        categoryStateHolder.SaveableStateProvider(shown) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                DiscoveryMediaContent(catalog, unlocked, language, shown, onCategory, query, onQuery, onCourse, onCup,
                    jukebox = jukebox,
                    extraCatalog = extraCatalog,
                    packDirectory = packDirectory,
                    activeTrackKey = activeTrackKey,
                    onJukeboxGame = onJukeboxGame,
                    onPlayJukeboxTrack = onPlayJukeboxTrack,
                    onPlayAllJukebox = onPlayAllJukebox,
                    onPlayJukeboxNext = onPlayJukeboxNext,
                    onJukeboxTrack = onJukeboxTrack,
                    onSfcCourse = onSfcCourse,
                    onBattleTrack = onBattleTrack,
                    onOpenTitle = onOpenTitle,
                    onPlayExtraQueue = onPlayExtraQueue,
                    onAuditionExtra = onAuditionExtra)
            }
        }
    }
}

@Composable
private fun DiscoveryMediaContent(catalog: BrowseCatalog?, unlocked: Boolean, language: String,
                            category: Int, onCategory: (Int) -> Unit, query: String, onQuery: (String) -> Unit,
                            onCourse: (String) -> Unit, onCup: (String) -> Unit,
                            jukebox: JukeboxCatalog.Catalog = JukeboxCatalog.Catalog(emptyList(), emptyMap()),
                            extraCatalog: ExtraMusicCatalog.Catalog = ExtraMusicCatalog.Catalog(),
                            packDirectory: java.io.File? = null,
                            activeTrackKey: String? = null,
                            onJukeboxGame: (String) -> Unit = {},
                            onPlayJukeboxTrack: (JukeboxCatalog.Game, Int) -> Unit = { _, _ -> },
                            onPlayAllJukebox: (Boolean) -> Unit = {},
                            onPlayJukeboxNext: (JukeboxCatalog.TrackItem, JukeboxCatalog.Game) -> Unit = { _, _ -> },
                            onJukeboxTrack: (String) -> Unit = {},
                            onSfcCourse: (ExtraMusicCatalog.TwoStateTrack) -> Unit = {},
                            onBattleTrack: (ExtraMusicCatalog.TwoStateTrack) -> Unit = {},
                            onOpenTitle: () -> Unit = {},
                            onPlayExtraQueue: (List<com.mylittleblover.mkwplayer.player.LinearQueue.Entry>, Int, com.mylittleblover.mkwplayer.player.LinearQueue.RepeatMode, Boolean) -> Unit = { _, _, _, _ -> },
                            onAuditionExtra: (com.mylittleblover.mkwplayer.player.LinearQueue.Entry) -> Unit = {}) {
    androidx.activity.compose.BackHandler(enabled = category >= 0) { onCategory(-1) }
    val categories = listOf(
        R.string.discovery_gp,
        R.string.discovery_rally,
        R.string.discovery_vs,
        R.string.discovery_battle,
        R.string.discovery_free,
        R.string.discovery_other,
        R.string.discovery_sounds,
        R.string.discovery_nintendo_music,
    )
    val routes = when (category) {
        0 -> catalog?.routes(OfficialRouteKind.GRAND_PRIX, unlocked, query).orEmpty()
        1 -> catalog?.routes(OfficialRouteKind.RALLY, unlocked, query).orEmpty()
        -1 -> if (query.isBlank()) emptyList() else catalog?.routes(OfficialRouteKind.GRAND_PRIX, unlocked, query).orEmpty() + catalog?.routes(OfficialRouteKind.RALLY, unlocked, query).orEmpty()
        else -> emptyList()
    }
    val courses = if (category == 2 || ((category == 0 || category == -1) && query.isNotBlank())) catalog?.searchCourses(query, unlocked).orEmpty() else emptyList()
    val sfcCourses = remember(extraCatalog, query, category) {
        if (category == 2 || (category < 0 && query.isNotBlank())) extraCatalog.sfcCourses.filter { it.matches(query) } else emptyList()
    }
    val battleTracks = remember(extraCatalog, query, category) {
        if (category == 3 || (category < 0 && query.isNotBlank())) extraCatalog.battleTracks.filter { it.matches(query) } else emptyList()
    }
    val otherItems = remember(extraCatalog, query, category) {
        if (category == 5 || (category < 0 && query.isNotBlank())) extraCatalog.otherMusic.filter { it.matches(query) } else emptyList()
    }
    val shortItems = remember(extraCatalog, query, category) {
        if (category == 6 || (category < 0 && query.isNotBlank())) extraCatalog.shortSounds.filter { it.matches(query) } else emptyList()
    }
    val nmItems = remember(extraCatalog, query, category) {
        if (category == 7 || (category < 0 && query.isNotBlank())) extraCatalog.nintendoMusicSpecial.filter { it.matches(query) } else emptyList()
    }
    val jukeboxSearch = remember(jukebox, query) {
        if (query.isBlank()) JukeboxCatalog.SearchResult(jukebox.games, emptyList()) else jukebox.search(query)
    }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val addedNextText = stringResource(R.string.jukebox_added_next)
    val otherCategoryLabel = stringResource(R.string.discovery_other)
    val shortLabel = stringResource(R.string.discovery_sounds)
    val nmLabel = stringResource(R.string.discovery_nintendo_music)
    val listState = rememberSaveable(category, saver = LazyListState.Saver) { LazyListState() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (category >= 0) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onCategory(-1) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                        }
                        Text(
                            stringResource(categories[category]),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = onQuery,
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            stringResource(if (category < 0) R.string.discovery_search_all else R.string.discovery_search),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = if (query.isNotBlank()) {
                        {
                            IconButton(onClick = { onQuery("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.discovery_cancel))
                            }
                        }
                    } else null,
                    singleLine = true,
                )
            }
            if (category >= 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            }
            val categoryHeight = 114.dp * LocalDensity.current.fontScale.coerceIn(1f, 1.35f)
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (category < 0 && query.isBlank()) {
                    for (row in 0..3) item(key = "cat-row:$row") {
                        Row(
                            Modifier.fillMaxWidth().height(categoryHeight),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            for (column in 0..1) {
                                val index = row * 2 + column
                                val catInteraction = remember { MutableInteractionSource() }
                                val catScale = com.mylittleblover.mkwplayer.ui.rememberPressScale(catInteraction, pressedScale = 0.97f)
                                Card(
                                    onClick = { onCategory(index) },
                                    interactionSource = catInteraction,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .graphicsLayer { scaleX = catScale; scaleY = catScale },
                                    shape = RoundedCornerShape(18.dp),
                                ) {
                                    Column(
                                        Modifier.fillMaxSize().padding(12.dp),
                                        verticalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        when (index) {
                                            0 -> ResourceArtwork(R.drawable.mode_grand_prix, Modifier.align(Alignment.End).size(40.dp))
                                            1 -> ResourceArtwork(R.drawable.mode_knockout, Modifier.align(Alignment.End).size(40.dp))
                                            2 -> ResourceArtwork(R.drawable.mode_vs, Modifier.align(Alignment.End).size(40.dp))
                                            3 -> ResourceArtwork(R.drawable.mode_balloon, Modifier.align(Alignment.End).size(40.dp))
                                            4 -> Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.align(Alignment.End).size(40.dp),
                                            ) {
                                                ResourceArtwork(R.drawable.media_free_roam, Modifier.fillMaxSize().padding(7.dp))
                                            }
                                            5 -> ResourceArtwork(R.drawable.media_other_music, Modifier.align(Alignment.End).size(40.dp))
                                            6 -> ResourceArtwork(R.drawable.media_short_sfx, Modifier.align(Alignment.End).size(40.dp))
                                            else -> ResourceArtwork(R.drawable.media_nintendo_music, Modifier.align(Alignment.End).size(40.dp))
                                        }
                                        Box(
                                            Modifier.fillMaxWidth().weight(1f),
                                            contentAlignment = Alignment.BottomStart,
                                        ) {
                                            val categoryRes = categories[index]
                                            val rawCategoryLabel = stringResource(categoryRes)
                                            val categoryLabel = if (
                                                categoryRes == R.string.discovery_nintendo_music &&
                                                rawCategoryLabel.startsWith("Nintendo Music ")
                                            ) {
                                                "Nintendo Music\n" + rawCategoryLabel.removePrefix("Nintendo Music ")
                                            } else {
                                                rawCategoryLabel
                                            }
                                            Text(
                                                categoryLabel,
                                                style = MaterialTheme.typography.titleSmall,
                                                lineHeight = 18.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                items(routes, key = { it.id }) { route -> RouteRow(route, catalog, language) { onCup(route.id) } }
                if (courses.isNotEmpty() && (category == 2 || sfcCourses.isNotEmpty())) {
                    item(key = "vs-standard-header") {
                        Text(
                            stringResource(R.string.discovery_vs_standard),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                items(courses, key = { "course:$it" }) { code ->
                    ExtraSquareRow(
                        title = catalog?.displayName(code, language) ?: code,
                        subtitle = stringResource(R.string.discovery_interactive),
                        packDirectory = packDirectory,
                        artworkName = extraCatalog.courseArtwork[code],
                        fallbackCourseCode = code,
                        onClick = { onCourse(code) },
                    )
                }
                if (sfcCourses.isNotEmpty()) {
                    item(key = "vs-sfc-header") {
                        Text(
                            stringResource(R.string.discovery_vs_sfc),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                items(sfcCourses, key = { "sfc:${it.id}" }) { track ->
                    ExtraSquareRow(
                        title = track.localizedName(language),
                        subtitle = listOfNotNull(track.ostIndex?.let { "#%03d".format(java.util.Locale.ROOT, it) }, stringResource(R.string.discovery_interactive)).joinToString(" · "),
                        packDirectory = packDirectory,
                        artworkName = track.artwork,
                        onClick = { onSfcCourse(track) },
                    )
                }
                if (category == 3 && battleTracks.isNotEmpty()) {
                    item(key = "battle-summary") {
                        Surface(
                            Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Text(
                                stringResource(R.string.discovery_battle_summary, battleTracks.size),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
                items(battleTracks, key = { "battle:${it.id}" }) { track ->
                    ExtraSquareRow(
                        title = track.localizedName(language),
                        subtitle = listOfNotNull(track.ostIndex?.let { "#%03d".format(java.util.Locale.ROOT, it) }, stringResource(R.string.discovery_interactive)).joinToString(" · "),
                        packDirectory = packDirectory,
                        artworkName = track.artwork,
                        onClick = { onBattleTrack(track) },
                    )
                }
                if (otherItems.isNotEmpty()) {
                    val linearOther = otherItems.filterNot { it is ExtraMusicCatalog.OtherMusicItem.InteractiveTitle }
                    val linearEntries = linearOther.map { item -> extraOtherToQueueEntry(item, language, otherCategoryLabel) }
                    if (category == 5 && linearEntries.isNotEmpty()) {
                        item(key = "other-actions") {
                            ExtraQueueActionsRow(
                                count = otherItems.size,
                                onPlayAll = { onPlayExtraQueue(linearEntries, 0, com.mylittleblover.mkwplayer.player.LinearQueue.RepeatMode.LIST, false) },
                                onShuffle = { onPlayExtraQueue(linearEntries, 0, com.mylittleblover.mkwplayer.player.LinearQueue.RepeatMode.LIST, true) },
                            )
                        }
                    }
                    items(otherItems, key = { "other:${it.id}" }) { item ->
                        val sub = buildList {
                            item.ostIndex?.let { add("#%03d".format(java.util.Locale.ROOT, it)) }
                            if (item is ExtraMusicCatalog.OtherMusicItem.InteractiveTitle) {
                                add(stringResource(R.string.discovery_interactive))
                            } else {
                                add(stringResource(R.string.discovery_normal_playback))
                                if (item.durationMs > 0L) add(formatExtraDuration(item.durationMs))
                            }
                        }.joinToString(" · ")
                        ExtraSquareRow(
                            title = item.localizedName(language),
                            subtitle = sub,
                            packDirectory = packDirectory,
                            artworkName = item.artwork,
                            onClick = {
                                if (item is ExtraMusicCatalog.OtherMusicItem.InteractiveTitle) {
                                    onOpenTitle()
                                } else {
                                    val idx = linearOther.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
                                    val repeat = if (item is ExtraMusicCatalog.OtherMusicItem.LinearAsset && item.asset.loop) {
                                        com.mylittleblover.mkwplayer.player.LinearQueue.RepeatMode.SINGLE
                                    } else {
                                        com.mylittleblover.mkwplayer.player.LinearQueue.RepeatMode.LIST
                                    }
                                    onPlayExtraQueue(linearEntries, idx, repeat, false)
                                }
                            },
                        )
                    }
                }
                if (shortItems.isNotEmpty()) {
                    val shortEntries = shortItems.map { item -> extraSimpleToQueueEntry(item, language, shortLabel) }
                    if (category == 6) {
                        item(key = "short-actions") {
                            ExtraQueueActionsRow(
                                count = shortItems.size,
                                onPlayAll = { onPlayExtraQueue(shortEntries, 0, com.mylittleblover.mkwplayer.player.LinearQueue.RepeatMode.LIST, false) },
                                onShuffle = { onPlayExtraQueue(shortEntries, 0, com.mylittleblover.mkwplayer.player.LinearQueue.RepeatMode.LIST, true) },
                            )
                        }
                    }
                    itemsIndexed(shortItems, key = { _, item -> "short:${item.id}" }) { idx, item ->
                        val sub = buildList {
                            item.ostIndex?.let { add("#%03d".format(java.util.Locale.ROOT, it)) }
                            if (item.durationMs > 0L) add(formatExtraDuration(item.durationMs))
                        }.joinToString(" · ")
                        ExtraSquareRow(
                            title = item.localizedName(language),
                            subtitle = sub,
                            packDirectory = packDirectory,
                            artworkName = item.artwork,
                            onAction = { onAuditionExtra(shortEntries[idx]) },
                            actionContentDescription = stringResource(R.string.discovery_audition),
                            onClick = {
                                val repeat = if (item.asset.loop) {
                                    com.mylittleblover.mkwplayer.player.LinearQueue.RepeatMode.SINGLE
                                } else {
                                    com.mylittleblover.mkwplayer.player.LinearQueue.RepeatMode.LIST
                                }
                                onPlayExtraQueue(shortEntries, idx, repeat, false)
                            },
                        )
                    }
                }
            if (nmItems.isNotEmpty()) {
                val nmEntries = nmItems.map { item -> extraSimpleToQueueEntry(item, language, nmLabel) }
                if (category == 7) {
                    item(key = "nm-actions") {
                        ExtraQueueActionsRow(
                            count = nmItems.size,
                            onPlayAll = { onPlayExtraQueue(nmEntries, 0, com.mylittleblover.mkwplayer.player.LinearQueue.RepeatMode.LIST, false) },
                            onShuffle = { onPlayExtraQueue(nmEntries, 0, com.mylittleblover.mkwplayer.player.LinearQueue.RepeatMode.LIST, true) },
                        )
                    }
                }
                items(nmItems, key = { "nm:${it.id}" }) { item ->
                    val sub = buildList {
                        item.ostIndex?.let { add("#%03d".format(java.util.Locale.ROOT, it)) }
                        if (item.durationMs > 0L) add(formatExtraDuration(item.durationMs))
                    }.joinToString(" · ")
                    ExtraSquareRow(
                        title = item.localizedName(language),
                        subtitle = sub,
                        packDirectory = packDirectory,
                        artworkName = item.artwork,
                        onClick = {
                            val idx = nmItems.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
                            onPlayExtraQueue(nmEntries, idx, com.mylittleblover.mkwplayer.player.LinearQueue.RepeatMode.LIST, false)
                        },
                    )
                }
            }
            if (category == 4) {
                if (!jukebox.isEmpty) {
                    item(key = "jukebox-head") {
                        Column(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                stringResource(R.string.jukebox_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    stringResource(R.string.jukebox_games_summary, jukebox.games.size, jukebox.trackCount),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f),
                                )
                                FilledTonalButton(onClick = { onPlayAllJukebox(false) }) {
                                    Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.jukebox_play_all))
                                }
                                OutlinedButton(onClick = { onPlayAllJukebox(true) }) {
                                    Icon(Icons.Filled.Shuffle, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.jukebox_shuffle))
                                }
                            }
                        }
                    }
                }
                if (query.isBlank()) {
                    items(jukebox.games, key = { "game:${it.id}" }) { game ->
                        JukeboxGameRow(
                            game = game,
                            language = language,
                            packDirectory = packDirectory,
                            artworkName = extraCatalog.jukeboxGameArtwork[game.id],
                        ) { onJukeboxGame(game.id) }
                    }
                } else {
                    if (jukeboxSearch.games.isNotEmpty()) {
                        item(key = "jb-search-games-h") {
                            Text(stringResource(R.string.jukebox_games_section), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        items(jukeboxSearch.games, key = { "game-s:${it.id}" }) { game ->
                            JukeboxGameRow(
                                game = game,
                                language = language,
                                packDirectory = packDirectory,
                                artworkName = extraCatalog.jukeboxGameArtwork[game.id],
                            ) { onJukeboxGame(game.id) }
                        }
                    }
                    if (jukeboxSearch.trackHits.isNotEmpty()) {
                        item(key = "jb-search-tracks-h") {
                            Text(stringResource(R.string.jukebox_tracks_section), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        items(jukeboxSearch.trackHits, key = { "track-s:${it.second.key}" }) { (game, item) ->
                            val idx = game.items.indexOfFirst { it.key == item.key }.coerceAtLeast(0)
                            JukeboxTrackRow(
                                item = item,
                                language = language,
                                subtitleExtra = game.localizedName(language),
                                packDirectory = packDirectory,
                                playing = activeTrackKey == item.key,
                                onPlay = { onPlayJukeboxTrack(game, idx) },
                                onPlayNext = {
                                    onPlayJukeboxNext(item, game)
                                    scope.launch { snackbarHostState.showSnackbar(addedNextText) }
                                },
                                onPlayFromHere = { onPlayJukeboxTrack(game, idx) },
                            )
                        }
                    }
                }
            }
            if (category < 0 && query.isNotBlank()) {
                if (jukeboxSearch.games.isNotEmpty()) {
                    item(key = "root-jb-games-h") {
                        Text(stringResource(R.string.jukebox_games_section), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    items(jukeboxSearch.games.take(6), key = { "root-game:${it.id}" }) { game ->
                        JukeboxGameRow(
                            game = game,
                            language = language,
                            packDirectory = packDirectory,
                            artworkName = extraCatalog.jukeboxGameArtwork[game.id],
                        ) { onJukeboxGame(game.id) }
                    }
                }
                if (jukeboxSearch.trackHits.isNotEmpty()) {
                    item(key = "root-jb-tracks-h") {
                        Text(stringResource(R.string.jukebox_tracks_section), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    items(jukeboxSearch.trackHits.take(15), key = { "root-track:${it.second.key}" }) { (game, item) ->
                        val idx = game.items.indexOfFirst { it.key == item.key }.coerceAtLeast(0)
                        JukeboxTrackRow(
                            item = item,
                            language = language,
                            subtitleExtra = game.localizedName(language),
                            packDirectory = packDirectory,
                            playing = activeTrackKey == item.key,
                            onPlay = { onPlayJukeboxTrack(game, idx) },
                            onPlayNext = {
                                onPlayJukeboxNext(item, game)
                                scope.launch { snackbarHostState.showSnackbar(addedNextText) }
                            },
                            onPlayFromHere = { onPlayJukeboxTrack(game, idx) },
                        )
                    }
                }
            }
            val emptyHere = when (category) {
                2 -> courses.isEmpty() && sfcCourses.isEmpty()
                3 -> battleTracks.isEmpty()
                4 -> if (query.isBlank()) jukebox.isEmpty else (jukeboxSearch.games.isEmpty() && jukeboxSearch.trackHits.isEmpty())
                5 -> otherItems.isEmpty()
                6 -> shortItems.isEmpty()
                7 -> nmItems.isEmpty()
                else -> category >= 0 && routes.isEmpty() && courses.isEmpty()
            }
            if (emptyHere) {
                item {
                    Text(
                        stringResource(if (category == 4 && query.isNotBlank()) R.string.jukebox_search_empty else R.string.discovery_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (category < 0 && query.isNotBlank() && routes.isEmpty() && courses.isEmpty() &&
                sfcCourses.isEmpty() && battleTracks.isEmpty() && otherItems.isEmpty() &&
                shortItems.isEmpty() && nmItems.isEmpty() && jukeboxSearch.games.isEmpty() && jukeboxSearch.trackHits.isEmpty()
            ) item {
                Text(stringResource(R.string.discovery_search_empty), Modifier.padding(vertical = 16.dp))
            }
            }
        }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun RouteRow(route: OfficialRoute, catalog: BrowseCatalog?, language: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val emblem = com.mylittleblover.mkwplayer.ui.art.CupArt.routeEmblem(route.id)
            emblem?.let { ResourceArtwork(it, Modifier.size(56.dp)) }
            val stopsText = stringResource(R.string.map_route_stops, route.stops.size)
            val subtitle = if (route.kind == OfficialRouteKind.RALLY && route.stops.size >= 2) {
                val startName = catalog?.displayName(route.stops.first().stageCode, language) ?: route.stops.first().stageCode
                val endName = catalog?.displayName(route.stops.last().stageCode, language) ?: route.stops.last().stageCode
                "$startName → $endName · $stopsText"
            } else {
                stopsText
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(route.displayName(language), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun DiscoveryCup(route: OfficialRoute?, catalog: BrowseCatalog?, language: String,
    onMap: (String) -> Unit, onCourse: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (route == null) item { Text(stringResource(R.string.discovery_empty)) }
        else {
            item {
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            val emblem = com.mylittleblover.mkwplayer.ui.art.CupArt.routeEmblem(route.id)
                            if (emblem != null) ResourceArtwork(emblem, Modifier.size(64.dp))
                            else Icon(Icons.Outlined.GraphicEq, null, Modifier.size(48.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(route.displayName(language), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.map_route_stops, route.stops.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        OutlinedButton(onClick = { onMap(route.id) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.discovery_view_map))
                        }
                    }
                }
            }
            item { Text(stringResource(R.string.discovery_course_order), style = MaterialTheme.typography.titleLarge) }
            items(route.stops, key = { it.id }) { stop ->
                Card(onClick = { onCourse(stop.stageCode) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(stop.ordinal.toString(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        StageArt.resolve(stop.stageCode)?.let { ResourceArtwork(it, Modifier.size(52.dp)) }
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
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(120) },
                label = { Text(stringResource(R.string.discovery_playlist_name)) },
                supportingText = if (name.isBlank()) {
                    { Text(stringResource(R.string.discovery_create_name_hint)) }
                } else null,
                singleLine = true,
            )
        },
        confirmButton = { TextButton(onClick = { onCreate(name); name = ""; creating = false }, enabled = name.isNotBlank()) { Text(stringResource(R.string.discovery_create)) } },
        dismissButton = { TextButton(onClick = { creating = false }) { Text(stringResource(R.string.discovery_cancel)) } })
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.discovery_ordinary_lists), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = { creating = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.discovery_create_playlist))
                }
            }
        }
        if (state.playlistError != null) item { Text(stringResource(R.string.discovery_save_failed), color = MaterialTheme.colorScheme.error) }
        if (state.playlists.isEmpty()) {
            item {
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Text(
                        stringResource(R.string.discovery_no_saved),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        } else {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(if (showAll) state.playlists else state.playlists.take(5), key = { "playlist:${it.id}" }) { playlist ->
                        Card(onClick = { onPlaylist(playlist.id) }, modifier = Modifier.width(164.dp)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.fillMaxWidth().height(112.dp), contentAlignment = Alignment.Center) {
                                    val first = playlist.courseIds.firstOrNull { it in catalog?.courses(unlocked).orEmpty() }
                                    val art = first?.let { StageArt.resolve(it) }
                                    if (art != null) ResourceArtwork(art, Modifier.size(90.dp))
                                    else Icon(Icons.Outlined.MusicNote, null, Modifier.size(40.dp))
                                }
                                Text(playlist.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(playlist.courseIds.size.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            if (!showAll && state.playlists.size > 5) item { TextButton(onClick = { showAll = true }) { Text(stringResource(R.string.discovery_show_all)) } }
        }
        item {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }
        item {
            Text(
                stringResource(R.string.discovery_official_quick),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
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
                        val emblem = com.mylittleblover.mkwplayer.ui.art.CupArt.routeEmblem(route.id)
                        if (emblem != null) ResourceArtwork(emblem, Modifier.size(90.dp))
                        else ResourceArtwork(R.drawable.mode_knockout, Modifier.size(80.dp))
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
    var menuOpen by remember { mutableStateOf(false) }
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
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it.take(120) },
                label = { Text(stringResource(R.string.discovery_playlist_name)) },
                supportingText = if (newName.isBlank()) {
                    { Text(stringResource(R.string.discovery_create_name_hint)) }
                } else null,
                singleLine = true,
            )
        },
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
                Text(playlist.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.jukebox_more_actions))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.discovery_rename)) },
                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                newName = playlist.name
                                renaming = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.discovery_delete_playlist), color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuOpen = false
                                deleting = true
                            },
                        )
                    }
                }
            } }
            if (error) item { Text(stringResource(R.string.discovery_save_failed), color = MaterialTheme.colorScheme.error) }
            item {
                Button(onClick = { choosing = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.discovery_add_course))
                }
            }
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

@Composable
private fun JukeboxGameRow(
    game: JukeboxCatalog.Game,
    language: String,
    packDirectory: java.io.File? = null,
    artworkName: String? = null,
    onClick: () -> Unit,
) {
    val localized = game.localizedName(language)
    val countLabel = stringResource(R.string.jukebox_tracks_count, game.items.size)
    val sub = if (game.yearLabel.isNotBlank()) "${game.yearLabel} · $countLabel" else countLabel
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PackArtwork.Thumbnail(
                packDirectory, listOfNotNull(artworkName),
                Modifier.size(54.dp).clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
            ) {
                val badge = game.year.takeIf { it > 0 }?.toString()
                if (badge != null) {
                    Text(
                        badge,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else {
                    Icon(Icons.Outlined.MusicNote, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    localized,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun JukeboxTrackRow(
    item: JukeboxCatalog.TrackItem,
    language: String = "en",
    subtitleExtra: String? = null,
    indexNumber: Int? = null,
    packDirectory: java.io.File? = null,
    playing: Boolean = false,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayFromHere: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val artworkName = item.ostIndex?.let { "t_%03d.webp".format(java.util.Locale.ROOT, it) }
    val containerColor = if (playing) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    Surface(
        onClick = onPlay,
        color = containerColor,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = if (playing) 2.dp else 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PackArtwork.Thumbnail(
                packDirectory, listOfNotNull(artworkName), Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
            ) {
                when {
                    playing -> Icon(Icons.Filled.PlayArrow, null, tint = MaterialTheme.colorScheme.primary)
                    indexNumber != null -> Text(
                        indexNumber.toString().padStart(2, '0'),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> Icon(Icons.Outlined.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    item.localizedTitle(language),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (playing) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val metaParts = buildList {
                    item.localizedArrangement(language)?.let { add(it) }
                    subtitleExtra?.let { add(it) }
                    if (item.durationMs > 0L) add(item.durationFormatted)
                }
                if (metaParts.isNotEmpty()) {
                    Text(
                        metaParts.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.jukebox_more_actions),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.jukebox_play_next)) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
                        onClick = {
                            menuOpen = false
                            onPlayNext()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.jukebox_play_from_here)) },
                        leadingIcon = { Icon(Icons.Filled.PlayArrow, null) },
                        onClick = {
                            menuOpen = false
                            onPlayFromHere()
                        },
                    )
                }
            }
        }
    }
}

/**
 * Detail page for one original reference game in the Jukebox (#98-#104), aligned with
 * the HTML prototype's `gamepage()`: chronological game header, OST track sequence,
 * independent rows for every arrangement, and Play All / Shuffle / Play Next actions.
 */
@Composable
internal fun DiscoveryJukeboxGame(
    game: JukeboxCatalog.Game?,
    language: String,
    activeTrackKey: String?,
    packDirectory: java.io.File? = null,
    onPlayFromIndex: (JukeboxCatalog.Game, Int, Boolean) -> Unit,
    onPlayNext: (JukeboxCatalog.TrackItem, JukeboxCatalog.Game) -> Unit,
) {
    if (game == null) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.discovery_empty))
        }
        return
    }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val addedNextText = stringResource(R.string.jukebox_added_next)
    val otherCategoryLabel = stringResource(R.string.discovery_other)
    val shortLabel = stringResource(R.string.discovery_sounds)
    val nmLabel = stringResource(R.string.discovery_nintendo_music)
    val localizedTitle = game.localizedName(language)
    val englishTitle = game.localizedNames["en"]

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "header") {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.jukebox_game_eyebrow),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            localizedTitle,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (englishTitle != null && !englishTitle.equals(localizedTitle, ignoreCase = true)) {
                            Text(
                                englishTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val countStr = stringResource(R.string.jukebox_tracks_count, game.items.size)
                        val yearAndCount = if (game.yearLabel.isNotBlank()) "${game.yearLabel} · $countStr" else countStr
                        Text(
                            yearAndCount,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.jukebox_game_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { onPlayFromIndex(game, 0, false) },
                                enabled = game.items.isNotEmpty(),
                            ) {
                                Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.jukebox_play_all))
                            }
                            OutlinedButton(
                                onClick = {
                                    val randomStart = game.items.indices.random()
                                    onPlayFromIndex(game, randomStart, true)
                                },
                                enabled = game.items.isNotEmpty(),
                            ) {
                                Icon(Icons.Filled.Shuffle, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.jukebox_shuffle_play))
                            }
                        }
                    }
                }
            }
            item(key = "arr-section") {
                Text(
                    stringResource(R.string.jukebox_arrangements_section),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                )
            }
            itemsIndexed(game.items, key = { _, item -> "jb-game:${item.key}" }) { index, item ->
                JukeboxTrackRow(
                    item = item,
                    language = language,
                    indexNumber = index + 1,
                    packDirectory = packDirectory,
                    playing = activeTrackKey == item.key,
                    onPlay = { onPlayFromIndex(game, index, false) },
                    onPlayNext = {
                        onPlayNext(item, game)
                        scope.launch { snackbarHostState.showSnackbar(addedNextText) }
                    },
                    onPlayFromHere = { onPlayFromIndex(game, index, false) },
                )
            }
        }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun ExtraSquareRow(
    title: String,
    subtitle: String,
    packDirectory: java.io.File?,
    artworkName: String?,
    fallbackCourseCode: String? = null,
    onAction: (() -> Unit)? = null,
    actionContentDescription: String? = null,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PackArtwork.Thumbnail(
                packDirectory, listOfNotNull(artworkName),
                Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                if (fallbackCourseCode != null) CourseCover(fallbackCourseCode, Modifier.fillMaxSize())
                else Icon(Icons.Outlined.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (onAction != null) {
                FilledTonalIconButton(
                    onClick = onAction,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = actionContentDescription, modifier = Modifier.size(20.dp))
                }
            } else {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ExtraQueueActionsRow(
    count: Int,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.jukebox_tracks_count, count),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        FilledTonalButton(onClick = onPlayAll) {
            Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.jukebox_play_all))
        }
        OutlinedButton(onClick = onShuffle) {
            Icon(Icons.Filled.Shuffle, null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.jukebox_shuffle))
        }
    }
}

private fun extraOtherToQueueEntry(
    item: ExtraMusicCatalog.OtherMusicItem,
    language: String,
    categoryLabel: String,
): com.mylittleblover.mkwplayer.player.LinearQueue.Entry {
    val title = item.localizedName(language)
    return when (item) {
        is ExtraMusicCatalog.OtherMusicItem.LinearAsset -> com.mylittleblover.mkwplayer.player.LinearQueue.Entry(
            key = "extra:${item.id}",
            title = title,
            subtitle = categoryLabel,
            ostIndex = item.ostIndex,
            durationMs = item.durationMs,
            artworkName = item.artwork,
            assetSha256 = item.asset.sha256,
            loop = item.asset.loop,
            loopStart = item.asset.loopStart,
            loopEnd = item.asset.loopEnd,
        )
        is ExtraMusicCatalog.OtherMusicItem.OverlaidOpening -> com.mylittleblover.mkwplayer.player.LinearQueue.Entry(
            key = "extra:${item.id}",
            title = title,
            subtitle = categoryLabel,
            ostIndex = item.ostIndex,
            durationMs = item.durationMs,
            artworkName = item.artwork,
            overlaidOpening = item,
        )
        is ExtraMusicCatalog.OtherMusicItem.ChainedSequence -> com.mylittleblover.mkwplayer.player.LinearQueue.Entry(
            key = "extra:${item.id}",
            title = title,
            subtitle = categoryLabel,
            ostIndex = item.ostIndex,
            durationMs = item.durationMs,
            artworkName = item.artwork,
            chainedSequence = item,
        )
        is ExtraMusicCatalog.OtherMusicItem.InteractiveTitle -> com.mylittleblover.mkwplayer.player.LinearQueue.Entry(
            key = "extra:${item.id}",
            title = title,
            subtitle = categoryLabel,
            ostIndex = item.ostIndex,
            durationMs = item.durationMs,
            artworkName = item.artwork,
        )
    }
}

private fun extraSimpleToQueueEntry(
    item: ExtraMusicCatalog.SimpleTrackItem,
    language: String,
    categoryLabel: String,
): com.mylittleblover.mkwplayer.player.LinearQueue.Entry = com.mylittleblover.mkwplayer.player.LinearQueue.Entry(
    key = "extra:${item.id}",
    title = item.localizedName(language),
    subtitle = categoryLabel,
    ostIndex = item.ostIndex,
    durationMs = item.durationMs,
    artworkName = item.artwork,
    assetSha256 = item.asset.sha256,
    loop = item.asset.loop,
    loopStart = item.asset.loopStart,
    loopEnd = item.asset.loopEnd,
)

private fun formatExtraDuration(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return "%d:%02d".format(java.util.Locale.ROOT, min, sec)
}
