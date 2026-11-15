package com.mylittleblover.mkwplayer.ui.art

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.mylittleblover.mkwplayer.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/** Decodes map artwork on demand; the map screen owns the resulting bitmaps. */
internal object MapArtworkCache {
    /** Decoded `MapLine_00` / `VoteLine_00` RGBA sprites ready for Compose Canvas rendering. */
    data class RouteSprites(
        val officialRoad: ImageBitmap,
        val neighbourRoad: ImageBitmap,
        val draftRoad: ImageBitmap,
        val arrowSharp: ImageBitmap,
        val arrowGlow: ImageBitmap,
    )

    suspend fun load(context: Context): ImageBitmap? = withContext(Dispatchers.IO) {
        val opts = BitmapFactory.Options().apply { inMutable = true }
        val raw = BitmapFactory.decodeResource(context.applicationContext.resources, R.drawable.world_map, opts)
            ?: return@withContext null
        featherWorldMapEdges(raw).asImageBitmap()
    }

    suspend fun loadRouteSprites(context: Context): RouteSprites? = withContext(Dispatchers.IO) {
        run {
                val res = context.applicationContext.resources
                val opts = BitmapFactory.Options().apply { inScaled = false }
                val wavy = BitmapFactory.decodeResource(res, R.drawable.map_route_road_wavy, opts) ?: return@run null
                val straight = BitmapFactory.decodeResource(res, R.drawable.map_route_road_straight, opts) ?: return@run null
                val arrow00 = BitmapFactory.decodeResource(res, R.drawable.map_route_arrow, opts) ?: return@run null
                val arrow01 = BitmapFactory.decodeResource(res, R.drawable.map_route_arrow_glow, opts) ?: return@run null
                RouteSprites(
                    officialRoad = shaderRoadToImageBitmap(wavy, 255, 214, 72),
                    neighbourRoad = shaderRoadToImageBitmap(straight, 120, 224, 255),
                    draftRoad = shaderRoadToImageBitmap(wavy, 255, 118, 178),
                    arrowSharp = lumaMaskToArrowBitmap(arrow00, 225, 248, 255),
                    arrowGlow = lumaMaskToArrowBitmap(arrow01, 255, 232, 128),
                ).also {
                    wavy.recycle()
                    straight.recycle()
                    arrow00.recycle()
                    arrow01.recycle()
                }
        }
    }

    suspend fun loadHighlightMask(context: Context, maskResId: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        run {
                val opts = BitmapFactory.Options().apply { inScaled = false }
                val raw = BitmapFactory.decodeResource(context.applicationContext.resources, maskResId, opts)
                    ?: return@run null
                val converted = convertHighlightMask(raw)
                raw.recycle()
                converted
        }
    }

