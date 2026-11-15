package com.mylittleblover.mkwplayer.ui.art

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal val artworkDecoder = Dispatchers.IO.limitedParallelism(3)

private data class CachedResourceArtwork(
    val loaded: PackArtwork.Loaded,
    val byteCount: Int,
)

private val resourceArtworkCache = object : LruCache<String, CachedResourceArtwork>(12 * 1024 * 1024) {
    override fun sizeOf(key: String, value: CachedResourceArtwork): Int = value.byteCount.coerceAtLeast(1)
}

/** Owns the decode only while the laid-out slot intersects the clipped viewport. */
@Composable
internal fun ArtworkSlot(modifier: Modifier, content: @Composable (maxEdge: Int) -> Unit) {
    var maxEdge by remember { mutableStateOf(0) }
    Box(modifier.onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInWindow()
        val edge = maxOf(coordinates.size.width, coordinates.size.height)
        maxEdge = if (bounds.width > 0f && bounds.height > 0f && edge > 0) {
            // Round to a bucket so a small layout change does not restart a decode, and keep a
            // minimum bucket high enough that 16:9 covers stay sharp inside square/small thumbnails.
            maxOf(384, (edge + 255) / 256 * 256)
        } else 0
    }, contentAlignment = Alignment.Center) {
        content(maxEdge)
    }
}

@Composable
internal fun rememberResourceArtwork(resourceId: Int?, maxEdge: Int): PackArtwork.Loaded? {
    val resources = LocalContext.current.resources
    if (resourceId == null || maxEdge == 0) return null
    return key(resources, resourceId, maxEdge) {
        val cacheKey = "$resourceId@$maxEdge"
        val immediate = resourceArtworkCache.get(cacheKey)?.loaded
        if (immediate != null) {
            immediate
        } else {
            val image by produceState<PackArtwork.Loaded?>(null, resourceId, maxEdge) {
                value = resourceArtworkCache.get(cacheKey)?.loaded ?: withContext(artworkDecoder) {
                    decodeArtwork(maxEdge) { BitmapFactory.decodeResource(resources, resourceId, it) }?.also { loaded ->
                        val bytes = runCatching { loaded.bitmap.asAndroidBitmap().allocationByteCount }
                            .getOrDefault(loaded.bitmap.width * loaded.bitmap.height * 4)
                        resourceArtworkCache.put(cacheKey, CachedResourceArtwork(loaded, bytes))
                    }
                }
            }
            image
        }
    }
}

/** Keep the layout stable after releasing the off-screen bitmap. */
@Composable
internal fun rememberArtworkRatio(id: Any?, artwork: PackArtwork.Loaded?, fallback: Float): Float {
    var ratio by remember(id, fallback) { mutableStateOf(fallback) }
    val actual = artwork?.aspectRatio
    LaunchedEffect(actual) {
        if (actual != null) ratio = actual
    }
    return ratio
}

internal fun decodeArtwork(maxEdge: Int, decode: (BitmapFactory.Options) -> Bitmap?): PackArtwork.Loaded? {
    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
        inScaled = false
    }
    decode(options)
    val width = options.outWidth
    val height = options.outHeight
    if (width <= 0 || height <= 0) return null
    val shortest = minOf(width, height)
    var sample = 1
    while (shortest / sample / 2 >= maxEdge) sample *= 2
    options.inJustDecodeBounds = false
    options.inSampleSize = sample
    val bitmap = decode(options) ?: return null
    return PackArtwork.Loaded(bitmap.asImageBitmap(), width.toFloat() / height)
}

@Composable
internal fun artworkFade(bitmap: ImageBitmap): Modifier {
    val alpha = remember(bitmap) { Animatable(0f) }
    LaunchedEffect(bitmap) { alpha.animateTo(1f, tween(180)) }
    return Modifier.graphicsLayer { this.alpha = alpha.value }
}

@Composable
internal fun FadingArtwork(
    bitmap: ImageBitmap,
    modifier: Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    Image(
        bitmap,
        null,
        modifier.then(artworkFade(bitmap)),
        contentScale = contentScale,
        filterQuality = FilterQuality.High,
    )
}

@Composable
internal fun ResourceArtwork(
    resourceId: Int,
    modifier: Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    ArtworkSlot(modifier) { edge ->
        rememberResourceArtwork(resourceId, edge)?.let {
            FadingArtwork(it.bitmap, Modifier.fillMaxSize(), contentScale)
        }
    }
}
