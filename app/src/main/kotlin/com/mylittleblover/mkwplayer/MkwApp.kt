package com.mylittleblover.mkwplayer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
    val configuration = LocalConfiguration.current
    val sideNavigation = configuration.screenWidthDp >= 600 ||
        (configuration.screenWidthDp > configuration.screenHeightDp && configuration.screenHeightDp < 500)
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            val title = when (nav.detail) {
                NavState.Detail.Settings -> androidx.compose.ui.res.stringResource(R.string.settings_title)
                is NavState.Detail.Course -> androidx.compose.ui.res.stringResource(R.string.nav_course)
                is NavState.Detail.Cup -> androidx.compose.ui.res.stringResource(R.string.discovery_gp)
                NavState.Detail.Flow -> androidx.compose.ui.res.stringResource(R.string.discovery_interactive)
                is NavState.Detail.Interactive -> androidx.compose.ui.res.stringResource(R.string.discovery_interactive)
                is NavState.Detail.Playlist -> androidx.compose.ui.res.stringResource(R.string.discovery_playlists)
                else -> tabTitle(nav.tab)
            }
            AppHeader(title, nav.canGoBack, nav.detail != NavState.Detail.Settings,
                onBack = { nav = nav.back() ?: nav }, onSettings = { nav = nav.open(NavState.Detail.Settings) })
        },
        bottomBar = {
            if (!sideNavigation) NavigationBar {
                Tab.ordered().forEach { tab ->
                    NavigationBarItem(
                        selected = nav.tab == tab,
                        onClick = { nav = nav.select(tab) },
                        label = { Text(tabTitle(tab)) },
                        icon = { TabIcon(tab) },
                    )
                }
            }
        },
    ) { inner ->
        Row(Modifier.padding(inner).fillMaxSize()) {
            if (sideNavigation) androidx.compose.material3.NavigationRail {
                Tab.ordered().forEach { tab ->
                    androidx.compose.material3.NavigationRailItem(selected = nav.tab == tab,
                        onClick = { nav = nav.select(tab) }, icon = { TabIcon(tab) }, label = { Text(tabTitle(tab)) })
                }
            }
            Column(Modifier.weight(1f).fillMaxSize()) {
            val detail = nav.detail
            when {
                detail == NavState.Detail.Settings -> AppSettingsScreen(
                    language = language, onLanguage = { AppLanguageSettings.select(context, it) },
                    busy = ui.busy, sourceName = ui.pack?.name, onOpenPack = openPack, onOpenFiles = openFiles,
                    testMode = ui.unlockTestMode,
                    testUnlockVisible = com.mylittleblover.mkwplayer.ui.CupVisibility.mayReveal(ui.unlock),
                    onTestUnlock = model::setUnlockTestMode,
                )
                detail is NavState.Detail.Course -> CoursePage(model, ui, detail.code) {
                    nav = nav.open(NavState.Detail.Interactive(detail.code))
                }
                detail is NavState.Detail.Interactive -> {
                    if (model.isStageOpen(detail.code)) ConsolePage(model, ui, openPack, openFiles)
                    else Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(browse?.displayName(detail.code, languageTag) ?: detail.code, style = MaterialTheme.typography.headlineSmall)
                        if (model.canStartStage(detail.code)) {
                            Button(onClick = { model.startStagePerformance(detail.code) }, enabled = !ui.busy) {
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
                    browse?.route(detail.id, unlocked), browse, languageTag) { code -> nav = nav.open(NavState.Detail.Course(code)) }
                detail == NavState.Detail.Flow -> ConsolePage(model, ui, openPack, openFiles)
                else -> when (nav.tab) {
                    Tab.HOME -> com.mylittleblover.mkwplayer.ui.home.DiscoveryHome(browse, discoveryState, languageTag, unlocked,
                        onOpen = { nav = nav.open(NavState.Detail.Course(it)) }, onFavorite = discovery::toggleFavorite,
                        onImport = openPack, onPlayer = { nav = nav.open(NavState.Detail.Flow) }, hasPlayer = ui.flow != null && ui.canStop)
                    Tab.MEDIA -> com.mylittleblover.mkwplayer.ui.home.DiscoveryMedia(browse, unlocked, languageTag,
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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(androidx.compose.ui.res.stringResource(R.string.discovery_suite), style = MaterialTheme.typography.titleLarge)
                    Text(androidx.compose.ui.res.stringResource(R.string.discovery_later))
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(androidx.compose.ui.res.stringResource(R.string.discovery_interactive), style = MaterialTheme.typography.titleLarge)
                    Button(onClick = onInteractive) { Text(androidx.compose.ui.res.stringResource(R.string.discovery_open_player)) }
                }
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
                val image = requireNotNull(android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.world_map)).asImageBitmap()
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
    LazyColumn(
        Modifier
            .weight(1f)
            .testTag("interactive-page")
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "performance-console") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(androidx.compose.ui.res.stringResource(R.string.interactive_title), style = MaterialTheme.typography.headlineSmall)
                com.mylittleblover.mkwplayer.ui.interactive.InteractiveCoursePicker(
                    names = names,
                    specialUnlocked = com.mylittleblover.mkwplayer.ui.CupVisibility.mayReveal(ui.unlock),
                    busy = ui.busy || flow?.fading == true,
                    available = model::canStartStage,
                    onPlay = model::startStagePerformance,
                )

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (flow == null) {
                            Text(
                                androidx.compose.ui.res.stringResource(R.string.interactive_empty),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = onOpenPack) { Text(androidx.compose.ui.res.stringResource(R.string.interactive_import)) }
                                OutlinedButton(onClick = onOpenFiles) { Text(androidx.compose.ui.res.stringResource(R.string.interactive_files)) }
                                OutlinedButton(onClick = { model.openFlowFromFolder() }) {
                                    Text(androidx.compose.ui.res.stringResource(R.string.interactive_scan))
                                }
                            }
                        } else {
                            Text(displayTitle, style = MaterialTheme.typography.titleMedium)
                            Text(flow.summary, style = MaterialTheme.typography.bodySmall)
                            Text(
                                flow.performanceStage.ifEmpty { androidx.compose.ui.res.stringResource(R.string.interactive_waiting) },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                            )

                            com.mylittleblover.mkwplayer.ui.interactive.InteractivePlaybackTimeline(ui.position, flow)

                            com.mylittleblover.mkwplayer.ui.interactive.InteractiveEntryChooser(
                                choices = model.initialEntryChoices,
                                selected = model.selectedEntrySegment,
                                enabled = !ui.canStop && !ui.busy,
                                onSelect = model::selectInitialEntry,
                            )

                            if (flow.performanceBlocker != null) {
                                // A disabled button with no explanation reads as a broken app.
                                Text(flow.performanceBlocker, style = MaterialTheme.typography.bodySmall)
                            } else {
                                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { model.startPerformance() },
                                        enabled = !flow.performing,
                                    ) { Text(androidx.compose.ui.res.stringResource(R.string.interactive_start)) }

                                    // The next-stage event. The prelude loops until this is pressed, which is what
                                    // the game does: the race start is an event, not a timer.
                                    OutlinedButton(
                                        onClick = { model.advancePerformance() },
                                        enabled = flow.canAdvance,
                                    ) { Text(androidx.compose.ui.res.stringResource(R.string.interactive_next)) }

                                }

                                if (flow.hasFast) {
                                    // The Native graph owns the transition timing and shared fanfare.
                                    OutlinedButton(
                                        onClick = { model.toggleFast() },
                                        enabled = flow.canFast || (flow.versionNumbers.isEmpty() && flow.regionNumbers.isEmpty() && flow.canEnd && !flow.fastPlaying),
                                    ) {
                                        Text(if (flow.fastPlaying) androidx.compose.ui.res.stringResource(R.string.interactive_fast_playing) else androidx.compose.ui.res.stringResource(R.string.interactive_fast))
                                    }
                                }

                                if (flow.canCancelPending) {
                                    OutlinedButton(onClick = { model.cancelPendingPerformance() }) {
                                        Text(androidx.compose.ui.res.stringResource(R.string.cancel_pending_transition))
                                    }
                                }

                                if (flow.regionNumbers.isNotEmpty()) {
                                    Text(androidx.compose.ui.res.stringResource(R.string.music_region))
                                    Text(
                                        "Full → Pattern 2 → Pattern 3 → Pattern 4 → Pattern 5 → Fast",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Text(androidx.compose.ui.res.stringResource(R.string.music_region_hint),style = MaterialTheme.typography.bodySmall)
                                }

                                if (flow.versionNumbers.isNotEmpty()) {
                                    Text(androidx.compose.ui.res.stringResource(R.string.recording_version))
                                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        flow.versionNumbers.forEach { number ->
                                            FilterChip(
                                                selected = flow.selectedVersion == number,
                                                onClick = { if(flow.selectedVersion != number) model.selectRecordingVersion(number) },
                                                enabled = number in flow.availableVersions || number == flow.selectedVersion,
                                                label = { Text(androidx.compose.ui.res.stringResource(R.string.recording_version_number,number)) },
                                            )
                                        }
                                    }
                                    Text(androidx.compose.ui.res.stringResource(R.string.recording_version_hint),style = MaterialTheme.typography.bodySmall)
                                }

                                if (flow.canEnd) {
                                    // The ending is chosen by how far into the loop playback has got; the console
                                    // offers the event, not a menu of files.
                                    Button(onClick = { model.takeOutro() }) { Text(androidx.compose.ui.res.stringResource(R.string.interactive_end)) }
                                    Text(
                                        androidx.compose.ui.res.stringResource(R.string.interactive_ending_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }

                            Text(flow.provenance, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = { model.pause() }, enabled = ui.canPause) {
                        Text(androidx.compose.ui.res.stringResource(R.string.interactive_pause))
                    }
                    OutlinedButton(onClick = { model.resume() }, enabled = ui.canResume) {
                        Text(androidx.compose.ui.res.stringResource(R.string.interactive_resume))
                    }
                }
                ui.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

            }
        }

    }
    com.mylittleblover.mkwplayer.ui.interactive.InteractiveStopBar(
        canStop = ui.canStop,
        canFade = !ui.busy && model.canFadeStop,
        fading = flow?.fading == true,
        onStop = model::stop,
        onFade = model::fadeStopPerformance,
    )
    }
}
