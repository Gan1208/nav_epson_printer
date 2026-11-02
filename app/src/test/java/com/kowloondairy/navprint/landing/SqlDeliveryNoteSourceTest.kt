package com.kowloondairy.navprint.landing

import com.kowloondairy.navprint.landing.SqlDeliveryNoteSource.Companion.categoryOf
import com.kowloondairy.navprint.landing.SqlDeliveryNoteSource.Companion.locationCodesFor
import com.kowloondairy.navprint.landing.SqlDeliveryNoteSource.Companion.mergeByNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class SqlDeliveryNoteSourceTest {

    @Test
    fun `milk ships from 92 and 93, ice cream from T1`() {
        assertEquals(listOf("92", "93"), locationCodesFor(Category.MILK))
        assertEquals(listOf("T1"), locationCodesFor(Category.ICE_CREAM))
        assertEquals(Category.MILK, categoryOf("92"))
        assertEquals(Category.MILK, categoryOf("93"))
        assertEquals(Category.ICE_CREAM, categoryOf("T1"))
    }

    @Test
    fun `all categories covers only the known locations`() {
        assertEquals(listOf("92", "93", "T1"), locationCodesFor(null))
        assertNull(categoryOf("99"))
    }

    @Test
    fun `a 送貨單 in both 92 and 93 is one 送貨單, marked if either has a 訂閱單`() {
        val day = LocalDate.of(2026, 10, 19)
        val rows = listOf(
            DeliveryNote("HDDN1", day, Category.MILK, hasSubscriptionOrder = false),
            DeliveryNote("HDDN1", day, Category.MILK, hasSubscriptionOrder = true),
            DeliveryNote("HDDN2", day, Category.MILK, hasSubscriptionOrder = false),
        )

        assertEquals(
            listOf(
                DeliveryNote("HDDN1", day, Category.MILK, hasSubscriptionOrder = true),
                DeliveryNote("HDDN2", day, Category.MILK, hasSubscriptionOrder = false),
            ),
            mergeByNumber(rows),
        )
    }

    @Test
    fun `a 送貨單 lists each of its 訂單 once and is named after the first`() {
        val day = LocalDate.of(2026, 10, 19)
        fun row(location: String, order: String, name: String) =
            DeliveryNote("HDDN1", day, Category.MILK, orderNumbers = listOf(order), customerName = "$name@$location")
        // 92 and 93 each carry part of SO2; SO1 only ships from 93.
        val rows = listOf(row("92", "SO2", "Chan"), row("93", "SO1", "Wong"), row("93", "SO2", "Chan"))

        val merged = mergeByNumber(rows).single()

        assertEquals(listOf("SO1", "SO2"), merged.orderNumbers)
        assertEquals("Wong@93", merged.customerName)
    }

    @Test
    fun `a 送貨單 is 已打印 only when every 訂單 and location on it is`() {
        val day = LocalDate.of(2026, 10, 19)
        fun row(number: String, order: String, printed: Boolean) =
            DeliveryNote(number, day, Category.MILK, orderNumbers = listOf(order), printed = printed)
        val rows = listOf(
            row("HDDN1", "SO1", printed = true),
            row("HDDN1", "SO2", printed = false),
            row("HDDN2", "SO3", printed = true),
            row("HDDN2", "SO3", printed = true),
        )

        assertEquals(listOf(false, true), mergeByNumber(rows).map { it.printed })
    }

    @Test
    fun `connection url names the instance only when there is one`() {
        val settings = SqlConnectionSettings("10.0.0.5", 1433, "", "KDLNAV_HD", "u", "p")

        assertEquals(
            "jdbc:jtds:sqlserver://10.0.0.5:1433/KDLNAV_HD;loginTimeout=10;socketTimeout=10",
            settings.jdbcUrl,
        )
        assertEquals(
            "jdbc:jtds:sqlserver://10.0.0.5:1433/KDLNAV_HD;instance=NAV;loginTimeout=10;socketTimeout=10",
            settings.copy(instance = "NAV").jdbcUrl,
        )
    }
}
