package com.mylittleblover.mkwplayer.ui.art

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.mylittleblover.mkwplayer.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reuse the decoded map across Home and the expanded player to avoid a blank frame on navigation. */
internal object MapArtworkCache {
    @Volatile private var image: ImageBitmap? = null
    fun cached(): ImageBitmap? = image
    suspend fun load(context: Context): ImageBitmap? = withContext(Dispatchers.IO) {
        image ?: synchronized(this@MapArtworkCache) {
            image ?: BitmapFactory.decodeResource(context.applicationContext.resources, R.drawable.world_map)
                ?.asImageBitmap()?.also { image = it }
        }
    }
}
