package com.eslam.check.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

val Navy = Color(0xFF123B5D)
val Gold = Color(0xFFD4A84F)
val Good = Color(0xFF2E7D32)
val Warn = Color(0xFFB26A00)
val Bad = Color(0xFFB3261E)
val Mystery = Color(0xFF6A4C93)

private val MoneyLight = lightColorScheme(
    primary = Color(0xFF34638A), onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E8F8), onPrimaryContainer = Navy,
    secondary = Color(0xFF48758F), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9E8F8), onSecondaryContainer = Navy,
    tertiary = Color(0xFF9870B7), tertiaryContainer = Color(0xFFEFE5F8),
    onTertiaryContainer = Color(0xFF573E70),
    background = Color(0xFFF5F6FB), onBackground = Color(0xFF252C32),
    surface = Color.White, onSurface = Color(0xFF252C32),
    surfaceVariant = Color(0xFFEEF2F6), onSurfaceVariant = Color(0xFF5C6974),
    surfaceTint = Color.Transparent, outline = Color(0xFF8C99A5), outlineVariant = Color(0xFFE1E5EB),
    surfaceContainer = Color(0xFFEDF0F6), surfaceContainerLow = Color.White,
    surfaceContainerHigh = Color(0xFFE9EEF4), surfaceContainerHighest = Color(0xFFE1E8F0),
    error = Bad, onError = Color.White, errorContainer = Color(0xFFFCE5E3), onErrorContainer = Bad
)

private val NavyLight = lightColorScheme(
    primary = Navy,
    secondary = Gold,
    tertiary = Mystery,
    error = Bad,
    surface = Color(0xFFF8FAFC),
    background = Color(0xFFF5F7FA),
    onSurface = Color(0xFF17212B),
    onBackground = Color(0xFF17212B)
)

private val NavyDark = darkColorScheme(
    primary = Color(0xFF9CCAF0),
    secondary = Gold,
    tertiary = Color(0xFFC8A7EA),
    error = Color(0xFFFFB4AB),
    surface = Color(0xFF17212B),
    background = Color(0xFF101820),
    onSurface = Color(0xFFF2EEF7),
    onBackground = Color(0xFFF2EEF7)
)

private val Midnight = darkColorScheme(
    primary = Color(0xFF9B8CFF),
    secondary = Color(0xFF7ED7C4),
    tertiary = Color(0xFFC7A8FF),
    surface = Color(0xFF171721),
    background = Color(0xFF0C0C12),
    onSurface = Color(0xFFF4F1FA),
    onBackground = Color(0xFFF4F1FA)
)

private val Emerald = darkColorScheme(
    primary = Color(0xFF76D7A8),
    secondary = Color(0xFFC6B36A),
    tertiary = Color(0xFF87CBE8),
    surface = Color(0xFF15221E),
    background = Color(0xFF0E1714),
    onSurface = Color(0xFFF0F8F4),
    onBackground = Color(0xFFF0F8F4)
)

private val Graphite = darkColorScheme(
    primary = Color(0xFFB8C2CC),
    secondary = Color(0xFF90CAF9),
    tertiary = Color(0xFFD0BCFF),
    surface = Color(0xFF20242A),
    background = Color(0xFF14171B),
    onSurface = Color(0xFFF2F4F6),
    onBackground = Color(0xFFF2F4F6)
)

private val Sand = lightColorScheme(
    primary = Color(0xFF7A5631),
    secondary = Color(0xFF9A7B4F),
    tertiary = Color(0xFF6E6A86),
    surface = Color(0xFFFFFBF2),
    background = Color(0xFFF4EEDF),
    onSurface = Color(0xFF2E2822),
    onBackground = Color(0xFF2E2822)
)

@Composable
fun EslamCheckTheme(
    preset: String = "MONEY",
    customPrimary: String = "",
    customBackground: String = "",
    customSurface: String = "",
    customText: String = "",
    fontChoice: String = "SANS",
    fontScale: Float = 1f,
    content: @Composable () -> Unit
) {
    val base = when (preset.uppercase()) {
        "MONEY" -> MoneyLight
        "MIDNIGHT" -> Midnight
        "EMERALD" -> Emerald
        "GRAPHITE" -> Graphite
        "SAND" -> Sand
        else -> if (isSystemInDarkTheme()) NavyDark else NavyLight
    }

    val text = colorFromHex(customText) ?: base.onSurface
    val scheme = base.copy(
        primary = colorFromHex(customPrimary) ?: base.primary,
        background = colorFromHex(customBackground) ?: base.background,
        surface = colorFromHex(customSurface) ?: base.surface,
        onBackground = text,
        onSurface = text
    )

    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        val window = (view.context as? android.app.Activity)?.window
        if (window != null) WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = scheme.background.luminance() > 0.5f
            isAppearanceLightNavigationBars = scheme.background.luminance() > 0.5f
        }
    }
    val family = when (fontChoice.uppercase()) {
        "SERIF" -> FontFamily.Serif
        "MONO" -> FontFamily.Monospace
        else -> FontFamily.SansSerif
    }
    val scale = fontScale.coerceIn(0.85f, 1.35f)
    val typography = Typography(
        bodyLarge = TextStyle(fontFamily = family, fontSize = (16f * scale).sp),
        bodyMedium = TextStyle(fontFamily = family, fontSize = (14f * scale).sp),
        bodySmall = TextStyle(fontFamily = family, fontSize = (12f * scale).sp),
        titleLarge = TextStyle(fontFamily = family, fontSize = (22f * scale).sp),
        titleMedium = TextStyle(fontFamily = family, fontSize = (16f * scale).sp),
        titleSmall = TextStyle(fontFamily = family, fontSize = (14f * scale).sp),
        labelLarge = TextStyle(fontFamily = family, fontSize = (14f * scale).sp),
        labelMedium = TextStyle(fontFamily = family, fontSize = (12f * scale).sp),
        labelSmall = TextStyle(fontFamily = family, fontSize = (11f * scale).sp)
    )
    MaterialTheme(colorScheme = scheme, typography = typography,
        shapes = Shapes(extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp)),
        content = content)
}

fun colorFromHex(value: String): Color? {
    val clean = value.trim().removePrefix("#")
    if (clean.length !in setOf(6, 8)) return null
    return try {
        val number = clean.toLong(16)
        if (clean.length == 6) Color(0xFF000000 or number) else Color(number)
    } catch (_: Exception) {
        null
    }
}

