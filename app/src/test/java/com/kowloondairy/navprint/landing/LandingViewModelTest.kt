package com.kowloondairy.navprint.landing

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class LandingViewModelTest {

    private val today = LocalDate.of(2026, 10, 16)
    private val tomorrow = LocalDate.of(2026, 10, 17)

    private val milkTomorrow = DeliveryNote("D001", tomorrow, Category.MILK)
    private val iceCreamTomorrow = DeliveryNote("D002", tomorrow, Category.ICE_CREAM)
    private val milkToday = DeliveryNote("D003", today, Category.MILK)

    /** Answers like the real database would, and remembers what it was asked. */
    private class FakeSource(private val notes: List<DeliveryNote>) : DeliveryNoteSource {
        val asked = mutableListOf<Pair<LocalDate, Category?>>()
        var failing = false

        override suspend fun find(deliveryDate: LocalDate, category: Category?): List<DeliveryNote> {
            asked += deliveryDate to category
            if (failing) throw IOException("no connection")
            return notes.filter { it.deliveryDate == deliveryDate && (category == null || it.category == category) }
        }
    }

    private val source = FakeSource(listOf(milkTomorrow, iceCreamTomorrow, milkToday))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `starts on tomorrow, all categories, no delivery note chosen`() {
        val viewModel = LandingViewModel(today = today, deliveryNotes = source)

        val state = viewModel.state.value
        assertEquals(tomorrow, state.deliveryDate)
        assertNull(state.category)
        assertNull(state.selectedDeliveryNote)
        assertEquals(listOf(tomorrow to null), source.asked)
    }

    @Test
    fun `offers the delivery notes found for the chosen date and category`() {
        val viewModel = LandingViewModel(today = today, deliveryNotes = source)

        assertEquals(listOf(milkTomorrow, iceCreamTomorrow), viewModel.state.value.deliveryNoteOptions)

        viewModel.chooseCategory(Category.ICE_CREAM)
        assertEquals(tomorrow to Category.ICE_CREAM, source.asked.last())
        assertEquals(listOf(iceCreamTomorrow), viewModel.state.value.deliveryNoteOptions)

        viewModel.chooseCategory(null)
        viewModel.chooseDeliveryDate(today)
        assertEquals(today to null, source.asked.last())
        assertEquals(listOf(milkToday), viewModel.state.value.deliveryNoteOptions)
    }

    @Test
    fun `shows loading until the delivery notes arrive`() {
        val answer = CompletableDeferred<List<DeliveryNote>>()
        val slowSource = object : DeliveryNoteSource {
            override suspend fun find(deliveryDate: LocalDate, category: Category?) = answer.await()
        }
        val viewModel = LandingViewModel(today = today, deliveryNotes = slowSource)

        assertEquals(LoadStatus.LOADING, viewModel.state.value.loadStatus)

        answer.complete(listOf(milkTomorrow))
        assertEquals(LoadStatus.LOADED, viewModel.state.value.loadStatus)
        assertEquals(listOf(milkTomorrow), viewModel.state.value.deliveryNoteOptions)
    }

    @Test
    fun `a failed load can be retried`() {
        source.failing = true
        val viewModel = LandingViewModel(today = today, deliveryNotes = source)

        assertEquals(LoadStatus.FAILED, viewModel.state.value.loadStatus)
        assertEquals(emptyList<DeliveryNote>(), viewModel.state.value.deliveryNoteOptions)

        source.failing = false
        viewModel.retry()
        assertEquals(LoadStatus.LOADED, viewModel.state.value.loadStatus)
        assertEquals(listOf(milkTomorrow, iceCreamTomorrow), viewModel.state.value.deliveryNoteOptions)
    }

    @Test
    fun `changing the category clears the chosen delivery note`() {
        val viewModel = LandingViewModel(today = today, deliveryNotes = source)

        viewModel.chooseDeliveryNote(milkTomorrow)
        assertEquals(milkTomorrow, viewModel.state.value.selectedDeliveryNote)

        viewModel.chooseCategory(Category.MILK)
        assertNull(viewModel.state.value.selectedDeliveryNote)
    }

    @Test
    fun `changing the delivery date clears the chosen delivery note`() {
        val viewModel = LandingViewModel(today = today, deliveryNotes = source)

        viewModel.chooseDeliveryNote(milkTomorrow)
        assertEquals(milkTomorrow, viewModel.state.value.selectedDeliveryNote)

        viewModel.chooseDeliveryDate(today)
        assertNull(viewModel.state.value.selectedDeliveryNote)
    }

    @Test
    fun `print and preview are allowed only once a delivery note is chosen`() {
        val viewModel = LandingViewModel(today = today, deliveryNotes = source)

        assertFalse(viewModel.state.value.canPrint)

        viewModel.chooseDeliveryNote(milkTomorrow)
        assertTrue(viewModel.state.value.canPrint)
    }
}
