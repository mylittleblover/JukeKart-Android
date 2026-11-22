package com.mylittleblover.mkwplayer.widget

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.mylittleblover.mkwplayer.MainActivity
import com.mylittleblover.mkwplayer.R
import com.mylittleblover.mkwplayer.player.PlaybackHolder
import com.mylittleblover.mkwplayer.player.PlaybackService

class PlayerTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val session = PlaybackHolder.get()
        val state = session.state.value
        if (session.uninterruptible) return

        if (state.active) {
            val intent = Intent(this, PlaybackService::class.java).setAction(PlaybackService.ACTION_TOGGLE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            updateTileState()
        } else {
            val launch = Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP,
            )
            if (Build.VERSION.SDK_INT >= 34) {
                val pending = PendingIntent.getActivity(
                    this,
                    705,
                    launch,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                startActivityAndCollapse(pending)
            } else {
                collapseLegacy(launch)
            }
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun collapseLegacy(launch: Intent) {
        startActivityAndCollapse(launch)
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val state = PlaybackHolder.get().state.value
        tile.state = when {
            state.playing -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.label = state.title.takeIf { it.isNotBlank() } ?: getString(R.string.tile_default_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when {
                state.playing -> getString(R.string.tile_playing)
                state.paused -> getString(R.string.tile_paused)
                else -> getString(R.string.tile_idle)
            }
        }
        tile.icon = Icon.createWithResource(
            this,
            if (state.playing) R.drawable.ic_widget_pause else R.drawable.ic_stat_jukekart,
        )
        tile.updateTile()
    }

    companion object {
        fun requestUpdate(context: Context) {
            runCatching {
                requestListeningState(context, ComponentName(context, PlayerTileService::class.java))
            }
        }
    }
}
