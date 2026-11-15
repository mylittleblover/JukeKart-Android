package com.mylittleblover.mkwplayer.ui.interactive

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mylittleblover.mkwplayer.R
import com.mylittleblover.mkwplayer.player.PlayerViewModel

private val patternLabel = Regex("^(Main|Full|Fast|Outro[0-9]*) Pattern([0-9]+)$")
private val regionLabel = Regex("^Region ([0-9]+)$")
private val areaLabel = Regex("^Area([0-9]+)(?:_(Pre|Bridge|Outro[0-9]*))?$")

/** Source labels stay stable in the engine; only their presentation is translated. */
@Composable
internal fun stageText(raw: String): String {
    val value = raw.trim()
    val pattern = patternLabel.matchEntire(value)
    if (pattern != null) return stringResource(R.string.stage_join,
        stageText(pattern.groupValues[1]), stringResource(R.string.stage_pattern, pattern.groupValues[2].toInt()))
    val region = regionLabel.matchEntire(value)
    if (region != null) return stringResource(R.string.stage_region, region.groupValues[1].toInt())
    val area = areaLabel.matchEntire(value)
    if (area != null) {
        val location = stringResource(R.string.stage_region, area.groupValues[1].toInt())
        val phase = area.groupValues[2]
        return if (phase.isEmpty()) location else stringResource(R.string.stage_join, location, stageText(phase))
    }
    if (value == "Final_Pre") return stringResource(R.string.stage_join,
        stringResource(R.string.stage_final_lap), stringResource(R.string.stage_prelude))
    if (value.startsWith("Outro")) {
        val number = value.removePrefix("Outro").toIntOrNull()
        return if (number != null) stringResource(R.string.stage_outro_numbered, number + 1)
        else stringResource(R.string.stage_outro)
    }
    return when (value.lowercase()) {
        "titleextended" -> stringResource(R.string.stage_title_extended)
        "titlenormal" -> stringResource(R.string.stage_title_normal)
        "titlebrasscut" -> stringResource(R.string.stage_title_brasscut)
        "battlefast" -> stringResource(R.string.stage_battle_fast)
        "pre", "prelude", "prelude, looping" -> stringResource(R.string.stage_prelude)
        "preshort", "pre (short)", "short prelude", "short prelude, looping" -> stringResource(R.string.stage_short_prelude)
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
    // During a bridge the foreground source may already have ended; do not claim it is still playing.
    if (flow.performing && flow.voiceCount > 0 && flow.sourceFrame == null)
        return stringResource(R.string.stage_transitioning)
    val current = stageText(flow.stageKey ?: flow.performanceStage)
    val pending = flow.pendingStageKey ?: return current
    return stringResource(R.string.stage_pending, current, stageText(pending))
}
