package com.kowloondairy.navprint.landing

import java.time.LocalDate

enum class Category(val label: String) {
    MILK("牛奶"),
    ICE_CREAM("雪糕"),
}

/** The name staff see for a 類別, or 全部 for null. */
val Category?.choiceLabel: String
    get() = this?.label ?: "全部"

data class DeliveryNote(
    val number: String,
    val deliveryDate: LocalDate,
    val category: Category,
    /** True when at least one 訂單 on this 送貨單 is a 訂閱單. */
    val hasSubscriptionOrder: Boolean = false,
    /** Every 訂單 on this 送貨單, smallest first. */
    val orderNumbers: List<String> = emptyList(),
    /** The name the 收據 shows: from the first of [orderNumbers]. */
    val customerName: String = "",
    /** 已打印: its whole 收據 was printed, and it has not changed since. */
    val printed: Boolean = false,
) {
    /** What people see: the last four characters of [number], plus `**` for a 訂閱單. */
    val displayNumber: String
        get() = number.takeLast(4) + if (hasSubscriptionOrder) "**" else ""
}

interface DeliveryNoteSource {
    /** 送貨單 for [deliveryDate], sorted by number. [category] null means 全部. */
    suspend fun find(deliveryDate: LocalDate, category: Category?): List<DeliveryNote>
}
