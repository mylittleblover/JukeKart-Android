package com.mylittleblover.mkwplayer.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.LruCache
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.mylittleblover.mkwplayer.MainActivity
import com.mylittleblover.mkwplayer.R
import java.io.File
import java.util.concurrent.Executors

/**
 * Keeps playback alive when the activity goes away, and publishes media controls.
 *
 * The service holds no audio state of its own: it reads [PlaybackSession] through the
 * holder and forwards commands back. That is deliberate -- duplicating the transport in
 * a service is how a player ends up with two clocks and a notification that disagrees
 * with what you hear.
 *
 * Foreground rules this follows:
 *  - `startForeground` runs in `onCreate` before media-session setup, because a service declared
 *    as media playback must show its notification within a few seconds of starting
 *  - the notification is always built from the current session state, never cached
 *  - `stopSelf` is called when the session goes inactive, so the service does not linger
 *    as a permanently foreground process with nothing playing
 */
class PlaybackService : Service() {

    private lateinit var session: PlaybackSession
    private var mediaSession: MediaSessionCompat? = null
    private lateinit var manager: NotificationManager
    private lateinit var focus: AudioFocusManager

    /** True when focus itself paused the transport, so a gain may resume it. */
    private var pausedByFocus = false

    /**
     * Keeps the session state fresh while the screen is away.
     *
     * The view model polls only while the activity is alive. Without this, the session's
     * state stops updating the moment the user leaves the app: the notification shows a
     * stale position, and flags read later (for example `playing` during a focus decision)
     * describe a transport that has moved on. The engine is the authority; the service has
     * to ask it.
     *
     * Runs on the main looper because every session method expects the control thread.
     */
    private val pollTicker = object : Runnable {
        override fun run() {
            if (!session.state.value.active) {
                PlaybackTimers.tickFromSession(session)
                polling = false
                return
            }
            val st = session.poll()
            PlaybackTimers.tickFromSession(session)
            if (st.linear && st.active) {
                PlaybackHolder.linear().onPositionObserved(st.positionFrames, st.playing)
            }
            refresh()
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }
    private var polling = false
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val artExecutor = Executors.newSingleThreadExecutor()
    private val artCache = object : LruCache<String, Bitmap>(4) {}
    private var currentArtKey: String? = null
    private var currentArtBitmap: Bitmap? = null
    private var pendingArtKey: String? = null
    private var lastMetadataKey: String? = null

    private fun currentCoverBitmap(state: PlaybackSession.State): Bitmap? {
        val key = coverKeyFor(state.packDirPath, state.artworkName, state.courseCode) ?: run {
            currentArtKey = null
            currentArtBitmap = null
            pendingArtKey = null
            return null
        }
        if (key == currentArtKey) return currentArtBitmap
        artCache.get(key)?.let { cached ->
            currentArtKey = key
            currentArtBitmap = cached
            pendingArtKey = null
            return cached
        }
        if (key != pendingArtKey) {
            pendingArtKey = key
            val packPath = state.packDirPath
            val artName = state.artworkName
            val course = state.courseCode
            artExecutor.execute {
                val decoded = runCatching {
                    loadCoverBitmap(packPath, artName, course, resources)
                }.getOrNull()
                handler.post {
                    if (pendingArtKey == key) pendingArtKey = null
                    if (decoded != null) {
                        artCache.put(key, decoded)
                        if (coverKeyFor(
                                session.state.value.packDirPath,
                                session.state.value.artworkName,
                                session.state.value.courseCode,
                            ) == key
                        ) {
                            currentArtKey = key
                            currentArtBitmap = decoded
                            if (session.state.value.active) {
                                refresh()
                            }
                        }
                    }
                }
            }
        }
        return null
    }

    private fun startPolling() {
        if (polling) return
        polling = true
        handler.postDelayed(pollTicker, POLL_INTERVAL_MS)
    }

    private fun stopPolling() {
        polling = false
        handler.removeCallbacks(pollTicker)
    }

    /**
     * Receives the route-change broadcast.
     *
     * Unplugging headphones must stop audible output; without this the music keeps playing
     * through the loudspeaker. Registered while the service exists and unregistered in
     * `onDestroy`, so it never outlives the playback it protects.
     */
    private val noisyReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != AudioFocusManager.ACTION_AUDIO_BECOMING_NOISY) return
            focus.notifyRouteLost()
        }
    }

    override fun onCreate() {
        super.onCreate()
        session = PlaybackHolder.get()
        manager = getSystemService(NotificationManager::class.java)
        createChannel()
        // MediaSession/receiver initialization can involve slow Binder calls. Meet the
        // platform deadline before that work, then refresh the richer notification later.
        startMediaForeground()

        focus = AudioFocusManager(this) { event -> handleFocusEvent(event) }

        ContextCompat.registerReceiver(
            this,
            noisyReceiver,
            android.content.IntentFilter(AudioFocusManager.ACTION_AUDIO_BECOMING_NOISY),
            // EXPORTED, deliberately. Route changes come from system broadcasts; guarded by
            // ContextCompat so API 26-32 receives standard registration cleanly without InlinedApi risk.
            ContextCompat.RECEIVER_EXPORTED,
        )

        mediaSession = MediaSessionCompat(this, MEDIA_SESSION_TAG).apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    if (session.uninterruptible) return
                    if (!requestFocusForPlayback()) return
                    session.resume()
                    refresh()
                }

                override fun onPause() {
                    if (session.uninterruptible) return
                    session.pause()
                    // A user pause is not a focus pause: focus must not resume it later.
                    pausedByFocus = false
                    refresh()
                }

                override fun onStop() {
                    if (session.uninterruptible) return
                    session.stop()
                    pausedByFocus = false
                    focus.abandon()
                    refresh()
                    stopSelf()
                }

                override fun onSeekTo(pos: Long) {
                    if (session.uninterruptible) return
                    val current = session.state.value
                    val targetFrame = if (current.sampleRate > 0 && current.totalFrames > 0) {
                        (pos * current.sampleRate / 1000L).coerceIn(0L, current.totalFrames)
                    } else {
                        pos.coerceAtLeast(0L)
                    }
                    val linearSeek = session.onLinearSeek
                    if (current.linear && linearSeek != null) {
                        linearSeek(targetFrame)
                    } else {
                        session.seek(targetFrame)
                    }
                    refresh()
                }

                override fun onSkipToNext() {
                    if (session.uninterruptible) return
                    if (session.onSkipNext?.invoke() == true) {
                        refresh()
                    }
                }

                override fun onSkipToPrevious() {
                    if (session.uninterruptible) return
                    if (session.onSkipPrevious?.invoke() == true) {
                        refresh()
                    }
                }
            })
            isActive = true
        }
    }

    /**
     * Applies a focus action.
     *
     * The policy lives in [FocusPolicy]; this only performs the result.
     */
    private fun handleFocusEvent(event: FocusPolicy.Event) {
        if (session.uninterruptible) return
        val action = FocusPolicy.decide(
            event = event,
            playing = session.state.value.playing,
            pausedByFocus = pausedByFocus,
        )
        when (action) {
            FocusPolicy.Action.NONE -> Unit

            FocusPolicy.Action.PAUSE_RESUMABLE -> {
                session.pause()
                pausedByFocus = true
                refresh()
            }

            FocusPolicy.Action.PAUSE_FINAL -> {
                session.pause()
                pausedByFocus = false
                refresh()
            }

            FocusPolicy.Action.RESUME -> {
                pausedByFocus = false
                if (focus.hasFocus) session.resume()
                refresh()
            }
        }
    }

    /**
     * Requests focus before starting playback.
     *
     * Returns false when the system refuses. Callers must treat that as "do not start":
     * starting anyway is exactly how two apps end up playing at once.
     */
    private fun requestFocusForPlayback(): Boolean {
        if (focus.hasFocus) return true
        pausedByFocus = false
        return focus.request()
    }

    private fun startMediaForeground() {
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), serviceType)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start foreground before anything else: the platform requires the notification
        // to exist promptly once a media service is started.
        startMediaForeground()

        when (intent?.action) {
            ACTION_PAUSE -> {
                if (!session.uninterruptible) {
                    session.pause()
                    pausedByFocus = false
                }
            }
            ACTION_RESUME -> {
                if (!session.uninterruptible && requestFocusForPlayback()) session.resume()
            }
            ACTION_TOGGLE -> {
                if (!session.uninterruptible) {
                    if (session.state.value.playing) {
                        session.pause()
                        pausedByFocus = false
                    } else if (session.state.value.paused && requestFocusForPlayback()) {
                        session.resume()
                    }
                }
            }
            ACTION_PREV -> {
                if (!session.uninterruptible) session.onSkipPrevious?.invoke()
            }
            ACTION_NEXT -> {
                if (!session.uninterruptible) session.onSkipNext?.invoke()
            }
            ACTION_STOP -> {
                if (!session.uninterruptible) {
                    session.stop()
                    pausedByFocus = false
                    focus.abandon()
                    com.mylittleblover.mkwplayer.widget.PlayerWidgetUpdater.updateAll(this, session.state.value, null)
                    com.mylittleblover.mkwplayer.widget.PlayerTileService.requestUpdate(this)
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
            else -> {
                // Started because a track began. If focus is refused the transport must not
                // keep playing into another app's audio.
                if (session.state.value.playing && !session.uninterruptible && !requestFocusForPlayback()) {
                    session.pause()
                }
            }
        }

        refresh()
        startupGate.promoted(intent?.getLongExtra(EXTRA_START_GENERATION,0L) ?: 0L)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try {
            unregisterReceiver(noisyReceiver)
        } catch (e: IllegalArgumentException) {
            // Not registered, or already gone. Nothing to undo.
        }
        focus.abandon()
        pausedByFocus = false
        stopPolling()
        pendingArtKey = null
        currentArtBitmap = null
        lastMetadataKey = null
        artCache.evictAll()
        artExecutor.shutdownNow()
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
        // A destroyed service must not leave its media notification in the shade.
        manager.cancel(NOTIFICATION_ID)
        if (!session.state.value.active) {
            com.mylittleblover.mkwplayer.widget.PlayerWidgetUpdater.updateAll(this, session.state.value, null)
            com.mylittleblover.mkwplayer.widget.PlayerTileService.requestUpdate(this)
        }
        super.onDestroy()
    }

    /** Rebuilds the notification and the media session state from the session. */
    private fun refresh() {
        val state = session.state.value
        updatePlaybackState(state)
        manager.notify(NOTIFICATION_ID, buildNotification())
        com.mylittleblover.mkwplayer.widget.PlayerWidgetUpdater.updateAll(
            this,
            state,
            if (state.active) currentCoverBitmap(state) else null,
        )
        com.mylittleblover.mkwplayer.widget.PlayerTileService.requestUpdate(this)

        if (state.active) startPolling() else stopPolling()

        if (!state.active) {
            // REMOVE, not DETACH: detaching leaves the notification behind after the
            // service dies, so a stopped player would keep a media control in the shade
            // with nothing behind it.
            stopForeground(STOP_FOREGROUND_REMOVE)
            manager.cancel(NOTIFICATION_ID)
            stopSelf()
        }
    }

    private fun updatePlaybackState(state: PlaybackSession.State) {
        val session = mediaSession ?: return
        val playbackState = when {
            state.playing -> PlaybackStateCompat.STATE_PLAYING
            state.paused -> PlaybackStateCompat.STATE_PAUSED
            else -> PlaybackStateCompat.STATE_STOPPED
        }
        val position = if (state.sampleRate > 0) {
            // MediaSession reports milliseconds; the engine only knows frames.
            state.positionFrames * 1000L / state.sampleRate
        } else {
            0L
        }
        val cover = currentCoverBitmap(state)
        val titleText = state.title.ifEmpty { getString(R.string.app_name) }
        val artistText = state.subtitle?.takeIf { it.isNotBlank() }
            ?: state.detail?.takeIf { it.isNotBlank() }
            ?: getString(R.string.app_name)
        val albumText = state.courseCode?.takeIf { it.isNotBlank() }?.removeSuffix("_00")
            ?: getString(R.string.app_name)
        val duration = durationMillis(state)
        val coverKey = coverKeyFor(state.packDirPath, state.artworkName, state.courseCode).orEmpty()
        val metadataKey = "$titleText|$artistText|$albumText|$duration|$coverKey|${cover != null}"
        if (metadataKey != lastMetadataKey) {
            val metadataBuilder = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, titleText)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artistText)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, albumText)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
            if (cover != null) {
                metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, cover)
            }
            session.setMetadata(metadataBuilder.build())
            lastMetadataKey = metadataKey
        }
        val actions = if (this.session.uninterruptible) {
            0L
        } else {
            val baseActions = PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SEEK_TO
            if (state.linear && this.session.canSkipQueue?.invoke() == true) {
                baseActions or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
            } else {
                baseActions
            }
        }
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(playbackState, position, if (state.playing) 1f else 0f)
                .build(),
        )
    }

    private fun durationMillis(state: PlaybackSession.State): Long =
        if (state.sampleRate > 0) state.totalFrames * 1000L / state.sampleRate else 0L

    private fun buildNotification(): Notification {
        val state = session.state.value
        val cover = currentCoverBitmap(state)
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val subtitleText = state.subtitle?.takeIf { it.isNotBlank() } ?: when {
            state.detail != null -> state.detail
            state.paused -> "Paused"
            state.playing -> "Playing"
            else -> "Stopped"
        }

        val titleText = state.title.ifEmpty { getString(R.string.app_name) }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_jukekart)
            .setColor(0xFFC52D49.toInt())
            .setContentTitle(titleText)
            .setContentText(subtitleText)
            .setSubText(state.courseCode?.takeIf { it.isNotBlank() }?.removeSuffix("_00"))
            .setLargeIcon(cover)
            .setColorized(cover != null)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setShowWhen(false)
            .setContentIntent(openApp)
            .setOngoing(state.playing || session.uninterruptible)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        val canSkip = state.linear && session.canSkipQueue?.invoke() == true
        var actionCount = 0
        var prevIdx = -1
        var playPauseIdx = -1
        var nextIdx = -1
        var stopIdx = -1

        if (!session.uninterruptible) {
            if (canSkip) {
                prevIdx = actionCount++
                builder.addAction(
                    R.drawable.ic_notif_prev, "Previous",
                    command(ACTION_PREV),
                )
            }
            if (!state.playing) {
                playPauseIdx = actionCount++
                builder.addAction(
                    R.drawable.ic_widget_play, "Resume",
                    command(ACTION_RESUME),
                )
            }
            if (state.playing) {
                playPauseIdx = actionCount++
                builder.addAction(
                    R.drawable.ic_widget_pause, "Pause",
                    command(ACTION_PAUSE),
                )
            }
            if (canSkip) {
                nextIdx = actionCount++
                builder.addAction(
                    R.drawable.ic_notif_next, "Next",
                    command(ACTION_NEXT),
                )
            }
            stopIdx = actionCount++
            builder.addAction(R.drawable.ic_notif_stop, "Stop", command(ACTION_STOP))
        }

        mediaSession?.let { media ->
            val style = androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(media.sessionToken)
                .setShowCancelButton(true)
                .setCancelButtonIntent(command(ACTION_STOP))
            val compactIndices = when {
                session.uninterruptible || actionCount == 0 -> intArrayOf()
                prevIdx >= 0 && playPauseIdx >= 0 && nextIdx >= 0 -> intArrayOf(prevIdx, playPauseIdx, nextIdx)
                playPauseIdx >= 0 && stopIdx >= 0 -> intArrayOf(playPauseIdx, stopIdx)
                playPauseIdx >= 0 -> intArrayOf(playPauseIdx)
                else -> intArrayOf(0)
            }
            if (compactIndices.isNotEmpty()) {
                style.setShowActionsInCompactView(*compactIndices)
            }
            builder.setStyle(style)
        }
        return builder.build()
    }

    private fun command(action: String): PendingIntent = PendingIntent.getService(
        this,
        action.hashCode(),
        Intent(this, PlaybackService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Playback",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows what is playing and offers transport controls."
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val EXTRA_START_GENERATION="playbackStartGeneration"
        private val startupGate=PlaybackStartupGate()
        internal suspend fun awaitForegroundPromotion() = startupGate.awaitPromotion()

        const val CHANNEL_ID = "mkw.playback"
        const val NOTIFICATION_ID = 1
        const val MEDIA_SESSION_TAG = "mkwplayer"

        /** 500 ms: smooth enough for a progress bar, cheap enough to run indefinitely. */
        const val POLL_INTERVAL_MS = 500L

        const val ACTION_TOGGLE = "com.mylittleblover.mkwplayer.TOGGLE"
        const val ACTION_PAUSE = "com.mylittleblover.mkwplayer.PAUSE"
        const val ACTION_RESUME = "com.mylittleblover.mkwplayer.RESUME"
        const val ACTION_PREV = "com.mylittleblover.mkwplayer.PREV"
        const val ACTION_NEXT = "com.mylittleblover.mkwplayer.NEXT"
        const val ACTION_STOP = "com.mylittleblover.mkwplayer.STOP"

        /**
         * Starts the foreground service, but only when something is actually playing.
         *
         * The guard is not cosmetic. `startForegroundService` promises the platform that
         * `startForeground` follows promptly; starting it for an idle session and then
         * stopping immediately races that handshake and the system kills the process with
         * `ForegroundServiceDidNotStartInTimeException`. Observed on the emulator: the
         * service was "brought down while still waiting for start foreground".
         *
         * Returns false when nothing was started, so callers can tell the difference.
         */
        fun start(context: Context): Boolean {
            if (!PlaybackHolder.get().state.value.active) return false
            // A start cancels a stop that has not fired yet: the user resumed.
            pendingStop?.let { stopHandler.removeCallbacks(it) }
            pendingStop = null

            val request=startupGate.requested()
            val intent = Intent(context, PlaybackService::class.java).putExtra(EXTRA_START_GENERATION,request)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
                else context.startService(intent)
            } catch(error: RuntimeException) {
                startupGate.promoted(request)
                throw error
            }
            return true
        }

        /**
         * Stops the service after the foreground handshake has had time to finish.
         *
         * Calling `stopService` while `startForegroundService`'s handshake is still pending
         * brings the service down before `startForeground` runs, and the platform then kills
         * the process with `ForegroundServiceDidNotStartInTimeException`. That is reachable
         * from the UI -- start a track, press Stop immediately -- and it is what made the
         * device suite crash rather than fail.
         */
        fun stop(context: Context) {
            val app = context.applicationContext
            pendingStop?.let { stopHandler.removeCallbacks(it) }
            val runnable = Runnable {
                pendingStop = null
                app.stopService(Intent(app, PlaybackService::class.java))
            }
            pendingStop = runnable
            stopHandler.postDelayed(runnable, STOP_GRACE_MS)
        }

        /** Delay long enough for one foreground handshake, short enough to feel immediate. */
        private const val STOP_GRACE_MS = 400L

        private val stopHandler by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }
        private var pendingStop: Runnable? = null

        internal fun coverKeyFor(packDirPath: String?, artworkName: String?, courseCode: String?): String? {
            val cleanArt = artworkName?.trim()?.takeIf { it.isNotEmpty() }
            val cleanCourse = courseCode?.trim()?.takeIf { it.isNotEmpty() }
            return when {
                !packDirPath.isNullOrBlank() && cleanArt != null -> "pack:$packDirPath|$cleanArt"
                cleanCourse != null -> "course:$cleanCourse"
                else -> null
            }
        }

        internal fun loadCoverBitmap(
            packDirPath: String?,
            artworkName: String?,
            courseCode: String?,
            resources: android.content.res.Resources,
        ): Bitmap? {
            if (!packDirPath.isNullOrBlank() && !artworkName.isNullOrBlank()) {
                val file = File(File(packDirPath, "artwork"), artworkName)
                if (file.isFile) {
                    decodeSampled { BitmapFactory.decodeFile(file.path, it) }?.let { return it }
                }
            }
            val cleanCourse = courseCode?.trim()?.takeIf { it.isNotEmpty() }
            if (cleanCourse != null && !packDirPath.isNullOrBlank()) {
                val artDir = File(packDirPath, "artwork")
                for (candidate in com.mylittleblover.mkwplayer.ui.art.CourseCoverArt.packCandidates(cleanCourse)) {
                    val file = File(artDir, candidate)
                    if (file.isFile) {
                        decodeSampled { BitmapFactory.decodeFile(file.path, it) }?.let { return it }
                    }
                }
            }
            return null
        }

        private fun decodeSampled(decode: (BitmapFactory.Options) -> Bitmap?): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            decode(bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
            while (maxDim / sample > 384) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            return decode(opts)
        }
    }
}
