package com.mylittleblover.mkwplayer.ui

import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Android's animator duration scale is honored by Compose, including disabled system animations. */
internal object AppMotion {
    val easeOut = CubicBezierEasing(.23f, 1f, .32f, 1f)
    val easeInOut = CubicBezierEasing(.77f, 0f, .175f, 1f)
    const val duration = 220

    val snapSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Bouncy spring reserved strictly for release after a user drag/flick gesture. */
    val dragReleaseSpring = spring<Float>(
        dampingRatio = 0.8f,
        stiffness = Spring.StiffnessMediumLow,
    )

    val pressSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh,
    )

    fun page(direction: Int, reduceMotion: Boolean = false): ContentTransform {
        if (reduceMotion || direction == 0) {
            return (fadeIn(tween(150)) togetherWith fadeOut(tween(130)))
        }
        return ((slideInHorizontally(tween(duration, easing = easeOut)) { direction * it / 6 } +
            fadeIn(tween(180, easing = easeOut))) togetherWith
            (slideOutHorizontally(tween(180, easing = easeOut)) { -direction * it / 12 } +
                fadeOut(tween(140))))
    }

    /** Symmetric vertical rise/close transition paired with MiniPlayer expansion/collapse. */
    fun playerSheet(opening: Boolean, reduceMotion: Boolean = false): ContentTransform {
        if (reduceMotion) {
            return (fadeIn(tween(160)) togetherWith fadeOut(tween(140)))
        }
        return if (opening) {
            (slideInVertically(tween(260, easing = easeOut)) { (it * 0.24f).toInt().coerceAtLeast(1) } +
                fadeIn(tween(210, easing = easeOut))) togetherWith
                fadeOut(tween(150))
        } else {
            (fadeIn(tween(190, easing = easeOut)) togetherWith
                (slideOutVertically(tween(220, easing = easeOut)) { (it * 0.24f).toInt().coerceAtLeast(1) } +
                    fadeOut(tween(170))))
        }.apply {
            targetContentZIndex = if (opening) 1f else 0f
        }
    }
}

@Composable
internal fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }
}

@Composable
internal fun rememberPressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.97f,
): Float {
    val reduceMotion = rememberReducedMotion()
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) pressedScale else 1f,
        animationSpec = AppMotion.pressSpring,
        label = "press-scale",
    )
    return scale
}

@Composable
internal fun rememberPressScale(
    pressedScale: Float = 0.97f,
): Pair<Modifier, MutableInteractionSource> {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interactionSource = interactionSource, pressedScale = pressedScale)
    return Modifier.graphicsLayer { scaleX = scale; scaleY = scale } to interactionSource
}

@Composable
internal fun DragHandlePill(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(width = 36.dp, height = 4.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.outlineVariant,
        content = {},
    )
}

@Composable
internal fun SmoothProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    barHeight: Dp = 4.dp,
) {
    val safe = progress.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    val value by animateFloatAsState(safe, tween(AppMotion.duration, easing = LinearEasing), label = "playback-progress")
    Canvas(modifier = modifier.height(barHeight)) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(color = trackColor, size = size, cornerRadius = radius)
        val fillWidth = (size.width * value).coerceIn(0f, size.width)
        if (fillWidth > 0.5f) {
            drawRoundRect(color = color, size = Size(fillWidth, size.height), cornerRadius = radius)
        }
    }
}
