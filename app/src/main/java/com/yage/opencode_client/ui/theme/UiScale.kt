package com.yage.opencode_client.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class UiScale(val factor: Float = 1.0f) {
    fun scale(value: Dp): Dp = (value.value * factor).dp
}

val LocalUiScale = staticCompositionLocalOf { UiScale() }

@Composable
fun Dp.uiScaled(): Dp = LocalUiScale.current.scale(this)
