package com.mylittleblover.mkwplayer.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/** The control looper stays free until all promised foreground starts have been handled. */
internal class PlaybackStartupGate {
    private var requested=0L
    private val acknowledged=MutableStateFlow(0L)
    fun requested(): Long = ++requested
    fun promoted(request: Long) { if(request>acknowledged.value) acknowledged.value=request }
    suspend fun awaitPromotion() { acknowledged.first { it>=requested } }
}
