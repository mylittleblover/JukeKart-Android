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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mylittleblover.mkwplayer.R
import com.mylittleblover.mkwplayer.library.MapAnchors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The supplied landscape covers are pending; this is an explicitly temporary map crop. */
@Composable
internal fun CoursePlaceholder(code: String?, modifier: Modifier = Modifier, showIcon: Boolean = true) {
    val context = LocalContext.current
    val anchor = remember(context, code) {
        runCatching {
            MapAnchors.parse(context.assets.open("map_anchors.json").bufferedReader().use { it.readText() })?.stageTolerant(code.orEmpty())
        }.getOrNull()
    }
    val map by produceState(MapArtworkCache.cached(), context) {
        value = MapArtworkCache.load(context)
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (map != null && anchor != null) {
            val image = map!!
            Canvas(Modifier.fillMaxSize()) {
                val width = (image.width * .24f).toInt().coerceAtLeast(1)
                val height = (image.height * .18f).toInt().coerceAtLeast(1)
                val left = (anchor.u * image.width - width / 2).toInt().coerceIn(0, image.width - width)
                val top = (anchor.v * image.height - height / 2).toInt().coerceIn(0, image.height - height)
                drawImage(image, srcOffset = IntOffset(left, top), srcSize = IntSize(width, height),
                    dstSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)))
            }
        }
        if (showIcon) code?.let { StageArt.resolve(it) }?.let { Image(painterResource(it), null, Modifier.size(120.dp)) }
    }
}
