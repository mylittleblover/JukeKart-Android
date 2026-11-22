package com.mylittleblover.mkwplayer.ui.routes

import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import com.mylittleblover.mkwplayer.ui.kit.MkwBadge
import com.mylittleblover.mkwplayer.ui.kit.MkwBadgeTone
import com.mylittleblover.mkwplayer.ui.kit.MkwBottomSheet
import com.mylittleblover.mkwplayer.ui.kit.MkwButtonSize
import com.mylittleblover.mkwplayer.ui.kit.MkwCard
import com.mylittleblover.mkwplayer.ui.kit.MkwDialog
import com.mylittleblover.mkwplayer.ui.kit.MkwDivider
import com.mylittleblover.mkwplayer.ui.kit.MkwFilterPill
import com.mylittleblover.mkwplayer.ui.kit.MkwFloatingPanel
import com.mylittleblover.mkwplayer.ui.kit.MkwGhostButton
import com.mylittleblover.mkwplayer.ui.kit.MkwIconButton
import com.mylittleblover.mkwplayer.ui.kit.MkwIconButtonTone
import com.mylittleblover.mkwplayer.ui.kit.MkwKitTokens
import com.mylittleblover.mkwplayer.ui.kit.MkwOptionRow
import com.mylittleblover.mkwplayer.ui.kit.MkwPrimaryButton
import com.mylittleblover.mkwplayer.ui.kit.MkwRoseButton
import com.mylittleblover.mkwplayer.ui.kit.MkwSecondaryButton
import com.mylittleblover.mkwplayer.ui.kit.MkwSurfaceTone
import com.mylittleblover.mkwplayer.ui.kit.MkwSwitch
import com.mylittleblover.mkwplayer.ui.kit.MkwTextField
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mylittleblover.mkwplayer.R
import com.mylittleblover.mkwplayer.library.MapAnchors
import com.mylittleblover.mkwplayer.routes.CustomRouteEdgeConfig
import com.mylittleblover.mkwplayer.routes.CustomRouteStop
import com.mylittleblover.mkwplayer.routes.LocalizedNames
import com.mylittleblover.mkwplayer.routes.OfficialRoute
import com.mylittleblover.mkwplayer.routes.OfficialRouteCatalog
import com.mylittleblover.mkwplayer.routes.OfficialRouteKind
import com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog
import com.mylittleblover.mkwplayer.ui.AppMotion
import com.mylittleblover.mkwplayer.ui.rememberReducedMotion
import com.mylittleblover.mkwplayer.ui.art.CupArt
import com.mylittleblover.mkwplayer.ui.art.MapArtworkCache
import com.mylittleblover.mkwplayer.ui.art.ArtworkSlot
import com.mylittleblover.mkwplayer.ui.art.ResourceArtwork
import com.mylittleblover.mkwplayer.ui.art.artworkFade
import com.mylittleblover.mkwplayer.ui.art.rememberResourceArtwork
import com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import com.mylittleblover.mkwplayer.ui.art.StageArt
import java.io.File
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun OfficialMapScreen(
    image: ImageBitmap,
    anchors: MapAnchors.Catalog,
    routes: OfficialRouteCatalog,
    names: LocalizedNames,
    languageTag: String,
    specialUnlocked: Boolean,
    playableStages: Set<String>,
    state: WorldMapState,
    onOpenCourse: (String) -> Unit,
    modifier: Modifier = Modifier,
    connections: com.mylittleblover.mkwplayer.library.StageConnections.Catalog? = null,
    installedJukebox: List<Pair<JukeboxCatalog.TrackItem, JukeboxCatalog.Game>> = emptyList(),
    packDirectory: File? = null,
    inspectOfficialRoute: (OfficialRoute, Map<Int, String>, RoadJukeboxCatalog.RoadMusicRuleMode) -> RoadJukeboxCatalog.RouteValidationReport? = { _, _, _ -> null },
    inspectDraftRoute: (List<Pair<String, String>>, Map<Int, CustomRouteEdgeConfig>, RoadJukeboxCatalog.RoadMusicRuleMode) -> RoadJukeboxCatalog.RouteValidationReport? = { _, _, _ -> null },
    onPlayOfficialRoute: (OfficialRoute, Boolean, Map<Int, String>, RoadJukeboxCatalog.RoadMusicRuleMode, RoadJukeboxCatalog.RouteSuiteMode) -> Unit = { _, _, _, _, _ -> },
    onSaveCustomRoute: (routeId: String?, name: String, stops: List<CustomRouteStop>, edges: List<CustomRouteEdgeConfig>, loopWholeRoute: Boolean, roadRuleMode: RoadJukeboxCatalog.RoadMusicRuleMode, routeSuiteMode: RoadJukeboxCatalog.RouteSuiteMode) -> Unit = { _, _, _, _, _, _, _ -> },
    onPlayCustomDraft: (name: String, stops: List<CustomRouteStop>, edges: List<CustomRouteEdgeConfig>, loopWholeRoute: Boolean, roadRuleMode: RoadJukeboxCatalog.RoadMusicRuleMode, routeSuiteMode: RoadJukeboxCatalog.RouteSuiteMode) -> Unit = { _, _, _, _, _, _ -> },
) {
    val visibleRoutes = routes.visible(specialUnlocked)
    val visibleStages = routes.stageCodes(specialUnlocked)
    val pins = anchors.stages.filter { it.code.removeSuffix("_00") in visibleStages }
    val route = visibleRoutes.firstOrNull { it.id == state.selectedRouteId }
    val course = state.selectedCourseCode?.takeIf { it in visibleStages }
    // The real connections of the selected course, as the game defines them. Empty when nothing is
    // selected, when no table shipped, or when the course is a sink such as Rainbow Road -- which is a
    // real answer rather than a missing one.
    val neighbours = remember(course, connections, visibleStages) {
        course?.let { connections?.neighbours(it) }
            ?.filter { it in visibleStages }
            .orEmpty()
    }
    val sink = course != null && connections != null && connections.outgoing(course).isEmpty()
    var picker by rememberSaveable { mutableStateOf(false) }
    var stops by rememberSaveable { mutableStateOf(false) }
    var officialLoop by rememberSaveable(route?.id) { mutableStateOf(false) }
    var officialRuleMode by rememberSaveable(route?.id) { mutableStateOf(RoadJukeboxCatalog.RoadMusicRuleMode.AUTO) }
    var officialSuiteMode by rememberSaveable(route?.id) { mutableStateOf(RoadJukeboxCatalog.RouteSuiteMode.GAME_SUITE) }
    val sessionOverrides = remember(route?.id) { mutableStateMapOf<Int, String>() }
    var pickingEdgeIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var pickingOfficialLeg by rememberSaveable { mutableStateOf(false) }
    var saveDialogOpen by rememberSaveable { mutableStateOf(false) }
    var saveDraftName by rememberSaveable(state.editingRouteId) { mutableStateOf(state.editingRouteName) }
    val defaultRouteName = stringResource(R.string.route_default_name)

    val officialReport = remember(route, sessionOverrides.toMap(), officialRuleMode, packDirectory, installedJukebox) {
        route?.let { inspectOfficialRoute(it, sessionOverrides.toMap(), officialRuleMode) }
    }
    val draftReport = remember(state.draft, state.draftEdgeConfigs, state.draftRoadRuleMode, packDirectory, installedJukebox) {
        if (state.draft.isNotEmpty()) inspectDraftRoute(state.draft, state.draftEdgeConfigs, state.draftRoadRuleMode) else null
    }
    val fallbackName = stringResource(R.string.map_course)
    val name: (String) -> String = { names.name(it, languageTag) ?: fallbackName }

    /**
     * What a pin tap means.
     *
     * In edit mode a tap is not a selection: it either starts a connection or completes one, and the game's
     * own table decides whether that is allowed. A refused pair states why instead of doing nothing, and the
     * reverse of a real connection is refused rather than silently accepted with the ends swapped.
     *
     * The refusal sentences are resolved here, outside the lambda: `stringResource` is a composable call and
     * a tap happens after composition has finished.
     */
    val refusalReverse = stringResource(R.string.map_edit_reverse_only)
    val refusalSink = stringResource(R.string.map_edit_sink)
    val refusalMissing = stringResource(R.string.map_edit_refusal_missing)
    val onPinTap: (String) -> Unit = { code ->
        val tapped = code.removeSuffix("_00")
        if (!state.editing || connections == null) {
            state.selectCourse(code)
        } else if (state.editFrom == null) {
            state.editStart(tapped)
        } else {
            val from = state.editFrom!!
            when {
                from == tapped -> if (state.draft.isEmpty()) state.editStart(null)
                connections.connects(from, tapped) -> state.editAppend(from, tapped)
                connections.connects(tapped, from) -> state.editRefuse(refusalReverse)
                connections.outgoing(from).isEmpty() -> state.editRefuse("$refusalSink ${name(from)}")
                else -> state.editRefuse(refusalMissing)
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val wide = maxWidth >= 840.dp || (maxWidth >= 540.dp && maxHeight < 480.dp)
        val paneWidth = if (maxWidth >= 840.dp) 320.dp else 248.dp
        Row(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                val viewportMaxHeight = maxHeight
                MapCanvas(image, anchors.canvasWidth.toFloat(), pins, route, state, name, Modifier.fillMaxSize(),
                    neighbours = neighbours, onPinTap = onPinTap, draft = state.draft, editFrom = state.editFrom,
                    draftEdgeConfigs = state.draftEdgeConfigs,
                    packDirectory = packDirectory,
                    onDraftEdgeTap = { idx ->
                        pickingOfficialLeg = false
                        pickingEdgeIndex = idx
                    })
                val horizontalControls = maxHeight < 240.dp || (!wide && maxHeight < 400.dp)
                MkwFloatingPanel(
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
                        .widthIn(max = (maxWidth - if (horizontalControls) 196.dp else 72.dp).coerceIn(140.dp, 340.dp)),
                    shape = MkwKitTokens.Shape.pill,
                    shadowElevation = 3.dp,
                    alpha = 0.96f,
                ) {
                    Row(
                        Modifier.padding(start = 14.dp, end = if (wide) 14.dp else 6.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(Modifier.weight(1f, fill = false)) {
                            Text(
                                stringResource(R.string.map_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MkwKitTokens.Palette.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (wide && !horizontalControls) {
                                Text(
                                    stringResource(R.string.map_subtitle, pins.size),
                                    style = MkwKitTokens.tabular(MaterialTheme.typography.labelSmall),
                                    color = MkwKitTokens.Palette.inkSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (!wide) {
                            MkwFilterPill(
                                label = stringResource(R.string.map_official_routes),
                                selected = true,
                                onClick = { picker = true },
                                role = Role.Button,
                            )
                        }
                    }
                }
                val controls: @Composable () -> Unit = {
                    MapControl("+", stringResource(R.string.map_zoom_in), state.camera.zoom < MapTransform.MAX_ZOOM) { state.zoomBy(1.3f) }
                    MapControl("−", stringResource(R.string.map_zoom_out), state.camera.zoom > MapTransform.MIN_ZOOM) { state.zoomBy(1f / 1.3f) }
                    MapControl("⌖", stringResource(R.string.map_reset)) { state.reset() }
                    if (connections != null) {
                        // The editor is only offered when a connection table shipped: without one, every tap
                        // would be refused, which is a broken button rather than a missing feature.
                        MapControl(
                            symbol = if (state.editing) "✕" else "✎",
                            label = stringResource(if (state.editing) R.string.map_edit_exit else R.string.map_edit_enter),
                            active = state.editing,
                        ) {
                            if (state.editing) state.exitEdit() else state.enterEdit()
                        }
                    }
                }
                MkwFloatingPanel(
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                    shape = MkwKitTokens.Shape.pill,
                    shadowElevation = 3.dp,
                    alpha = 0.96f,
                ) {
                    if (horizontalControls) {
                        Row(Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) { controls() }
                    } else {
                        Column(Modifier.padding(3.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) { controls() }
                    }
                }
                if (!wide) {
                    val bottomKey = when {
                        state.editing -> "edit"
                        course != null -> "course:$course"
                        route != null -> "route:${route.id}"
                        else -> "browse"
                    }
                    val miniPlayerClearance = com.mylittleblover.mkwplayer.ui.LocalMiniPlayerOverlayPadding.current
                    val availablePanelHeight = (viewportMaxHeight - miniPlayerClearance).coerceAtLeast(180.dp)
                    val mapReducedMotion = rememberReducedMotion()
                    Box(
                        Modifier.align(Alignment.BottomCenter)
                            .padding(
                                start = 10.dp,
                                top = 10.dp,
                                end = 10.dp,
                                bottom = com.mylittleblover.mkwplayer.ui.rememberMiniPlayerBottomClearance(10.dp),
                            )
                            .widthIn(max = 640.dp)
                            .animateContentSize(tween(if (mapReducedMotion) 0 else 240, easing = AppMotion.easeOut)),
                    ) {
                        AnimatedContent(
                            targetState = bottomKey,
                            transitionSpec = {
                                if (mapReducedMotion) {
                                    fadeIn(snap()) togetherWith fadeOut(snap())
                                } else {
                                    (slideInVertically(tween(220, easing = AppMotion.easeOut)) { it / 5 } +
                                        fadeIn(tween(200, easing = AppMotion.easeOut))) togetherWith
                                        (slideOutVertically(tween(180, easing = AppMotion.easeInOut)) { it / 6 } +
                                            fadeOut(tween(150)))
                                }
                            },
                            label = "map-bottom-card",
                        ) { key ->
                            if (key == "browse") {
                                MkwFloatingPanel(
                                    shape = MkwKitTokens.Shape.pill,
                                    shadowElevation = 3.dp,
                                    alpha = 0.96f,
                                ) {
                                    Text(
                                        stringResource(R.string.map_browse_hint),
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MkwKitTokens.Palette.inkSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            } else {
                                MkwFloatingPanel(
                                    modifier = Modifier.fillMaxWidth().heightIn(max = availablePanelHeight * if (state.editing) .62f else .56f),
                                    shape = MkwKitTokens.Shape.card,
                                    shadowElevation = 4.dp,
                                    alpha = 0.98f,
                                ) {
                                    if (state.editing) {
                                        ConnectionEditorPanel(
                                            state = state,
                                            name = name,
                                            languageTag = languageTag,
                                            connections = connections,
                                            draftReport = draftReport,
                                            onPickEdgeMusic = { idx ->
                                                pickingOfficialLeg = false
                                                pickingEdgeIndex = idx
                                            },
                                            onSaveClick = {
                                                saveDraftName = state.editingRouteName.ifBlank { defaultRouteName }
                                                saveDialogOpen = true
                                            },
                                            onPlayDraft = {
                                                val routeName = state.editingRouteName.ifBlank { defaultRouteName }
                                                onPlayCustomDraft(
                                                    routeName,
                                                    state.toCustomRouteStops(),
                                                    state.toCustomRouteEdges(),
                                                    state.draftLoopWholeRoute,
                                                    state.draftRoadRuleMode,
                                                    state.draftRouteSuiteMode,
                                                )
                                            },
                                            onDone = { stops = false },
                                        )
                                    } else if (course == null && route != null) {
                                        OfficialRouteCompactDock(
                                            route = route,
                                            languageTag = languageTag,
                                            name = name,
                                            officialReport = officialReport,
                                            onPlayRoute = {
                                                onPlayOfficialRoute(route, officialLoop, sessionOverrides.toMap(), officialRuleMode, officialSuiteMode)
                                            },
                                            onOpenStopsSheet = { stops = true },
                                            onClearRoute = { state.clearSelection() },
                                        )
                                    } else {
                                        MapSelectionPanel(
                                            route = route,
                                            course = course,
                                            name = name,
                                            languageTag = languageTag,
                                            playable = playableStages,
                                            state = state,
                                            onStops = { stops = true },
                                            onOpenCourse = onOpenCourse,
                                            neighbours = neighbours,
                                            sink = sink,
                                            connections = connections,
                                            officialReport = officialReport,
                                            loopRoute = officialLoop,
                                            onLoopChange = { officialLoop = it },
                                            ruleMode = officialRuleMode,
                                            onRuleModeChange = {
                                                officialRuleMode = it
                                                sessionOverrides.clear()
                                            },
                                            suiteMode = officialSuiteMode,
                                            onSuiteModeChange = { officialSuiteMode = it },
                                            onPlayRoute = {
                                                if (route != null) {
                                                    onPlayOfficialRoute(route, officialLoop, sessionOverrides.toMap(), officialRuleMode, officialSuiteMode)
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (wide) {
                Column(
                    Modifier
                        .width(paneWidth)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = com.mylittleblover.mkwplayer.ui.rememberMiniPlayerBottomClearance(12.dp)),
                ) {
                    MkwGhostButton(
                        text = stringResource(R.string.map_official_routes),
                        onClick = { picker = true },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    if (state.editing) {
                        ConnectionEditorPanel(
                            state = state,
                            name = name,
                            languageTag = languageTag,
                            connections = connections,
                            draftReport = draftReport,
                            onPickEdgeMusic = { idx ->
                                pickingOfficialLeg = false
                                pickingEdgeIndex = idx
                            },
                            onSaveClick = {
                                saveDraftName = state.editingRouteName.ifBlank { defaultRouteName }
                                saveDialogOpen = true
                            },
                            onPlayDraft = {
                                val routeName = state.editingRouteName.ifBlank { defaultRouteName }
                                onPlayCustomDraft(
                                    routeName,
                                    state.toCustomRouteStops(),
                                    state.toCustomRouteEdges(),
                                    state.draftLoopWholeRoute,
                                    state.draftRoadRuleMode,
                                    state.draftRouteSuiteMode,
                                )
                            },
                            onDone = { stops = false },
                            scrollable = false,
                        )
                    } else {
                        MapSelectionPanel(
                            route = route,
                            course = course,
                            name = name,
                            languageTag = languageTag,
                            playable = playableStages,
                            state = state,
                            onStops = { stops = true },
                            onOpenCourse = onOpenCourse,
                            neighbours = neighbours,
                            sink = sink,
                            connections = connections,
                            officialReport = officialReport,
                            loopRoute = officialLoop,
                            onLoopChange = { officialLoop = it },
                            ruleMode = officialRuleMode,
                            onRuleModeChange = {
                                officialRuleMode = it
                                sessionOverrides.clear()
                            },
                            suiteMode = officialSuiteMode,
                            onSuiteModeChange = { officialSuiteMode = it },
                            onPlayRoute = {
                                if (route != null) {
                                    onPlayOfficialRoute(route, officialLoop, sessionOverrides.toMap(), officialRuleMode, officialSuiteMode)
                                }
                            },
                            scrollable = false,
                        )
                    }
                }
            }
        }
    }
    if (picker) {
        val gpRoutes = remember(visibleRoutes) { visibleRoutes.filter { it.kind == OfficialRouteKind.GRAND_PRIX } }
        val rallyRoutes = remember(visibleRoutes) { visibleRoutes.filter { it.kind == OfficialRouteKind.RALLY } }
        var pickerKind by rememberSaveable(route?.id) {
            mutableStateOf(route?.kind ?: OfficialRouteKind.GRAND_PRIX)
        }
        val activePickerRoutes = if (pickerKind == OfficialRouteKind.GRAND_PRIX) gpRoutes else rallyRoutes
        MkwBottomSheet(
            onDismissRequest = { picker = false },
            title = stringResource(R.string.map_official_routes),
            subtitle = stringResource(R.string.map_picker_subtitle),
            modifier = Modifier.fillMaxHeight(0.82f),
        ) {
            Surface(
                shape = MkwKitTokens.Shape.pill,
                color = MkwKitTokens.Palette.inset,
                border = BorderStroke(MkwKitTokens.Stroke.hairline, MkwKitTokens.Palette.hairline),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf(
                        OfficialRouteKind.GRAND_PRIX to gpRoutes.size,
                        OfficialRouteKind.RALLY to rallyRoutes.size,
                    ).forEach { (kind, count) ->
                        val selectedTab = pickerKind == kind
                        val label = stringResource(
                            R.string.map_kind_count_fmt,
                            stringResource(if (kind == OfficialRouteKind.GRAND_PRIX) R.string.map_grand_prix else R.string.map_rallies),
                            count,
                        )
                        Surface(
                            onClick = { pickerKind = kind },
                            shape = MkwKitTokens.Shape.pill,
                            color = if (selectedTab) MkwKitTokens.Palette.ground else Color.Transparent,
                            shadowElevation = if (selectedTab) 1.dp else 0.dp,
                            border = if (selectedTab) BorderStroke(MkwKitTokens.Stroke.hairline, MkwKitTokens.Palette.crimson.copy(alpha = 0.28f)) else null,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 40.dp)
                                .semantics { role = Role.Tab; selected = selectedTab },
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = label,
                                    style = MkwKitTokens.tabular(MaterialTheme.typography.labelLarge),
                                    fontWeight = if (selectedTab) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedTab) MkwKitTokens.Palette.crimsonDeep else MkwKitTokens.Palette.inkSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            MkwDivider(modifier = Modifier.padding(top = 4.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            ) {
                items(activePickerRoutes, key = { it.id }) { item ->
                    val isSelected = route?.id == item.id
                    MkwCard(
                        onClick = {
                            val points = item.stops.mapNotNull { anchors.stageTolerant(it.stageCode)?.let { p -> Offset(p.u, p.v) } }
                            state.selectRoute(item.id, points)
                            stops = false
                            picker = false
                        },
                        tone = if (isSelected) MkwSurfaceTone.RoseTint else MkwSurfaceTone.Porcelain,
                        selected = isSelected,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CupArt.routeEmblem(item.id)?.let { emblem ->
                                ResourceArtwork(emblem, Modifier.size(46.dp))
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = item.displayName(languageTag),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isSelected) MkwKitTokens.Palette.crimsonDeep else MkwKitTokens.Palette.ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${name(item.start.stageCode)} → ${name(item.end.stageCode)} · ${stringResource(R.string.map_route_stops, item.stops.size)}",
                                    style = MkwKitTokens.tabular(MaterialTheme.typography.bodySmall),
                                    color = MkwKitTokens.Palette.inkSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MkwKitTokens.Palette.crimson,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (stops && route != null) {
        MkwBottomSheet(
            onDismissRequest = { stops = false },
            title = route.displayName(languageTag),
            subtitle = "${name(route.start.stageCode)} → ${name(route.end.stageCode)} · ${stringResource(R.string.map_route_stops, route.stops.size)}",
            modifier = Modifier.fillMaxHeight(0.84f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (officialReport?.canPlay == false) {
                    Text(
                        officialReport.blockingReason ?: stringResource(R.string.route_blocked_incomplete_pool),
                        style = MaterialTheme.typography.bodySmall,
                        color = MkwKitTokens.Palette.danger,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CupArt.routeEmblem(route.id)?.let { emblem ->
                        ResourceArtwork(emblem, Modifier.size(40.dp))
                    }
                    MkwPrimaryButton(
                        text = stringResource(R.string.route_play),
                        onClick = {
                            onPlayOfficialRoute(route, officialLoop, sessionOverrides.toMap(), officialRuleMode, officialSuiteMode)
                        },
                        enabled = officialReport?.canPlay != false,
                        leadingIcon = Icons.Filled.PlayArrow,
                        size = MkwButtonSize.Compact,
                        modifier = Modifier.weight(1f),
                    )
                    MkwFilterPill(
                        label = stringResource(R.string.route_loop_whole),
                        selected = officialLoop,
                        onClick = { officialLoop = !officialLoop },
                    )
                }
                RoadMusicRuleModeSelector(
                    selectedMode = officialRuleMode,
                    officialKind = route.kind,
                    onSelectMode = {
                        officialRuleMode = it
                        sessionOverrides.clear()
                    },
                )
                RouteSuiteModeSelector(
                    selectedMode = officialSuiteMode,
                    onSelectMode = { officialSuiteMode = it },
                )
            }
            MkwDivider(modifier = Modifier.padding(top = 4.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item(key = "stops-schematic-note") {
                    Text(
                        "${stringResource(R.string.map_route_readonly)} ${stringResource(R.string.map_schematic)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MkwKitTokens.Palette.inkSecondary,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                items(route.stops.size, key = { idx -> route.stops[idx].id }) { idx ->
                    val stop = route.stops[idx]
                    MkwCard(
                        onClick = {
                            state.selectCourse(stop.stageCode)
                            stops = false
                        },
                        tone = MkwSurfaceTone.Porcelain,
                        shape = MkwKitTokens.Shape.control,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                "${stop.ordinal}",
                                style = MkwKitTokens.tabular(MaterialTheme.typography.titleSmall),
                                fontWeight = FontWeight.Bold,
                                color = MkwKitTokens.Palette.crimson,
                            )
                            StageArt.resolve(stop.stageCode)?.let { artRes ->
                                ResourceArtwork(artRes, Modifier.size(36.dp))
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    name(stop.stageCode),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MkwKitTokens.Palette.ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (stop == route.start || stop == route.end) {
                                    Text(
                                        stringResource(if (stop == route.start) R.string.map_start_badge else R.string.map_finish_badge),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MkwKitTokens.Palette.crimsonDeep,
                                    )
                                }
                            }
                        }
                    }
                    val leg = officialReport?.legs?.getOrNull(idx)
                    if (leg != null) {
                        val summary = edgeResolutionSummary(leg.resolution, languageTag)
                        MkwCard(
                            onClick = {
                                pickingOfficialLeg = true
                                pickingEdgeIndex = idx
                            },
                            tone = MkwSurfaceTone.White,
                            shape = MkwKitTokens.Shape.thumb,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(Icons.Filled.MusicNote, null, Modifier.size(16.dp), tint = MkwKitTokens.Palette.crimson)
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "${stringResource(R.string.route_road_prefix)} ${name(leg.from)} → ${name(leg.to)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MkwKitTokens.Palette.ink,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        summary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (leg.resolution.isMissingOrIncomplete()) {
                                            MkwKitTokens.Palette.danger
                                        } else {
                                            MkwKitTokens.Palette.inkSecondary
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (leg.resolution.hasKnownPool()) {
                                    Text(
                                        stringResource(R.string.preset_edit),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MkwKitTokens.Palette.crimson,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val activeLegIndex = pickingEdgeIndex
    val activeReport = if (pickingOfficialLeg) officialReport else draftReport
    val activeLeg = if (activeLegIndex != null) activeReport?.legs?.getOrNull(activeLegIndex) else null
    if (activeLeg != null && activeLegIndex != null) {
        val currentCfg = if (!pickingOfficialLeg) {
            state.edgeConfigAt(activeLegIndex)
        } else {
            CustomRouteEdgeConfig(from = activeLeg.from, to = activeLeg.to)
        }
        val currentOverrideKey = if (pickingOfficialLeg) sessionOverrides[activeLegIndex] else currentCfg.selectedJukeboxKey
        val currentMuted = if (pickingOfficialLeg) false else !currentCfg.musicEnabled
        RouteEdgeMusicPickerSheet(
            legTitle = "${name(activeLeg.from)} → ${name(activeLeg.to)}",
            leg = activeLeg,
            installedJukebox = installedJukebox,
            allowCustomSubstitute = !pickingOfficialLeg,
            allowMute = !pickingOfficialLeg,
            sessionHint = pickingOfficialLeg,
            language = languageTag,
            packDirectory = packDirectory,
            currentSelectedKey = currentOverrideKey,
            currentMuted = currentMuted,
            onSelectAuto = {
                if (pickingOfficialLeg) {
                    sessionOverrides.remove(activeLegIndex)
                } else {
                    state.setEdgeMusic(activeLegIndex, musicEnabled = true, selectedJukeboxKey = null)
                }
            },
            onSelectMuted = {
                if (!pickingOfficialLeg) {
                    state.setEdgeMusic(activeLegIndex, musicEnabled = false, selectedJukeboxKey = null)
                }
            },
            onSelectTrackKey = { trackKey ->
                if (pickingOfficialLeg) {
                    sessionOverrides[activeLegIndex] = trackKey
                } else {
                    state.setEdgeMusic(activeLegIndex, musicEnabled = true, selectedJukeboxKey = trackKey)
                }
            },
            onDismiss = { pickingEdgeIndex = null },
        )
    }

    if (saveDialogOpen) {
        MkwDialog(
            onDismissRequest = { saveDialogOpen = false },
            title = stringResource(R.string.map_edit_save),
            confirmButton = {
                MkwPrimaryButton(
                    text = stringResource(R.string.route_save_changes),
                    onClick = {
                        val trimmed = saveDraftName.trim().ifEmpty { defaultRouteName }
                        state.editingRouteName = trimmed
                        onSaveCustomRoute(
                            state.editingRouteId,
                            trimmed,
                            state.toCustomRouteStops(),
                            state.toCustomRouteEdges(),
                            state.draftLoopWholeRoute,
                            state.draftRoadRuleMode,
                            state.draftRouteSuiteMode,
                        )
                        saveDialogOpen = false
                    },
                    enabled = state.draft.isNotEmpty(),
                    size = MkwButtonSize.Compact,
                )
            },
            dismissButton = {
                MkwGhostButton(
                    text = stringResource(R.string.discovery_cancel),
                    onClick = { saveDialogOpen = false },
                )
            },
        ) {
            MkwTextField(
                value = saveDraftName,
                onValueChange = { saveDraftName = it.take(120) },
                label = stringResource(R.string.route_name_label),
                singleLine = true,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConnectionEditorPanel(
    state: WorldMapState,
    name: (String) -> String,
    languageTag: String,
    connections: com.mylittleblover.mkwplayer.library.StageConnections.Catalog?,
    draftReport: RoadJukeboxCatalog.RouteValidationReport?,
    onPickEdgeMusic: (Int) -> Unit,
    onSaveClick: () -> Unit,
    onPlayDraft: () -> Unit,
    onDone: () -> Unit,
    scrollable: Boolean = true,
) {
    val stops = state.draftStops()
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            state.editingRouteName.ifBlank { stringResource(R.string.map_edit_title) },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            if (state.editingRouteId != null) stringResource(R.string.route_tail_only_note)
            else stringResource(R.string.map_edit_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.editRefusal?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        val bodyScrollMod = if (scrollable) {
            Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())
        } else {
            Modifier.fillMaxWidth()
        }
        Column(
            modifier = bodyScrollMod,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
        RoadMusicRuleModeSelector(
            selectedMode = state.draftRoadRuleMode,
            officialKind = null,
            onSelectMode = { state.draftRoadRuleMode = it },
        )
        RouteSuiteModeSelector(
            selectedMode = state.draftRouteSuiteMode,
            onSelectMode = { state.draftRouteSuiteMode = it },
        )
        if (stops.isEmpty()) {
            Text(stringResource(R.string.map_edit_empty), style = MaterialTheme.typography.bodyMedium)
        } else {
            // Numbered so the order is visible: a route is a sequence, not a set of connections.
            Text(stops.mapIndexed { index, code -> "${index + 1}. ${name(code)}" }.joinToString("  →  "),
                style = MaterialTheme.typography.bodyMedium)
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Loop, null, Modifier.size(16.dp), tint = MkwKitTokens.Palette.crimson)
                    Text(stringResource(R.string.route_loop_whole), style = MaterialTheme.typography.bodySmall, color = MkwKitTokens.Palette.ink)
                }
                MkwSwitch(
                    checked = state.draftLoopWholeRoute,
                    onCheckedChange = { state.draftLoopWholeRoute = it },
                )
            }
            for ((edgeIdx, edge) in state.draft.withIndex()) {
                val leg = draftReport?.legs?.getOrNull(edgeIdx)
                val edgeTitle = "${edgeIdx + 1}. ${name(edge.first)} → ${name(edge.second)}"
                val edgeSub = if (leg != null) {
                    edgeResolutionSummary(leg.resolution, languageTag)
                } else {
                    stringResource(R.string.route_edge_music_auto)
                }
                val cfg = state.edgeConfigAt(edgeIdx)
                MkwCard(
                    onClick = { onPickEdgeMusic(edgeIdx) },
                    tone = MkwSurfaceTone.Porcelain,
                    shape = MkwKitTokens.Shape.thumb,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            if (cfg.musicEnabled) Icons.Filled.MusicNote else Icons.Filled.MusicOff,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (leg?.resolution?.isMissingOrIncomplete() == true) {
                                MkwKitTokens.Palette.danger
                            } else {
                                MkwKitTokens.Palette.crimson
                            },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(edgeTitle, style = MaterialTheme.typography.labelMedium, color = MkwKitTokens.Palette.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(edgeSub, style = MaterialTheme.typography.bodySmall, color = MkwKitTokens.Palette.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if (draftReport?.canPlay == false) {
                Text(
                    draftReport.blockingReason ?: stringResource(R.string.route_blocked_incomplete_pool),
                    style = MaterialTheme.typography.bodySmall,
                    color = MkwKitTokens.Palette.danger,
                )
            }
        }
        }
        MkwDivider()
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MkwPrimaryButton(
                text = stringResource(R.string.map_edit_save),
                onClick = onSaveClick,
                enabled = state.draft.isNotEmpty(),
                leadingIcon = Icons.Filled.Save,
                size = MkwButtonSize.Compact,
            )
            MkwRoseButton(
                text = stringResource(R.string.route_play),
                onClick = onPlayDraft,
                enabled = state.draft.isNotEmpty() && draftReport?.canPlay != false,
                leadingIcon = Icons.Filled.PlayArrow,
                size = MkwButtonSize.Compact,
            )
            MkwGhostButton(
                text = stringResource(R.string.map_edit_undo),
                onClick = state::editUndo,
                enabled = state.draft.isNotEmpty(),
            )
            MkwGhostButton(
                text = stringResource(R.string.map_edit_clear),
                onClick = state::editClear,
                enabled = state.draft.isNotEmpty() && state.editingRouteId == null,
            )
            MkwGhostButton(
                text = stringResource(R.string.map_edit_exit),
                onClick = { state.exitEdit(); onDone() },
            )
        }
        // The provenance line is deliberately here and not in a tooltip: a route the listener builds is only
        // as trustworthy as the table behind it, and that table has a name.
        Text(stringResource(R.string.map_edit_source), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun OfficialRouteCompactDock(
    route: OfficialRoute,
    languageTag: String,
    name: (String) -> String,
    officialReport: RoadJukeboxCatalog.RouteValidationReport?,
    onPlayRoute: () -> Unit,
    onOpenStopsSheet: () -> Unit,
    onClearRoute: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CupArt.routeEmblem(route.id)?.let { emblem ->
                ResourceArtwork(emblem, Modifier.size(40.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = route.displayName(languageTag),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MkwKitTokens.Palette.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${name(route.start.stageCode)} → ${name(route.end.stageCode)} · ${stringResource(R.string.map_route_stops, route.stops.size)}",
                    style = MkwKitTokens.tabular(MaterialTheme.typography.bodySmall),
                    color = MkwKitTokens.Palette.inkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            MkwIconButton(
                icon = Icons.Filled.Close,
                contentDescription = stringResource(R.string.map_clear_route),
                onClick = onClearRoute,
                visualSize = 36.dp,
                iconSize = 18.dp,
                modifier = Modifier.size(40.dp),
            )
        }
        if (officialReport?.canPlay == false) {
            Text(
                text = officialReport.blockingReason ?: stringResource(R.string.route_blocked_incomplete_pool),
                style = MaterialTheme.typography.bodySmall,
                color = MkwKitTokens.Palette.danger,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MkwPrimaryButton(
                text = stringResource(R.string.route_play),
                onClick = onPlayRoute,
                enabled = officialReport?.canPlay != false,
                leadingIcon = Icons.Filled.PlayArrow,
                size = MkwButtonSize.Compact,
            )
            MkwSecondaryButton(
                text = stringResource(R.string.map_stops_sheet_action),
                onClick = onOpenStopsSheet,
                leadingIcon = Icons.Filled.Tune,
                size = MkwButtonSize.Compact,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MapSelectionPanel(
    route: OfficialRoute?, course: String?, name: (String) -> String, languageTag: String,
    playable: Set<String>, state: WorldMapState, onStops: () -> Unit, onOpenCourse: (String) -> Unit,
    neighbours: List<String> = emptyList(), sink: Boolean = false,
    connections: com.mylittleblover.mkwplayer.library.StageConnections.Catalog? = null,
    officialReport: RoadJukeboxCatalog.RouteValidationReport? = null,
    loopRoute: Boolean = false,
    onLoopChange: (Boolean) -> Unit = {},
    ruleMode: RoadJukeboxCatalog.RoadMusicRuleMode = RoadJukeboxCatalog.RoadMusicRuleMode.AUTO,
    onRuleModeChange: (RoadJukeboxCatalog.RoadMusicRuleMode) -> Unit = {},
    suiteMode: RoadJukeboxCatalog.RouteSuiteMode = RoadJukeboxCatalog.RouteSuiteMode.GAME_SUITE,
    onSuiteModeChange: (RoadJukeboxCatalog.RouteSuiteMode) -> Unit = {},
    onPlayRoute: () -> Unit = {},
    scrollable: Boolean = true,
) {
    var connectionsExpanded by remember(course) { mutableStateOf(false) }
    val panelMod = if (scrollable) {
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
    } else {
        Modifier.fillMaxWidth()
    }
    Column(panelMod.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when {
            course != null -> {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        name(course),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    MkwBadge(
                        text = stringResource(if (course in playable) R.string.map_audio_ready else R.string.map_audio_missing),
                        tone = if (course in playable) MkwBadgeTone.Rose else MkwBadgeTone.Neutral,
                        tabular = false,
                    )
                }
                // The game's own connections for this course. A course with none says so, because
                // "nowhere" is the answer for a finish line and pretending otherwise would show a road
                // the game does not have.
                when {
                    sink -> Text(
                        stringResource(R.string.map_course_sink),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    neighbours.isNotEmpty() -> {
                        // The count is the table's own, not the number drawn. A locked destination is hidden
                        // from the map but still exists in the export, and reporting "7 connections" when the
                        // game defines 8 would be a quieter lie than hiding the pin.
                        val total = connections?.outgoing(course)?.size ?: neighbours.size
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(MkwKitTokens.Shape.thumb)
                                .clickable { connectionsExpanded = !connectionsExpanded }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                stringResource(
                                    R.string.map_course_connections,
                                    total,
                                    neighbours.joinToString(" · ") { name(it) },
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = if (connectionsExpanded) 4 else 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                if (connectionsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (connectionsExpanded && total > neighbours.size) {
                            Text(
                                stringResource(R.string.map_course_connections_hidden, total - neighbours.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    else -> Text(
                        stringResource(R.string.map_course_no_connections),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MkwPrimaryButton(
                        text = stringResource(R.string.map_course_details),
                        onClick = { onOpenCourse(course) },
                        size = MkwButtonSize.Compact,
                    )
                    MkwGhostButton(
                        text = stringResource(if (route != null) R.string.map_back_route else R.string.map_close),
                        onClick = { state.clearCourse() },
                    )
                }
            }
            route != null -> {
                Text(
                    route.displayName(languageTag),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MkwKitTokens.Palette.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.map_start, name(route.start.stageCode)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MkwKitTokens.Palette.inkSecondary,
                    )
                    Text(
                        stringResource(R.string.map_finish, name(route.end.stageCode)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MkwKitTokens.Palette.inkSecondary,
                    )
                }
                if (officialReport?.canPlay == false) {
                    Text(
                        officialReport.blockingReason ?: stringResource(R.string.route_blocked_incomplete_pool),
                        style = MaterialTheme.typography.bodySmall,
                        color = MkwKitTokens.Palette.danger,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MkwPrimaryButton(
                        text = stringResource(R.string.route_play),
                        onClick = onPlayRoute,
                        enabled = officialReport?.canPlay != false,
                        leadingIcon = Icons.Filled.PlayArrow,
                        size = MkwButtonSize.Compact,
                    )
                    MkwSecondaryButton(
                        text = stringResource(R.string.map_view_stops),
                        onClick = onStops,
                        size = MkwButtonSize.Compact,
                    )
                    MkwGhostButton(
                        text = stringResource(R.string.map_clear_route),
                        onClick = { state.clearSelection() },
                    )
                }
                Row(
                    Modifier.fillMaxWidth().clickable { onLoopChange(!loopRoute) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(R.string.route_loop_whole),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MkwKitTokens.Palette.ink,
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                    )
                    MkwSwitch(checked = loopRoute, onCheckedChange = onLoopChange)
                }
                RoadMusicRuleModeSelector(
                    selectedMode = ruleMode,
                    officialKind = route.kind,
                    onSelectMode = onRuleModeChange,
                )
                RouteSuiteModeSelector(
                    selectedMode = suiteMode,
                    onSelectMode = onSuiteModeChange,
                )
            }
            else -> Text(stringResource(R.string.map_browse_hint), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun MapControl(
    symbol: String,
    label: String,
    enabled: Boolean = true,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    // Every symbol the caller passes must have an icon: an unmapped one used to fall through to the
    // location glyph, so the edit button looked like a second "recentre" control.
    val icon = when (symbol) {
        "+" -> Icons.Filled.Add
        "−" -> Icons.Filled.Remove
        "✎" -> Icons.Filled.Edit
        "✕" -> Icons.Filled.Close
        else -> Icons.Filled.MyLocation
    }
    MkwIconButton(
        icon = icon,
        contentDescription = label,
        onClick = onClick,
        enabled = enabled,
        tone = if (active) MkwIconButtonTone.Rose else MkwIconButtonTone.Plain,
        visualSize = 36.dp,
        iconSize = 20.dp,
        modifier = Modifier.size(40.dp),
    )
}

@Composable
private fun MapCanvas(
    image: ImageBitmap, sourceCanvasWidth: Float, pins: List<MapAnchors.Anchor>, route: OfficialRoute?, state: WorldMapState,
    name: (String) -> String, modifier: Modifier,
    neighbours: List<String> = emptyList(),
    onPinTap: (String) -> Unit = { state.selectCourse(it) },
    draft: List<Pair<String, String>> = emptyList(),
    editFrom: String? = null,
    draftEdgeConfigs: Map<Int, CustomRouteEdgeConfig> = emptyMap(),
    packDirectory: File? = null,
    onDraftEdgeTap: (Int) -> Unit = {},
) {
    val density = LocalDensity.current
    val viewConfig = LocalViewConfiguration.current
    val context = LocalContext.current.applicationContext
    val padding = with(density) { 24.dp.toPx() }
    val accent = MaterialTheme.colorScheme.primary
    // Connections are drawn in the secondary colour so a real road never reads as an official route line.
    val connectionAccent = MaterialTheme.colorScheme.secondary.copy(alpha = .85f)
    val routeSprites = if (route != null || state.selectedCourseCode != null || draft.isNotEmpty() || editFrom != null) {
        val sprites by produceState<MapArtworkCache.RouteSprites?>(null, context, packDirectory) {
            value = MapArtworkCache.loadRouteSprites(context, packDirectory)
        }
        sprites
    } else null

    val reducedMotion = rememberReducedMotion()

    // Non-flickering official highlight mask loading + true dual-layer crossfade
    val highlightRouteId = route?.id
    val loadedMask by produceState<Pair<String, ImageBitmap>?>(null, context, packDirectory, highlightRouteId) {
        value = if (highlightRouteId != null) {
            MapArtworkCache.loadHighlightMask(context, packDirectory, highlightRouteId)?.let { highlightRouteId to it }
        } else null
    }
    var currentMask by remember { mutableStateOf<Pair<String, ImageBitmap>?>(null) }
    var outgoingMask by remember { mutableStateOf<Pair<String, ImageBitmap>?>(null) }
    val currentMaskAlpha = remember { Animatable(0f) }
    val outgoingMaskAlpha = remember { Animatable(0f) }
    LaunchedEffect(highlightRouteId, loadedMask, reducedMotion) {
        val fadeDuration = if (reducedMotion) 0 else 360
        if (highlightRouteId == null || loadedMask == null) {
            outgoingMask?.let {
                launch {
                    outgoingMaskAlpha.animateTo(0f, tween(fadeDuration, easing = FastOutSlowInEasing))
                    outgoingMask = null
                }
            }
            if (currentMask != null) {
                currentMaskAlpha.animateTo(0f, tween(if (reducedMotion) 0 else 280, easing = FastOutSlowInEasing))
                currentMask = null
            }
        } else if (loadedMask?.first == highlightRouteId) {
            if (currentMask?.first != highlightRouteId) {
                val prev = currentMask
                val prevAlpha = currentMaskAlpha.value
                if (prev != null && prevAlpha > 0.01f && !reducedMotion) {
                    outgoingMask = prev
                    outgoingMaskAlpha.snapTo(prevAlpha)
                    launch {
                        outgoingMaskAlpha.animateTo(0f, tween(fadeDuration, easing = FastOutSlowInEasing))
                        outgoingMask = null
                    }
                } else {
                    outgoingMask = null
                    outgoingMaskAlpha.snapTo(0f)
                }
                currentMask = loadedMask
                currentMaskAlpha.snapTo(0f)
                currentMaskAlpha.animateTo(1f, tween(fadeDuration, easing = FastOutSlowInEasing))
            }
        }
    }

    // Dynamic spotlight shadow mask when inspecting a route, course, or editing a custom route
    val spotlightActive = loadedMask == null && (route != null || state.selectedCourseCode != null || editFrom != null)
    val spotlightAlpha by animateFloatAsState(
        targetValue = if (spotlightActive) 1f else 0f,
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else 280, easing = FastOutSlowInEasing),
        label = "map-spotlight-mask",
    )

    // Smooth sequential reveal AND retraction for official routes (on switch or cancel)
    var renderedRoute by remember { mutableStateOf<OfficialRoute?>(route) }
    val routeReveal = remember { Animatable(if (route != null) 1f else 0f) }
    LaunchedEffect(route?.id, reducedMotion) {
        if (route != null) {
            if (renderedRoute != null && renderedRoute?.id != route.id && routeReveal.value > 0.01f && !reducedMotion) {
                routeReveal.animateTo(0f, tween(durationMillis = 150, easing = AppMotion.easeOut))
            }
            renderedRoute = route
            if (reducedMotion) {
                routeReveal.snapTo(1f)
            } else {
                routeReveal.snapTo(0f)
                val durationMs = ((route.stops.size - 1).coerceAtLeast(1) * 140).coerceIn(360, 920)
                routeReveal.animateTo(1f, tween(durationMillis = durationMs, easing = FastOutSlowInEasing))
            }
        } else if (renderedRoute != null) {
            if (!reducedMotion && routeReveal.value > 0.01f) {
                routeReveal.animateTo(0f, tween(durationMillis = 220, easing = AppMotion.easeOut))
            } else {
                routeReveal.snapTo(0f)
            }
            renderedRoute = null
        }
    }

    // Smooth radial growth AND retraction for selected course's reachable neighbours
    var renderedCourse by remember { mutableStateOf(state.selectedCourseCode) }
    var renderedNeighbours by remember { mutableStateOf(neighbours) }
    val neighbourReveal = remember { Animatable(if (state.selectedCourseCode != null && neighbours.isNotEmpty()) 1f else 0f) }
    LaunchedEffect(state.selectedCourseCode, neighbours, reducedMotion) {
        val selectedCode = state.selectedCourseCode
        if (selectedCode != null) {
            renderedCourse = selectedCode
            renderedNeighbours = neighbours
            if (neighbours.isNotEmpty()) {
                if (reducedMotion) {
                    neighbourReveal.snapTo(1f)
                } else {
                    neighbourReveal.snapTo(0f)
                    neighbourReveal.animateTo(1f, tween(durationMillis = 320, easing = AppMotion.easeOut))
                }
            } else {
                neighbourReveal.snapTo(0f)
            }
        } else if (renderedCourse != null) {
            if (!reducedMotion && renderedNeighbours.isNotEmpty() && neighbourReveal.value > 0.01f) {
                neighbourReveal.animateTo(0f, tween(durationMillis = 200, easing = AppMotion.easeOut))
            } else {
                neighbourReveal.snapTo(0f)
            }
            renderedCourse = null
            renderedNeighbours = emptyList()
        }
    }

    // Progressive growth for each newly appended edge in custom draft editing
    val draftLastReveal = remember { Animatable(1f) }
    var previousDraftSize by remember { mutableIntStateOf(draft.size) }
    LaunchedEffect(draft.size, reducedMotion) {
        if (draft.size > previousDraftSize && draft.isNotEmpty() && !reducedMotion) {
            draftLastReveal.snapTo(0f)
            draftLastReveal.animateTo(1f, tween(durationMillis = 260, easing = AppMotion.easeOut))
        } else {
            draftLastReveal.snapTo(1f)
        }
        previousDraftSize = draft.size
    }

    // Gentle breathing pulse for active/editing pin halo
    val hasActivePin = state.selectedCourseCode != null || editFrom != null
    val infiniteTransition = rememberInfiniteTransition(label = "pin-halo-pulse")
    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pin-pulse-phase",
    )

    // Camera animation: instant snap while finger dragging, flinging, or reducedMotion; smooth spring on buttons/fit/cancel
    val gestureScope = rememberCoroutineScope()
    var isPointerInteracting by remember { mutableStateOf(false) }
    var isFlinging by remember { mutableStateOf(false) }
    var flingJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val directControl = isPointerInteracting || isFlinging || reducedMotion
    val zoomSpec = if (directControl) snap<Float>() else spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessMediumLow)
    val panSpec = if (directControl) snap<Float>() else spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessMediumLow)
    val animZoom by animateFloatAsState(state.camera.zoom, animationSpec = zoomSpec, label = "map-cam-zoom")
    val animCenterU by animateFloatAsState(state.camera.centerU, animationSpec = panSpec, label = "map-cam-u")
    val animCenterV by animateFloatAsState(state.camera.centerV, animationSpec = panSpec, label = "map-cam-v")

    val imageAspect = image.width.toFloat() / image.height
    val occurrences = route?.stops?.groupBy { it.stageCode }.orEmpty()
    BoxWithConstraints(
        modifier
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF214E6B), Color(0xFF14354C)),
                ),
            )
            .clipToBounds()
            .pointerInput(state) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        flingJob?.cancel()
                        isFlinging = false
                        isPointerInteracting = false
                        if (state.camera.zoom >= 2.1f) {
                            state.reset()
                        } else {
                            val targetZoom = (state.camera.zoom * 2.2f).coerceIn(1.9f, MapTransform.MAX_ZOOM)
                            state.gesture(tapOffset, Offset.Zero, targetZoom / state.camera.zoom)
                        }
                    },
                )
            }
            .pointerInput(state) {
                val touchSlop = viewConfig.touchSlop
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    flingJob?.cancel()
                    isFlinging = false
                    var zoom = 1f
                    var panTotal = Offset.Zero
                    var pastTouchSlop = false
                    var usedMultiTouch = false
                    var lastCentroid = Offset.Zero
                    var cumulativePan = Offset.Zero
                    val velocityTracker = VelocityTracker()
                    do {
                        val event = awaitPointerEvent()
                        val canceled = event.changes.any { it.isConsumed }
                        if (!canceled) {
                            val pointerCount = event.changes.count { it.pressed }
                            if (pointerCount >= 2) usedMultiTouch = true
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = false)
                            if (centroid != Offset.Unspecified) lastCentroid = centroid
                            if (!pastTouchSlop) {
                                zoom *= zoomChange
                                panTotal += panChange
                                val centroidSize = event.calculateCentroid(useCurrent = true)
                                if (centroidSize != Offset.Unspecified) lastCentroid = centroidSize
                                val motion = panTotal.getDistance()
                                val zoomMotion = kotlin.math.abs(1f - zoom) * 180f
                                if (motion > touchSlop || zoomMotion > touchSlop || pointerCount >= 2) {
                                    pastTouchSlop = true
                                    isPointerInteracting = true
                                }
                            }
                            if (pastTouchSlop) {
                                if (zoomChange != 1f || panChange != Offset.Zero) {
                                    state.gesture(lastCentroid, panChange, zoomChange)
                                    val timeMs = event.changes.firstOrNull()?.uptimeMillis ?: 0L
                                    if (!usedMultiTouch && pointerCount == 1) {
                                        cumulativePan += panChange
                                        velocityTracker.addPosition(timeMs, cumulativePan)
                                    }
                                }
                                event.changes.forEach {
                                    if (it.positionChanged()) it.consume()
                                }
                            }
                        }
                    } while (!canceled && event.changes.any { it.pressed })

                    if (pastTouchSlop) {
                        isPointerInteracting = false
                        val velocity = velocityTracker.calculateVelocity()
                        val vx = velocity.x
                        val vy = velocity.y
                        val speed = hypot(vx, vy)
                        if (!usedMultiTouch && speed > 140f) {
                            val anchor = if (lastCentroid != Offset.Unspecified) lastCentroid else Offset.Zero
                            flingJob = gestureScope.launch {
                                isFlinging = true
                                try {
                                    var curVx = vx
                                    var curVy = vy
                                    var lastNanos = withFrameNanos { it }
                                    while (hypot(curVx, curVy) > 24f) {
                                        val now = withFrameNanos { it }
                                        val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                                        lastNanos = now
                                        val factor = exp(-5.0f * dt)
                                        state.gesture(anchor, Offset(curVx * dt, curVy * dt), 1f)
                                        curVx *= factor
                                        curVy *= factor
                                    }
                                } finally {
                                    isFlinging = false
                                }
                            }
                        }
                    }
                }
            },
    ) {
        val targetTransform = state.configure(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), imageAspect, padding)
        val transform = remember(targetTransform.viewportWidth, targetTransform.viewportHeight, imageAspect, animZoom, animCenterU, animCenterV, padding) {
            MapTransform.create(
                targetTransform.viewportWidth,
                targetTransform.viewportHeight,
                imageAspect,
                MapCamera(animZoom, animCenterU, animCenterV),
                padding,
            )
        }
        val highlightedStages = remember(route?.id, state.selectedCourseCode, neighbours, editFrom, draft) {
            buildSet {
                route?.stops?.forEach { add(it.stageCode) }
                state.selectedCourseCode?.let(::add)
                addAll(neighbours)
                editFrom?.let(::add)
                draft.forEach { (a, b) -> add(a); add(b) }
            }
        }
        var lastSpotlightStages by remember { mutableStateOf<Set<String>>(emptySet()) }
        var lastSpotlightFocus by remember { mutableStateOf<String?>(null) }
        if (highlightedStages.isNotEmpty()) {
            lastSpotlightStages = highlightedStages
            lastSpotlightFocus = state.selectedCourseCode ?: editFrom
        } else if (spotlightAlpha <= 0.01f) {
            lastSpotlightStages = emptySet()
            lastSpotlightFocus = null
        }
        val activeSpotlightStages = highlightedStages.ifEmpty { lastSpotlightStages }

        Canvas(Modifier.fillMaxSize().then(artworkFade(image))) {
            if (transform.drawnWidth > 0f && transform.drawnHeight > 0f) {
                withTransform({
                    translate(transform.left, transform.top)
                    scale(transform.drawnWidth / image.width, transform.drawnHeight / image.height, pivot = Offset.Zero)
                }) { drawImage(image, filterQuality = FilterQuality.High) }

                val outBmp = outgoingMask?.second
                val outAlpha = if (outBmp != null) outgoingMaskAlpha.value else 0f
                val curBmp = currentMask?.second
                val curAlpha = if (curBmp != null) currentMaskAlpha.value else 0f
                val combinedLayerAlpha = (outAlpha + curAlpha).coerceIn(0f, 1f)
                if (combinedLayerAlpha > 0.01f) {
                    val layerPaint = Paint().apply { alpha = combinedLayerAlpha }
                    drawContext.canvas.saveLayer(Rect(Offset.Zero, size), layerPaint)
                    drawRect(Color(0x91060C1A))
                    if (outBmp != null && outAlpha > 0.01f) {
                        val normOut = (outAlpha / combinedLayerAlpha).coerceIn(0f, 1f)
                        withTransform({
                            translate(transform.left, transform.top)
                            scale(transform.drawnWidth / outBmp.width, transform.drawnHeight / outBmp.height, pivot = Offset.Zero)
                        }) {
                            drawImage(outBmp, alpha = normOut, blendMode = BlendMode.DstOut, filterQuality = FilterQuality.High)
                        }
                    }
                    if (curBmp != null && curAlpha > 0.01f) {
                        val normCur = (curAlpha / combinedLayerAlpha).coerceIn(0f, 1f)
                        withTransform({
                            translate(transform.left, transform.top)
                            scale(transform.drawnWidth / curBmp.width, transform.drawnHeight / curBmp.height, pivot = Offset.Zero)
                        }) {
                            drawImage(curBmp, alpha = normCur, blendMode = BlendMode.DstOut, filterQuality = FilterQuality.High)
                        }
                    }
                    drawContext.canvas.restore()
                }

                if (spotlightAlpha > 0.01f && activeSpotlightStages.isNotEmpty()) {
                    val layerPaint = Paint().apply { alpha = spotlightAlpha }
                    drawContext.canvas.saveLayer(Rect(Offset.Zero, size), layerPaint)
                    drawRect(Color(0x66060C1A))
                    for (code in activeSpotlightStages) {
                        val pin = pins.firstOrNull { it.code.removeSuffix("_00") == code } ?: continue
                        val center = transform.project(pin.u, pin.v)
                        val focusCode = state.selectedCourseCode ?: editFrom ?: lastSpotlightFocus
                        val isPrimaryFocus = code == focusCode
                        val spotRadius = transform.sourcePixelsToScreen(
                            pin.artSize * if (isPrimaryFocus) 1.55f else 1.05f,
                            sourceCanvasWidth,
                        ).coerceAtLeast(36f)
                        drawCircle(
                            brush = Brush.radialGradient(
                                0f to Color.White,
                                0.55f to Color.White.copy(alpha = 0.78f),
                                1f to Color.Transparent,
                                center = center,
                                radius = spotRadius,
                            ),
                            radius = spotRadius,
                            center = center,
                            blendMode = BlendMode.DstOut,
                        )
                    }
                    drawContext.canvas.restore()
                }
            }
            val officialLegs = renderedRoute?.stops?.zipWithNext().orEmpty()
            val officialLegCount = officialLegs.size.coerceAtLeast(1)
            for ((legIdx, pair) in officialLegs.withIndex()) {
                val (from, to) = pair
                val a = pins.firstOrNull { it.code.removeSuffix("_00") == from.stageCode } ?: continue
                val b = pins.firstOrNull { it.code.removeSuffix("_00") == to.stageCode } ?: continue
                val legProgress = ((routeReveal.value * officialLegCount - legIdx) * 1.15f).coerceIn(0f, 1f)
                if (legProgress > 0.01f) {
                    drawRouteConnection(
                        start = transform.project(a.u, a.v),
                        end = transform.project(b.u, b.v),
                        startRadius = transform.sourcePixelsToScreen(a.artSize * .38f, sourceCanvasWidth),
                        endRadius = transform.sourcePixelsToScreen(b.artSize * .38f, sourceCanvasWidth),
                        roadSprite = routeSprites?.officialRoad,
                        arrowSprite = routeSprites?.arrowGlow,
                        roadThickness = transform.sourcePixelsToScreen(42f, sourceCanvasWidth),
                        arrowSize = transform.sourcePixelsToScreen(48f, sourceCanvasWidth),
                        arrowFraction = .56f,
                        progress = legProgress,
                        fallbackColor = accent,
                        fallbackStroke = transform.sourcePixelsToScreen(8f, sourceCanvasWidth),
                        fallbackArrowSize = transform.sourcePixelsToScreen(28f, sourceCanvasWidth),
                    )
                }
            }
            // The selected course's real connections, drawn with straight lines so they stay distinct
            // from official tour ribbons. Each line matches an export row; a sink course draws none.
            val selected = renderedCourse
            if (selected != null && renderedNeighbours.isNotEmpty() && neighbourReveal.value > 0.01f) {
                val origin = pins.firstOrNull { it.code.removeSuffix("_00") == selected }
                if (origin != null) {
                    val start = transform.project(origin.u, origin.v)
                    val startRadius = transform.sourcePixelsToScreen(origin.artSize * .38f, sourceCanvasWidth)
                    for ((nIdx, target) in renderedNeighbours.withIndex()) {
                        val pin = pins.firstOrNull { it.code.removeSuffix("_00") == target } ?: continue
                        val stagger = (nIdx * 0.05f).coerceAtMost(0.24f)
                        val prog = ((neighbourReveal.value * (1f + stagger)) - stagger).coerceIn(0f, 1f)
                        if (prog > 0.01f) {
                            drawRouteConnection(
                                start = start,
                                end = transform.project(pin.u, pin.v),
                                startRadius = startRadius,
                                endRadius = transform.sourcePixelsToScreen(pin.artSize * .38f, sourceCanvasWidth),
                                roadSprite = routeSprites?.neighbourRoad,
                                arrowSprite = routeSprites?.arrowSharp,
                                roadThickness = transform.sourcePixelsToScreen(30f, sourceCanvasWidth),
                                arrowSize = transform.sourcePixelsToScreen(36f, sourceCanvasWidth),
                                arrowFraction = .62f,
                                alpha = .92f,
                                progress = prog,
                                fallbackColor = connectionAccent,
                                fallbackStroke = transform.sourcePixelsToScreen(5f, sourceCanvasWidth),
                            )
                        }
                    }
                }
            }
            // User custom route connections drawn last so the draft ribbon sits above base road links.
            if (draft.isNotEmpty()) {
                for ((edgeIdx, pair) in draft.withIndex()) {
                    val (from, to) = pair
                    val a = pins.firstOrNull { it.code.removeSuffix("_00") == from } ?: continue
                    val b = pins.firstOrNull { it.code.removeSuffix("_00") == to } ?: continue
                    val prog = if (edgeIdx == draft.lastIndex) draftLastReveal.value else 1f
                    if (prog > 0.01f) {
                        drawRouteConnection(
                            start = transform.project(a.u, a.v),
                            end = transform.project(b.u, b.v),
                            startRadius = transform.sourcePixelsToScreen(a.artSize * .38f, sourceCanvasWidth),
                            endRadius = transform.sourcePixelsToScreen(b.artSize * .38f, sourceCanvasWidth),
                            roadSprite = routeSprites?.draftRoad,
                            arrowSprite = routeSprites?.arrowGlow,
                            roadThickness = transform.sourcePixelsToScreen(44f, sourceCanvasWidth),
                            arrowSize = transform.sourcePixelsToScreen(46f, sourceCanvasWidth),
                            arrowFraction = .55f,
                            progress = prog,
                            fallbackColor = accent,
                            fallbackStroke = transform.sourcePixelsToScreen(9f, sourceCanvasWidth),
                        )
                    }
                }
            }
            // Pulsing halo ring around currently selected or active-editing course pin
            if (hasActivePin) {
                val focusCode = editFrom ?: state.selectedCourseCode
                val focusPin = focusCode?.let { fc -> pins.firstOrNull { it.code.removeSuffix("_00") == fc } }
                if (focusPin != null) {
                    val center = transform.project(focusPin.u, focusPin.v)
                    val baseR = transform.sourcePixelsToScreen(focusPin.artSize * .56f, sourceCanvasWidth).coerceAtLeast(14f)
                    val ringRadius = baseR * (1f + pulsePhase * 0.45f)
                    val ringAlpha = (1f - pulsePhase) * 0.42f
                    drawCircle(
                        color = accent.copy(alpha = ringAlpha),
                        radius = ringRadius,
                        center = center,
                    )
                }
            }
        }
        for (pin in pins) {
            val code = pin.code.removeSuffix("_00")
            val point = transform.project(pin.u, pin.v)
            val iconPixels = transform.sourcePixelsToScreen(pin.artSize, sourceCanvasWidth)
            val iconSize = with(density) { iconPixels.toDp() }
            val visits = occurrences[code].orEmpty()
            val selected = state.selectedCourseCode == code
            val editingHere = editFrom == code
            val selectionScale by animateFloatAsState(
                targetValue = if (selected || editingHere) 1.24f else 1f,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
                label = "course-selection",
            )
            val badgeScale by animateFloatAsState(
                targetValue = if (visits.isNotEmpty()) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
                label = "visit-badge-scale",
            )
            val description = if (visits.isEmpty()) name(code) else stringResource(R.string.map_pin_occurrences, name(code), visits.joinToString(stringResource(R.string.map_stop_separator)) { it.ordinal.toString() })
            Box(
                Modifier.offset { IntOffset((point.x - iconPixels / 2f).roundToInt(), (point.y - iconPixels / 2f).roundToInt()) }
                    .size(iconSize)
                    .zIndex(if (selected || editingHere) 1f else 0f)
                    .clickable(role = Role.Button) { onPinTap(code) }
                    .semantics { contentDescription = description; this.selected = selected || editingHere },
                contentAlignment = Alignment.Center,
            ) {
                StageArt.resolve(code)?.let { art ->
                    ArtworkSlot(Modifier.fillMaxSize()) { edgePx ->
                    val artwork = rememberResourceArtwork(art, edgePx)
                    if (artwork != null) {
                    val painter = remember(artwork) { BitmapPainter(artwork.bitmap, filterQuality = FilterQuality.High) }
                    Image(painter, contentDescription = null, modifier = Modifier.fillMaxSize()
                        .then(artworkFade(artwork.bitmap))
                        .graphicsLayer { scaleX = selectionScale; scaleY = selectionScale }
                        .drawWithContent {
                            if (selected || editingHere) {
                                // Draw the source alpha silhouette, rather than a circular marker.
                                val edge = size.minDimension * .035f
                                for (dx in -1..1) for (dy in -1..1) if (dx != 0 || dy != 0) {
                                    withTransform({ translate(dx * edge, dy * edge) }) {
                                        with(painter) { draw(size = size, colorFilter = ColorFilter.tint(Color.White)) }
                                    }
                                }
                            }
                            drawContent()
                        })
                    }
                    }
                }
                if (visits.isNotEmpty() && badgeScale > 0.02f) {
                    Surface(
                        Modifier.align(Alignment.TopEnd)
                            .offset(x = iconSize * .1f, y = -iconSize * .1f)
                            .graphicsLayer { scaleX = badgeScale; scaleY = badgeScale; alpha = badgeScale.coerceIn(0f, 1f) },
                        shape = CircleShape,
                        color = accent,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Text(visits.joinToString("·") { it.ordinal.toString() }, Modifier.padding(horizontal = iconSize * .08f, vertical = iconSize * .025f),
                            fontSize = (iconSize.value * .22f).sp, lineHeight = (iconSize.value * .25f).sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (draft.isNotEmpty()) {
            val badgePx = with(density) { 26.dp.toPx() }
            for ((edgeIdx, edge) in draft.withIndex()) {
                val a = pins.firstOrNull { it.code.removeSuffix("_00") == edge.first } ?: continue
                val b = pins.firstOrNull { it.code.removeSuffix("_00") == edge.second } ?: continue
                val mid = transform.project((a.u + b.u) / 2f, (a.v + b.v) / 2f)
                val cfg = draftEdgeConfigs[edgeIdx]
                val musicOn = cfg?.musicEnabled ?: true
                val badgeVisibleProgress = if (edgeIdx == draft.lastIndex) {
                    ((draftLastReveal.value - 0.35f) / 0.65f).coerceIn(0f, 1f)
                } else 1f
                if (badgeVisibleProgress > 0.02f) {
                    Surface(
                        onClick = { onDraftEdgeTap(edgeIdx) },
                        shape = CircleShape,
                        color = if (musicOn) MkwKitTokens.Palette.rose else MkwKitTokens.Palette.card,
                        contentColor = if (musicOn) MkwKitTokens.Palette.crimsonDeep else MkwKitTokens.Palette.inkSecondary,
                        border = BorderStroke(MkwKitTokens.Stroke.hairline, MkwKitTokens.Palette.hairline),
                        shadowElevation = 3.dp,
                        modifier = Modifier
                            .offset { IntOffset((mid.x - badgePx / 2f).roundToInt(), (mid.y - badgePx / 2f).roundToInt()) }
                            .graphicsLayer {
                                scaleX = badgeVisibleProgress
                                scaleY = badgeVisibleProgress
                                alpha = badgeVisibleProgress
                            }
                            .size(26.dp)
                            .zIndex(2f),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                if (musicOn) Icons.Filled.MusicNote else Icons.Filled.MusicOff,
                                contentDescription = stringResource(R.string.route_pick_edge_track),
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawRouteConnection(
    start: Offset,
    end: Offset,
    startRadius: Float,
    endRadius: Float,
    roadSprite: ImageBitmap?,
    arrowSprite: ImageBitmap?,
    roadThickness: Float,
    arrowSize: Float,
    arrowFraction: Float,
    alpha: Float = 1f,
    progress: Float = 1f,
    fallbackColor: Color,
    fallbackStroke: Float,
    fallbackArrowSize: Float = 0f,
) {
    val difference = end - start
    val length = difference.getDistance()
    if (length <= startRadius + endRadius) return
    val clampedProgress = progress.coerceIn(0f, 1f)
    if (clampedProgress <= 0f) return
    val unit = difference / length
    val fullSegLen = length - startRadius - endRadius
    val visibleSegLen = fullSegLen * clampedProgress
    if (visibleSegLen < 1f) return
    val segStart = start + unit * startRadius
    val visibleSegEnd = segStart + unit * visibleSegLen
    if (roadSprite == null) {
        drawLine(fallbackColor.copy(alpha = fallbackColor.alpha * alpha), segStart, visibleSegEnd, strokeWidth = fallbackStroke, cap = StrokeCap.Round)
        val arrowAlpha = ((clampedProgress - (arrowFraction - 0.12f)) / 0.2f).coerceIn(0f, 1f) * alpha
        if (fallbackArrowSize > 0f && arrowAlpha > 0.01f) {
            val middle = segStart + unit * (fullSegLen * arrowFraction)
            val perpendicular = Offset(-unit.y, unit.x)
            val arrow = Path().apply {
                val tip = middle + unit * fallbackArrowSize
                val left = middle - unit * fallbackArrowSize * .7f + perpendicular * fallbackArrowSize * .7f
                val right = middle - unit * fallbackArrowSize * .7f - perpendicular * fallbackArrowSize * .7f
                moveTo(tip.x, tip.y); lineTo(left.x, left.y); lineTo(right.x, right.y); close()
            }
            drawPath(arrow, fallbackColor.copy(alpha = fallbackColor.alpha * arrowAlpha))
        }
        return
    }
    val angleDeg = Math.toDegrees(atan2(unit.y.toDouble(), unit.x.toDouble())).toFloat()
    val tileH = roadThickness.coerceAtLeast(4f)
    val tileW = (tileH * roadSprite.width.toFloat() / roadSprite.height.toFloat()).coerceAtLeast(tileH)
    val topY = (-tileH / 2f).roundToInt()
    val dstH = tileH.roundToInt().coerceAtLeast(1)
    withTransform({
        translate(left = segStart.x, top = segStart.y)
        rotate(degrees = angleDeg, pivot = Offset.Zero)
    }) {
        var offsetX = 0f
        while (offsetX < visibleSegLen - .5f) {
            val chunkW = minOf(tileW, visibleSegLen - offsetX)
            val srcW = ((chunkW / tileW) * roadSprite.width).roundToInt().coerceIn(1, roadSprite.width)
            val dstStart = offsetX.roundToInt()
            val dstEnd = (offsetX + chunkW).roundToInt()
            val dstW = (dstEnd - dstStart).coerceAtLeast(1)
            drawImage(
                image = roadSprite,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(srcW, roadSprite.height),
                dstOffset = IntOffset(dstStart, topY),
                dstSize = IntSize(dstW, dstH),
                alpha = alpha,
                filterQuality = FilterQuality.Medium,
            )
            offsetX += chunkW
        }
        val arrowRevealAlpha = ((clampedProgress - (arrowFraction - 0.12f)) / 0.22f).coerceIn(0f, 1f)
        if (arrowSprite != null && arrowSize >= 4f && fullSegLen > arrowSize * .6f && arrowRevealAlpha > 0.01f) {
            val px = arrowSize.roundToInt().coerceAtLeast(1)
            val arrowX = (fullSegLen * arrowFraction - px / 2f).roundToInt()
            drawImage(
                image = arrowSprite,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(arrowSprite.width, arrowSprite.height),
                dstOffset = IntOffset(arrowX, (-px / 2f).roundToInt()),
                dstSize = IntSize(px, px),
                alpha = alpha * arrowRevealAlpha,
                filterQuality = FilterQuality.Medium,
            )
        }
    }
}
