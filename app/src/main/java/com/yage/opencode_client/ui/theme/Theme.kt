package com.yage.opencode_client.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = BrandPrimary,
    onPrimary = OnSurfaceDark,
    secondary = BrandPrimary,
    tertiary = BrandGold,
    error = StopRed,
    background = BgDark,
    onBackground = OnSurfaceDark,
    surface = BgDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    surfaceContainer = SurfaceDark,
    surfaceContainerLow = ComposerDark,
    surfaceContainerLowest = BgDark,
    surfaceContainerHigh = SurfaceDark,
    surfaceContainerHighest = SurfaceDark,
    primaryContainer = BrandPrimary,
    onPrimaryContainer = OnSurfaceDark,
    outline = OutlineDark,
    outlineVariant = OutlineDark
)

private val LightColorScheme = lightColorScheme(
    primary = BrandPrimaryLight,
    onPrimary = BgLight,
    secondary = BrandPrimaryLight,
    tertiary = BrandGold,
    error = StopRed,
    background = BgLight,
    onBackground = OnSurfaceLight,
    surface = BgLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    surfaceContainer = SurfaceLight,
    surfaceContainerLow = ComposerLight,
    surfaceContainerLowest = BgLight,
    surfaceContainerHigh = SurfaceLight,
    surfaceContainerHighest = SurfaceLight,
    primaryContainer = BrandPrimaryLight,
    onPrimaryContainer = BgLight,
    outline = OutlineLight,
    outlineVariant = OutlineLight
)

@Composable
fun OpenCodeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    fontSizeScale: Float = 1.0f,
    uiScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val typography = scaledTypography(Typography, fontSizeScale)

    CompositionLocalProvider(LocalUiScale provides UiScale(uiScale)) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = colorScheme.background
            ) {
                content()
            }
        }
    }
}
