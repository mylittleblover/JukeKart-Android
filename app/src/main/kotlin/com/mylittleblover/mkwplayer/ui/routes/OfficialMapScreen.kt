package com.mylittleblover.mkwplayer.ui.routes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mylittleblover.mkwplayer.R
import com.mylittleblover.mkwplayer.library.MapAnchors
import com.mylittleblover.mkwplayer.routes.LocalizedNames
import com.mylittleblover.mkwplayer.routes.OfficialRoute
import com.mylittleblover.mkwplayer.routes.OfficialRouteCatalog
import com.mylittleblover.mkwplayer.routes.OfficialRouteKind
import com.mylittleblover.mkwplayer.ui.art.StageArt
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
) {
    val visibleRoutes = routes.visible(specialUnlocked)
    val visibleStages = routes.stageCodes(specialUnlocked)
    val pins = anchors.stages.filter { it.code.removeSuffix("_00") in visibleStages }
    val route = visibleRoutes.firstOrNull { it.id == state.selectedRouteId }
    val course = state.selectedCourseCode?.takeIf { it in visibleStages }
    var picker by rememberSaveable { mutableStateOf(false) }
    var stops by rememberSaveable { mutableStateOf(false) }
    val fallbackName = stringResource(R.string.map_course)
    val name: (String) -> String = { names.name(it, languageTag) ?: fallbackName }

    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val wide = maxWidth >= 840.dp || (maxWidth >= 540.dp && maxHeight < 480.dp)
        val paneWidth = if (maxWidth >= 840.dp) 320.dp else 248.dp
        Row(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                MapCanvas(image, anchors.canvasWidth.toFloat(), pins, route, state, name, Modifier.fillMaxSize())
                val horizontalControls = maxHeight < 240.dp || (!wide && maxHeight < 400.dp)
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp).widthIn(max = (maxWidth - if (horizontalControls) 184.dp else 88.dp).coerceIn(0.dp, 320.dp)),
                    shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .96f), shadowElevation = 2.dp,
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(stringResource(R.string.map_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (!horizontalControls) Text(stringResource(R.string.map_subtitle, pins.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!wide) TextButton(onClick = { picker = true }, contentPadding = PaddingValues(horizontal = 0.dp)) { Text(stringResource(R.string.map_official_routes)) }
                    }
                }
                val controls: @Composable () -> Unit = {
                    MapControl("+", stringResource(R.string.map_zoom_in), state.camera.zoom < MapTransform.MAX_ZOOM) { state.zoomBy(1.3f) }
                    MapControl("−", stringResource(R.string.map_zoom_out), state.camera.zoom > MapTransform.MIN_ZOOM) { state.zoomBy(1f / 1.3f) }
                    MapControl("⌖", stringResource(R.string.map_reset)) { state.reset() }
                }
                if (horizontalControls) {
                    Row(Modifier.align(Alignment.TopEnd).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { controls() }
                } else {
                    Column(Modifier.align(Alignment.TopEnd).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { controls() }
                }
                if (!wide) {
                    Surface(
                        Modifier.align(Alignment.BottomCenter).padding(12.dp).widthIn(max = 640.dp).fillMaxWidth().heightIn(max = maxHeight * .45f),
                        shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .98f), shadowElevation = 3.dp,
                    ) {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            MapSelectionPanel(route, course, name, languageTag, playableStages, state, { stops = true }, onOpenCourse)
                        }
                    }
                }
            }
            if (wide) {
                Column(Modifier.width(paneWidth).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    TextButton(onClick = { picker = true }, modifier = Modifier.padding(horizontal = 8.dp)) { Text(stringResource(R.string.map_official_routes)) }
                    MapSelectionPanel(route, course, name, languageTag, playableStages, state, { stops = true }, onOpenCourse)
                }
            }
        }
    }
    if (picker) {
        ModalBottomSheet(onDismissRequest = { picker = false }) {
            Text(stringResource(R.string.map_official_routes), Modifier.padding(horizontal = 24.dp, vertical = 12.dp), style = MaterialTheme.typography.headlineSmall)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                for (kind in OfficialRouteKind.entries) {
                    item(key = kind.name) {
                        Text(stringResource(if (kind == OfficialRouteKind.GRAND_PRIX) R.string.map_grand_prix else R.string.map_rallies),
                            Modifier.padding(horizontal = 24.dp, vertical = 12.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    items(visibleRoutes.filter { it.kind == kind }, key = { it.id }) { item ->
                        ListItem(
                            headlineContent = { Text(item.displayName(languageTag)) },
                            supportingContent = { Text(stringResource(R.string.map_route_stops, item.stops.size)) },
                            modifier = Modifier.clickable(role = Role.Button) {
                                val points = item.stops.mapNotNull { anchors.stageTolerant(it.stageCode)?.let { p -> Offset(p.u, p.v) } }
                                state.selectRoute(item.id, points); stops = false; picker = false
                            },
                        )
                    }
                }
            }
        }
    }
    if (stops && route != null) {
        ModalBottomSheet(onDismissRequest = { stops = false }) {
            Text(route.displayName(languageTag), Modifier.padding(horizontal = 24.dp, vertical = 12.dp), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.map_route_readonly), Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(route.stops, key = { it.id }) { stop ->
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.map_route_stop, stop.ordinal, name(stop.stageCode))) },
                        supportingContent = if (stop == route.start || stop == route.end) ({ Text(stringResource(if (stop == route.start) R.string.map_start_badge else R.string.map_finish_badge)) }) else null,
                        modifier = Modifier.clickable(role = Role.Button) { state.selectCourse(stop.stageCode); stops = false },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MapSelectionPanel(
    route: OfficialRoute?, course: String?, name: (String) -> String, languageTag: String,
    playable: Set<String>, state: WorldMapState, onStops: () -> Unit, onOpenCourse: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            course != null -> {
                Text(name(course), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(if (course in playable) R.string.map_audio_ready else R.string.map_audio_missing), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onOpenCourse(course) }) { Text(stringResource(R.string.map_course_details)) }
                    TextButton(onClick = { state.clearCourse() }) { Text(stringResource(if (route != null) R.string.map_back_route else R.string.map_close)) }
                }
            }
            route != null -> {
                Text(route.displayName(languageTag), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.map_start, name(route.start.stageCode)), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.map_finish, name(route.end.stageCode)), style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onStops) { Text(stringResource(R.string.map_view_stops)) }
                    TextButton(onClick = { state.clearSelection() }) { Text(stringResource(R.string.map_clear_route)) }
                }
                Text(stringResource(R.string.map_schematic), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> Text(stringResource(R.string.map_browse_hint), style = MaterialTheme.typography.bodyMedium)
        }
        Text(stringResource(R.string.map_art_notice), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MapControl(symbol: String, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp).semantics { contentDescription = label },
        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface)) {
        if (symbol == "⌖") {
            val ink = LocalContentColor.current
            Canvas(Modifier.size(24.dp).clearAndSetSemantics { }) {
                drawCircle(ink, 6.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                val distance = 10.dp.toPx(); val inner = 6.dp.toPx()
                for (axis in listOf(Offset(1f, 0f), Offset(-1f, 0f), Offset(0f, 1f), Offset(0f, -1f))) {
                    drawLine(ink, center + axis * inner, center + axis * distance, 2.dp.toPx())
                }
            }
        } else Text(symbol, Modifier.clearAndSetSemantics { }, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun MapCanvas(
    image: ImageBitmap, sourceCanvasWidth: Float, pins: List<MapAnchors.Anchor>, route: OfficialRoute?, state: WorldMapState,
    name: (String) -> String, modifier: Modifier,
) {
    val density = LocalDensity.current
    val padding = with(density) { 24.dp.toPx() }
    val accent = MaterialTheme.colorScheme.primary
    val occurrences = route?.stops?.groupBy { it.stageCode }.orEmpty()
    BoxWithConstraints(modifier.background(Color(0xFF24516F)).clipToBounds().pointerInput(state) {
        detectTransformGestures { centroid, pan, zoom, _ -> state.gesture(centroid, pan, zoom) }
    }) {
        val transform = state.configure(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), image.width.toFloat() / image.height, padding)
        Canvas(Modifier.fillMaxSize()) {
            if (transform.drawnWidth > 0f && transform.drawnHeight > 0f) {
                withTransform({
                    translate(transform.left, transform.top)
                    scale(transform.drawnWidth / image.width, transform.drawnHeight / image.height, pivot = Offset.Zero)
                }) { drawImage(image, filterQuality = FilterQuality.Medium) }
            }
            for ((from, to) in route?.stops?.zipWithNext().orEmpty()) {
                val a = pins.firstOrNull { it.code.removeSuffix("_00") == from.stageCode } ?: continue
                val b = pins.firstOrNull { it.code.removeSuffix("_00") == to.stageCode } ?: continue
                val start = transform.project(a.u, a.v)
                val end = transform.project(b.u, b.v)
                val difference = end - start
                val length = difference.getDistance()
                val startRadius = transform.sourcePixelsToScreen(a.artSize / 2f + 6f, sourceCanvasWidth)
                val endRadius = transform.sourcePixelsToScreen(b.artSize / 2f + 6f, sourceCanvasWidth)
                if (length <= startRadius + endRadius) continue
                val unit = difference / length
                drawLine(accent, start + unit * startRadius, end - unit * endRadius,
                    strokeWidth = transform.sourcePixelsToScreen(8f, sourceCanvasWidth), cap = StrokeCap.Round)
                val middle = start + difference * .6f
                val perpendicular = Offset(-unit.y, unit.x)
                val arrow = Path().apply {
                    val arrowSize = transform.sourcePixelsToScreen(28f, sourceCanvasWidth)
                    val tip = middle + unit * arrowSize
                    val left = middle - unit * arrowSize * .7f + perpendicular * arrowSize * .7f
                    val right = middle - unit * arrowSize * .7f - perpendicular * arrowSize * .7f
                    moveTo(tip.x, tip.y); lineTo(left.x, left.y); lineTo(right.x, right.y); close()
                }
                drawPath(arrow, accent)
            }
        }
        for (pin in pins) {
            val code = pin.code.removeSuffix("_00")
            val point = transform.project(pin.u, pin.v)
            val iconPixels = transform.sourcePixelsToScreen(pin.artSize, sourceCanvasWidth)
            val iconSize = with(density) { iconPixels.toDp() }
            val visits = occurrences[code].orEmpty()
            val selected = state.selectedCourseCode == code
            val description = if (visits.isEmpty()) name(code) else stringResource(R.string.map_pin_occurrences, name(code), visits.joinToString(stringResource(R.string.map_stop_separator)) { it.ordinal.toString() })
            Box(
                Modifier.offset { IntOffset((point.x - iconPixels / 2f).roundToInt(), (point.y - iconPixels / 2f).roundToInt()) }
                    .size(iconSize)
                    .then(if(selected || visits.isNotEmpty()) Modifier.border(iconSize * .045f, accent, CircleShape) else Modifier)
                    .clickable(role = Role.Button) { state.selectCourse(code) }
                    .semantics { contentDescription = description; this.selected = selected },
                contentAlignment = Alignment.Center,
            ) {
                StageArt.resolve(code)?.let { art -> Image(painterResource(art), contentDescription = null, modifier = Modifier.fillMaxSize()) }
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
