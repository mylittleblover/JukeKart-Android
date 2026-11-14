package com.mylittleblover.mkwplayer

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mylittleblover.mkwplayer.R
import com.mylittleblover.mkwplayer.player.PlayerViewModel
import com.mylittleblover.mkwplayer.ui.NavState
import com.mylittleblover.mkwplayer.ui.Tokens
import com.mylittleblover.mkwplayer.ui.Tab
import com.mylittleblover.mkwplayer.ui.UiMapping
import com.mylittleblover.mkwplayer.ui.detail.DetailScreen
import com.mylittleblover.mkwplayer.ui.library.LibraryScreen
import com.mylittleblover.mkwplayer.ui.play.PlayScreen
import com.mylittleblover.mkwplayer.ui.AppHeader
import com.mylittleblover.mkwplayer.ui.tabTitle
import com.mylittleblover.mkwplayer.ui.TabIcon
import com.mylittleblover.mkwplayer.ui.settings.AppSettingsScreen
import com.mylittleblover.mkwplayer.i18n.AppLanguageSettings
import com.mylittleblover.mkwplayer.ui.routes.OfficialMapScreen
import com.mylittleblover.mkwplayer.ui.routes.WorldMapState
import com.mylittleblover.mkwplayer.routes.OfficialRouteCatalog
import com.mylittleblover.mkwplayer.routes.LocalizedNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.mylittleblover.mkwplayer.ui.routes.RoutesScreen
import com.mylittleblover.mkwplayer.ui.settings.importItems
import com.mylittleblover.mkwplayer.ui.settings.settingsItems

/**
 * The app: four tabs and the pages reached from them.
 *
 * Every screen in the build is reachable from here. Before this, each one existed and was tested but nothing
 * showed it -- a page nobody can open is not a feature, and a single scrolling test surface is not an app.
 *
 * The console deliberately sits above the tabs rather than inside one. It is the operator's controls for the
 * performance that is playing right now, and burying it inside the library would mean losing it whenever the
 * listener looks at the map.
 */
