package com.mylittleblover.mkwplayer.audio

/** Resource ownership boundary used by the process-wide playback session. */
internal interface SessionAudioEngine : PlaybackEngine {
    fun nativeHandle(): Long
    fun fadeStop(beats: Double): Int = 1
    fun cancelPendingTransition(): Int
    fun sourceSnapshot(slot: Int): LongArray?
    fun bindStreamedBwav(assetIndex: Int,bytes: ByteArray,assetId: String): Int = bindBwav(assetIndex,bytes,assetId)
}

internal interface SessionAudioOutput {
    fun timingSnapshot(): LongArray?
    fun close()
}
