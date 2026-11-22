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

    suspend fun load(
        context: Context,
        packDirectory: File? = null,
        anchors: MapAnchors.Catalog? = null,
        connections: StageConnections.Catalog? = null,
    ): ImageBitmap = withContext(Dispatchers.IO) {
        val candidates = listOfNotNull(
            packDirectory?.let { File(it, "artwork") },
            File(context.applicationContext.filesDir, "artwork"),
        )
        val extensions = listOf("webp", "png", "jpg", "jpeg")
        for (dir in candidates) {
            for (ext in extensions) {
                val file = File(dir, "world_map.$ext")
                if (file.isFile) {
                    val opts = BitmapFactory.Options().apply {
                        inMutable = true
                        inSampleSize = 2
                    }
                    val decoded = runCatching { BitmapFactory.decodeFile(file.path, opts) }.getOrNull()
                    if (decoded != null) {
                        return@withContext featherWorldMapEdges(decoded).asImageBitmap()
                    }
                }
            }
        }
        featherWorldMapEdges(renderProceduralAtlas(anchors, connections)).asImageBitmap()
    }

    suspend fun loadRouteSprites(@Suppress("UNUSED_PARAMETER") context: Context): RouteSprites? = null

    suspend fun loadHighlightMask(
        @Suppress("UNUSED_PARAMETER") context: Context,
        @Suppress("UNUSED_PARAMETER") maskResId: Int,
    ): ImageBitmap? = null

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