    /**
     * Converts Nintendo `MapLine_00` packed shader input (`A` silhouette + `R` inner road & dashed divider)
     * into an unpremultiplied RGBA bitmap with `0.78` dark casing and tinted highway body.
     */
    private fun shaderRoadToImageBitmap(src: Bitmap, tintR: Int, tintG: Int, tintB: Int): ImageBitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val px = pixels[i]
            val alphaMask = (px ushr 24) and 0xFF
            if (alphaMask == 0) {
                pixels[i] = 0
                continue
            }
            val r = (px ushr 16) and 0xFF
            val fill = ((r - 28f) / 185f).coerceIn(0f, 1f)
            val aNorm = alphaMask / 255f
            val fgA = aNorm * fill
            val caseA = aNorm * 0.78f
            val outAf = fgA + caseA * (1f - fgA)
            if (outAf <= 1e-4f) {
                pixels[i] = 0
                continue
            }
            val caseKeep = caseA * (1f - fgA)
            val outR = ((tintR * fgA + 10f * caseKeep) / outAf).roundToInt().coerceIn(0, 255)
            val outG = ((tintG * fgA + 14f * caseKeep) / outAf).roundToInt().coerceIn(0, 255)
            val outB = ((tintB * fgA + 24f * caseKeep) / outAf).roundToInt().coerceIn(0, 255)
            val outA = (outAf * 255f).roundToInt().coerceIn(0, 255)
            pixels[i] = (outA shl 24) or (outR shl 16) or (outG shl 8) or outB
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    /** Converts `VoteLine_00` black-background white-chevron `_s` sprite into transparent tinted ARGB. */
    private fun lumaMaskToArrowBitmap(src: Bitmap, tintR: Int, tintG: Int, tintB: Int): ImageBitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val px = pixels[i]
            val r = (px ushr 16) and 0xFF
            val g = (px ushr 8) and 0xFF
            val b = px and 0xFF
            val luma = max(r, max(g, b))
            pixels[i] = (luma shl 24) or (tintR shl 16) or (tintG shl 8) or tintB
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    /**
     * Converts a `500x500` square `Map_Light_*_s` mask (`0` = highlighted corridor, `255` = outside)
     * into a cutout alpha mask (`255` = clear the dim veil, `0` = keep full dim veil) cropped
     * vertically (`40..460`, i.e., `2400 / 2857`) to match `world_map` (`2857x2400`).
     * A narrow outer feather ensures even a bright highlight halo never cuts abruptly at the map edge.
     */
    private fun convertHighlightMask(src: Bitmap): ImageBitmap {
        val w = src.width
        val fullH = src.height
        val cropTop = ((fullH * (2857f - 2400f)) / (2f * 2857f)).roundToInt().coerceIn(0, fullH - 1)
        val cropH = ((fullH * 2400f) / 2857f).roundToInt().coerceIn(1, fullH - cropTop)
        val pixels = IntArray(w * cropH)
        src.getPixels(pixels, 0, w, 0, cropTop, w, cropH)
        val edgePx = 6f
        for (y in 0 until cropH) {
            val edgeY = smoothEdge(y.toFloat(), cropH.toFloat(), edgePx)
            val rowOffset = y * w
            for (x in 0 until w) {
                val idx = rowOffset + x
                val luma = (pixels[idx] ushr 16) and 0xFF
                val cutout = (255 - luma) / 255f
                val edgeX = smoothEdge(x.toFloat(), w.toFloat(), edgePx)
                val outA = (cutout * edgeX * edgeY * 255f).roundToInt().coerceIn(0, 255)
                pixels[idx] = outA shl 24
            }
        }
        return Bitmap.createBitmap(pixels, w, cropH, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    /** Softens the outer perimeter of `world_map` so out-of-bounds panning/zooming melts into the sea. */
    private fun featherWorldMapEdges(src: Bitmap): Bitmap {
        val mutable = if (src.isMutable && src.config == Bitmap.Config.ARGB_8888) src else {
            src.copy(Bitmap.Config.ARGB_8888, true).also { src.recycle() }
        }
        val w = mutable.width
        val h = mutable.height
        val featherPx = minOf(w, h) * 0.04f
        val row = IntArray(w)
        val rowEdge = FloatArray(w) { x -> smoothEdge(x.toFloat(), w.toFloat(), featherPx) }
        for (y in 0 until h) {
            val ey = smoothEdge(y.toFloat(), h.toFloat(), featherPx)
            if (ey < 1f) {
                mutable.getPixels(row, 0, w, 0, y, w, 1)
                for (x in 0 until w) {
                    val a = ((rowEdge[x] * ey) * 255f).roundToInt().coerceIn(0, 255)
                    row[x] = (a shl 24) or (row[x] and 0x00FFFFFF)
                }
                mutable.setPixels(row, 0, w, 0, y, w, 1)
            } else {
                val margin = featherPx.toInt().coerceAtMost(w / 2)
                if (margin > 0) {
                    mutable.getPixels(row, 0, margin, 0, y, margin, 1)
                    for (x in 0 until margin) {
                        val a = (rowEdge[x] * 255f).roundToInt().coerceIn(0, 255)
                        row[x] = (a shl 24) or (row[x] and 0x00FFFFFF)
                    }
                    mutable.setPixels(row, 0, margin, 0, y, margin, 1)
                    val rightStart = w - margin
                    mutable.getPixels(row, 0, margin, rightStart, y, margin, 1)
                    for (i in 0 until margin) {
                        val a = (rowEdge[rightStart + i] * 255f).roundToInt().coerceIn(0, 255)
                        row[i] = (a shl 24) or (row[i] and 0x00FFFFFF)
                    }
                    mutable.setPixels(row, 0, margin, rightStart, y, margin, 1)
                }
            }
        }
        return mutable
    }

    private fun smoothEdge(pos: Float, length: Float, margin: Float): Float {
        if (margin <= 0f) return 1f
        val d = minOf(pos + 0.5f, length - pos - 0.5f).coerceIn(0f, margin)
        val t = d / margin
        return t * t * (3f - 2f * t)
    }
}
