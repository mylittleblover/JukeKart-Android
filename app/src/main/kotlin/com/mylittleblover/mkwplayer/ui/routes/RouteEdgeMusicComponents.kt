package com.mylittleblover.mkwplayer.ui.routes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mylittleblover.mkwplayer.R
import com.mylittleblover.mkwplayer.routes.OfficialRouteKind
import com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog
import com.mylittleblover.mkwplayer.ui.art.PackArtwork
import com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog
import com.mylittleblover.mkwplayer.ui.kit.MkwBadge
import com.mylittleblover.mkwplayer.ui.kit.MkwBadgeTone
import com.mylittleblover.mkwplayer.ui.kit.MkwBottomSheet
import com.mylittleblover.mkwplayer.ui.kit.MkwButtonSize
import com.mylittleblover.mkwplayer.ui.kit.MkwCard
import com.mylittleblover.mkwplayer.ui.kit.MkwDivider
import com.mylittleblover.mkwplayer.ui.kit.MkwIconButton
import com.mylittleblover.mkwplayer.ui.kit.MkwKitTokens
import com.mylittleblover.mkwplayer.ui.kit.MkwOptionRow
import com.mylittleblover.mkwplayer.ui.kit.MkwSecondaryButton
import com.mylittleblover.mkwplayer.ui.kit.MkwSurfaceTone
import com.mylittleblover.mkwplayer.ui.kit.MkwTextField
import java.io.File
import java.util.Locale

internal fun RoadJukeboxCatalog.EdgeMusicResolution.hasKnownPool(): Boolean =
    this is RoadJukeboxCatalog.EdgeMusicResolution.KnownPoolComplete ||
        this is RoadJukeboxCatalog.EdgeMusicResolution.KnownPoolIncomplete

internal fun RoadJukeboxCatalog.EdgeMusicResolution.isMissingOrIncomplete(): Boolean =
    this is RoadJukeboxCatalog.EdgeMusicResolution.KnownPoolIncomplete ||
        this is RoadJukeboxCatalog.EdgeMusicResolution.SubstituteMissing

@Composable
internal fun candidateTierLabel(tier: RoadJukeboxCatalog.CandidateTier?): String? = when (tier) {
    RoadJukeboxCatalog.CandidateTier.GRAND_PRIX -> stringResource(R.string.route_tier_gp)
    RoadJukeboxCatalog.CandidateTier.EXCLUSIVE_ORDER -> stringResource(R.string.route_tier_exclusive)
    RoadJukeboxCatalog.CandidateTier.THEMED_BOOSTED -> stringResource(R.string.route_tier_boosted)
    RoadJukeboxCatalog.CandidateTier.MAIN_ORDER -> stringResource(R.string.route_tier_main)
    RoadJukeboxCatalog.CandidateTier.NIGHT_ORDER -> stringResource(R.string.route_tier_night)
    null -> null
}

@Composable
internal fun edgeResolutionSummary(
    resolution: RoadJukeboxCatalog.EdgeMusicResolution,
    language: String,
): String = when (resolution) {
    is RoadJukeboxCatalog.EdgeMusicResolution.KnownPoolComplete -> {
        val override = resolution.selectedOverride
        if (override != null) {
            stringResource(R.string.route_edge_music_override, override.localizedDisplayName(language))
        } else {
            val boostedCount = resolution.candidateTiers.values.count {
                it == RoadJukeboxCatalog.CandidateTier.EXCLUSIVE_ORDER ||
                    it == RoadJukeboxCatalog.CandidateTier.THEMED_BOOSTED
            }
            when {
                resolution.candidates.isNotEmpty() && boostedCount > 0 ->
                    stringResource(R.string.route_edge_music_auto_weighted, resolution.candidates.size, boostedCount)
                resolution.candidates.isNotEmpty() ->
                    stringResource(R.string.route_edge_music_auto_count, resolution.candidates.size)
                else -> stringResource(R.string.route_edge_music_auto)
            }
        }
    }
    is RoadJukeboxCatalog.EdgeMusicResolution.SpecialRoadApproach ->
        stringResource(R.string.route_edge_music_to_rainbow)
    is RoadJukeboxCatalog.EdgeMusicResolution.KnockoutTourDirect ->
        stringResource(R.string.route_edge_music_knockout_direct)
    is RoadJukeboxCatalog.EdgeMusicResolution.KnownPoolIncomplete,
    is RoadJukeboxCatalog.EdgeMusicResolution.SubstituteMissing ->
        stringResource(R.string.route_edge_music_incomplete_pool)
    is RoadJukeboxCatalog.EdgeMusicResolution.CustomSubstitute ->
        stringResource(R.string.route_edge_music_substitute, resolution.track.localizedDisplayName(language))
    is RoadJukeboxCatalog.EdgeMusicResolution.MissingCandidateData ->
        stringResource(R.string.route_edge_music_pool_empty)
    is RoadJukeboxCatalog.EdgeMusicResolution.DisabledByUser ->
        stringResource(R.string.route_edge_music_muted)
}

