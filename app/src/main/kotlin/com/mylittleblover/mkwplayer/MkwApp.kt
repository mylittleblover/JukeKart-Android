package com.mylittleblover.mkwplayer

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt
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
    val reduceMotion = com.mylittleblover.mkwplayer.ui.rememberReducedMotion()
    val playerDetail = nav.detail == NavState.Detail.Flow || nav.detail == NavState.Detail.Track ||
        ((nav.detail as? NavState.Detail.Interactive)?.let { model.isStageOpen(it.code) } == true)
    val navChromeEnter = if (reduceMotion) {
        fadeIn(tween(150))
    } else {
        expandVertically(
            animationSpec = tween(240, easing = com.mylittleblover.mkwplayer.ui.AppMotion.easeOut),
            expandFrom = Alignment.Bottom,
        ) + fadeIn(tween(200, easing = com.mylittleblover.mkwplayer.ui.AppMotion.easeOut))
    }
    val navChromeExit = if (reduceMotion) {
        fadeOut(tween(120))
    } else {
        shrinkVertically(
            animationSpec = tween(190, easing = com.mylittleblover.mkwplayer.ui.AppMotion.easeOut),
            shrinkTowards = Alignment.Bottom,
        ) + fadeOut(tween(150))
    }
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
                is NavState.Detail.AllPlaylists -> androidx.compose.ui.res.stringResource(R.string.discovery_all_lists_title)
                is NavState.Detail.CustomRoute -> discoveryState.customRoutes.firstOrNull { it.id == detail.id }?.name
                    ?: androidx.compose.ui.res.stringResource(R.string.discovery_playlists_group_routes)
                is NavState.Detail.FlowEditor -> androidx.compose.ui.res.stringResource(R.string.flow_editor_title)
                is NavState.Detail.JukeboxGame -> ui.jukebox.gameById(detail.id)?.localizedName(languageTag)
                    ?: androidx.compose.ui.res.stringResource(R.string.discovery_free)
                else -> tabTitle(nav.tab)
            }
            AppHeader(title, nav.canGoBack, nav.detail != NavState.Detail.Settings,
                onBack = { nav = nav.back() ?: nav }, onSettings = { nav = nav.open(NavState.Detail.Settings) })
        },
        bottomBar = {
            Column {
                AnimatedVisibility(
                    visible = playerDetail && ui.flow != null,
                    enter = navChromeEnter,
                    exit = navChromeExit,
                ) {
                    com.mylittleblover.mkwplayer.ui.interactive.InteractiveStopBar(
                        canStop = ui.canStop, canFade = !ui.busy && model.canFadeStop, fading = ui.flow?.fading == true,
                        onStop = model::stop, onFade = model::fadeStopPerformance)
                }
                AnimatedVisibility(
                    visible = ui.canStop && !playerDetail,
                    enter = navChromeEnter,
                    exit = navChromeExit,
                ) {
                    val miniTitle = model.currentStageCode?.let { browse?.displayName(it, languageTag) }
                        ?: ui.linearTrack?.title?.takeIf { it.isNotBlank() }
                        ?: ui.title
                    MiniPlayer(miniTitle, model.currentStageCode, model.packDirectory, playbackArtworkNames(model, ui), ui,
                        onOpen = { nav = nav.open(if (ui.flow == null) NavState.Detail.Track else NavState.Detail.Flow) },
                        onToggle = { if (ui.canPause) model.pause() else if (ui.canResume) model.resume() },
                        onNext = if (ui.linearMode && ui.linearTrack != null) model::skipNext else null)
                }
                AnimatedVisibility(
                    visible = !sideNavigation && !playerDetail && nav.detail !is NavState.Detail.Interactive,
                    enter = navChromeEnter,
                    exit = navChromeExit,
                ) {
                    NavigationBar {
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
            AnimatedContent(
                targetState = nav.snapshot(),
                modifier = Modifier.weight(1f).fillMaxSize(),
                transitionSpec = {
                    val from = NavState.restore(initialState)
                    val to = NavState.restore(targetState)
                    val fromIsPlayer = from.detail == NavState.Detail.Flow ||
                        from.detail == NavState.Detail.Track ||
                        from.detail is NavState.Detail.Interactive
                    val toIsPlayer = to.detail == NavState.Detail.Flow ||
                        to.detail == NavState.Detail.Track ||
                        to.detail is NavState.Detail.Interactive
                    when {
                        toIsPlayer && !fromIsPlayer ->
                            com.mylittleblover.mkwplayer.ui.AppMotion.playerSheet(opening = true, reduceMotion = reduceMotion)
                        fromIsPlayer && !toIsPlayer ->
                            com.mylittleblover.mkwplayer.ui.AppMotion.playerSheet(opening = false, reduceMotion = reduceMotion)
                        else ->
                            com.mylittleblover.mkwplayer.ui.AppMotion.page(to.motionFrom(from), reduceMotion = reduceMotion)
                    }
                },
                label = "page-transition",
            ) { snapshot ->
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
                    } else CoursePage(
                        model = model,
                        ui = ui,
                        code = detail.code,
                        presets = discoveryState.presets,
                        playlistsReferencingPreset = discovery::playlistsReferencingPreset,
                        onOpenFlowEditor = { courseCode, presetId ->
                            nav = nav.open(NavState.Detail.FlowEditor(courseCode, presetId))
                        },
                        onPlayCustomPreset = { preset, courseTitle, suiteLabel ->
                            if (model.previewCustomPreset(preset, courseTitle, suiteLabel)) {
                                nav = nav.open(NavState.Detail.Track)
                            }
                        },
                        onDuplicatePreset = { preset ->
                            discovery.duplicatePreset(preset.id, "${preset.name} Copy")
                        },
                        onDeletePreset = { preset -> discovery.deletePreset(preset.id) },
                        onPlayMedley = { medleyId, suiteLabel, rallyLabel ->
                            val courses = browse.courses(unlocked)
                            if (model.playCourseMedley(medleyId, languageTag, courses, suiteLabel, rallyLabel)) {
                                nav = nav.open(NavState.Detail.Track)
                            }
                        },
                        onInteractive = { nav = nav.open(NavState.Detail.Interactive(detail.code)) },
                    )
                }
                detail is NavState.Detail.FlowEditor -> {
                    val inventory = remember(detail.courseCode, ui.pack) {
                        model.inspectCourseForEditor(detail.courseCode)
                    }
                    val existing = remember(discoveryState.presets, detail.presetId) {
                        discoveryState.presets.firstOrNull { it.id == detail.presetId }
                    }
                    val courseTitle = browse?.displayName(detail.courseCode, languageTag) ?: detail.courseCode
                    val suiteLabel = androidx.compose.ui.res.stringResource(R.string.discovery_suite)
                    if (inventory == null) {
                        Text(androidx.compose.ui.res.stringResource(R.string.discovery_empty), Modifier.padding(24.dp))
                    } else {
                        com.mylittleblover.mkwplayer.ui.interactive.FlowEditorScreen(
                            courseTitle = courseTitle,
                            inventory = inventory,
                            existingPreset = existing,
                            onAudition = { draft ->
                                if (model.previewCustomPreset(draft, courseTitle, suiteLabel)) {
                                    nav = nav.open(NavState.Detail.Track)
                                }
                            },
                            onSave = { savedPreset ->
                                discovery.savePreset(savedPreset) {
                                    nav = nav.back() ?: nav
                                }
                            },
                        )
                    }
                }
                detail is NavState.Detail.Interactive -> {
                    if (browse == null || detail.code.removeSuffix("_00") !in browse.courses(unlocked)) {
                        Text(androidx.compose.ui.res.stringResource(R.string.discovery_empty), Modifier.padding(24.dp))
                    } else if (model.isStageOpen(detail.code)) {
                        PlayerDismissContainer(onDismiss = { nav = nav.back() ?: nav }) {
                            ConsolePage(model, ui, openPack, openFiles)
                        }
                    } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        com.mylittleblover.mkwplayer.ui.art.CourseCover(detail.code,
                            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(20.dp)))
                        Text(browse?.displayName(detail.code, languageTag) ?: detail.code, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(androidx.compose.ui.res.stringResource(R.string.discovery_interactive_note), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        androidx.compose.material3.Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (model.canStartStage(detail.code)) {
                                    val entryNames = remember(detail.code, ui.pack) { model.initialStageLabels(detail.code) }
                                    var selectedEntry by rememberSaveable(detail.code) { mutableStateOf(entryNames.firstOrNull()) }
                                    val choices = entryNames.mapIndexed { index, name -> index to name }.toMap()
                                    val effectiveEntry = selectedEntry?.takeIf { it in entryNames } ?: entryNames.firstOrNull()
                                    com.mylittleblover.mkwplayer.ui.interactive.InteractiveEntryChooser(
                                        choices = choices, selected = entryNames.indexOf(effectiveEntry).takeIf { it >= 0 }, enabled = !ui.busy && ui.flow?.fading != true,
                                        onSelect = { index -> selectedEntry = entryNames.getOrNull(index) })
                                    Button(
                                        onClick = { model.startStagePerformance(detail.code, effectiveEntry) },
                                        enabled = !ui.busy && ui.flow?.fading != true,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                    ) {
                                        Text(androidx.compose.ui.res.stringResource(R.string.discovery_start))
                                    }
                                } else {
                                    Text(androidx.compose.ui.res.stringResource(R.string.interactive_empty))
                                    OutlinedButton(
                                        onClick = openPack,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(androidx.compose.ui.res.stringResource(R.string.discovery_import))
                                    }
                                }
                            }
                        }
                    }
                }
                detail is NavState.Detail.AllPlaylists -> com.mylittleblover.mkwplayer.ui.home.DiscoveryAllPlaylists(
                    initialSection = detail.section,
                    catalog = browse,
                    unlocked = unlocked,
                    language = languageTag,
                    state = discoveryState,
                    jukeboxGames = ui.jukebox.games,
                    extraCatalog = model.extraCatalog,
                    packDirectory = model.packDirectory,
                    onCreate = discovery::createPlaylist,
                    onPlaylist = { nav = nav.open(NavState.Detail.Playlist(it)) },
                    onCup = { nav = nav.open(NavState.Detail.Cup(it)) },
                    onJukeboxGame = { nav = nav.open(NavState.Detail.JukeboxGame(it)) },
                    onCreateRouteOnMap = {
                        mapState.enterEdit()
                        nav = nav.select(Tab.WORLD_MAP)
                    },
                    onCustomRoute = { nav = nav.open(NavState.Detail.CustomRoute(it)) },
                    inspectCustomRoute = model::inspectCustomRoute,
                )
                detail is NavState.Detail.CustomRoute -> {
                    val activeRoute = discoveryState.customRoutes.firstOrNull { it.id == detail.id }
                    val suiteLabel = androidx.compose.ui.res.stringResource(R.string.discovery_suite)
                    val rallyLabel = androidx.compose.ui.res.stringResource(R.string.discovery_rally)
                    val roadPrefix = androidx.compose.ui.res.stringResource(R.string.route_road_prefix)
                    val subTag = androidx.compose.ui.res.stringResource(R.string.route_custom_substitute_tag)
                    val routeReport = remember(activeRoute, ui.pack, ui.jukebox) {
                        activeRoute?.let(model::inspectCustomRoute)
                    }
                    com.mylittleblover.mkwplayer.ui.home.DiscoveryCustomRouteDetail(
                        route = activeRoute,
                        catalog = browse,
                        language = languageTag,
                        presets = discoveryState.presets,
                        installedJukebox = model.allInstalledJukeboxItems(),
                        packDirectory = model.packDirectory,
                        courseArtwork = model.extraCatalog.courseArtwork,
                        report = routeReport,
                        onPlayRoute = { route ->
                            if (model.playCustomRoute(
                                    route = route,
                                    userPresets = discoveryState.presets,
                                    languageTag = languageTag,
                                    suiteLabel = suiteLabel,
                                    rallyLabel = rallyLabel,
                                    roadPrefix = roadPrefix,
                                    customSubstituteTag = subTag,
                                ) is PlayerViewModel.RoutePlayResult.Started
                            ) {
                                nav = nav.open(NavState.Detail.Track)
                            }
                        },
                        onEditOnMap = { route ->
                            val points = route.stops.mapNotNull { stop ->
                                model.mapAnchors?.stageTolerant(stop.stageCode)?.let {
                                    androidx.compose.ui.geometry.Offset(it.u, it.v)
                                }
                            }
                            mapState.loadCustomRoute(route, points)
                            nav = nav.select(Tab.WORLD_MAP)
                        },
                        onToggleLoop = { discovery.setCustomRouteLoop(detail.id, it) },
                        onRename = { discovery.renameCustomRoute(detail.id, it) },
                        onDelete = { discovery.deleteCustomRoute(detail.id); nav = nav.back() ?: nav },
                        onRemoveLastStop = { discovery.removeCustomRouteLastStop(detail.id) },
                        onUpdateStopPreset = { stopIdx, presetId ->
                            discovery.setCustomRouteStopPreset(detail.id, stopIdx, presetId)
                        },
                        onUpdateEdgeMusic = { edgeIdx, playMusic, trackKey ->
                            discovery.setCustomRouteEdgeMusic(detail.id, edgeIdx, playMusic, trackKey)
                        },
                        onOpenCourse = { code -> nav = nav.open(NavState.Detail.Course(code)) },
                        onOpenFlowEditor = { courseCode, presetId ->
                            nav = nav.open(NavState.Detail.FlowEditor(courseCode, presetId))
                        },
                    )
                }
                detail is NavState.Detail.Playlist -> {
                    val activePlaylist = discoveryState.playlists.firstOrNull { it.id == detail.id }
                    val suiteLabel = androidx.compose.ui.res.stringResource(R.string.discovery_suite)
                    val rallyLabel = androidx.compose.ui.res.stringResource(R.string.discovery_rally)
                    val nativePrefix = androidx.compose.ui.res.stringResource(R.string.discovery_native_transition_prefix)
                    com.mylittleblover.mkwplayer.ui.home.DiscoveryPlaylist(
                        playlist = activePlaylist,
                        catalog = browse,
                        unlocked = unlocked,
                        language = languageTag,
                        error = discoveryState.playlistError != null,
                        onAdd = { discovery.addToPlaylist(detail.id, it) },
                        onRemove = { discovery.removeFromPlaylist(detail.id, it) },
                        onRename = { discovery.renamePlaylist(detail.id, it) },
                        onMove = { from, to -> discovery.moveInPlaylist(detail.id, from, to) },
                        onDelete = { discovery.deletePlaylist(detail.id); nav = nav.back() ?: nav },
                        onCourse = { nav = nav.open(NavState.Detail.Course(it)) },
                        presets = discoveryState.presets,
                        packDirectory = model.packDirectory,
                        courseArtwork = model.extraCatalog.courseArtwork,
                        isThisPlaylistPlaying = ui.linearMode &&
                            ui.linearContextKind == com.mylittleblover.mkwplayer.player.LinearSessionStore.ContextKind.PLAYLIST &&
                            ui.linearContextId == detail.id,
                        onPlayAll = {
                            if (activePlaylist != null && model.playUserPlaylist(
                                    activePlaylist, discoveryState.presets, 0, false,
                                    languageTag, suiteLabel, rallyLabel, nativePrefix,
                                )
                            ) {
                                nav = nav.open(NavState.Detail.Track)
                            }
                        },
                        onShuffle = {
                            if (activePlaylist != null && model.playUserPlaylist(
                                    activePlaylist, discoveryState.presets, 0, true,
                                    languageTag, suiteLabel, rallyLabel, nativePrefix,
                                )
                            ) {
                                nav = nav.open(NavState.Detail.Track)
                            }
                        },
                        onPlayFrom = { idx ->
                            if (activePlaylist != null && model.playUserPlaylist(
                                    activePlaylist, discoveryState.presets, idx, false,
                                    languageTag, suiteLabel, rallyLabel, nativePrefix,
                                )
                            ) {
                                nav = nav.open(NavState.Detail.Track)
                            }
                        },
                        onToggleNativeDefault = { enabled ->
                            discovery.setPlaylistNativeTransitionDefault(detail.id, enabled)
                        },
                        onUpdateEntryPreset = { idx, presetId ->
                            discovery.setPlaylistEntryPreset(detail.id, idx, presetId)
                        },
                        onUpdateEntryNative = { idx, override ->
                            discovery.setPlaylistEntryNativeTransition(detail.id, idx, override)
                        },
                        onOpenFlowEditor = { courseCode, presetId ->
                            nav = nav.open(NavState.Detail.FlowEditor(courseCode, presetId))
                        },
                    )
                }
                detail is NavState.Detail.Cup -> {
                    val cupRoute = browse?.route(detail.id, unlocked)
                    val suiteLabel = androidx.compose.ui.res.stringResource(R.string.discovery_suite)
                    val rallyLabel = androidx.compose.ui.res.stringResource(R.string.discovery_rally)
                    val roadPrefix = androidx.compose.ui.res.stringResource(R.string.route_road_prefix)
                    com.mylittleblover.mkwplayer.ui.home.DiscoveryCup(
                        route = cupRoute,
                        catalog = browse,
                        language = languageTag,
                        installedJukebox = model.allInstalledJukeboxItems(),
                        packDirectory = model.packDirectory,
                        inspectOfficialRoute = model::inspectOfficialRoute,
                        onPlayRoute = { route, loop, overrides ->
                            if (model.playOfficialRoute(
                                    route = route,
                                    userPresets = discoveryState.presets,
                                    loopRoute = loop,
                                    sessionOverrides = overrides,
                                    languageTag = languageTag,
                                    suiteLabel = suiteLabel,
                                    rallyLabel = rallyLabel,
                                    roadPrefix = roadPrefix,
                                ) is PlayerViewModel.RoutePlayResult.Started
                            ) {
                                nav = nav.open(NavState.Detail.Track)
                            }
                        },
                        onMap = { id ->
                            val route = browse?.route(id, unlocked)
                            val points = route?.stops.orEmpty().mapNotNull { stop -> model.mapAnchors?.stageTolerant(stop.stageCode)?.let { androidx.compose.ui.geometry.Offset(it.u, it.v) } }
                            mapState.selectRoute(id, points)
                            nav = nav.select(Tab.WORLD_MAP)
                        },
                        onCourse = { code -> nav = nav.open(NavState.Detail.Course(code)) },
                    )
                }
                detail is NavState.Detail.JukeboxGame -> com.mylittleblover.mkwplayer.ui.home.DiscoveryJukeboxGame(
                    game = ui.jukebox.gameById(detail.id),
                    language = languageTag,
                    activeTrackKey = ui.selectedKey,
                    packDirectory = model.packDirectory,
                    allowPlayNext = ui.allowPlayNext,
                    onPlayFromIndex = { game, index, shuffle ->
                        model.playJukeboxQueue(game, index, languageTag, shuffle = shuffle)
                        nav = nav.open(NavState.Detail.Track)
                    },
                    onPlayNext = { item, game ->
                        model.playJukeboxNext(item, game, languageTag)
                    },
                )
                detail == NavState.Detail.Flow -> PlayerDismissContainer(onDismiss = { nav = nav.back() ?: nav }) {
                    ConsolePage(model, ui, openPack, openFiles)
                }
                detail == NavState.Detail.Track -> PlayerDismissContainer(onDismiss = { nav = nav.back() ?: nav }) {
                    SingleTrackPlayer(model, ui)
                }
                else -> when (page.tab) {
                    Tab.HOME -> com.mylittleblover.mkwplayer.ui.home.DiscoveryHome(browse, discoveryState, languageTag, unlocked,
                        onOpen = { nav = nav.open(NavState.Detail.Course(it)) }, onFavorite = discovery::toggleFavorite,
                        onImport = openPack, onPlayer = { nav = nav.open(if (ui.flow == null) NavState.Detail.Track else NavState.Detail.Flow) },
                        onToggle = { if (ui.canPause) model.pause() else if (ui.canResume) model.resume() },
                        onMedia = { mediaCategory = -1; mediaQuery = ""; nav = nav.select(Tab.MEDIA) },
                        session = ui, currentCode = model.currentStageCode,
                        packDirectory = model.packDirectory, artworkNames = playbackArtworkNames(model, ui),
                        courseArtwork = model.extraCatalog.courseArtwork,
                        onQuickPlay = { id -> model.startStagePerformance(id); nav = nav.open(NavState.Detail.Flow) },
                        onResumeSaved = { model.resumeContinueListening { nav = nav.open(NavState.Detail.Track) } },
                        onDismissSaved = model::dismissContinueListening)
                    Tab.MEDIA -> com.mylittleblover.mkwplayer.ui.home.DiscoveryMedia(browse, unlocked, languageTag,
                        category = mediaCategory, onCategory = { mediaCategory = it }, query = mediaQuery, onQuery = { mediaQuery = it },
                        onCourse = { nav = nav.open(NavState.Detail.Course(it)) }, onCup = { nav = nav.open(NavState.Detail.Cup(it)) },
                        jukebox = ui.jukebox,
                        extraCatalog = model.extraCatalog,
                        packDirectory = model.packDirectory,
                        activeTrackKey = ui.selectedKey,
                        allowPlayNext = ui.allowPlayNext,
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
                        },
                        onAuditionExtra = { entry ->
                            model.playExtraLinearQueue(listOf(entry), 0, LinearQueue.RepeatMode.OFF, false)
                        })
                    Tab.WORLD_MAP -> MapPage(
                        model = model,
                        ui = ui,
                        state = mapState,
                        discovery = discovery,
                        presets = discoveryState.presets,
                        onOpenCourse = { code -> nav = nav.open(NavState.Detail.Course(code)) },
                        onOpenCustomRoute = { routeId -> nav = nav.open(NavState.Detail.CustomRoute(routeId)) },
                        onPlayStarted = { nav = nav.open(NavState.Detail.Track) },
                    )
                    Tab.PLAYLISTS -> com.mylittleblover.mkwplayer.ui.home.DiscoveryPlaylists(
                        catalog = browse,
                        unlocked = unlocked,
                        language = languageTag,
                        state = discoveryState,
                        onCreate = discovery::createPlaylist,
                        onPlaylist = { nav = nav.open(NavState.Detail.Playlist(it)) },
                        onCup = { nav = nav.open(NavState.Detail.Cup(it)) },
                        jukeboxGames = ui.jukebox.games,
                        extraCatalog = model.extraCatalog,
                        packDirectory = model.packDirectory,
                        onViewAll = { section -> nav = nav.open(NavState.Detail.AllPlaylists(section)) },
                        onJukeboxGame = { gameId -> nav = nav.open(NavState.Detail.JukeboxGame(gameId)) },
                        onCustomRoute = { routeId -> nav = nav.open(NavState.Detail.CustomRoute(routeId)) },
                        onPlanRouteOnMap = {
                            mapState.enterEdit()
                            nav = nav.select(Tab.WORLD_MAP)
                        },
                    )
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
private fun CoursePage(
    model: PlayerViewModel,
    ui: PlayerViewModel.Ui,
    code: String,
    presets: List<com.mylittleblover.mkwplayer.library.CoursePreset> = emptyList(),
    playlistsReferencingPreset: (String) -> List<com.mylittleblover.mkwplayer.library.UserPlaylist> = { emptyList() },
    onOpenFlowEditor: (courseCode: String, presetId: String?) -> Unit = { _, _ -> },
    onPlayCustomPreset: (com.mylittleblover.mkwplayer.library.CoursePreset, courseTitle: String, suiteLabel: String) -> Unit = { _, _, _ -> },
    onDuplicatePreset: (com.mylittleblover.mkwplayer.library.CoursePreset) -> Unit = {},
    onDeletePreset: (com.mylittleblover.mkwplayer.library.CoursePreset) -> Unit = {},
    onPlayMedley: (medleyId: String, suiteLabel: String, rallyLabel: String) -> Unit = { _, _, _ -> },
    onInteractive: () -> Unit,
) {
    val context = LocalContext.current
    val languageTag = LocalConfiguration.current.locales[0].toLanguageTag()
    val names = remember { runCatching { LocalizedNames.parse(context.assets.open("localized_names.json").bufferedReader().use { it.readText() }) }.getOrNull() }
    val title = names?.name(code, languageTag) ?: androidx.compose.ui.res.stringResource(R.string.map_course)
    val discovery: com.mylittleblover.mkwplayer.ui.home.DiscoveryViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val saved by discovery.state.collectAsStateWithLifecycle()
    val stableCode = code.removeSuffix("_00")
    val coursePresets = remember(presets, stableCode) {
        presets.filter { it.courseCode.equals(stableCode, ignoreCase = true) }
    }
    var pendingDeletePreset by remember { mutableStateOf<com.mylittleblover.mkwplayer.library.CoursePreset?>(null) }

    pendingDeletePreset?.let { preset ->
        val refs = remember(preset.id, saved.playlists) { playlistsReferencingPreset(preset.id) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingDeletePreset = null },
            title = { Text(androidx.compose.ui.res.stringResource(R.string.preset_delete_warning_title)) },
            text = {
                Text(
                    if (refs.isEmpty()) preset.name
                    else androidx.compose.ui.res.stringResource(
                        R.string.preset_delete_warning_body,
                        refs.joinToString(", ") { it.name },
                    ),
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    onDeletePreset(preset)
                    pendingDeletePreset = null
                }) {
                    Text(androidx.compose.ui.res.stringResource(R.string.preset_delete))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { pendingDeletePreset = null }) {
                    Text(androidx.compose.ui.res.stringResource(R.string.discovery_cancel))
                }
            },
        )
    }

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
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                val artNames = listOfNotNull(
                    model.extraCatalog.courseArtwork[code],
                    model.extraCatalog.courseArtwork[code.removeSuffix("_00")],
                ).distinct()
                val cover = com.mylittleblover.mkwplayer.ui.art.courseCoverDrawable(code)
                ArtworkSlot(Modifier.fillMaxWidth()) { edge ->
                    val packed = PackArtwork.rememberArtwork(model.packDirectory, artNames, edge)
                    val art = packed ?: cover?.let { rememberResourceArtwork(it, edge) }
                    val frame = Modifier.fillMaxWidth().aspectRatio(rememberArtworkRatio(code, art, 16f / 9f))
                    when {
                        art != null -> FadingArtwork(art.bitmap, frame)
                        cover == null -> com.mylittleblover.mkwplayer.ui.art.CourseCover(code, frame)
                        else -> Box(frame)
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    androidx.compose.material3.Icon(Icons.Outlined.GraphicEq, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(androidx.compose.ui.res.stringResource(R.string.discovery_interactive), style = MaterialTheme.typography.titleLarge)
                    Text(androidx.compose.ui.res.stringResource(R.string.discovery_interactive_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        onClick = onInteractive,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(androidx.compose.ui.res.stringResource(R.string.discovery_open_player))
                    }
                }
            }
        }
        item {
            androidx.compose.material3.Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.Icon(Icons.Outlined.Headphones, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(androidx.compose.ui.res.stringResource(R.string.discovery_normal_playback), style = MaterialTheme.typography.titleMedium)
                    }
                    val suiteLabel = androidx.compose.ui.res.stringResource(R.string.discovery_suite)
                    val rallyLabel = androidx.compose.ui.res.stringResource(R.string.discovery_rally)
                    val medleys = remember(code, ui.pack, ui.unlock) { model.courseMedleysForCourse(code) }
                    if (medleys.isEmpty()) {
                        Text(suiteLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(androidx.compose.ui.res.stringResource(R.string.discovery_suite_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Spacer(Modifier.height(4.dp))
                        medleys.forEach { (medley, playable) ->
                            val label = if (medley.isKnockoutVariant) "$suiteLabel · $rallyLabel" else suiteLabel
                            val totalSec = (medley.durationMs / 1000L).coerceAtLeast(0L)
                            val dur = "%d:%02d".format(java.util.Locale.ROOT, totalSec / 60L, totalSec % 60L)
                            androidx.compose.material3.FilledTonalButton(
                                onClick = {
                                    discovery.recordRecent(stableCode)
                                    onPlayMedley(medley.id, suiteLabel, rallyLabel)
                                },
                                enabled = playable,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) {
                                androidx.compose.material3.Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(label, modifier = Modifier.weight(1f))
                                Text(dur, style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"))
                            }
                        }
                        if (medleys.none { it.second }) {
                            Text(
                                androidx.compose.ui.res.stringResource(R.string.discovery_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    androidx.compose.material3.HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                    Text(
                        androidx.compose.ui.res.stringResource(R.string.preset_user_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    coursePresets.forEach { preset ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            androidx.compose.material3.FilledTonalButton(
                                onClick = {
                                    discovery.recordRecent(stableCode)
                                    onPlayCustomPreset(preset, title, suiteLabel)
                                },
                                enabled = model.canStartStage(code),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                            ) {
                                androidx.compose.material3.Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(preset.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            }
                            androidx.compose.material3.IconButton(onClick = { onOpenFlowEditor(stableCode, preset.id) }) {
                                androidx.compose.material3.Icon(Icons.Filled.Edit, contentDescription = androidx.compose.ui.res.stringResource(R.string.preset_edit))
                            }
                            androidx.compose.material3.IconButton(onClick = { onDuplicatePreset(preset) }) {
                                androidx.compose.material3.Icon(Icons.Filled.ContentCopy, contentDescription = androidx.compose.ui.res.stringResource(R.string.preset_duplicate))
                            }
                            androidx.compose.material3.IconButton(onClick = { pendingDeletePreset = preset }) {
                                androidx.compose.material3.Icon(Icons.Filled.DeleteOutline, contentDescription = androidx.compose.ui.res.stringResource(R.string.preset_delete))
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { onOpenFlowEditor(stableCode, null) },
                        enabled = model.canStartStage(code),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                    ) {
                        androidx.compose.material3.Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(androidx.compose.ui.res.stringResource(R.string.preset_new))
                    }
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
private fun MapPage(
    model: PlayerViewModel,
    ui: PlayerViewModel.Ui,
    state: WorldMapState,
    discovery: com.mylittleblover.mkwplayer.ui.home.DiscoveryViewModel,
    presets: List<com.mylittleblover.mkwplayer.library.CoursePreset>,
    onOpenCourse: (String) -> Unit,
    onOpenCustomRoute: (String) -> Unit,
    onPlayStarted: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val languageTag = LocalConfiguration.current.locales[0].toLanguageTag()
    val suiteLabel = androidx.compose.ui.res.stringResource(R.string.discovery_suite)
    val rallyLabel = androidx.compose.ui.res.stringResource(R.string.discovery_rally)
    val roadPrefix = androidx.compose.ui.res.stringResource(R.string.route_road_prefix)
    val subTag = androidx.compose.ui.res.stringResource(R.string.route_custom_substitute_tag)
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
        installedJukebox = remember(ui.jukebox) { model.allInstalledJukeboxItems() },
        packDirectory = model.packDirectory,
        inspectOfficialRoute = model::inspectOfficialRoute,
        inspectDraftRoute = { pairs, edgeCfgs ->
            val stopCodes = if (pairs.isEmpty()) emptyList() else listOf(pairs.first().first) + pairs.map { it.second }
            val synthetic = com.mylittleblover.mkwplayer.routes.CustomRoute(
                id = state.editingRouteId ?: "draft",
                name = state.editingRouteName,
                stops = stopCodes.mapIndexed { idx, code ->
                    com.mylittleblover.mkwplayer.routes.CustomRouteStop(code, state.draftStopPresets[idx])
                },
                edges = pairs.indices.map { idx ->
                    edgeCfgs[idx] ?: com.mylittleblover.mkwplayer.routes.CustomRouteEdgeConfig(
                        from = pairs[idx].first,
                        to = pairs[idx].second,
                    )
                },
                loopWholeRoute = state.draftLoopWholeRoute,
            )
            model.inspectCustomRoute(synthetic)
        },
        onPlayOfficialRoute = { route, loop, overrides ->
            if (model.playOfficialRoute(
                    route = route,
                    userPresets = presets,
                    loopRoute = loop,
                    sessionOverrides = overrides,
                    languageTag = languageTag,
                    suiteLabel = suiteLabel,
                    rallyLabel = rallyLabel,
                    roadPrefix = roadPrefix,
                ) is PlayerViewModel.RoutePlayResult.Started
            ) {
                onPlayStarted()
            }
        },
        onSaveCustomRoute = { routeId, routeName, stops, edges, loop ->
            discovery.saveCustomRoute(routeId, routeName, stops, edges, loop) { res ->
                res.onSuccess { saved ->
                    state.exitEdit()
                    onOpenCustomRoute(saved.id)
                }
            }
        },
        onPlayCustomDraft = { routeName, stops, edges, loop ->
            val draftRoute = com.mylittleblover.mkwplayer.routes.CustomRoute(
                id = state.editingRouteId ?: "draft",
                name = routeName,
                stops = stops,
                edges = edges,
                loopWholeRoute = loop,
            )
            if (model.playCustomRoute(
                    route = draftRoute,
                    userPresets = presets,
                    languageTag = languageTag,
                    suiteLabel = suiteLabel,
                    rallyLabel = rallyLabel,
                    roadPrefix = roadPrefix,
                    customSubstituteTag = subTag,
                ) is PlayerViewModel.RoutePlayResult.Started
            ) {
                onPlayStarted()
            }
        },
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

@Composable
private fun PlayerDismissContainer(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val dismissThresholdPx = with(density) { 88.dp.toPx() }
    val maxDragPx = with(density) { 260.dp.toPx() }
    val offsetY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var dismissed by remember { mutableStateOf(false) }

    fun finishDrag(velocityY: Float) {
        if (dismissed) return
        scope.launch {
            if (offsetY.value > dismissThresholdPx || (offsetY.value > 18f && velocityY > 900f)) {
                dismissed = true
                onDismiss()
            } else {
                offsetY.animateTo(0f, com.mylittleblover.mkwplayer.ui.AppMotion.dragReleaseSpring)
            }
        }
    }

    val nestedScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (dismissed || offsetY.value <= 0f) return Offset.Zero
                val next = (offsetY.value + available.y).coerceIn(0f, maxDragPx)
                val consumedY = next - offsetY.value
                scope.launch { offsetY.snapTo(next) }
                return Offset(0f, consumedY)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (dismissed || source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                val next = (offsetY.value + available.y).coerceIn(0f, maxDragPx)
                val consumedY = next - offsetY.value
                if (consumedY > 0f) scope.launch { offsetY.snapTo(next) }
                return Offset(0f, consumedY)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (offsetY.value > 0f) {
                    finishDrag(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .offset { IntOffset(0, offsetY.value.roundToInt()) }
            .graphicsLayer {
                val fraction = (offsetY.value / maxDragPx).coerceIn(0f, 1f)
                alpha = 1f - fraction * 0.25f
            }
            .nestedScroll(nestedScroll),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        if (!dismissed) {
                            scope.launch {
                                offsetY.snapTo((offsetY.value + delta).coerceIn(0f, maxDragPx))
                            }
                        }
                    },
                    onDragStopped = { velocity -> finishDrag(velocity) },
                )
                .padding(top = 8.dp, bottom = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            com.mylittleblover.mkwplayer.ui.DragHandlePill()
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            content()
        }
    }
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
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth >= 640.dp) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            PlayerCover(model.currentStageCode, model.packDirectory, coverArtNames, Modifier.weight(1f))
                            PlayerInformation(model, ui, displayTitle, Modifier.weight(1f))
                        }
                    } else {
                        Column(
                            Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            PlayerCover(model.currentStageCode, model.packDirectory, coverArtNames, Modifier.fillMaxWidth(), maxCoverWidth = 320.dp)
                            PlayerInformation(model, ui, displayTitle, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            if (flow != null) {
                item {
                    androidx.compose.material3.Surface(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column(Modifier.animateContentSize(tween(220)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            AnimatedVisibility(visible = !ui.canStop && model.initialEntryChoices.isNotEmpty()) {
                                com.mylittleblover.mkwplayer.ui.interactive.InteractiveEntryChooser(
                                    choices = model.initialEntryChoices, selected = model.selectedEntrySegment,
                                    enabled = !ui.canStop && !ui.busy, onSelect = model::selectInitialEntry)
                            }
                            if (flow.performanceBlocker != null) Text(flow.performanceBlocker, style = MaterialTheme.typography.bodySmall)
                            else {
                                com.mylittleblover.mkwplayer.ui.interactive.InteractiveStageBanner(
                                    flow = flow,
                                    onCancel = model::cancelPendingPerformance,
                                )
                                com.mylittleblover.mkwplayer.ui.interactive.InteractiveActionBar(flow,
                                    onAdvance = model::advancePerformance, onFast = model::toggleFast,
                                    onCancel = model::cancelPendingPerformance, onEnd = model::takeOutro,
                                    showCancelInBar = false)
                                val hasExtraControls = (flow.outros.size > 1 && flow.canEnd) ||
                                    flow.hasSpeakerOverlay ||
                                    flow.versionNumbers.isNotEmpty()
                                if (hasExtraControls) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                    if (flow.outros.size > 1 && flow.canEnd) {
                                        com.mylittleblover.mkwplayer.ui.interactive.InteractiveOutroChooser(
                                            outros = flow.outros,
                                            enabled = flow.canEnd,
                                            onSelect = { number -> model.takeOutro(number) },
                                        )
                                    }
                                    if (flow.versionNumbers.isNotEmpty()) {
                                        com.mylittleblover.mkwplayer.ui.interactive.InteractiveVersionChooser(
                                            versionNumbers = flow.versionNumbers,
                                            selectedVersion = flow.selectedVersion,
                                            availableVersions = flow.availableVersions,
                                            onSelect = { number -> model.selectRecordingVersion(number) },
                                        )
                                    }
                                    if (flow.hasSpeakerOverlay) {
                                        Row(
                                            Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Text(
                                                androidx.compose.ui.res.stringResource(R.string.discovery_speaker_overlay),
                                                style = MaterialTheme.typography.titleSmall,
                                                modifier = Modifier.weight(1f).padding(end = 12.dp),
                                            )
                                            androidx.compose.material3.Switch(
                                                checked = flow.speakerEnabled,
                                                onCheckedChange = { model.toggleSpeakerOverlay() },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (flow == null || ui.detail != null) {
                item {
                    if (flow == null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onOpenPack) { Text(androidx.compose.ui.res.stringResource(R.string.interactive_import)) }
                        OutlinedButton(onClick = onOpenFiles) { Text(androidx.compose.ui.res.stringResource(R.string.interactive_files)) }
                    }
                    ui.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
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
    maxCoverWidth: Dp = 440.dp,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ArtworkSlot(Modifier.widthIn(max = maxCoverWidth).fillMaxWidth()) { edge ->
            val loadedArt = PackArtwork.rememberArtwork(packDirectory, artworkNames, edge)
            val ratio = rememberArtworkRatio(artworkNames, loadedArt, if (code != null) 16f / 9f else 1f)
            androidx.compose.material3.Surface(
                modifier = Modifier.fillMaxWidth().aspectRatio(ratio),
                shape = RoundedCornerShape(24.dp),
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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
            com.mylittleblover.mkwplayer.ui.interactive.InteractivePlaybackTimeline(ui.position, it)
        } ?: Text(androidx.compose.ui.res.stringResource(R.string.interactive_empty))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            val actionLabel = when {
                ui.canPause -> androidx.compose.ui.res.stringResource(R.string.interactive_pause)
                ui.canResume -> androidx.compose.ui.res.stringResource(R.string.interactive_resume)
                else -> androidx.compose.ui.res.stringResource(R.string.interactive_start)
            }
            val playInteraction = remember { MutableInteractionSource() }
            val playScale = com.mylittleblover.mkwplayer.ui.rememberPressScale(playInteraction)
            androidx.compose.material3.FilledIconButton(
                onClick = {
                    when { ui.canPause -> model.pause(); ui.canResume -> model.resume(); else -> model.startPerformance() }
                },
                enabled = flow != null && !ui.busy && (ui.canPause || ui.canResume || !flow.performing),
                interactionSource = playInteraction,
                modifier = Modifier
                    .size(64.dp)
                    .graphicsLayer { scaleX = playScale; scaleY = playScale }
                    .semantics { contentDescription = actionLabel },
                colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.surface),
            ) {
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
    onNext: (() -> Unit)? = null,
) {
    val flow = ui.flow
    val label = androidx.compose.ui.res.stringResource(if (ui.canPause) R.string.interactive_pause else R.string.interactive_resume)
    val stateLabel = androidx.compose.ui.res.stringResource(if (ui.canResume) R.string.discovery_paused else R.string.discovery_playing)
    val subtitle = flow?.let { com.mylittleblover.mkwplayer.ui.interactive.performanceStageText(it) }
        ?: ui.linearTrack?.subtitle?.takeIf { it.isNotBlank() }?.let { "$it · $stateLabel" }
        ?: stateLabel
    val density = LocalDensity.current
    val swipeOpenThresholdPx = with(density) { 36.dp.toPx() }
    val dragOffsetY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val rowInteraction = remember { MutableInteractionSource() }
    val pressScale = com.mylittleblover.mkwplayer.ui.rememberPressScale(rowInteraction, pressedScale = 0.985f)

    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        androidx.compose.material3.Surface(
            modifier = Modifier
                .offset { IntOffset(0, dragOffsetY.value.roundToInt()) }
                .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
                .testTag("mini-player")
                .fillMaxWidth()
                .height(64.dp)
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            dragOffsetY.snapTo((dragOffsetY.value + delta).coerceIn(-swipeOpenThresholdPx * 1.6f, 6f))
                        }
                    },
                    onDragStopped = { velocity ->
                        scope.launch {
                            if (dragOffsetY.value < -swipeOpenThresholdPx || velocity < -600f) {
                                dragOffsetY.snapTo(0f)
                                onOpen()
                            } else {
                                dragOffsetY.animateTo(0f, com.mylittleblover.mkwplayer.ui.AppMotion.snapSpring)
                            }
                        }
                    },
                ),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        ) {
            Column {
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = rowInteraction,
                            indication = androidx.compose.foundation.LocalIndication.current,
                            onClick = onOpen,
                        )
                        .padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
                        modifier = Modifier.size(44.dp).semantics { contentDescription = label }) {
                        androidx.compose.material3.Icon(if (ui.canPause) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null)
                    }
                    if (onNext != null) {
                        androidx.compose.material3.IconButton(
                            onClick = onNext,
                            modifier = Modifier.size(40.dp),
                        ) {
                            androidx.compose.material3.Icon(
                                Icons.Filled.SkipNext,
                                contentDescription = androidx.compose.ui.res.stringResource(R.string.jukebox_next),
                            )
                        }
                    }
                }
                val frame = flow?.sourceFrame
                val length = flow?.sourceFrames
                val progress = if (frame != null && frame >= 0 && length != null && length > 0)
                    (frame.toDouble() / length).toFloat().coerceIn(0f, 1f) else ui.progress.coerceIn(0f, 1f)
                com.mylittleblover.mkwplayer.ui.SmoothProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(bottom = 5.dp),
                    barHeight = 3.dp,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SingleTrackPlayer(model: PlayerViewModel, ui: PlayerViewModel.Ui) {
    val stageCode = model.currentStageCode
    val linearTrack = ui.linearTrack
    val hasLinearTrack = ui.linearMode && linearTrack != null
    var showQueueSheet by rememberSaveable { mutableStateOf(false) }
    var scrubbing by remember(ui.selectedKey) { mutableStateOf(false) }
    var scrubFraction by remember(ui.selectedKey) { mutableStateOf(0f) }
    var pendingSeek by remember(ui.selectedKey) { mutableStateOf<Float?>(null) }
    val reduceMotion = com.mylittleblover.mkwplayer.ui.rememberReducedMotion()

    LaunchedEffect(ui.progress, pendingSeek) {
        val target = pendingSeek ?: return@LaunchedEffect
        if (abs(ui.progress - target) < 0.02f) {
            pendingSeek = null
        }
    }
    LaunchedEffect(pendingSeek) {
        if (pendingSeek != null) {
            delay(600L)
            pendingSeek = null
        }
    }

    val playing = ui.canPause
    val artScale by animateFloatAsState(
        targetValue = if (playing || reduceMotion) 1f else 0.93f,
        animationSpec = com.mylittleblover.mkwplayer.ui.AppMotion.snapSpring,
        label = "single-track-art-scale",
    )
    val artElevation by animateDpAsState(
        targetValue = if (playing) 14.dp else 3.dp,
        animationSpec = tween(if (reduceMotion) 0 else 280, easing = com.mylittleblover.mkwplayer.ui.AppMotion.easeOut),
        label = "single-track-art-elevation",
    )

    val artworkBlock: @Composable (Modifier) -> Unit = { artworkModifier ->
        Box(artworkModifier, contentAlignment = Alignment.Center) {
            ArtworkSlot(
                Modifier.widthIn(max = 360.dp).fillMaxWidth()
                    .graphicsLayer { scaleX = artScale; scaleY = artScale },
            ) { edge ->
                val art = PackArtwork.rememberArtwork(model.packDirectory, playbackArtworkNames(model, ui), edge)
                val coverCode = stageCode ?: linearTrack?.courseMedley?.medley?.courseCode
                val ratio = rememberArtworkRatio(ui.selectedKey, art, if (coverCode != null) 16f / 9f else 1f)
                androidx.compose.material3.Surface(
                    Modifier.fillMaxWidth().aspectRatio(ratio),
                    shape = RoundedCornerShape(24.dp),
                    shadowElevation = artElevation,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    when {
                        art != null -> FadingArtwork(art.bitmap, Modifier.fillMaxSize())
                        coverCode != null -> com.mylittleblover.mkwplayer.ui.art.CourseCover(coverCode, Modifier.fillMaxSize())
                        else -> JukeboxArtworkCard(linearTrack)
                    }
                }
            }
        }
    }

    val controlsBlock: @Composable (Modifier) -> Unit = { controlsModifier ->
        Column(controlsModifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        androidx.compose.ui.res.stringResource(R.string.discovery_normal_playback),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    linearTrack?.ostIndex?.let { index ->
                        androidx.compose.material3.Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Text(
                                "#$index",
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        linearTrack?.title ?: ui.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (stageCode != null) {
                        val discovery: com.mylittleblover.mkwplayer.ui.home.DiscoveryViewModel =
                            androidx.lifecycle.viewmodel.compose.viewModel()
                        val saved by discovery.state.collectAsStateWithLifecycle()
                        val stableCode = stageCode.removeSuffix("_00")
                        val favorite = stableCode in saved.favorites
                        val favLabel = androidx.compose.ui.res.stringResource(
                            if (favorite) R.string.discovery_favorite_remove else R.string.discovery_favorite_add,
                        )
                        androidx.compose.material3.IconButton(
                            onClick = { discovery.toggleFavorite(stableCode) },
                            modifier = Modifier.semantics { contentDescription = favLabel },
                        ) {
                            androidx.compose.material3.Icon(
                                if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = null,
                            )
                        }
                    }
                }
                if (linearTrack != null) {
                    val metaParts = buildList {
                        if (linearTrack.subtitle.isNotBlank()) add(linearTrack.subtitle)
                        linearTrack.arrangement
                            ?.takeIf { arr ->
                                arr.isNotBlank() &&
                                    !linearTrack.title.contains(arr, ignoreCase = true) &&
                                    !linearTrack.subtitle.contains(arr, ignoreCase = true)
                            }
                            ?.let { add(it) }
                    }
                    if (metaParts.isNotEmpty()) {
                        Text(
                            metaParts.joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    linearTrack.nativeTransitionSourceLabel?.let { transitionLabel ->
                        androidx.compose.material3.Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Text(
                                text = transitionLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }

            // The status line is not decoration: without it a refused track shows an empty player with a clock
            // reading --:-- and no explanation, which reads as a broken app rather than a stated refusal.
            if (!ui.canPause && !ui.canResume && !hasLinearTrack) {
                Text(ui.status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ui.detail?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            val displayedProgress = when {
                scrubbing -> scrubFraction
                pendingSeek != null -> pendingSeek!!
                else -> ui.progress.coerceIn(0f, 1f)
            }
            val displayedPosition = if ((scrubbing || pendingSeek != null) && ui.totalFrames > 0L && ui.sampleRate > 0) {
                EngineSnapshot.formatPosition((displayedProgress.toDouble() * ui.totalFrames).toLong(), ui.sampleRate)
            } else {
                ui.position
            }
            val sliderInteraction = remember { MutableInteractionSource() }
            val dragged by sliderInteraction.collectIsDraggedAsState()
            val activeScrub = scrubbing || dragged || pendingSeek != null
            val trackHeight by animateDpAsState(
                targetValue = if (activeScrub && !reduceMotion) 10.dp else 6.dp,
                animationSpec = tween(if (reduceMotion) 0 else 160, easing = com.mylittleblover.mkwplayer.ui.AppMotion.easeOut),
                label = "scrub-track-height",
            )

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
                            pendingSeek = target
                            scrubbing = false
                            model.seekTo(target)
                        },
                        interactionSource = sliderInteraction,
                        modifier = Modifier.fillMaxWidth(),
                        thumb = { Spacer(Modifier.size(0.dp)) },
                        track = { sliderState ->
                            val span = (sliderState.valueRange.endInclusive - sliderState.valueRange.start)
                            val fillFraction = if (span > 0f) {
                                ((sliderState.value - sliderState.valueRange.start) / span).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(trackHeight)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            ) {
                                if (fillFraction > 0f) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth(fillFraction)
                                            .height(trackHeight)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                    )
                                }
                            }
                        },
                    )
                } else {
                    com.mylittleblover.mkwplayer.ui.SmoothProgressIndicator(ui.progress, modifier = Modifier.fillMaxWidth())
                }
                val timeStyle = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        displayedPosition,
                        style = timeStyle,
                        fontWeight = if (activeScrub) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (activeScrub) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(ui.total, style = timeStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            val label = androidx.compose.ui.res.stringResource(if (ui.canPause) R.string.interactive_pause else R.string.interactive_resume)
            val playInteraction = remember { MutableInteractionSource() }
            val playScale = com.mylittleblover.mkwplayer.ui.rememberPressScale(playInteraction, pressedScale = 0.92f)
            val prevInteraction = remember { MutableInteractionSource() }
            val prevScale = com.mylittleblover.mkwplayer.ui.rememberPressScale(prevInteraction, pressedScale = 0.94f)
            val nextInteraction = remember { MutableInteractionSource() }
            val nextScale = com.mylittleblover.mkwplayer.ui.rememberPressScale(nextInteraction, pressedScale = 0.94f)

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
            ) {
                if (hasLinearTrack) {
                    FilledTonalIconButton(
                        onClick = model::skipPrevious,
                        interactionSource = prevInteraction,
                        modifier = Modifier.size(52.dp).graphicsLayer { scaleX = prevScale; scaleY = prevScale },
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
                    interactionSource = playInteraction,
                    modifier = Modifier
                        .size(72.dp)
                        .graphicsLayer { scaleX = playScale; scaleY = playScale }
                        .semantics { contentDescription = label },
                    colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.onSurface,
                        contentColor = MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    androidx.compose.material3.Icon(
                        if (ui.canPause) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                    )
                }
                if (hasLinearTrack) {
                    FilledTonalIconButton(
                        onClick = model::skipNext,
                        interactionSource = nextInteraction,
                        modifier = Modifier.size(52.dp).graphicsLayer { scaleX = nextScale; scaleY = nextScale },
                    ) {
                        androidx.compose.material3.Icon(
                            Icons.Filled.SkipNext,
                            contentDescription = androidx.compose.ui.res.stringResource(R.string.jukebox_next),
                        )
                    }
                }
            }

            androidx.compose.material3.Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            ) {
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
                    val shuffleLabel = androidx.compose.ui.res.stringResource(
                        if (ui.queue.shuffle) R.string.jukebox_shuffle_on else R.string.jukebox_shuffle,
                    )
                    val queuedTotal = ui.queue.tempQueue.size + ui.queue.upNext.size
                    val queueLabel = androidx.compose.ui.res.stringResource(R.string.jukebox_queue)

                    Row(
                        Modifier.fillMaxWidth().padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SingleTrackUtilityButton(
                            icon = Icons.Filled.Shuffle,
                            label = shuffleLabel,
                            active = ui.queue.shuffle,
                            enabled = ui.allowShuffle,
                            onClick = model::toggleShuffle,
                            modifier = Modifier.weight(1f),
                        )
                        SingleTrackUtilityButton(
                            icon = repeatIcon,
                            label = repeatLabel,
                            active = ui.queue.repeatMode != LinearQueue.RepeatMode.OFF,
                            enabled = ui.allowRepeatToggle,
                            onClick = model::cycleRepeatMode,
                            modifier = Modifier.weight(1f),
                        )
                        SingleTrackUtilityButton(
                            icon = Icons.AutoMirrored.Filled.QueueMusic,
                            label = queueLabel,
                            active = false,
                            badgeCount = queuedTotal,
                            onClick = { showQueueSheet = true },
                            modifier = Modifier.weight(1f),
                        )
                        SingleTrackUtilityButton(
                            icon = Icons.Filled.Stop,
                            label = androidx.compose.ui.res.stringResource(R.string.interactive_stop),
                            active = false,
                            enabled = ui.canStop,
                            onClick = model::stop,
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    Box(Modifier.fillMaxWidth().padding(6.dp)) {
                        SingleTrackUtilityButton(
                            icon = Icons.Filled.Stop,
                            label = androidx.compose.ui.res.stringResource(R.string.interactive_stop),
                            active = false,
                            enabled = ui.canStop,
                            onClick = model::stop,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 640.dp) {
            Row(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                artworkBlock(Modifier.weight(1f))
                controlsBlock(Modifier.weight(1f))
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                artworkBlock(Modifier.fillMaxWidth())
                controlsBlock(Modifier.fillMaxWidth())
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
private fun SingleTrackUtilityButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    enabled: Boolean = true,
    badgeCount: Int = 0,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        active -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val semanticsLabel = if (badgeCount > 0) "$label ($badgeCount)" else label
    androidx.compose.material3.Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        color = if (active && enabled) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
        modifier = modifier.heightIn(min = 54.dp).semantics { contentDescription = semanticsLabel },
    ) {
        Column(
            Modifier.padding(horizontal = 2.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                androidx.compose.material3.Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = tint,
                )
                if (badgeCount > 0) {
                    androidx.compose.material3.Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            badgeCount.toString(),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                        )
                    }
                }
            }
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
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
    androidx.compose.material3.Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (highlight) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f) else androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
    ) {
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
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
