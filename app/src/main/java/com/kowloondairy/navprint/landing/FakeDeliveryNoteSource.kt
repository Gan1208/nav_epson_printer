package com.kowloondairy.navprint.landing

import java.time.LocalDate

/** Placeholder until real 送貨單 data exists. */
class FakeDeliveryNoteSource(today: LocalDate) : DeliveryNoteSource {

    private val tomorrow = today.plusDays(1)

    private val notes = listOf(
        DeliveryNote("DN-0001", tomorrow, Category.MILK),
        DeliveryNote("DN-0002", tomorrow, Category.MILK),
        DeliveryNote("DN-0003", tomorrow, Category.ICE_CREAM),
        DeliveryNote("DN-0004", today, Category.MILK),
        DeliveryNote("DN-0005", today, Category.ICE_CREAM),
    )

    override fun all(): List<DeliveryNote> = notes
}
