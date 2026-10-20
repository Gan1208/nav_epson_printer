package com.kowloondairy.navprint.landing

import java.time.LocalDate

enum class Category(val label: String) {
    MILK("牛奶"),
    ICE_CREAM("雪糕"),
}

data class DeliveryNote(
    val number: String,
    val deliveryDate: LocalDate,
    val category: Category,
    /** True when at least one 訂單 on this 送貨單 is a 訂閱單. */
    val hasSubscriptionOrder: Boolean = false,
) {
    /** What people see: the last four characters of [number], plus `**` for a 訂閱單. */
    val displayNumber: String
        get() = number.takeLast(4) + if (hasSubscriptionOrder) "**" else ""
}

interface DeliveryNoteSource {
    /** 送貨單 for [deliveryDate], sorted by number. [category] null means 全部. */
    suspend fun find(deliveryDate: LocalDate, category: Category?): List<DeliveryNote>
}
