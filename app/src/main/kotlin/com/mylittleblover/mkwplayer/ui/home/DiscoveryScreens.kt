package com.mylittleblover.mkwplayer.ui.home

import androidx.compose.animation.AnimatedVisibility
import com.mylittleblover.mkwplayer.ui.art.PackArtwork
import com.mylittleblover.mkwplayer.ui.art.ResourceArtwork
import com.mylittleblover.mkwplayer.ui.art.CourseCover
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AddToQueue
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.selected
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
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import com.mylittleblover.mkwplayer.R
import com.mylittleblover.mkwplayer.library.BrowseCatalog
import com.mylittleblover.mkwplayer.library.PlaylistItemRef
import com.mylittleblover.mkwplayer.library.PresetRefs
import com.mylittleblover.mkwplayer.library.TrackPlayStat
import com.mylittleblover.mkwplayer.library.TrackSortOrder
import com.mylittleblover.mkwplayer.routes.CustomRoute
import com.mylittleblover.mkwplayer.routes.OfficialRoute
import com.mylittleblover.mkwplayer.routes.OfficialRouteKind
import com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog
import com.mylittleblover.mkwplayer.ui.art.StageArt
import com.mylittleblover.mkwplayer.ui.routes.RoadMusicRuleModeSelector
import com.mylittleblover.mkwplayer.ui.routes.RouteEdgeMusicPickerSheet
import com.mylittleblover.mkwplayer.ui.routes.edgeResolutionSummary
import com.mylittleblover.mkwplayer.ui.routes.hasKnownPool
import com.mylittleblover.mkwplayer.ui.routes.isMissingOrIncomplete

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
private fun DiscoveryHomeProgressBar(
    flow: com.mylittleblover.mkwplayer.player.PlayerViewModel.FlowUi?,
    fallbackProgress: Float,
    progressFlow: kotlinx.coroutines.flow.StateFlow<com.mylittleblover.mkwplayer.player.PlayerViewModel.ProgressUi>?,
) {
    val progressUi = progressFlow?.collectAsState()?.value
    val srcFrame = progressUi?.sourceFrame ?: flow?.sourceFrame
    val fraction = if (srcFrame != null && flow?.sourceFrames != null && flow.sourceFrames > 0) {
        (srcFrame.toDouble() / flow.sourceFrames).toFloat().coerceIn(0f, 1f)
    } else {
        (progressUi?.progress ?: fallbackProgress).coerceIn(0f, 1f)
    }
    com.mylittleblover.mkwplayer.ui.SmoothProgressIndicator(fraction, modifier = Modifier.fillMaxWidth())
}

