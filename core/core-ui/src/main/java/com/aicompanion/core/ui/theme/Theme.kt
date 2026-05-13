package com.aicompanion.core.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Anime pink palette
private val Pink500 = Color(0xFFFF6B8A)
private val Pink50 = Color(0xFFFFF0F5)
private val Pink100 = Color(0xFFFFE0EC)
private val Purple500 = Color(0xFF9C6B9E)
private val SurfaceLight = Color(0xFFFFF5F7)
private val SurfaceDark = Color(0xFF2D1B2E)
private val TextDark = Color(0xFF2D1B2E)

private val LightColorScheme = lightColorScheme(
    primary = Pink500,
    onPrimary = Color.White,
    primaryContainer = Pink100,
    secondary = Purple500,
    tertiary = Color(0xFFC4A5E8),
    surface = SurfaceLight,
    surfaceVariant = Color(0xFFF3E8FF),
    background = SurfaceLight,
    error = Color(0xFFBA1A1A)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFF85A2),
    onPrimary = SurfaceDark,
    primaryContainer = Color(0xFF7A2E45),
    secondary = Color(0xFFD4BCE0),
    tertiary = Color(0xFFE8D5F5),
    surface = SurfaceDark,
    surfaceVariant = Color(0xFF3D2838),
    background = SurfaceDark,
    error = Color(0xFFFFB4AB)
)

@Composable
fun AICompanionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
