package com.kowloondairy.navprint.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Company colours: red and white. */
val CompanyRed = Color(0xFFB0141B)
private val DarkRed = Color(0xFF6E0B0F)
private val PaleRed = Color(0xFFFDE4E5)

private val colors = lightColorScheme(
    primary = CompanyRed,
    onPrimary = Color.White,
    primaryContainer = PaleRed,
    onPrimaryContainer = DarkRed,
    secondary = CompanyRed,
    onSecondary = Color.White,
    secondaryContainer = PaleRed,
    onSecondaryContainer = DarkRed,
    background = Color.White,
    onBackground = Color(0xFF1C1B1B),
    surface = Color.White,
    onSurface = Color(0xFF1C1B1B),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White,
)

@Composable
fun NavPrintTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
