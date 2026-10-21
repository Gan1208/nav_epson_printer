package com.kowloondairy.navprint.receipt

import com.kowloondairy.navprint.landing.DeliveryNote
import java.math.BigDecimal
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the 客戶單 QR code opens. */
const val CONTACT_US_URL = "https://homedelivery.kowloondairy.com/contact-us"

/** Printed on every 客戶單. */
const val CUSTOMER_REMARKS =
    "Products must be placed in the refrigerator to avoid spoilage.\n請盡快將產品放入雪櫃，以免變壞。"

private val qrDateFormat = DateTimeFormatter.ofPattern("dd/MM/yy")
private val displayDateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/** Everything the three pages of a 收據 need for one 送貨單. */
data class Receipt(
    val deliveryNote: DeliveryNote,
    val customer: Customer,
    /** Location codes the goods ship from, sorted, each once. */
    val routes: List<String>,
    /** Only 訂單 with something to deliver. */
    val orders: List<ReceiptOrder>,
    val personalMessage: String?,
) {
    /** On the 回單 and 執貨單, e.g. `#20/10/26###HDDN230451799##`. */
    val returnQrContent: String
        get() = "#${deliveryNote.deliveryDate.format(qrDateFormat)}###${deliveryNote.number}##"

    /** e.g. `20 Oct 2026` */
    val deliveryDateText: String
        get() = deliveryNote.deliveryDate.format(displayDateFormat)

    /** e.g. `92`, or `92 / 93` for a milk 送貨單 shipping from both. */
    val routeText: String
        get() = routes.joinToString(" / ")
}

data class Customer(
    val name: String,
    val contact: String,
    val phone: String,
    val addressLines: List<String>,
)

data class ReceiptOrder(
    val number: String,
    val items: List<ReceiptItem>,
)

data class ReceiptItem(
    val quantity: BigDecimal,
    val englishName: String,
    val chineseName: String,
    /** Name on the 執貨單, e.g. `鮮奶 (大)`. */
    val shortName: String,
) {
    /** `4.000…` shows as `4`. */
    val quantityText: String
        get() = quantity.stripTrailingZeros().toPlainString()
}

/** One 送貨單 line joined with its 訂單 header, as NAV gives it. */
data class ReceiptRow(
    val orderNumber: String,
    val locationCode: String,
    val englishName: String,
    val chineseName: String,
    /** Blank when the item has none. */
    val shortChineseName: String,
    val quantity: BigDecimal,
    val name: String,
    val contact: String,
    val phone: String,
    val shipToAddress: String,
    val shipToAddress2: String,
    val billToAddress: String,
    val billToAddress2: String,
    val billToCity: String,
    val message: String?,
)

/** [rows] must all belong to [note] and be in line order; customer details come from the first. */
fun buildReceipt(note: DeliveryNote, rows: List<ReceiptRow>): Receipt {
    val first = rows.first()
    val orders = rows
        .filter { it.quantity.signum() != 0 }
        .groupBy { it.orderNumber }
        .map { (number, lines) ->
            ReceiptOrder(number, lines.map(::receiptItem))
        }
    return Receipt(
        deliveryNote = note,
        customer = Customer(
            name = first.name.trim(),
            contact = first.contact.trim(),
            phone = first.phone.trim(),
            addressLines = addressLines(first),
        ),
        routes = rows.map { it.locationCode.trim() }.distinct().sorted(),
        orders = orders,
        personalMessage = first.message?.trim()?.takeIf { it.length > 1 },
    )
}

private fun receiptItem(row: ReceiptRow): ReceiptItem {
    val chineseName = row.chineseName.trim()
    return ReceiptItem(
        quantity = row.quantity,
        englishName = row.englishName.trim(),
        chineseName = chineseName,
        shortName = row.shortChineseName.trim().ifEmpty { chineseName },
    )
}

private fun addressLines(row: ReceiptRow): List<String> =
    listOf(
        listOf(row.shipToAddress, row.shipToAddress2).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", "),
        row.billToAddress,
        row.billToAddress2,
        row.billToCity,
    ).map { it.trim() }.filter { it.isNotEmpty() }

interface ReceiptSource {
    /** The 收據 for [note]. */
    suspend fun load(note: DeliveryNote): Receipt
}
