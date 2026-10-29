package com.kowloondairy.navprint.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density

/** The main page, text and boxes alike, is drawn this much bigger than the phone's own size. */
private const val PAGE_SCALE = 1.2f

/**
 * Draws [content] at the main page's size. Taken from the phone itself, not the screen around it,
 * so a dialog (its own window) comes out as big as the page under it.
 */
@Composable
fun ProvidePageScale(content: @Composable () -> Unit) {
    val phone = Density(LocalContext.current)
    CompositionLocalProvider(LocalDensity provides Density(phone.density * PAGE_SCALE, phone.fontScale), content = content)
}

/** Bold and 1.3 times bigger, so staff can read the 送貨單 list, and the print dialog, at a glance. */
@Composable
fun ProvideListText(content: @Composable () -> Unit) {
    val base = MaterialTheme.typography.bodyLarge
    val style = base.copy(
        fontSize = base.fontSize * 1.3f,
        lineHeight = base.lineHeight * 1.3f,
        fontWeight = FontWeight.Bold,
    )
    CompositionLocalProvider(LocalTextStyle provides style, content = content)
}
