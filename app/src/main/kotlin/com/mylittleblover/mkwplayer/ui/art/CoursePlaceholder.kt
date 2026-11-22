package com.mylittleblover.mkwplayer.ui.art

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mylittleblover.mkwplayer.R
import com.mylittleblover.mkwplayer.library.MapAnchors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

/** The supplied landscape covers are pending; this is an explicitly temporary map crop. */
@Composable
internal fun CoursePlaceholder(code: String?, modifier: Modifier = Modifier, showIcon: Boolean = true) {
    ArtworkSlot(modifier) { edge ->
        CoursePlaceholderContent(code = code, edge = edge, showIcon = showIcon)
    }
}

@Composable
internal fun CoursePlaceholderContent(code: String?, edge: Int, showIcon: Boolean = true) {
    val context = LocalContext.current
    val initialCatalog = remember { MapAnchorsCache.peek() }
    val anchorsCatalog by produceState(initialValue = initialCatalog, key1 = context) {
        if (value == null) {
            value = withContext(Dispatchers.IO) { MapAnchorsCache.load(context) }
        }
    }
    val anchor = remember(anchorsCatalog, code) {
        anchorsCatalog?.stageTolerant(code.orEmpty())
    }
    val lookup = rememberResourceArtworkState(R.drawable.world_map.takeIf { anchor != null }, if (edge == 0) 0 else 960)
    val image = lookup.loaded?.bitmap
    if (image != null && anchor != null) {
        Canvas(Modifier.fillMaxSize().then(if (!lookup.fromCache) artworkFade(image) else Modifier)) {
            val width = (image.width * .24f).toInt().coerceAtLeast(1)
            val height = (image.height * .18f).toInt().coerceAtLeast(1)
            val left = (anchor.u * image.width - width / 2).toInt().coerceIn(0, image.width - width)
            val top = (anchor.v * image.height - height / 2).toInt().coerceIn(0, image.height - height)
            drawImage(
                image,
                srcOffset = IntOffset(left, top),
                srcSize = IntSize(width, height),
                dstSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)),
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
