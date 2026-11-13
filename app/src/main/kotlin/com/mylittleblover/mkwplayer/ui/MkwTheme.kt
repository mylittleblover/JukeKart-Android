package com.mylittleblover.mkwplayer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Selected design A: white surfaces and a restrained red accent, including under system dark mode. */
private val mkwColors = lightColorScheme(
    primary = Color(0xFFC52D49), onPrimary = Color.White,
    primaryContainer = Color(0xFFFCE8ED), onPrimaryContainer = Color(0xFF851C32),
    secondary = Color(0xFF5E626E), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0F1F5), onSecondaryContainer = Color(0xFF30343E),
    background = Color.White, onBackground = Color(0xFF20232B),
    surface = Color.White, onSurface = Color(0xFF20232B),
    surfaceVariant = Color(0xFFF0F1F5), onSurfaceVariant = Color(0xFF626672),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF8F8FA),
    surfaceContainer = Color(0xFFF5F5F8), surfaceContainerHigh = Color(0xFFF0F1F5),
    surfaceContainerHighest = Color(0xFFE8E9EF),
    outline = Color(0xFF747884), outlineVariant = Color(0xFFE2E4EA),
)

@Composable
internal fun MkwTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = mkwColors, content = content)
}
