package com.kowloondairy.navprint.receipt

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density

/** At this density [PaperWidth] is exactly the 576 dots of one print line. */
private val printDensity = Density(2f)

/**
 * Lays out every page of [receipt] at print size without showing it,
 * recording each into its layer so [printImage] can turn it into a picture for the printer.
 */
@Composable
fun PrintPages(receipt: Receipt, layers: Map<ReceiptPage, GraphicsLayer>) {
    CompositionLocalProvider(LocalDensity provides printDensity) {
        Layout(
            content = {
                ReceiptPage.entries.forEach { page ->
                    val layer = layers.getValue(page)
                    ReceiptPaper(
                        receipt,
                        page,
                        // Record only: the layer is never drawn on screen.
                        Modifier.drawWithContent { layer.record { this@drawWithContent.drawContent() } },
                    )
                }
            },
        ) { measurables, _ ->
            // Pages keep their own size but take no room in the screen around them.
            val placeables = measurables.map { it.measure(Constraints()) }
            layout(0, 0) { placeables.forEach { it.place(0, 0) } }
        }
    }
}

/** The page recorded by [PrintPages], as a picture the printer can take. */
suspend fun GraphicsLayer.printImage(): Bitmap {
    val image = toImageBitmap().asAndroidBitmap()
    // Pixels of a hardware bitmap cannot be read, and the printer needs to read them.
    return if (image.config == Bitmap.Config.HARDWARE) image.copy(Bitmap.Config.ARGB_8888, false) else image
}
