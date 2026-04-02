package com.yage.opencode_client.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.m3.markdownTypography

/** Scale all font sizes in a Typography by the given multiplier. */
fun scaledTypography(base: Typography, scale: Float): Typography {
    if (scale == 1.0f) return base
    fun TextStyle.scaled() = copy(
        fontSize = fontSize * scale,
        lineHeight = lineHeight * scale
    )
    return base.copy(
        displayLarge = base.displayLarge.scaled(),
        displayMedium = base.displayMedium.scaled(),
        displaySmall = base.displaySmall.scaled(),
        headlineLarge = base.headlineLarge.scaled(),
        headlineMedium = base.headlineMedium.scaled(),
        headlineSmall = base.headlineSmall.scaled(),
        titleLarge = base.titleLarge.scaled(),
        titleMedium = base.titleMedium.scaled(),
        titleSmall = base.titleSmall.scaled(),
        bodyLarge = base.bodyLarge.scaled(),
        bodyMedium = base.bodyMedium.scaled(),
        bodySmall = base.bodySmall.scaled(),
        labelLarge = base.labelLarge.scaled(),
        labelMedium = base.labelMedium.scaled(),
        labelSmall = base.labelSmall.scaled()
    )
}

private fun TextStyle.compact(reference: TextStyle, target: TextStyle): TextStyle = copy(
    fontSize = fontSize * (target.fontSize.value / reference.fontSize.value),
    lineHeight = lineHeight * (target.lineHeight.value / reference.lineHeight.value)
)

/** Markdown typography with headers one size smaller than default. */
@Composable
fun markdownTypographyCompact() = markdownTypography(
    h1 = MaterialTheme.typography.headlineLarge,
    h2 = MaterialTheme.typography.headlineMedium,
    h3 = MaterialTheme.typography.headlineSmall,
    h4 = MaterialTheme.typography.titleLarge,
    h5 = MaterialTheme.typography.titleMedium,
    h6 = MaterialTheme.typography.titleSmall,
    text = MaterialTheme.typography.bodyLarge,
    code = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
    inlineCode = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
    quote = MaterialTheme.typography.bodyMedium,
    paragraph = MaterialTheme.typography.bodyLarge,
    ordered = MaterialTheme.typography.bodyLarge,
    bullet = MaterialTheme.typography.bodyLarge,
    list = MaterialTheme.typography.bodyLarge,
    table = MaterialTheme.typography.bodyLarge
)

/** Slightly smaller typography for Files and Chat columns in tablet layout. */
fun compactTypography(base: Typography): Typography = base.copy(
    bodyLarge = base.bodyLarge.compact(Typography.bodyLarge, Typography.bodyLarge.copy(fontSize = 12.sp, lineHeight = 18.sp)),
    bodyMedium = base.bodyMedium.compact(Typography.bodyMedium, Typography.bodyMedium.copy(fontSize = 11.sp, lineHeight = 16.sp)),
    bodySmall = base.bodySmall.compact(Typography.bodySmall, Typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 14.sp)),
    labelLarge = base.labelLarge.compact(Typography.labelLarge, Typography.labelLarge.copy(fontSize = 11.sp, lineHeight = 14.sp)),
    labelMedium = base.labelMedium.compact(Typography.labelMedium, Typography.labelMedium.copy(fontSize = 10.sp, lineHeight = 12.sp)),
    labelSmall = base.labelSmall.compact(Typography.labelSmall, Typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 12.sp)),
    titleLarge = base.titleLarge.compact(Typography.titleLarge, Typography.titleLarge.copy(fontSize = 16.sp, lineHeight = 22.sp)),
    titleMedium = base.titleMedium.compact(Typography.titleMedium, Typography.titleMedium.copy(fontSize = 14.sp, lineHeight = 20.sp)),
    titleSmall = base.titleSmall.compact(Typography.titleSmall, Typography.titleSmall.copy(fontSize = 12.sp, lineHeight = 18.sp))
)

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.5.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.25.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.25.sp
    )
)
