package com.mylittleblover.mkwplayer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import com.mylittleblover.mkwplayer.MainActivity
import com.mylittleblover.mkwplayer.R
import com.mylittleblover.mkwplayer.player.PlaybackHolder
import com.mylittleblover.mkwplayer.player.PlaybackService
import com.mylittleblover.mkwplayer.player.PlaybackSession

internal object PlayerWidgetUpdater {

    fun updateAll(
        context: Context,
        state: PlaybackSession.State = PlaybackHolder.get().state.value,
        artwork: Bitmap? = null,
    ) {
        runCatching {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(app, PlayerWidgetProvider::class.java))
            if (ids.isEmpty()) return

            val resolvedArtwork = artwork ?: if (state.active) {
                PlaybackService.loadCoverBitmap(state.packDirPath, state.artworkName, state.courseCode, app.resources)
            } else null

            val views = buildViews(app, state, resolvedArtwork)
            manager.updateAppWidget(ids, views)
        }
    }

    private fun buildViews(
        context: Context,
        state: PlaybackSession.State,
        artwork: Bitmap?,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.mkw_player_widget)
        val title = state.title.takeIf { it.isNotBlank() } ?: context.getString(R.string.app_name)
        val subtitle = when {
            state.subtitle?.isNotBlank() == true -> state.subtitle
            state.playing -> context.getString(R.string.tile_playing)
            state.paused -> context.getString(R.string.tile_paused)
            else -> context.getString(R.string.tile_idle)
        }
        views.setTextViewText(R.id.widget_title, title)
        views.setTextViewText(R.id.widget_subtitle, subtitle)

        if (artwork != null) {
            views.setImageViewBitmap(R.id.widget_artwork, artwork)
        } else {
            views.setImageViewResource(R.id.widget_artwork, android.R.drawable.ic_media_play)
        }

        views.setImageViewResource(
            R.id.widget_btn_play_pause,
            if (state.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
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
        val canSkip = state.active && !uninterruptible && state.linear && session.canSkipQueue?.invoke() == true
        views.setViewVisibility(R.id.widget_btn_prev, if (canSkip) View.VISIBLE else View.GONE)
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
        PlayerWidgetUpdater.updateAll(context)
    }
}
