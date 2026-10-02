package com.eslam.check.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Navy = Color(0xFF123B5D)
val Gold = Color(0xFFD4A84F)
val Good = Color(0xFF2E7D32)
val Warn = Color(0xFFB26A00)
val Bad = Color(0xFFB3261E)
val Mystery = Color(0xFF6A4C93)

private val Light = lightColorScheme(
    primary = Navy,
    secondary = Gold,
    tertiary = Mystery,
    error = Bad,
    surface = Color(0xFFF8FAFC),
    background = Color(0xFFF5F7FA)
)

private val Dark = darkColorScheme(
    primary = Color(0xFF9CCAF0),
    secondary = Gold,
    tertiary = Color(0xFFC8A7EA),
    error = Color(0xFFFFB4AB),
    surface = Color(0xFF17212B),
    background = Color(0xFF101820)
)

@Composable
fun EslamCheckTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
