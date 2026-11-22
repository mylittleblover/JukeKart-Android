package com.mylittleblover.mkwplayer.player

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.mylittleblover.mkwplayer.MainActivity
import com.mylittleblover.mkwplayer.R
import com.mylittleblover.mkwplayer.library.PlayStatsRepository
import com.mylittleblover.mkwplayer.library.PlaylistItemRef
import com.mylittleblover.mkwplayer.library.RecentCourseStore
import com.mylittleblover.mkwplayer.library.StageFlowDiscovery
import com.mylittleblover.mkwplayer.ui.art.PackArtwork
import com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog
import com.mylittleblover.mkwplayer.unlock.ListeningProgress
import com.mylittleblover.mkwplayer.unlock.SpecialCupRules
import com.mylittleblover.mkwplayer.unlock.SpecialCupUnlock
import com.mylittleblover.mkwplayer.unlock.UnlockStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Process-wide Special Cup unlock, notification, and background recent-play coordinator.
 *
 * Lives on [PlaybackHolder] rather than [PlayerViewModel] so background playback driven by
 * [PlaybackService] and [LinearPlaybackCoordinator] continues crediting 30-second listens,
 * persisting [UnlockStore], firing the unlock heads-up notification, and recording recent tracks
 * even when `MainActivity` is stopped or cleared.
 */
internal class UnlockProgressCoordinator {

    data class ConfigureOutcome(
        val problem: String?,
        val repairedCeremony: Boolean,
        val newlyEntitled: Boolean,
    )

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saveMutex = Mutex()

    private var appContext: Context? = null
    private var unlockStore: UnlockStore? = null
    private var cup: SpecialCupUnlock? = null
    private var listening: ListeningProgress? = null

    private var lastSeekSequence: Long = -1L
    private var lastSampleWallMs: Long = 0L
    private var lastUnlockSavedMs: Long = 0L
    private var offered: Boolean = false

    @Volatile
    var pendingUnlockNotification: Boolean = false
        private set

    private val _naturalUnlockUi = MutableStateFlow<UnlockUi?>(null)
    val naturalUnlockUi: StateFlow<UnlockUi?> = _naturalUnlockUi.asStateFlow()

    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()

    private val _needsNotificationPermission = MutableStateFlow(false)
    val needsNotificationPermission: StateFlow<Boolean> = _needsNotificationPermission.asStateFlow()

    fun consumeNotificationPermissionEvent() {
        _needsNotificationPermission.value = false
    }

    fun configure(
        context: Context,
        eligibleStages: Set<String>,
        preloadedUnlock: UnlockStore.Loaded? = null,
        storeFile: File = File(context.applicationContext.filesDir, PlayerViewModel.SPECIAL_CUP_FILE),
    ): ConfigureOutcome {
        val app = context.applicationContext
        appContext = app
        val store = UnlockStore(storeFile)
        unlockStore = store

        if (eligibleStages.isEmpty()) {
            cup = null
            listening = null
            offered = false
            _naturalUnlockUi.value = null
            return ConfigureOutcome(problem = null, repairedCeremony = false, newlyEntitled = false)
        }

        val restored = preloadedUnlock ?: store.load()
        val rules = SpecialCupUnlock(eligibleStages).let { fresh ->
            restored.state?.let { fresh.withState(it) } ?: fresh
        }
        val bridge = ListeningProgress(rules)
        val wasEntitled = restored.state?.entitled == true
        val reconciledChanged = bridge.reconcile()
        val nowEntitled = bridge.snapshot.entitled
        val newlyEntitledRetroactive = nowEntitled && !wasEntitled

        cup = rules.withState(bridge.snapshot)
        listening = bridge
        lastSeekSequence = -1L
        lastSampleWallMs = 0L

        if (reconciledChanged || newlyEntitledRetroactive) {
            saveUnlockAsync(bridge.snapshot)
        }

        offered = bridge.snapshot.ceremony == SpecialCupUnlock.CeremonyPhase.PENDING ||
            bridge.snapshot.ceremony == SpecialCupUnlock.CeremonyPhase.PLAYING ||
            bridge.snapshot.ceremony == SpecialCupUnlock.CeremonyPhase.COMPLETED ||
            newlyEntitledRetroactive
        _naturalUnlockUi.value = UnlockUi.from(bridge, offered)

        if (newlyEntitledRetroactive) {
            postUnlockReadyNotification(app)
        }

        return ConfigureOutcome(
            problem = restored.problem,
            repairedCeremony = restored.repairedCeremony,
            newlyEntitled = newlyEntitledRetroactive,
        )
    }