@Composable
internal fun MkwApp(
    model: PlayerViewModel,
    ui: PlayerViewModel.Ui,
    modifier: Modifier = Modifier,
) {
    var nav by rememberSaveable(stateSaver = listSaver<NavState, String>(save = { it.snapshot() }, restore = { NavState.restore(it) })) { mutableStateOf(NavState()) }
    val mapState = rememberSaveable(saver = WorldMapState.Saver) { WorldMapState() }
    var mediaCategory by rememberSaveable { mutableIntStateOf(-1) }
    var mediaQuery by rememberSaveable { mutableStateOf("") }

    // The pack picker. It is the only import path a normal listener needs: one file carries the audio, the
    // stage structures and the timings. Kept here rather than inside a page so the library's empty state and
    // the console's import button open the same picker.
    val packPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) model.importPack(uri)
    }
    val openPack = { packPicker.launch(arrayOf("*/*")) }

    // The single-file picker, kept because a listener who has a handful of .bwav files should not have to
    // build a pack to hear them.
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) model.openFlow(uris)
    }
    val openFiles = { filePicker.launch(arrayOf("audio/*", "*/*")) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> model.onNotificationPermissionResult(granted) }

    LaunchedEffect(ui.needsNotificationPermission) {
        if (ui.needsNotificationPermission) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    BackHandler(enabled = nav.canGoBack) { nav = nav.back() ?: nav }
    val context = LocalContext.current
    val language = AppLanguageSettings.current(context)
    val languageTag = LocalConfiguration.current.locales[0].toLanguageTag()
    val discovery: com.mylittleblover.mkwplayer.ui.home.DiscoveryViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val discoveryState by discovery.state.collectAsStateWithLifecycle()
    val browse = remember(model) {
        runCatching {
            val anchors = requireNotNull(model.mapAnchors)
            val official = requireNotNull(OfficialRouteCatalog.parse(
                context.assets.open("official_routes.json").bufferedReader().use { it.readText() },
                anchors.stages.map { it.code }.toSet()))
            val names = requireNotNull(LocalizedNames.parse(context.assets.open("localized_names.json").bufferedReader().use { it.readText() }))
            com.mylittleblover.mkwplayer.library.BrowseCatalog.create(official, names, anchors)
        }.getOrNull()
    }
    val unlocked = com.mylittleblover.mkwplayer.ui.CupVisibility.mayReveal(ui.unlock)
    val eligible = remember(browse, unlocked, ui.pack, ui.flow?.title) {
        browse?.courses(unlocked).orEmpty().map { id ->
            com.mylittleblover.mkwplayer.library.RecommendationCandidate(id,
                com.mylittleblover.mkwplayer.library.RecommendationKind.COURSE, model.canStartStage(id))
        }
    }
    LaunchedEffect(eligible) { discovery.updateEligible(eligible) }
    val playingCourse = model.currentStageCode
    var lastRecordedCourse by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(ui.canStop, ui.canPause, ui.flow?.voiceCount, playingCourse) {
        if (!ui.canStop) lastRecordedCourse = null
        else if (ui.canPause && playingCourse != null && (ui.flow?.voiceCount ?: 0) > 0 && lastRecordedCourse != playingCourse) {
            discovery.recordRecent(playingCourse)
            lastRecordedCourse = playingCourse
        }
    }
    val configuration = LocalConfiguration.current
    val sideNavigation = configuration.screenWidthDp >= 600 ||
        (configuration.screenWidthDp > configuration.screenHeightDp && configuration.screenHeightDp < 500)
    val playerDetail = nav.detail == NavState.Detail.Flow || nav.detail == NavState.Detail.Track ||
        ((nav.detail as? NavState.Detail.Interactive)?.let { model.isStageOpen(it.code) } == true)
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            val title = when (nav.detail) {
                NavState.Detail.Settings -> androidx.compose.ui.res.stringResource(R.string.settings_title)
                is NavState.Detail.Course -> androidx.compose.ui.res.stringResource(R.string.nav_course)
                is NavState.Detail.Cup -> androidx.compose.ui.res.stringResource(R.string.discovery_gp)
                NavState.Detail.Flow -> androidx.compose.ui.res.stringResource(R.string.discovery_interactive)
                NavState.Detail.Track -> androidx.compose.ui.res.stringResource(R.string.discovery_media)
                is NavState.Detail.Interactive -> androidx.compose.ui.res.stringResource(R.string.discovery_interactive)
                is NavState.Detail.Playlist -> androidx.compose.ui.res.stringResource(R.string.discovery_playlists)
                else -> tabTitle(nav.tab)
            }
            AppHeader(title, nav.canGoBack, nav.detail != NavState.Detail.Settings,
                onBack = { nav = nav.back() ?: nav }, onSettings = { nav = nav.open(NavState.Detail.Settings) })
        },
        bottomBar = {
            Column {
                if (playerDetail && ui.flow != null) {
                    com.mylittleblover.mkwplayer.ui.interactive.InteractiveStopBar(
                        canStop = ui.canStop, canFade = !ui.busy && model.canFadeStop, fading = ui.flow?.fading == true,
                        onStop = model::stop, onFade = model::fadeStopPerformance)
                } else if (playerDetail && nav.detail == NavState.Detail.Track) {
                    androidx.compose.material3.Surface {
                        OutlinedButton(onClick = model::stop, enabled = ui.canStop,
                            modifier = Modifier.fillMaxWidth().padding(12.dp).height(48.dp)) {
                            Text(androidx.compose.ui.res.stringResource(R.string.interactive_stop))
                        }
                    }
                } else {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = ui.canStop, enter = fadeIn(tween(180)), exit = fadeOut(tween(150))) {
                        val miniTitle = model.currentStageCode?.let { browse?.displayName(it, languageTag) } ?: ui.title
                        MiniPlayer(miniTitle, model.currentStageCode, ui,
                            onOpen = { nav = nav.open(if (ui.flow == null) NavState.Detail.Track else NavState.Detail.Flow) },
                            onToggle = { if (ui.canPause) model.pause() else if (ui.canResume) model.resume() })
                    }
                }
            if (!sideNavigation) NavigationBar {
                Tab.ordered().forEach { tab ->
                    NavigationBarItem(
                        selected = nav.tab == tab,
                        onClick = { if (tab == Tab.MEDIA) { mediaCategory = -1; mediaQuery = "" }; nav = nav.select(tab) },
                        label = { Text(tabTitle(tab)) },
                        icon = { TabIcon(tab, nav.tab == tab) },
                    )
                }
            }
            }
        },
    ) { inner ->
        Row(Modifier.padding(inner).fillMaxSize()) {
            if (sideNavigation) androidx.compose.material3.NavigationRail(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Tab.ordered().forEach { tab ->
                    androidx.compose.material3.NavigationRailItem(selected = nav.tab == tab,
                        onClick = { if (tab == Tab.MEDIA) { mediaCategory = -1; mediaQuery = "" }; nav = nav.select(tab) }, icon = { TabIcon(tab, nav.tab == tab) }, label = { Text(tabTitle(tab)) })
                }
            }
            AnimatedContent(targetState = nav.snapshot(), modifier = Modifier.weight(1f).fillMaxSize(),
                transitionSpec = { (fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 32 }) togetherWith ExitTransition.None },
                label = "page-transition") { snapshot ->
            val page = NavState.restore(snapshot)
            val detail = page.detail
            when {
                detail == NavState.Detail.Settings -> AppSettingsScreen(
                    language = language, onLanguage = { AppLanguageSettings.select(context, it) },
                    busy = ui.busy, sourceName = ui.pack?.name, onOpenPack = openPack, onOpenFiles = openFiles,
                    testMode = ui.unlockTestMode,
                    testUnlockVisible = com.mylittleblover.mkwplayer.ui.CupVisibility.mayReveal(ui.unlock),
                    onTestUnlock = model::setUnlockTestMode,
                    sourceCount = ui.pack?.trackCount, playableCount = ui.pack?.playableCount,
                    importProgress = ui.pack?.progress.orEmpty(), importDetail = ui.detail,
                )
                detail is NavState.Detail.Course -> {
                    if (browse == null || detail.code.removeSuffix("_00") !in browse.courses(unlocked)) {
                        Text(androidx.compose.ui.res.stringResource(R.string.discovery_empty), Modifier.padding(24.dp))
                    } else CoursePage(model, ui, detail.code) {
                        nav = nav.open(NavState.Detail.Interactive(detail.code))
                    }
                }
                detail is NavState.Detail.Interactive -> {
                    if (browse == null || detail.code.removeSuffix("_00") !in browse.courses(unlocked)) {
                        Text(androidx.compose.ui.res.stringResource(R.string.discovery_empty), Modifier.padding(24.dp))
                    } else if (model.isStageOpen(detail.code)) ConsolePage(model, ui, openPack, openFiles)
                    else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        com.mylittleblover.mkwplayer.ui.art.CoursePlaceholder(detail.code,
                            Modifier.fillMaxWidth().aspectRatio(1.7f).clip(MaterialTheme.shapes.large))
                        Text(browse?.displayName(detail.code, languageTag) ?: detail.code, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(androidx.compose.ui.res.stringResource(R.string.discovery_interactive_note), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (model.canStartStage(detail.code)) {
                            val entryNames = remember(detail.code, ui.pack) { model.initialStageLabels(detail.code) }
                            var selectedEntry by rememberSaveable(detail.code) { mutableStateOf(entryNames.firstOrNull()) }
                            val choices = entryNames.mapIndexed { index, name -> index to name }.toMap()
                            val effectiveEntry = selectedEntry?.takeIf { it in entryNames } ?: entryNames.firstOrNull()
                            com.mylittleblover.mkwplayer.ui.interactive.InteractiveEntryChooser(
                                choices = choices, selected = entryNames.indexOf(effectiveEntry).takeIf { it >= 0 }, enabled = !ui.busy && ui.flow?.fading != true,
                                onSelect = { index -> selectedEntry = entryNames.getOrNull(index) })
                            Button(onClick = { model.startStagePerformance(detail.code, effectiveEntry) }, enabled = !ui.busy && ui.flow?.fading != true) {
                                Text(androidx.compose.ui.res.stringResource(R.string.discovery_start))
                            }
                        } else {
                            Text(androidx.compose.ui.res.stringResource(R.string.interactive_empty))
                            OutlinedButton(onClick = openPack) { Text(androidx.compose.ui.res.stringResource(R.string.discovery_import)) }
                        }
                    }
                }
                detail is NavState.Detail.Playlist -> com.mylittleblover.mkwplayer.ui.home.DiscoveryPlaylist(
                    discoveryState.playlists.firstOrNull { it.id == detail.id }, browse, unlocked, languageTag,
                    discoveryState.playlistError != null,
                    onAdd = { discovery.addToPlaylist(detail.id, it) }, onRemove = { discovery.removeFromPlaylist(detail.id, it) },
                    onRename = { discovery.renamePlaylist(detail.id, it) }, onMove = { from, to -> discovery.moveInPlaylist(detail.id, from, to) },
                    onDelete = { discovery.deletePlaylist(detail.id); nav = nav.back() ?: nav },
                    onCourse = { nav = nav.open(NavState.Detail.Course(it)) })
                detail is NavState.Detail.Cup -> com.mylittleblover.mkwplayer.ui.home.DiscoveryCup(
                    browse?.route(detail.id, unlocked), browse, languageTag,
                    onMap = { id ->
                        val route = browse?.route(id, unlocked)
                        val points = route?.stops.orEmpty().mapNotNull { stop -> model.mapAnchors?.stageTolerant(stop.stageCode)?.let { androidx.compose.ui.geometry.Offset(it.u, it.v) } }
                        mapState.selectRoute(id, points)
                        nav = nav.select(Tab.WORLD_MAP)
                    }) { code -> nav = nav.open(NavState.Detail.Course(code)) }
                detail == NavState.Detail.Flow -> ConsolePage(model, ui, openPack, openFiles)
                detail == NavState.Detail.Track -> SingleTrackPlayer(model, ui)
                else -> when (page.tab) {
                    Tab.HOME -> com.mylittleblover.mkwplayer.ui.home.DiscoveryHome(browse, discoveryState, languageTag, unlocked,
                        onOpen = { nav = nav.open(NavState.Detail.Course(it)) }, onFavorite = discovery::toggleFavorite,
                        onImport = openPack, onPlayer = { nav = nav.open(if (ui.flow == null) NavState.Detail.Track else NavState.Detail.Flow) },
                        onToggle = { if (ui.canPause) model.pause() else if (ui.canResume) model.resume() },
                        onMedia = { mediaCategory = -1; mediaQuery = ""; nav = nav.select(Tab.MEDIA) },
                        session = ui, currentCode = model.currentStageCode)
                    Tab.MEDIA -> com.mylittleblover.mkwplayer.ui.home.DiscoveryMedia(browse, unlocked, languageTag,
                        category = mediaCategory, onCategory = { mediaCategory = it }, query = mediaQuery, onQuery = { mediaQuery = it },
                        recentIds = discoveryState.recentIds,
                        onCourse = { nav = nav.open(NavState.Detail.Course(it)) }, onCup = { nav = nav.open(NavState.Detail.Cup(it)) })
                    Tab.WORLD_MAP -> MapPage(model, ui, mapState) { code -> nav = nav.open(NavState.Detail.Course(code)) }
                    Tab.PLAYLISTS -> com.mylittleblover.mkwplayer.ui.home.DiscoveryPlaylists(browse, unlocked, languageTag,
                        discoveryState, onCreate = discovery::createPlaylist,
                        onPlaylist = { nav = nav.open(NavState.Detail.Playlist(it)) },
                        onCup = { nav = nav.open(NavState.Detail.Cup(it)) })
                }
            }
            }
        }
    }
}

