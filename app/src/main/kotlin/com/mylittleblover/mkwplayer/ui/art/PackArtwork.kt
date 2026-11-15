package com.mylittleblover.mkwplayer.ui.art

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.withContext
import java.io.File

internal object PackArtwork {
    data class Loaded(
        val bitmap: ImageBitmap,
        val aspectRatio: Float,
    )

    private data class CachedEntry(
        val loaded: Loaded,
        val byteCount: Int,
    )

    private val maxCacheBytes = minOf(
        40 * 1024 * 1024,
        (Runtime.getRuntime().maxMemory() / 6L).toInt().coerceAtLeast(8 * 1024 * 1024),
    )

    private val bitmapCache = object : LruCache<String, CachedEntry>(maxCacheBytes) {
        override fun sizeOf(key: String, value: CachedEntry): Int = value.byteCount.coerceAtLeast(1)
    }

    private val resolvedNameCache = LruCache<String, String>(256)

    fun evictAll() {
        resolvedNameCache.evictAll()
        bitmapCache.evictAll()
    }

    private fun fileKey(packDirectory: File, artworkName: String, maxEdge: Int): String =
        "${packDirectory.path}\u0000$maxEdge\u0000$artworkName"

    private fun requestKey(packDirectory: File, artworkNames: List<String>, maxEdge: Int): String =
        "${packDirectory.path}\u0000$maxEdge\u0000${artworkNames.joinToString("\u0000")}"

    private fun lookupCached(packDirectory: File, artworkNames: List<String>, maxEdge: Int): Loaded? {
        val reqKey = requestKey(packDirectory, artworkNames, maxEdge)
        val resolved = resolvedNameCache.get(reqKey)
        if (resolved != null) {
            bitmapCache.get(fileKey(packDirectory, resolved, maxEdge))?.loaded?.let { return it }
        }
        for (name in artworkNames) {
            bitmapCache.get(fileKey(packDirectory, name, maxEdge))?.loaded?.let { hit ->
                resolvedNameCache.put(reqKey, name)
                return hit
            }
        }
        return null
    }

    private fun load(packDirectory: File, artworkName: String, maxEdge: Int): Loaded? {
        val fKey = fileKey(packDirectory, artworkName, maxEdge)
        bitmapCache.get(fKey)?.loaded?.let { return it }
        val file = File(packDirectory, "artwork/$artworkName")
        if (!file.isFile) return null
        val loaded = decodeArtwork(maxEdge) { BitmapFactory.decodeFile(file.path, it) } ?: return null
        val bytes = runCatching { loaded.bitmap.asAndroidBitmap().allocationByteCount }
            .getOrDefault(loaded.bitmap.width * loaded.bitmap.height * 4)
        bitmapCache.put(fKey, CachedEntry(loaded, bytes))
        return loaded
    }

    @Composable
    fun rememberArtwork(packDirectory: File?, artworkNames: List<String>, maxEdge: Int): Loaded? {
        if (maxEdge == 0 || packDirectory == null || artworkNames.isEmpty()) return null
        return key(packDirectory, artworkNames, maxEdge) {
            val immediate = lookupCached(packDirectory, artworkNames, maxEdge)
            if (immediate != null) {
                immediate
            } else {
                val state by produceState<Loaded?>(null, packDirectory, artworkNames, maxEdge) {
                    value = lookupCached(packDirectory, artworkNames, maxEdge) ?: withContext(artworkDecoder) {
                        val reqKey = requestKey(packDirectory, artworkNames, maxEdge)
                        artworkNames.firstNotNullOfOrNull { name ->
                            load(packDirectory, name, maxEdge)?.also {
                                resolvedNameCache.put(reqKey, name)
                            }
                        }
                    }
                }
                state
            }
        }
    }

    @Composable
    fun Thumbnail(packDirectory: File?, artworkNames: List<String>, modifier: Modifier, fallback: @Composable () -> Unit) {
        ArtworkSlot(modifier) { edge ->
            val artwork = rememberArtwork(packDirectory, artworkNames, edge)
            if (artwork != null) {
                FadingArtwork(artwork.bitmap, Modifier.fillMaxSize(), ContentScale.Crop)
            } else {
                fallback()
            }
        }
    }
}
