package com.mylittleblover.mkwplayer.ui.home

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mylittleblover.mkwplayer.library.DailySelectionStore
import com.mylittleblover.mkwplayer.library.FavoriteStore
import com.mylittleblover.mkwplayer.library.RecommendationCandidate
import com.mylittleblover.mkwplayer.library.millisUntilNextMidnight
import java.io.File
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class DiscoveryViewModel(application: Application) : AndroidViewModel(application) {
    enum class Error { FAVORITES_READ, FAVORITES_WRITE, DAILY_SELECTION, PLAYLISTS, RECENT }
    data class State(
        val favorites: Set<String> = emptySet(), val day: LocalDate = LocalDate.now(),
        val dailyIds: List<String> = emptyList(), val favoritesReady: Boolean = false, val dailyReady: Boolean = false,
        val favoritesError: Error? = null, val dailyError: Error? = null,
        val playlists: List<com.mylittleblover.mkwplayer.library.UserPlaylist> = emptyList(), val playlistError: Error? = null,
        val recentIds: List<String> = emptyList(), val recentError: Error? = null,
    ) {
        val error: Error? get() = favoritesError ?: dailyError ?: playlistError ?: recentError
    }
    private val favorites = FavoriteStore(File(application.filesDir, "user-favorites.json"))
    private val recent = com.mylittleblover.mkwplayer.library.RecentCourseStore(File(application.filesDir, "recent-courses.json"))
    private val recentOperations = Mutex()
    private val playlists = com.mylittleblover.mkwplayer.library.PlaylistStore(File(application.filesDir, "user-playlists.json"))
    private val playlistOperations = Mutex()
    private val daily = DailySelectionStore(File(application.filesDir, "daily-selection.json"))
    private val candidates = MutableStateFlow<List<RecommendationCandidate>?>(null)
    private val favoriteOperations = Mutex()
    private val day = MutableStateFlow(LocalDate.now())
    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()
    private val dateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { refreshDate() }
    }
    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED); addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= 33) application.registerReceiver(dateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else application.registerReceiver(dateReceiver, filter)
        reloadFavorites()
        updatePlaylists { playlists.read() }
        updateRecent { recent.read() }
        viewModelScope.launch {
            while (isActive) {
                refreshDate()
                delay(millisUntilNextMidnight(ZonedDateTime.now()).coerceAtMost(60_000L))
            }
        }
        viewModelScope.launch {
            combine(day, candidates) { date, pool -> pool?.let { date to it } }.filterNotNull().collectLatest { (date, pool) ->
                val selected = withContext(Dispatchers.IO) { daily.select(date, pool) }
                selected.onSuccess { ids -> _state.update { it.copy(day = date, dailyIds = ids, dailyReady = true, dailyError = null) } }
                    .onFailure { _state.update { it.copy(day = date, dailyReady = true, dailyError = Error.DAILY_SELECTION) } }
            }
        }
    }
    fun updateEligible(values: List<RecommendationCandidate>) { candidates.value = values.toList() }
    fun refreshDate() { day.value = LocalDate.now() }
    fun reloadFavorites() {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            favoriteOperations.withLock {
                val loaded = withContext(Dispatchers.IO) { favorites.read() }
                loaded.onSuccess { value -> _state.update { it.copy(favorites = value, favoritesReady = true, favoritesError = null) } }
                    .onFailure { _state.update { it.copy(favoritesReady = true, favoritesError = Error.FAVORITES_READ) } }
            }
        }
    }
    fun toggleFavorite(id: String) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) {
                favoriteOperations.withLock {
                    val result = withContext(Dispatchers.IO) { favorites.toggle(id) }
                    result.onSuccess { value -> _state.update { it.copy(favorites = value, favoritesReady = true, favoritesError = null) } }
                        .onFailure { _state.update { it.copy(favoritesError = Error.FAVORITES_WRITE) } }
                }
            }
        }
    }
    private fun updateRecent(operation: () -> Result<List<String>>) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) {
                recentOperations.withLock {
                    val result = withContext(Dispatchers.IO) { operation() }
                    result.onSuccess { value -> _state.update { it.copy(recentIds = value, recentError = null) } }
                        .onFailure { _state.update { it.copy(recentError = Error.RECENT) } }
                }
            }
        }
    }
    fun recordRecent(code: String) = updateRecent { recent.record(code.removeSuffix("_00")) }
    private fun updatePlaylists(operation: () -> Result<List<com.mylittleblover.mkwplayer.library.UserPlaylist>>) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) {
                playlistOperations.withLock {
                    val result = withContext(Dispatchers.IO) { operation() }
                    result.onSuccess { value -> _state.update { it.copy(playlists = value, playlistError = null) } }
                        .onFailure { _state.update { it.copy(playlistError = Error.PLAYLISTS) } }
                }
            }
        }
    }
    fun createPlaylist(name: String) = updatePlaylists { playlists.create(name) }
    fun addToPlaylist(id: String, code: String) = updatePlaylists { playlists.addCourse(id, code) }
    fun removeFromPlaylist(id: String, index: Int) = updatePlaylists { playlists.removeCourse(id, index) }
    fun renamePlaylist(id: String, name: String) = updatePlaylists { playlists.rename(id, name) }
    fun moveInPlaylist(id: String, from: Int, to: Int) = updatePlaylists { playlists.moveCourse(id, from, to) }
    fun deletePlaylist(id: String) = updatePlaylists { playlists.delete(id) }
    override fun onCleared() {
        getApplication<Application>().unregisterReceiver(dateReceiver)
        super.onCleared()
    }
}
