package com.mylittleblover.mkwplayer.ui.art

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.mylittleblover.mkwplayer.library.MapAnchors
import kotlin.math.abs
import kotlin.math.sin

/** Process-wide parsed `map_anchors.json` cache so composing course placeholders never performs asset I/O on Main. */
internal object MapAnchorsCache {
    @Volatile
    private var cached: MapAnchors.Catalog? = null

    fun peek(): MapAnchors.Catalog? = cached

    fun seed(catalog: MapAnchors.Catalog?) {
        if (catalog != null) cached = catalog
    }

    fun load(context: android.content.Context): MapAnchors.Catalog? {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: runCatching {
                context.applicationContext.assets.open("map_anchors.json").bufferedReader().use {
                    MapAnchors.parse(it.readText())
                }
            }.getOrNull()?.also { cached = it }
        }
    }
}

private val PLACEHOLDER_PALETTES = listOf(
    Pair(Color(0xFF1E293B), Color(0xFF0F172A)),
    Pair(Color(0xFF7C2D12), Color(0xFF431407)),
    Pair(Color(0xFF065F46), Color(0xFF022C22)),
    Pair(Color(0xFF1D4ED8), Color(0xFF172554)),
    Pair(Color(0xFF6D28D9), Color(0xFF2E1065)),
    Pair(Color(0xFF9F1239), Color(0xFF4C0519)),
    Pair(Color(0xFF0F766E), Color(0xFF042F2E)),
)

/** Original procedural circuit card backdrop rendered when no custom pack cover is present. */
@Composable
internal fun CoursePlaceholder(code: String?, modifier: Modifier = Modifier, showIcon: Boolean = true) {
    ArtworkSlot(modifier) { edge ->
        CoursePlaceholderContent(code = code, edge = edge, showIcon = showIcon)
    }
}

@Composable
internal fun CoursePlaceholderContent(code: String?, edge: Int, showIcon: Boolean = true) {
    val cleanCode = code.orEmpty().trim()
    val seed = remember(cleanCode) { abs(cleanCode.hashCode()) }
    val (cTop, cBottom) = remember(seed) { PLACEHOLDER_PALETTES[seed % PLACEHOLDER_PALETTES.size] }
    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(cTop, cBottom),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            ),
        )
        val gridAlpha = 0.08f
        val cols = 6
        for (i in 1 until cols) {
            val x = size.width * i / cols
            drawLine(Color.White.copy(alpha = gridAlpha), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        }
        val phase = (seed % 360) * 0.01745f
        for (band in 0..2) {
            val path = Path()
            val yBase = size.height * (0.28f + band * 0.22f)
            val amp = size.height * (0.06f + band * 0.02f)
            val steps = 16
            for (s in 0..steps) {
                val t = s.toFloat() / steps
                val px = size.width * t
                val py = yBase + sin((t * 4.5f + phase + band).toDouble()).toFloat() * amp
                if (s == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(
                path = path,
                color = Color.White.copy(alpha = 0.14f - band * 0.03f),
                style = Stroke(width = 2.5f),
            )
        }
    }
    if (showIcon) {
        code?.let { StageArt.resolve(it) }?.let { iconRes ->
            val iconLookup = rememberResourceArtworkState(iconRes, edge)
            iconLookup.loaded?.let {
                FadingArtwork(it.bitmap, Modifier.size(120.dp), ContentScale.Fit, animate = !iconLookup.fromCache)
            }
        }
    }
}
