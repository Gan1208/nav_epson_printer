package com.kowloondairy.navprint.alert

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class CheckTimesTest {

    private val day = LocalDate.of(2026, 10, 27)
    private fun at(hour: Int, minute: Int, second: Int = 0): LocalDateTime = day.atTime(hour, minute, second)
    private val tomorrowFirst = day.plusDays(1).atTime(16, 0)

    @Test
    fun `before 4 the first check is 4 today`() {
        assertEquals(at(16, 0), nextCheck(at(9, 30)))
        assertEquals(at(16, 0), nextCheck(at(15, 59, 59)))
    }

    @Test
    fun `between 4 and 6 it checks every 5 minutes`() {
        assertEquals(at(16, 5), nextCheck(at(16, 0)))
        assertEquals(at(16, 35), nextCheck(at(16, 31)))
        assertEquals(at(18, 0), nextCheck(at(17, 55)))
    }

    @Test
    fun `from 6 the next check is 4 tomorrow`() {
        assertEquals(tomorrowFirst, nextCheck(at(18, 0)))
        assertEquals(tomorrowFirst, nextCheck(at(23, 59)))
    }
}
