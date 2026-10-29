package com.ujwal.focusgram.core.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = InstagramBlue,
    onPrimary = PureBlack,
    secondary = FocusGold,
    onSecondary = PureBlack,
    tertiary = FocusGreen,
    background = PureBlack,
    surface = DarkSurface,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    surfaceVariant = DarkCard,
    onSurfaceVariant = TextSecondary,
    outline = DarkBorder,
    error = AlertRed
)

private val LightColorScheme = lightColorScheme(
    primary = InstagramBlue,
    onPrimary = PureWhite,
    secondary = FocusGold,
    onSecondary = PureWhite,
    tertiary = FocusGreen,
    background = PureWhite,
    surface = PureWhite,
    onBackground = PureBlack,
    onSurface = PureBlack,
    surfaceVariant = LightGrayBg,
    onSurfaceVariant = SecondaryText,
    outline = LightBorder,
    error = AlertRed
)

@Composable
fun FocusGramTheme(
    content: @Composable () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val windowInsetsController = WindowCompat.getInsetsController(window, view)
        windowInsetsController.isAppearanceLightStatusBars = !isDark
        windowInsetsController.isAppearanceLightNavigationBars = !isDark
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
