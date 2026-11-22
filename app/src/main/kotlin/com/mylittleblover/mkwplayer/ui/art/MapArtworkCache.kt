package com.mylittleblover.mkwplayer.ui.art

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.mylittleblover.mkwplayer.library.MapAnchors
import com.mylittleblover.mkwplayer.library.StageConnections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Loads an optional user-supplied `world_map.*` from `.mkwpack/artwork/` (or `files/artwork/`),
 * falling back cleanly to a deterministic procedural JukeKart Circuit Atlas aligned to [MapAnchors].
 */
internal object MapArtworkCache {
    data class RouteSprites(
        val officialRoad: ImageBitmap,
        val neighbourRoad: ImageBitmap,
        val draftRoad: ImageBitmap,
        val arrowSharp: ImageBitmap,
        val arrowGlow: ImageBitmap,
    )

    private const val ATLAS_WIDTH = 1428
    private const val ATLAS_HEIGHT = 1200

    private const val WORLD_MAP_MAX_EDGE_PX = 3000

    private fun artworkDirectories(context: Context, packDirectory: File?): List<File> = listOfNotNull(
        packDirectory?.let { File(it, "artwork") },
        File(context.applicationContext.filesDir, "artwork"),
    )

    private fun decodePackArtworkFile(
        dirs: List<File>,
        baseName: String,
        extensions: List<String> = listOf("png", "webp", "jpg", "jpeg"),
        mutable: Boolean = false,
        maxEdgePx: Int? = null,
    ): Bitmap? {
        for (dir in dirs) {
            for (ext in extensions) {
                val file = File(dir, "$baseName.$ext")
                if (!file.isFile) continue
                val opts = BitmapFactory.Options().apply {
                    inScaled = false
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                if (maxEdgePx != null) {
                    opts.inJustDecodeBounds = true
                    BitmapFactory.decodeFile(file.path, opts)
                    val longest = maxOf(opts.outWidth, opts.outHeight)
                    if (longest <= 0) continue
                    var sample = 1
                    while (longest / sample > maxEdgePx) sample *= 2
                    opts.inJustDecodeBounds = false
                    opts.inSampleSize = sample
                }
                opts.inMutable = mutable
                val decoded = runCatching { BitmapFactory.decodeFile(file.path, opts) }.getOrNull()
                if (decoded != null) return decoded
            }
        }
        return null
    }

    suspend fun load(
        context: Context,
        packDirectory: File? = null,
        anchors: MapAnchors.Catalog? = null,
        connections: StageConnections.Catalog? = null,
    ): ImageBitmap = withContext(Dispatchers.IO) {
        val decoded = decodePackArtworkFile(
            dirs = artworkDirectories(context, packDirectory),
            baseName = "world_map",
            extensions = listOf("webp", "png", "jpg", "jpeg"),
            mutable = true,
            maxEdgePx = WORLD_MAP_MAX_EDGE_PX,
        )
        if (decoded != null) {
            return@withContext featherWorldMapEdges(decoded).asImageBitmap()
        }
        featherWorldMapEdges(renderProceduralAtlas(anchors, connections)).asImageBitmap()
    }

    suspend fun loadRouteSprites(
        context: Context,
        packDirectory: File? = null,
    ): RouteSprites? = withContext(Dispatchers.IO) {
        val dirs = artworkDirectories(context, packDirectory)
        val spriteExts = listOf("png", "webp")
        val wavy = decodePackArtworkFile(dirs, "map_route_road_wavy", spriteExts) ?: return@withContext null
        val straight = decodePackArtworkFile(dirs, "map_route_road_straight", spriteExts) ?: run {
            wavy.recycle()
            return@withContext null
        }
        val arrow00 = decodePackArtworkFile(dirs, "map_route_arrow", spriteExts) ?: run {
            wavy.recycle()
            straight.recycle()
            return@withContext null
        }
        val arrow01 = decodePackArtworkFile(dirs, "map_route_arrow_glow", spriteExts) ?: run {
            wavy.recycle()
            straight.recycle()
            arrow00.recycle()
            return@withContext null
        }
        try {
            RouteSprites(
                officialRoad = shaderRoadToImageBitmap(wavy, 255, 214, 72),
                neighbourRoad = shaderRoadToImageBitmap(straight, 120, 224, 255),
                draftRoad = shaderRoadToImageBitmap(wavy, 255, 118, 178),
                arrowSharp = lumaMaskToArrowBitmap(arrow00, 225, 248, 255),
                arrowGlow = lumaMaskToArrowBitmap(arrow01, 255, 232, 128),
            )
        } finally {
            wavy.recycle()
            straight.recycle()
            arrow00.recycle()
            arrow01.recycle()
        }
    }

    suspend fun loadHighlightMask(
        context: Context,
        packDirectory: File? = null,
        routeId: String,
    ): ImageBitmap? = withContext(Dispatchers.IO) {
        val baseName = CupArt.routeHighlightMaskName(routeId) ?: return@withContext null
        val raw = decodePackArtworkFile(
            dirs = artworkDirectories(context, packDirectory),
            baseName = baseName,
            extensions = listOf("png", "webp"),
        ) ?: return@withContext null
        try {
            convertHighlightMask(raw)
        } finally {
            raw.recycle()
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

    private fun renderProceduralAtlas(
        anchors: MapAnchors.Catalog?,
        connections: StageConnections.Catalog?,
    ): Bitmap {
        val w = ATLAS_WIDTH
        val h = ATLAS_HEIGHT
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val seaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, w.toFloat(), h.toFloat(),
                intArrayOf(0xFF0A172B.toInt(), 0xFF102847.toInt(), 0xFF0D203B.toInt()),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), seaPaint)

        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                w * 0.52f, h * 0.48f, w * 0.62f,
                0x38204B7A, 0x000A172B, Shader.TileMode.CLAMP,
            )
        }
        canvas.drawCircle(w * 0.52f, h * 0.48f, w * 0.62f, ringPaint)

