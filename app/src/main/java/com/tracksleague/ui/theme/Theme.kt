package com.tracksleague.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LocalTracksThemeColors = staticCompositionLocalOf { LocalLightTracksColors }

object TracksTheme {
    val colors: TracksColors
        @Composable
        @ReadOnlyComposable
        get() = LocalTracksThemeColors.current
}

private val LightColorScheme = lightColorScheme(
    primary = LightAccent,
    onPrimary = LightPanel,
    secondary = LightClueRail,
    background = LightBg,
    onBackground = LightInk,
    surface = LightPanel,
    onSurface = LightInk,
    outline = LightLine
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkAccent,
    onPrimary = DarkPanel,
    secondary = DarkClueRail,
    background = DarkBg,
    onBackground = DarkInk,
    surface = DarkPanel,
    onSurface = DarkInk,
    outline = DarkLine
)

@Composable
fun TracksLeagueTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val tracksColors = if (darkTheme) LocalDarkTracksColors else LocalLightTracksColors
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = tracksColors.bg.toArgb()
            window.navigationBarColor = tracksColors.bg.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalTracksThemeColors provides tracksColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
