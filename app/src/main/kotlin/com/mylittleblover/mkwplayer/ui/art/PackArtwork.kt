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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

internal object PackArtwork {
    val activePackDirectory = MutableStateFlow<File?>(null)
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
    private val artworkIndexCache = ConcurrentHashMap<String, Set<String>>()

    fun evictAll() {
        activePackDirectory.value = null
        resolvedNameCache.evictAll()
        bitmapCache.evictAll()
        artworkIndexCache.clear()
        evictResourceArtworkCache()
    }

    /** Indexes available artwork filenames once on I/O so missing files skip unnecessary disk reads & fallback flashes. */
    fun warmArtworkIndex(packDirectory: File?): Set<String>? {
        activePackDirectory.value = packDirectory
        packDirectory ?: return null
        val key = packDirectory.path
        artworkIndexCache[key]?.let { return it }
        val artDir = File(packDirectory, "artwork")
        val names = if (artDir.isDirectory) artDir.list()?.toHashSet().orEmpty() else emptySet()
        artworkIndexCache[key] = names
        return names
    }

    fun hasAnyCandidate(packDirectory: File?, artworkNames: List<String>): Boolean {
        if (packDirectory == null || artworkNames.isEmpty()) return false
        val indexed = artworkIndexCache[packDirectory.path] ?: return true
        return artworkNames.any { it in indexed }
    }

    fun warmThumbnails(packDirectory: File?, artworkNames: List<String>, maxEdge: Int = 512) {
        packDirectory ?: return
        warmArtworkIndex(packDirectory)
        artworkNames.forEach { name ->
            load(packDirectory, name, maxEdge)
        }
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
        val indexed = artworkIndexCache[packDirectory.path]
        if (indexed != null && artworkName !in indexed) return null
        val file = File(packDirectory, "artwork/$artworkName")
        if (!file.isFile) return null
        val loaded = decodeArtwork(maxEdge) { BitmapFactory.decodeFile(file.path, it) } ?: return null
        val bytes = runCatching { loaded.bitmap.asAndroidBitmap().allocationByteCount }
            .getOrDefault(loaded.bitmap.width * loaded.bitmap.height * 4)
        bitmapCache.put(fKey, CachedEntry(loaded, bytes))
        return loaded
    }

    @Composable
    fun rememberArtwork(packDirectory: File?, artworkNames: List<String>, maxEdge: Int): Loaded? =
        rememberArtworkState(packDirectory, artworkNames, maxEdge).loaded

    @Composable
    internal fun rememberArtworkState(packDirectory: File?, artworkNames: List<String>, maxEdge: Int): ArtworkLookup {
        if (maxEdge == 0 || packDirectory == null || artworkNames.isEmpty()) {
            return ArtworkLookup(null, fromCache = false)
        }
        return key(packDirectory, artworkNames, maxEdge) {
            val immediate = lookupCached(packDirectory, artworkNames, maxEdge)
            if (immediate != null) {
                ArtworkLookup(immediate, fromCache = true)
            } else if (!hasAnyCandidate(packDirectory, artworkNames)) {
                ArtworkLookup(null, fromCache = true)
            } else {
                val state by produceState<Loaded?>(null, packDirectory, artworkNames, maxEdge) {
                    value = lookupCached(packDirectory, artworkNames, maxEdge) ?: withContext(artworkDecoder) {
                        warmArtworkIndex(packDirectory)
                        val reqKey = requestKey(packDirectory, artworkNames, maxEdge)
                        artworkNames.firstNotNullOfOrNull { name ->
                            load(packDirectory, name, maxEdge)?.also {
                                resolvedNameCache.put(reqKey, name)
                            }
                        }
                    }
                }
                ArtworkLookup(state, fromCache = false)
            }
        }
    }

    @Composable
    fun Thumbnail(packDirectory: File?, artworkNames: List<String>, modifier: Modifier, fallback: @Composable () -> Unit) {
        ArtworkSlot(modifier) { edge ->
            val lookup = rememberArtworkState(packDirectory, artworkNames, edge)
            if (lookup.loaded != null) {
                FadingArtwork(lookup.loaded.bitmap, Modifier.fillMaxSize(), ContentScale.Crop, animate = !lookup.fromCache)
            } else {
                fallback()
            }
        }
    }
}
