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
)

fun interface DeliveryNoteSource {
    fun all(): List<DeliveryNote>
}
