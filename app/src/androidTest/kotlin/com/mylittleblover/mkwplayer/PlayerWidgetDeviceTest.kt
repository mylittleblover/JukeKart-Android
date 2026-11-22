package com.mylittleblover.mkwplayer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mylittleblover.mkwplayer.player.PlaybackSession
import com.mylittleblover.mkwplayer.widget.PlayerWidgetUpdater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class PlayerWidgetDeviceTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun widgetRendersIdleAndPlayingStatesWithJukeKartBranding() {
        val outDir = context.getExternalFilesDir(null) ?: context.filesDir

        // 1. Idle state: JukeKart title, hidden status eyebrow, idle subtitle, no prev/next
        val idleViews = PlayerWidgetUpdater.buildViews(
            context = context,
            state = PlaybackSession.State(),
            artwork = null,
            canSkip = false,
        )
        val idleRoot = renderAndSave(idleViews, File(outDir, "widget_idle_preview.png"))
        assertEquals(context.getString(R.string.app_name), idleRoot.findViewById<TextView>(R.id.widget_title).text.toString())
        assertEquals(context.getString(R.string.tile_idle), idleRoot.findViewById<TextView>(R.id.widget_subtitle).text.toString())
        assertEquals(View.GONE, idleRoot.findViewById<View>(R.id.widget_eyebrow).visibility)
        assertEquals(View.GONE, idleRoot.findViewById<View>(R.id.widget_status_dot).visibility)
        assertEquals(View.GONE, idleRoot.findViewById<View>(R.id.widget_btn_prev).visibility)
        assertEquals(View.GONE, idleRoot.findViewById<View>(R.id.widget_btn_next).visibility)

        // 2. Playing linear track state with rounded cover artwork and skip controls
        val sampleArt = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).apply {
            val c = Canvas(this)
            c.drawColor(Color.parseColor("#C52D49"))
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            c.drawCircle(100f, 100f, 48f, p)
        }
        val rounded = PlayerWidgetUpdater.roundSquareBitmap(sampleArt, targetPx = 162, radiusPx = 40f)
        val activeViews = PlayerWidgetUpdater.buildViews(
            context = context,
            state = PlaybackSession.State(
                loaded = true,
                playing = true,
                linear = true,
                title = "Ground BGM (Latin)",
                subtitle = "1985 · Super Mario Bros.",
            ),
            artwork = rounded,
            canSkip = true,
        )
        val activeRoot = renderAndSave(activeViews, File(outDir, "widget_playing_preview.png"))
        assertEquals("Ground BGM (Latin)", activeRoot.findViewById<TextView>(R.id.widget_title).text.toString())
        assertEquals("1985 · Super Mario Bros.", activeRoot.findViewById<TextView>(R.id.widget_subtitle).text.toString())
        assertEquals(context.getString(R.string.tile_playing), activeRoot.findViewById<TextView>(R.id.widget_eyebrow).text.toString())
        assertEquals(View.VISIBLE, activeRoot.findViewById<View>(R.id.widget_eyebrow).visibility)
        assertEquals(View.VISIBLE, activeRoot.findViewById<View>(R.id.widget_status_dot).visibility)
        assertEquals(View.VISIBLE, activeRoot.findViewById<View>(R.id.widget_artwork).visibility)
        assertEquals(View.VISIBLE, activeRoot.findViewById<View>(R.id.widget_btn_prev).visibility)
        assertEquals(View.VISIBLE, activeRoot.findViewById<View>(R.id.widget_btn_next).visibility)
        assertNotNull(activeRoot.findViewById<View>(R.id.widget_btn_play_pause))
    }

    private fun renderAndSave(remoteViews: android.widget.RemoteViews, output: File): View {
        val density = context.resources.displayMetrics.density
        val widthPx = (336 * density).toInt()
        val heightPx = (96 * density).toInt()
        val container = FrameLayout(context)
        val inflated = remoteViews.apply(context, container)
        container.addView(inflated, FrameLayout.LayoutParams(widthPx, heightPx))
        container.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY),
        )
        container.layout(0, 0, widthPx, heightPx)
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#EEF0F4"))
        container.draw(canvas)
        FileOutputStream(output).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return inflated
    }
}
