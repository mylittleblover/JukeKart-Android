package com.mylittleblover.mkwplayer.ui.kit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mylittleblover.mkwplayer.ui.rememberPressScale

internal enum class MkwSurfaceTone {
    Porcelain,
    White,
    RoseTint,
    Recessed,
}

internal enum class MkwBadgeTone {
    Rose,
    Crimson,
    Neutral,
    Ink,
    Scrim,
    Danger,
}

private fun toneBackground(tone: MkwSurfaceTone): Color = when (tone) {
    MkwSurfaceTone.Porcelain -> MkwKitTokens.Palette.card
    MkwSurfaceTone.White -> MkwKitTokens.Palette.ground
    MkwSurfaceTone.RoseTint -> MkwKitTokens.Palette.rose.copy(alpha = 0.55f)
    MkwSurfaceTone.Recessed -> MkwKitTokens.Palette.inset
}

/**
 * Standard MKW surface container: crisp corner geometry + `1.dp` hairline stroke, zero murky MD3
 * tonal-elevation haze.
 */
@Composable
internal fun MkwCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    tone: MkwSurfaceTone = MkwSurfaceTone.Porcelain,
    selected: Boolean = false,
    error: Boolean = false,
    shape: Shape = MkwKitTokens.Shape.card,
    contentPadding: PaddingValues = PaddingValues(MkwKitTokens.Spacing.md),
    content: @Composable ColumnScope.() -> Unit,
) {
    val bgColor = when {
        selected -> MkwKitTokens.Palette.rose.copy(alpha = 0.45f)
        else -> toneBackground(tone)
    }
    val borderColor = when {
        error -> MkwKitTokens.Palette.danger.copy(alpha = 0.65f)
        selected -> MkwKitTokens.Palette.crimson.copy(alpha = 0.55f)
        else -> MkwKitTokens.Palette.hairline
    }
    val border = BorderStroke(MkwKitTokens.Stroke.border, borderColor)

    if (onClick != null) {
        val (pressScale, src) = rememberPressScale(pressedScale = 0.985f)
        Surface(
            onClick = onClick,
            enabled = enabled,
            interactionSource = src,
            shape = shape,
            color = bgColor,
            tonalElevation = 0.dp,
            border = border,
            modifier = modifier.then(pressScale),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(MkwKitTokens.Spacing.xs),
                content = content,
            )
        }
    } else {
        Surface(
            shape = shape,
            color = bgColor,
            tonalElevation = 0.dp,
            border = border,
            modifier = modifier,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(MkwKitTokens.Spacing.xs),
                content = content,
            )
        }
    }
}

/** Elevated floating porcelain overlay reserved for map HUD cards, mini-player, and docks. */
@Composable
internal fun MkwFloatingPanel(
    modifier: Modifier = Modifier,
    shape: Shape = MkwKitTokens.Shape.card,
    shadowElevation: Dp = 4.dp,
    alpha: Float = 0.97f,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = MkwKitTokens.Palette.ground.copy(alpha = alpha),
        tonalElevation = 0.dp,
        shadowElevation = shadowElevation,
        border = BorderStroke(MkwKitTokens.Stroke.border, MkwKitTokens.Palette.hairline.copy(alpha = 0.85f)),
        content = content,
    )
}

/** Recessed inner well for nested metadata, code blocks, waveform plots, and summary panes. */
@Composable
internal fun MkwInsetBox(
    modifier: Modifier = Modifier,
    shape: Shape = MkwKitTokens.Shape.control,
    contentPadding: PaddingValues = PaddingValues(MkwKitTokens.Spacing.sm),
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = MkwKitTokens.Palette.inset,
        tonalElevation = 0.dp,
        border = BorderStroke(MkwKitTokens.Stroke.hairline, MkwKitTokens.Palette.hairline),
    ) {
        Box(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}

/** Section header enforcing consistent type scale, single-line overflow safety, and optional actions. */
@Composable
internal fun MkwSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = if (action != null) 8.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MkwKitTokens.Palette.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MkwKitTokens.Palette.inkSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (action != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MkwKitTokens.Spacing.xxs),
                content = action,
            )
        }
    }
}

/** Uniform hairline divider matching our card/container stroke weight. */
@Composable
internal fun MkwDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = MkwKitTokens.Stroke.hairline,
    color: Color = MkwKitTokens.Palette.hairline,
) {
    HorizontalDivider(modifier = modifier, thickness = thickness, color = color)
}

/** Compact pill badge (`Rose`, `Crimson`, `Neutral`, `Ink`, `Scrim`, `Danger`) with optional tabular numerals. */
@Composable
internal fun MkwBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: MkwBadgeTone = MkwBadgeTone.Rose,
    leadingIcon: ImageVector? = null,
    tabular: Boolean = true,
) {
    val (bg, fg, border) = when (tone) {
        MkwBadgeTone.Rose -> Triple(MkwKitTokens.Palette.rose, MkwKitTokens.Palette.crimsonDeep, null)
        MkwBadgeTone.Crimson -> Triple(MkwKitTokens.Palette.crimson, Color.White, null)
        MkwBadgeTone.Neutral -> Triple(
            MkwKitTokens.Palette.track,
            MkwKitTokens.Palette.inkSecondary,
            BorderStroke(MkwKitTokens.Stroke.hairline, MkwKitTokens.Palette.hairline),
        )
        MkwBadgeTone.Ink -> Triple(MkwKitTokens.Palette.ink, Color.White, null)
        MkwBadgeTone.Scrim -> Triple(MkwKitTokens.Palette.photoScrim, Color.White, null)
        MkwBadgeTone.Danger -> Triple(MkwKitTokens.Palette.dangerTint, MkwKitTokens.Palette.danger, null)
    }
    val textStyle = MaterialTheme.typography.labelMedium.let {
        if (tabular) MkwKitTokens.tabular(it) else it
    }
    Surface(
        modifier = modifier.height(26.dp),
        shape = MkwKitTokens.Shape.pill,
        color = bg,
        border = border,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(14.dp), tint = fg)
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = text,
                style = textStyle,
                fontWeight = FontWeight.SemiBold,
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
