package com.mylittleblover.mkwplayer.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class SleepTimerMode {
    OFF,
    COUNTDOWN,
    AFTER_CURRENT_TRACK,
}

internal data class SleepTimerState(
    val mode: SleepTimerMode = SleepTimerMode.OFF,
    val totalMs: Long = 0L,
    val remainingMs: Long = 0L,
) {
    val active: Boolean
        get() = mode != SleepTimerMode.OFF
}

internal sealed interface InteractiveTimedAction {
    data class FadeStop(val fadeSeconds: Int = 3) : InteractiveTimedAction
    data object Advance : InteractiveTimedAction
    data object ToggleFast : InteractiveTimedAction
    data class TakeOutro(val number: Int? = null) : InteractiveTimedAction
    data class SelectRegion(val region: Int, val label: String) : InteractiveTimedAction
    data class SelectVersion(val version: Int, val label: String) : InteractiveTimedAction
    data object ToggleSpeakerOverlay : InteractiveTimedAction
}

internal data class InteractiveTimerState(
    val active: Boolean = false,
    val totalMs: Long = 0L,
    val remainingMs: Long = 0L,
    val action: InteractiveTimedAction? = null,
    val courseKey: String? = null,
    val pausedByTransport: Boolean = false,
)

internal data class TimerTickOutcome(
    val sleepExpired: Boolean = false,
    val triggeredInteractiveAction: InteractiveTimedAction? = null,
)

/**
 * Deterministic engine driving both the global Sleep Timer and the Interactive
 * Scheduled Transition / Stop Timer. Injectable monotonic [clockMs] makes it
 * testable in plain JVM tests without Android framework mocks.
 */