        // Longitude / latitude graticule lines for navigational clarity.
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x1A6BA3D6
            strokeWidth = 1.5f
            style = Paint.Style.STROKE
        }
        for (i in 1..11) {
            val x = w * (i / 12f)
            canvas.drawLine(x, 0f, x, h.toFloat(), gridPaint)
        }
        for (j in 1..9) {
            val y = h * (j / 10f)
            canvas.drawLine(0f, y, w.toFloat(), y, gridPaint)
        }

        val stages = anchors?.stages.orEmpty()
        if (stages.isNotEmpty()) {
            // Soft coastal shelf under stage clusters.
            val shelfPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x3D1C4266 }
            val landPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xAA1F3B4D.toInt() }
            val peakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x802C5264.toInt() }
            val contourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x445283A8
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }

            // Faint under-layer dashed/arc network matching stage topology.
            if (connections != null) {
                val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0x30588BAE
                    style = Paint.Style.STROKE
                    strokeWidth = 3f
                    strokeCap = Paint.Cap.ROUND
                }
                val byCode = stages.associateBy { it.code.removeSuffix("_00") }
                for (a in stages) {
                    val codeA = a.code.removeSuffix("_00")
                    for (edge in connections.outgoing(codeA)) {
                        val b = byCode[edge.to.removeSuffix("_00")] ?: continue
                        canvas.drawLine(a.u * w, a.v * h, b.u * w, b.v * h, edgePaint)
                    }
                }
            }

            fun drawIslandBlob(cx: Float, cy: Float, rx: Float, ry: Float, seed: Int, paint: Paint, stroke: Paint? = null) {
                val path = Path()
                val pts = 18
                for (k in 0 until pts) {
                    val ang = (k.toDouble() / pts) * Math.PI * 2.0
                    val wobble = 0.84 + 0.16 * sin(ang * 3.0 + seed * 1.7) + 0.08 * cos(ang * 5.0 - seed)
                    val px = cx + (cos(ang) * rx * wobble).toFloat()
                    val py = cy + (sin(ang) * ry * wobble).toFloat()
                    if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                canvas.drawPath(path, paint)
                if (stroke != null) canvas.drawPath(path, stroke)
            }

            stages.forEachIndexed { idx, st ->
                val cx = st.u * w
                val cy = st.v * h
                drawIslandBlob(cx, cy, 118f, 96f, idx + 3, shelfPaint)
            }
            stages.forEachIndexed { idx, st ->
                val cx = st.u * w
                val cy = st.v * h
                drawIslandBlob(cx, cy, 78f, 64f, idx + 7, landPaint, contourPaint)
            }
            stages.forEachIndexed { idx, st ->
                val cx = st.u * w
                val cy = st.v * h
                drawIslandBlob(cx, cy, 42f, 34f, idx + 11, peakPaint)
            }
        }
        return bmp
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