@Composable
internal fun DiscoveryHome(catalog: BrowseCatalog?, state: DiscoveryViewModel.State, language: String, unlocked: Boolean,
                           onOpen: (String) -> Unit, onFavorite: (String) -> Unit,
                           onImport: () -> Unit, onPlayer: () -> Unit, onToggle: () -> Unit, onMedia: () -> Unit,
                           session: com.mylittleblover.mkwplayer.player.PlayerViewModel.Ui, currentCode: String?,
                           packDirectory: java.io.File? = null, artworkNames: List<String> = emptyList(),
                           courseArtwork: Map<String, String> = emptyMap(),
                           jukebox: JukeboxCatalog.Catalog = JukeboxCatalog.Catalog(emptyList(), emptyMap()),
                           extraCatalog: ExtraMusicCatalog.Catalog = ExtraMusicCatalog.Catalog(),
                           progressFlow: kotlinx.coroutines.flow.StateFlow<com.mylittleblover.mkwplayer.player.PlayerViewModel.ProgressUi>? = null,
                           onQuickPlay: (String) -> Unit = {},
                           onResumeSaved: () -> Unit = {},
                           onDismissSaved: () -> Unit = {},
                           playStats: Map<String, TrackPlayStat> = emptyMap(),
                           onPlaySelectedRefs: (List<String>) -> Boolean = { false }) {
    val dailyHintFallback = stringResource(R.string.discovery_home_daily_hint)
    val coursesSubtitle = stringResource(R.string.discovery_courses)
    val sfcSubtitle = stringResource(R.string.discovery_vs_sfc)
    val battleSubtitle = stringResource(R.string.discovery_battle)
    val freeSubtitle = stringResource(R.string.discovery_free)
    val otherSubtitle = stringResource(R.string.discovery_other)
    val soundsSubtitle = stringResource(R.string.discovery_sounds)
    val nmSubtitle = stringResource(R.string.discovery_nintendo_music)
    data class DailyCardInfo(
        val rawId: String,
        val title: String,
        val subtitle: String,
        val artworkName: String?,
        val fallbackCourseCode: String?,
    )
    val visibleCourseSet = catalog?.courses(unlocked).orEmpty().toSet()
    val gamesById = remember(jukebox) { jukebox.games.associateBy { it.id } }
    val visibleDaily = remember(
        state.dailyIds,
        catalog,
        unlocked,
        language,
        jukebox,
        extraCatalog,
        courseArtwork,
        dailyHintFallback,
        sfcSubtitle,
        battleSubtitle,
        freeSubtitle,
        otherSubtitle,
    ) {
        state.dailyIds.mapNotNull { rawId ->
            when (val ref = com.mylittleblover.mkwplayer.library.PlaylistItemRef.parse(rawId)) {
                is com.mylittleblover.mkwplayer.library.PlaylistItemRef.Course -> {
                    val logical = ref.logicalCode
                    if (logical !in visibleCourseSet && rawId !in visibleCourseSet) null
                    else DailyCardInfo(
                        rawId = rawId,
                        title = catalog?.displayName(logical, language) ?: logical,
                        subtitle = dailyHintFallback,
                        artworkName = courseArtwork[rawId] ?: courseArtwork[logical],
                        fallbackCourseCode = logical,
                    )
                }
                is com.mylittleblover.mkwplayer.library.PlaylistItemRef.SfcTrack -> {
                    val track = extraCatalog.sfcCourses.firstOrNull { it.id == ref.trackId }
                    if (track == null || !ExtraMusicCatalog.isVisible(track.id, unlocked)) null
                    else DailyCardInfo(
                        rawId = rawId,
                        title = track.localizedName(language),
                        subtitle = sfcSubtitle,
                        artworkName = track.artwork,
                        fallbackCourseCode = null,
                    )
                }
                is com.mylittleblover.mkwplayer.library.PlaylistItemRef.BattleTrack -> {
                    val track = extraCatalog.battleTracks.firstOrNull { it.id == ref.trackId }
                    if (track == null || !ExtraMusicCatalog.isVisible(track.id, unlocked)) null
                    else DailyCardInfo(
                        rawId = rawId,
                        title = track.localizedName(language),
                        subtitle = battleSubtitle,
                        artworkName = track.artwork,
                        fallbackCourseCode = null,
                    )
                }
                is com.mylittleblover.mkwplayer.library.PlaylistItemRef.Jukebox -> {
                    val item = jukebox.itemsByKey[ref.trackKey]
                    if (item == null) null
                    else {
                        val game = gamesById[item.gameId]
                        DailyCardInfo(
                            rawId = rawId,
                            title = item.localizedDisplayName(language),
                            subtitle = game?.localizedName(language) ?: freeSubtitle,
                            artworkName = item.ostIndex?.let { "t_%03d.webp".format(java.util.Locale.ROOT, it) }
                                ?: extraCatalog.jukeboxGameArtwork[item.gameId],
                            fallbackCourseCode = null,
                        )
                    }
                }
                is com.mylittleblover.mkwplayer.library.PlaylistItemRef.ExtraTrack -> {
                    val other = extraCatalog.otherMusic.firstOrNull { it.id == ref.extraId }
                    if (other == null || !ExtraMusicCatalog.isVisible(other.id, unlocked)) null
                    else DailyCardInfo(
                        rawId = rawId,
                        title = other.localizedName(language),
                        subtitle = otherSubtitle,
                        artworkName = other.artwork,
                        fallbackCourseCode = null,
                    )
                }
            }
        }
    }
    val mostPlayedHome = remember(playStats, catalog, unlocked, language, extraCatalog, jukebox) {
        playStats.values
            .asSequence()
            .filter { it.playCount > 0 }
            .sortedWith(compareByDescending<TrackPlayStat> { it.playCount }.thenByDescending { it.lastPlayedEpochMs })
            .mapNotNull { stat ->
                resolveSmartPlaylistEntry(
                    rawId = stat.trackId,
                    catalog = catalog,
                    unlocked = unlocked,
                    language = language,
                    extraCatalog = extraCatalog,
                    jukeboxItemsByKey = jukebox.itemsByKey,
                    gamesById = gamesById,
                    visibleCourseSet = visibleCourseSet,
                    coursesLabel = coursesSubtitle,
                    sfcLabel = sfcSubtitle,
                    battleLabel = battleSubtitle,
                    freeLabel = freeSubtitle,
                    otherLabel = otherSubtitle,
                    soundsLabel = soundsSubtitle,
                    nmLabel = nmSubtitle,
                    playCount = stat.playCount,
                )
            }
            .distinctBy { it.rawRef }
            .take(30)
            .toList()
    }
    val favoritesMixHome = remember(state.favorites, playStats, catalog, unlocked, language, extraCatalog, jukebox) {
        state.favorites
            .mapNotNull { fav ->
                val plays = playStats[canonicalStatIdForRef(fav)]?.playCount ?: 0
                resolveSmartPlaylistEntry(
                    rawId = fav,
                    catalog = catalog,
                    unlocked = unlocked,
                    language = language,
                    extraCatalog = extraCatalog,
                    jukeboxItemsByKey = jukebox.itemsByKey,
                    gamesById = gamesById,
                    visibleCourseSet = visibleCourseSet,
                    coursesLabel = coursesSubtitle,
                    sfcLabel = sfcSubtitle,
                    battleLabel = battleSubtitle,
                    freeLabel = freeSubtitle,
                    otherLabel = otherSubtitle,
                    soundsLabel = soundsSubtitle,
                    nmLabel = nmSubtitle,
                    playCount = plays,
                )
            }
            .distinctBy { it.rawRef }
    }
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
                    val card = visibleDaily[index]
                    val id = card.rawId
                    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp))) {
                        if (card.fallbackCourseCode != null) {
                            CourseCover(
                                code = card.fallbackCourseCode,
                                modifier = Modifier.fillMaxSize(),
                                packDirectory = packDirectory,
                                artworkName = card.artworkName,
                            )
                        } else {
                            PackArtwork.Thumbnail(
                                packDirectory = packDirectory,
                                artworkNames = listOfNotNull(card.artworkName),
                                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer),
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Outlined.MusicNote,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
                                        modifier = Modifier.size(64.dp),
                                    )
                                }
                            }
                        }
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x33000000), Color(0xCC000000)))))
                        card.fallbackCourseCode?.let { StageArt.resolve(it) }?.let {
                            ResourceArtwork(it, Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 12.dp).size(88.dp))
                        }
                        Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                "${stringResource(R.string.discovery_home_today)} · ${index + 1} / ${visibleDaily.size}",
                                color = Color.White.copy(alpha = .92f),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(card.title, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(card.subtitle, color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 240.dp))
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
        if (!session.canStop && session.continueListening != null) {
            val saved = session.continueListening
            item { Text(stringResource(R.string.discovery_continue_listening), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            item {
                Card(
                    onClick = onResumeSaved,
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PackArtwork.Thumbnail(
                            packDirectory,
                            listOfNotNull(saved.artworkName),
                            Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)),
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Outlined.MusicNote, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(30.dp))
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(saved.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val meta = listOfNotNull(
                                saved.subtitle.takeIf { it.isNotBlank() },
                                "${stringResource(R.string.discovery_continue_paused)} · ${saved.positionText} / ${saved.totalText}",
                            ).joinToString(" · ")
                            Text(meta, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            com.mylittleblover.mkwplayer.ui.SmoothProgressIndicator(saved.progress.coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth())
                        }
                        FilledIconButton(
                            onClick = onResumeSaved,
                            modifier = Modifier.semantics { contentDescription = "" },
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.discovery_continue_resume))
                        }
                        IconButton(onClick = onDismissSaved) {
                            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.discovery_continue_dismiss))
                        }
                    }
                }
            }
        } else if (session.canStop) {
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
                            DiscoveryHomeProgressBar(
                                flow = flow,
                                fallbackProgress = session.progress,
                                progressFlow = progressFlow,
                            )
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
        if (mostPlayedHome.isNotEmpty() || favoritesMixHome.isNotEmpty()) {
            item { Text(stringResource(R.string.smart_playlists_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            if (mostPlayedHome.isNotEmpty()) {
                item {
                    SmartPlaylistCard(
                        title = stringResource(R.string.smart_most_played),
                        subtitle = stringResource(R.string.smart_most_played_subtitle),
                        emptyText = stringResource(R.string.smart_most_played_empty),
                        entries = mostPlayedHome,
                        packDirectory = packDirectory,
                        onPlayRefs = onPlaySelectedRefs,
                    )
                }
            }
            if (favoritesMixHome.isNotEmpty()) {
                item {
                    SmartPlaylistCard(
                        title = stringResource(R.string.smart_favorites_mix),
                        subtitle = stringResource(R.string.smart_favorites_mix_subtitle),
                        emptyText = stringResource(R.string.smart_favorites_mix_empty),
                        entries = favoritesMixHome,
                        packDirectory = packDirectory,
                        onPlayRefs = onPlaySelectedRefs,
                    )
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
    allowPlayNext: Boolean = true,
    unlockProgress: String? = null,
    unlockCompleted: Int = 0,
    unlockTotal: Int = 0,
    onJukeboxGame: (String) -> Unit = {},
    onPlayJukeboxTrack: (JukeboxCatalog.Game, Int) -> Unit = { _, _ -> },
    onPlayAllJukebox: (Boolean) -> Unit = {},
    onPlayJukeboxNext: (JukeboxCatalog.TrackItem, JukeboxCatalog.Game) -> Unit = { _, _ -> },
    onJukeboxTrack: (String) -> Unit = {},
    onSfcCourse: (ExtraMusicCatalog.TwoStateTrack) -> Unit = {},
    onBattleTrack: (ExtraMusicCatalog.TwoStateTrack) -> Unit = {},
    onOpenTitle: () -> Unit = {},
    onPlayExtraQueue: (List<com.mylittleblover.mkwplayer.player.LinearQueue.Entry>, Int, com.mylittleblover.mkwplayer.player.LinearQueue.RepeatMode, Boolean) -> Unit = { _, _, _, _ -> },
    onAuditionExtra: (com.mylittleblover.mkwplayer.player.LinearQueue.Entry) -> Unit = {},
    extraMedleyCatalog: com.mylittleblover.mkwplayer.interactive.ExtraMedleyCatalog.Catalog = com.mylittleblover.mkwplayer.interactive.ExtraMedleyCatalog.Catalog(),
    onPlayExtraMedley: (id: String, title: String, subtitle: String, artwork: String?) -> Unit = { _, _, _, _ -> },
    onOpenExtraFlowEditor: (String) -> Unit = {},
    playlists: List<com.mylittleblover.mkwplayer.library.UserPlaylist> = emptyList(),
    favorites: Set<String> = emptySet(),
    onToggleFavorite: (String) -> Unit = {},
    onAddToPlaylist: (playlistId: String, refs: List<String>) -> Unit = { _, _ -> },
    onCreatePlaylistAndAdd: (name: String, refs: List<String>) -> Unit = { _, _ -> },
    onPlayNextRefs: (List<String>) -> Boolean = { false },
    onAddToQueueRefs: (List<String>) -> Boolean = { false },
    onPlaySelectedRefs: (List<String>) -> Boolean = { false },
    onOpenCourseFlowEditor: (String) -> Unit = {},
    playStats: Map<String, TrackPlayStat> = emptyMap()) {
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
                    allowPlayNext = allowPlayNext,
                    unlockProgress = unlockProgress,
                    unlockCompleted = unlockCompleted,
                    unlockTotal = unlockTotal,
                    onJukeboxGame = onJukeboxGame,
                    onPlayJukeboxTrack = onPlayJukeboxTrack,
                    onPlayAllJukebox = onPlayAllJukebox,
                    onPlayJukeboxNext = onPlayJukeboxNext,
                    onJukeboxTrack = onJukeboxTrack,
                    onSfcCourse = onSfcCourse,
                    onBattleTrack = onBattleTrack,
                    onOpenTitle = onOpenTitle,
                    onPlayExtraQueue = onPlayExtraQueue,
                    onAuditionExtra = onAuditionExtra,
                    extraMedleyCatalog = extraMedleyCatalog,
                    onPlayExtraMedley = onPlayExtraMedley,
                    onOpenExtraFlowEditor = onOpenExtraFlowEditor,
                    playlists = playlists,
                    favorites = favorites,
                    onToggleFavorite = onToggleFavorite,
                    onAddToPlaylist = onAddToPlaylist,
                    onCreatePlaylistAndAdd = onCreatePlaylistAndAdd,
                    onPlayNextRefs = onPlayNextRefs,
                    onAddToQueueRefs = onAddToQueueRefs,
                    onPlaySelectedRefs = onPlaySelectedRefs,
                    onOpenCourseFlowEditor = onOpenCourseFlowEditor,
                    playStats = playStats)
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
                            allowPlayNext: Boolean = true,
                            unlockProgress: String? = null,
                            unlockCompleted: Int = 0,
                            unlockTotal: Int = 0,
                            onJukeboxGame: (String) -> Unit = {},
                            onPlayJukeboxTrack: (JukeboxCatalog.Game, Int) -> Unit = { _, _ -> },
                            onPlayAllJukebox: (Boolean) -> Unit = {},
                            onPlayJukeboxNext: (JukeboxCatalog.TrackItem, JukeboxCatalog.Game) -> Unit = { _, _ -> },
                            onJukeboxTrack: (String) -> Unit = {},
                            onSfcCourse: (ExtraMusicCatalog.TwoStateTrack) -> Unit = {},
                            onBattleTrack: (ExtraMusicCatalog.TwoStateTrack) -> Unit = {},
                            onOpenTitle: () -> Unit = {},
                            onPlayExtraQueue: (List<com.mylittleblover.mkwplayer.player.LinearQueue.Entry>, Int, com.mylittleblover.mkwplayer.player.LinearQueue.RepeatMode, Boolean) -> Unit = { _, _, _, _ -> },
                            onAuditionExtra: (com.mylittleblover.mkwplayer.player.LinearQueue.Entry) -> Unit = {},
                            extraMedleyCatalog: com.mylittleblover.mkwplayer.interactive.ExtraMedleyCatalog.Catalog = com.mylittleblover.mkwplayer.interactive.ExtraMedleyCatalog.Catalog(),
                            onPlayExtraMedley: (id: String, title: String, subtitle: String, artwork: String?) -> Unit = { _, _, _, _ -> },
                            onOpenExtraFlowEditor: (String) -> Unit = {},
                            playlists: List<com.mylittleblover.mkwplayer.library.UserPlaylist> = emptyList(),
                            favorites: Set<String> = emptySet(),
                            onToggleFavorite: (String) -> Unit = {},
                            onAddToPlaylist: (playlistId: String, refs: List<String>) -> Unit = { _, _ -> },
                            onCreatePlaylistAndAdd: (name: String, refs: List<String>) -> Unit = { _, _ -> },
                            onPlayNextRefs: (List<String>) -> Boolean = { false },
                            onAddToQueueRefs: (List<String>) -> Boolean = { false },
                            onPlaySelectedRefs: (List<String>) -> Boolean = { false },
                            onOpenCourseFlowEditor: (String) -> Unit = {},
                            playStats: Map<String, TrackPlayStat> = emptyMap()) {
    androidx.activity.compose.BackHandler(enabled = category >= 0) { onCategory(-1) }
    var sortOrder by rememberSaveable(category) { mutableStateOf(TrackSortOrder.DEFAULT) }
    val categories = listOf(
        R.string.discovery_courses,
        R.string.discovery_battle,
        R.string.discovery_free,
        R.string.discovery_other,
        R.string.discovery_sounds,
        R.string.discovery_nintendo_music,
    )
    val routes = if (category < 0 && query.isNotBlank()) {
        catalog?.routes(OfficialRouteKind.GRAND_PRIX, unlocked, query).orEmpty() +
            catalog?.routes(OfficialRouteKind.RALLY, unlocked, query).orEmpty()
    } else {
        emptyList()
    }
    val rawCourses = if (category == 0 || (category < 0 && query.isNotBlank())) catalog?.searchCourses(query, unlocked).orEmpty() else emptyList()
    val courses = remember(rawCourses, sortOrder, playStats, catalog, language) {
        when (sortOrder) {
            TrackSortOrder.NAME_ASC -> rawCourses.sortedBy { (catalog?.displayName(it, language) ?: it).lowercase() }
            TrackSortOrder.MOST_PLAYED -> rawCourses.sortedByDescending { playStats[it.removeSuffix("_00")]?.playCount ?: 0 }
            else -> rawCourses
        }
    }
    val showLockedRainbow = remember(unlocked, catalog, category, query, rawCourses) {
        !unlocked && catalog != null &&
            (category == 0 || (category < 0 && query.isNotBlank())) &&
            "Rainbow" !in rawCourses &&
            "Rainbow" in catalog.searchCourses("", specialUnlocked = true) &&
            (query.isBlank() || "?" in query || "？" in query)
    }
    val sfcCourses = remember(extraCatalog, query, category, sortOrder, playStats, language) {
        val base = if (category == 0 || (category < 0 && query.isNotBlank())) extraCatalog.sfcCourses.filter { it.matches(query) } else emptyList()
        when (sortOrder) {
            TrackSortOrder.NAME_ASC -> base.sortedBy { it.localizedName(language).lowercase() }
            TrackSortOrder.MOST_PLAYED -> base.sortedByDescending { playStats[PlaylistItemRef.SfcTrack(it.id).raw]?.playCount ?: 0 }
            else -> base
        }
    }
    val battleTracks = remember(extraCatalog, query, category, sortOrder, playStats, language) {
        val base = if (category == 1 || (category < 0 && query.isNotBlank())) extraCatalog.battleTracks.filter { it.matches(query) } else emptyList()
        when (sortOrder) {
            TrackSortOrder.NAME_ASC -> base.sortedBy { it.localizedName(language).lowercase() }
            TrackSortOrder.MOST_PLAYED -> base.sortedByDescending { playStats[PlaylistItemRef.BattleTrack(it.id).raw]?.playCount ?: 0 }
            else -> base
        }
    }
    val otherItems = remember(extraCatalog, query, category, unlocked, sortOrder, playStats, language) {
        val base = if (category == 3 || (category < 0 && query.isNotBlank())) {
            extraCatalog.otherMusic.filter {
                ExtraMusicCatalog.isVisible(it.id, unlocked) && it.matches(query)
            }
        } else emptyList()
        when (sortOrder) {
            TrackSortOrder.NAME_ASC -> base.sortedBy { it.localizedName(language).lowercase() }
            TrackSortOrder.DURATION_DESC -> base.sortedByDescending { it.durationMs }
            TrackSortOrder.MOST_PLAYED -> base.sortedByDescending { playStats[PlaylistItemRef.ExtraTrack(it.id).raw]?.playCount ?: 0 }
            else -> base
        }
    }
    val shortItems = remember(extraCatalog, query, category, unlocked, sortOrder, playStats, language) {
        val base = if (category == 4 || (category < 0 && query.isNotBlank())) {
            extraCatalog.shortSounds.filter {
                ExtraMusicCatalog.isVisible(it.id, unlocked) && it.matches(query)
            }
        } else emptyList()
        when (sortOrder) {
            TrackSortOrder.NAME_ASC -> base.sortedBy { it.localizedName(language).lowercase() }
            TrackSortOrder.DURATION_DESC -> base.sortedByDescending { it.durationMs }
            TrackSortOrder.MOST_PLAYED -> base.sortedByDescending { playStats[PlaylistItemRef.ExtraTrack(it.id).raw]?.playCount ?: 0 }
            else -> base
        }
    }
    val nmItems = remember(extraCatalog, query, category, unlocked, sortOrder, playStats, language) {
        val base = if (category == 5 || (category < 0 && query.isNotBlank())) {
            extraCatalog.nintendoMusicSpecial.filter {
                ExtraMusicCatalog.isVisible(it.id, unlocked) && it.matches(query)
            }
        } else emptyList()
        when (sortOrder) {
            TrackSortOrder.NAME_ASC -> base.sortedBy { it.localizedName(language).lowercase() }
            TrackSortOrder.DURATION_DESC -> base.sortedByDescending { it.durationMs }
            TrackSortOrder.MOST_PLAYED -> base.sortedByDescending { playStats[PlaylistItemRef.ExtraTrack(it.id).raw]?.playCount ?: 0 }
            else -> base
        }
    }
    val jukeboxSearch = remember(jukebox, query, sortOrder, playStats, language) {
        if (query.isBlank()) {
            JukeboxCatalog.SearchResult(jukebox.games, emptyList())
        } else {
            val raw = jukebox.search(query)
            val sortedHits = when (sortOrder) {
                TrackSortOrder.NAME_ASC -> raw.trackHits.sortedBy { it.second.localizedTitle(language).lowercase() }
                TrackSortOrder.DURATION_DESC -> raw.trackHits.sortedByDescending { it.second.durationMs }
                TrackSortOrder.MOST_PLAYED -> raw.trackHits.sortedByDescending {
                    playStats[PlaylistItemRef.Jukebox(it.second.key).raw]?.playCount ?: 0
                }
                else -> raw.trackHits
            }
            raw.copy(trackHits = sortedHits)
        }
    }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val addedNextText = stringResource(R.string.jukebox_added_next)
    val addedQueueText = stringResource(R.string.track_action_added_to_queue)
    val interactiveSubtitle = stringResource(R.string.discovery_interactive)
    val coursesCatLabel = stringResource(R.string.discovery_courses)
    val sfcCatLabel = stringResource(R.string.discovery_vs_sfc)
    val battleCatLabel = stringResource(R.string.discovery_battle)
    val otherCategoryLabel = stringResource(R.string.discovery_other)
    val shortLabel = stringResource(R.string.discovery_sounds)
    val nmLabel = stringResource(R.string.discovery_nintendo_music)
    val extraSuiteLabel = stringResource(R.string.discovery_suite)
    val listState = rememberSaveable(category, saver = LazyListState.Saver) { LazyListState() }
    var selectedJukeboxDecade by rememberSaveable { mutableStateOf<Int?>(null) }

    var menuTarget by remember { mutableStateOf<TrackMenuSpec?>(null) }
    var infoTarget by remember { mutableStateOf<TrackMenuSpec?>(null) }
    var playlistPickerRefs by remember { mutableStateOf<List<String>?>(null) }
    var multiSelectMode by rememberSaveable(category) { mutableStateOf(false) }
    var selectedKeys by rememberSaveable(category) { mutableStateOf(listOf<String>()) }
    val selectedSet = remember(selectedKeys) { selectedKeys.toSet() }

    androidx.activity.compose.BackHandler(enabled = multiSelectMode) {
        multiSelectMode = false
        selectedKeys = emptyList()
    }

    val visibleRefEntries = remember(
        courses, sfcCourses, battleTracks, otherItems, shortItems, nmItems,
        category, query, jukeboxSearch,
    ) {
        buildList<Pair<String, String>> {
            courses.forEach { code -> add("course:$code" to code) }
            sfcCourses.forEach { t -> add("sfc:${t.id}" to com.mylittleblover.mkwplayer.library.PlaylistItemRef.SfcTrack(t.id).raw) }
            battleTracks.forEach { t -> add("battle:${t.id}" to com.mylittleblover.mkwplayer.library.PlaylistItemRef.BattleTrack(t.id).raw) }
            otherItems.forEach { item -> add("other:${item.id}" to com.mylittleblover.mkwplayer.library.PlaylistItemRef.ExtraTrack(item.id).raw) }
            shortItems.forEach { item -> add("short:${item.id}" to com.mylittleblover.mkwplayer.library.PlaylistItemRef.ExtraTrack(item.id).raw) }
            nmItems.forEach { item -> add("nm:${item.id}" to com.mylittleblover.mkwplayer.library.PlaylistItemRef.ExtraTrack(item.id).raw) }
            if (category == 2 && query.isNotBlank()) {
                jukeboxSearch.trackHits.forEach { (_, item) ->
                    add("jb:${item.key}" to com.mylittleblover.mkwplayer.library.PlaylistItemRef.Jukebox(item.key).raw)
                }
            }
            if (category < 0 && query.isNotBlank()) {
                jukeboxSearch.trackHits.take(15).forEach { (_, item) ->
                    add("jb:${item.key}" to com.mylittleblover.mkwplayer.library.PlaylistItemRef.Jukebox(item.key).raw)
                }
            }
        }
    }
    val visibleRefByKey = remember(visibleRefEntries) { visibleRefEntries.toMap() }
    val allSelectableKeys = remember(visibleRefEntries) { visibleRefEntries.map { it.first } }

    fun toggleSelected(key: String) {
        selectedKeys = if (key in selectedSet) selectedKeys - key else selectedKeys + key
    }

    TrackActionOverlayHosts(
        menuTarget = menuTarget,
        onDismissMenu = { menuTarget = null },
        playlistPickerRefs = playlistPickerRefs,
        onOpenPlaylistPicker = { refs -> playlistPickerRefs = refs },
        onDismissPlaylistPicker = { playlistPickerRefs = null },
        infoTarget = infoTarget,
        onOpenInfo = { infoTarget = it },
        onDismissInfo = { infoTarget = null },
        packDirectory = packDirectory,
        allowPlayNext = allowPlayNext,
        playlists = playlists,
        favorites = favorites,
        onToggleFavorite = onToggleFavorite,
        onOpenCourse = onCourse,
        onOpenFlowEditor = { editorCode ->
            if (editorCode.startsWith("extra:")) {
                onOpenExtraFlowEditor(editorCode.removePrefix("extra:"))
            } else {
                onOpenCourseFlowEditor(editorCode)
            }
        },
        onEnterMultiSelect = { key ->
            multiSelectMode = true
            selectedKeys = (selectedKeys + key).distinct()
        },
        onPlayNextRefs = onPlayNextRefs,
        onAddToQueueRefs = onAddToQueueRefs,
        onAddToPlaylist = { pid, refs ->
            onAddToPlaylist(pid, refs)
            multiSelectMode = false
            selectedKeys = emptyList()
        },
        onCreatePlaylistAndAdd = { name, refs ->
            onCreatePlaylistAndAdd(name, refs)
            multiSelectMode = false
            selectedKeys = emptyList()
        },
        snackbarHostState = snackbarHostState,
        addedNextText = addedNextText,
        addedQueueText = addedQueueText,
    )

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AnimatedVisibility(visible = multiSelectMode) {
                val allSelected = allSelectableKeys.isNotEmpty() && selectedSet.containsAll(allSelectableKeys)
                MultiSelectTopBanner(
                    selectedCount = selectedSet.size,
                    allSelected = allSelected,
                    onToggleSelectAll = {
                        selectedKeys = if (allSelected) emptyList() else allSelectableKeys
                    },
                    onClose = {
                        multiSelectMode = false
                        selectedKeys = emptyList()
                    },
                )
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
            if ((category >= 0 && category != 2) || query.isNotBlank()) {
                com.mylittleblover.mkwplayer.ui.components.TrackSortBar(
                    selected = sortOrder,
                    includeOstOrder = false,
                    onSelect = { sortOrder = it },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                )
            }
            if (category >= 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            }
            val categoryHeight = 124.dp * LocalDensity.current.fontScale.coerceIn(1f, 1.5f)
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (category < 0 && query.isBlank()) {
                    for (row in 0..2) item(key = "cat-row:$row") {
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
                                            0 -> ResourceArtwork(R.drawable.mode_vs, Modifier.align(Alignment.End).size(40.dp))
                                            1 -> ResourceArtwork(R.drawable.mode_balloon, Modifier.align(Alignment.End).size(40.dp))
                                            2 -> Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.align(Alignment.End).size(40.dp),
                                            ) {
                                                ResourceArtwork(R.drawable.media_free_roam, Modifier.fillMaxSize().padding(7.dp))
                                            }
                                            3 -> ResourceArtwork(R.drawable.media_other_music, Modifier.align(Alignment.End).size(40.dp))
                                            4 -> ResourceArtwork(R.drawable.media_short_sfx, Modifier.align(Alignment.End).size(40.dp))
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
                                                lineHeight = 19.sp,
                                                fontWeight = FontWeight.Bold,
                                                minLines = 2,
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
                items(routes, key = { it.id }) { route -> RouteRow(route, catalog, language, packDirectory, extraCatalog.courseArtwork) { onCup(route.id) } }
                if ((courses.isNotEmpty() || showLockedRainbow) && (category == 0 || sfcCourses.isNotEmpty() || query.isNotBlank())) {
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
                    val courseTitle = catalog?.displayName(code, language) ?: code
                    val selKey = "course:$code"
                    val plays = playStats[code.removeSuffix("_00")]?.playCount ?: 0
                    val subWithPlays = if (plays > 0) {
                        "$interactiveSubtitle · ${stringResource(R.string.play_count_fmt, plays)}"
                    } else {
                        interactiveSubtitle
                    }
                    val favId = code.removeSuffix("_00")
                    val isFav = favId in favorites || code in favorites
                    val spec = TrackMenuSpec(
                        selectionKey = selKey,
                        playlistRef = code,
                        title = courseTitle,
                        subtitle = subWithPlays,
                        artworkName = extraCatalog.courseArtwork[code],
                        fallbackCourseCode = code,
                        favoriteId = code,
                        courseCodeForDetail = code,
                        editorTargetCode = code,
                        infoPairs = listOf(
                            R.string.track_info_category to coursesCatLabel,
                            R.string.track_info_ref to code,
                        ),
                        onPlayNow = { onPlaySelectedRefs(listOf(code)) },
                    )
                    com.mylittleblover.mkwplayer.ui.components.SwipeToTrackActionBox(
                        enabled = !multiSelectMode,
                        onSwipeStartToEnd = if (allowPlayNext) {
                            {
                                if (onPlayNextRefs(listOf(code))) {
                                    scope.launch { snackbarHostState.showSnackbar(addedNextText) }
                                }
                            }
                        } else null,
                        onSwipeEndToStart = { onToggleFavorite(code) },
                        endToStartIcon = if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        endToStartLabel = stringResource(if (isFav) R.string.swipe_action_unfavorite else R.string.swipe_action_favorite),
                    ) {
                        ExtraSquareRow(
                            title = courseTitle,
                            subtitle = subWithPlays,
                            packDirectory = packDirectory,
                            artworkName = extraCatalog.courseArtwork[code],
                            fallbackCourseCode = code,
                            selectionMode = multiSelectMode,
                            selected = selKey in selectedSet,
                            onLongClick = {
                                if (multiSelectMode) toggleSelected(selKey) else menuTarget = spec
                            },
                            onClick = {
                                if (multiSelectMode) toggleSelected(selKey) else onCourse(code)
                            },
                        )
                    }
                }
                if (showLockedRainbow && catalog != null) {
                    item(key = "course:Rainbow:locked") {
                        val toastContext = LocalContext.current
                        val lockedName = stringResource(R.string.media_course_locked_name)
                        val lockedBadge = stringResource(R.string.media_course_locked_badge)
                        val localizedProgress = if (unlockTotal > 0) {
                            stringResource(R.string.media_course_locked_progress, unlockCompleted, unlockTotal)
                        } else {
                            unlockProgress?.takeIf { it.isNotBlank() }
                        }
                        val lockedHint = if (localizedProgress.isNullOrBlank()) {
                            lockedBadge
                        } else {
                            stringResource(R.string.media_course_locked_hint, localizedProgress)
                        }
                        Surface(
                            onClick = {
                                android.widget.Toast.makeText(toastContext, lockedHint, android.widget.Toast.LENGTH_SHORT).show()
                            },
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(16.dp),
                            tonalElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Lock,
                                        contentDescription = lockedBadge,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = lockedName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                        ) {
                                            Text(
                                                text = lockedBadge,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            )
                                        }
                                    }
                                    if (!localizedProgress.isNullOrBlank()) {
                                        Text(
                                            text = localizedProgress,
                                            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    if (unlockTotal > 0) {
                                        androidx.compose.material3.LinearProgressIndicator(
                                            progress = { (unlockCompleted.toFloat() / unlockTotal.coerceAtLeast(1)).coerceIn(0f, 1f) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 2.dp)
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(999.dp)),
                                        )
                                    }
                                }
                            }
                        }
                    }
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
                    val medley = extraMedleyCatalog.forTarget(track.id).firstOrNull()
                    val trackTitle = track.localizedName(language)
                    val selKey = "sfc:${track.id}"
                    val refRaw = PlaylistItemRef.SfcTrack(track.id).raw
                    val plays = playStats[refRaw]?.playCount ?: 0
                    val subWithPlays = if (plays > 0) {
                        "$interactiveSubtitle · ${stringResource(R.string.play_count_fmt, plays)}"
                    } else {
                        interactiveSubtitle
                    }
                    val isFav = refRaw in favorites
                    val spec = TrackMenuSpec(
                        selectionKey = selKey,
                        playlistRef = refRaw,
                        title = trackTitle,
                        subtitle = subWithPlays,
                        artworkName = track.artwork,
                        favoriteId = refRaw,
                        editorTargetCode = "extra:${track.id}",
                        infoPairs = listOf(
                            R.string.track_info_category to sfcCatLabel,
                            R.string.track_info_ref to track.id,
                        ),
                        onPlayNow = { onSfcCourse(track) },
                    )
                    com.mylittleblover.mkwplayer.ui.components.SwipeToTrackActionBox(
                        enabled = !multiSelectMode,
                        onSwipeStartToEnd = if (allowPlayNext) {
                            {
                                if (onPlayNextRefs(listOf(refRaw))) {
                                    scope.launch { snackbarHostState.showSnackbar(addedNextText) }
                                }
                            }
                        } else null,
                        onSwipeEndToStart = { onToggleFavorite(refRaw) },
                        endToStartIcon = if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        endToStartLabel = stringResource(if (isFav) R.string.swipe_action_unfavorite else R.string.swipe_action_favorite),
                    ) {
                        ExtraSquareRow(
                            title = trackTitle,
                            subtitle = subWithPlays,
                            packDirectory = packDirectory,
                            artworkName = track.artwork,
                            onSecondaryAction = { onOpenExtraFlowEditor(track.id) },
                            secondaryActionDescription = stringResource(R.string.discovery_open_flow_editor),
                            onAction = medley?.let { entry ->
                                {
                                    onPlayExtraMedley(entry.medley.id, entry.localizedLabel(language, trackTitle), extraSuiteLabel, entry.artwork ?: track.artwork)
                                }
                            },
                            actionContentDescription = extraSuiteLabel,
                            selectionMode = multiSelectMode,
                            selected = selKey in selectedSet,
                            onLongClick = {
                                if (multiSelectMode) toggleSelected(selKey) else menuTarget = spec
                            },
                            onClick = {
                                if (multiSelectMode) toggleSelected(selKey) else onSfcCourse(track)
                            },
                        )
                    }
                }
                items(battleTracks, key = { "battle:${it.id}" }) { track ->
                    val medley = extraMedleyCatalog.forTarget(track.id).firstOrNull()
                    val trackTitle = track.localizedName(language)
                    val selKey = "battle:${track.id}"
                    val refRaw = PlaylistItemRef.BattleTrack(track.id).raw
                    val plays = playStats[refRaw]?.playCount ?: 0
                    val subWithPlays = if (plays > 0) {
                        "$interactiveSubtitle · ${stringResource(R.string.play_count_fmt, plays)}"
                    } else {
                        interactiveSubtitle
                    }
                    val isFav = refRaw in favorites
                    val spec = TrackMenuSpec(
                        selectionKey = selKey,
                        playlistRef = refRaw,
                        title = trackTitle,
                        subtitle = subWithPlays,
                        artworkName = track.artwork,
                        favoriteId = refRaw,
                        editorTargetCode = "extra:${track.id}",
                        infoPairs = listOf(
                            R.string.track_info_category to battleCatLabel,
                            R.string.track_info_ref to track.id,
                        ),
                        onPlayNow = { onBattleTrack(track) },
                    )
                    com.mylittleblover.mkwplayer.ui.components.SwipeToTrackActionBox(
                        enabled = !multiSelectMode,
                        onSwipeStartToEnd = if (allowPlayNext) {
                            {
                                if (onPlayNextRefs(listOf(refRaw))) {
                                    scope.launch { snackbarHostState.showSnackbar(addedNextText) }
                                }
                            }
                        } else null,
                        onSwipeEndToStart = { onToggleFavorite(refRaw) },
                        endToStartIcon = if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        endToStartLabel = stringResource(if (isFav) R.string.swipe_action_unfavorite else R.string.swipe_action_favorite),
                    ) {
                        ExtraSquareRow(
                            title = trackTitle,
                            subtitle = subWithPlays,
                            packDirectory = packDirectory,
                            artworkName = track.artwork,
                            onSecondaryAction = { onOpenExtraFlowEditor(track.id) },
                            secondaryActionDescription = stringResource(R.string.discovery_open_flow_editor),
                            onAction = medley?.let { entry ->
                                {
                                    onPlayExtraMedley(entry.medley.id, entry.localizedLabel(language, trackTitle), extraSuiteLabel, entry.artwork ?: track.artwork)
                                }
                            },
                            actionContentDescription = extraSuiteLabel,
                            selectionMode = multiSelectMode,
                            selected = selKey in selectedSet,
                            onLongClick = {
                                if (multiSelectMode) toggleSelected(selKey) else menuTarget = spec
                            },
                            onClick = {
                                if (multiSelectMode) toggleSelected(selKey) else onBattleTrack(track)
                            },
                        )
                    }
                }
                if (otherItems.isNotEmpty()) {
                    val linearOther = otherItems.filterNot { it is ExtraMusicCatalog.OtherMusicItem.InteractiveTitle }
                    val linearEntries = linearOther.map { item -> extraOtherToQueueEntry(item, language, otherCategoryLabel) }
                    if (category == 3 && linearEntries.isNotEmpty()) {
                        item(key = "other-actions") {
                            ExtraQueueActionsRow(
                                count = otherItems.size,
                                onPlayAll = { onPlayExtraQueue(linearEntries, 0, com.mylittleblover.mkwplayer.player.LinearQueue.RepeatMode.LIST, false) },
                                onShuffle = { onPlayExtraQueue(linearEntries, 0, com.mylittleblover.mkwplayer.player.LinearQueue.RepeatMode.LIST, true) },
                            )
                        }
                    }
                    items(otherItems, key = { "other:${it.id}" }) { item ->
                        val itemTitle = item.localizedName(language)
                        val selKey = "other:${item.id}"
                        val refRaw = PlaylistItemRef.ExtraTrack(item.id).raw
                        val plays = playStats[refRaw]?.playCount ?: 0
                        val baseSub = if (item is ExtraMusicCatalog.OtherMusicItem.InteractiveTitle) {
                            interactiveSubtitle
                        } else {
                            buildList {
                                add(stringResource(R.string.discovery_normal_playback))
                                if (item.durationMs > 0L) add(formatExtraDuration(item.durationMs))
                            }.joinToString(" · ")
                        }
                        val sub = if (plays > 0) {
                            listOfNotNull(baseSub.takeIf { it.isNotBlank() }, stringResource(R.string.play_count_fmt, plays)).joinToString(" · ")
                        } else {
                            baseSub
                        }
                        val isFav = refRaw in favorites
                        val medley = extraMedleyCatalog.forTarget(item.id).firstOrNull()
                        val playAction: () -> Unit = {
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
                        }
                        val spec = TrackMenuSpec(
                            selectionKey = selKey,
                            playlistRef = refRaw,
                            title = itemTitle,
                            subtitle = sub,
                            artworkName = item.artwork,
                            favoriteId = refRaw,
                            editorTargetCode = if (item is ExtraMusicCatalog.OtherMusicItem.InteractiveTitle) "extra:${item.id}" else null,
                            infoPairs = buildList {
                                add(R.string.track_info_category to otherCategoryLabel)
                                if (item.durationMs > 0L) add(R.string.track_info_duration to formatExtraDuration(item.durationMs))
                                add(R.string.track_info_ref to item.id)
                            },
                            onPlayNow = playAction,
                        )
                        com.mylittleblover.mkwplayer.ui.components.SwipeToTrackActionBox(
                            enabled = !multiSelectMode,
                            onSwipeStartToEnd = if (allowPlayNext) {
                                {
                                    if (onPlayNextRefs(listOf(refRaw))) {
                                        scope.launch { snackbarHostState.showSnackbar(addedNextText) }
                                    }
                                }
                            } else null,
                            onSwipeEndToStart = { onToggleFavorite(refRaw) },
                            endToStartIcon = if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            endToStartLabel = stringResource(if (isFav) R.string.swipe_action_unfavorite else R.string.swipe_action_favorite),
                        ) {
                            ExtraSquareRow(
                                title = itemTitle,
                                subtitle = sub,
                                packDirectory = packDirectory,
                                artworkName = item.artwork,
                                onSecondaryAction = if (item is ExtraMusicCatalog.OtherMusicItem.InteractiveTitle) {
                                    { onOpenExtraFlowEditor(item.id) }
                                } else null,
                                secondaryActionDescription = stringResource(R.string.discovery_open_flow_editor),
                                onAction = medley?.let { entry ->
                                    {
                                        onPlayExtraMedley(entry.medley.id, entry.localizedLabel(language, itemTitle), extraSuiteLabel, entry.artwork ?: item.artwork)
                                    }
                                },
                                actionContentDescription = extraSuiteLabel,
                                selectionMode = multiSelectMode,
                                selected = selKey in selectedSet,
                                onLongClick = {
                                    if (multiSelectMode) toggleSelected(selKey) else menuTarget = spec
                                },
                                onClick = {
                                    if (multiSelectMode) toggleSelected(selKey) else playAction()
                                },
                            )
                        }
                    }
                }
                val otherExtraMedleys = if (category == 3) {
                    extraMedleyCatalog.entries.filter {
                        it.targetType == com.mylittleblover.mkwplayer.interactive.ExtraMedleyCatalog.TargetType.OTHER &&
                            ExtraMusicCatalog.isVisible(it.targetId, unlocked)
                    }
                } else emptyList()
                if (otherExtraMedleys.isNotEmpty()) {
                    item(key = "extra-medleys-header") {
                        Text(
                            stringResource(R.string.extra_medley_section_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    items(otherExtraMedleys, key = { "extra_medley:${it.medley.id}" }) { entry ->
                        val title = entry.localizedLabel(language, entry.medley.courseCode)
                        ExtraSquareRow(
                            title = title,
                            subtitle = listOf(extraSuiteLabel, formatExtraDuration(entry.medley.durationMs)).joinToString(" · "),
                            packDirectory = packDirectory,
                            artworkName = entry.artwork,
                            onAction = { onPlayExtraMedley(entry.medley.id, title, extraSuiteLabel, entry.artwork) },
                            actionContentDescription = extraSuiteLabel,
                            onClick = { onPlayExtraMedley(entry.medley.id, title, extraSuiteLabel, entry.artwork) },
                        )
                    }
                }
                if (shortItems.isNotEmpty()) {
                    val shortEntries = shortItems.map { item -> extraSimpleToQueueEntry(item, language, shortLabel) }
                    if (category == 4) {
                        item(key = "short-actions") {
                            ExtraQueueActionsRow(
                                count = shortItems.size,
                                onPlayAll = { onPlayExtraQueue(shortEntries, 0, com.mylittleblover.mkwplayer.player.LinearQueue.RepeatMode.LIST, false) },
                                onShuffle = { onPlayExtraQueue(shortEntries, 0, com.mylittleblover.mkwplayer.player.LinearQueue.RepeatMode.LIST, true) },
                            )
                        }
                    }
                    itemsIndexed(shortItems, key = { _, item -> "short:${item.id}" }) { idx, item ->
                        val baseSub = if (item.durationMs > 0L) formatExtraDuration(item.durationMs) else ""
                        val itemTitle = item.localizedName(language)
                        val selKey = "short:${item.id}"
                        val refRaw = PlaylistItemRef.ExtraTrack(item.id).raw
                        val plays = playStats[refRaw]?.playCount ?: 0
                        val sub = if (plays > 0) {
                            listOfNotNull(baseSub.takeIf { it.isNotBlank() }, stringResource(R.string.play_count_fmt, plays)).joinToString(" · ")
                        } else {
                            baseSub
                        }
                        val isFav = refRaw in favorites
                        val playAction: () -> Unit = {
                            val repeat = if (item.asset.loop) {
                                com.mylittleblover.mkwplayer.player.LinearQueue.RepeatMode.SINGLE
                            } else {
                                com.mylittleblover.mkwplayer.player.LinearQueue.RepeatMode.LIST
                            }
                            onPlayExtraQueue(shortEntries, idx, repeat, false)
                        }
                        val spec = TrackMenuSpec(
                            selectionKey = selKey,
                            playlistRef = refRaw,
                            title = itemTitle,
                            subtitle = if (sub.isNotBlank()) sub else shortLabel,
                            artworkName = item.artwork,
                            favoriteId = refRaw,
                            infoPairs = buildList {
                                add(R.string.track_info_category to shortLabel)
                                if (item.durationMs > 0L) add(R.string.track_info_duration to formatExtraDuration(item.durationMs))
                                add(R.string.track_info_ref to item.id)
                            },
                            onPlayNow = playAction,
                        )
                        com.mylittleblover.mkwplayer.ui.components.SwipeToTrackActionBox(
                            enabled = !multiSelectMode,
                            onSwipeStartToEnd = if (allowPlayNext) {
                                {
                                    if (onPlayNextRefs(listOf(refRaw))) {
                                        scope.launch { snackbarHostState.showSnackbar(addedNextText) }
                                    }
                                }
                            } else null,
                            onSwipeEndToStart = { onToggleFavorite(refRaw) },
                            endToStartIcon = if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            endToStartLabel = stringResource(if (isFav) R.string.swipe_action_unfavorite else R.string.swipe_action_favorite),
                        ) {
                            ExtraSquareRow(
                                title = itemTitle,
                                subtitle = sub,
                                packDirectory = packDirectory,
                                artworkName = item.artwork,
                                onAction = { onAuditionExtra(shortEntries[idx]) },
                                actionContentDescription = stringResource(R.string.discovery_audition),
                                selectionMode = multiSelectMode,
                                selected = selKey in selectedSet,
                                onLongClick = {
                                    if (multiSelectMode) toggleSelected(selKey) else menuTarget = spec
                                },
                                onClick = {
                                    if (multiSelectMode) toggleSelected(selKey) else playAction()
                                },
                            )
                        }
                    }
                }
            if (nmItems.isNotEmpty()) {
                val nmEntries = nmItems.map { item -> extraSimpleToQueueEntry(item, language, nmLabel) }
                if (category == 5) {
                    item(key = "nm-actions") {
                        ExtraQueueActionsRow(
                            count = nmItems.size,
                            onPlayAll = { onPlayExtraQueue(nmEntries, 0, com.mylittleblover.mkwplayer.player.LinearQueue.RepeatMode.LIST, false) },
                            onShuffle = { onPlayExtraQueue(nmEntries, 0, com.mylittleblover.mkwplayer.player.LinearQueue.RepeatMode.LIST, true) },
                        )
                    }
                }
                items(nmItems, key = { "nm:${it.id}" }) { item ->
                    val baseSub = if (item.durationMs > 0L) formatExtraDuration(item.durationMs) else ""
                    val itemTitle = item.localizedName(language)
                    val selKey = "nm:${item.id}"
                    val refRaw = PlaylistItemRef.ExtraTrack(item.id).raw
                    val plays = playStats[refRaw]?.playCount ?: 0
                    val sub = if (plays > 0) {
                        listOfNotNull(baseSub.takeIf { it.isNotBlank() }, stringResource(R.string.play_count_fmt, plays)).joinToString(" · ")
                    } else {
                        baseSub
                    }
                    val isFav = refRaw in favorites
                    val idx = nmItems.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
                    val playAction: () -> Unit = {
                        onPlayExtraQueue(nmEntries, idx, com.mylittleblover.mkwplayer.player.LinearQueue.RepeatMode.LIST, false)
                    }
                    val spec = TrackMenuSpec(
                        selectionKey = selKey,
                        playlistRef = refRaw,
                        title = itemTitle,
                        subtitle = if (sub.isNotBlank()) sub else nmLabel,
                        artworkName = item.artwork,
                        favoriteId = refRaw,
                        infoPairs = buildList {
                            add(R.string.track_info_category to nmLabel)
                            if (item.durationMs > 0L) add(R.string.track_info_duration to formatExtraDuration(item.durationMs))
                            add(R.string.track_info_ref to item.id)
                        },
                        onPlayNow = playAction,
                    )
                    com.mylittleblover.mkwplayer.ui.components.SwipeToTrackActionBox(
                        enabled = !multiSelectMode,
                        onSwipeStartToEnd = if (allowPlayNext) {
                            {
                                if (onPlayNextRefs(listOf(refRaw))) {
                                    scope.launch { snackbarHostState.showSnackbar(addedNextText) }
                                }
                            }
                        } else null,
                        onSwipeEndToStart = { onToggleFavorite(refRaw) },
                        endToStartIcon = if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        endToStartLabel = stringResource(if (isFav) R.string.swipe_action_unfavorite else R.string.swipe_action_favorite),
                    ) {
                        ExtraSquareRow(
                            title = itemTitle,
                            subtitle = sub,
                            packDirectory = packDirectory,
                            artworkName = item.artwork,
                            selectionMode = multiSelectMode,
                            selected = selKey in selectedSet,
                            onLongClick = {
                                if (multiSelectMode) toggleSelected(selKey) else menuTarget = spec
                            },
                            onClick = {
                                if (multiSelectMode) toggleSelected(selKey) else playAction()
                            },
                        )
                    }
                }
            }
            if (category == 2) {
                if (!jukebox.isEmpty) {
                    item(key = "jukebox-head") {
                        Column(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                stringResource(R.string.jukebox_games_summary, jukebox.games.size, jukebox.trackCount),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                FilledTonalButton(onClick = { onPlayAllJukebox(false) }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.jukebox_play_all), maxLines = 1)
                                }
                                OutlinedButton(onClick = { onPlayAllJukebox(true) }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Filled.Shuffle, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.jukebox_shuffle), maxLines = 1)
                                }
                            }
                        }
                    }
                }
                if (query.isBlank()) {
                    val decades = jukebox.games.mapNotNull { g -> g.year.takeIf { it > 0 }?.let { (it / 10) * 10 } }.distinct().sorted()
                    val activeDecade = selectedJukeboxDecade?.takeIf { it in decades }
                    if (decades.size > 1) {
                        item(key = "jukebox-chronology-index") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item(key = "era-all") {
                                    FilterChip(
                                        selected = activeDecade == null,
                                        onClick = { selectedJukeboxDecade = null },
                                        label = { Text(stringResource(R.string.jukebox_all_eras)) },
                                    )
                                }
                                items(decades, key = { "era:$it" }) { decade ->
                                    FilterChip(
                                        selected = activeDecade == decade,
                                        onClick = {
                                            selectedJukeboxDecade = if (activeDecade == decade) null else decade
                                        },
                                        label = { Text(stringResource(R.string.jukebox_decade_label, decade)) },
                                    )
                                }
                            }
                        }
                    }
                    val shownGames = if (activeDecade == null) jukebox.games else {
                        jukebox.games.filter { g -> g.year > 0 && (g.year / 10) * 10 == activeDecade }
                    }
                    items(shownGames, key = { "game:${it.id}" }) { game ->
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
                            val selKey = "jb:${item.key}"
                            val refRaw = PlaylistItemRef.Jukebox(item.key).raw
                            val plays = playStats[refRaw]?.playCount ?: 0
                            val isFav = refRaw in favorites
                            val art = item.ostIndex?.let { "t_%03d.webp".format(java.util.Locale.ROOT, it) }
                            val gameTitle = game.localizedName(language)
                            val playAction: () -> Unit = { onPlayJukeboxTrack(game, idx) }
                            val spec = TrackMenuSpec(
                                selectionKey = selKey,
                                playlistRef = refRaw,
                                title = item.localizedTitle(language),
                                subtitle = listOfNotNull(item.localizedArrangement(language), gameTitle).joinToString(" · "),
                                artworkName = art,
                                favoriteId = refRaw,
                                infoPairs = buildList {
                                    add(R.string.track_info_game to gameTitle)
                                    if (item.durationMs > 0L) add(R.string.track_info_duration to item.durationFormatted)
                                    item.ostIndex?.let { add(R.string.track_info_ost to "#$it") }
                                    add(R.string.track_info_ref to item.key)
                                },
                                onPlayNow = playAction,
                                onPlayNextDirect = { onPlayJukeboxNext(item, game) },
                            )
                            com.mylittleblover.mkwplayer.ui.components.SwipeToTrackActionBox(
                                enabled = !multiSelectMode,
                                onSwipeStartToEnd = if (allowPlayNext) {
                                    {
                                        onPlayJukeboxNext(item, game)
                                        scope.launch { snackbarHostState.showSnackbar(addedNextText) }
                                    }
                                } else null,
                                onSwipeEndToStart = { onToggleFavorite(refRaw) },
                                endToStartIcon = if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                endToStartLabel = stringResource(if (isFav) R.string.swipe_action_unfavorite else R.string.swipe_action_favorite),
                            ) {
                                JukeboxTrackRow(
                                    item = item,
                                    language = language,
                                    subtitleExtra = gameTitle,
                                    playCount = plays,
                                    packDirectory = packDirectory,
                                    playing = activeTrackKey == item.key,
                                    allowPlayNext = allowPlayNext,
                                    selectionMode = multiSelectMode,
                                    selected = selKey in selectedSet,
                                    onLongClick = {
                                        if (multiSelectMode) toggleSelected(selKey) else menuTarget = spec
                                    },
                                    onOpenMenu = { menuTarget = spec },
                                    onPlay = {
                                        if (multiSelectMode) toggleSelected(selKey) else playAction()
                                    },
                                    onPlayNext = {
                                        onPlayJukeboxNext(item, game)
                                        scope.launch { snackbarHostState.showSnackbar(addedNextText) }
                                    },
                                    onPlayFromHere = playAction,
                                )
                            }
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
                        val selKey = "jb:${item.key}"
                        val refRaw = PlaylistItemRef.Jukebox(item.key).raw
                        val plays = playStats[refRaw]?.playCount ?: 0
                        val isFav = refRaw in favorites
                        val art = item.ostIndex?.let { "t_%03d.webp".format(java.util.Locale.ROOT, it) }
                        val gameTitle = game.localizedName(language)
                        val playAction: () -> Unit = { onPlayJukeboxTrack(game, idx) }
                        val spec = TrackMenuSpec(
                            selectionKey = selKey,
                            playlistRef = refRaw,
                            title = item.localizedTitle(language),
                            subtitle = listOfNotNull(item.localizedArrangement(language), gameTitle).joinToString(" · "),
                            artworkName = art,
                            favoriteId = refRaw,
                            infoPairs = buildList {
                                add(R.string.track_info_game to gameTitle)
                                if (item.durationMs > 0L) add(R.string.track_info_duration to item.durationFormatted)
                                item.ostIndex?.let { add(R.string.track_info_ost to "#$it") }
                                add(R.string.track_info_ref to item.key)
                            },
                            onPlayNow = playAction,
                            onPlayNextDirect = { onPlayJukeboxNext(item, game) },
                        )
                        com.mylittleblover.mkwplayer.ui.components.SwipeToTrackActionBox(
                            enabled = !multiSelectMode,
                            onSwipeStartToEnd = if (allowPlayNext) {
                                {
                                    onPlayJukeboxNext(item, game)
                                    scope.launch { snackbarHostState.showSnackbar(addedNextText) }
                                }
                            } else null,
                            onSwipeEndToStart = { onToggleFavorite(refRaw) },
                            endToStartIcon = if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            endToStartLabel = stringResource(if (isFav) R.string.swipe_action_unfavorite else R.string.swipe_action_favorite),
                        ) {
                            JukeboxTrackRow(
                                item = item,
                                language = language,
                                subtitleExtra = gameTitle,
                                playCount = plays,
                                packDirectory = packDirectory,
                                playing = activeTrackKey == item.key,
                                allowPlayNext = allowPlayNext,
                                selectionMode = multiSelectMode,
                                selected = selKey in selectedSet,
                                onLongClick = {
                                    if (multiSelectMode) toggleSelected(selKey) else menuTarget = spec
                                },
                                onOpenMenu = { menuTarget = spec },
                                onPlay = {
                                    if (multiSelectMode) toggleSelected(selKey) else playAction()
                                },
                                onPlayNext = {
                                    onPlayJukeboxNext(item, game)
                                    scope.launch { snackbarHostState.showSnackbar(addedNextText) }
                                },
                                onPlayFromHere = playAction,
                            )
                        }
                    }
                }
            }
            val emptyHere = when (category) {
                0 -> courses.isEmpty() && sfcCourses.isEmpty()
                1 -> battleTracks.isEmpty()
                2 -> if (query.isBlank()) jukebox.isEmpty else (jukeboxSearch.games.isEmpty() && jukeboxSearch.trackHits.isEmpty())
                3 -> otherItems.isEmpty()
                4 -> shortItems.isEmpty()
                5 -> nmItems.isEmpty()
                else -> category >= 0 && routes.isEmpty() && courses.isEmpty()
            }
            if (emptyHere) {
                item {
                    Text(
                        stringResource(if (category == 2 && query.isNotBlank()) R.string.jukebox_search_empty else R.string.discovery_empty),
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
            AnimatedVisibility(visible = multiSelectMode) {
                val chosenRefs = selectedKeys.mapNotNull { visibleRefByKey[it] }
                MultiSelectActionBar(
                    hasSelection = chosenRefs.isNotEmpty(),
                    allowPlayNext = allowPlayNext,
                    onPlaySelected = {
                        if (chosenRefs.isNotEmpty() && onPlaySelectedRefs(chosenRefs)) {
                            multiSelectMode = false
                            selectedKeys = emptyList()
                        }
                    },
                    onPlayNextSelected = {
                        if (chosenRefs.isNotEmpty() && onPlayNextRefs(chosenRefs)) {
                            scope.launch { snackbarHostState.showSnackbar(addedNextText) }
                        }
                        multiSelectMode = false
                        selectedKeys = emptyList()
                    },
                    onAddToQueueSelected = {
                        if (chosenRefs.isNotEmpty() && onAddToQueueRefs(chosenRefs)) {
                            scope.launch { snackbarHostState.showSnackbar(addedQueueText) }
                        }
                        multiSelectMode = false
                        selectedKeys = emptyList()
                    },
                    onAddToPlaylistSelected = {
                        if (chosenRefs.isNotEmpty()) playlistPickerRefs = chosenRefs
                    },
                )
            }
        }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun CourseMosaicBox(
    route: OfficialRoute,
    packDirectory: java.io.File?,
    courseArtwork: Map<String, String>,
    modifier: Modifier = Modifier,
    badgeSize: androidx.compose.ui.unit.Dp = 36.dp,
    badgePadding: androidx.compose.ui.unit.Dp = 6.dp,
) {
    val emblem = com.mylittleblover.mkwplayer.ui.art.CupArt.routeEmblem(route.id)
    val mosaicCodes = route.stops.take(4).map { it.stageCode }
    Box(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (rowIdx in 0..1) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (colIdx in 0..1) {
                        val tileCode = mosaicCodes.getOrNull(rowIdx * 2 + colIdx)
                        if (tileCode != null) {
                            com.mylittleblover.mkwplayer.ui.art.CourseCover(
                                code = tileCode,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                showIcon = false,
                                packDirectory = packDirectory,
                                artworkName = courseArtwork[tileCode] ?: courseArtwork[tileCode.removeSuffix("_00")],
                            )
                        } else {
                            Box(Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant))
                        }
                    }
                }
            }
        }
        emblem?.let {
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shadowElevation = 2.dp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(badgePadding).size(badgeSize),
            ) {
                ResourceArtwork(it, Modifier.fillMaxSize().padding(badgePadding / 2))
            }
        }
    }
}

