package com.kowloondairy.navprint.alert

import java.time.LocalDateTime
import java.time.LocalTime

/** The 未打印提醒 checks from here… */
val FIRST_CHECK: LocalTime = LocalTime.of(16, 0)

/** …to here, both included. */
val LAST_CHECK: LocalTime = LocalTime.of(18, 0)

const val MINUTES_BETWEEN_CHECKS = 5L

/** The first check strictly after [now]: later today if one is left, else tomorrow's [FIRST_CHECK]. */
fun nextCheck(now: LocalDateTime): LocalDateTime {
    val today = now.toLocalDate()
    var check = today.atTime(FIRST_CHECK)
    while (!check.isAfter(now)) check = check.plusMinutes(MINUTES_BETWEEN_CHECKS)
    return if (check.toLocalDate() == today && !check.toLocalTime().isAfter(LAST_CHECK)) check
    else today.plusDays(1).atTime(FIRST_CHECK)
}
