package com.mylittleblover.mkwplayer.player

import android.content.Context
import com.mylittleblover.mkwplayer.audio.BwavHeader
import com.mylittleblover.mkwplayer.pack.PackCoverage
import com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Coordinates linear Jukebox queue playback across both [PlayerViewModel] and [PlaybackService].
 *
 * Keeping queue ownership and async track opening on the process holder allows track-to-track
 * advancement, MediaSession skip actions, shuffle permutations, and temporary "Play next" entries
 * to keep working even while the Activity is backgrounded or recreated.
 */
internal class LinearPlaybackCoordinator(
    private val sessionProvider: () -> PlaybackSession = { PlaybackHolder.get() },
    val queue: LinearQueue = LinearQueue(),
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    data class SessionContext(
        val contextKind: LinearSessionStore.ContextKind = LinearSessionStore.ContextKind.NONE,
        val contextId: String? = null,
        val contextTitle: String? = null,
        val nativeTransitionDefault: Boolean = false,
        val packFingerprints: List<String> = emptyList(),
        val allowPlayNext: Boolean = true,
        val allowShuffle: Boolean = true,
        val allowSingleRepeat: Boolean = true,
        val allowRepeatToggle: Boolean = true,
    )

    data class LinearUiState(
        val active: Boolean = false,
        val current: LinearQueue.Entry? = null,
        val queue: LinearQueue.Snapshot = LinearQueue.Snapshot(),
        val totalFrames: Long = 0L,
        val sampleRate: Int = 0,
        val error: String? = null,
        val loading: Boolean = false,
        val contextKind: LinearSessionStore.ContextKind = LinearSessionStore.ContextKind.NONE,
        val contextId: String? = null,
        val contextTitle: String? = null,
        val allowPlayNext: Boolean = true,
        val allowShuffle: Boolean = true,
        val allowSingleRepeat: Boolean = true,
        val allowRepeatToggle: Boolean = true,
        val abLoopStartFrame: Long? = null,
        val abLoopEndFrame: Long? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private val _state = MutableStateFlow(LinearUiState())
    val state: StateFlow<LinearUiState> = _state.asStateFlow()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var catalog: JukeboxCatalog.Catalog = JukeboxCatalog.Catalog(emptyList(), emptyMap())

    @Volatile
    private var extraPackDir: File? = null

    @Volatile
    private var activeLoopStart: Long = -1L

    @Volatile
    private var activeLoopEnd: Long = -1L

    @Volatile
    private var abLoopStartFrame: Long? = null

    @Volatile
    private var abLoopEndFrame: Long? = null

    private var requestGeneration: Long = 0L
    private var preloadGeneration: Long = 0L
    private var preloadJob: Job? = null
    private var preparedNext: PreparedNextEntry? = null
    private var seekEpoch: Long = 0L
    private var seekJob: Job? = null
    private var sessionContext: SessionContext = SessionContext()

    private data class PreparedNextEntry(
        val entryKey: String,
        val chainKeys: List<String>,
        val singleLoop: Boolean,
        val packDirPath: String,
        val prepared: PlaybackSession.Prepared,
        val totalFrames: Long,
        val sampleRate: Int,
        val loopStart: Long = -1L,
        val loopEnd: Long = -1L,
        val chainWindows: List<com.mylittleblover.mkwplayer.interactive.CourseMedleyChainCompiler.SegmentWindow> = emptyList(),
    )

    @Volatile
    private var sessionStore: LinearSessionStore? = null

    @Volatile
    private var activeChainWindows: List<com.mylittleblover.mkwplayer.interactive.CourseMedleyChainCompiler.SegmentWindow> = emptyList()

    @Volatile
    private var activeChainIndex: Int = 0

    @Volatile
    var onSessionCompletedNaturally: ((LinearQueue.Entry?) -> Unit)? = null

    @Volatile
    var onTrackOpened: ((LinearQueue.Entry) -> Unit)? = null

    fun configureSessionStore(store: LinearSessionStore) {
        sessionStore = store
    }

    fun readSavedSession(): LinearSessionStore.Snapshot? = sessionStore?.read()?.getOrNull()

    fun clearSavedSession() {
        scope.launch(ioDispatcher) { sessionStore?.clear() }
    }

    fun attach(context: Context, catalog: JukeboxCatalog.Catalog) {
        appContext = context.applicationContext
        this.catalog = catalog
        bindSessionCallbacks()
    }

    fun updateCatalog(catalog: JukeboxCatalog.Catalog) {
        this.catalog = catalog
    }

    fun startQueue(
        entries: List<LinearQueue.Entry>,
        startIndex: Int,
        shuffleOverride: Boolean? = null,
        context: SessionContext = SessionContext(),
    ) {
        sessionContext = context
        activeChainWindows = emptyList()
        activeChainIndex = 0
        abLoopStartFrame = null
        abLoopEndFrame = null
        val started = queue.startSession(entries, startIndex, if (context.allowShuffle) shuffleOverride else false) ?: return
        bindSessionCallbacks()
        openEntry(started)
    }

    fun startExtraQueue(
        entries: List<LinearQueue.Entry>,
        startIndex: Int,
        packDir: File,
        repeatMode: LinearQueue.RepeatMode = LinearQueue.RepeatMode.LIST,
        shuffleOverride: Boolean? = null,
        context: SessionContext = SessionContext(),
    ) {
        extraPackDir = packDir
        sessionContext = context
        activeChainWindows = emptyList()
        activeChainIndex = 0
        abLoopStartFrame = null
        abLoopEndFrame = null
        val safeRepeat = if (!context.allowSingleRepeat && repeatMode == LinearQueue.RepeatMode.SINGLE) {
            LinearQueue.RepeatMode.LIST
        } else {
            repeatMode
        }
        queue.setRepeatMode(safeRepeat)
        val started = queue.startSession(entries, startIndex, if (context.allowShuffle) shuffleOverride else false) ?: return
        bindSessionCallbacks()
        openEntry(started)
    }

    fun restoreAndPlay(
        state: LinearQueue.ExportedState,
        context: SessionContext,
        packDir: File?,
        resumePositionFrames: Long,
    ) {
        if (packDir != null) extraPackDir = packDir
        sessionContext = context
        activeChainWindows = emptyList()
        activeChainIndex = 0
        abLoopStartFrame = null
        abLoopEndFrame = null
        queue.restoreState(state)
        bindSessionCallbacks()
        openEntry(state.current, initialSeekFrames = resumePositionFrames.coerceAtLeast(0L))
    }

    fun enqueueNext(entry: LinearQueue.Entry) = enqueueNextBatch(listOf(entry))

    fun enqueueNextBatch(entries: List<LinearQueue.Entry>, packDir: File? = null) {
        if (packDir != null) extraPackDir = packDir
        if (entries.isEmpty() || !sessionContext.allowPlayNext) return
        val wasEmpty = queue.snapshot().current == null
        queue.enqueueNextBatch(entries)
        bindSessionCallbacks()
        if (wasEmpty) {
            queue.snapshot().current?.let { openEntry(it) }
        } else {
            if (!invalidateChainIfHeadChanged()) {
                refreshPreloadIfNeeded()
                publishSnapshot(error = null)
            }
        }
    }

    fun enqueueEndBatch(entries: List<LinearQueue.Entry>, packDir: File? = null) {
        if (packDir != null) extraPackDir = packDir
        if (entries.isEmpty() || !sessionContext.allowPlayNext) return
        val wasEmpty = queue.snapshot().current == null
        queue.enqueueEndBatch(entries)
        bindSessionCallbacks()
        if (wasEmpty) {
            queue.snapshot().current?.let { openEntry(it) }
        } else {
            if (!invalidateChainIfHeadChanged()) {
                refreshPreloadIfNeeded()
                publishSnapshot(error = null)
            }
        }
    }

    private fun invalidateChainIfHeadChanged(): Boolean {
        val expectedNextKey = activeChainWindows.getOrNull(activeChainIndex + 1)?.entryKey
        val current = queue.snapshot().current
        val firstUpcomingKey = queue.peekNextEntries(1).firstOrNull()?.key
        if (activeChainWindows.isNotEmpty() && current != null && firstUpcomingKey != expectedNextKey) {
            val pos = sessionProvider().state.value.positionFrames.coerceAtLeast(0L)
            activeChainWindows = emptyList()
            activeChainIndex = 0
            invalidatePreload()
            openEntry(current, initialSeekFrames = pos, allowChaining = false)
            return true
        }
        return false
    }

    fun removeTempAt(index: Int) {
        if (queue.removeTempAt(index)) {
            if (!invalidateChainIfHeadChanged()) {
                refreshPreloadIfNeeded()
                publishSnapshot(error = null)
            }
        }
    }

    fun removeUpcomingAt(index: Int) {
        if (queue.removeUpcomingAt(index)) {
            if (!invalidateChainIfHeadChanged()) {
                refreshPreloadIfNeeded()
                publishSnapshot(error = null)
            }
        }
    }

    fun moveTemp(from: Int, to: Int) {
        if (queue.moveTemp(from, to)) {
            if (!invalidateChainIfHeadChanged()) {
                refreshPreloadIfNeeded()
                publishSnapshot(error = null)
            }
        }
    }

    fun moveUpcoming(from: Int, to: Int) {
        if (queue.moveUpcoming(from, to)) {
            if (!invalidateChainIfHeadChanged()) {
                refreshPreloadIfNeeded()
                publishSnapshot(error = null)
            }
        }
    }

    fun clearUpcoming() {
        if (queue.clearUpcoming()) {
            if (!invalidateChainIfHeadChanged()) {
                refreshPreloadIfNeeded()
                publishSnapshot(error = null)
            }
        }
    }

    fun setAbLoopPointA() {
        val session = sessionProvider()
        val sState = session.state.value
        if (!sState.linear || !sState.active || session.uninterruptible) return
        val total = _state.value.totalFrames.takeIf { it > 0L } ?: sState.totalFrames
        val pos = sState.positionFrames.coerceIn(0L, (total - 1L).coerceAtLeast(0L))
        abLoopStartFrame = pos
        val currentB = abLoopEndFrame
        if (currentB != null && currentB <= pos) {
            abLoopEndFrame = null
            applyNativeRepeatForCurrentState()
        }
        publishSnapshot(error = null)
    }

    fun setAbLoopPointB() {
        val session = sessionProvider()
        val sState = session.state.value
        if (!sState.linear || !sState.active || session.uninterruptible) return
        val total = _state.value.totalFrames.takeIf { it > 0L } ?: sState.totalFrames
        val sr = _state.value.sampleRate.takeIf { it > 0 } ?: sState.sampleRate.coerceAtLeast(48_000)
        val aFrame = (abLoopStartFrame ?: 0L).coerceIn(0L, (total - 2L).coerceAtLeast(0L))
        val minEnd = (aFrame + (sr / 4).coerceAtLeast(1)).coerceAtMost(total.coerceAtLeast(1L))
        val pos = sState.positionFrames.coerceIn(minEnd, total.coerceAtLeast(minEnd))
        if (pos <= aFrame) return
        abLoopStartFrame = aFrame
        abLoopEndFrame = pos
        val current = queue.snapshot().current
        if (supportsNativeAbLoop(current)) {
            session.setLinearRepeat(true, aFrame, pos)
        }
        if (sState.positionFrames >= pos) {
            seekToFrame(aFrame)
        }
        publishSnapshot(error = null)
    }

    fun clearAbLoop() {
        if (abLoopStartFrame == null && abLoopEndFrame == null) return
        abLoopStartFrame = null
        abLoopEndFrame = null
        applyNativeRepeatForCurrentState()
        publishSnapshot(error = null)
    }

    fun onPositionObserved(positionFrames: Long, playing: Boolean) {
        if (!playing) return
        val a = abLoopStartFrame ?: return
        val b = abLoopEndFrame ?: return
        if (b <= a) return
        val current = queue.snapshot().current ?: return
        if (supportsNativeAbLoop(current)) return
        val session = sessionProvider()
        if (!session.state.value.linear || !session.state.value.active || session.isSeeking) return
        if (positionFrames >= b) {
            seekToFrame(a)
        }
    }

    private fun supportsNativeAbLoop(entry: LinearQueue.Entry?): Boolean =
        entry != null &&
            entry.overlaidOpening == null &&
            entry.chainedSequence == null &&
            entry.courseMedley == null &&
            activeChainWindows.isEmpty()

    private fun applyNativeRepeatForCurrentState() {
        val session = sessionProvider()
        val current = queue.snapshot().current
        val sState = session.state.value
        if (!sState.linear || !sState.active) return
        val a = abLoopStartFrame
        val b = abLoopEndFrame
        if (a != null && b != null && b > a && supportsNativeAbLoop(current)) {
            session.setLinearRepeat(true, a, b)
            return
        }
        if (supportsNativeAbLoop(current)) {
            val single = queue.repeatMode == LinearQueue.RepeatMode.SINGLE && sessionContext.allowSingleRepeat
            session.setLinearRepeat(single, activeLoopStart, activeLoopEnd)
        }
    }

    fun jumpToTemp(index: Int) {
        val chosen = queue.jumpToTemp(index) ?: return
        activeChainWindows = emptyList()
        activeChainIndex = 0
        abLoopStartFrame = null
        abLoopEndFrame = null
        openEntry(chosen)
    }

    fun jumpToUpcoming(index: Int) {
        val chosen = queue.jumpToUpcoming(index) ?: return
        activeChainWindows = emptyList()
        activeChainIndex = 0
        abLoopStartFrame = null
        abLoopEndFrame = null
        openEntry(chosen)
    }

    fun toggleShuffle() {
        if (!sessionContext.allowShuffle) return
        queue.setShuffle(!queue.shuffle)
        refreshPreloadIfNeeded()
        publishSnapshot(error = null)
    }

    fun cycleRepeatMode() {
        if (!sessionContext.allowRepeatToggle) return
        val mode = if (!sessionContext.allowSingleRepeat) {
            val next = if (queue.repeatMode == LinearQueue.RepeatMode.LIST) {
                LinearQueue.RepeatMode.OFF
            } else {
                LinearQueue.RepeatMode.LIST
            }
            queue.setRepeatMode(next)
            next
        } else {
            queue.cycleRepeatMode()
        }
        val session = sessionProvider()
        val current = queue.snapshot().current
        if (abLoopStartFrame != null && abLoopEndFrame != null) {
            refreshPreloadIfNeeded()
            publishSnapshot(error = null)
            return
        }
        if (session.state.value.linear && session.state.value.active &&
            current?.overlaidOpening == null && current?.chainedSequence == null && current?.courseMedley == null
        ) {
            session.setLinearRepeat(mode == LinearQueue.RepeatMode.SINGLE, activeLoopStart, activeLoopEnd)
        } else if (mode == LinearQueue.RepeatMode.SINGLE && activeChainWindows.isNotEmpty() && current != null) {
            val pos = session.state.value.positionFrames.coerceAtLeast(0L)
            activeChainWindows = emptyList()
            activeChainIndex = 0
            invalidatePreload()
            openEntry(current, initialSeekFrames = pos, allowChaining = false)
            return
        }
        refreshPreloadIfNeeded()
        publishSnapshot(error = null)
    }

    fun seekToFrame(targetFrame: Long) {
        val session = sessionProvider()
        if (session.uninterruptible) return
        val current = queue.snapshot().current
        val clamped = targetFrame.coerceAtLeast(0L)
        if (activeChainWindows.isNotEmpty() && activeChainIndex > 0 && current != null) {
            // Re-open chain starting from `current` on Dispatchers.IO with initialSeekFrames
            // applied inside prepareFlow before commitPrepared, keeping remaining stops chained.
            seekEpoch++
            seekJob?.cancel()
            seekJob = null
            activeChainWindows = emptyList()
            activeChainIndex = 0
            openEntry(current, initialSeekFrames = clamped, allowChaining = true)
            return
        }
        val firstWindowExit = activeChainWindows.firstOrNull()?.handoverExitFrame
        val effectiveTarget = if (activeChainWindows.isNotEmpty() && activeChainIndex == 0 && firstWindowExit != null) {
            clamped.coerceAtMost((firstWindowExit - 1L).coerceAtLeast(0L))
        } else {
            clamped
        }
        if (seekJob?.isActive == true) {
            session.updatePendingSeek(effectiveTarget)
            return
        }
        val epoch = ++seekEpoch
        seekJob = scope.launch {
            val sought = session.seekAsync(effectiveTarget)
            if (epoch == seekEpoch && sought.active) {
                saveCurrentProgress(sought.positionFrames, _state.value.totalFrames, _state.value.sampleRate)
            }
        }
    }

    fun saveCurrentProgress(positionFrames: Long, totalFrames: Long, sampleRate: Int) {
        if (sessionProvider().uninterruptible || queue.snapshot().current?.key == "extra:special_cup_appears") return
        val store = sessionStore ?: return
        val exported = queue.exportState() ?: return
        val snapshot = LinearSessionStore.Snapshot(
            frozenBase = exported.frozenBase.map(LinearHandoverPlanner::toSavedEntry),
            current = LinearHandoverPlanner.toSavedEntry(exported.current),
            currentBaseIndex = exported.currentBaseIndex,
            pastHistory = exported.pastHistory.map { (e, idx) ->
                LinearSessionStore.IndexedEntry(LinearHandoverPlanner.toSavedEntry(e), idx)
            },
            forwardHistory = exported.forwardHistory.map { (e, idx) ->
                LinearSessionStore.IndexedEntry(LinearHandoverPlanner.toSavedEntry(e), idx)
            },
            tempQueue = exported.tempQueue.map(LinearHandoverPlanner::toSavedEntry),
            upcomingBase = exported.upcomingBase.map { (e, idx) ->
                LinearSessionStore.IndexedEntry(LinearHandoverPlanner.toSavedEntry(e), idx)
            },
            repeatMode = exported.repeatMode,
            shuffle = exported.shuffle,
            nativeTransitionDefault = sessionContext.nativeTransitionDefault,
            contextKind = sessionContext.contextKind,
            contextId = sessionContext.contextId,
            contextTitle = sessionContext.contextTitle,
            positionFrames = positionFrames.coerceAtLeast(0L),
            totalFrames = totalFrames.coerceAtLeast(0L),
            sampleRate = sampleRate.coerceAtLeast(1),
            packFingerprints = sessionContext.packFingerprints,
            savedAtEpochMs = System.currentTimeMillis(),
        )
        scope.launch(ioDispatcher) {
            store.write(snapshot)
        }
    }

    fun replayCurrent() {
        val current = queue.snapshot().current ?: return
        openEntry(current)
    }

    fun skipNext(): Boolean {
        val step = queue.next()
        return applyStep(step)
    }

    fun skipPrevious(): Boolean {
        val state = sessionProvider().state.value
        val elapsedMs = if (state.sampleRate > 0) state.positionFrames * 1000L / state.sampleRate else 0L
        val step = queue.previous(restartThresholdMs = PREVIOUS_RESTART_MS, elapsedMs = elapsedMs)
        return applyStep(step)
    }

    fun handleNaturalEnd(): Boolean {
        if (PlaybackTimers.engine.consumeAfterCurrentTrackOnNaturalEnd()) {
            abLoopStartFrame = null
            abLoopEndFrame = null
            sessionProvider().stop()
            appContext?.let { PlaybackService.stop(it) }
            publishSnapshot(active = false, error = null)
            return false
        }
        val activeEntry = queue.snapshot().current
        val aFrame = abLoopStartFrame
        val bFrame = abLoopEndFrame
        if (activeEntry != null && aFrame != null && bFrame != null && bFrame > aFrame) {
            openEntry(activeEntry, initialSeekFrames = aFrame, allowChaining = false)
            return true
        }
        val endedEntry = queue.snapshot().current
        val step = queue.onNaturalEnd()
        return when (step) {
            is LinearQueue.StepResult.Play -> {
                abLoopStartFrame = null
                abLoopEndFrame = null
                openEntry(step.entry)
                true
            }
            LinearQueue.StepResult.RestartCurrent -> {
                queue.snapshot().current?.let { openEntry(it) } ?: false
                true
            }
            LinearQueue.StepResult.Ended -> {
                abLoopStartFrame = null
                abLoopEndFrame = null
                publishSnapshot(active = false, error = null)
                onSessionCompletedNaturally?.invoke(endedEntry)
                false
            }
        }
    }

    fun clearSession() {
        requestGeneration++
        invalidatePreload()
        seekEpoch++
        seekJob?.cancel()
        seekJob = null
        activeChainWindows = emptyList()
        activeChainIndex = 0
        abLoopStartFrame = null
        abLoopEndFrame = null
        queue.clear()
        _state.value = LinearUiState(queue = queue.snapshot())
    }

    fun canSkip(): Boolean = queue.snapshot().hasQueue || queue.snapshot().current != null

    /** Increments request generation when an external interactive flow takes over the session. */
    fun yieldToExternalTransport() {
        requestGeneration++
        invalidatePreload()
        seekEpoch++
        seekJob?.cancel()
        seekJob = null
        activeChainWindows = emptyList()
        activeChainIndex = 0
        abLoopStartFrame = null
        abLoopEndFrame = null
        _state.value = LinearUiState(queue = queue.snapshot())
    }

    private fun applyStep(step: LinearQueue.StepResult): Boolean {
        return when (step) {
            is LinearQueue.StepResult.Play -> {
                abLoopStartFrame = null
                abLoopEndFrame = null
                openEntry(step.entry)
                true
            }
            LinearQueue.StepResult.RestartCurrent -> {
                val session = sessionProvider()
                val current = queue.snapshot().current
                if (activeChainWindows.isNotEmpty() && activeChainIndex > 0 && current != null) {
                    openEntry(current)
                } else if (session.state.value.linear && (session.state.value.playing || session.state.value.paused)) {
                    seekToFrame(0L)
                    publishSnapshot(error = null)
                } else if (current != null) {
                    openEntry(current)
                } else {
                    return false
                }
                true
            }
            LinearQueue.StepResult.Ended -> {
                seekEpoch++
                seekJob?.cancel()
                seekJob = null
                abLoopStartFrame = null
                abLoopEndFrame = null
                sessionProvider().stop()
                appContext?.let { PlaybackService.stop(it) }
                publishSnapshot(active = false, error = null)
                false
            }
        }
    }

    private fun bindSessionCallbacks() {
        val session = sessionProvider()
        session.onLinearCompleted = { handleNaturalEnd() }
        session.onLinearSegmentAdvanced = { segId, _ -> handleChainSegmentAdvanced(segId) }
        session.onLinearSeek = { frame -> seekToFrame(frame) }
        session.onSkipNext = { skipNext() }
        session.onSkipPrevious = { skipPrevious() }
        session.canSkipQueue = { canSkip() }
    }

    private fun handleChainSegmentAdvanced(segmentId: Int) {
        if (PlaybackTimers.engine.consumeAfterCurrentTrackOnNaturalEnd()) {
            abLoopStartFrame = null
            abLoopEndFrame = null
            sessionProvider().stop()
            appContext?.let { PlaybackService.stop(it) }
            publishSnapshot(active = false, error = null)
            return
        }
        val windows = activeChainWindows
        val winIdx = windows.indexOfFirst { it.segmentId == segmentId }
        if (winIdx <= activeChainIndex || winIdx !in windows.indices) return
        val targetWindow = windows[winIdx]
        val advanced = queue.advanceSilentForHandover(targetWindow.entryKey)
        if (advanced == null) {
            // Queue was mutated while playing (e.g. Play Next or Repeat Single); honor current queue state.
            activeChainWindows = emptyList()
            activeChainIndex = 0
            handleNaturalEnd()
            return
        }
        activeChainIndex = winIdx
        abLoopStartFrame = null
        abLoopEndFrame = null
        val session = sessionProvider()
        session.updateLinearWindow(
            title = advanced.title,
            totalFrames = targetWindow.displayTotalFrames,
            transportOriginFrames = targetWindow.originTransportFrame,
            artworkName = advanced.artworkName,
            courseCode = advanced.courseCode,
            subtitle = advanced.subtitle,
        )
        _state.value = _state.value.copy(
            active = true,
            current = advanced,
            queue = queue.snapshot(),
            totalFrames = targetWindow.displayTotalFrames,
            contextKind = sessionContext.contextKind,
            contextId = sessionContext.contextId,
            contextTitle = sessionContext.contextTitle,
            allowPlayNext = sessionContext.allowPlayNext,
            allowShuffle = sessionContext.allowShuffle,
            allowSingleRepeat = sessionContext.allowSingleRepeat,
            allowRepeatToggle = sessionContext.allowRepeatToggle,
            abLoopStartFrame = null,
            abLoopEndFrame = null,
            error = null,
            loading = false,
        )
        notifyTrackOpened(advanced)
        saveCurrentProgress(0L, targetWindow.displayTotalFrames, _state.value.sampleRate)
        if (winIdx == windows.lastIndex) {
            schedulePreloadNext(requestGeneration)
        }
    }

    private fun notifyTrackOpened(entry: LinearQueue.Entry) {
        onTrackOpened?.invoke(entry)
        appContext?.let { ctx ->
            PlaybackHolder.unlock().recordLinearTrackOpened(ctx, entry)
        }
    }

    private fun invalidatePreload() {
        preloadGeneration++
        preloadJob?.cancel()
        preloadJob = null
        val stale = preparedNext
        preparedNext = null
        if (stale != null) {
            scope.launch(NonCancellable + ioDispatcher) {
                stale.prepared.discard()
            }
        }
    }

    private fun refreshPreloadIfNeeded() {
        if (!_state.value.active) return
        val nextKey = queue.peekNextEntries(1).firstOrNull()?.key
        val singleLoop = queue.repeatMode == LinearQueue.RepeatMode.SINGLE
        val currentPreload = preparedNext
        if (currentPreload != null) {
            val chainValid = if (currentPreload.chainWindows.isEmpty()) {
                true
            } else {
                val expectedChain = LinearHandoverPlanner.collectChainEntries(
                    queue.peekNextEntries(1).firstOrNull() ?: return,
                    queue.peekNextEntries(LinearHandoverPlanner.MAX_CHAIN_SEGMENTS).drop(1),
                ).map { it.key }
                expectedChain == currentPreload.chainKeys
            }
            if (currentPreload.entryKey == nextKey && currentPreload.singleLoop == singleLoop && !singleLoop && chainValid) {
                return
            }
            invalidatePreload()
        }
        schedulePreloadNext(requestGeneration)
    }

    private fun consumePreparedIfMatching(
        entry: LinearQueue.Entry,
        initialSeekFrames: Long,
        allowChaining: Boolean,
    ): PreparedNextEntry? {
        if (initialSeekFrames != 0L) {
            invalidatePreload()
            return null
        }
        val candidate = preparedNext ?: return null
        val singleLoop = queue.repeatMode == LinearQueue.RepeatMode.SINGLE
        if (candidate.entryKey != entry.key || candidate.singleLoop != singleLoop) {
            invalidatePreload()
            return null
        }
        val canChain = allowChaining &&
            entry.courseMedley != null &&
            PlaybackTimers.sleepState.value.mode != SleepTimerMode.AFTER_CURRENT_TRACK
        if (candidate.chainWindows.isNotEmpty()) {
            val expectedKeys = if (canChain) {
                LinearHandoverPlanner.collectChainEntries(
                    entry,
                    queue.peekNextEntries(LinearHandoverPlanner.MAX_CHAIN_SEGMENTS - 1),
                ).map { it.key }
            } else {
                emptyList()
            }
            if (expectedKeys != candidate.chainKeys) {
                invalidatePreload()
                return null
            }
        } else if (canChain) {
            val chainEntries = LinearHandoverPlanner.collectChainEntries(
                entry,
                queue.peekNextEntries(LinearHandoverPlanner.MAX_CHAIN_SEGMENTS - 1),
            )
            if (chainEntries.size >= 2) {
                invalidatePreload()
                return null
            }
        }
        preparedNext = null
        preloadGeneration++
        preloadJob?.cancel()
        preloadJob = null
        return candidate
    }

    private fun schedulePreloadNext(generation: Long) {
        val session = sessionProvider()
        if (session.uninterruptible) return
        if (PlaybackTimers.sleepState.value.mode == SleepTimerMode.AFTER_CURRENT_TRACK) return
        if (queue.repeatMode == LinearQueue.RepeatMode.SINGLE) return
        if (activeChainWindows.isNotEmpty() && activeChainIndex < activeChainWindows.lastIndex) return
        val nextEntry = queue.peekNextEntries(1).firstOrNull() ?: return
        if (nextEntry.key == "extra:special_cup_appears") return
        if (preparedNext?.entryKey == nextEntry.key) return

        val lookupKey = nextEntry.sourceTrackKey ?: nextEntry.key
        val track = catalog.tracksByKey[lookupKey]
        val packDir = catalog.packByKey[lookupKey] ?: extraPackDir ?: return
        if (track == null &&
            nextEntry.assetSha256 == null &&
            nextEntry.overlaidOpening == null &&
            nextEntry.chainedSequence == null &&
            nextEntry.courseMedley == null
        ) {
            return
        }

        val upcomingAfterNext = queue.peekNextEntries(LinearHandoverPlanner.MAX_CHAIN_SEGMENTS).drop(1)
        val chainEntries = if (nextEntry.courseMedley != null) {
            LinearHandoverPlanner.collectChainEntries(nextEntry, upcomingAfterNext)
        } else {
            emptyList()
        }

        val myPreloadGen = ++preloadGeneration
        preloadJob?.cancel()
        preloadJob = scope.launch {
            delay(200L)
            if (!isActive || generation != requestGeneration || myPreloadGen != preloadGeneration) return@launch
            val built = withContext(ioDispatcher) {
                runCatching {
                    prepareEntryOffMain(
                        entry = nextEntry,
                        chainEntries = chainEntries,
                        packDir = packDir,
                        sha256Override = track?.primary?.sha256,
                        session = session,
                    )
                }.getOrNull()
            } ?: return@launch
            if (!isActive || generation != requestGeneration || myPreloadGen != preloadGeneration) {
                withContext(NonCancellable + ioDispatcher) {
                    built.prepared.discard()
                }
                return@launch
            }
            val old = preparedNext
            preparedNext = built
            if (old != null) {
                withContext(NonCancellable + ioDispatcher) {
                    old.prepared.discard()
                }
            }
        }
    }

    private fun prepareEntryOffMain(
        entry: LinearQueue.Entry,
        chainEntries: List<LinearQueue.Entry>,
        packDir: File,
        sha256Override: String?,
        session: PlaybackSession,
    ): PreparedNextEntry? {
        if (entry.overlaidOpening != null) {
            val op = entry.overlaidOpening
            val opBytes = readRawBwav(op.op.sha256, packDir)
            val telopBytes = readRawBwav(op.telop.sha256, packDir)
            val compiled = com.mylittleblover.mkwplayer.interactive.ExtraInteractiveCompilers
                .compileOverlaidOpeningGraph(op, opBytes, telopBytes)
            val prep = session.prepareFlow(
                title = entry.title,
                packet = compiled.packet,
                assets = compiled.assets,
                frames = compiled.totalFrames,
                startSegment = 100,
                streamed = false,
                linear = true,
            )
            return (prep as? PlaybackSession.PrepareResult.Ok)?.let {
                PreparedNextEntry(
                    entryKey = entry.key,
                    chainKeys = emptyList(),
                    singleLoop = false,
                    packDirPath = packDir.path,
                    prepared = it.prepared,
                    totalFrames = compiled.totalFrames,
                    sampleRate = op.op.sampleRate,
                )
            }
        }
        if (entry.chainedSequence != null) {
            val chained = entry.chainedSequence
            val bytesByStem = buildMap {
                chained.parts.forEach { part ->
                    if (part.asset.stem !in this) {
                        put(part.asset.stem, readRawBwav(part.asset.sha256, packDir))
                    }
                }
            }
            val compiled = com.mylittleblover.mkwplayer.interactive.ExtraInteractiveCompilers
                .compileChainedSequenceGraph(chained, bytesByStem)
            val sampleRate = chained.parts.firstOrNull()?.asset?.sampleRate ?: 48_000
            val prep = session.prepareFlow(
                title = entry.title,
                packet = compiled.packet,
                assets = compiled.assets,
                frames = compiled.totalFrames,
                startSegment = 100,
                streamed = false,
                linear = true,
            )
            return (prep as? PlaybackSession.PrepareResult.Ok)?.let {
                PreparedNextEntry(
                    entryKey = entry.key,
                    chainKeys = emptyList(),
                    singleLoop = false,
                    packDirPath = packDir.path,
                    prepared = it.prepared,
                    totalFrames = compiled.totalFrames,
                    sampleRate = sampleRate,
                )
            }
        }
        if (entry.courseMedley != null) {
            if (chainEntries.size >= 2) {
                val (filesByStem, fallbackBytesByStem) = resolveStemsOnDisk(
                    chainEntries.flatMap { item ->
                        item.courseMedley?.stemDigests?.entries?.map { it.key to it.value }.orEmpty()
                    },
                    packDir,
                )
                val pairs = chainEntries.map { item -> item.key to item.courseMedley!! }
                val chain = com.mylittleblover.mkwplayer.interactive.CourseMedleyChainCompiler
                    .compileChainFromFiles(pairs, filesByStem, fallbackBytesByStem)
                val prep = session.prepareFlowFromFiles(
                    title = entry.title,
                    packet = chain.packet,
                    assets = chain.assets,
                    frames = chain.firstTotalFrames,
                    startSegment = 100,
                    streamed = true,
                    autoAdvance = chain.autoAdvance,
                    prequeueInitial = true,
                    linear = true,
                )
                return (prep as? PlaybackSession.PrepareResult.Ok)?.let {
                    PreparedNextEntry(
                        entryKey = entry.key,
                        chainKeys = chainEntries.map { c -> c.key },
                        singleLoop = false,
                        packDirPath = packDir.path,
                        prepared = it.prepared,
                        totalFrames = chain.firstTotalFrames,
                        sampleRate = chain.sampleRate,
                        chainWindows = chain.windows,
                    )
                }
            }
            val resolved = entry.courseMedley
            val (filesByStem, fallbackBytesByStem) = resolveStemsOnDisk(
                resolved.stemDigests.entries.map { it.key to it.value },
                packDir,
            )
            val compiled = com.mylittleblover.mkwplayer.interactive.CourseMedleyCompiler.compileGraphFromFiles(
                medley = resolved.medley,
                sampleRate = resolved.sampleRate,
                filesByStem = filesByStem,
                knownDigestsHex = resolved.stemDigests,
                fallbackBytesByStem = fallbackBytesByStem,
            )
            val prep = session.prepareFlowFromFiles(
                title = entry.title,
                packet = compiled.packet,
                assets = compiled.assets,
                frames = compiled.totalFrames,
                startSegment = 100,
                streamed = true,
                linear = true,
            )
            return (prep as? PlaybackSession.PrepareResult.Ok)?.let {
                PreparedNextEntry(
                    entryKey = entry.key,
                    chainKeys = emptyList(),
                    singleLoop = false,
                    packDirPath = packDir.path,
                    prepared = it.prepared,
                    totalFrames = compiled.totalFrames,
                    sampleRate = resolved.sampleRate,
                )
            }
        }
        val sha256 = sha256Override ?: entry.assetSha256 ?: return null
        val info = inspectPlayableAudioFile(sha256, packDir, entry.loopStart, entry.loopEnd).getOrNull() ?: return null
        val prep = session.prepareLinearFile(
            title = entry.title,
            file = info.file,
            assetId = sha256,
            frames = info.frames,
            loop = false,
            streamed = true,
            loopStart = info.loopStart,
            loopEnd = info.loopEnd,
        )
        return (prep as? PlaybackSession.PrepareResult.Ok)?.let {
            PreparedNextEntry(
                entryKey = entry.key,
                chainKeys = emptyList(),
                singleLoop = false,
                packDirPath = packDir.path,
                prepared = it.prepared,
                totalFrames = info.frames,
                sampleRate = info.sampleRate,
                loopStart = info.loopStart ?: -1L,
                loopEnd = info.loopEnd ?: -1L,
            )
        }
    }

    private fun openEntry(
        entry: LinearQueue.Entry,
        initialSeekFrames: Long = 0L,
        allowChaining: Boolean = true,
    ) {
        val generation = ++requestGeneration
        seekEpoch++
        seekJob?.cancel()
        seekJob = null
        activeChainWindows = emptyList()
        activeChainIndex = 0

        val preloadedHit = consumePreparedIfMatching(entry, initialSeekFrames, allowChaining)
        if (preloadedHit != null) {
            val session = sessionProvider()
            activeLoopStart = preloadedHit.loopStart
            activeLoopEnd = preloadedHit.loopEnd
            activeChainWindows = preloadedHit.chainWindows
            activeChainIndex = 0
            session.updateMetadata(
                artworkName = entry.artworkName,
                courseCode = entry.courseCode,
                packDirPath = preloadedHit.packDirPath,
                subtitle = entry.subtitle,
            )
            session.commitPrepared(preloadedHit.prepared)
            _state.value = LinearUiState(
                active = true,
                current = entry,
                queue = queue.snapshot(),
                totalFrames = preloadedHit.totalFrames,
                sampleRate = preloadedHit.sampleRate,
                error = null,
                loading = false,
                contextKind = sessionContext.contextKind,
                contextId = sessionContext.contextId,
                contextTitle = sessionContext.contextTitle,
                allowPlayNext = sessionContext.allowPlayNext,
                allowShuffle = sessionContext.allowShuffle,
                allowSingleRepeat = sessionContext.allowSingleRepeat,
                allowRepeatToggle = sessionContext.allowRepeatToggle,
                abLoopStartFrame = abLoopStartFrame,
                abLoopEndFrame = abLoopEndFrame,
            )
            notifyTrackOpened(entry)
            saveCurrentProgress(0L, preloadedHit.totalFrames, preloadedHit.sampleRate)
            appContext?.let { PlaybackService.start(it) }
            schedulePreloadNext(generation)
            return
        }

        publishSnapshot(active = true, error = null, loading = true)

        scope.launch {
            PlaybackService.awaitForegroundPromotion()
            if (generation != requestGeneration) return@launch

            val lookupKey = entry.sourceTrackKey ?: entry.key
            val track = catalog.tracksByKey[lookupKey]
            val packDir = catalog.packByKey[lookupKey] ?: extraPackDir
            if (packDir == null || (
                    track == null &&
                    entry.assetSha256 == null &&
                    entry.overlaidOpening == null &&
                    entry.chainedSequence == null &&
                    entry.courseMedley == null
                )
            ) {
                publishSnapshot(active = false, error = "No installed pack carries this track.", loading = false)
                return@launch
            }

            val session = sessionProvider()
            if (entry.overlaidOpening != null) {
                val op = entry.overlaidOpening
                openCompiledFlowEntry(
                    entry = entry,
                    session = session,
                    generation = generation,
                    sampleRate = op.op.sampleRate,
                    streamed = false,
                    initialSeekFrames = initialSeekFrames,
                ) {
                    val opBytes = readRawBwav(op.op.sha256, packDir)
                    val telopBytes = readRawBwav(op.telop.sha256, packDir)
                    com.mylittleblover.mkwplayer.interactive.ExtraInteractiveCompilers.compileOverlaidOpeningGraph(op, opBytes, telopBytes)
                }
                return@launch
            }

            if (entry.chainedSequence != null) {
                val chained = entry.chainedSequence
                openCompiledFlowEntry(
                    entry = entry,
                    session = session,
                    generation = generation,
                    sampleRate = chained.parts.firstOrNull()?.asset?.sampleRate ?: 48000,
                    streamed = false,
                    initialSeekFrames = initialSeekFrames,
                ) {
                    val bytesByStem = buildMap {
                        chained.parts.forEach { part ->
                            if (part.asset.stem !in this) {
                                put(part.asset.stem, readRawBwav(part.asset.sha256, packDir))
                            }
                        }
                    }
                    com.mylittleblover.mkwplayer.interactive.ExtraInteractiveCompilers.compileChainedSequenceGraph(chained, bytesByStem)
                }
                return@launch
            }

            if (entry.courseMedley != null) {
                val resolved = entry.courseMedley
                val chainEntries = if (allowChaining && PlaybackTimers.sleepState.value.mode != SleepTimerMode.AFTER_CURRENT_TRACK) {
                    LinearHandoverPlanner.collectChainEntries(
                        entry,
                        queue.peekNextEntries(LinearHandoverPlanner.MAX_CHAIN_SEGMENTS - 1),
                    )
                } else {
                    listOf(entry)
                }
                if (chainEntries.size >= 2) {
                    openChainedMedleyEntries(
                        chainEntries = chainEntries,
                        session = session,
                        generation = generation,
                        packDir = packDir,
                        initialSeekFrames = initialSeekFrames,
                    )
                    return@launch
                }
                openPreparedFlowEntry(
                    entry = entry,
                    generation = generation,
                    sampleRate = resolved.sampleRate,
                ) {
                    val (filesByStem, fallbackBytesByStem) = resolveStemsOnDisk(
                        resolved.stemDigests.entries.map { it.key to it.value },
                        packDir,
                    )
                    val compiled = com.mylittleblover.mkwplayer.interactive.CourseMedleyCompiler.compileGraphFromFiles(
                        medley = resolved.medley,
                        sampleRate = resolved.sampleRate,
                        filesByStem = filesByStem,
                        knownDigestsHex = resolved.stemDigests,
                        fallbackBytesByStem = fallbackBytesByStem,
                    )
                    val clampedSeek = initialSeekFrames.coerceIn(0L, (compiled.totalFrames - 1L).coerceAtLeast(0L))
                    Triple(
                        compiled.totalFrames,
                        clampedSeek,
                        session.prepareFlowFromFiles(
                            title = entry.title,
                            packet = compiled.packet,
                            assets = compiled.assets,
                            frames = compiled.totalFrames,
                            startSegment = 100,
                            streamed = true,
                            linear = true,
                            initialSeekFrames = clampedSeek,
                        ),
                    )
                }
                return@launch
            }

            val sha256 = track?.primary?.sha256 ?: entry.assetSha256!!
            val singleLoop = queue.repeatMode == LinearQueue.RepeatMode.SINGLE
            val preparedResult = withContext(ioDispatcher) {
                inspectPlayableAudioFile(sha256, packDir, entry.loopStart, entry.loopEnd).map { result ->
                    val clampedSeek = initialSeekFrames.coerceIn(0L, (result.frames - 1L).coerceAtLeast(0L))
                    result to session.prepareLinearFile(
                        title = entry.title,
                        file = result.file,
                        assetId = sha256,
                        frames = result.frames,
                        loop = singleLoop,
                        streamed = true,
                        loopStart = result.loopStart,
                        loopEnd = result.loopEnd,
                        initialSeekFrames = clampedSeek,
                    )
                }
            }
            val (result, prep) = preparedResult.getOrElse { problem ->
                if (generation == requestGeneration) {
                    publishSnapshot(active = false, error = problem.message ?: "Could not read audio.", loading = false)
                }
                return@launch
            }
            when (prep) {
                is PlaybackSession.PrepareResult.Failed -> {
                    if (generation == requestGeneration) {
                        publishSnapshot(active = false, error = prep.reason, loading = false)
                    }
                    return@launch
                }
                is PlaybackSession.PrepareResult.Ok -> {
                    if (generation != requestGeneration) {
                        withContext(kotlinx.coroutines.NonCancellable + ioDispatcher) { prep.prepared.discard() }
                        return@launch
                    }
                    activeLoopStart = result.loopStart ?: -1L
                    activeLoopEnd = result.loopEnd ?: -1L
                    session.updateMetadata(
                        artworkName = entry.artworkName,
                        courseCode = entry.courseCode,
                        packDirPath = packDir.path,
                        subtitle = entry.subtitle,
                    )
                    session.commitPrepared(prep.prepared)
                    val aFrame = abLoopStartFrame
                    val bFrame = abLoopEndFrame
                    if (aFrame != null && bFrame != null && bFrame > aFrame) {
                        session.setLinearRepeat(true, aFrame, bFrame)
                    } else {
                        val desiredSingleLoop = queue.repeatMode == LinearQueue.RepeatMode.SINGLE && sessionContext.allowSingleRepeat
                        if (desiredSingleLoop != singleLoop) {
                            session.setLinearRepeat(desiredSingleLoop, activeLoopStart, activeLoopEnd)
                        }
                    }
                }
            }

            _state.value = LinearUiState(
                active = true,
                current = entry,
                queue = queue.snapshot(),
                totalFrames = result.frames,
                sampleRate = result.sampleRate,
                error = null,
                loading = false,
                contextKind = sessionContext.contextKind,
                contextId = sessionContext.contextId,
                contextTitle = sessionContext.contextTitle,
                allowPlayNext = sessionContext.allowPlayNext,
                allowShuffle = sessionContext.allowShuffle,
                allowSingleRepeat = sessionContext.allowSingleRepeat,
                allowRepeatToggle = sessionContext.allowRepeatToggle,
                abLoopStartFrame = abLoopStartFrame,
                abLoopEndFrame = abLoopEndFrame,
            )
            notifyTrackOpened(entry)
            val savedSeek = initialSeekFrames.coerceIn(0L, (result.frames - 1L).coerceAtLeast(0L))
            saveCurrentProgress(savedSeek, result.frames, result.sampleRate)
            appContext?.let { PlaybackService.start(it) }
            schedulePreloadNext(generation)
        }
    }

    private suspend fun openChainedMedleyEntries(
        chainEntries: List<LinearQueue.Entry>,
        session: PlaybackSession,
        generation: Long,
        packDir: File,
        initialSeekFrames: Long = 0L,
    ) {
        val first = chainEntries.first()
        val preparedResult = withContext(ioDispatcher) {
            runCatching {
                val (filesByStem, fallbackBytesByStem) = resolveStemsOnDisk(
                    chainEntries.flatMap { item ->
                        item.courseMedley?.stemDigests?.entries?.map { it.key to it.value }.orEmpty()
                    },
                    packDir,
                )
                val pairs = chainEntries.map { item -> item.key to item.courseMedley!! }
                val chain = com.mylittleblover.mkwplayer.interactive.CourseMedleyChainCompiler
                    .compileChainFromFiles(pairs, filesByStem, fallbackBytesByStem)
                val maxChainSeek = chain.windows.firstOrNull()?.handoverExitFrame?.minus(1L)?.coerceAtLeast(0L)
                    ?: (chain.firstTotalFrames - 1L).coerceAtLeast(0L)
                val clampedSeek = initialSeekFrames.coerceIn(0L, maxChainSeek)
                Triple(
                    chain,
                    clampedSeek,
                    session.prepareFlowFromFiles(
                        title = first.title,
                        packet = chain.packet,
                        assets = chain.assets,
                        frames = chain.firstTotalFrames,
                        startSegment = 100,
                        streamed = true,
                        autoAdvance = chain.autoAdvance,
                        prequeueInitial = clampedSeek == 0L,
                        linear = true,
                        initialSeekFrames = clampedSeek,
                    ),
                )
            }
        }
        val (chain, clampedSeek, prep) = preparedResult.getOrElse { problem ->
            if (generation == requestGeneration) {
                publishSnapshot(active = false, error = problem.message ?: "Could not read audio.", loading = false)
            }
            return
        }
        when (prep) {
            is PlaybackSession.PrepareResult.Failed -> {
                if (generation == requestGeneration) {
                    publishSnapshot(active = false, error = prep.reason, loading = false)
                }
                return
            }
            is PlaybackSession.PrepareResult.Ok -> {
                if (generation != requestGeneration) {
                    withContext(kotlinx.coroutines.NonCancellable + ioDispatcher) { prep.prepared.discard() }
                    return
                }
                activeLoopStart = -1L
                activeLoopEnd = -1L
                activeChainWindows = chain.windows
                activeChainIndex = 0
                val firstEntry = chainEntries.first()
                session.updateMetadata(
                    artworkName = firstEntry.artworkName,
                    courseCode = firstEntry.courseCode,
                    packDirPath = packDir.path,
                    subtitle = firstEntry.subtitle,
                )
                session.commitPrepared(prep.prepared)
            }
        }
        _state.value = LinearUiState(
            active = true,
            current = first,
            queue = queue.snapshot(),
            totalFrames = chain.firstTotalFrames,
            sampleRate = chain.sampleRate,
            error = null,
            loading = false,
            contextKind = sessionContext.contextKind,
            contextId = sessionContext.contextId,
            contextTitle = sessionContext.contextTitle,
            allowPlayNext = sessionContext.allowPlayNext,
            allowShuffle = sessionContext.allowShuffle,
            allowSingleRepeat = sessionContext.allowSingleRepeat,
            allowRepeatToggle = sessionContext.allowRepeatToggle,
            abLoopStartFrame = abLoopStartFrame,
            abLoopEndFrame = abLoopEndFrame,
        )
        notifyTrackOpened(first)
        saveCurrentProgress(clampedSeek, chain.firstTotalFrames, chain.sampleRate)
        appContext?.let { PlaybackService.start(it) }
        schedulePreloadNext(generation)
    }

    private suspend fun openCompiledFlowEntry(
        entry: LinearQueue.Entry,
        session: PlaybackSession,
        generation: Long,
        sampleRate: Int,
        streamed: Boolean,
        initialSeekFrames: Long = 0L,
        compile: () -> com.mylittleblover.mkwplayer.interactive.ExtraInteractiveCompilers.CompiledLinearGraph,
    ) {
        openPreparedFlowEntry(
            entry = entry,
            generation = generation,
            sampleRate = sampleRate,
        ) {
            val compiled = compile()
            val clampedSeek = initialSeekFrames.coerceIn(0L, (compiled.totalFrames - 1L).coerceAtLeast(0L))
            Triple(
                compiled.totalFrames,
                clampedSeek,
                session.prepareFlow(
                    title = entry.title,
                    packet = compiled.packet,
                    assets = compiled.assets,
                    frames = compiled.totalFrames,
                    startSegment = 100,
                    streamed = streamed,
                    linear = true,
                    initialSeekFrames = clampedSeek,
                ),
            )
        }
    }

    private suspend fun openPreparedFlowEntry(
        entry: LinearQueue.Entry,
        generation: Long,
        sampleRate: Int,
        prepareOffMain: () -> Triple<Long, Long, PlaybackSession.PrepareResult>,
    ) {
        val session = sessionProvider()
        val preparedResult = withContext(ioDispatcher) {
            runCatching { prepareOffMain() }
        }
        val (totalFrames, clampedSeek, prep) = preparedResult.getOrElse { problem ->
            if (generation == requestGeneration) {
                publishSnapshot(active = false, error = problem.message ?: "Could not read audio.", loading = false)
            }
            return
        }
        when (prep) {
            is PlaybackSession.PrepareResult.Failed -> {
                if (generation == requestGeneration) {
                    publishSnapshot(active = false, error = prep.reason, loading = false)
                }
                return
            }
            is PlaybackSession.PrepareResult.Ok -> {
                if (generation != requestGeneration) {
                    withContext(kotlinx.coroutines.NonCancellable + ioDispatcher) { prep.prepared.discard() }
                    return
                }
                activeLoopStart = -1L
                activeLoopEnd = -1L
                session.updateMetadata(
                    artworkName = entry.artworkName,
                    courseCode = entry.courseCode,
                    packDirPath = extraPackDir?.path,
                    subtitle = entry.subtitle,
                )
                session.commitPrepared(prep.prepared)
            }
        }
        _state.value = LinearUiState(
            active = true,
            current = entry,
            queue = queue.snapshot(),
            totalFrames = totalFrames,
            sampleRate = sampleRate,
            error = null,
            loading = false,
            contextKind = sessionContext.contextKind,
            contextId = sessionContext.contextId,
            contextTitle = sessionContext.contextTitle,
            allowPlayNext = sessionContext.allowPlayNext,
            allowShuffle = sessionContext.allowShuffle,
            allowSingleRepeat = sessionContext.allowSingleRepeat,
            allowRepeatToggle = sessionContext.allowRepeatToggle,
            abLoopStartFrame = abLoopStartFrame,
            abLoopEndFrame = abLoopEndFrame,
        )
        notifyTrackOpened(entry)
        saveCurrentProgress(clampedSeek, totalFrames, sampleRate)
        appContext?.let { PlaybackService.start(it) }
        schedulePreloadNext(generation)
    }

    private fun publishSnapshot(
        active: Boolean = _state.value.active,
        error: String? = _state.value.error,
        loading: Boolean = false,
    ) {
        val snap = queue.snapshot()
        _state.value = _state.value.copy(
            active = active,
            current = snap.current,
            queue = snap,
            error = error,
            loading = loading,
            contextKind = sessionContext.contextKind,
            contextId = sessionContext.contextId,
            contextTitle = sessionContext.contextTitle,
            allowPlayNext = sessionContext.allowPlayNext,
            allowShuffle = sessionContext.allowShuffle,
            allowSingleRepeat = sessionContext.allowSingleRepeat,
            allowRepeatToggle = sessionContext.allowRepeatToggle,
            abLoopStartFrame = abLoopStartFrame,
            abLoopEndFrame = abLoopEndFrame,
        )
        if (!loading && active) {
            val s = sessionProvider().state.value
            saveCurrentProgress(s.positionFrames, _state.value.totalFrames, _state.value.sampleRate)
        }
    }

    private data class LinearFileInfo(
        val file: File,
        val frames: Long,
        val sampleRate: Int,
        val loopStart: Long? = null,
        val loopEnd: Long? = null,
    )

    private fun validateBwavFileHeader(file: File) {
        val info = when (val parsed = BwavHeader.readFile(file)) {
            is BwavHeader.Result.Ok -> parsed.info
            is BwavHeader.Result.Rejected -> error(parsed.reason)
        }
        if (!PackCoverage.isDecodable(info.codecKind)) {
            error("Codec ${info.codecKind} is not decodable by this build.")
        }
    }

    private fun resolveStemsOnDisk(
        stemDigests: Iterable<Pair<String, String>>,
        packDir: File,
    ): Pair<Map<String, File>, Map<String, ByteArray>> {
        val filesByStem = linkedMapOf<String, File>()
        val fallbackBytesByStem = linkedMapOf<String, ByteArray>()
        stemDigests.forEach { (stem, sha) ->
            if (stem in filesByStem || stem in fallbackBytesByStem) return@forEach
            val file = File(packDir, "audio/$sha")
            if (file.isFile) {
                validateBwavFileHeader(file)
                filesByStem[stem] = file
            } else if (sha.equals(com.mylittleblover.mkwplayer.interactive.FinalLapFanfareCompiler.SHA256, ignoreCase = true)) {
                fallbackBytesByStem[stem] = readRawBwav(sha, packDir)
            } else {
                error("Missing audio file for track.")
            }
        }
        return filesByStem to fallbackBytesByStem
    }

    private fun readRawBwav(sha256: String, packDir: File): ByteArray {
        val file = File(packDir, "audio/$sha256")
        val bytes = if (file.isFile) {
            file.readBytes()
        } else if (sha256.equals(com.mylittleblover.mkwplayer.interactive.FinalLapFanfareCompiler.SHA256, ignoreCase = true)) {
            runCatching {
                appContext?.assets?.open("audio/${com.mylittleblover.mkwplayer.interactive.FinalLapFanfareCompiler.ASSET}")
                    ?.use { it.readBytes() }
            }.getOrNull() ?: com.mylittleblover.mkwplayer.interactive.FinalLapFanfareCompiler.silentFallbackBwav()
        } else {
            error("Missing audio file for track.")
        }
        val info = when (val parsed = BwavHeader.read(bytes)) {
            is BwavHeader.Result.Ok -> parsed.info
            is BwavHeader.Result.Rejected -> error(parsed.reason)
        }
        if (!PackCoverage.isDecodable(info.codecKind)) {
            error("Codec ${info.codecKind} is not decodable by this build.")
        }
        return bytes
    }

    private fun inspectPlayableAudioFile(
        sha256: String,
        packDir: File,
        fallbackLoopStart: Long? = null,
        fallbackLoopEnd: Long? = null,
    ): Result<LinearFileInfo> = runCatching {
        val file = File(packDir, "audio/$sha256")
        if (!file.isFile) error("Missing audio file for track.")
        val info = when (val parsed = BwavHeader.readFile(file)) {
            is BwavHeader.Result.Ok -> parsed.info
            is BwavHeader.Result.Rejected -> error(parsed.reason)
        }
        if (!PackCoverage.isDecodable(info.codecKind)) {
            error("Codec ${info.codecKind} is not decodable by this build.")
        }
        val loopStart = info.loopStart?.toLong() ?: fallbackLoopStart
        val loopEnd = info.loopEnd?.toLong() ?: fallbackLoopEnd
        LinearFileInfo(file, info.frames.toLong(), info.sampleRate, loopStart, loopEnd)
    }

    companion object {
        const val PREVIOUS_RESTART_MS = 3_000L

        fun entryFor(item: JukeboxCatalog.TrackItem, game: JukeboxCatalog.Game, languageTag: String): LinearQueue.Entry {
            val gameName = game.localizedName(languageTag)
            val arrangement = item.localizedArrangement(languageTag)
            return LinearQueue.Entry(
                key = item.key,
                title = item.localizedDisplayName(languageTag),
                subtitle = if (game.yearLabel.isNotBlank()) "${game.yearLabel} · $gameName" else gameName,
                sourceType = LinearSessionStore.SourceType.JUKEBOX,
                gameId = game.id,
                gameTitle = gameName,
                yearLabel = game.yearLabel.ifBlank { null },
                arrangement = arrangement,
                ostIndex = item.ostIndex,
                durationMs = item.durationMs,
                artworkName = item.ostIndex?.let { "t_%03d.webp".format(java.util.Locale.ROOT, it) },
                assetSha256 = item.track.primary.sha256,
                sourceTrackKey = item.track.key,
            )
        }
    }
}