internal class PlaybackTimerEngine(
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private val lock = Any()

    private val _sleep = MutableStateFlow(SleepTimerState())
    val sleep: StateFlow<SleepTimerState> = _sleep.asStateFlow()

    private val _interactive = MutableStateFlow(InteractiveTimerState())
    val interactive: StateFlow<InteractiveTimerState> = _interactive.asStateFlow()

    private var lastSleepTickMs: Long = 0L
    private var lastInteractiveTickMs: Long = 0L

    fun startSleepCountdown(durationMs: Long) {
        val clean = durationMs.coerceAtLeast(1_000L)
        synchronized(lock) {
            lastSleepTickMs = clockMs()
            _sleep.value = SleepTimerState(
                mode = SleepTimerMode.COUNTDOWN,
                totalMs = clean,
                remainingMs = clean,
            )
        }
    }

    fun startSleepAfterCurrentTrack() {
        synchronized(lock) {
            lastSleepTickMs = clockMs()
            _sleep.value = SleepTimerState(
                mode = SleepTimerMode.AFTER_CURRENT_TRACK,
                totalMs = 0L,
                remainingMs = 0L,
            )
        }
    }

    fun extendSleep(addMs: Long) {
        val cleanAdd = addMs.coerceAtLeast(1_000L)
        synchronized(lock) {
            val now = clockMs()
            lastSleepTickMs = now
            val current = _sleep.value
            if (current.mode == SleepTimerMode.COUNTDOWN) {
                _sleep.value = current.copy(
                    totalMs = current.totalMs + cleanAdd,
                    remainingMs = current.remainingMs + cleanAdd,
                )
            } else {
                _sleep.value = SleepTimerState(
                    mode = SleepTimerMode.COUNTDOWN,
                    totalMs = cleanAdd,
                    remainingMs = cleanAdd,
                )
            }
        }
    }

    fun cancelSleep() {
        synchronized(lock) {
            lastSleepTickMs = 0L
            _sleep.value = SleepTimerState()
        }
    }

    fun consumeAfterCurrentTrackOnNaturalEnd(): Boolean {
        synchronized(lock) {
            if (_sleep.value.mode != SleepTimerMode.AFTER_CURRENT_TRACK) return false
            lastSleepTickMs = 0L
            _sleep.value = SleepTimerState()
            return true
        }
    }

    fun startInteractiveTimer(
        durationMs: Long,
        action: InteractiveTimedAction,
        courseKey: String?,
        currentlyPlaying: Boolean = true,
    ) {
        val clean = durationMs.coerceAtLeast(1_000L)
        synchronized(lock) {
            lastInteractiveTickMs = clockMs()
            _interactive.value = InteractiveTimerState(
                active = true,
                totalMs = clean,
                remainingMs = clean,
                action = action,
                courseKey = courseKey,
                pausedByTransport = !currentlyPlaying,
            )
        }
    }

    fun cancelInteractiveTimer() {
        synchronized(lock) {
            lastInteractiveTickMs = 0L
            _interactive.value = InteractiveTimerState()
        }
    }

    fun syncInteractiveContext(isInteractiveActive: Boolean, courseKey: String?) {
        synchronized(lock) {
            val cur = _interactive.value
            if (!cur.active) return
            val switchedCourse = courseKey != null && cur.courseKey != null && courseKey != cur.courseKey
            if (!isInteractiveActive || switchedCourse) {
                lastInteractiveTickMs = 0L
                _interactive.value = InteractiveTimerState()
            }
        }
    }

    fun tick(
        isSessionActive: Boolean,
        isPlaying: Boolean,
        isInteractive: Boolean,
    ): TimerTickOutcome {
        synchronized(lock) {
            val now = clockMs()
            var sleepExpired = false
            var firedInteractive: InteractiveTimedAction? = null

            val sleepCur = _sleep.value
            if (sleepCur.mode == SleepTimerMode.COUNTDOWN) {
                val elapsed = (now - lastSleepTickMs).coerceAtLeast(0L)
                lastSleepTickMs = now
                val nextRemaining = (sleepCur.remainingMs - elapsed).coerceAtLeast(0L)
                if (nextRemaining == 0L) {
                    _sleep.value = SleepTimerState()
                    lastSleepTickMs = 0L
                    sleepExpired = true
                } else if (nextRemaining != sleepCur.remainingMs) {
                    _sleep.value = sleepCur.copy(remainingMs = nextRemaining)
                }
            }

            val intCur = _interactive.value
            if (intCur.active) {
                if (!isSessionActive || !isInteractive) {
                    _interactive.value = InteractiveTimerState()
                    lastInteractiveTickMs = 0L
                } else if (isPlaying) {
                    val elapsed = (now - lastInteractiveTickMs).coerceAtLeast(0L)
                    lastInteractiveTickMs = now
                    val nextRemaining = (intCur.remainingMs - elapsed).coerceAtLeast(0L)
                    if (nextRemaining == 0L) {
                        firedInteractive = intCur.action
                        _interactive.value = InteractiveTimerState()
                        lastInteractiveTickMs = 0L
                    } else {
                        _interactive.value = intCur.copy(
                            remainingMs = nextRemaining,
                            pausedByTransport = false,
                        )
                    }
                } else {
                    lastInteractiveTickMs = now
                    if (!intCur.pausedByTransport) {
                        _interactive.value = intCur.copy(pausedByTransport = true)
                    }
                }
            }

            return TimerTickOutcome(
                sleepExpired = sleepExpired,
                triggeredInteractiveAction = firedInteractive,
            )
        }
    }
}

/**
 * Process-wide owner for sleep & interactive timers so [PlaybackService] continues
 * enforcing timers even when the Activity / UI leaves the foreground.
 */
internal object PlaybackTimers {
    val engine = PlaybackTimerEngine()
    val sleepState: StateFlow<SleepTimerState> = engine.sleep
    val interactiveState: StateFlow<InteractiveTimerState> = engine.interactive

    @Volatile
    var onSleepExpired: (() -> Unit)? = null

    @Volatile
    var onInteractiveActionTriggered: ((InteractiveTimedAction) -> Unit)? = null

    fun tickFromSession(session: PlaybackSession): TimerTickOutcome {
        val st = session.state.value
        val outcome = engine.tick(
            isSessionActive = st.active,
            isPlaying = st.playing,
            isInteractive = st.active && !st.linear,
        )
        if (outcome.sleepExpired) {
            val handler = onSleepExpired
            if (handler != null) {
                handler.invoke()
            } else if (st.active && !session.uninterruptible) {
                session.stop()
            }
        }
        outcome.triggeredInteractiveAction?.let { action ->
            val handler = onInteractiveActionTriggered
            if (handler != null) {
                handler.invoke(action)
            } else if (action is InteractiveTimedAction.FadeStop && st.active && !session.uninterruptible) {
                session.stop()
            }
        }
        return outcome
    }
}
