package com.mylittleblover.mkwplayer

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import com.mylittleblover.mkwplayer.ui.art.ArtworkSlot
import com.mylittleblover.mkwplayer.ui.art.FadingArtwork
import com.mylittleblover.mkwplayer.ui.art.PackArtwork
import com.mylittleblover.mkwplayer.ui.art.ResourceArtwork
import com.mylittleblover.mkwplayer.ui.art.rememberArtworkRatio
import com.mylittleblover.mkwplayer.ui.art.rememberResourceArtwork
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mylittleblover.mkwplayer.R
import com.mylittleblover.mkwplayer.audio.EngineSnapshot
import com.mylittleblover.mkwplayer.player.LinearQueue
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
    val pageStateHolder = rememberSaveableStateHolder()
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
    // The master trim is a device preference, read once and pushed to the running engine when it changes.
    // `remember` on the context keeps it stable across recompositions; the session owns the live value.
    var outputTrim by remember(context) {
        mutableStateOf(com.mylittleblover.mkwplayer.audio.OutputTrimSettings.current(context))
    }
    LaunchedEffect(outputTrim) { model.applyOutputTrim(outputTrim) }
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
            val title = when (val detail = nav.detail) {
                NavState.Detail.Settings -> androidx.compose.ui.res.stringResource(R.string.settings_title)
                is NavState.Detail.Course -> androidx.compose.ui.res.stringResource(R.string.nav_course)
                is NavState.Detail.Cup -> androidx.compose.ui.res.stringResource(
                    if (browse?.route(detail.id, unlocked)?.kind == com.mylittleblover.mkwplayer.routes.OfficialRouteKind.RALLY) {
                        R.string.discovery_rally
                    } else {
                        R.string.discovery_gp
                    },
                )
                NavState.Detail.Flow -> androidx.compose.ui.res.stringResource(R.string.discovery_interactive)
                NavState.Detail.Track -> androidx.compose.ui.res.stringResource(R.string.discovery_media)
                is NavState.Detail.Interactive -> androidx.compose.ui.res.stringResource(R.string.discovery_interactive)
                is NavState.Detail.Playlist -> androidx.compose.ui.res.stringResource(R.string.discovery_playlists)
                is NavState.Detail.JukeboxGame -> ui.jukebox.gameById(detail.id)?.localizedName(languageTag)
                    ?: androidx.compose.ui.res.stringResource(R.string.discovery_free)
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
                        val miniTitle = model.currentStageCode?.let { browse?.displayName(it, languageTag) }
                            ?: ui.linearTrack?.title?.takeIf { it.isNotBlank() }
                            ?: ui.title
                        MiniPlayer(miniTitle, model.currentStageCode, model.packDirectory, playbackArtworkNames(model, ui), ui,
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
                transitionSpec = { com.mylittleblover.mkwplayer.ui.AppMotion.page(NavState.restore(targetState).motionFrom(NavState.restore(initialState))) },
                label = "page-transition") { snapshot ->
            val page = NavState.restore(snapshot)
            val detail = page.detail
            pageStateHolder.SaveableStateProvider(snapshot.joinToString("") { "${it.length}:$it" }) {
            androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when {
                detail == NavState.Detail.Settings -> AppSettingsScreen(
                    language = language, onLanguage = { AppLanguageSettings.select(context, it) },
                    busy = ui.busy, sourceName = ui.pack?.name, onOpenPack = openPack, onOpenFiles = openFiles,
                    testMode = ui.unlockTestMode,
                    testUnlockVisible = com.mylittleblover.mkwplayer.ui.CupVisibility.mayReveal(ui.unlock),
                    onTestUnlock = model::setUnlockTestMode,
                    outputTrim = outputTrim,
                    onOutputTrim = { chosen ->
                        outputTrim = chosen
                        com.mylittleblover.mkwplayer.audio.OutputTrimSettings.select(context, chosen)
                        model.applyOutputTrim(chosen)
                    },
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
                        com.mylittleblover.mkwplayer.ui.art.CourseCover(detail.code,
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
                detail is NavState.Detail.JukeboxGame -> com.mylittleblover.mkwplayer.ui.home.DiscoveryJukeboxGame(
                    game = ui.jukebox.gameById(detail.id),
                    language = languageTag,
                    activeTrackKey = ui.selectedKey,
                    packDirectory = model.packDirectory,
                    onPlayFromIndex = { game, index, shuffle ->
                        model.playJukeboxQueue(game, index, languageTag, shuffle = shuffle)
                        nav = nav.open(NavState.Detail.Track)
                    },
                    onPlayNext = { item, game ->
                        model.playJukeboxNext(item, game, languageTag)
                    },
                )
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
                        onCourse = { nav = nav.open(NavState.Detail.Course(it)) }, onCup = { nav = nav.open(NavState.Detail.Cup(it)) },
                        jukebox = ui.jukebox,
                        extraCatalog = model.extraCatalog,
                        packDirectory = model.packDirectory,
                        activeTrackKey = ui.selectedKey,
                        onJukeboxGame = { gameId -> nav = nav.open(NavState.Detail.JukeboxGame(gameId)) },
                        onPlayJukeboxTrack = { game, index ->
                            model.playJukeboxQueue(game, index, languageTag, shuffle = false)
                            nav = nav.open(NavState.Detail.Track)
                        },
                        onPlayAllJukebox = { shuffle ->
                            model.playAllJukebox(languageTag, shuffle = shuffle)
                            nav = nav.open(NavState.Detail.Track)
                        },
                        onPlayJukeboxNext = { item, game ->
                            model.playJukeboxNext(item, game, languageTag)
                        },
                        onJukeboxTrack = { key ->
                            model.openJukeboxTrack(key, languageTag)
                            // The free-driving pieces are single tracks, not stage flows, so they belong on
                            // the single-track screen. Sending them to the console would offer stage events
                            // for music that has no stages.
                            nav = nav.open(NavState.Detail.Track)
                        },
                        onSfcCourse = { track ->
                            model.openInteractiveTwoState(track, isBattle = false, languageTag = languageTag, autoStart = true)
                            nav = nav.open(NavState.Detail.Flow)
                        },
                        onBattleTrack = { track ->
                            model.openInteractiveTwoState(track, isBattle = true, languageTag = languageTag, autoStart = true)
                            nav = nav.open(NavState.Detail.Flow)
                        },
                        onOpenTitle = {
                            model.openInteractiveTitle(languageTag = languageTag, autoStart = false)
                            nav = nav.open(NavState.Detail.Flow)
                        },
                        onPlayExtraQueue = { entries, startIndex, repeatMode, shuffle ->
                            model.playExtraLinearQueue(entries, startIndex, repeatMode, shuffle)
                            nav = nav.open(NavState.Detail.Track)
                        })
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
                val cover = com.mylittleblover.mkwplayer.ui.art.courseCoverDrawable(code)
                if (cover != null) {
                    ArtworkSlot(Modifier.fillMaxWidth()) { edge ->
                        val art = rememberResourceArtwork(cover, edge)
                        val frame = Modifier.fillMaxWidth().aspectRatio(rememberArtworkRatio(cover, art, 16f / 9f))
                        if (art == null) Box(frame)
                        else FadingArtwork(art.bitmap, frame)
                    }
                } else {
                    com.mylittleblover.mkwplayer.ui.art.CourseCover(code, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
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
                // The game's own connection table. Absent is allowed: the map then draws no roads rather
                // than inventing them, and the editor is not offered.
                val connections = runCatching {
                    com.mylittleblover.mkwplayer.library.StageConnections.parse(
                        context.assets.open("stage_connections.json").bufferedReader().use { it.readText() })
                }.getOrNull()
                MapResources(image, anchors, routes, names, connections)
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
        connections = data.connections,
    )
}

private data class MapResources(
    val image: androidx.compose.ui.graphics.ImageBitmap,
    val anchors: com.mylittleblover.mkwplayer.library.MapAnchors.Catalog,
    val routes: OfficialRouteCatalog,
    val names: LocalizedNames,
    val connections: com.mylittleblover.mkwplayer.library.StageConnections.Catalog? = null,
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

    val coverArtNames = playbackArtworkNames(model, ui)

    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).testTag("interactive-page"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item {
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth >= 640.dp) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(36.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            PlayerCover(model.currentStageCode, model.packDirectory, coverArtNames, Modifier.weight(1f))
                            PlayerInformation(model, ui, displayTitle, Modifier.weight(1f))
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            PlayerCover(model.currentStageCode, model.packDirectory, coverArtNames, Modifier.fillMaxWidth())
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
                        Column(Modifier.animateContentSize(tween(220)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            com.mylittleblover.mkwplayer.ui.interactive.InteractiveEntryChooser(
                                choices = model.initialEntryChoices, selected = model.selectedEntrySegment,
                                enabled = !ui.canStop && !ui.busy, onSelect = model::selectInitialEntry)
                            if (flow.performanceBlocker != null) Text(flow.performanceBlocker, style = MaterialTheme.typography.bodySmall)
                            else {
                                com.mylittleblover.mkwplayer.ui.interactive.InteractiveActionBar(flow,
                                    onAdvance = model::advancePerformance, onFast = model::toggleFast,
                                    onCancel = model::cancelPendingPerformance, onEnd = model::takeOutro)
                                if (flow.outros.size > 1 && flow.canEnd) {
                                    androidx.compose.foundation.layout.FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        flow.outros.forEach { number ->
                                            OutlinedButton(onClick = { model.takeOutro(number) }) {
                                                Text(androidx.compose.ui.res.stringResource(R.string.stage_outro_numbered, number + 1))
                                            }
                                        }
                                    }
                                }
                                if (flow.hasSpeakerOverlay) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            androidx.compose.ui.res.stringResource(R.string.discovery_speaker_overlay),
                                            style = MaterialTheme.typography.titleSmall,
                                            modifier = Modifier.weight(1f),
                                        )
                                        androidx.compose.material3.Switch(
                                            checked = flow.speakerEnabled,
                                            onCheckedChange = { model.toggleSpeakerOverlay() },
                                        )
                                    }
                                }
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
private fun PlayerCover(
    code: String?,
    packDirectory: java.io.File?,
    artworkNames: List<String>,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ArtworkSlot(Modifier.widthIn(max = 440.dp).fillMaxWidth()) { edge ->
            val loadedArt = PackArtwork.rememberArtwork(packDirectory, artworkNames, edge)
            val ratio = rememberArtworkRatio(artworkNames, loadedArt, if (code != null) 16f / 9f else 1f)
            androidx.compose.material3.Surface(
                modifier = Modifier.fillMaxWidth().aspectRatio(ratio),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                if (loadedArt != null) FadingArtwork(loadedArt.bitmap, Modifier.fillMaxSize())
                else com.mylittleblover.mkwplayer.ui.art.CourseCover(code, Modifier.fillMaxSize())
            }
        }
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

private fun playbackArtworkNames(model: PlayerViewModel, ui: PlayerViewModel.Ui): List<String> {
    val track = ui.linearTrack
    val names = if (track != null) {
        listOfNotNull(
            track.artworkName,
            track.ostIndex?.let { "t_%03d.webp".format(java.util.Locale.ROOT, it) },
            track.gameId?.let { model.extraCatalog.jukeboxGameArtwork[it] },
        )
    } else {
        listOfNotNull(ui.artworkName, model.currentStageCode?.let { model.extraCatalog.courseArtwork[it] })
    }
    val courseCover = model.currentStageCode?.let { com.mylittleblover.mkwplayer.ui.art.courseCoverDrawable(it) }
    return (if (courseCover == null) names + "album.webp" else names).distinct()
}

@Composable
private fun MiniPlayer(
    title: String,
    courseCode: String?,
    packDirectory: java.io.File?,
    artworkNames: List<String>,
    ui: PlayerViewModel.Ui,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
) {
    val flow = ui.flow
    val label = androidx.compose.ui.res.stringResource(if (ui.canPause) R.string.interactive_pause else R.string.interactive_resume)
    val stateLabel = androidx.compose.ui.res.stringResource(if (ui.canResume) R.string.discovery_paused else R.string.discovery_playing)
    val subtitle = flow?.let { com.mylittleblover.mkwplayer.ui.interactive.performanceStageText(it) }
        ?: ui.linearTrack?.subtitle?.takeIf { it.isNotBlank() }?.let { "$it · $stateLabel" }
        ?: stateLabel
    androidx.compose.material3.Surface(modifier = Modifier.testTag("mini-player").height(72.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp, shadowElevation = 2.dp) {
        Column {
            Row(Modifier.weight(1f).fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                PackArtwork.Thumbnail(packDirectory, artworkNames, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))) {
                    val courseArt = com.mylittleblover.mkwplayer.ui.art.courseCoverDrawable(courseCode)
                    if (courseArt != null) {
                        ResourceArtwork(courseArt, Modifier.fillMaxSize(), androidx.compose.ui.layout.ContentScale.Crop)
                    } else {
                        androidx.compose.material3.Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                androidx.compose.material3.Icon(
                                    Icons.Outlined.MusicNote, null, tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            com.mylittleblover.mkwplayer.ui.SmoothProgressIndicator(progress, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SingleTrackPlayer(model: PlayerViewModel, ui: PlayerViewModel.Ui) {
    val stageCode = model.currentStageCode
    val linearTrack = ui.linearTrack
    val hasLinearTrack = ui.linearMode && linearTrack != null
    var showQueueSheet by rememberSaveable { mutableStateOf(false) }
    var scrubbing by remember(ui.selectedKey) { mutableStateOf(false) }
    var scrubFraction by remember(ui.selectedKey) { mutableStateOf(0f) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtworkSlot(Modifier.widthIn(max = 420.dp).fillMaxWidth()) { edge ->
            val art = PackArtwork.rememberArtwork(model.packDirectory, playbackArtworkNames(model, ui), edge)
            val ratio = rememberArtworkRatio(ui.selectedKey, art, if (stageCode != null) 16f / 9f else 1f)
            androidx.compose.material3.Surface(
                Modifier.fillMaxWidth().aspectRatio(ratio),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                when {
                    art != null -> FadingArtwork(art.bitmap, Modifier.fillMaxSize())
                    stageCode != null -> com.mylittleblover.mkwplayer.ui.art.CourseCover(stageCode, Modifier.fillMaxSize())
                    else -> JukeboxArtworkCard(linearTrack)
                }
            }
        }

        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                linearTrack?.title ?: ui.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            if (linearTrack != null) {
                val metaParts = buildList {
                    if (linearTrack.subtitle.isNotBlank()) add(linearTrack.subtitle)
                    linearTrack.arrangement?.takeIf { it.isNotBlank() }?.let { add(it) }
                    linearTrack.ostIndex?.let { add("#$it") }
                }
                if (metaParts.isNotEmpty()) {
                    Text(
                        metaParts.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // The status line is not decoration: without it a refused track shows an empty player with a clock
        // reading --:-- and no explanation, which reads as a broken app rather than a stated refusal.
        if (!ui.canPause && !ui.canResume && !hasLinearTrack) {
            Text(ui.status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ui.detail?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }

        val displayedProgress = if (scrubbing) scrubFraction else ui.progress.coerceIn(0f, 1f)
        val displayedPosition = if (scrubbing && ui.totalFrames > 0L && ui.sampleRate > 0) {
            EngineSnapshot.formatPosition((scrubFraction.toDouble() * ui.totalFrames).toLong(), ui.sampleRate)
        } else {
            ui.position
        }

        Column(Modifier.fillMaxWidth()) {
            if (ui.canSeek && ui.totalFrames > 0L) {
                Slider(
                    value = displayedProgress,
                    onValueChange = { fraction ->
                        scrubbing = true
                        scrubFraction = fraction.coerceIn(0f, 1f)
                    },
                    onValueChangeFinished = {
                        val target = scrubFraction
                        scrubbing = false
                        model.seekTo(target)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                com.mylittleblover.mkwplayer.ui.SmoothProgressIndicator(ui.progress, modifier = Modifier.fillMaxWidth())
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(displayedPosition, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(ui.total, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        val label = androidx.compose.ui.res.stringResource(if (ui.canPause) R.string.interactive_pause else R.string.interactive_resume)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (hasLinearTrack) {
                FilledTonalIconButton(
                    onClick = model::skipPrevious,
                    modifier = Modifier.size(52.dp),
                ) {
                    androidx.compose.material3.Icon(
                        Icons.Filled.SkipPrevious,
                        contentDescription = androidx.compose.ui.res.stringResource(R.string.jukebox_previous),
                    )
                }
            }
            androidx.compose.material3.FilledIconButton(
                onClick = {
                    when {
                        ui.canPause -> model.pause()
                        hasLinearTrack -> model.replayOrResumeLinear()
                        else -> model.resume()
                    }
                },
                enabled = ui.canPause || ui.canResume || hasLinearTrack,
                modifier = Modifier.size(68.dp).semantics { contentDescription = label },
            ) {
                androidx.compose.material3.Icon(
                    if (ui.canPause) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                )
            }
            if (hasLinearTrack) {
                FilledTonalIconButton(
                    onClick = model::skipNext,
                    modifier = Modifier.size(52.dp),
                ) {
                    androidx.compose.material3.Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = androidx.compose.ui.res.stringResource(R.string.jukebox_next),
                    )
                }
            }
        }

        if (hasLinearTrack) {
            val repeatLabel = androidx.compose.ui.res.stringResource(
                when (ui.queue.repeatMode) {
                    LinearQueue.RepeatMode.LIST -> R.string.jukebox_repeat_list
                    LinearQueue.RepeatMode.SINGLE -> R.string.jukebox_repeat_single
                    LinearQueue.RepeatMode.OFF -> R.string.jukebox_repeat_off
                },
            )
            val repeatIcon = if (ui.queue.repeatMode == LinearQueue.RepeatMode.SINGLE) {
                Icons.Filled.RepeatOne
            } else {
                Icons.Filled.Repeat
            }
            val queuedTotal = ui.queue.tempQueue.size + ui.queue.upNext.size
            val queueLabel = androidx.compose.ui.res.stringResource(R.string.jukebox_queue)

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = ui.queue.shuffle,
                    onClick = model::toggleShuffle,
                    label = {
                        Text(
                            androidx.compose.ui.res.stringResource(
                                if (ui.queue.shuffle) R.string.jukebox_shuffle_on else R.string.jukebox_shuffle,
                            ),
                            maxLines = 1,
                        )
                    },
                    leadingIcon = { androidx.compose.material3.Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
                FilterChip(
                    selected = ui.queue.repeatMode != LinearQueue.RepeatMode.OFF,
                    onClick = model::cycleRepeatMode,
                    label = { Text(repeatLabel, maxLines = 1) },
                    leadingIcon = { androidx.compose.material3.Icon(repeatIcon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
                AssistChip(
                    onClick = { showQueueSheet = true },
                    label = {
                        Text(if (queuedTotal > 0) "$queueLabel ($queuedTotal)" else queueLabel, maxLines = 1)
                    },
                    leadingIcon = {
                        androidx.compose.material3.Icon(
                            Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }
        }
    }

    if (showQueueSheet && hasLinearTrack) {
        LinearQueueBottomSheet(
            queue = ui.queue,
            onDismiss = { showQueueSheet = false },
            onJumpToTemp = { index ->
                model.jumpToTempQueueItem(index)
                showQueueSheet = false
            },
            onRemoveTemp = model::removeTempQueueItem,
            onJumpToUpcoming = { index ->
                model.jumpToUpcomingQueueItem(index)
                showQueueSheet = false
            },
        )
    }
}

@Composable
private fun JukeboxArtworkCard(entry: LinearQueue.Entry?) {
    val gradient = Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.tertiaryContainer,
        ),
    )
    Box(
        Modifier.fillMaxSize().background(gradient).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (entry != null) {
                androidx.compose.material3.Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                ) {
                    Text(
                        androidx.compose.ui.res.stringResource(R.string.jukebox_eyebrow),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            androidx.compose.material3.Surface(
                modifier = Modifier.size(112.dp),
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(
                        Icons.Outlined.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            entry?.yearLabel?.takeIf { it.isNotBlank() }?.let { year ->
                Text(
                    year,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinearQueueBottomSheet(
    queue: LinearQueue.Snapshot,
    onDismiss: () -> Unit,
    onJumpToTemp: (Int) -> Unit,
    onRemoveTemp: (Int) -> Unit,
    onJumpToUpcoming: (Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                androidx.compose.ui.res.stringResource(R.string.jukebox_queue),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                androidx.compose.ui.res.stringResource(R.string.jukebox_queue_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        LazyColumn(
            Modifier.fillMaxWidth().height(380.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            queue.current?.let { now ->
                item(key = "now-hdr") {
                    QueueSectionHeader(androidx.compose.ui.res.stringResource(R.string.jukebox_now_playing))
                }
                item(key = "now-item") {
                    QueueEntryRow(entry = now, highlight = true, onClick = onDismiss)
                }
            }
            if (queue.tempQueue.isNotEmpty()) {
                item(key = "temp-hdr") {
                    QueueSectionHeader(androidx.compose.ui.res.stringResource(R.string.jukebox_up_next_temp))
                }
                itemsIndexed(queue.tempQueue, key = { idx, item -> "temp:$idx:${item.key}" }) { idx, item ->
                    QueueEntryRow(
                        entry = item,
                        highlight = false,
                        onClick = { onJumpToTemp(idx) },
                        onRemove = { onRemoveTemp(idx) },
                    )
                }
            }
            if (queue.upNext.isNotEmpty()) {
                item(key = "upnext-hdr") {
                    QueueSectionHeader(androidx.compose.ui.res.stringResource(R.string.jukebox_up_next_list))
                }
                itemsIndexed(queue.upNext, key = { idx, item -> "upnext:$idx:${item.key}" }) { idx, item ->
                    QueueEntryRow(
                        entry = item,
                        highlight = false,
                        onClick = { onJumpToUpcoming(idx) },
                    )
                }
            }
            if (queue.tempQueue.isEmpty() && queue.upNext.isEmpty()) {
                item(key = "queue-empty") {
                    Text(
                        androidx.compose.ui.res.stringResource(R.string.jukebox_queue_empty),
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun QueueSectionHeader(text: String) {
    Text(
        text,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun QueueEntryRow(
    entry: LinearQueue.Entry,
    highlight: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    val sub = buildList {
        if (entry.subtitle.isNotBlank()) add(entry.subtitle)
        entry.arrangement?.takeIf { it.isNotBlank() }?.let { add(it) }
        if (entry.durationMs > 0L) add(EngineSnapshot.formatPosition(entry.durationMs / 1000L, 1))
    }.joinToString(" · ")
    ListItem(
        headlineContent = {
            Text(
                entry.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium,
            )
        },
        supportingContent = if (sub.isNotEmpty()) {
            { Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else null,
        trailingContent = if (onRemove != null) {
            {
                androidx.compose.material3.IconButton(onClick = onRemove) {
                    androidx.compose.material3.Icon(
                        Icons.Filled.Close,
                        contentDescription = androidx.compose.ui.res.stringResource(R.string.discovery_remove),
                    )
                }
            }
        } else null,
        colors = if (highlight) {
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
        } else {
            ListItemDefaults.colors()
        },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}