    fun snapshot(): SpecialCupUnlock.UnlockState? = listening?.snapshot

    fun isEntitled(): Boolean {
        val rules = cup ?: return false
        val bridge = listening ?: return false
        return rules.withState(bridge.snapshot).isEntitled
    }

    fun owesCongratulations(): Boolean {
        val rules = cup ?: return false
        val bridge = listening ?: return false
        return rules.withState(bridge.snapshot).owesCongratulations
    }

    fun creditSample(
        state: PlaybackSession.State,
        isCeremonyLocked: Boolean,
        seekSequence: Long,
        playbackSpeed: Float,
    ) {
        if (isCeremonyLocked) return
        val rules = cup ?: return
        val bridge = listening ?: return

        val rawCourse = if (state.linear) {
            state.courseCode?.takeIf { it.isNotBlank() && !it.startsWith(PlaylistItemRef.PREFIX_EXTRA) }
        } else {
            state.courseCode?.takeIf { it.isNotBlank() }
                ?: state.title.takeIf { it.isNotEmpty() }?.let(StageFlowDiscovery::prefixOf)
        } ?: return

        val normalized = SpecialCupRules.normalizeStageCode(rawCourse)
        if (normalized.isBlank() || SpecialCupRules.isExcludedFromUnlock(normalized)) return
        val stage = rules.resolveEligibleStage(normalized) ?: return

        val beforeCompleted = bridge.snapshot.completedStages.size
        val beforeEntitled = bridge.snapshot.entitled
        val beforeCeremony = bridge.snapshot.ceremony

        if (lastSeekSequence != seekSequence) {
            if (lastSeekSequence >= 0L) {
                bridge.stopped()
            }
            lastSeekSequence = seekSequence
        }

        if (bridge.snapshot.currentStage != stage) {
            val sr = if (state.sampleRate > 0) state.sampleRate else 48_000
            bridge.begin(stage, state.totalFrames.coerceAtLeast(0L), sr)
            lastSampleWallMs = SystemClock.elapsedRealtime()
        }
        if (!state.playing && !state.paused) {
            bridge.stopped()
        }

        val nowMs = SystemClock.elapsedRealtime()
        val wallDeltaMs = if (lastSampleWallMs > 0L) (nowMs - lastSampleWallMs).coerceAtLeast(1L) else 1_000L
        lastSampleWallMs = nowMs

        val outcome = bridge.sampleContinuous(
            frames = state.positionFrames,
            playing = state.playing,
            wallDeltaMs = wallDeltaMs,
            maxPlaybackSpeed = playbackSpeed.toDouble().coerceIn(1.0, 2.5),
        )
        if (outcome.ignoredReason != null) return

        if (outcome.offeredNow) {
            offered = true
        }
        val milestone = outcome.state.completedStages.size != beforeCompleted ||
            outcome.state.entitled != beforeEntitled ||
            outcome.state.ceremony != beforeCeremony ||
            outcome.offeredNow
        val nextUi = UnlockUi.from(bridge, offered)
        if (milestone || nextUi != _naturalUnlockUi.value) {
            _naturalUnlockUi.value = nextUi
        }
        val stageInProgress = stage !in outcome.state.completedStages
        if (milestone || (stageInProgress && nowMs - lastUnlockSavedMs >= 5_000L)) {
            lastUnlockSavedMs = nowMs
            saveUnlockAsync(outcome.state)
        }
        if (outcome.newlyEntitled) {
            appContext?.let(::postUnlockReadyNotification)
        }
    }

