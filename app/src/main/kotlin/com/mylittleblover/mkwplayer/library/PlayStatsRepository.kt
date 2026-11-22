package com.mylittleblover.mkwplayer.library

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Process-wide reactive owner for [PlayStatsStore] so play counts recorded in [PlayerViewModel]
 * immediately update lists, sorting, and Smart Playlists inside [DiscoveryViewModel].
 */
internal object PlayStatsRepository {
    private val lock = Any()

    @Volatile
    private var store: PlayStatsStore? = null

    private val _stats = MutableStateFlow<Map<String, TrackPlayStat>>(emptyMap())
    val stats: StateFlow<Map<String, TrackPlayStat>> = _stats.asStateFlow()

    private fun ensureStore(context: Context): PlayStatsStore {
        store?.let { return it }
        return synchronized(lock) {
            store ?: PlayStatsStore(File(context.applicationContext.filesDir, "play-stats.json")).also { created ->
                store = created
                _stats.value = created.read().getOrDefault(emptyMap())
            }
        }
    }

    fun reload(context: Context): Map<String, TrackPlayStat> {
        val loaded = ensureStore(context).read().getOrDefault(emptyMap())
        _stats.value = loaded
        return loaded
    }

    /** Loads stats from disk once on caller thread; subsequent callers reuse the populated flow. */
    fun ensureLoadedSync(context: Context): Map<String, TrackPlayStat> {
        store?.let { return _stats.value }
        return reload(context)
    }

    fun recordPlay(context: Context, trackId: String): Result<Map<String, TrackPlayStat>> {
        if (trackId.isBlank()) return Result.success(_stats.value)
        return ensureStore(context).recordPlay(trackId).onSuccess { updated ->
            _stats.value = updated
        }
    }

    fun merge(context: Context, incoming: Map<String, TrackPlayStat>): Result<Map<String, TrackPlayStat>> =
        ensureStore(context).merge(incoming).onSuccess { updated ->
            _stats.value = updated
        }

    fun replaceAll(context: Context, next: Map<String, TrackPlayStat>): Result<Map<String, TrackPlayStat>> =
        ensureStore(context).replaceAll(next).onSuccess { updated ->
            _stats.value = updated
        }

    fun readSnapshot(context: Context): Map<String, TrackPlayStat> =
        ensureStore(context).read().getOrDefault(emptyMap())
}
