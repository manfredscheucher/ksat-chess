package org.bytefred.satchess.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** A calm slate/teal Material3 scheme that matches the board colours. */
private val SatChessColors = lightColorScheme(
    primary = Color(0xFF2F5D62),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFE3E4),
    onPrimaryContainer = Color(0xFF0E2E31),
    secondary = Color(0xFF5B6F78),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE5E9),
    onSecondaryContainer = Color(0xFF1B2A31),
    tertiary = Color(0xFF8A6A2F),
    tertiaryContainer = Color(0xFFF6E7C8),
    onTertiaryContainer = Color(0xFF2E2208),
    background = Color(0xFFF6F7F8),
    onBackground = Color(0xFF1B1F22),
    surface = Color(0xFFFCFCFD),
    onSurface = Color(0xFF1B1F22),
    surfaceVariant = Color(0xFFE6EBEE),
    onSurfaceVariant = Color(0xFF46535A),
    surfaceContainerLow = Color(0xFFF1F4F5),
    surfaceContainer = Color(0xFFEBEFF1),
    outline = Color(0xFF8A979E),
    outlineVariant = Color(0xFFCCD5DA),
    error = Color(0xFFB3261E),
)

@Composable
fun SatChessTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SatChessColors, content = content)
}