    fun enterCeremony(): Boolean {
        val rules = cup ?: return false
        val bridge = listening ?: return false
        if (bridge.snapshot.ceremony == SpecialCupUnlock.CeremonyPhase.PLAYING) return false
        val next = rules.withState(bridge.snapshot).beginCeremony()
        bridge.seed(next)
        offered = true
        saveUnlockAsync(next)
        _naturalUnlockUi.value = UnlockUi.from(bridge, offered = true)
        return true
    }

    fun leaveCeremony() {
        val rules = cup ?: return
        val bridge = listening ?: return
        if (bridge.snapshot.ceremony != SpecialCupUnlock.CeremonyPhase.PLAYING) return
        val next = rules.withState(bridge.snapshot).leaveCeremonyEarly()
        bridge.seed(next)
        offered = true
        saveUnlockAsync(next)
        _naturalUnlockUi.value = UnlockUi.from(bridge, offered = true)
    }

    fun ceremonyReachedEnd() {
        val rules = cup ?: return
        val bridge = listening ?: return
        if (bridge.snapshot.ceremony != SpecialCupUnlock.CeremonyPhase.PLAYING) return
        val next = rules.withState(bridge.snapshot).finishCeremony(reachedEnd = true)
        bridge.seed(next)
        offered = true
        saveUnlockAsync(next)
        _naturalUnlockUi.value = UnlockUi.from(bridge, offered = true)
    }

    fun markCongratulated() {
        val rules = cup ?: return
        val bridge = listening ?: return
        if (!rules.withState(bridge.snapshot).owesCongratulations) return
        val next = rules.withState(bridge.snapshot).markCongratulated()
        bridge.seed(next)
        offered = true
        saveUnlockAsync(next)
        _naturalUnlockUi.value = UnlockUi.from(bridge, offered = true)
    }

    fun reset(context: Context): String? {
        val store = unlockStore ?: UnlockStore(File(context.applicationContext.filesDir, PlayerViewModel.SPECIAL_CUP_FILE))
        lastSeekSequence = -1L
        lastSampleWallMs = 0L
        lastUnlockSavedMs = 0L
        cancelUnlockNotification(context)
        return store.reset()
    }

