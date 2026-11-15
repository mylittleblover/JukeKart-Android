package com.mylittleblover.mkwplayer.ui.art

import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.withContext
import java.io.File

internal object PackArtwork {
    data class Loaded(
        val bitmap: ImageBitmap,
        val aspectRatio: Float,
    )

    private fun load(packDirectory: File, artworkName: String, maxEdge: Int): Loaded? {
        val file = File(packDirectory, "artwork/$artworkName")
        if (!file.isFile) return null
        return decodeArtwork(maxEdge) { BitmapFactory.decodeFile(file.path, it) }
    }

    @Composable
    fun rememberArtwork(packDirectory: File?, artworkNames: List<String>, maxEdge: Int): Loaded? {
        if (maxEdge == 0 || packDirectory == null || artworkNames.isEmpty()) return null
        return key(packDirectory, artworkNames, maxEdge) {
            val state by produceState<Loaded?>(null) {
                value = withContext(artworkDecoder) {
                    artworkNames.firstNotNullOfOrNull { load(packDirectory, it, maxEdge) }
                }
            }
            state
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
