package com.mylittleblover.mkwplayer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mylittleblover.mkwplayer.R
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun tabTitle(tab: Tab): String = stringResource(when (tab) {
    Tab.HOME -> R.string.discovery_home
    Tab.MEDIA -> R.string.discovery_media
    Tab.WORLD_MAP -> R.string.nav_map
    Tab.PLAYLISTS -> R.string.discovery_playlists
})

@Composable
internal fun AppHeader(title: String, canBack: Boolean, showSettings: Boolean, onBack: () -> Unit, onSettings: () -> Unit) {
    val settingsLabel = stringResource(R.string.settings_title)
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            Row(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
                    .heightIn(min = 48.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (canBack) TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
                else if (LocalDensity.current.fontScale <= 1.3f) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("/", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (showSettings) {
                    IconButton(onClick = onSettings, modifier = Modifier.size(48.dp).semantics { contentDescription = settingsLabel }) {
                        val ink = LocalContentColor.current
                        Canvas(Modifier.size(24.dp).clearAndSetSemantics { }) {
                            drawCircle(ink, 5.dp.toPx(), style = Stroke(2.dp.toPx()))
                            for (index in 0 until 8) {
                                val radians = index * Math.PI / 4.0
                                val unit = Offset(cos(radians).toFloat(), sin(radians).toFloat())
                                drawLine(ink, center + unit * 7.dp.toPx(), center + unit * 10.dp.toPx(), 2.dp.toPx())
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/** Compact icons remain legible without requiring an additional icon font. */
@Composable
internal fun TabIcon(tab: Tab) {
    val ink = LocalContentColor.current
    Canvas(Modifier.size(24.dp).clearAndSetSemantics { }) {
        val w = size.width
        val line = 2.dp.toPx()
        when (tab) {
            Tab.HOME -> {
                drawLine(ink, Offset(w * .15f, w * .47f), Offset(w * .5f, w * .16f), line)
                drawLine(ink, Offset(w * .5f, w * .16f), Offset(w * .85f, w * .47f), line)
                drawLine(ink, Offset(w * .25f, w * .43f), Offset(w * .25f, w * .85f), line)
                drawLine(ink, Offset(w * .75f, w * .43f), Offset(w * .75f, w * .85f), line)
                drawLine(ink, Offset(w * .25f, w * .85f), Offset(w * .75f, w * .85f), line)
            }
            Tab.MEDIA -> {
                drawCircle(ink, w * .31f, style = Stroke(line))
                drawCircle(ink, w * .055f)
            }
            Tab.WORLD_MAP -> {
                drawCircle(ink, w * .34f, style = Stroke(line))
                drawLine(ink, Offset(w * .16f, w * .5f), Offset(w * .84f, w * .5f), line)
                drawLine(ink, Offset(w * .5f, w * .16f), Offset(w * .5f, w * .84f), line)
            }
            Tab.PLAYLISTS -> {
                for (i in 0..2) drawLine(ink, Offset(w * .17f, w * (.25f + i * .24f)), Offset(w * .83f, w * (.25f + i * .24f)), line)
            }
        }
    }
}
