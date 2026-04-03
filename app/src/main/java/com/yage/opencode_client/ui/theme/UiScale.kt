package com.yage.opencode_client.ui.theme

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class UiScale(val factor: Float = 1.0f) {
    fun scale(value: Dp): Dp = (value.value * factor).dp
}

val LocalUiScale = staticCompositionLocalOf { UiScale() }

@Composable
fun Dp.uiScaled(): Dp = LocalUiScale.current.scale(this)

@Composable
fun ProvideScaledDpDensity(
    content: @Composable () -> Unit
) {
    val uiScale = LocalUiScale.current.factor
    val density = LocalDensity.current
    val scaledDensity = remember(density, uiScale) {
        Density(
            density = density.density * uiScale,
            fontScale = density.fontScale / uiScale
        )
    }
    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        content()
    }
}
