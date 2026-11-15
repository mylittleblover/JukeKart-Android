package com.mylittleblover.mkwplayer.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

/** Android's animator duration scale is honored by Compose, including disabled system animations. */
internal object AppMotion {
    val easeOut = CubicBezierEasing(.23f, 1f, .32f, 1f)
    const val duration = 220
    fun page(direction: Int): ContentTransform {
        if (direction == 0) return (fadeIn(tween(150)) togetherWith ExitTransition.None)
        return ((slideInHorizontally(tween(duration, easing = easeOut)) { direction * it / 5 } + fadeIn(tween(180))) togetherWith
            ExitTransition.None)
    }
}

@Composable
internal fun SmoothProgressIndicator(progress: Float, modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary, trackColor: Color = MaterialTheme.colorScheme.surfaceVariant) {
    val safe = progress.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    val value by animateFloatAsState(safe, tween(AppMotion.duration, easing = LinearEasing), label = "playback-progress")
    LinearProgressIndicator(progress = { value }, modifier = modifier, color = color, trackColor = trackColor)
}
