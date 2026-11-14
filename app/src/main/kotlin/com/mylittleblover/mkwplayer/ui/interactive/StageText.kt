package com.mylittleblover.mkwplayer.ui.interactive

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mylittleblover.mkwplayer.R
import com.mylittleblover.mkwplayer.player.PlayerViewModel

/** Source labels stay stable in the engine; only their presentation is translated. */
@Composable
internal fun stageText(raw: String): String {
    val value = raw.trim()
    val pattern = Regex("^(Main|Full|Fast|Outro[0-9]*) Pattern([0-9]+)$").matchEntire(value)
    if (pattern != null) return stringResource(R.string.stage_join,
        stageText(pattern.groupValues[1]), stringResource(R.string.stage_pattern, pattern.groupValues[2].toInt()))
    val region = Regex("^Region ([0-9]+)$").matchEntire(value)
    if (region != null) return stringResource(R.string.stage_region, region.groupValues[1].toInt())
    val area = Regex("^Area([0-9]+)(?:_(Pre|Bridge|Outro[0-9]*))?$").matchEntire(value)
    if (area != null) {
        val location = stringResource(R.string.stage_region, area.groupValues[1].toInt())
        val phase = area.groupValues[2]
        return if (phase.isEmpty()) location else stringResource(R.string.stage_join, location, stageText(phase))
    }
    if (value == "Final_Pre") return stringResource(R.string.stage_join,
        stringResource(R.string.stage_final_lap), stringResource(R.string.stage_prelude))
    if (value.startsWith("Outro")) return stringResource(R.string.stage_outro)
    return when (value.lowercase()) {
        "pre", "prelude", "prelude, looping" -> stringResource(R.string.stage_prelude)
        "preshort", "short prelude", "short prelude, looping" -> stringResource(R.string.stage_short_prelude)
        "full", "main", "main loop" -> stringResource(R.string.stage_main)
        "fast", "final", "final lap" -> stringResource(R.string.stage_final_lap)
        "bridge" -> stringResource(R.string.stage_bridge)
        "outro", "ending" -> stringResource(R.string.stage_outro)
        "finallapff", "finallapfanfare" -> stringResource(R.string.stage_fanfare)
        "stopped" -> stringResource(R.string.stage_stopped)
        "finished" -> stringResource(R.string.stage_finished)
        "waiting", "not started", "" -> stringResource(R.string.stage_not_started)
        else -> value
    }
}

@Composable
internal fun performanceStageText(flow: PlayerViewModel.FlowUi): String {
    val current = stageText(flow.stageKey ?: flow.performanceStage)
    val pending = flow.pendingStageKey ?: return current
    return stringResource(R.string.stage_pending, current, stageText(pending))
}