@Composable
private fun CupMosaicCard(
    route: OfficialRoute,
    language: String,
    packDirectory: java.io.File? = null,
    courseArtwork: Map<String, String> = emptyMap(),
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val cardInteraction = remember { MutableInteractionSource() }
    val pressScale = com.mylittleblover.mkwplayer.ui.rememberPressScale(cardInteraction, pressedScale = 0.97f)
    Card(
        onClick = onClick,
        interactionSource = cardInteraction,
        modifier = modifier.graphicsLayer { scaleX = pressScale; scaleY = pressScale },
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CourseMosaicBox(
                route = route,
                packDirectory = packDirectory,
                courseArtwork = courseArtwork,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.35f)
                    .clip(RoundedCornerShape(12.dp)),
            )
            Column(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    route.displayName(language),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.map_route_stops, route.stops.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun RouteRow(
    route: OfficialRoute,
    catalog: BrowseCatalog?,
    language: String,
    packDirectory: java.io.File? = null,
    courseArtwork: Map<String, String> = emptyMap(),
    onClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CourseMosaicBox(
                route = route,
                packDirectory = packDirectory,
                courseArtwork = courseArtwork,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
                badgeSize = 24.dp,
                badgePadding = 3.dp,
            )
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
internal fun DiscoveryCup(
    route: OfficialRoute?,
    catalog: BrowseCatalog?,
    language: String,
    installedJukebox: List<Pair<JukeboxCatalog.TrackItem, JukeboxCatalog.Game>> = emptyList(),
    packDirectory: java.io.File? = null,
    extraCatalog: ExtraMusicCatalog.Catalog = ExtraMusicCatalog.Catalog(),
    inspectOfficialRoute: (OfficialRoute, Map<Int, String>, RoadJukeboxCatalog.RoadMusicRuleMode) -> RoadJukeboxCatalog.RouteValidationReport? = { _, _, _ -> null },
    onPlayRoute: (OfficialRoute, Boolean, Map<Int, String>, RoadJukeboxCatalog.RoadMusicRuleMode) -> Unit = { _, _, _, _ -> },
    onMap: (String) -> Unit,
    onCourse: (String) -> Unit,
) {
    var loopRoute by rememberSaveable(route?.id) { mutableStateOf(false) }
    var ruleMode by rememberSaveable(route?.id) { mutableStateOf(RoadJukeboxCatalog.RoadMusicRuleMode.AUTO) }
    val sessionOverrides = remember(route?.id) { mutableStateMapOf<Int, String>() }
    var pickingLegIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    val report = remember(route, sessionOverrides.toMap(), ruleMode, packDirectory, installedJukebox) {
        route?.let { inspectOfficialRoute(it, sessionOverrides.toMap(), ruleMode) }
    }
    val openingItem = remember(route, extraCatalog) {
        val r = route ?: return@remember null
        val openingId = ExtraMusicCatalog.openingIdForOfficialRoute(r.id, r.kind) ?: return@remember null
        extraCatalog.otherMusic.filterIsInstance<ExtraMusicCatalog.OtherMusicItem.OverlaidOpening>()
            .firstOrNull { it.id == openingId }
    }
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
                        if (report?.canPlay == false) {
                            Text(
                                report.blockingReason ?: stringResource(R.string.route_blocked_incomplete_pool),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onPlayRoute(route, loopRoute, sessionOverrides.toMap(), ruleMode) },
                                enabled = report?.canPlay != false,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.route_play))
                            }
                            OutlinedButton(onClick = { onMap(route.id) }) {
                                Icon(Icons.Filled.Map, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.discovery_view_map))
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().clickable { loopRoute = !loopRoute },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                stringResource(R.string.route_loop_whole),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f).padding(end = 12.dp),
                            )
                            Switch(checked = loopRoute, onCheckedChange = { loopRoute = it })
                        }
                        RoadMusicRuleModeSelector(
                            selectedMode = ruleMode,
                            officialKind = route.kind,
                            onSelectMode = {
                                ruleMode = it
                                sessionOverrides.clear()
                            },
                        )
                    }
                }
            }
            item { Text(stringResource(R.string.discovery_course_order), style = MaterialTheme.typography.titleLarge) }
            if (openingItem != null) {
                item(key = "opening:${openingItem.id}") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Text(
                                stringResource(R.string.route_opening_badge),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            PackArtwork.Thumbnail(
                                packDirectory,
                                listOf(openingItem.artwork),
                                Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            ) {
                                Icon(Icons.Outlined.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    openingItem.localizedName(language),
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (openingItem.durationMs > 0L) {
                                    Text(
                                        formatExtraDuration(openingItem.durationMs),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            itemsIndexed(route.stops, key = { _, stop -> stop.id }) { idx, stop ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Card(onClick = { onCourse(stop.stageCode) }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(stop.ordinal.toString(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            StageArt.resolve(stop.stageCode)?.let { ResourceArtwork(it, Modifier.size(52.dp)) }
                            Text(catalog?.displayName(stop.stageCode, language) ?: stop.stageCode, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    val leg = report?.legs?.getOrNull(idx)
                    if (leg != null) {
                        val fromTitle = catalog?.displayName(leg.from, language) ?: leg.from
                        val toTitle = catalog?.displayName(leg.to, language) ?: leg.to
                        val summary = edgeResolutionSummary(leg.resolution, language)
                        Surface(
                            onClick = { pickingLegIndex = idx },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(Icons.Outlined.MusicNote, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "${stringResource(R.string.route_road_prefix)} $fromTitle → $toTitle",
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        summary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (leg.resolution.isMissingOrIncomplete()) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (leg.resolution.hasKnownPool()) {
                                    Icon(Icons.Filled.Tune, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    val pickIdx = pickingLegIndex
    val activeLeg = if (pickIdx != null) report?.legs?.getOrNull(pickIdx) else null
    if (activeLeg != null && pickIdx != null) {
        val fromTitle = catalog?.displayName(activeLeg.from, language) ?: activeLeg.from
        val toTitle = catalog?.displayName(activeLeg.to, language) ?: activeLeg.to
        RouteEdgeMusicPickerSheet(
            legTitle = "$fromTitle → $toTitle",
            leg = activeLeg,
            installedJukebox = installedJukebox,
            allowCustomSubstitute = false,
            allowMute = false,
            sessionHint = true,
            language = language,
            packDirectory = packDirectory,
            currentSelectedKey = sessionOverrides[pickIdx],
            currentMuted = false,
            onSelectAuto = { sessionOverrides.remove(pickIdx) },
            onSelectMuted = {},
            onSelectTrackKey = { trackKey -> sessionOverrides[pickIdx] = trackKey },
            onDismiss = { pickingLegIndex = null },
        )
    }
}

private data class SmartPlaylistEntry(
    val rawRef: String,
    val title: String,
    val subtitle: String,
    val artworkName: String?,
    val fallbackCourseCode: String?,
    val playCount: Int,
)

private fun canonicalStatIdForRef(rawId: String): String = when (val ref = PlaylistItemRef.parse(rawId)) {
    is PlaylistItemRef.Course -> ref.logicalCode
    else -> ref.raw
}

private fun resolveSmartPlaylistEntry(
    rawId: String,
    catalog: BrowseCatalog?,
    unlocked: Boolean,
    language: String,
    extraCatalog: ExtraMusicCatalog.Catalog,
    jukeboxItemsByKey: Map<String, JukeboxCatalog.TrackItem>,
    gamesById: Map<String, JukeboxCatalog.Game>,
    visibleCourseSet: Set<String>,
    coursesLabel: String,
    sfcLabel: String,
    battleLabel: String,
    freeLabel: String,
    otherLabel: String,
    soundsLabel: String,
    nmLabel: String,
    playCount: Int,
): SmartPlaylistEntry? {
    return when (val ref = PlaylistItemRef.parse(rawId)) {
        is PlaylistItemRef.Course -> {
            val logical = ref.logicalCode
            if (logical !in visibleCourseSet && rawId !in visibleCourseSet) null
            else SmartPlaylistEntry(
                rawRef = logical,
                title = catalog?.displayName(logical, language) ?: logical,
                subtitle = coursesLabel,
                artworkName = extraCatalog.courseArtwork[rawId] ?: extraCatalog.courseArtwork[logical],
                fallbackCourseCode = logical,
                playCount = playCount,
            )
        }
        is PlaylistItemRef.Jukebox -> {
            val item = jukeboxItemsByKey[ref.trackKey] ?: return null
            val gameName = gamesById[item.gameId]?.localizedName(language) ?: freeLabel
            SmartPlaylistEntry(
                rawRef = ref.raw,
                title = item.localizedDisplayName(language),
                subtitle = gameName,
                artworkName = item.ostIndex?.let { "t_%03d.webp".format(java.util.Locale.ROOT, it) },
                fallbackCourseCode = null,
                playCount = playCount,
            )
        }
        is PlaylistItemRef.SfcTrack -> {
            if (!ExtraMusicCatalog.isVisible(ref.trackId, unlocked)) null
            else {
                val track = extraCatalog.sfcCourses.firstOrNull { it.id == ref.trackId } ?: return null
                SmartPlaylistEntry(
                    rawRef = ref.raw,
                    title = track.localizedName(language),
                    subtitle = sfcLabel,
                    artworkName = track.artwork,
                    fallbackCourseCode = null,
                    playCount = playCount,
                )
            }
        }
        is PlaylistItemRef.BattleTrack -> {
            if (!ExtraMusicCatalog.isVisible(ref.trackId, unlocked)) null
            else {
                val track = extraCatalog.battleTracks.firstOrNull { it.id == ref.trackId } ?: return null
                SmartPlaylistEntry(
                    rawRef = ref.raw,
                    title = track.localizedName(language),
                    subtitle = battleLabel,
                    artworkName = track.artwork,
                    fallbackCourseCode = null,
                    playCount = playCount,
                )
            }
        }
        is PlaylistItemRef.ExtraTrack -> {
            if (!ExtraMusicCatalog.isVisible(ref.extraId, unlocked)) null
            else {
                val other = extraCatalog.otherMusic.firstOrNull { it.id == ref.extraId }
                val simple = extraCatalog.shortSounds.firstOrNull { it.id == ref.extraId }
                    ?: extraCatalog.nintendoMusicSpecial.firstOrNull { it.id == ref.extraId }
                val title = other?.localizedName(language) ?: simple?.localizedName(language) ?: return null
                val sub = when {
                    other != null -> otherLabel
                    extraCatalog.shortSounds.any { it.id == ref.extraId } -> soundsLabel
                    else -> nmLabel
                }
                SmartPlaylistEntry(
                    rawRef = ref.raw,
                    title = title,
                    subtitle = sub,
                    artworkName = other?.artwork ?: simple?.artwork,
                    fallbackCourseCode = null,
                    playCount = playCount,
                )
            }
        }
    }
}

@Composable
private fun SmartPlaylistCard(
    title: String,
    subtitle: String,
    emptyText: String,
    entries: List<SmartPlaylistEntry>,
    packDirectory: java.io.File?,
    onPlayRefs: (List<String>) -> Boolean,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    ElevatedCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        val top = entries.firstOrNull()
                        if (top != null && (top.artworkName != null || top.fallbackCourseCode != null)) {
                            PackArtwork.Thumbnail(packDirectory, listOfNotNull(top.artworkName), Modifier.fillMaxSize()) {
                                if (top.fallbackCourseCode != null) CourseCover(top.fallbackCourseCode, Modifier.fillMaxSize())
                                else Icon(Icons.Outlined.MusicNote, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        } else {
                            Icon(Icons.Outlined.MusicNote, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (entries.isNotEmpty()) {
                            "$subtitle · ${stringResource(R.string.jukebox_tracks_count, entries.size)}"
                        } else {
                            subtitle
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (entries.isNotEmpty()) {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                        )
                    }
                }
            }
            if (entries.isEmpty()) {
                Text(emptyText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val refs = remember(entries) { entries.map { it.rawRef } }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onPlayRefs(refs) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.discovery_playlist_play_all), maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { onPlayRefs(refs.shuffled()) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Shuffle, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.jukebox_shuffle), maxLines = 1)
                    }
                }
                AnimatedVisibility(visible = expanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        entries.take(10).forEachIndexed { idx, entry ->
                            Surface(
                                onClick = {
                                    val rotated = listOf(entry.rawRef) + refs.filterIndexed { i, _ -> i != idx }
                                    onPlayRefs(rotated)
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text(
                                        "${idx + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(20.dp),
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(entry.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        val meta = if (entry.playCount > 0) {
                                            "${entry.subtitle} · ${stringResource(R.string.play_count_fmt, entry.playCount)}"
                                        } else {
                                            entry.subtitle
                                        }
                                        Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DiscoveryPlaylists(
    catalog: BrowseCatalog?,
    unlocked: Boolean,
    language: String,
    state: DiscoveryViewModel.State,
    onCreate: (String) -> Unit,
    onPlaylist: (String) -> Unit,
    onCup: (String) -> Unit,
    jukeboxGames: List<JukeboxCatalog.Game> = emptyList(),
    extraCatalog: ExtraMusicCatalog.Catalog = ExtraMusicCatalog.Catalog(),
    packDirectory: java.io.File? = null,
    onViewAll: (Int) -> Unit = {},
    onJukeboxGame: (String) -> Unit = {},
    onCustomRoute: (String) -> Unit = {},
    onPlanRouteOnMap: () -> Unit = {},
    inspectCustomRoute: (CustomRoute) -> RoadJukeboxCatalog.RouteValidationReport? = { null },
    playStats: Map<String, TrackPlayStat> = emptyMap(),
    onPlaySelectedRefs: (List<String>) -> Boolean = { false },
) {
    val rallies = catalog?.routes(OfficialRouteKind.RALLY, unlocked).orEmpty()
    val gpRoutes = catalog?.routes(OfficialRouteKind.GRAND_PRIX, unlocked).orEmpty()
    val visibleCourseSet = remember(catalog, unlocked) { catalog?.courses(unlocked).orEmpty().toSet() }
    val gamesById = remember(jukeboxGames) { jukeboxGames.associateBy { it.id } }
    val jukeboxItemsByKey = remember(jukeboxGames) { jukeboxGames.flatMap { it.items }.associateBy { it.key } }
    val coursesCatLabel = stringResource(R.string.discovery_courses)
    val sfcCatLabel = stringResource(R.string.discovery_vs_sfc)
    val battleCatLabel = stringResource(R.string.discovery_battle)
    val freeCatLabel = stringResource(R.string.discovery_free)
    val otherCatLabel = stringResource(R.string.discovery_other)
    val soundsCatLabel = stringResource(R.string.discovery_sounds)
    val nmCatLabel = stringResource(R.string.discovery_nintendo_music)
    val mostPlayedEntries = remember(playStats, catalog, unlocked, language, extraCatalog, jukeboxGames) {
        playStats.values
            .asSequence()
            .filter { it.playCount > 0 }
            .sortedWith(compareByDescending<TrackPlayStat> { it.playCount }.thenByDescending { it.lastPlayedEpochMs })
            .mapNotNull { stat ->
                resolveSmartPlaylistEntry(
                    rawId = stat.trackId,
                    catalog = catalog,
                    unlocked = unlocked,
                    language = language,
                    extraCatalog = extraCatalog,
                    jukeboxItemsByKey = jukeboxItemsByKey,
                    gamesById = gamesById,
                    visibleCourseSet = visibleCourseSet,
                    coursesLabel = coursesCatLabel,
                    sfcLabel = sfcCatLabel,
                    battleLabel = battleCatLabel,
                    freeLabel = freeCatLabel,
                    otherLabel = otherCatLabel,
                    soundsLabel = soundsCatLabel,
                    nmLabel = nmCatLabel,
                    playCount = stat.playCount,
                )
            }
            .distinctBy { it.rawRef }
            .take(30)
            .toList()
    }
    val favoritesMixEntries = remember(state.favorites, playStats, catalog, unlocked, language, extraCatalog, jukeboxGames) {
        state.favorites
            .mapNotNull { fav ->
                val plays = playStats[canonicalStatIdForRef(fav)]?.playCount ?: 0
                resolveSmartPlaylistEntry(
                    rawId = fav,
                    catalog = catalog,
                    unlocked = unlocked,
                    language = language,
                    extraCatalog = extraCatalog,
                    jukeboxItemsByKey = jukeboxItemsByKey,
                    gamesById = gamesById,
                    visibleCourseSet = visibleCourseSet,
                    coursesLabel = coursesCatLabel,
                    sfcLabel = sfcCatLabel,
                    battleLabel = battleCatLabel,
                    freeLabel = freeCatLabel,
                    otherLabel = otherCatLabel,
                    soundsLabel = soundsCatLabel,
                    nmLabel = nmCatLabel,
                    playCount = plays,
                )
            }
            .distinctBy { it.rawRef }
    }
    var creating by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    if (creating) AlertDialog(
        onDismissRequest = { creating = false },
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
        confirmButton = {
            TextButton(onClick = { onCreate(name); name = ""; creating = false }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.discovery_create))
            }
        },
        dismissButton = { TextButton(onClick = { creating = false }) { Text(stringResource(R.string.discovery_cancel)) } },
    )
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "smart-playlists-hdr") {
            Text(stringResource(R.string.smart_playlists_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        item(key = "smart-most-played") {
            SmartPlaylistCard(
                title = stringResource(R.string.smart_most_played),
                subtitle = stringResource(R.string.smart_most_played_subtitle),
                emptyText = stringResource(R.string.smart_most_played_empty),
                entries = mostPlayedEntries,
                packDirectory = packDirectory,
                onPlayRefs = onPlaySelectedRefs,
            )
        }
        item(key = "smart-favorites-mix") {
            SmartPlaylistCard(
                title = stringResource(R.string.smart_favorites_mix),
                subtitle = stringResource(R.string.smart_favorites_mix_subtitle),
                emptyText = stringResource(R.string.smart_favorites_mix_empty),
                entries = favoritesMixEntries,
                packDirectory = packDirectory,
                onPlayRefs = onPlaySelectedRefs,
            )
        }
        item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)) }
        item {
            PlaylistGroupHeader(
                title = stringResource(R.string.discovery_playlists_group_user),
                count = state.playlists.size,
                onViewAll = { onViewAll(0) },
                action = {
                    OutlinedButton(onClick = { creating = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.discovery_create_playlist))
                    }
                },
            )
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
                    items(state.playlists, key = { "playlist:${it.id}" }) { playlist ->
                        Card(onClick = { onPlaylist(playlist.id) }, modifier = Modifier.width(164.dp)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.fillMaxWidth().height(112.dp), contentAlignment = Alignment.Center) {
                                    val firstRef = playlist.courseIds.firstOrNull()?.let {
                                        com.mylittleblover.mkwplayer.library.PlaylistItemRef.parse(it)
                                    }
                                    val courseCode = (firstRef as? com.mylittleblover.mkwplayer.library.PlaylistItemRef.Course)?.logicalCode
                                    val artworkName = when (firstRef) {
                                        is com.mylittleblover.mkwplayer.library.PlaylistItemRef.Course ->
                                            extraCatalog.courseArtwork[firstRef.raw] ?: extraCatalog.courseArtwork[firstRef.logicalCode]
                                        is com.mylittleblover.mkwplayer.library.PlaylistItemRef.SfcTrack ->
                                            extraCatalog.sfcCourses.firstOrNull { it.id == firstRef.trackId }?.artwork
                                        is com.mylittleblover.mkwplayer.library.PlaylistItemRef.BattleTrack ->
                                            extraCatalog.battleTracks.firstOrNull { it.id == firstRef.trackId }?.artwork
                                        is com.mylittleblover.mkwplayer.library.PlaylistItemRef.ExtraTrack ->
                                            extraCatalog.otherMusic.firstOrNull { it.id == firstRef.extraId }?.artwork
                                                ?: extraCatalog.shortSounds.firstOrNull { it.id == firstRef.extraId }?.artwork
                                                ?: extraCatalog.nintendoMusicSpecial.firstOrNull { it.id == firstRef.extraId }?.artwork
                                        is com.mylittleblover.mkwplayer.library.PlaylistItemRef.Jukebox, null -> null
                                    }
                                    if (artworkName != null && packDirectory != null) {
                                        PackArtwork.Thumbnail(packDirectory, listOf(artworkName), Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))) {
                                            Icon(Icons.Outlined.MusicNote, null, Modifier.size(40.dp))
                                        }
                                    } else {
                                        val art = courseCode?.let { StageArt.resolve(it) }
                                        if (art != null) ResourceArtwork(art, Modifier.size(90.dp))
                                        else Icon(Icons.Outlined.MusicNote, null, Modifier.size(40.dp))
                                    }
                                }
                                Text(playlist.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    stringResource(R.string.jukebox_tracks_count, playlist.courseIds.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)) }
        item {
            PlaylistGroupHeader(
                title = stringResource(R.string.discovery_playlists_group_routes),
                count = state.customRoutes.size,
                onViewAll = { onViewAll(1) },
                action = {
                    OutlinedButton(onClick = onPlanRouteOnMap) {
                        Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.route_plan_on_map))
                    }
                },
            )
        }
        if (state.customRoutes.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.discovery_no_saved),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.customRoutes, key = { "my-route:${it.id}" }) { customRoute ->
                        val report = inspectCustomRoute(customRoute)
                        val isInvalid = report?.canPlay == false
                        val firstCode = customRoute.stops.firstOrNull()?.stageCode?.removeSuffix("_00")
                        val artwork = firstCode?.let { extraCatalog.courseArtwork[it] ?: extraCatalog.courseArtwork["${it}_00"] }
                        Card(onClick = { onCustomRoute(customRoute.id) }, modifier = Modifier.width(164.dp)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                PackArtwork.Thumbnail(
                                    packDirectory,
                                    listOfNotNull(artwork),
                                    Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                                ) {
                                    if (firstCode != null) CourseCover(firstCode, Modifier.fillMaxSize())
                                    else Icon(Icons.Filled.Map, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                                Text(customRoute.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    if (isInvalid) {
                                        report?.blockingReason ?: stringResource(R.string.route_invalid_topology)
                                    } else {
                                        stringResource(R.string.route_stops_count, customRoute.stops.size)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isInvalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (gpRoutes.isNotEmpty() || rallies.isNotEmpty()) {
            item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)) }
        }
        if (gpRoutes.isNotEmpty()) {
            item {
                PlaylistGroupHeader(
                    title = stringResource(R.string.discovery_gp),
                    count = gpRoutes.size,
                    onViewAll = { onViewAll(2) },
                )
            }
            item { OfficialRouteStrip(gpRoutes, language, packDirectory, extraCatalog.courseArtwork, onCup) }
        }
        if (rallies.isNotEmpty()) {
            item {
                PlaylistGroupHeader(
                    title = stringResource(R.string.discovery_rally),
                    count = rallies.size,
                    onViewAll = { onViewAll(2) },
                )
            }
            item { OfficialRouteStrip(rallies, language, packDirectory, extraCatalog.courseArtwork, onCup) }
        }

        if (jukeboxGames.isNotEmpty()) {
            item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)) }
            item {
                PlaylistGroupHeader(
                    title = stringResource(R.string.discovery_playlists_group_jukebox),
                    count = jukeboxGames.size,
                    onViewAll = { onViewAll(3) },
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(jukeboxGames, key = { "jb-group:${it.id}" }) { game ->
                        Card(onClick = { onJukeboxGame(game.id) }, modifier = Modifier.width(164.dp)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                PackArtwork.Thumbnail(
                                    packDirectory,
                                    listOfNotNull(extraCatalog.jukeboxGameArtwork[game.id]),
                                    Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                                ) {
                                    Text(
                                        game.yearLabel.ifBlank { "OST" },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Text(game.localizedName(language), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(stringResource(R.string.jukebox_tracks_count, game.items.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistGroupHeader(
    title: String,
    count: Int,
    onViewAll: () -> Unit,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (count > 0) {
                TextButton(onClick = onViewAll) {
                    Text(stringResource(R.string.discovery_view_all_count, count))
                }
            }
            action?.invoke()
        }
    }
}

@Composable
internal fun DiscoveryAllPlaylists(
    initialSection: Int,
    catalog: BrowseCatalog?,
    unlocked: Boolean,
    language: String,
    state: DiscoveryViewModel.State,
    jukeboxGames: List<JukeboxCatalog.Game>,
    extraCatalog: ExtraMusicCatalog.Catalog,
    packDirectory: java.io.File?,
    onCreate: (String) -> Unit,
    onPlaylist: (String) -> Unit,
    onCup: (String) -> Unit,
    onJukeboxGame: (String) -> Unit,
    onCreateRouteOnMap: () -> Unit = {},
    onCustomRoute: (String) -> Unit = {},
    inspectCustomRoute: (CustomRoute) -> RoadJukeboxCatalog.RouteValidationReport? = { null },
) {
    var section by rememberSaveable(initialSection) { mutableIntStateOf(initialSection.coerceIn(0, 3)) }
    var query by rememberSaveable { mutableStateOf("") }
    var creating by rememberSaveable { mutableStateOf(false) }
    var newName by rememberSaveable { mutableStateOf("") }
    val sectionTitles = listOf(
        stringResource(R.string.discovery_playlists_group_user),
        stringResource(R.string.discovery_playlists_group_routes),
        stringResource(R.string.discovery_playlists_group_rallies),
        stringResource(R.string.discovery_playlists_group_jukebox),
    )
    if (creating) AlertDialog(
        onDismissRequest = { creating = false },
        title = { Text(stringResource(R.string.discovery_create_playlist)) },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it.take(120) },
                label = { Text(stringResource(R.string.discovery_playlist_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(newName); newName = ""; creating = false }, enabled = newName.isNotBlank()) {
                Text(stringResource(R.string.discovery_create))
            }
        },
        dismissButton = { TextButton(onClick = { creating = false }) { Text(stringResource(R.string.discovery_cancel)) } },
    )
    Column(Modifier.fillMaxSize()) {
        val options = sectionTitles.mapIndexed { idx, label ->
            com.mylittleblover.mkwplayer.ui.interactive.DrawerOption(idx, label)
        }
        com.mylittleblover.mkwplayer.ui.interactive.SelectionDrawerRow(
            label = stringResource(R.string.discovery_playlist_category_label),
            selectedLabel = sectionTitles.getOrElse(section) { sectionTitles.first() },
            sheetTitle = stringResource(R.string.discovery_playlist_category_label),
            options = options,
            selectedValue = section,
            onSelect = { section = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            compact = true,
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.discovery_search)) },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, null) }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val trimmed = query.trim()
            when (section) {
                0 -> {
                    item {
                        OutlinedButton(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Add, null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.discovery_create_playlist))
                        }
                    }
                    val list = state.playlists.filter { trimmed.isEmpty() || it.name.contains(trimmed, ignoreCase = true) }
                    if (list.isEmpty()) item { Text(stringResource(R.string.discovery_no_saved)) }
                    items(list, key = { "all-pl:${it.id}" }) { playlist ->
                        val first = playlist.courseIds.firstOrNull { it in catalog?.courses(unlocked).orEmpty() }
                        ExtraSquareRow(
                            title = playlist.name,
                            subtitle = stringResource(R.string.jukebox_tracks_count, playlist.courseIds.size),
                            packDirectory = packDirectory,
                            artworkName = first?.let { extraCatalog.courseArtwork[it] ?: extraCatalog.courseArtwork[it.removeSuffix("_00")] },
                            fallbackCourseCode = first,
                            onClick = { onPlaylist(playlist.id) },
                        )
                    }
                }
                1 -> {
                    item {
                        OutlinedButton(onClick = onCreateRouteOnMap) {
                            Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.route_plan_on_map))
                        }
                    }
                    val matchingRoutes = state.customRoutes.filter {
                        trimmed.isEmpty() || it.name.contains(trimmed, ignoreCase = true)
                    }
                    if (matchingRoutes.isEmpty()) {
                        item { Text(stringResource(R.string.discovery_no_saved)) }
                    }
                    items(matchingRoutes, key = { "all-route:${it.id}" }) { customRoute ->
                        val report = inspectCustomRoute(customRoute)
                        val first = customRoute.stops.firstOrNull()?.stageCode?.removeSuffix("_00")
                        val legsSummary = stringResource(R.string.route_legs_count, (customRoute.stops.size - 1).coerceAtLeast(0))
                        val stopsSummary = stringResource(R.string.route_stops_count, customRoute.stops.size)
                        val sub = if (report?.canPlay == false) {
                            "$stopsSummary · $legsSummary · ${report.blockingReason ?: stringResource(R.string.route_invalid_topology)}"
                        } else if (customRoute.loopWholeRoute) {
                            "$stopsSummary · $legsSummary · ${stringResource(R.string.route_loop_whole)}"
                        } else {
                            "$stopsSummary · $legsSummary"
                        }
                        ExtraSquareRow(
                            title = customRoute.name,
                            subtitle = sub,
                            packDirectory = packDirectory,
                            artworkName = first?.let { extraCatalog.courseArtwork[it] ?: extraCatalog.courseArtwork["${it}_00"] },
                            fallbackCourseCode = first,
                            onClick = { onCustomRoute(customRoute.id) },
                        )
                    }
                }
                2 -> {
                    val rallies = (catalog?.routes(OfficialRouteKind.GRAND_PRIX, unlocked).orEmpty() +
                        catalog?.routes(OfficialRouteKind.RALLY, unlocked).orEmpty())
                        .filter { trimmed.isEmpty() || it.displayName(language).contains(trimmed, ignoreCase = true) }
                    items(rallies, key = { "all-rally:${it.id}" }) { route ->
                        RouteRow(route, catalog, language, packDirectory, extraCatalog.courseArtwork) { onCup(route.id) }
                    }
                }
                3 -> {
                    val games = jukeboxGames.filter {
                        trimmed.isEmpty() || it.localizedName(language).contains(trimmed, ignoreCase = true) || it.yearLabel.contains(trimmed, ignoreCase = true)
                    }
                    items(games, key = { "all-jb:${it.id}" }) { game ->
                        JukeboxGameRow(
                            game = game,
                            language = language,
                            packDirectory = packDirectory,
                            artworkName = extraCatalog.jukeboxGameArtwork[game.id],
                        ) { onJukeboxGame(game.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun OfficialRouteStrip(
    routes: List<OfficialRoute>,
    language: String,
    packDirectory: java.io.File?,
    courseArtwork: Map<String, String>,
    onCup: (String) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(routes, key = { it.id }) { route ->
            CupMosaicCard(
                route = route,
                language = language,
                packDirectory = packDirectory,
                courseArtwork = courseArtwork,
                modifier = Modifier.width(164.dp),
                onClick = { onCup(route.id) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun DiscoveryPlaylist(
    playlist: com.mylittleblover.mkwplayer.library.UserPlaylist?,
    catalog: BrowseCatalog?,
    unlocked: Boolean,
    language: String,
    error: Boolean,
    onRemove: (Int) -> Unit,
    onRemoveMany: (Collection<Int>) -> Unit = {},
    onRename: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onDelete: () -> Unit,
    onCourse: (String) -> Unit,
    presets: List<com.mylittleblover.mkwplayer.library.CoursePreset> = emptyList(),
    packDirectory: java.io.File? = null,
    courseArtwork: Map<String, String> = emptyMap(),
    jukebox: JukeboxCatalog.Catalog = JukeboxCatalog.Catalog(emptyList(), emptyMap()),
    extraCatalog: ExtraMusicCatalog.Catalog = ExtraMusicCatalog.Catalog(),
    isThisPlaylistPlaying: Boolean = false,
    onPlayAll: () -> Unit = {},
    onShuffle: () -> Unit = {},
    onPlayFrom: (Int) -> Unit = {},
    onToggleNativeDefault: (Boolean) -> Unit = {},
    onUpdateEntryPreset: (Int, String?) -> Unit = { _, _ -> },
    onUpdateEntryNative: (Int, Boolean?) -> Unit = { _, _ -> },
    onOpenFlowEditor: (String, String?) -> Unit = { _, _ -> },
    isPresetSelectable: (String, String?) -> Boolean = { _, ref -> ref != PresetRefs.SYSTEM_KNOCKOUT },
    playlists: List<com.mylittleblover.mkwplayer.library.UserPlaylist> = emptyList(),
    favorites: Set<String> = emptySet(),
    onToggleFavorite: (String) -> Unit = {},
    onAddToPlaylist: (playlistId: String, refs: List<String>) -> Unit = { _, _ -> },
    onCreatePlaylistAndAdd: (name: String, refs: List<String>) -> Unit = { _, _ -> },
    allowPlayNext: Boolean = true,
    onPlayNextRefs: (List<String>) -> Boolean = { false },
    onAddToQueueRefs: (List<String>) -> Boolean = { false },
    onPlaySelectedRefs: (List<String>) -> Boolean = { false },
    playStats: Map<String, com.mylittleblover.mkwplayer.library.TrackPlayStat> = emptyMap(),
) {
    var deleting by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var editingEntryIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var newName by rememberSaveable { mutableStateOf("") }
    val visibleCourses = catalog?.courses(unlocked).orEmpty()
    val visibleCourseSet = remember(visibleCourses) { visibleCourses.toSet() }
    val configSheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val systemDefaultLabel = stringResource(R.string.preset_system_default)
    val systemKnockoutLabel = stringResource(R.string.preset_system_knockout)
    val coursesCatLabel = stringResource(R.string.discovery_courses)
    val sfcCatLabel = stringResource(R.string.discovery_vs_sfc)
    val battleCatLabel = stringResource(R.string.discovery_battle)
    val freeCatLabel = stringResource(R.string.discovery_free)
    val otherCatLabel = stringResource(R.string.discovery_other)
    val soundsCatLabel = stringResource(R.string.discovery_sounds)
    val nmCatLabel = stringResource(R.string.discovery_nintendo_music)
    val addedNextText = stringResource(R.string.jukebox_added_next)
    val addedQueueText = stringResource(R.string.track_action_added_to_queue)
    val gamesById = remember(jukebox) { jukebox.games.associateBy { it.id } }

    var trackMenuTarget by remember { mutableStateOf<TrackMenuSpec?>(null) }
    var infoTarget by remember { mutableStateOf<TrackMenuSpec?>(null) }
    var playlistPickerRefs by remember { mutableStateOf<List<String>?>(null) }
    var multiSelectMode by rememberSaveable(playlist?.id) { mutableStateOf(false) }
    var selectedIndices by rememberSaveable(playlist?.id) { mutableStateOf(listOf<Int>()) }
    val selectedIndexSet = remember(selectedIndices) { selectedIndices.toSet() }

    androidx.activity.compose.BackHandler(enabled = multiSelectMode) {
        multiSelectMode = false
        selectedIndices = emptyList()
    }

    data class ResolvedRowDisplay(
        val ref: com.mylittleblover.mkwplayer.library.PlaylistItemRef,
        val available: Boolean,
        val title: String,
        val categorySubtitle: String,
        val artworkName: String?,
        val fallbackCourseCode: String? = null,
        val editorTargetCode: String? = null,
        val isCourse: Boolean = false,
    )

    fun resolveEntry(rawId: String): ResolvedRowDisplay {
        return when (val ref = com.mylittleblover.mkwplayer.library.PlaylistItemRef.parse(rawId)) {
            is com.mylittleblover.mkwplayer.library.PlaylistItemRef.Course -> {
                val logical = ref.logicalCode
                val ok = logical in visibleCourseSet || rawId in visibleCourseSet
                ResolvedRowDisplay(
                    ref = ref,
                    available = ok,
                    title = if (ok) catalog?.displayName(logical, language) ?: logical else "",
                    categorySubtitle = coursesCatLabel,
                    artworkName = courseArtwork[rawId] ?: courseArtwork[logical],
                    fallbackCourseCode = logical,
                    editorTargetCode = logical,
                    isCourse = true,
                )
            }
            is com.mylittleblover.mkwplayer.library.PlaylistItemRef.Jukebox -> {
                val item = jukebox.itemsByKey[ref.trackKey]
                val gameName = item?.let { gamesById[it.gameId]?.localizedName(language) } ?: freeCatLabel
                ResolvedRowDisplay(
                    ref = ref,
                    available = item != null,
                    title = item?.localizedDisplayName(language).orEmpty(),
                    categorySubtitle = gameName,
                    artworkName = item?.ostIndex?.let { "t_%03d.webp".format(java.util.Locale.ROOT, it) },
                )
            }
            is com.mylittleblover.mkwplayer.library.PlaylistItemRef.SfcTrack -> {
                val ok = ExtraMusicCatalog.isVisible(ref.trackId, unlocked)
                val track = extraCatalog.sfcCourses.firstOrNull { it.id == ref.trackId }
                ResolvedRowDisplay(
                    ref = ref,
                    available = ok && track != null,
                    title = track?.localizedName(language).orEmpty(),
                    categorySubtitle = sfcCatLabel,
                    artworkName = track?.artwork,
                    editorTargetCode = "extra:${ref.trackId}",
                )
            }
            is com.mylittleblover.mkwplayer.library.PlaylistItemRef.BattleTrack -> {
                val ok = ExtraMusicCatalog.isVisible(ref.trackId, unlocked)
                val track = extraCatalog.battleTracks.firstOrNull { it.id == ref.trackId }
                ResolvedRowDisplay(
                    ref = ref,
                    available = ok && track != null,
                    title = track?.localizedName(language).orEmpty(),
                    categorySubtitle = battleCatLabel,
                    artworkName = track?.artwork,
                    editorTargetCode = "extra:${ref.trackId}",
                )
            }
            is com.mylittleblover.mkwplayer.library.PlaylistItemRef.ExtraTrack -> {
                val ok = ExtraMusicCatalog.isVisible(ref.extraId, unlocked)
                val other = extraCatalog.otherMusic.firstOrNull { it.id == ref.extraId }
                val simple = extraCatalog.shortSounds.firstOrNull { it.id == ref.extraId }
                    ?: extraCatalog.nintendoMusicSpecial.firstOrNull { it.id == ref.extraId }
                val sub = when {
                    other != null -> otherCatLabel
                    extraCatalog.shortSounds.any { it.id == ref.extraId } -> soundsCatLabel
                    else -> nmCatLabel
                }
                ResolvedRowDisplay(
                    ref = ref,
                    available = ok && (other != null || simple != null),
                    title = other?.localizedName(language) ?: simple?.localizedName(language).orEmpty(),
                    categorySubtitle = sub,
                    artworkName = other?.artwork ?: simple?.artwork,
                    editorTargetCode = if (other is ExtraMusicCatalog.OtherMusicItem.InteractiveTitle) {
                        "extra:${other.id}"
                    } else null,
                )
            }
        }
    }

    val entryIdx = editingEntryIndex
    if (playlist != null && entryIdx != null && entryIdx in playlist.courseIds.indices) {
        val rawCode = playlist.courseIds[entryIdx]
        val rowInfo = resolveEntry(rawCode)
        val editorTarget = rowInfo.editorTargetCode
        if (editorTarget != null) {
            val logicalCode = editorTarget
            val cfg = playlist.configAt(entryIdx)
            val matchingPresets = presets.filter {
                it.courseCode.equals(logicalCode, ignoreCase = true) && isPresetSelectable(logicalCode, it.id)
            }
            val knockoutSelectable = rowInfo.isCourse && isPresetSelectable(logicalCode, PresetRefs.SYSTEM_KNOCKOUT)
            val defaultSelectable = !rowInfo.isCourse || isPresetSelectable(logicalCode, PresetRefs.SYSTEM_DEFAULT)
            val itemTitle = rowInfo.title.ifBlank { rawCode }
            ModalBottomSheet(sheetState = configSheet, onDismissRequest = { editingEntryIndex = null }) {
                Column(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "$itemTitle · ${stringResource(R.string.discovery_playlist_entry_config)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.discovery_playlist_entry_preset),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column {
                        val isDefaultSelected = cfg.presetId == null || cfg.presetId == PresetRefs.SYSTEM_DEFAULT
                        if (defaultSelectable || isDefaultSelected) {
                            ListItem(
                                headlineContent = { Text(systemDefaultLabel) },
                                trailingContent = { RadioButton(selected = isDefaultSelected, onClick = null) },
                                modifier = Modifier.fillMaxWidth().clickable { onUpdateEntryPreset(entryIdx, null) },
                            )
                        }
                        if (knockoutSelectable) {
                            ListItem(
                                headlineContent = { Text(systemKnockoutLabel) },
                                trailingContent = { RadioButton(selected = cfg.presetId == PresetRefs.SYSTEM_KNOCKOUT, onClick = null) },
                                modifier = Modifier.fillMaxWidth().clickable { onUpdateEntryPreset(entryIdx, PresetRefs.SYSTEM_KNOCKOUT) },
                            )
                        }
                        matchingPresets.forEach { preset ->
                            ListItem(
                                headlineContent = { Text(preset.name) },
                                trailingContent = { RadioButton(selected = cfg.presetId == preset.id, onClick = null) },
                                modifier = Modifier.fillMaxWidth().clickable { onUpdateEntryPreset(entryIdx, preset.id) },
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = {
                                editingEntryIndex = null
                                onOpenFlowEditor(logicalCode, cfg.presetId)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.Edit, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.preset_new))
                        }
                    }
                    if (rowInfo.isCourse) {
                        HorizontalDivider()
                        Text(
                            stringResource(R.string.discovery_playlist_native_transition),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Column {
                            listOf(
                                null to stringResource(R.string.discovery_playlist_entry_native_inherit),
                                true to stringResource(R.string.discovery_playlist_entry_native_on),
                                false to stringResource(R.string.discovery_playlist_entry_native_off),
                            ).forEach { (value, label) ->
                                ListItem(
                                    headlineContent = { Text(label) },
                                    trailingContent = { RadioButton(selected = cfg.nativeTransitionEnabled == value, onClick = null) },
                                    modifier = Modifier.fillMaxWidth().clickable { onUpdateEntryNative(entryIdx, value) },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        } else {
            editingEntryIndex = null
        }
    }

    if (renaming) AlertDialog(
        onDismissRequest = { renaming = false },
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
        dismissButton = { TextButton(onClick = { renaming = false }) { Text(stringResource(R.string.discovery_cancel)) } },
    )
    if (deleting) AlertDialog(
        onDismissRequest = { deleting = false },
        title = { Text(stringResource(R.string.discovery_delete_playlist)) },
        text = { Text(playlist?.name.orEmpty()) },
        confirmButton = { TextButton(onClick = { deleting = false; onDelete() }) { Text(stringResource(R.string.discovery_delete_playlist)) } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.discovery_cancel)) } },
    )

    TrackActionOverlayHosts(
        menuTarget = trackMenuTarget,
        onDismissMenu = { trackMenuTarget = null },
        playlistPickerRefs = playlistPickerRefs,
        onOpenPlaylistPicker = { refs -> playlistPickerRefs = refs },
        onDismissPlaylistPicker = { playlistPickerRefs = null },
        infoTarget = infoTarget,
        onOpenInfo = { infoTarget = it },
        onDismissInfo = { infoTarget = null },
        packDirectory = packDirectory,
        allowPlayNext = allowPlayNext,
        playlists = playlists.filter { it.id != playlist?.id },
        favorites = favorites,
        onToggleFavorite = onToggleFavorite,
        onOpenCourse = onCourse,
        onOpenFlowEditor = { editorCode -> onOpenFlowEditor(editorCode, null) },
        onEnterMultiSelect = { key ->
            val idx = key.toIntOrNull()
            multiSelectMode = true
            if (idx != null) selectedIndices = (selectedIndices + idx).distinct()
        },
        onPlayNextRefs = onPlayNextRefs,
        onAddToQueueRefs = onAddToQueueRefs,
        onAddToPlaylist = { pid, refs ->
            onAddToPlaylist(pid, refs)
            multiSelectMode = false
            selectedIndices = emptyList()
        },
        onCreatePlaylistAndAdd = { name, refs ->
            onCreatePlaylistAndAdd(name, refs)
            multiSelectMode = false
            selectedIndices = emptyList()
        },
        snackbarHostState = snackbarHostState,
        addedNextText = addedNextText,
        addedQueueText = addedQueueText,
    )

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AnimatedVisibility(visible = multiSelectMode && playlist != null) {
                val allIndices = playlist?.courseIds?.indices?.toList().orEmpty()
                val allSelected = allIndices.isNotEmpty() && selectedIndexSet.containsAll(allIndices)
                MultiSelectTopBanner(
                    selectedCount = selectedIndexSet.size,
                    allSelected = allSelected,
                    onToggleSelectAll = {
                        selectedIndices = if (allSelected) emptyList() else allIndices
                    },
                    onClose = {
                        multiSelectMode = false
                        selectedIndices = emptyList()
                    },
                )
            }
            val plListState = androidx.compose.foundation.lazy.rememberLazyListState()
            val latestPlaylist by rememberUpdatedState(playlist)
            val latestMove by rememberUpdatedState(onMove)
            val latestPlaying by rememberUpdatedState(isThisPlaylistPlaying)
            val latestError by rememberUpdatedState(error)
            val latestMultiSelect by rememberUpdatedState(multiSelectMode)
            val rowKeys = remember(playlist?.courseIds) {
                val ids = playlist?.courseIds.orEmpty()
                val seen = HashMap<String, Int>(ids.size)
                ids.map { raw ->
                    val n = (seen[raw] ?: 0) + 1
                    seen[raw] = n
                    "$raw#$n"
                }
            }
            val dragController = remember(plListState, playlist?.id) {
                com.mylittleblover.mkwplayer.ui.components.DragReorderController(
                    listState = plListState,
                    sectionStartIndex = {
                        val pl = latestPlaylist ?: return@DragReorderController 0
                        1 +
                            (if (latestPlaying) 1 else 0) +
                            (if (latestError) 1 else 0) +
                            (if (pl.courseIds.size > 1 && !latestMultiSelect) 1 else 0)
                    },
                    itemCountProvider = { latestPlaylist?.courseIds?.size ?: 0 },
                    onMove = { from, to -> latestMove(from, to) },
                )
            }
            LazyColumn(
                state = plListState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (playlist == null) item { Text(stringResource(R.string.discovery_empty)) }
                else {
                    val resolvedEntries = playlist.courseIds.map(::resolveEntry)
                    val hasCourses = resolvedEntries.any { it.isCourse }
                    item(key = "pl-hero") {
                        val firstHero = resolvedEntries.firstOrNull { it.available }
                        ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    PackArtwork.Thumbnail(
                                        packDirectory,
                                        listOfNotNull(firstHero?.artworkName),
                                        Modifier.size(72.dp).clip(RoundedCornerShape(16.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                                    ) {
                                        val fallbackCourse = firstHero?.fallbackCourseCode
                                        val art = fallbackCourse?.let { StageArt.resolve(it) }
                                        if (art != null) ResourceArtwork(art, Modifier.size(56.dp))
                                        else Icon(Icons.Outlined.MusicNote, null, modifier = Modifier.size(32.dp))
                                    }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(playlist.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                        Text(
                                            stringResource(R.string.jukebox_tracks_count, playlist.courseIds.size),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Box {
                                        IconButton(onClick = { menuOpen = true }) {
                                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.jukebox_more_actions))
                                        }
                                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.track_action_multi_select)) },
                                                leadingIcon = { Icon(Icons.Outlined.CheckCircle, contentDescription = null) },
                                                enabled = playlist.courseIds.isNotEmpty(),
                                                onClick = {
                                                    menuOpen = false
                                                    multiSelectMode = true
                                                    selectedIndices = emptyList()
                                                },
                                            )
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
                                }

                                val playableCount = resolvedEntries.count { it.available }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = onPlayAll, enabled = playableCount > 0, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.discovery_playlist_play_all))
                                    }
                                    OutlinedButton(onClick = onShuffle, enabled = playableCount > 0, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Filled.Shuffle, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.jukebox_shuffle))
                                    }
                                }

                                if (hasCourses) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(stringResource(R.string.discovery_playlist_native_transition), style = MaterialTheme.typography.titleSmall)
                                            Text(
                                                stringResource(R.string.discovery_playlist_native_transition_desc),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Switch(
                                            checked = playlist.nativeTransitionDefault,
                                            onCheckedChange = onToggleNativeDefault,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (isThisPlaylistPlaying) item(key = "pl-playing") {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                stringResource(R.string.discovery_playlist_frozen_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                    if (error) item(key = "pl-error") { Text(stringResource(R.string.discovery_save_failed), color = MaterialTheme.colorScheme.error) }
                    if (playlist.courseIds.isEmpty()) item(key = "pl-empty") { Text(stringResource(R.string.discovery_playlist_tracks_empty)) }
                    else if (playlist.courseIds.size > 1 && !multiSelectMode) item(key = "pl-drag-hint") {
                        Text(
                            stringResource(R.string.playlist_drag_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    itemsIndexed(playlist.courseIds, key = { idx, _ -> rowKeys[idx] }) { index, rawId ->
                        val info = resolvedEntries[index]
                        val cfg = playlist.configAt(index)
                        val title = if (info.available) info.title.ifBlank { rawId } else stringResource(R.string.discovery_empty)
                        val editorTarget = info.editorTargetCode
                        val baseMetaLine = if (editorTarget != null) {
                            val presetName = when {
                                info.isCourse && cfg.presetId == PresetRefs.SYSTEM_KNOCKOUT &&
                                    isPresetSelectable(editorTarget, PresetRefs.SYSTEM_KNOCKOUT) -> systemKnockoutLabel
                                cfg.presetId == null || cfg.presetId == PresetRefs.SYSTEM_DEFAULT -> systemDefaultLabel
                                else -> presets.firstOrNull {
                                    it.id == cfg.presetId && it.courseCode.equals(editorTarget, ignoreCase = true)
                                }?.name ?: systemDefaultLabel
                            }
                            val nativeStatus = if (info.isCourse) {
                                when (cfg.nativeTransitionEnabled) {
                                    true -> stringResource(R.string.discovery_playlist_entry_native_on)
                                    false -> stringResource(R.string.discovery_playlist_entry_native_off)
                                    null -> if (playlist.nativeTransitionDefault) stringResource(R.string.discovery_playlist_entry_native_inherit) else null
                                }
                            } else null
                            listOfNotNull(info.categorySubtitle, presetName, nativeStatus).joinToString(" · ")
                        } else {
                            info.categorySubtitle
                        }
                        val statId = when (val r = info.ref) {
                            is com.mylittleblover.mkwplayer.library.PlaylistItemRef.Course -> r.logicalCode
                            else -> r.raw
                        }
                        val plays = playStats[statId]?.playCount ?: 0
                        val metaLine = if (plays > 0) {
                            listOfNotNull(baseMetaLine.takeIf { it.isNotBlank() }, stringResource(R.string.play_count_fmt, plays)).joinToString(" · ")
                        } else {
                            baseMetaLine
                        }
                        val courseCodeOrNull = (info.ref as? com.mylittleblover.mkwplayer.library.PlaylistItemRef.Course)?.logicalCode
                        val spec = TrackMenuSpec(
                            selectionKey = index.toString(),
                            playlistRef = rawId,
                            title = title,
                            subtitle = metaLine,
                            artworkName = info.artworkName,
                            fallbackCourseCode = info.fallbackCourseCode,
                            favoriteId = if (info.isCourse) courseCodeOrNull else rawId,
                            courseCodeForDetail = if (info.isCourse && info.available) courseCodeOrNull else null,
                            editorTargetCode = if (info.available) editorTarget else null,
                            infoPairs = listOf(
                                R.string.track_info_category to info.categorySubtitle,
                                R.string.track_info_ref to rawId,
                            ),
                            onPlayNow = { if (info.available) onPlayFrom(index) },
                            playlistEntryIndex = index,
                            playlistCanMoveUp = index > 0,
                            playlistCanMoveDown = index + 1 < playlist.courseIds.size,
                            onPlaylistConfig = if (editorTarget != null && info.available) ({ editingEntryIndex = index }) else null,
                            onPlaylistMoveUp = if (index > 0) ({ onMove(index, index - 1) }) else null,
                            onPlaylistMoveDown = if (index + 1 < playlist.courseIds.size) ({ onMove(index, index + 1) }) else null,
                            onPlaylistRemove = { onRemove(index) },
                        )
                        val isSelected = index in selectedIndexSet
                        val dragging = dragController.draggingIndex == index
                        val rowMod = if (dragging) {
                            Modifier
                                .zIndex(1f)
                                .graphicsLayer { translationY = dragController.dragDeltaY }
                        } else {
                            Modifier
                        }
                        com.mylittleblover.mkwplayer.ui.components.SwipeToTrackActionBox(
                            enabled = !multiSelectMode && dragController.draggingIndex == null,
                            onSwipeStartToEnd = if (allowPlayNext && info.available) {
                                {
                                    if (onPlayNextRefs(listOf(rawId))) {
                                        scope.launch { snackbarHostState.showSnackbar(addedNextText) }
                                    }
                                }
                            } else null,
                            onSwipeEndToStart = { onRemove(index) },
                            endToStartIcon = Icons.Filled.DeleteOutline,
                            endToStartLabel = stringResource(R.string.swipe_action_remove),
                            endToStartDestructive = true,
                        ) {
                            Card(
                                elevation = CardDefaults.cardElevation(defaultElevation = if (dragging) 8.dp else 0.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = when {
                                        dragging -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
                                        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                                        else -> MaterialTheme.colorScheme.surfaceContainerLow
                                    },
                                ),
                                modifier = rowMod
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .combinedClickable(
                                        onClick = {
                                            if (multiSelectMode) {
                                                selectedIndices = if (isSelected) selectedIndices - index else selectedIndices + index
                                            } else if (info.available) {
                                                onPlayFrom(index)
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (multiSelectMode) {
                                                selectedIndices = if (isSelected) selectedIndices - index else selectedIndices + index
                                            } else {
                                                trackMenuTarget = spec
                                            }
                                        },
                                    ),
                            ) {
                                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        if (multiSelectMode) {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = {
                                                    selectedIndices = if (isSelected) selectedIndices - index else selectedIndices + index
                                                },
                                            )
                                        }
                                        PackArtwork.Thumbnail(
                                            packDirectory,
                                            listOfNotNull(info.artworkName),
                                            Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)),
                                        ) {
                                            val fallback = info.fallbackCourseCode
                                            if (fallback != null) CourseCover(fallback, Modifier.fillMaxSize())
                                            else Icon(Icons.Outlined.MusicNote, null, modifier = Modifier.size(22.dp))
                                        }
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(metaLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        if (!multiSelectMode) {
                                            if (info.isCourse) {
                                                IconButton(onClick = { onCourse((info.ref as com.mylittleblover.mkwplayer.library.PlaylistItemRef.Course).logicalCode) }, enabled = info.available) {
                                                    Icon(Icons.Outlined.MusicNote, contentDescription = title)
                                                }
                                            }
                                            if (editorTarget != null) {
                                                IconButton(onClick = { editingEntryIndex = index }, enabled = info.available) {
                                                    Icon(Icons.Filled.Tune, contentDescription = stringResource(R.string.discovery_playlist_entry_config))
                                                }
                                            }
                                            IconButton(onClick = { trackMenuTarget = spec }) {
                                                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.jukebox_more_actions))
                                            }
                                            if (playlist.courseIds.size > 1) {
                                                com.mylittleblover.mkwplayer.ui.components.DragHandleButton(
                                                    index = index,
                                                    controller = dragController,
                                                )
                                            }
                                        }
                                    }
                                    if (!multiSelectMode) {
                                        Row(Modifier.align(Alignment.End)) {
                                            IconButton(onClick = { onMove(index, index - 1) }, enabled = index > 0) {
                                                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "${stringResource(R.string.discovery_move_up)}: $title, ${index + 1}")
                                            }
                                            IconButton(onClick = { onMove(index, index + 1) }, enabled = index + 1 < playlist.courseIds.size) {
                                                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "${stringResource(R.string.discovery_move_down)}: $title, ${index + 1}")
                                            }
                                            IconButton(onClick = { onRemove(index) }) {
                                                Icon(Icons.Filled.RemoveCircleOutline, contentDescription = "${stringResource(R.string.discovery_remove)}: $title, ${index + 1}")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(visible = multiSelectMode && playlist != null) {
                val chosenRefs = selectedIndices
                    .sorted()
                    .mapNotNull { idx -> playlist?.courseIds?.getOrNull(idx) }
                MultiSelectActionBar(
                    hasSelection = chosenRefs.isNotEmpty(),
                    allowPlayNext = allowPlayNext,
                    onPlaySelected = {
                        if (chosenRefs.isNotEmpty() && onPlaySelectedRefs(chosenRefs)) {
                            multiSelectMode = false
                            selectedIndices = emptyList()
                        }
                    },
                    onPlayNextSelected = {
                        if (chosenRefs.isNotEmpty() && onPlayNextRefs(chosenRefs)) {
                            scope.launch { snackbarHostState.showSnackbar(addedNextText) }
                        }
                        multiSelectMode = false
                        selectedIndices = emptyList()
                    },
                    onAddToQueueSelected = {
                        if (chosenRefs.isNotEmpty() && onAddToQueueRefs(chosenRefs)) {
                            scope.launch { snackbarHostState.showSnackbar(addedQueueText) }
                        }
                        multiSelectMode = false
                        selectedIndices = emptyList()
                    },
                    onAddToPlaylistSelected = {
                        if (chosenRefs.isNotEmpty()) playlistPickerRefs = chosenRefs
                    },
                    onRemoveSelected = {
                        if (selectedIndexSet.isNotEmpty()) {
                            onRemoveMany(selectedIndexSet)
                        }
                        multiSelectMode = false
                        selectedIndices = emptyList()
                    },
                )
            }
        }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun JukeboxGameRow(
    game: JukeboxCatalog.Game,
    language: String,
    packDirectory: java.io.File? = null,
    artworkName: String? = null,
    chronologicalIndex: Int? = null,
    onClick: () -> Unit,
) {
    val localized = game.localizedName(language)
    val countLabel = stringResource(R.string.jukebox_tracks_count, game.items.size)
    val sub = listOfNotNull(
        game.yearLabel.takeIf { it.isNotBlank() },
        countLabel,
    ).joinToString(" · ")
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun JukeboxTrackRow(
    item: JukeboxCatalog.TrackItem,
    language: String = "en",
    subtitleExtra: String? = null,
    indexNumber: Int? = null,
    playCount: Int = 0,
    packDirectory: java.io.File? = null,
    playing: Boolean = false,
    allowPlayNext: Boolean = true,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onOpenMenu: (() -> Unit)? = null,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayFromHere: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val artworkName = item.ostIndex?.let { "t_%03d.webp".format(java.util.Locale.ROOT, it) }
    val containerColor = when {
        selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        playing -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.surface
    }
    val shape = RoundedCornerShape(14.dp)
    Surface(
        color = containerColor,
        shape = shape,
        tonalElevation = if (playing || selected) 2.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(
                onClick = onPlay,
                onLongClick = {
                    val action = onLongClick ?: onOpenMenu
                    if (action != null) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        action()
                    }
                },
            ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onPlay() },
                )
            }
            PackArtwork.Thumbnail(
                packDirectory, listOfNotNull(artworkName), Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
            ) {
                if (playing) {
                    Icon(Icons.Filled.PlayArrow, null, tint = MaterialTheme.colorScheme.primary)
                } else {
                    Icon(Icons.Outlined.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    if (playCount > 0) add(stringResource(R.string.play_count_fmt, playCount))
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
            if (!selectionMode) {
                Box {
                    IconButton(onClick = {
                        if (onOpenMenu != null) onOpenMenu() else menuOpen = true
                    }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.jukebox_more_actions),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (onOpenMenu == null) {
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (allowPlayNext) stringResource(R.string.jukebox_play_next)
                                        else "${stringResource(R.string.jukebox_play_next)} (${stringResource(R.string.route_play_next_disabled)})",
                                    )
                                },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
                                enabled = allowPlayNext,
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
    }
}

/**
 * Detail page for one original reference game in the Jukebox (#98-#104), aligned with
 * the HTML prototype's `gamepage()`: chronological game header, OST track sequence,
 * independent rows for every arrangement, and Play All / Shuffle / Play Next actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiscoveryJukeboxGame(
    game: JukeboxCatalog.Game?,
    language: String,
    activeTrackKey: String?,
    packDirectory: java.io.File? = null,
    allowPlayNext: Boolean = true,
    onPlayFromIndex: (JukeboxCatalog.Game, Int, Boolean) -> Unit,
    onPlayNext: (JukeboxCatalog.TrackItem, JukeboxCatalog.Game) -> Unit,
    playlists: List<com.mylittleblover.mkwplayer.library.UserPlaylist> = emptyList(),
    onAddToPlaylist: (playlistId: String, refs: List<String>) -> Unit = { _, _ -> },
    onCreatePlaylistAndAdd: (name: String, refs: List<String>) -> Unit = { _, _ -> },
    onAddToQueueRefs: (List<String>) -> Boolean = { false },
    onPlaySelectedRefs: (List<String>) -> Boolean = { false },
    favorites: Set<String> = emptySet(),
    onToggleFavorite: (String) -> Unit = {},
    playStats: Map<String, com.mylittleblover.mkwplayer.library.TrackPlayStat> = emptyMap(),
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
    val addedQueueText = stringResource(R.string.track_action_added_to_queue)
    val localizedTitle = game.localizedName(language)
    val englishTitle = game.localizedNames["en"]

    var sortOrder by rememberSaveable(game.id) { mutableStateOf(com.mylittleblover.mkwplayer.library.TrackSortOrder.DEFAULT) }
    var menuTarget by remember { mutableStateOf<TrackMenuSpec?>(null) }
    var infoTarget by remember { mutableStateOf<TrackMenuSpec?>(null) }
    var playlistPickerRefs by remember { mutableStateOf<List<String>?>(null) }
    var multiSelectMode by rememberSaveable(game.id) { mutableStateOf(false) }
    var selectedKeys by rememberSaveable(game.id) { mutableStateOf(listOf<String>()) }
    val selectedSet = remember(selectedKeys) { selectedKeys.toSet() }

    androidx.activity.compose.BackHandler(enabled = multiSelectMode) {
        multiSelectMode = false
        selectedKeys = emptyList()
    }

    val trackSpecs = remember(game, language) {
        game.items.mapIndexed { idx, item ->
            val refRaw = com.mylittleblover.mkwplayer.library.PlaylistItemRef.Jukebox(item.key).raw
            val art = item.ostIndex?.let { "t_%03d.webp".format(java.util.Locale.ROOT, it) }
            val infoPairs = buildList {
                add(R.string.track_info_game to localizedTitle)
                if (item.durationMs > 0L) add(R.string.track_info_duration to item.durationFormatted)
                item.ostIndex?.let { add(R.string.track_info_ost to "#$it") }
                add(R.string.track_info_ref to item.key)
            }
            TrackMenuSpec(
                selectionKey = item.key,
                playlistRef = refRaw,
                title = item.localizedTitle(language),
                subtitle = listOfNotNull(item.localizedArrangement(language), localizedTitle).joinToString(" · "),
                artworkName = art,
                favoriteId = refRaw,
                infoPairs = infoPairs,
                onPlayNow = { onPlayFromIndex(game, idx, false) },
                onPlayNextDirect = { onPlayNext(item, game) },
            )
        }
    }

    TrackActionOverlayHosts(
        menuTarget = menuTarget,
        onDismissMenu = { menuTarget = null },
        playlistPickerRefs = playlistPickerRefs,
        onOpenPlaylistPicker = { refs -> playlistPickerRefs = refs },
        onDismissPlaylistPicker = { playlistPickerRefs = null },
        infoTarget = infoTarget,
        onOpenInfo = { infoTarget = it },
        onDismissInfo = { infoTarget = null },
        packDirectory = packDirectory,
        allowPlayNext = allowPlayNext,
        playlists = playlists,
        favorites = favorites,
        onToggleFavorite = onToggleFavorite,
        onEnterMultiSelect = { key ->
            multiSelectMode = true
            selectedKeys = (selectedKeys + key).distinct()
        },
        onPlayNextRefs = { false },
        onAddToQueueRefs = { refs -> onAddToQueueRefs(refs) },
        onAddToPlaylist = { pid, refs ->
            onAddToPlaylist(pid, refs)
            multiSelectMode = false
            selectedKeys = emptyList()
        },
        onCreatePlaylistAndAdd = { name, refs ->
            onCreatePlaylistAndAdd(name, refs)
            multiSelectMode = false
            selectedKeys = emptyList()
        },
        snackbarHostState = snackbarHostState,
        addedNextText = addedNextText,
        addedQueueText = addedQueueText,
    )

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AnimatedVisibility(visible = multiSelectMode) {
                val allKeys = trackSpecs.map { it.selectionKey }
                val allSelected = allKeys.isNotEmpty() && selectedSet.containsAll(allKeys)
                MultiSelectTopBanner(
                    selectedCount = selectedSet.size,
                    allSelected = allSelected,
                    onToggleSelectAll = {
                        selectedKeys = if (allSelected) emptyList() else allKeys
                    },
                    onClose = {
                        multiSelectMode = false
                        selectedKeys = emptyList()
                    },
                )
            }
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
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
                if (game.items.size > 1) {
                    item(key = "arr-sort-bar") {
                        com.mylittleblover.mkwplayer.ui.components.TrackSortBar(
                            selected = sortOrder,
                            includeOstOrder = true,
                            onSelect = { sortOrder = it },
                        )
                    }
                }
                val indexedItems = when (sortOrder) {
                    com.mylittleblover.mkwplayer.library.TrackSortOrder.DEFAULT ->
                        game.items.mapIndexed { idx, item -> idx to item }
                    com.mylittleblover.mkwplayer.library.TrackSortOrder.OST_INDEX ->
                        game.items.mapIndexed { idx, item -> idx to item }
                            .sortedWith(compareBy<Pair<Int, JukeboxCatalog.TrackItem>> { it.second.ostIndex ?: Int.MAX_VALUE }.thenBy { it.first })
                    com.mylittleblover.mkwplayer.library.TrackSortOrder.NAME_ASC ->
                        game.items.mapIndexed { idx, item -> idx to item }
                            .sortedWith(compareBy<Pair<Int, JukeboxCatalog.TrackItem>> { it.second.localizedTitle(language).lowercase() }.thenBy { it.first })
                    com.mylittleblover.mkwplayer.library.TrackSortOrder.DURATION_DESC ->
                        game.items.mapIndexed { idx, item -> idx to item }
                            .sortedWith(compareByDescending<Pair<Int, JukeboxCatalog.TrackItem>> { it.second.durationMs }.thenBy { it.first })
                    com.mylittleblover.mkwplayer.library.TrackSortOrder.MOST_PLAYED ->
                        game.items.mapIndexed { idx, item -> idx to item }
                            .sortedWith(
                                compareByDescending<Pair<Int, JukeboxCatalog.TrackItem>> {
                                    playStats[com.mylittleblover.mkwplayer.library.PlaylistItemRef.Jukebox(it.second.key).raw]?.playCount ?: 0
                                }.thenBy { it.first },
                            )
                }
                items(indexedItems, key = { (_, item) -> "jb-game:${item.key}" }) { (origIdx, item) ->
                    val spec = trackSpecs[origIdx]
                    val isChecked = spec.selectionKey in selectedSet
                    val refRaw = com.mylittleblover.mkwplayer.library.PlaylistItemRef.Jukebox(item.key).raw
                    val plays = playStats[refRaw]?.playCount ?: 0
                    val isFav = refRaw in favorites
                    com.mylittleblover.mkwplayer.ui.components.SwipeToTrackActionBox(
                        enabled = !multiSelectMode,
                        onSwipeStartToEnd = if (allowPlayNext) {
                            {
                                onPlayNext(item, game)
                                scope.launch { snackbarHostState.showSnackbar(addedNextText) }
                            }
                        } else null,
                        onSwipeEndToStart = { onToggleFavorite(refRaw) },
                        endToStartIcon = if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        endToStartLabel = stringResource(if (isFav) R.string.swipe_action_unfavorite else R.string.swipe_action_favorite),
                    ) {
                        JukeboxTrackRow(
                            item = item,
                            language = language,
                            playCount = plays,
                            packDirectory = packDirectory,
                            playing = activeTrackKey == item.key,
                            allowPlayNext = allowPlayNext,
                            selectionMode = multiSelectMode,
                            selected = isChecked,
                            onLongClick = {
                                if (multiSelectMode) {
                                    selectedKeys = if (isChecked) selectedKeys - spec.selectionKey else selectedKeys + spec.selectionKey
                                } else {
                                    menuTarget = spec
                                }
                            },
                            onOpenMenu = { menuTarget = spec },
                            onPlay = {
                                if (multiSelectMode) {
                                    selectedKeys = if (isChecked) selectedKeys - spec.selectionKey else selectedKeys + spec.selectionKey
                                } else {
                                    onPlayFromIndex(game, origIdx, false)
                                }
                            },
                            onPlayNext = {
                                onPlayNext(item, game)
                                scope.launch { snackbarHostState.showSnackbar(addedNextText) }
                            },
                            onPlayFromHere = { onPlayFromIndex(game, origIdx, false) },
                        )
                    }
                }
            }
            AnimatedVisibility(visible = multiSelectMode) {
                val chosenSpecs = trackSpecs.filter { it.selectionKey in selectedSet }
                val chosenRefs = chosenSpecs.mapNotNull { it.playlistRef }
                MultiSelectActionBar(
                    hasSelection = chosenRefs.isNotEmpty(),
                    allowPlayNext = allowPlayNext,
                    onPlaySelected = {
                        if (chosenRefs.isNotEmpty() && onPlaySelectedRefs(chosenRefs)) {
                            multiSelectMode = false
                            selectedKeys = emptyList()
                        }
                    },
                    onPlayNextSelected = {
                        chosenSpecs.asReversed().forEach { s -> s.onPlayNextDirect?.invoke() }
                        scope.launch { snackbarHostState.showSnackbar(addedNextText) }
                        multiSelectMode = false
                        selectedKeys = emptyList()
                    },
                    onAddToQueueSelected = {
                        if (chosenRefs.isNotEmpty() && onAddToQueueRefs(chosenRefs)) {
                            scope.launch { snackbarHostState.showSnackbar(addedQueueText) }
                        }
                        multiSelectMode = false
                        selectedKeys = emptyList()
                    },
                    onAddToPlaylistSelected = {
                        if (chosenRefs.isNotEmpty()) playlistPickerRefs = chosenRefs
                    },
                )
            }
        }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExtraSquareRow(
    title: String,
    subtitle: String,
    packDirectory: java.io.File?,
    artworkName: String?,
    fallbackCourseCode: String? = null,
    onAction: (() -> Unit)? = null,
    actionContentDescription: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    secondaryActionDescription: String? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val (pressModifier, interactionSource) = com.mylittleblover.mkwplayer.ui.rememberPressScale(0.98f)
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(16.dp)
    val bgColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    Surface(
        color = bgColor,
        shape = shape,
        tonalElevation = if (selected) 2.dp else 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .then(pressModifier)
            .clip(shape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = {
                    if (onLongClick != null) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    }
                },
            ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onClick() },
                )
            }
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
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!selectionMode) {
                if (onSecondaryAction != null) {
                    IconButton(onClick = onSecondaryAction) {
                        Icon(
                            Icons.Filled.Tune,
                            contentDescription = secondaryActionDescription,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (onAction != null) {
                    val (actMod, actSrc) = com.mylittleblover.mkwplayer.ui.rememberPressScale(0.94f)
                    FilledTonalIconButton(
                        onClick = onAction,
                        interactionSource = actSrc,
                        modifier = Modifier.size(44.dp).then(actMod),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = actionContentDescription, modifier = Modifier.size(20.dp))
                    }
                } else if (onSecondaryAction == null) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

internal data class TrackMenuSpec(
    val selectionKey: String,
    val playlistRef: String?,
    val title: String,
    val subtitle: String,
    val artworkName: String? = null,
    val fallbackCourseCode: String? = null,
    val favoriteId: String? = null,
    val courseCodeForDetail: String? = null,
    val editorTargetCode: String? = null,
    val infoPairs: List<Pair<Int, String>> = emptyList(),
    val onPlayNow: () -> Unit = {},
    val onPlayNextDirect: (() -> Unit)? = null,
    val playlistEntryIndex: Int? = null,
    val playlistCanMoveUp: Boolean = false,
    val playlistCanMoveDown: Boolean = false,
    val onPlaylistConfig: (() -> Unit)? = null,
    val onPlaylistMoveUp: (() -> Unit)? = null,
    val onPlaylistMoveDown: (() -> Unit)? = null,
    val onPlaylistRemove: (() -> Unit)? = null,
)

@Composable
private fun MultiSelectTopBanner(
    selectedCount: Int,
    allSelected: Boolean,
    onToggleSelectAll: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.discovery_cancel))
            }
            Text(
                text = stringResource(R.string.track_select_count, selectedCount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onToggleSelectAll) {
                Icon(Icons.Filled.SelectAll, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(if (allSelected) R.string.track_select_clear else R.string.track_select_all))
            }
        }
    }
}

@Composable
private fun MultiSelectActionBar(
    hasSelection: Boolean,
    allowPlayNext: Boolean,
    onPlaySelected: () -> Unit,
    onPlayNextSelected: () -> Unit,
    onAddToQueueSelected: () -> Unit,
    onAddToPlaylistSelected: () -> Unit,
    onRemoveSelected: (() -> Unit)? = null,
) {
    Surface(
        tonalElevation = 6.dp,
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilledTonalButton(onClick = onPlaySelected, enabled = hasSelection) {
                    Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.track_select_play))
                }
            }
            item {
                OutlinedButton(onClick = onPlayNextSelected, enabled = hasSelection && allowPlayNext) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.jukebox_play_next))
                }
            }
            item {
                OutlinedButton(onClick = onAddToQueueSelected, enabled = hasSelection && allowPlayNext) {
                    Icon(Icons.Filled.AddToQueue, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.track_action_add_to_queue))
                }
            }
            item {
                Button(onClick = onAddToPlaylistSelected, enabled = hasSelection) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.track_action_add_to_playlist))
                }
            }
            if (onRemoveSelected != null) {
                item {
                    OutlinedButton(
                        onClick = onRemoveSelected,
                        enabled = hasSelection,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Filled.DeleteOutline, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.track_select_remove_playlist))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackActionOverlayHosts(
    menuTarget: TrackMenuSpec?,
    onDismissMenu: () -> Unit,
    playlistPickerRefs: List<String>?,
    onOpenPlaylistPicker: (List<String>) -> Unit,
    onDismissPlaylistPicker: () -> Unit,
    infoTarget: TrackMenuSpec?,
    onOpenInfo: (TrackMenuSpec) -> Unit,
    onDismissInfo: () -> Unit,
    packDirectory: java.io.File?,
    allowPlayNext: Boolean,
    playlists: List<com.mylittleblover.mkwplayer.library.UserPlaylist>,
    favorites: Set<String> = emptySet(),
    onToggleFavorite: (String) -> Unit = {},
    onOpenCourse: ((String) -> Unit)? = null,
    onOpenFlowEditor: ((String) -> Unit)? = null,
    onEnterMultiSelect: (String) -> Unit,
    onPlayNextRefs: (List<String>) -> Boolean,
    onAddToQueueRefs: (List<String>) -> Boolean,
    onAddToPlaylist: (playlistId: String, refs: List<String>) -> Unit,
    onCreatePlaylistAndAdd: (name: String, refs: List<String>) -> Unit,
    snackbarHostState: SnackbarHostState,
    addedNextText: String,
    addedQueueText: String,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    if (menuTarget != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val isFav = menuTarget.favoriteId != null && menuTarget.favoriteId in favorites
        ModalBottomSheet(onDismissRequest = onDismissMenu, sheetState = sheetState) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PackArtwork.Thumbnail(
                        packDirectory,
                        listOfNotNull(menuTarget.artworkName),
                        Modifier.size(52.dp).clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        if (menuTarget.fallbackCourseCode != null) {
                            CourseCover(menuTarget.fallbackCourseCode, Modifier.fillMaxSize())
                        } else {
                            Icon(Icons.Outlined.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            menuTarget.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (menuTarget.subtitle.isNotBlank()) {
                            Text(
                                menuTarget.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (menuTarget.favoriteId != null) {
                        IconButton(onClick = { onToggleFavorite(menuTarget.favoriteId) }) {
                            Icon(
                                if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = stringResource(
                                    if (isFav) R.string.discovery_favorite_remove else R.string.discovery_favorite_add,
                                ),
                                tint = if (isFav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                ListItem(
                    headlineContent = { Text(stringResource(R.string.track_action_play_now)) },
                    leadingContent = { Icon(Icons.Filled.PlayArrow, null) },
                    modifier = Modifier.fillMaxWidth().clickable {
                        val action = menuTarget.onPlayNow
                        onDismissMenu()
                        action()
                    },
                )
                if (menuTarget.onPlayNextDirect != null || menuTarget.playlistRef != null) {
                    ListItem(
                        headlineContent = {
                            Text(
                                if (allowPlayNext) stringResource(R.string.jukebox_play_next)
                                else "${stringResource(R.string.jukebox_play_next)} (${stringResource(R.string.route_play_next_disabled)})",
                            )
                        },
                        leadingContent = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
                        modifier = Modifier.fillMaxWidth().clickable(enabled = allowPlayNext) {
                            val direct = menuTarget.onPlayNextDirect
                            val ref = menuTarget.playlistRef
                            onDismissMenu()
                            val ok = if (direct != null) {
                                direct(); true
                            } else if (ref != null) {
                                onPlayNextRefs(listOf(ref))
                            } else false
                            if (ok) scope.launch { snackbarHostState.showSnackbar(addedNextText) }
                        },
                    )
                }
                if (menuTarget.playlistRef != null) {
                    ListItem(
                        headlineContent = {
                            Text(
                                if (allowPlayNext) stringResource(R.string.track_action_add_to_queue)
                                else "${stringResource(R.string.track_action_add_to_queue)} (${stringResource(R.string.route_play_next_disabled)})",
                            )
                        },
                        leadingContent = { Icon(Icons.Filled.AddToQueue, null) },
                        modifier = Modifier.fillMaxWidth().clickable(enabled = allowPlayNext) {
                            val ref = menuTarget.playlistRef
                            onDismissMenu()
                            if (ref != null && onAddToQueueRefs(listOf(ref))) {
                                scope.launch { snackbarHostState.showSnackbar(addedQueueText) }
                            }
                        },
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.track_action_add_to_playlist)) },
                        leadingContent = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                        modifier = Modifier.fillMaxWidth().clickable {
                            val ref = menuTarget.playlistRef
                            onDismissMenu()
                            if (ref != null) onOpenPlaylistPicker(listOf(ref))
                        },
                    )
                }
                if (menuTarget.onPlaylistConfig != null) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.discovery_playlist_entry_config)) },
                        leadingContent = { Icon(Icons.Filled.Tune, null) },
                        modifier = Modifier.fillMaxWidth().clickable {
                            val cfg = menuTarget.onPlaylistConfig
                            onDismissMenu()
                            cfg()
                        },
                    )
                }
                if (menuTarget.courseCodeForDetail != null && onOpenCourse != null) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.track_action_open_course)) },
                        leadingContent = { Icon(Icons.Outlined.Album, null) },
                        modifier = Modifier.fillMaxWidth().clickable {
                            val code = menuTarget.courseCodeForDetail
                            onDismissMenu()
                            onOpenCourse(code)
                        },
                    )
                }
                if (menuTarget.editorTargetCode != null && onOpenFlowEditor != null) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.discovery_open_flow_editor)) },
                        leadingContent = { Icon(Icons.Filled.Tune, null) },
                        modifier = Modifier.fillMaxWidth().clickable {
                            val editor = menuTarget.editorTargetCode
                            onDismissMenu()
                            onOpenFlowEditor(editor)
                        },
                    )
                }
                if (menuTarget.onPlaylistMoveUp != null && menuTarget.playlistCanMoveUp) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.discovery_move_up)) },
                        leadingContent = { Icon(Icons.Filled.KeyboardArrowUp, null) },
                        modifier = Modifier.fillMaxWidth().clickable {
                            val act = menuTarget.onPlaylistMoveUp
                            onDismissMenu()
                            act()
                        },
                    )
                }
                if (menuTarget.onPlaylistMoveDown != null && menuTarget.playlistCanMoveDown) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.discovery_move_down)) },
                        leadingContent = { Icon(Icons.Filled.KeyboardArrowDown, null) },
                        modifier = Modifier.fillMaxWidth().clickable {
                            val act = menuTarget.onPlaylistMoveDown
                            onDismissMenu()
                            act()
                        },
                    )
                }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.track_action_multi_select)) },
                    leadingContent = { Icon(Icons.Outlined.CheckCircle, null) },
                    modifier = Modifier.fillMaxWidth().clickable {
                        val key = menuTarget.selectionKey
                        onDismissMenu()
                        onEnterMultiSelect(key)
                    },
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.track_action_info)) },
                    leadingContent = { Icon(Icons.Outlined.Info, null) },
                    modifier = Modifier.fillMaxWidth().clickable {
                        val info = menuTarget
                        onDismissMenu()
                        onOpenInfo(info)
                    },
                )
                if (menuTarget.onPlaylistRemove != null) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.discovery_remove), color = MaterialTheme.colorScheme.error) },
                        leadingContent = { Icon(Icons.Filled.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                        modifier = Modifier.fillMaxWidth().clickable {
                            val rem = menuTarget.onPlaylistRemove
                            onDismissMenu()
                            rem()
                        },
                    )
                }
            }
        }
    }

    if (playlistPickerRefs != null) {
        val refsToAdd = playlistPickerRefs
        var creatingNew by remember { mutableStateOf(false) }
        var newListName by remember { mutableStateOf("") }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = onDismissPlaylistPicker, sheetState = sheetState) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.track_action_add_to_playlist),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                OutlinedButton(
                    onClick = { creatingNew = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.track_action_new_playlist_and_add))
                }
                HorizontalDivider()
                if (playlists.isEmpty()) {
                    Text(
                        stringResource(R.string.track_playlist_pick_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                        items(playlists, key = { it.id }) { pl ->
                            ListItem(
                                headlineContent = { Text(pl.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text(stringResource(R.string.jukebox_tracks_count, pl.courseIds.size)) },
                                leadingContent = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null) },
                                modifier = Modifier.fillMaxWidth().clickable {
                                    onAddToPlaylist(pl.id, refsToAdd)
                                    val msg = context.getString(R.string.track_action_added_to_playlist, refsToAdd.size, pl.name)
                                    onDismissPlaylistPicker()
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                },
                            )
                        }
                    }
                }
            }
        }
        if (creatingNew) {
            AlertDialog(
                onDismissRequest = { creatingNew = false },
                title = { Text(stringResource(R.string.track_action_new_playlist_and_add)) },
                text = {
                    OutlinedTextField(
                        value = newListName,
                        onValueChange = { newListName = it.take(120) },
                        label = { Text(stringResource(R.string.discovery_playlist_name)) },
                        singleLine = true,
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = newListName.isNotBlank(),
                        onClick = {
                            val cleanName = newListName.trim()
                            onCreatePlaylistAndAdd(cleanName, refsToAdd)
                            val msg = context.getString(R.string.track_action_added_to_playlist, refsToAdd.size, cleanName)
                            creatingNew = false
                            onDismissPlaylistPicker()
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        },
                    ) {
                        Text(stringResource(R.string.discovery_create))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { creatingNew = false }) { Text(stringResource(R.string.discovery_cancel)) }
                },
            )
        }
    }

    if (infoTarget != null) {
        AlertDialog(
            onDismissRequest = onDismissInfo,
            title = { Text(stringResource(R.string.track_action_info)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(infoTarget.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (infoTarget.subtitle.isNotBlank()) {
                        Text(infoTarget.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider()
                    infoTarget.infoPairs.forEach { (labelRes, value) ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                stringResource(labelRes),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(value, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissInfo) { Text(stringResource(R.string.discovery_cancel)) }
            },
        )
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
            sourceType = com.mylittleblover.mkwplayer.player.LinearSessionStore.SourceType.EXTRA,
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
            sourceType = com.mylittleblover.mkwplayer.player.LinearSessionStore.SourceType.EXTRA,
            ostIndex = item.ostIndex,
            durationMs = item.durationMs,
            artworkName = item.artwork,
            overlaidOpening = item,
        )
        is ExtraMusicCatalog.OtherMusicItem.ChainedSequence -> com.mylittleblover.mkwplayer.player.LinearQueue.Entry(
            key = "extra:${item.id}",
            title = title,
            subtitle = categoryLabel,
            sourceType = com.mylittleblover.mkwplayer.player.LinearSessionStore.SourceType.EXTRA,
            ostIndex = item.ostIndex,
            durationMs = item.durationMs,
            artworkName = item.artwork,
            chainedSequence = item,
        )
        is ExtraMusicCatalog.OtherMusicItem.InteractiveTitle -> com.mylittleblover.mkwplayer.player.LinearQueue.Entry(
            key = "extra:${item.id}",
            title = title,
            subtitle = categoryLabel,
            sourceType = com.mylittleblover.mkwplayer.player.LinearSessionStore.SourceType.EXTRA,
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
    sourceType = com.mylittleblover.mkwplayer.player.LinearSessionStore.SourceType.EXTRA,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiscoveryCustomRouteDetail(
    route: CustomRoute?,
    catalog: BrowseCatalog?,
    language: String,
    presets: List<com.mylittleblover.mkwplayer.library.CoursePreset>,
    installedJukebox: List<Pair<JukeboxCatalog.TrackItem, JukeboxCatalog.Game>>,
    packDirectory: java.io.File?,
    courseArtwork: Map<String, String>,
    report: RoadJukeboxCatalog.RouteValidationReport?,
    onPlayRoute: (CustomRoute) -> Unit,
    onEditOnMap: (CustomRoute) -> Unit,
    onToggleLoop: (Boolean) -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onRemoveLastStop: () -> Unit,
    onUpdateStopPreset: (Int, String?) -> Unit,
    onUpdateEdgeMusic: (Int, Boolean, String?) -> Unit,
    onOpenCourse: (String) -> Unit,
    onOpenFlowEditor: (String, String?) -> Unit,
    isPresetSelectable: (String, String?) -> Boolean = { _, ref -> ref != PresetRefs.SYSTEM_KNOCKOUT },
    onUpdateRuleMode: (RoadJukeboxCatalog.RoadMusicRuleMode) -> Unit = {},
) {
    if (route == null) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.discovery_empty))
        }
        return
    }
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var newName by rememberSaveable(route.id) { mutableStateOf(route.name) }
    var editingStopIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var pickingEdgeIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    val presetSheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val systemDefaultLabel = stringResource(R.string.preset_system_default)
    val systemKnockoutLabel = stringResource(R.string.preset_system_knockout)

    if (renaming) {
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(stringResource(R.string.route_rename)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it.take(120) },
                    label = { Text(stringResource(R.string.route_name_label)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { onRename(newName); renaming = false }, enabled = newName.isNotBlank()) {
                    Text(stringResource(R.string.route_save_changes))
                }
            },
            dismissButton = {
                TextButton(onClick = { renaming = false }) { Text(stringResource(R.string.discovery_cancel)) }
            },
        )
    }

    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.route_delete_confirm_title)) },
            text = { Text(route.name) },
            confirmButton = {
                TextButton(onClick = { deleting = false; onDelete() }) {
                    Text(stringResource(R.string.route_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.discovery_cancel)) }
            },
        )
    }

    val stopPresetIdx = editingStopIndex
    if (stopPresetIdx != null && stopPresetIdx in route.stops.indices) {
        val stop = route.stops[stopPresetIdx]
        val logicalCode = stop.stageCode.removeSuffix("_00")
        val matchingPresets = presets.filter {
            it.courseCode.equals(logicalCode, ignoreCase = true) && isPresetSelectable(logicalCode, it.id)
        }
        val knockoutSelectable = isPresetSelectable(logicalCode, PresetRefs.SYSTEM_KNOCKOUT)
        val defaultSelectable = isPresetSelectable(logicalCode, PresetRefs.SYSTEM_DEFAULT)
        val courseTitle = catalog?.displayName(logicalCode, language) ?: logicalCode
        ModalBottomSheet(sheetState = presetSheet, onDismissRequest = { editingStopIndex = null }) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.route_stop_preset_title, stopPresetIdx + 1, courseTitle),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.discovery_playlist_entry_preset),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column {
                    val isDefaultSelected = stop.presetId == null || stop.presetId == PresetRefs.SYSTEM_DEFAULT
                    if (defaultSelectable || isDefaultSelected) {
                        ListItem(
                            headlineContent = { Text(systemDefaultLabel) },
                            trailingContent = { RadioButton(selected = isDefaultSelected, onClick = null) },
                            modifier = Modifier.fillMaxWidth().clickable {
                                onUpdateStopPreset(stopPresetIdx, null)
                                editingStopIndex = null
                            },
                        )
                    }
                    if (knockoutSelectable) {
                        ListItem(
                            headlineContent = { Text(systemKnockoutLabel) },
                            trailingContent = { RadioButton(selected = stop.presetId == PresetRefs.SYSTEM_KNOCKOUT, onClick = null) },
                            modifier = Modifier.fillMaxWidth().clickable {
                                onUpdateStopPreset(stopPresetIdx, PresetRefs.SYSTEM_KNOCKOUT)
                                editingStopIndex = null
                            },
                        )
                    }
                    matchingPresets.forEach { preset ->
                        ListItem(
                            headlineContent = { Text(preset.name) },
                            trailingContent = { RadioButton(selected = stop.presetId == preset.id, onClick = null) },
                            modifier = Modifier.fillMaxWidth().clickable {
                                onUpdateStopPreset(stopPresetIdx, preset.id)
                                editingStopIndex = null
                            },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            editingStopIndex = null
                            onOpenFlowEditor(logicalCode, stop.presetId)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Edit, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.preset_new))
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    val pickEdgeIdx = pickingEdgeIndex
    val activeLeg = if (pickEdgeIdx != null) report?.legs?.getOrNull(pickEdgeIdx) else null
    if (activeLeg != null && pickEdgeIdx != null) {
        val edgeCfg = route.edgeFor(pickEdgeIdx)
        val fromTitle = catalog?.displayName(activeLeg.from, language) ?: activeLeg.from
        val toTitle = catalog?.displayName(activeLeg.to, language) ?: activeLeg.to
        RouteEdgeMusicPickerSheet(
            legTitle = "$fromTitle → $toTitle",
            leg = activeLeg,
            installedJukebox = installedJukebox,
            allowCustomSubstitute = true,
            allowMute = true,
            sessionHint = false,
            language = language,
            packDirectory = packDirectory,
            currentSelectedKey = edgeCfg?.selectedJukeboxKey,
            currentMuted = edgeCfg?.musicEnabled == false,
            onSelectAuto = { onUpdateEdgeMusic(pickEdgeIdx, true, null) },
            onSelectMuted = { onUpdateEdgeMusic(pickEdgeIdx, false, null) },
            onSelectTrackKey = { trackKey -> onUpdateEdgeMusic(pickEdgeIdx, true, trackKey) },
            onDismiss = { pickingEdgeIndex = null },
        )
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(route.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "${stringResource(R.string.route_stops_count, route.stops.size)} · ${stringResource(R.string.route_legs_count, (route.stops.size - 1).coerceAtLeast(0))}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = null)
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.route_rename)) },
                                    leadingIcon = { Icon(Icons.Filled.Edit, null) },
                                    onClick = { menuOpen = false; newName = route.name; renaming = true },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.route_delete), color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Filled.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = { menuOpen = false; deleting = true },
                                )
                            }
                        }
                    }

                    if (report?.canPlay == false) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(Icons.Filled.WarningAmber, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                                Text(
                                    report.blockingReason ?: stringResource(R.string.route_invalid_topology),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onPlayRoute(route) },
                            enabled = report?.canPlay != false,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.route_play))
                        }
                        OutlinedButton(onClick = { onEditOnMap(route) }) {
                            Icon(Icons.Filled.Map, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.route_edit_on_map))
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.route_loop_whole), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.route_loop_whole_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = route.loopWholeRoute, onCheckedChange = onToggleLoop)
                    }

                    RoadMusicRuleModeSelector(
                        selectedMode = route.roadRuleMode,
                        officialKind = null,
                        onSelectMode = onUpdateRuleMode,
                    )

                    Text(
                        stringResource(R.string.route_tail_only_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        itemsIndexed(route.stops, key = { idx, stop -> "route-stop:$idx:${stop.stageCode}" }) { idx, stop ->
            val logical = stop.stageCode.removeSuffix("_00")
            val stopTitle = catalog?.displayName(logical, language) ?: logical
            val presetLabel = when {
                stop.presetId == PresetRefs.SYSTEM_KNOCKOUT && isPresetSelectable(logical, PresetRefs.SYSTEM_KNOCKOUT) -> systemKnockoutLabel
                stop.presetId == null || stop.presetId == PresetRefs.SYSTEM_DEFAULT -> systemDefaultLabel
                else -> presets.firstOrNull {
                    it.id == stop.presetId && isPresetSelectable(logical, it.id)
                }?.name ?: systemDefaultLabel
            }
            val artwork = courseArtwork[logical] ?: courseArtwork["${logical}_00"]
            val isLastRemovableTail = idx == route.stops.lastIndex && route.stops.size > 2
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Card(
                    onClick = { onOpenCourse(logical) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            "${idx + 1}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        PackArtwork.Thumbnail(
                            packDirectory,
                            listOfNotNull(artwork),
                            Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
                        ) {
                            CourseCover(logical, Modifier.fillMaxSize())
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(stopTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(presetLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { editingStopIndex = idx }) {
                            Icon(Icons.Filled.Tune, contentDescription = stringResource(R.string.discovery_playlist_entry_preset))
                        }
                        if (isLastRemovableTail) {
                            IconButton(onClick = onRemoveLastStop) {
                                Icon(
                                    Icons.Filled.RemoveCircleOutline,
                                    contentDescription = stringResource(R.string.route_tail_delete_stop),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }

                val leg = report?.legs?.getOrNull(idx)
                val edgeCfg = if (idx < route.stops.lastIndex) route.edgeFor(idx) else null
                if (idx < route.stops.lastIndex) {
                    val nextLogical = route.stops[idx + 1].stageCode.removeSuffix("_00")
                    val fromName = catalog?.displayName(logical, language) ?: logical
                    val toName = catalog?.displayName(nextLogical, language) ?: nextLogical
                    val summary = if (leg != null) {
                        edgeResolutionSummary(leg.resolution, language)
                    } else {
                        stringResource(R.string.route_edge_music_auto)
                    }
                    Surface(
                        onClick = { pickingEdgeIndex = idx },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                if (edgeCfg?.musicEnabled != false) Icons.Outlined.MusicNote else Icons.Filled.MusicOff,
                                null,
                                Modifier.size(18.dp),
                                tint = if (leg?.resolution?.isMissingOrIncomplete() == true || leg?.connectionValid == false) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${stringResource(R.string.route_road_prefix)} $fromName → $toName",
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (leg?.resolution?.isMissingOrIncomplete() == true) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(Icons.Filled.Tune, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