    fun postUnlockReadyNotification(context: Context? = appContext) {
        val app = (context ?: appContext)?.applicationContext ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) {
                pendingUnlockNotification = true
                _needsNotificationPermission.value = true
                return
            }
        }
        pendingUnlockNotification = false
        val nm = app.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                UNLOCK_CHANNEL_ID,
                app.getString(R.string.unlock_notif_channel),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                enableVibration(true)
            }
            nm.createNotificationChannel(ch)
        }
        val launchIntent = Intent(app, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_ENTER_CEREMONY, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            app,
            UNLOCK_NOTIFICATION_ID,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val largeIcon = resolveUnlockLargeIcon(app)
        val notification = NotificationCompat.Builder(app, UNLOCK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_jukekart)
            .setLargeIcon(largeIcon)
            .setColor(0xFFC52D49.toInt())
            .setContentTitle(app.getString(R.string.unlock_notif_title))
            .setContentText(app.getString(R.string.unlock_notif_body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()
        nm.notify(UNLOCK_NOTIFICATION_ID, notification)
    }

    private fun resolveUnlockLargeIcon(app: Context): Bitmap? {
        val packDir = PackArtwork.activePackDirectory.value
        if (packDir != null) {
            for (name in listOf("cup_special.png", "cup_special.webp")) {
                val file = File(File(packDir, "artwork"), name)
                if (file.isFile) {
                    BitmapFactory.decodeFile(file.path)?.let { return it }
                }
            }
        }
        val drawable = ContextCompat.getDrawable(app, R.mipmap.ic_launcher) ?: return null
        val size = 192
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bmp ->
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
        }
    }

    fun onNotificationPermissionResult(context: Context, granted: Boolean) {
        if (granted && pendingUnlockNotification) {
            postUnlockReadyNotification(context)
        } else if (!granted) {
            pendingUnlockNotification = false
        }
    }

    fun cancelUnlockNotification(context: Context? = appContext) {
        pendingUnlockNotification = false
        val app = (context ?: appContext)?.applicationContext ?: return
        val nm = app.getSystemService(NotificationManager::class.java)
        nm?.cancel(UNLOCK_NOTIFICATION_ID)
    }

    fun recordLinearTrackOpened(context: Context, entry: LinearQueue.Entry) {
        val refId = canonicalRefId(context.applicationContext, entry) ?: return
        val app = context.applicationContext
        ioScope.launch {
            PlayStatsRepository.recordPlay(app, refId)
            RecentCourseStore(File(app.filesDir, "recent-courses.json")).record(refId)
        }
    }

    private fun saveUnlockAsync(state: SpecialCupUnlock.UnlockState) {
        val store = unlockStore ?: return
        ioScope.launch {
            val problem = saveMutex.withLock {
                store.save(state)
            }
            if (problem != null) {
                _saveError.value = problem
            }
        }
    }

    companion object {
        const val UNLOCK_CHANNEL_ID = "jukekart_special_unlock_high"
        const val UNLOCK_NOTIFICATION_ID = 1002

        internal fun canonicalRefId(
            context: Context?,
            entry: LinearQueue.Entry,
            extraCatalogOverride: ExtraMusicCatalog.Catalog? = null,
        ): String? {
            if (entry.key == PlayerViewModel.CEREMONY_ENTRY_KEY) return null
            if (entry.sourceType == LinearSessionStore.SourceType.JUKEBOX) {
                val rawKey = (entry.sourceTrackKey ?: entry.key).removePrefix(PlaylistItemRef.PREFIX_JUKEBOX)
                return "${PlaylistItemRef.PREFIX_JUKEBOX}$rawKey"
            }
            val extraCatalog = extraCatalogOverride ?: context?.let { ExtraMusicCatalog.load(it) }
            fun resolveExtra(targetId: String): String = when {
                extraCatalog?.sfcCourses?.any { it.id == targetId } == true -> "${PlaylistItemRef.PREFIX_SFC}$targetId"
                extraCatalog?.battleTracks?.any { it.id == targetId } == true -> "${PlaylistItemRef.PREFIX_BATTLE}$targetId"
                else -> "${PlaylistItemRef.PREFIX_EXTRA}$targetId"
            }
            val code = entry.courseCode
            if (!code.isNullOrBlank()) {
                if (!code.startsWith(PlaylistItemRef.PREFIX_EXTRA)) {
                    return code.removeSuffix("_00")
                }
                return resolveExtra(code.removePrefix(PlaylistItemRef.PREFIX_EXTRA))
            }
            entry.overlaidOpening?.id?.let { return "${PlaylistItemRef.PREFIX_EXTRA}$it" }
            return when {
                entry.key.startsWith(PlaylistItemRef.PREFIX_SFC) ||
                    entry.key.startsWith(PlaylistItemRef.PREFIX_BATTLE) ||
                    entry.key.startsWith(PlaylistItemRef.PREFIX_JUKEBOX) -> entry.key
                entry.key.startsWith(PlaylistItemRef.PREFIX_EXTRA) ->
                    resolveExtra(entry.key.removePrefix(PlaylistItemRef.PREFIX_EXTRA))
                ":${PlaylistItemRef.PREFIX_EXTRA}" in entry.key -> {
                    val tid = entry.key.substringAfter(":${PlaylistItemRef.PREFIX_EXTRA}").substringBefore(':')
                    resolveExtra(tid)
                }
                else -> null
            }
        }
    }
}
