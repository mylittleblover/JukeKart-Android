package com.mylittleblover.mkwplayer.player

import android.content.Context
import com.mylittleblover.mkwplayer.audio.BwavHeader
import com.mylittleblover.mkwplayer.pack.PackCoverage
import com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) {

    data class LinearUiState(
        val active: Boolean = false,
        val current: LinearQueue.Entry? = null,
        val queue: LinearQueue.Snapshot = LinearQueue.Snapshot(),
        val totalFrames: Long = 0L,
        val sampleRate: Int = 0,
        val error: String? = null,
        val loading: Boolean = false,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
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

    private var requestGeneration: Long = 0L

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
    ) {
        val started = queue.startSession(entries, startIndex, shuffleOverride) ?: return
        bindSessionCallbacks()
        openEntry(started)
    }

    fun startExtraQueue(
        entries: List<LinearQueue.Entry>,
        startIndex: Int,
        packDir: File,
        repeatMode: LinearQueue.RepeatMode = LinearQueue.RepeatMode.LIST,
        shuffleOverride: Boolean? = null,
    ) {
        extraPackDir = packDir
        queue.setRepeatMode(repeatMode)
        val started = queue.startSession(entries, startIndex, shuffleOverride) ?: return
        bindSessionCallbacks()
        openEntry(started)
    }

    fun enqueueNext(entry: LinearQueue.Entry) {
        val wasEmpty = queue.snapshot().current == null
        queue.enqueueNext(entry)
        bindSessionCallbacks()
        if (wasEmpty) {
            queue.snapshot().current?.let { openEntry(it) }
        } else {
            publishSnapshot(error = null)
        }
    }

    fun removeTempAt(index: Int) {
        if (queue.removeTempAt(index)) {
            publishSnapshot(error = null)
        }
    }

    fun jumpToTemp(index: Int) {
        val chosen = queue.jumpToTemp(index) ?: return
        openEntry(chosen)
    }

    fun jumpToUpcoming(index: Int) {
        val chosen = queue.jumpToUpcoming(index) ?: return
        openEntry(chosen)
    }

    fun toggleShuffle() {
        queue.setShuffle(!queue.shuffle)
        publishSnapshot(error = null)
    }

    fun cycleRepeatMode() {
        val mode = queue.cycleRepeatMode()
        val session = sessionProvider()
        val current = queue.snapshot().current
        if (session.state.value.linear && session.state.value.active &&
            current?.overlaidOpening == null && current?.chainedSequence == null && current?.courseMedley == null
        ) {
            session.setLinearRepeat(mode == LinearQueue.RepeatMode.SINGLE, activeLoopStart, activeLoopEnd)
        }
        publishSnapshot(error = null)
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
        val step = queue.onNaturalEnd()
        return when (step) {
            is LinearQueue.StepResult.Play -> {
                openEntry(step.entry)
                true
            }
            LinearQueue.StepResult.RestartCurrent -> {
                queue.snapshot().current?.let { openEntry(it) } ?: false
                true
            }
            LinearQueue.StepResult.Ended -> {
                publishSnapshot(active = false, error = null)
                false
            }
        }
    }

    fun clearSession() {
        requestGeneration++
        queue.clear()
        _state.value = LinearUiState(queue = queue.snapshot())
    }

    fun canSkip(): Boolean = queue.snapshot().hasQueue || queue.snapshot().current != null

    /** Increments request generation when an external interactive flow takes over the session. */
    fun yieldToExternalTransport() {
        requestGeneration++
        _state.value = LinearUiState(queue = queue.snapshot())
    }

    private fun applyStep(step: LinearQueue.StepResult): Boolean {
        return when (step) {
            is LinearQueue.StepResult.Play -> {
                openEntry(step.entry)
                true
            }
            LinearQueue.StepResult.RestartCurrent -> {
                val session = sessionProvider()
                if (session.state.value.linear && (session.state.value.playing || session.state.value.paused)) {
                    session.seek(0L)
                    publishSnapshot(error = null)
                } else {
                    val current = queue.snapshot().current ?: return false
                    openEntry(current)
                }
                true
            }
            LinearQueue.StepResult.Ended -> {
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
        session.onSkipNext = { skipNext() }
        session.onSkipPrevious = { skipPrevious() }
        session.canSkipQueue = { canSkip() }
    }

    private fun openEntry(entry: LinearQueue.Entry) {
        val generation = ++requestGeneration
        publishSnapshot(active = true, error = null, loading = true)

        scope.launch {
            PlaybackService.awaitForegroundPromotion()
            if (generation != requestGeneration) return@launch

            val track = catalog.tracksByKey[entry.key]
            val packDir = catalog.packByKey[entry.key] ?: extraPackDir
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
                openCompiledFlowEntry(
                    entry = entry,
                    session = session,
                    generation = generation,
                    sampleRate = resolved.sampleRate,
                    streamed = true,
                ) {
                    val bytesByStem = resolved.stemDigests.mapValues { (_, sha) ->
                        readRawBwav(sha, packDir)
                    }
                    com.mylittleblover.mkwplayer.interactive.CourseMedleyCompiler.compileGraph(
                        medley = resolved.medley,
                        sampleRate = resolved.sampleRate,
                        bytesByStem = bytesByStem,
                    )
                }
                return@launch
            }

            val sha256 = track?.primary?.sha256 ?: entry.assetSha256!!
            val singleLoop = queue.repeatMode == LinearQueue.RepeatMode.SINGLE
            val preparedResult = withContext(Dispatchers.IO) {
                readPlayableAudio(sha256, packDir, entry.loopStart, entry.loopEnd).map { result ->
                    result to session.prepareLinear(
                        title = entry.title,
                        audio = result.bytes,
                        assetId = sha256,
                        frames = result.frames,
                        loop = singleLoop,
                        streamed = true,
                        loopStart = result.loopStart,
                        loopEnd = result.loopEnd,
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
                        withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { prep.prepared.discard() }
                        return@launch
                    }
                    activeLoopStart = result.loopStart ?: -1L
                    activeLoopEnd = result.loopEnd ?: -1L
                    session.commitPrepared(prep.prepared)
                    val desiredSingleLoop = queue.repeatMode == LinearQueue.RepeatMode.SINGLE
                    if (desiredSingleLoop != singleLoop) {
                        session.setLinearRepeat(desiredSingleLoop, activeLoopStart, activeLoopEnd)
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
            )

            appContext?.let { PlaybackService.start(it) }
        }
    }

    private suspend fun openCompiledFlowEntry(
        entry: LinearQueue.Entry,
        session: PlaybackSession,
        generation: Long,
        sampleRate: Int,
        streamed: Boolean,
        compile: () -> com.mylittleblover.mkwplayer.interactive.ExtraInteractiveCompilers.CompiledLinearGraph,
    ) {
        val preparedResult = withContext(Dispatchers.IO) {
            runCatching {
                val compiled = compile()
                compiled.totalFrames to session.prepareFlow(
                    title = entry.title,
                    packet = compiled.packet,
                    assets = compiled.assets,
                    frames = compiled.totalFrames,
                    startSegment = 100,
                    streamed = streamed,
                    linear = true,
                )
            }
        }
        val (totalFrames, prep) = preparedResult.getOrElse { problem ->
            publishSnapshot(active = false, error = problem.message ?: "Could not read audio.", loading = false)
            return
        }
        when (prep) {
            is PlaybackSession.PrepareResult.Failed -> {
                publishSnapshot(active = false, error = prep.reason, loading = false)
                return
            }
            is PlaybackSession.PrepareResult.Ok -> {
                if (generation != requestGeneration) {
                    withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { prep.prepared.discard() }
                    return
                }
                activeLoopStart = -1L
                activeLoopEnd = -1L
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
        )
        appContext?.let { PlaybackService.start(it) }
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
        )
    }

    private data class LoadedAudio(
        val bytes: ByteArray,
        val frames: Long,
        val sampleRate: Int,
        val loopStart: Long? = null,
        val loopEnd: Long? = null,
    )

    private fun readRawBwav(sha256: String, packDir: File): ByteArray {
        val file = File(packDir, "audio/$sha256")
        if (!file.isFile) error("Missing audio file for track.")
        val bytes = file.readBytes()
        val info = when (val parsed = BwavHeader.read(bytes)) {
            is BwavHeader.Result.Ok -> parsed.info
            is BwavHeader.Result.Rejected -> error(parsed.reason)
        }
        if (!PackCoverage.isDecodable(info.codecKind)) {
            error("Codec ${info.codecKind} is not decodable by this build.")
        }
        return bytes
    }

    private fun readPlayableAudio(
        sha256: String,
        packDir: File,
        fallbackLoopStart: Long? = null,
        fallbackLoopEnd: Long? = null,
    ): Result<LoadedAudio> = runCatching {
        val file = File(packDir, "audio/$sha256")
        if (!file.isFile) error("Missing audio file for track.")
        val bytes = file.readBytes()
        val info = when (val parsed = BwavHeader.read(bytes)) {
            is BwavHeader.Result.Ok -> parsed.info
            is BwavHeader.Result.Rejected -> error(parsed.reason)
        }
        if (!PackCoverage.isDecodable(info.codecKind)) {
            error("Codec ${info.codecKind} is not decodable by this build.")
        }
        val loopStart = info.loopStart?.toLong() ?: fallbackLoopStart
        val loopEnd = info.loopEnd?.toLong() ?: fallbackLoopEnd
        LoadedAudio(bytes, info.frames.toLong(), info.sampleRate, loopStart, loopEnd)
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
                gameId = game.id,
                gameTitle = gameName,
                yearLabel = game.yearLabel.ifBlank { null },
                arrangement = arrangement,
                ostIndex = item.ostIndex,
                durationMs = item.durationMs,
            )
        }
    }
}
