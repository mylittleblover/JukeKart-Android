package com.mylittleblover.mkwplayer.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mylittleblover.mkwplayer.R
import com.mylittleblover.mkwplayer.ui.kit.MkwDivider
import com.mylittleblover.mkwplayer.ui.kit.MkwFilterPill
import com.mylittleblover.mkwplayer.ui.kit.MkwIconButton
import com.mylittleblover.mkwplayer.ui.kit.MkwKitTokens
import kotlin.math.cos
import kotlin.math.sin

internal val LocalMiniPlayerOverlayPadding = androidx.compose.runtime.compositionLocalOf { 0.dp }

@Composable
internal fun rememberMiniPlayerBottomClearance(base: Dp = 16.dp): Dp =
    base + LocalMiniPlayerOverlayPadding.current

@Composable
internal fun tabTitle(tab: Tab): String = stringResource(when (tab) {
    Tab.HOME -> R.string.discovery_home
    Tab.MEDIA -> R.string.discovery_media
    Tab.WORLD_MAP -> R.string.nav_map
    Tab.PLAYLISTS -> R.string.discovery_playlists
})

@Composable
internal fun AppHeader(
    title: String,
    canBack: Boolean,
    showSettings: Boolean,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    sleepTimerRemainingMs: Long? = null,
    sleepAfterCurrentTrack: Boolean = false,
    onSleepTimer: (() -> Unit)? = null,
) {
    val settingsLabel = stringResource(R.string.settings_title)
    val sleepLabel = stringResource(R.string.sleep_timer_title)
    Surface(color = MkwKitTokens.Palette.ground) {
        Column {
            Row(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
                    .heightIn(min = 48.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (canBack) {
                    val backLabel = stringResource(R.string.common_back)
                    MkwIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = backLabel,
                        onClick = onBack,
                    )
                } else if (LocalDensity.current.fontScale <= 1.3f) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("/", color = MkwKitTokens.Palette.inkSecondary)
                }
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (onSleepTimer != null) {
                    val timerActive = sleepAfterCurrentTrack || (sleepTimerRemainingMs != null && sleepTimerRemainingMs > 0L)
                    if (timerActive) {
                        val countdownText = if (sleepAfterCurrentTrack) {
                            "1♪"
                        } else {
                            com.mylittleblover.mkwplayer.ui.components.formatCountdownMs(sleepTimerRemainingMs ?: 0L)
                        }
                        MkwFilterPill(
                            label = countdownText,
                            selected = true,
                            onClick = onSleepTimer,
                            leadingIcon = Icons.Filled.Bedtime,
                            role = Role.Button,
                        )
                    } else {
                        MkwIconButton(
                            icon = Icons.Outlined.Bedtime,
                            contentDescription = sleepLabel,
                            onClick = onSleepTimer,
                        )
                    }
                }
                if (showSettings) {
                    MkwIconButton(
                        icon = Icons.Outlined.Settings,
                        contentDescription = settingsLabel,
                        onClick = onSettings,
                    )
                }
            }
            MkwDivider()
        }
    }
}

internal fun tabIconVector(tab: Tab, selected: Boolean): ImageVector = when (tab) {
    Tab.HOME -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
    Tab.MEDIA -> if (selected) Icons.Filled.LibraryMusic else Icons.Outlined.LibraryMusic
    Tab.WORLD_MAP -> if (selected) Icons.Filled.Map else Icons.Outlined.Map
    Tab.PLAYLISTS -> if (selected) Icons.AutoMirrored.Filled.QueueMusic else Icons.AutoMirrored.Outlined.QueueMusic
}

/** Filled and outlined variants make selection visible without relying on color. */
@Composable
internal fun TabIcon(tab: Tab, selected: Boolean) {
    Icon(tabIconVector(tab, selected), contentDescription = null)
}
