package com.mylittleblover.mkwplayer.ui.routes

import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mylittleblover.mkwplayer.R
import com.mylittleblover.mkwplayer.library.MapAnchors
import com.mylittleblover.mkwplayer.routes.LocalizedNames
import com.mylittleblover.mkwplayer.routes.OfficialRoute
import com.mylittleblover.mkwplayer.routes.OfficialRouteCatalog
import com.mylittleblover.mkwplayer.routes.OfficialRouteKind
import com.mylittleblover.mkwplayer.ui.art.CupArt
import com.mylittleblover.mkwplayer.ui.art.MapArtworkCache
import com.mylittleblover.mkwplayer.ui.art.ArtworkSlot
import com.mylittleblover.mkwplayer.ui.art.artworkFade
import com.mylittleblover.mkwplayer.ui.art.rememberResourceArtwork
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import com.mylittleblover.mkwplayer.ui.art.StageArt
import kotlin.math.atan2
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
                from == tapped -> state.editStart(null)
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
                    neighbours = neighbours, onPinTap = onPinTap, draft = state.draft, editFrom = state.editFrom)
                val horizontalControls = maxHeight < 240.dp || (!wide && maxHeight < 400.dp)
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
                        .widthIn(max = (maxWidth - if (horizontalControls) 196.dp else 72.dp).coerceIn(140.dp, 340.dp)),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    shadowElevation = 3.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(Modifier.weight(1f, fill = false)) {
                            Text(
                                stringResource(R.string.map_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!horizontalControls) {
                                Text(
                                    stringResource(R.string.map_subtitle, pins.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (!wide) {
                            Surface(
                                onClick = { picker = true },
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Text(
                                    stringResource(R.string.map_official_routes),
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                )
                            }
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
                        MapControl(if (state.editing) "✕" else "✎",
                            stringResource(if (state.editing) R.string.map_edit_exit else R.string.map_edit_enter)) {
                            if (state.editing) state.exitEdit() else state.enterEdit()
                        }
                    }
                }
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    shadowElevation = 3.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
                ) {
                    if (horizontalControls) {
                        Row(Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) { controls() }
                    } else {
                        Column(Modifier.padding(3.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) { controls() }
                    }
                }
                if (!wide) {
                    val hasActiveBottom = state.editing || route != null || course != null
                    Box(
                        Modifier.align(Alignment.BottomCenter).padding(10.dp).widthIn(max = 640.dp)
                            .animateContentSize(tween(220, easing = com.mylittleblover.mkwplayer.ui.AppMotion.easeOut)),
                    ) {
                        if (!hasActiveBottom) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                                shadowElevation = 3.dp,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                            ) {
                                Text(
                                    stringResource(R.string.map_browse_hint),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        } else {
                            Surface(
                                Modifier.fillMaxWidth().heightIn(max = viewportMaxHeight * .33f),
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = .97f),
                                shadowElevation = 4.dp,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
                            ) {
                                Column(Modifier.verticalScroll(rememberScrollState())) {
                                    if (state.editing) {
                                        ConnectionEditorPanel(state, name, connections) { stops = false }
                                    } else {
                                        MapSelectionPanel(route, course, name, languageTag, playableStages, state, { stops = true }, onOpenCourse,
                                            neighbours = neighbours, sink = sink, connections = connections)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (wide) {
                Column(Modifier.width(paneWidth).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    TextButton(onClick = { picker = true }, modifier = Modifier.padding(horizontal = 8.dp)) { Text(stringResource(R.string.map_official_routes)) }
                    if (state.editing) {
                        ConnectionEditorPanel(state, name, connections) { stops = false }
                    } else {
                        MapSelectionPanel(route, course, name, languageTag, playableStages, state, { stops = true }, onOpenCourse,
                            neighbours = neighbours, sink = sink, connections = connections)
                    }
                }
            }
        }
    }
    val pickerSheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val stopSheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetScope = rememberCoroutineScope()
    if (picker) {
        ModalBottomSheet(sheetState = pickerSheet, onDismissRequest = { picker = false }) {
            Text(stringResource(R.string.map_official_routes), Modifier.padding(horizontal = 24.dp, vertical = 12.dp), style = MaterialTheme.typography.headlineSmall)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                for (kind in OfficialRouteKind.entries) {
                    item(key = kind.name) {
                        Text(stringResource(if (kind == OfficialRouteKind.GRAND_PRIX) R.string.map_grand_prix else R.string.map_rallies),
                            Modifier.padding(horizontal = 24.dp, vertical = 12.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    items(visibleRoutes.filter { it.kind == kind }, key = { it.id }) { item ->
                        ListItem(
                            leadingContent = CupArt.routeEmblem(item.id)?.let { emblem ->
                                { Image(painterResource(emblem), null, Modifier.size(40.dp)) }
                            },
                            headlineContent = { Text(item.displayName(languageTag)) },
                            supportingContent = { Text(stringResource(R.string.map_route_stops, item.stops.size)) },
                            modifier = Modifier.clickable(role = Role.Button) {
                                val points = item.stops.mapNotNull { anchors.stageTolerant(it.stageCode)?.let { p -> Offset(p.u, p.v) } }
                                state.selectRoute(item.id, points); stops = false; sheetScope.launch { pickerSheet.hide(); picker = false }
                            },
                        )
                    }
                }
            }
        }
    }
    if (stops && route != null) {
        ModalBottomSheet(sheetState = stopSheet, onDismissRequest = { stops = false }) {
            Text(route.displayName(languageTag), Modifier.padding(horizontal = 24.dp, vertical = 12.dp), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.map_route_readonly), Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.map_schematic), Modifier.padding(horizontal = 24.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(route.stops, key = { it.id }) { stop ->
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.map_route_stop, stop.ordinal, name(stop.stageCode))) },
                        supportingContent = if (stop == route.start || stop == route.end) ({ Text(stringResource(if (stop == route.start) R.string.map_start_badge else R.string.map_finish_badge)) }) else null,
                        modifier = Modifier.clickable(role = Role.Button) { state.selectCourse(stop.stageCode); sheetScope.launch { stopSheet.hide(); stops = false } },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConnectionEditorPanel(
    state: WorldMapState,
    name: (String) -> String,
    connections: com.mylittleblover.mkwplayer.library.StageConnections.Catalog?,
    onDone: () -> Unit,
) {
    val stops = state.draftStops()
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.map_edit_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.map_edit_hint), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.editRefusal?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (stops.isEmpty()) {
            Text(stringResource(R.string.map_edit_empty), style = MaterialTheme.typography.bodyMedium)
        } else {
            // Numbered so the order is visible: a route is a sequence, not a set of connections.
            Text(stops.mapIndexed { index, code -> "${index + 1}. ${name(code)}" }.joinToString("  →  "),
                style = MaterialTheme.typography.bodyMedium)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = state::editUndo, enabled = state.draft.isNotEmpty()) { Text(stringResource(R.string.map_edit_undo)) }
            TextButton(onClick = state::editClear, enabled = state.draft.isNotEmpty()) { Text(stringResource(R.string.map_edit_clear)) }
            TextButton(onClick = { state.exitEdit(); onDone() }) { Text(stringResource(R.string.map_edit_exit)) }
        }
        // The provenance line is deliberately here and not in a tooltip: a route the listener builds is only
        // as trustworthy as the table behind it, and that table has a name.
        Text(stringResource(R.string.map_edit_source), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MapSelectionPanel(
    route: OfficialRoute?, course: String?, name: (String) -> String, languageTag: String,
    playable: Set<String>, state: WorldMapState, onStops: () -> Unit, onOpenCourse: (String) -> Unit,
    neighbours: List<String> = emptyList(), sink: Boolean = false,
    connections: com.mylittleblover.mkwplayer.library.StageConnections.Catalog? = null,
) {
    var connectionsExpanded by remember(course) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (course in playable) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Text(
                            stringResource(if (course in playable) R.string.map_audio_ready else R.string.map_audio_missing),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (course in playable) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
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
                                .clip(RoundedCornerShape(8.dp))
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
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(
                        onClick = { onOpenCourse(course) },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.heightIn(min = 36.dp),
                    ) { Text(stringResource(R.string.map_course_details)) }
                    TextButton(
                        onClick = { state.clearCourse() },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.heightIn(min = 36.dp),
                    ) { Text(stringResource(if (route != null) R.string.map_back_route else R.string.map_close)) }
                }
            }
            route != null -> {
                Text(
                    route.displayName(languageTag),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.map_start, name(route.start.stageCode)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.map_finish, name(route.end.stageCode)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(
                        onClick = onStops,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.heightIn(min = 36.dp),
                    ) { Text(stringResource(R.string.map_view_stops)) }
                    TextButton(
                        onClick = { state.clearSelection() },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.heightIn(min = 36.dp),
                    ) { Text(stringResource(R.string.map_clear_route)) }
                }
            }
            else -> Text(stringResource(R.string.map_browse_hint), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun MapControl(symbol: String, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(40.dp).semantics { contentDescription = label },
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.36f),
        ),
    ) {
        // Every symbol the caller passes must have an icon: an unmapped one used to fall through to the
        // location glyph, so the edit button looked like a second "recentre" control.
        Icon(
            when (symbol) {
                "+" -> Icons.Filled.Add
                "−" -> Icons.Filled.Remove
                "✎" -> Icons.Filled.Edit
                "✕" -> Icons.Filled.Close
                else -> Icons.Filled.MyLocation
            },
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun MapCanvas(
    image: ImageBitmap, sourceCanvasWidth: Float, pins: List<MapAnchors.Anchor>, route: OfficialRoute?, state: WorldMapState,
    name: (String) -> String, modifier: Modifier,
    neighbours: List<String> = emptyList(),
    onPinTap: (String) -> Unit = { state.selectCourse(it) },
    draft: List<Pair<String, String>> = emptyList(),
    editFrom: String? = null,
) {
    val density = LocalDensity.current
    val context = LocalContext.current.applicationContext
    val padding = with(density) { 24.dp.toPx() }
    val accent = MaterialTheme.colorScheme.primary
    // Connections are drawn in the secondary colour so a real road never reads as an official route line.
    val connectionAccent = MaterialTheme.colorScheme.secondary.copy(alpha = .85f)
    val routeSprites = if (route != null || state.selectedCourseCode != null || draft.isNotEmpty() || editFrom != null) {
        val sprites by produceState<MapArtworkCache.RouteSprites?>(null, context) {
            value = MapArtworkCache.loadRouteSprites(context)
        }
        sprites
    } else null
    val highlightMaskId = route?.id?.let { CupArt.routeHighlightMask(it) }
    val highlightMask = if (highlightMaskId != null) androidx.compose.runtime.key(highlightMaskId) {
        val mask by produceState<ImageBitmap?>(null, context) {
            value = MapArtworkCache.loadHighlightMask(context, highlightMaskId)
        }
        mask
    } else null
    val maskAlpha by animateFloatAsState(
        if (highlightMask != null) 1f else 0f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "route-highlight-mask",
    )
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
                detectTransformGestures { centroid, pan, zoom, _ -> state.gesture(centroid, pan, zoom) }
            },
    ) {
        val transform = state.configure(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), image.width.toFloat() / image.height, padding)
        Canvas(Modifier.fillMaxSize().then(artworkFade(image))) {
            if (transform.drawnWidth > 0f && transform.drawnHeight > 0f) {
                withTransform({
                    translate(transform.left, transform.top)
                    scale(transform.drawnWidth / image.width, transform.drawnHeight / image.height, pivot = Offset.Zero)
                }) { drawImage(image, filterQuality = FilterQuality.High) }
                highlightMask?.let { mask ->
                    if (maskAlpha > 0f) {
                        val layerPaint = Paint().apply { alpha = maskAlpha }
                        drawContext.canvas.saveLayer(Rect(Offset.Zero, size), layerPaint)
                        drawRect(Color(0x91060C1A))
                        withTransform({
                            translate(transform.left, transform.top)
                            scale(transform.drawnWidth / mask.width, transform.drawnHeight / mask.height, pivot = Offset.Zero)
                        }) {
                            drawImage(mask, blendMode = BlendMode.DstOut, filterQuality = FilterQuality.High)
                        }
                        drawContext.canvas.restore()
                    }
                }
            }
            for ((from, to) in route?.stops?.zipWithNext().orEmpty()) {
                val a = pins.firstOrNull { it.code.removeSuffix("_00") == from.stageCode } ?: continue
                val b = pins.firstOrNull { it.code.removeSuffix("_00") == to.stageCode } ?: continue
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
                    fallbackColor = accent,
                    fallbackStroke = transform.sourcePixelsToScreen(8f, sourceCanvasWidth),
                    fallbackArrowSize = transform.sourcePixelsToScreen(28f, sourceCanvasWidth),
                )
            }
            // The selected course's real connections, drawn with straight vote lines so they stay distinct
            // from official tour ribbons. Each line matches an export row; a sink course draws none.
            val selected = state.selectedCourseCode
            if (selected != null && neighbours.isNotEmpty()) {
                val origin = pins.firstOrNull { it.code.removeSuffix("_00") == selected }
                if (origin != null) {
                    val start = transform.project(origin.u, origin.v)
                    val startRadius = transform.sourcePixelsToScreen(origin.artSize * .38f, sourceCanvasWidth)
                    for (target in neighbours) {
                        val pin = pins.firstOrNull { it.code.removeSuffix("_00") == target } ?: continue
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
                            fallbackColor = connectionAccent,
                            fallbackStroke = transform.sourcePixelsToScreen(5f, sourceCanvasWidth),
                        )
                    }
                }
            }
            // User custom route connections drawn last so the draft ribbon sits above base road links.
            if (draft.isNotEmpty()) {
                for ((from, to) in draft) {
                    val a = pins.firstOrNull { it.code.removeSuffix("_00") == from } ?: continue
                    val b = pins.firstOrNull { it.code.removeSuffix("_00") == to } ?: continue
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
                        fallbackColor = accent,
                        fallbackStroke = transform.sourcePixelsToScreen(9f, sourceCanvasWidth),
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
            val selectionScale by animateFloatAsState(if (selected || editingHere) 1.24f else 1f,
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing), label = "course-selection")
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
                if (visits.isNotEmpty()) {
                    Surface(Modifier.align(Alignment.TopEnd).offset(x = iconSize * .1f, y = -iconSize * .1f), shape = CircleShape, color = accent, contentColor = MaterialTheme.colorScheme.onPrimary) {
                        Text(visits.joinToString("·") { it.ordinal.toString() }, Modifier.padding(horizontal = iconSize * .08f, vertical = iconSize * .025f),
                            fontSize = (iconSize.value * .22f).sp, lineHeight = (iconSize.value * .25f).sp, fontWeight = FontWeight.Bold)
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
    fallbackColor: Color,
    fallbackStroke: Float,
    fallbackArrowSize: Float = 0f,
) {
    val difference = end - start
    val length = difference.getDistance()
    if (length <= startRadius + endRadius) return
    val unit = difference / length
    val segStart = start + unit * startRadius
    val segEnd = end - unit * endRadius
    if (roadSprite == null) {
        drawLine(fallbackColor, segStart, segEnd, strokeWidth = fallbackStroke, cap = StrokeCap.Round)
        if (fallbackArrowSize > 0f) {
            val middle = start + difference * arrowFraction
            val perpendicular = Offset(-unit.y, unit.x)
            val arrow = Path().apply {
                val tip = middle + unit * fallbackArrowSize
                val left = middle - unit * fallbackArrowSize * .7f + perpendicular * fallbackArrowSize * .7f
                val right = middle - unit * fallbackArrowSize * .7f - perpendicular * fallbackArrowSize * .7f
                moveTo(tip.x, tip.y); lineTo(left.x, left.y); lineTo(right.x, right.y); close()
            }
            drawPath(arrow, fallbackColor)
        }
        return
    }
    val segLen = length - startRadius - endRadius
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
        while (offsetX < segLen - .5f) {
            val chunkW = minOf(tileW, segLen - offsetX)
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
        if (arrowSprite != null && arrowSize >= 4f && segLen > arrowSize * .6f) {
            val px = arrowSize.roundToInt().coerceAtLeast(1)
            val arrowX = (segLen * arrowFraction - px / 2f).roundToInt()
            drawImage(
                image = arrowSprite,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(arrowSprite.width, arrowSprite.height),
                dstOffset = IntOffset(arrowX, (-px / 2f).roundToInt()),
                dstSize = IntSize(px, px),
                alpha = alpha,
                filterQuality = FilterQuality.Medium,
            )
        }
    }
}
