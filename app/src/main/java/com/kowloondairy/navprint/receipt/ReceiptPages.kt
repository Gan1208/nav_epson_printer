package com.kowloondairy.navprint.receipt

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.kowloondairy.navprint.R

/**
 * Printable width of 80mm paper on the Epson TM-m30II: 576 dots.
 * Drawn at 2 px per dp, so a page captured at density 2 is exactly one print line wide.
 */
val PaperWidth = 288.dp

private val ink = Color.Black
private val bodyStyle = TextStyle(color = ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, lineHeight = 15.sp)
private val packingStyle = TextStyle(color = ink, fontSize = 33.sp, fontWeight = FontWeight.Bold, lineHeight = 45.sp)
private val labelWidth = 58.dp
private val qrSize = 84.dp

enum class ReceiptPage(val label: String) {
    RETURN("回單"),
    PACKING("執貨單"),
    CUSTOMER("客戶單"),
}

/** One page of the 收據, as it will look on the paper. */
@Composable
fun ReceiptPaper(receipt: Receipt, page: ReceiptPage, modifier: Modifier = Modifier) {
    // Paper does not grow with the phone's font size setting.
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, fontScale = 1f),
        LocalTextStyle provides bodyStyle,
    ) {
        Column(
            modifier = modifier
                .width(PaperWidth)
                .background(Color.White)
                .padding(horizontal = 12.dp, vertical = 16.dp),
        ) {
            when (page) {
                ReceiptPage.RETURN -> ReturnPage(receipt)
                ReceiptPage.PACKING -> PackingPage(receipt)
                ReceiptPage.CUSTOMER -> CustomerPage(receipt)
            }
        }
    }
}

@Composable
private fun ColumnScope.ReturnPage(receipt: Receipt) {
    DeliveryDetails(receipt)
    Gap(16.dp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            RouteAndId(receipt)
        }
        QrCode(receipt.returnQrContent, qrSize)
    }
    Gap(16.dp)
    Text("Signature :", fontSize = 13.sp, fontWeight = FontWeight.Bold)
    Gap(20.dp)
    Text("Bottle Return Qty :", fontSize = 13.sp, fontWeight = FontWeight.Bold)
    Gap(48.dp)
    Footer("-- For internal usage --")
}

@Composable
private fun ColumnScope.CustomerPage(receipt: Receipt) {
    DeliveryDetails(receipt)
    Gap(16.dp)
    Row {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            RouteAndId(receipt)
            Gap(2.dp)
            Text("Remarks:")
            Text(CUSTOMER_REMARKS)
            receipt.personalMessage?.let {
                Gap(4.dp)
                Text(it)
            }
        }
        Spacer(Modifier.width(8.dp))
        QrCode(CONTACT_US_URL, qrSize)
    }
    Gap(48.dp)
    Footer("-- Thank you --")
}

@Composable
private fun ColumnScope.PackingPage(receipt: Receipt) {
    CompositionLocalProvider(LocalTextStyle provides packingStyle) {
        DashedLine()
        Gap(12.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(receipt.deliveryDateText, Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                QrCode(receipt.returnQrContent, qrSize)
                Gap(8.dp)
                Text(receipt.deliveryNote.displayNumber)
            }
        }
        Gap(8.dp)
        receipt.orders.forEach { order ->
            Text(order.number)
            Rule()
            order.items.forEach { item ->
                ItemLine(item.quantityText, 60.dp) { Text(item.shortName) }
                Rule()
            }
        }
        Gap(16.dp)
        DashedLine()
    }
}

/** Logo, date, customer and goods: the same on the 回單 and 客戶單. */
@Composable
private fun ColumnScope.DeliveryDetails(receipt: Receipt) {
    Image(
        painter = painterResource(R.drawable.kdl_receipt_logo),
        contentDescription = null,
        // 45% of the paper's width, centred.
        modifier = Modifier.requiredWidth(PaperWidth * 0.45f).align(Alignment.CenterHorizontally),
    )
    Gap(20.dp)
    Text("Delivery Date :  ${receipt.deliveryDateText}")
    Gap(12.dp)
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Field("Name :", receipt.customer.name)
        Field("Contact :", receipt.customer.contact)
        Field("Phone :", receipt.customer.phone)
        Row {
            Text("Address :", Modifier.width(labelWidth))
            Column {
                receipt.customer.addressLines.forEachIndexed { i, line ->
                    // Only the first line sits next to the label; the rest are indented under it.
                    Text(line, Modifier.padding(start = if (i == 0) 0.dp else 12.dp))
                }
            }
        }
    }
    receipt.orders.forEach { order ->
        Gap(16.dp)
        Text(order.number)
        order.items.forEach { item ->
            Gap(4.dp)
            ItemLine(item.quantityText, 20.dp) {
                Column {
                    Text(item.englishName)
                    Text(item.chineseName)
                }
            }
        }
    }
}

@Composable
private fun RouteAndId(receipt: Receipt) {
    Text("Route :  ${receipt.routeText}")
    Text("Delivery ID :  ${receipt.deliveryNote.displayNumber}")
}

@Composable
private fun Field(label: String, value: String) {
    Row {
        Text(label, Modifier.width(labelWidth))
        Text(value)
    }
}

@Composable
private fun ItemLine(quantity: String, quantityWidth: Dp, name: @Composable () -> Unit) {
    Row {
        Text(quantity, Modifier.width(quantityWidth))
        Text("x  ")
        name()
    }
}

@Composable
private fun ColumnScope.Footer(text: String) {
    Text(text, Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
}

@Composable
private fun Gap(height: Dp) = Spacer(Modifier.height(height))

@Composable
private fun Rule() = HorizontalDivider(thickness = 1.dp, color = ink)

@Composable
private fun DashedLine() {
    Canvas(Modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            color = ink,
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = size.height,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)),
        )
    }
}

@Composable
private fun QrCode(content: String, size: Dp) {
    val matrix = remember(content) {
        QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0))
    }
    Canvas(Modifier.size(size)) {
        val cell = this.size.width / matrix.width
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) {
                    // A hair wider than one cell, so neighbouring cells leave no white seams.
                    drawRect(ink, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
                }
            }
        }
    }
}
