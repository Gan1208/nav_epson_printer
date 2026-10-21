package com.kowloondairy.navprint.receipt

import com.kowloondairy.navprint.landing.Category
import com.kowloondairy.navprint.landing.DeliveryNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class ReceiptTest {

    private val note = DeliveryNote("HDDN230451799", LocalDate.of(2026, 10, 20), Category.MILK, hasSubscriptionOrder = true)

    private fun row(
        order: String = "2000002892",
        location: String = "92",
        english: String = "KD FRESH MILK - 946ML",
        chinese: String = "維記鮮奶 946毫升",
        short: String = "鮮奶 (大)",
        quantity: String = "1.00000000000000000000",
        message: String? = "",
    ) = ReceiptRow(
        orderNumber = order,
        locationCode = location,
        englishName = english,
        chineseName = chinese,
        shortChineseName = short,
        quantity = BigDecimal(quantity),
        name = "Patrick Lau",
        contact = "劉日成",
        phone = "96508279",
        shipToAddress = "Room F",
        shipToAddress2 = "20/F",
        billToAddress = "BLK 26 LAGUNA CITY",
        billToAddress2 = "12 EAST LAGUNA STREET",
        billToCity = "KWUN TONG, KLN",
        message = message,
    )

    @Test
    fun `return QR code holds the delivery date and the whole 送貨單號`() {
        val receipt = buildReceipt(note, listOf(row()))

        assertEquals("#20/10/26###HDDN230451799##", receipt.returnQrContent)
    }

    @Test
    fun `delivery date reads like 20 Oct 2026`() {
        assertEquals("20 Oct 2026", buildReceipt(note, listOf(row())).deliveryDateText)
    }

    @Test
    fun `address joins the two ship-to parts and skips blank lines`() {
        val receipt = buildReceipt(note, listOf(row().copy(billToAddress2 = " ")))

        assertEquals(
            listOf("Room F, 20/F", "BLK 26 LAGUNA CITY", "KWUN TONG, KLN"),
            receipt.customer.addressLines,
        )
    }

    @Test
    fun `address uses the one ship-to part that is filled in`() {
        val receipt = buildReceipt(note, listOf(row().copy(shipToAddress2 = "")))

        assertEquals("Room F", receipt.customer.addressLines.first())
    }

    @Test
    fun `each 訂單 keeps its own items, in order`() {
        val receipt = buildReceipt(
            note,
            listOf(
                row(order = "2000002892", english = "A"),
                row(order = "2000002950", english = "B"),
                row(order = "2000002892", english = "C"),
            ),
        )

        assertEquals(listOf("2000002892", "2000002950"), receipt.orders.map { it.number })
        assertEquals(listOf("A", "C"), receipt.orders[0].items.map { it.englishName })
        assertEquals(listOf("B"), receipt.orders[1].items.map { it.englishName })
    }

    @Test
    fun `items with quantity 0 are left out, and so is a 訂單 with nothing left`() {
        val receipt = buildReceipt(
            note,
            listOf(
                row(order = "2000002892", english = "A"),
                row(order = "2000002892", english = "B", quantity = "0.000"),
                row(order = "2000002950", english = "C", quantity = "0"),
            ),
        )

        assertEquals(listOf("2000002892"), receipt.orders.map { it.number })
        assertEquals(listOf("A"), receipt.orders[0].items.map { it.englishName })
    }

    @Test
    fun `quantity shows without trailing zeros`() {
        val receipt = buildReceipt(note, listOf(row(quantity = "4.00000000000000000000")))

        assertEquals("4", receipt.orders[0].items[0].quantityText)
    }

    @Test
    fun `route lists every location once, joined with a slash`() {
        val receipt = buildReceipt(note, listOf(row(location = "93"), row(location = "92"), row(location = "93")))

        assertEquals("92 / 93", receipt.routeText)
    }

    @Test
    fun `personal message needs more than one character`() {
        assertNull(buildReceipt(note, listOf(row(message = "."))).personalMessage)
        assertNull(buildReceipt(note, listOf(row(message = null))).personalMessage)
        assertEquals(
            "10 Jan delivery",
            buildReceipt(note, listOf(row(message = "10 Jan delivery "))).personalMessage,
        )
    }

    @Test
    fun `packing note uses the item's short Chinese name`() {
        val item = buildReceipt(note, listOf(row(short = " 鮮奶 (大) "))).orders[0].items[0]

        assertEquals("鮮奶 (大)", item.shortName)
    }

    @Test
    fun `packing note falls back to the Chinese name when the item has no short name`() {
        val item = buildReceipt(note, listOf(row(short = ""))).orders[0].items[0]

        assertEquals("維記鮮奶 946毫升", item.shortName)
    }
}
