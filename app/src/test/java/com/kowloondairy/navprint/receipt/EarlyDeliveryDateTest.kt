package com.kowloondairy.navprint.receipt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class EarlyDeliveryDateTest {

    private val today = LocalDate.of(2026, 11, 9)

    @Test
    fun `tomorrow needs no asking`() {
        assertNull(earlyDeliveryDateLine(LocalDate.of(2026, 11, 10), today))
    }

    @Test
    fun `today is named as today`() {
        assertEquals("送貨日：2026-11-09（星期一，今天）", earlyDeliveryDateLine(today, today))
    }

    @Test
    fun `yesterday is named as yesterday`() {
        assertEquals("送貨日：2026-11-08（星期日，昨天）", earlyDeliveryDateLine(LocalDate.of(2026, 11, 8), today))
    }

    @Test
    fun `earlier days say how many days ago`() {
        assertEquals("送貨日：2026-10-30（星期五，10 日前）", earlyDeliveryDateLine(LocalDate.of(2026, 10, 30), today))
    }
}
