package com.mylittleblover.mkwplayer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.LayoutRes
import com.mylittleblover.mkwplayer.MainActivity
import com.mylittleblover.mkwplayer.R
import com.mylittleblover.mkwplayer.player.PlaybackHolder
import com.mylittleblover.mkwplayer.player.PlaybackService
import com.mylittleblover.mkwplayer.player.PlaybackSession

internal object PlayerWidgetUpdater {

    private var lastRoundedSource: Bitmap? = null
    private var lastRoundedBitmap: Bitmap? = null
    private var lastSignature: String? = null

    fun updateAll(
        context: Context,
        state: PlaybackSession.State = PlaybackHolder.get().state.value,
        artwork: Bitmap? = null,
        force: Boolean = false,
    ) {
        runCatching {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(app, PlayerWidgetProvider::class.java))
            if (ids.isEmpty()) {
                lastSignature = null
                return
            }

            val resolvedArtwork = artwork ?: if (state.active) {
                PlaybackService.loadCoverBitmap(state.packDirPath, state.artworkName, state.courseCode, app.resources)
            } else null

            val session = PlaybackHolder.get()
            val canSkip = state.active && !session.uninterruptible && state.linear && session.canSkipQueue?.invoke() == true
            val artSig = PlaybackService.coverKeyFor(state.packDirPath, state.artworkName, state.courseCode).orEmpty()
            val signature = "${state.active}|${state.playing}|${state.paused}|${state.title}|${state.subtitle.orEmpty()}|$canSkip|$artSig|${resolvedArtwork != null}"
            if (!force && signature == lastSignature) return

            val roundedArt = resolvedArtwork?.let { roundSquareBitmap(it, targetPx = 168, radiusPx = 36f) }
            for (id in ids) {
                val options = manager.getAppWidgetOptions(id)
                val views = buildResponsiveViews(app, state, roundedArt, canSkip, options)
                manager.updateAppWidget(id, views)
            }
            lastSignature = signature
        }
    }

    internal fun roundSquareBitmap(source: Bitmap, targetPx: Int, radiusPx: Float): Bitmap {
        if (source === lastRoundedSource) {
            lastRoundedBitmap?.let { return it }
        }
        val out = Bitmap.createBitmap(targetPx, targetPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val scale = maxOf(
            targetPx.toFloat() / source.width.coerceAtLeast(1),
            targetPx.toFloat() / source.height.coerceAtLeast(1),
        )
        val dx = (targetPx - source.width * scale) * 0.5f
        val dy = (targetPx - source.height * scale) * 0.5f
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(dx, dy)
        }
        shader.setLocalMatrix(matrix)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            this.shader = shader
        }
        canvas.drawRoundRect(RectF(0f, 0f, targetPx.toFloat(), targetPx.toFloat()), radiusPx, radiusPx, paint)
        lastRoundedSource = source
        lastRoundedBitmap = out
        return out
    }

    internal fun buildResponsiveViews(
        context: Context,
        state: PlaybackSession.State,
        artwork: Bitmap?,
        canSkip: Boolean,
        options: Bundle?,
    ): RemoteViews {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            val sizes = options?.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            if (!sizes.isNullOrEmpty()) {
                val mapping = sizes.associateWith { sz ->
                    buildViewsForDimensions(context, state, artwork, canSkip, sz.width, sz.height)
                }
                return RemoteViews(mapping)
            }
        }
        val minW = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)?.toFloat() ?: 250f
        val minH = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)?.toFloat() ?: 110f
        return buildViewsForDimensions(context, state, artwork, canSkip, minW, minH)
    }

    private fun buildViewsForDimensions(
        context: Context,
        state: PlaybackSession.State,
        artwork: Bitmap?,
        canSkip: Boolean,
        widthDp: Float,
        heightDp: Float,
    ): RemoteViews {
        val compact = heightDp > 0f && heightDp < 92f
        val layoutRes = if (compact) R.layout.mkw_player_widget_compact else R.layout.mkw_player_widget
        // Keep text column wide in 1-row mode: only show Next when width is tight, adding Prev once wide.
        val showPrev = !compact || widthDp >= 285f
        return buildViews(
            context = context,
            state = state,
            artwork = artwork,
            canSkip = canSkip,
            layoutRes = layoutRes,
            compact = compact,
            showPrevInCompact = showPrev,
        )
    }

    internal fun buildViews(
        context: Context,
        state: PlaybackSession.State,
        artwork: Bitmap?,
        canSkip: Boolean,
        @LayoutRes layoutRes: Int = R.layout.mkw_player_widget,
        compact: Boolean = false,
        showPrevInCompact: Boolean = true,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, layoutRes)
        val title = state.title.takeIf { it.isNotBlank() } ?: context.getString(R.string.app_name)
        val showStatus = state.playing || state.paused
        val statusText = when {
            state.playing -> context.getString(R.string.tile_playing)
            state.paused -> context.getString(R.string.tile_paused)
            else -> ""
        }
        val subtitle = when {
            state.subtitle?.isNotBlank() == true -> state.subtitle
            state.playing -> context.getString(R.string.tile_playing)
            state.paused -> context.getString(R.string.tile_paused)
            else -> context.getString(R.string.tile_idle)
        }
        views.setViewVisibility(R.id.widget_status_dot, if (!compact && state.playing) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_eyebrow, if (!compact && showStatus) View.VISIBLE else View.GONE)
        views.setTextViewText(R.id.widget_eyebrow, statusText)
        views.setTextViewText(R.id.widget_title, title)
        views.setTextViewText(R.id.widget_subtitle, subtitle)

        if (artwork != null) {
            views.setImageViewBitmap(R.id.widget_artwork, artwork)
            views.setViewVisibility(R.id.widget_artwork, View.VISIBLE)
            views.setViewVisibility(R.id.widget_artwork_placeholder, View.GONE)
        } else {
            views.setViewVisibility(R.id.widget_artwork, View.GONE)
            views.setViewVisibility(R.id.widget_artwork_placeholder, View.VISIBLE)
        }

        views.setImageViewResource(
            R.id.widget_btn_play_pause,
            if (state.playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
        )

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val openAppPending = PendingIntent.getActivity(
            context,
            REQ_OPEN_APP,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(R.id.widget_root, openAppPending)

        val session = PlaybackHolder.get()
        val uninterruptible = session.uninterruptible
        views.setViewVisibility(R.id.widget_btn_prev, if (canSkip && (!compact || showPrevInCompact)) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_btn_next, if (canSkip) View.VISIBLE else View.GONE)

        if (state.active && !uninterruptible) {
            views.setOnClickPendingIntent(
                R.id.widget_btn_play_pause,
                serviceCommandIntent(context, REQ_TOGGLE, PlaybackService.ACTION_TOGGLE),
            )
        } else {
            views.setOnClickPendingIntent(R.id.widget_btn_play_pause, openAppPending)
        }

        if (canSkip) {
            views.setOnClickPendingIntent(
                R.id.widget_btn_prev,
                serviceCommandIntent(context, REQ_PREV, PlaybackService.ACTION_PREV),
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_next,
                serviceCommandIntent(context, REQ_NEXT, PlaybackService.ACTION_NEXT),
            )
        }
        return views
    }

    private fun serviceCommandIntent(context: Context, requestCode: Int, action: String): PendingIntent {
        val intent = Intent(context, PlaybackService::class.java).setAction(action)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(context, requestCode, intent, flags)
        } else {
            PendingIntent.getService(context, requestCode, intent, flags)
        }
    }

    private const val REQ_OPEN_APP = 701
    private const val REQ_TOGGLE = 702
    private const val REQ_PREV = 703
    private const val REQ_NEXT = 704
}

class PlayerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        PlayerWidgetUpdater.updateAll(context, force = true)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?,
    ) {
        PlayerWidgetUpdater.updateAll(context, force = true)
    }
}
