package com.kowloondairy.navprint.receipt

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private val dateFormat = DateTimeFormatter.ISO_LOCAL_DATE

/**
 * Before printing: the line telling staff which 送貨日 they are about to print, when it is not
 * tomorrow or later. Null means print without asking.
 */
fun earlyDeliveryDateLine(deliveryDate: LocalDate, today: LocalDate): String? {
    if (deliveryDate > today) return null
    val weekday = deliveryDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.TRADITIONAL_CHINESE)
    val daysAgo = ChronoUnit.DAYS.between(deliveryDate, today)
    val ago = when (daysAgo) {
        0L -> "今天"
        1L -> "昨天"
        else -> "$daysAgo 日前"
    }
    return "送貨日：${deliveryDate.format(dateFormat)}（$weekday，$ago）"
}