/**
 * The library, with the two import entries at the top.
 *
 * The pack entry is first and named as the main path, because it is: one file, one action, and a whole
 * soundtrack. The file entry stays below it for a listener who has loose `.bwav` files and should not have to
 * build a pack to hear them.
 */
@Composable
private fun LibraryPage(
    model: PlayerViewModel,
    ui: PlayerViewModel.Ui,
    onOpenPack: () -> Unit,
    onOpenFiles: () -> Unit,
) {
    val catalogState by (model.catalogState?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(null) })

    Column(Modifier.fillMaxSize()) {
        Card(Modifier.fillMaxWidth().padding(Tokens.Spacing.md)) {
            Column(Modifier.padding(Tokens.Spacing.md), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Open your soundtrack", style = MaterialTheme.typography.titleMedium)

                if (ui.pack != null) {
                    Text(ui.pack!!.summary, style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(
                        "A pack carries the audio, the stage structures and the timings in one file, so one " +
                            "import is enough.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onOpenPack, enabled = !ui.busy) {
                        Text(if (ui.busy) "Working..." else "Import a pack")
                    }
                    OutlinedButton(onClick = onOpenFiles, enabled = !ui.busy) {
                        Text("Add .bwav files")
                    }
                }

                if (ui.pack != null) {
                    OutlinedButton(onClick = { model.openFirstPackStage() }) {
                        Text("Open the first stage")
                    }
                }

                ui.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }

        LibraryScreen(
            header = UiMapping.libraryHeader(ui, catalogState),
            categories = UiMapping.libraryCategories(catalogState, ui.unlock),
            state = com.mylittleblover.mkwplayer.ui.library.stateFor(
                // The catalogue exists when the browser has rows; "available" tracks the parse, not the page.
                hasCatalogue = catalogState != null && catalogState!!.rows.isNotEmpty(),
                hasTracks = ui.tracks.isNotEmpty(),
                isPartial = ui.catalog?.isPartial == true,
                busy = ui.busy,
            ),
            onCategory = { model.toggleCategory(it) },
            onSearch = {},
            modifier = Modifier.weight(1f),
        )
    }
}

/** A course's detail page, reached from the map or the library. */
@Composable
private fun CoursePage(model: PlayerViewModel, ui: PlayerViewModel.Ui, code: String,onInteractive: () -> Unit) {
    val context = LocalContext.current
    val languageTag = LocalConfiguration.current.locales[0].toLanguageTag()
    val names = remember { runCatching { LocalizedNames.parse(context.assets.open("localized_names.json").bufferedReader().use { it.readText() }) }.getOrNull() }
    val title = names?.name(code, languageTag) ?: androidx.compose.ui.res.stringResource(R.string.map_course)
    val discovery: com.mylittleblover.mkwplayer.ui.home.DiscoveryViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val saved by discovery.state.collectAsStateWithLifecycle()
    val stableCode = code.removeSuffix("_00")
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                val favorite = stableCode in saved.favorites
                val label = androidx.compose.ui.res.stringResource(if (favorite) R.string.discovery_favorite_remove else R.string.discovery_favorite_add)
                androidx.compose.material3.IconButton(onClick = { discovery.toggleFavorite(stableCode) }, modifier = Modifier.semantics { contentDescription = label }) {
                    androidx.compose.material3.Icon(if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, contentDescription = null)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    com.mylittleblover.mkwplayer.ui.art.CoursePlaceholder(code, Modifier.fillMaxWidth().aspectRatio(1.7f))
                    Text(androidx.compose.ui.res.stringResource(R.string.discovery_placeholder),
                        modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.Icon(Icons.Outlined.Headphones, contentDescription = null, modifier = Modifier.size(32.dp))
                    Text(androidx.compose.ui.res.stringResource(R.string.discovery_normal_playback), style = MaterialTheme.typography.titleLarge)
                    Text(androidx.compose.ui.res.stringResource(R.string.discovery_suite), style = MaterialTheme.typography.titleSmall)
                    Text(androidx.compose.ui.res.stringResource(R.string.discovery_suite_note), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.Icon(Icons.Outlined.GraphicEq, contentDescription = null, modifier = Modifier.size(32.dp))
                    Text(androidx.compose.ui.res.stringResource(R.string.discovery_interactive), style = MaterialTheme.typography.titleLarge)
                    Text(androidx.compose.ui.res.stringResource(R.string.discovery_interactive_note), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onInteractive) { Text(androidx.compose.ui.res.stringResource(R.string.discovery_open_player)) }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(androidx.compose.ui.res.stringResource(R.string.stage_structure), style = MaterialTheme.typography.titleLarge)
                val current = ui.flow.takeIf { model.isStageOpen(code) }
                if (current != null) com.mylittleblover.mkwplayer.ui.interactive.InteractiveStructure(current)
                else Text(androidx.compose.ui.res.stringResource(R.string.stage_structure_open_hint), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * The world map: the shipped artwork, full screen, draggable and zoomable.
 *
 * The pins come from the anchor file the map was measured against, so a pin sits on its own diorama rather
 * than on a guess. The drag is clamped inside the map, and there is a reset button, because a map that can be
 * pushed off-screen with one flick is a map that has to be reloaded.
 */
@Composable
private fun MapPage(model: PlayerViewModel, ui: PlayerViewModel.Ui, state: WorldMapState, onOpenCourse: (String) -> Unit) {
    val context = LocalContext.current.applicationContext
    val languageTag = LocalConfiguration.current.locales[0].toLanguageTag()
    val loaded by produceState<Result<MapResources>?>(null, model) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val anchors = requireNotNull(model.mapAnchors)
                val routes = requireNotNull(OfficialRouteCatalog.parse(
                    context.assets.open("official_routes.json").bufferedReader().use { it.readText() },
                    anchors.stages.map { it.code }.toSet(),
                ))
                val names = requireNotNull(LocalizedNames.parse(context.assets.open("localized_names.json").bufferedReader().use { it.readText() }))
                val image = requireNotNull(com.mylittleblover.mkwplayer.ui.art.MapArtworkCache.load(context))
                MapResources(image, anchors, routes, names)
            }
        }
    }
    val data = loaded?.getOrNull()
    if (data == null) {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (loaded == null) androidx.compose.material3.CircularProgressIndicator()
                Text(androidx.compose.ui.res.stringResource(if (loaded == null) R.string.map_loading else R.string.map_data_missing))
            }
        }
        return
    }
    val playable = remember(ui.pack, ui.flow?.title, data) { data.routes.stageCodes(true).filter(model::canStartStage).toSet() }
    OfficialMapScreen(
        image = data.image, anchors = data.anchors, routes = data.routes, names = data.names,
        languageTag = languageTag, specialUnlocked = com.mylittleblover.mkwplayer.ui.CupVisibility.mayReveal(ui.unlock),
        playableStages = playable, state = state, onOpenCourse = onOpenCourse,
    )
}

private data class MapResources(
    val image: androidx.compose.ui.graphics.ImageBitmap,
    val anchors: com.mylittleblover.mkwplayer.library.MapAnchors.Catalog,
    val routes: OfficialRouteCatalog,
    val names: LocalizedNames,
)

/** The routes page. No saved routes exist yet, and it says so rather than showing an empty list. */
@Composable
private fun RoutesPage() {
    RoutesScreen(
        rows = emptyList(),
        saved = emptyList(),
        editor = com.mylittleblover.mkwplayer.ui.routes.RouteEditor.of(emptyList()),
        onSave = {},
        onRemove = {},
        modifier = Modifier.fillMaxSize(),
    )
}

/**
 * The performance console.
 *
 * Three events and one switch, each enabled only when it means something. A control that is always available
 * is a control that is sometimes a no-op, and the listener cannot tell which.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ConsolePage(
    model: PlayerViewModel,
    ui: PlayerViewModel.Ui,
    onOpenPack: () -> Unit,
    onOpenFiles: () -> Unit,
) {
    val flow = ui.flow
    val context = LocalContext.current
    val languageTag = LocalConfiguration.current.locales[0].toLanguageTag()
    val names = remember(context.assets) {
        runCatching { context.assets.open("localized_names.json").bufferedReader().use { LocalizedNames.parse(it.readText()) } }.getOrNull()
    }
    val displayTitle = model.currentStageCode
        ?.let { names?.name(it, languageTag) } ?: ui.title

    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).testTag("interactive-page"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item {
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth >= 640.dp) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(36.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            PlayerCover(model.currentStageCode, Modifier.weight(1f))
                            PlayerInformation(model, ui, displayTitle, Modifier.weight(1f))
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            PlayerCover(model.currentStageCode, Modifier.fillMaxWidth())
                            PlayerInformation(model, ui, displayTitle, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            if (flow != null) {
                item {
                    Text(androidx.compose.ui.res.stringResource(R.string.stage_actions), style = MaterialTheme.typography.titleLarge)
                }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            com.mylittleblover.mkwplayer.ui.interactive.InteractiveEntryChooser(
                                choices = model.initialEntryChoices, selected = model.selectedEntrySegment,
                                enabled = !ui.canStop && !ui.busy, onSelect = model::selectInitialEntry)
                            if (flow.performanceBlocker != null) Text(flow.performanceBlocker, style = MaterialTheme.typography.bodySmall)
                            else {
                                com.mylittleblover.mkwplayer.ui.interactive.InteractiveActionBar(flow,
                                    onAdvance = model::advancePerformance, onFast = model::toggleFast,
                                    onCancel = model::cancelPendingPerformance, onEnd = model::takeOutro)
                                if (flow.versionNumbers.isNotEmpty()) {
                                    Text(androidx.compose.ui.res.stringResource(R.string.recording_version), style = MaterialTheme.typography.titleSmall)
                                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        flow.versionNumbers.forEach { number ->
                                            FilterChip(selected = flow.selectedVersion == number,
                                                onClick = { if (flow.selectedVersion != number) model.selectRecordingVersion(number) },
                                                enabled = number in flow.availableVersions || number == flow.selectedVersion,
                                                label = { Text(androidx.compose.ui.res.stringResource(R.string.recording_version_number, number)) })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                com.mylittleblover.mkwplayer.ui.interactive.InteractiveCoursePicker(names,
                    com.mylittleblover.mkwplayer.ui.CupVisibility.mayReveal(ui.unlock), ui.busy || flow?.fading == true,
                    model::canStartStage, { model.startStagePerformance(it) })
                if (flow == null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onOpenPack) { Text(androidx.compose.ui.res.stringResource(R.string.interactive_import)) }
                    OutlinedButton(onClick = onOpenFiles) { Text(androidx.compose.ui.res.stringResource(R.string.interactive_files)) }
                }
                ui.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun PlayerCover(code: String?, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.material3.Surface(
            modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth().aspectRatio(1f),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) { com.mylittleblover.mkwplayer.ui.art.CoursePlaceholder(code, Modifier.fillMaxSize()) }
        Text(androidx.compose.ui.res.stringResource(R.string.discovery_placeholder), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun PlayerInformation(model: PlayerViewModel, ui: PlayerViewModel.Ui, title: String, modifier: Modifier = Modifier) {
    val flow = ui.flow
    val discovery: com.mylittleblover.mkwplayer.ui.home.DiscoveryViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val saved by discovery.state.collectAsStateWithLifecycle()
    val course = model.currentStageCode?.removeSuffix("_00")
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(androidx.compose.ui.res.stringResource(R.string.discovery_interactive),
            color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            if (course != null) {
                val favorite = course in saved.favorites
                val label = androidx.compose.ui.res.stringResource(if (favorite) R.string.discovery_favorite_remove else R.string.discovery_favorite_add)
                androidx.compose.material3.IconButton(onClick = { discovery.toggleFavorite(course) },
                    modifier = Modifier.semantics { contentDescription = label }) {
                    androidx.compose.material3.Icon(if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, contentDescription = null)
                }
            }
        }
        flow?.let {
            Text(com.mylittleblover.mkwplayer.ui.interactive.performanceStageText(it),
                style = MaterialTheme.typography.bodyMedium)
            com.mylittleblover.mkwplayer.ui.interactive.InteractivePlaybackTimeline(ui.position, it)
        } ?: Text(androidx.compose.ui.res.stringResource(R.string.interactive_empty))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            val actionLabel = when {
                ui.canPause -> androidx.compose.ui.res.stringResource(R.string.interactive_pause)
                ui.canResume -> androidx.compose.ui.res.stringResource(R.string.interactive_resume)
                else -> androidx.compose.ui.res.stringResource(R.string.interactive_start)
            }
            androidx.compose.material3.FilledIconButton(onClick = {
                when { ui.canPause -> model.pause(); ui.canResume -> model.resume(); else -> model.startPerformance() }
            }, enabled = flow != null && !ui.busy && (ui.canPause || ui.canResume || !flow.performing),
                modifier = Modifier.size(68.dp).semantics { contentDescription = actionLabel },
                colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.surface)) {
                androidx.compose.material3.Icon(if (ui.canPause) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(30.dp))
            }
        }
    }
}

@Composable
private fun MiniPlayer(title: String, courseCode: String?, ui: PlayerViewModel.Ui, onOpen: () -> Unit, onToggle: () -> Unit) {
    val flow = ui.flow
    val label = androidx.compose.ui.res.stringResource(if (ui.canPause) R.string.interactive_pause else R.string.interactive_resume)
    androidx.compose.material3.Surface(modifier = Modifier.testTag("mini-player").height(72.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp, shadowElevation = 2.dp) {
        Column {
            Row(Modifier.weight(1f).fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                courseCode?.let { com.mylittleblover.mkwplayer.ui.art.StageArt.resolve(it) }?.let { art ->
                    androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(art), contentDescription = null, modifier = Modifier.size(44.dp))
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(title, maxLines = 1, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        Text(flow?.let { com.mylittleblover.mkwplayer.ui.interactive.performanceStageText(it) } ?: androidx.compose.ui.res.stringResource(
                            if (ui.canResume) R.string.discovery_paused else R.string.discovery_playing),
                            maxLines = 1, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.material3.IconButton(onClick = onToggle, enabled = ui.canPause || ui.canResume,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = label }) {
                    androidx.compose.material3.Icon(if (ui.canPause) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null)
                }
            }
            val frame = flow?.sourceFrame
            val length = flow?.sourceFrames
            val progress = if (frame != null && frame >= 0 && length != null && length > 0)
                (frame.toDouble() / length).toFloat().coerceIn(0f, 1f) else ui.progress.coerceIn(0f, 1f)
            androidx.compose.material3.LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SingleTrackPlayer(model: PlayerViewModel, ui: PlayerViewModel.Ui) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.material3.Surface(Modifier.widthIn(max = 440.dp).fillMaxWidth().aspectRatio(1f),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            com.mylittleblover.mkwplayer.ui.art.CoursePlaceholder(model.currentStageCode, Modifier.fillMaxSize())
        }
        Text(ui.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        androidx.compose.material3.LinearProgressIndicator(progress = { ui.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(ui.position); Text(ui.total) }
        val label = androidx.compose.ui.res.stringResource(if (ui.canPause) R.string.interactive_pause else R.string.interactive_resume)
        androidx.compose.material3.FilledIconButton(onClick = { if (ui.canPause) model.pause() else model.resume() },
            enabled = ui.canPause || ui.canResume, modifier = Modifier.size(68.dp).semantics { contentDescription = label }) {
            androidx.compose.material3.Icon(if (ui.canPause) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(30.dp))
        }
    }
}
