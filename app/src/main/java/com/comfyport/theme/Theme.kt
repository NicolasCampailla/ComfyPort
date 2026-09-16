package com.comfyport.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalHighlightColor = staticCompositionLocalOf { AppHighlightColor.CYAN }

private fun getDarkColorScheme(highlightColor: AppHighlightColor) = darkColorScheme(
    primary = highlightColor.primary,
    onPrimary = highlightColor.onPrimary,
    secondary = highlightColor.gradientEnd,
    onSecondary = highlightColor.onPrimary,
    background = Black,
    onBackground = White,
    surface = DarkGray,
    onSurface = White,
    surfaceVariant = CardGray,
    onSurfaceVariant = LightGray,
    error = AccentRed,
    onError = Black
)

@Composable
fun ComfyPortTheme(
    highlightColor: AppHighlightColor = AppHighlightColor.CYAN,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalHighlightColor provides highlightColor
    ) {
        MaterialTheme(
            colorScheme = getDarkColorScheme(highlightColor),
            typography = Typography,
            content = content
        )
    }
}
