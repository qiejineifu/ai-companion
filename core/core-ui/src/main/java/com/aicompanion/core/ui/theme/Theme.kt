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

private val LightColorScheme = lightColorScheme(
    primary = Pink500,
    onPrimary = Color.White,
    primaryContainer = Pink100,
    onPrimaryContainer = Pink700,
    secondary = Pink600,
    onSecondary = Color.White,
    secondaryContainer = Pink50,
    onSecondaryContainer = Pink700,
    tertiary = Purple400,
    onTertiary = Color.White,
    tertiaryContainer = Purple100,
    surface = ChatBg,
    surfaceVariant = Pink50,
    background = ChatBg,
    onBackground = TextDark,
    onSurface = TextDark,
    outline = TextGray,
    error = Color(0xFFE0386A)
)

private val DarkColorScheme = darkColorScheme(
    primary = Pink400,
    onPrimary = Color(0xFF2D1B2E),
    primaryContainer = Color(0xFF7A2E45),
    secondary = Pink400,
    onSecondary = Color(0xFF2D1B2E),
    secondaryContainer = Color(0xFF5A2040),
    tertiary = Purple400,
    tertiaryContainer = Color(0xFF3D2838),
    surface = Color(0xFF2D1B2E),
    surfaceVariant = Color(0xFF3D2838),
    background = Color(0xFF1A1020),
    onBackground = Color(0xFFFFE0EC),
    onSurface = Color(0xFFFFE0EC),
    outline = Color(0xFF9B8EA0),
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
            window.statusBarColor = Pink600.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
