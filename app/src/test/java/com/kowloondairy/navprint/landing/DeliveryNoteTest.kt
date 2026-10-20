package com.kowloondairy.navprint.landing

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DeliveryNoteTest {

    private fun note(number: String, hasSubscriptionOrder: Boolean = false) =
        DeliveryNote(number, LocalDate.of(2026, 10, 19), Category.MILK, hasSubscriptionOrder)

    @Test
    fun `shows only the last four characters of the number`() {
        assertEquals("1798", note("HDDN230451798").displayNumber)
    }

    @Test
    fun `marks a 送貨單 with a 訂閱單 with two stars`() {
        assertEquals("1784**", note("HDDN230451784", hasSubscriptionOrder = true).displayNumber)
    }

    @Test
    fun `shows a short number whole`() {
        assertEquals("123", note("123").displayNumber)
    }
}
