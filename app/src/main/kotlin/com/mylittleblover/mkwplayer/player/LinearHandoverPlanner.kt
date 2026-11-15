package com.mylittleblover.mkwplayer.player

/**
 * Pure planning helpers for cross-track native transitions (#51-#53) and session serialization.
 */
internal object LinearHandoverPlanner {
    const val MAX_CHAIN_SEGMENTS = 4

    fun isVerifiedPreludeStart(stem: String): Boolean {
        val tail = stem.substringAfterLast('/')
        return tail.endsWith("_Pre_Short") ||
            tail.endsWith("_Pre") ||
            tail.endsWith("_Area1_Pre") ||
            tail.endsWith("_Bridge")
    }

    fun canHandoverNatively(current: LinearQueue.Entry, next: LinearQueue.Entry): Boolean {
        if (!current.nativeTransitionEnabled || !next.nativeTransitionEnabled) return false
        val currentMedley = current.courseMedley ?: return false
        val nextMedley = next.courseMedley ?: return false
        val handoverFrame = currentMedley.outroHandoverFrame ?: return false
        if (handoverFrame !in 1L..currentMedley.medley.totalFrames) return false
        val firstStem = nextMedley.medley.clips.firstOrNull()?.stem ?: return false
        return isVerifiedPreludeStart(firstStem)
    }

    fun collectChainEntries(first: LinearQueue.Entry, upcoming: List<LinearQueue.Entry>): List<LinearQueue.Entry> {
        val result = ArrayList<LinearQueue.Entry>(MAX_CHAIN_SEGMENTS)
        result += first
        var cursor = first
        for (candidate in upcoming) {
            if (result.size >= MAX_CHAIN_SEGMENTS) break
            if (!canHandoverNatively(cursor, candidate)) break
            result += candidate
            cursor = candidate
        }
        return result
    }

    fun toSavedEntry(entry: LinearQueue.Entry): LinearSessionStore.SavedEntry = LinearSessionStore.SavedEntry(
        key = entry.key,
        title = entry.title,
        subtitle = entry.subtitle,
        sourceType = entry.sourceType,
        courseCode = entry.courseCode,
        presetId = entry.presetId,
        medleyId = entry.medleyId,
        nativeTransitionOverride = entry.nativeTransitionOverride,
        gameId = entry.gameId,
        gameTitle = entry.gameTitle,
        yearLabel = entry.yearLabel,
        arrangement = entry.arrangement,
        ostIndex = entry.ostIndex,
        durationMs = entry.durationMs,
        artworkName = entry.artworkName,
        assetSha256 = entry.assetSha256,
        loop = entry.loop,
        loopStart = entry.loopStart,
        loopEnd = entry.loopEnd,
    )
}
