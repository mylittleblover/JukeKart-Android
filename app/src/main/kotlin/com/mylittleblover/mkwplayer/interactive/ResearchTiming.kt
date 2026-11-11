package com.mylittleblover.mkwplayer.interactive

/** Source-verified MINF arithmetic, not proof of gameplay permission for a candidate. */
internal object ResearchTiming {
    data class Plan(val bridgeStart: Long, val fullStart: Long, val bridgeHandover: Long)

    fun ordinary(
        preExit: Long,
        bridgePhase: Long,
        bridgeToFull: Long?,
        fullPhase: Long,
        fullSourceOffset: Long = 0,
    ): Plan {
        require(preExit >= 0 && bridgePhase >= 0 && fullPhase >= 0)
        require(fullSourceOffset == 0L) { "Full_SkipForBridge requires its own source-entry plan" }
        require(bridgeToFull != null && bridgeToFull >= 0) { "Bridge_ToFull has no source sample" }
        val bridge = Math.subtractExact(preExit, bridgePhase)
        val handover = Math.addExact(bridge, bridgeToFull)
        return Plan(bridge, Math.subtractExact(handover, fullPhase), handover)
    }
}
