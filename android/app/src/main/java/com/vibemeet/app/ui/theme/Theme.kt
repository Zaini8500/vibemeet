package com.vibemeet.app.ui.theme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
private val LightColors = lightColorScheme(primary = Color(0xFF6C4CF1), secondary = Color(0xFFFF6B9D), tertiary = Color(0xFF00C2A8), background = Color(0xFFF7F5FF), surface = Color.White)
private val DarkColors = darkColorScheme(primary = Color(0xFF9B8CFF), secondary = Color(0xFFFF8FB3), tertiary = Color(0xFF3ED6BE), background = Color(0xFF12101A), surface = Color(0xFF1E1A2E))
@Composable fun VibeMeetTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, typography = Typography(), content = content)
}
