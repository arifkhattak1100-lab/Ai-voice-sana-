package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val SanaDarkColorScheme = darkColorScheme(
    primary = SanaPinkSecondary,
    onPrimary = Color(0xFF560027),
    primaryContainer = Color(0xFF7A0039),
    onPrimaryContainer = Color(0xFFFFD8E4),
    secondary = SanaPeachAccent,
    onSecondary = Color(0xFF442B00),
    secondaryContainer = Color(0xFF623F00),
    onSecondaryContainer = Color(0xFFFFDDB3),
    tertiary = SanaPurpleCardBorder,
    background = SanaPurpleDark,
    onBackground = SanaSoftWhite,
    surface = SanaPurpleSurface,
    onSurface = SanaSoftWhite,
    surfaceVariant = SanaPurpleCard,
    onSurfaceVariant = SanaSubtext,
    outline = Color(0x66FF80AB)
)

private val SanaLightColorScheme = lightColorScheme(
    primary = SanaPinkPrimary,
    onPrimary = Color.White,
    primaryContainer = SanaPinkContainer,
    onPrimaryContainer = SanaOnPinkContainer,
    secondary = SanaPeachAccent,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFFFFE0B2),
    onSecondaryContainer = Color(0xFF3E2723),
    background = Color(0xFFFFF7FA),
    onBackground = Color(0xFF261821),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF261821),
    surfaceVariant = Color(0xFFF9E8F0),
    onSurfaceVariant = Color(0xFF53434B),
    outline = Color(0x44FF4081)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to gorgeous romantic dark theme for SANA
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) SanaDarkColorScheme else SanaLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
