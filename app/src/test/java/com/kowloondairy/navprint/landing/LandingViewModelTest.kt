package com.kowloondairy.navprint.landing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LandingViewModelTest {

    private val today = LocalDate.of(2026, 10, 16)
    private val tomorrow = LocalDate.of(2026, 10, 17)

    @Test
    fun `starts on tomorrow, all categories, no delivery note chosen`() {
        val viewModel = LandingViewModel(today = today, deliveryNotes = { emptyList() })

        val state = viewModel.state.value
        assertEquals(tomorrow, state.deliveryDate)
        assertNull(state.category)
        assertNull(state.selectedDeliveryNote)
    }

    private val milkTomorrow = DeliveryNote("DN-001", tomorrow, Category.MILK)
    private val iceCreamTomorrow = DeliveryNote("DN-002", tomorrow, Category.ICE_CREAM)
    private val milkToday = DeliveryNote("DN-003", today, Category.MILK)
    private val allNotes = listOf(milkTomorrow, iceCreamTomorrow, milkToday)

    @Test
    fun `offers only delivery notes for the chosen date and category`() {
        val viewModel = LandingViewModel(today = today, deliveryNotes = { allNotes })

        assertEquals(listOf(milkTomorrow, iceCreamTomorrow), viewModel.state.value.deliveryNoteOptions)

        viewModel.chooseCategory(Category.ICE_CREAM)
        assertEquals(listOf(iceCreamTomorrow), viewModel.state.value.deliveryNoteOptions)

        viewModel.chooseCategory(null)
        viewModel.chooseDeliveryDate(today)
        assertEquals(listOf(milkToday), viewModel.state.value.deliveryNoteOptions)
    }

    @Test
    fun `changing the category clears the chosen delivery note`() {
        val viewModel = LandingViewModel(today = today, deliveryNotes = { allNotes })

        viewModel.chooseDeliveryNote(milkTomorrow)
        assertEquals(milkTomorrow, viewModel.state.value.selectedDeliveryNote)

        viewModel.chooseCategory(Category.MILK)
        assertNull(viewModel.state.value.selectedDeliveryNote)
    }

    @Test
    fun `changing the delivery date clears the chosen delivery note`() {
        val viewModel = LandingViewModel(today = today, deliveryNotes = { allNotes })

        viewModel.chooseDeliveryNote(milkTomorrow)
        assertEquals(milkTomorrow, viewModel.state.value.selectedDeliveryNote)

        viewModel.chooseDeliveryDate(today)
        assertNull(viewModel.state.value.selectedDeliveryNote)
    }

    @Test
    fun `print and preview are allowed only once a delivery note is chosen`() {
        val viewModel = LandingViewModel(today = today, deliveryNotes = { allNotes })

        assertFalse(viewModel.state.value.canPrint)

        viewModel.chooseDeliveryNote(milkTomorrow)
        assertTrue(viewModel.state.value.canPrint)
    }
}
