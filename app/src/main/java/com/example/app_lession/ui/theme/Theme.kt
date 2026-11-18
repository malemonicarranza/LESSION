package com.example.app_lession.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = LessionYellow,
    onPrimary = LessionInk,
    secondary = LessionVioletLight,
    onSecondary = LessionInk,
    tertiary = LessionYellow,
    background = LessionSurfaceDark,
    surface = LessionSurfaceDark,
    onBackground = LessionOnSurfaceDark,
    onSurface = LessionOnSurfaceDark
)

private val LightColorScheme = lightColorScheme(
    primary = LessionViolet,
    onPrimary = LessionWhite,
    secondary = LessionYellow,
    onSecondary = LessionInk,
    tertiary = LessionVioletDark,
    background = LessionSurface,
    surface = LessionWhite,
    onBackground = LessionInk,
    onSurface = LessionInk
)

@Composable
fun AppLessionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}