@Composable
internal fun RoadMusicRuleModeSelector(
    selectedMode: RoadJukeboxCatalog.RoadMusicRuleMode,
    officialKind: OfficialRouteKind?,
    onSelectMode: (RoadJukeboxCatalog.RoadMusicRuleMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val autoLabel = when (officialKind) {
        OfficialRouteKind.GRAND_PRIX -> stringResource(R.string.route_rule_auto_gp)
        OfficialRouteKind.RALLY -> stringResource(R.string.route_rule_auto_rally)
        null -> stringResource(R.string.route_rule_auto_vs)
    }
    val options = listOf(
        com.mylittleblover.mkwplayer.ui.interactive.DrawerOption(
            value = RoadJukeboxCatalog.RoadMusicRuleMode.AUTO,
            label = autoLabel,
        ),
        com.mylittleblover.mkwplayer.ui.interactive.DrawerOption(
            value = RoadJukeboxCatalog.RoadMusicRuleMode.GRAND_PRIX,
            label = stringResource(R.string.route_rule_gp),
        ),
        com.mylittleblover.mkwplayer.ui.interactive.DrawerOption(
            value = RoadJukeboxCatalog.RoadMusicRuleMode.VS_DAY,
            label = stringResource(R.string.route_rule_vs_day),
        ),
        com.mylittleblover.mkwplayer.ui.interactive.DrawerOption(
            value = RoadJukeboxCatalog.RoadMusicRuleMode.VS_NIGHT,
            label = stringResource(R.string.route_rule_vs_night),
        ),
    )
    val selectedLabel = options.firstOrNull { it.value == selectedMode }?.label ?: autoLabel
    com.mylittleblover.mkwplayer.ui.interactive.SelectionDrawerRow(
        label = stringResource(R.string.route_rule_mode_label),
        selectedLabel = selectedLabel,
        sheetTitle = stringResource(R.string.route_rule_mode_label),
        options = options,
        selectedValue = selectedMode,
        onSelect = onSelectMode,
        modifier = modifier,
        compact = true,
    )
}

@Composable
internal fun RouteSuiteModeSelector(
    selectedMode: RoadJukeboxCatalog.RouteSuiteMode,
    onSelectMode: (RoadJukeboxCatalog.RouteSuiteMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        com.mylittleblover.mkwplayer.ui.interactive.DrawerOption(
            value = RoadJukeboxCatalog.RouteSuiteMode.GAME_SUITE,
            label = stringResource(R.string.route_suite_mode_game),
            subtitle = stringResource(R.string.route_suite_mode_game_desc),
        ),
        com.mylittleblover.mkwplayer.ui.interactive.DrawerOption(
            value = RoadJukeboxCatalog.RouteSuiteMode.COMPLETE_SUITE,
            label = stringResource(R.string.route_suite_mode_complete),
            subtitle = stringResource(R.string.route_suite_mode_complete_desc),
        ),
    )
    val selectedLabel = options.firstOrNull { it.value == selectedMode }?.label
        ?: stringResource(R.string.route_suite_mode_game)
    com.mylittleblover.mkwplayer.ui.interactive.SelectionDrawerRow(
        label = stringResource(R.string.route_suite_mode_label),
        selectedLabel = selectedLabel,
        sheetTitle = stringResource(R.string.route_suite_mode_label),
        options = options,
        selectedValue = selectedMode,
        onSelect = onSelectMode,
        modifier = modifier,
        compact = true,
    )
}

/** Shared string resource lookup for built-in suite variants (`"a"`..`"d"`, `"knockout"`) and `PresetRefs`. */
@androidx.annotation.StringRes
internal fun builtInSuiteLabelRes(variantOrRef: String?): Int = when (variantOrRef?.trim()?.lowercase(java.util.Locale.ROOT)) {
    "", "medley", "a", com.mylittleblover.mkwplayer.library.PresetRefs.SYSTEM_DEFAULT -> R.string.preset_system_default
    "a_alt", "a alt", "alt", com.mylittleblover.mkwplayer.library.PresetRefs.SYSTEM_SUITE_A_ALT -> R.string.preset_system_suite_a_alt
    "b", com.mylittleblover.mkwplayer.library.PresetRefs.SYSTEM_SUITE_B -> R.string.preset_system_suite_b
    "c", com.mylittleblover.mkwplayer.library.PresetRefs.SYSTEM_SUITE_C -> R.string.preset_system_suite_c
    "d", com.mylittleblover.mkwplayer.library.PresetRefs.SYSTEM_SUITE_D -> R.string.preset_system_suite_d
    "knockout", com.mylittleblover.mkwplayer.library.PresetRefs.SYSTEM_KNOCKOUT -> R.string.preset_system_knockout
    else -> R.string.preset_system_default
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RouteEdgeMusicPickerSheet(
    legTitle: String,
    leg: RoadJukeboxCatalog.RouteLegInspection,
    installedJukebox: List<Pair<JukeboxCatalog.TrackItem, JukeboxCatalog.Game>>,
    allowCustomSubstitute: Boolean,
    allowMute: Boolean,
    sessionHint: Boolean,
    language: String,
    packDirectory: File?,
    currentSelectedKey: String?,
    currentMuted: Boolean,
    onSelectAuto: () -> Unit,
    onSelectMuted: () -> Unit,
    onSelectTrackKey: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable(leg.index, legTitle) { mutableStateOf("") }
    val hasKnownPool = leg.resolution.hasKnownPool()
    val isSpecialApproach = leg.resolution is RoadJukeboxCatalog.EdgeMusicResolution.SpecialRoadApproach
    val isKnockoutDirect = leg.resolution is RoadJukeboxCatalog.EdgeMusicResolution.KnockoutTourDirect
    val showAllInstalledFallback = !hasKnownPool && allowCustomSubstitute
    val substituteTag = stringResource(R.string.route_custom_substitute_tag)
    val tiersByKey = (leg.resolution as? RoadJukeboxCatalog.EdgeMusicResolution.KnownPoolComplete)?.candidateTiers.orEmpty()

    val selectableTracks: List<Pair<JukeboxCatalog.TrackItem, JukeboxCatalog.Game?>> = remember(
        leg,
        installedJukebox,
        allowCustomSubstitute,
        query,
        language,
        hasKnownPool,
    ) {
        val trimmed = query.trim()
        val gamesByKey = installedJukebox.associate { (item, game) -> item.key to game }
        val base = when {
            hasKnownPool -> {
                val res = leg.resolution as? RoadJukeboxCatalog.EdgeMusicResolution.KnownPoolComplete
                res?.candidates.orEmpty().map { it to gamesByKey[it.key] }
            }
            allowCustomSubstitute -> installedJukebox.map { (item, game) -> item to game }
            else -> emptyList()
        }
        if (trimmed.isEmpty()) {
            base
        } else {
            base.filter { (item, game) ->
                val gameNames = game?.let { listOf(it.localizedName(language)) + it.localizedNames.values }.orEmpty()
                item.matches(trimmed, gameNames)
            }
        }
    }

    MkwBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.route_pick_edge_track),
        subtitle = legTitle,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (sessionHint) {
                Text(
                    stringResource(R.string.route_pick_edge_session_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MkwKitTokens.Palette.inkSecondary,
                )
            }
            if (showAllInstalledFallback) {
                MkwCard(
                    tone = MkwSurfaceTone.RoseTint,
                    selected = true,
                    shape = MkwKitTokens.Shape.control,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MkwBadge(text = substituteTag, tone = MkwBadgeTone.Rose, tabular = false)
                        Text(
                            if (isSpecialApproach) {
                                stringResource(R.string.route_edge_music_to_rainbow)
                            } else {
                                stringResource(R.string.route_edge_music_pool_empty)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MkwKitTokens.Palette.crimsonDeep,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MkwSecondaryButton(
                    text = stringResource(R.string.route_clear_override),
                    onClick = { onSelectAuto(); onDismiss() },
                    leadingIcon = Icons.Filled.Shuffle,
                    size = MkwButtonSize.Compact,
                    modifier = Modifier.weight(1f),
                )
                if (allowMute) {
                    MkwSecondaryButton(
                        text = stringResource(R.string.route_skip_edge),
                        onClick = { onSelectMuted(); onDismiss() },
                        leadingIcon = Icons.Filled.MusicOff,
                        size = MkwButtonSize.Compact,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (selectableTracks.size > 6 || query.isNotEmpty() || showAllInstalledFallback) {
                MkwTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(R.string.discovery_search),
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = MkwKitTokens.Palette.inkSecondary) },
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            MkwIconButton(
                                icon = Icons.Filled.Clear,
                                contentDescription = stringResource(R.string.discovery_cancel),
                                onClick = { query = "" },
                                visualSize = 32.dp,
                                iconSize = 18.dp,
                            )
                        }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            MkwDivider()

            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
            ) {
                if (selectableTracks.isEmpty()) {
                    item {
                        Text(
                            when {
                                isSpecialApproach -> stringResource(R.string.route_edge_music_to_rainbow)
                                isKnockoutDirect -> stringResource(R.string.route_edge_music_knockout_direct)
                                leg.resolution.isMissingOrIncomplete() -> stringResource(R.string.route_edge_music_incomplete_pool)
                                else -> stringResource(R.string.route_edge_music_pool_empty)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MkwKitTokens.Palette.inkSecondary,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                }
                items(selectableTracks, key = { (item, _) -> "pick:${item.key}" }) { (item, game) ->
                    val isSelected = !currentMuted && (
                        currentSelectedKey == item.key ||
                            currentSelectedKey == item.track.key ||
                            currentSelectedKey == item.track.primary.sha256
                        )
                    val artworkName = item.ostIndex?.let { "t_%03d.webp".format(Locale.ROOT, it) }
                    val tierText = candidateTierLabel(tiersByKey[item.key])
                    val sub = buildList {
                        tierText?.let(::add)
                        game?.localizedName(language)?.takeIf { it.isNotBlank() }?.let(::add)
                        item.localizedArrangement(language)?.takeIf { it.isNotBlank() }?.let(::add)
                        if (showAllInstalledFallback) add(substituteTag)
                    }.joinToString(" · ")
                    MkwOptionRow(
                        headline = item.localizedDisplayName(language),
                        supporting = sub.takeIf { it.isNotBlank() },
                        selected = isSelected,
                        onClick = {
                            onSelectTrackKey(item.key)
                            onDismiss()
                        },
                        leading = {
                            PackArtwork.Thumbnail(
                                packDirectory,
                                listOfNotNull(artworkName),
                                Modifier.size(44.dp).clip(MkwKitTokens.Shape.thumb)
                                    .background(MkwKitTokens.Palette.track),
                            ) {
                                Icon(Icons.Outlined.MusicNote, null, tint = MkwKitTokens.Palette.inkSecondary)
                            }
                        },
                        trailing = if (isSelected) {
                            { Icon(Icons.Filled.Check, null, tint = MkwKitTokens.Palette.crimson) }
                        } else null,
                    )
                }
            }
        }
    }
}
