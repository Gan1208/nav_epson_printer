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

    /** As the app opens with a printer of [category] in use. */
    private fun landing(source: DeliveryNoteSource = this.source, category: Category? = null) =
        LandingViewModel(today = today, deliveryNotes = source, printer = "倉庫", category = category)

    private fun queried(category: Category? = null): LandingViewModel = landing(category = category).also { it.query() }

    @Test
    fun `starts on tomorrow, the printer's category, and asks nothing until 查詢`() {
        val viewModel = landing(category = Category.MILK)

        val state = viewModel.state.value
        assertEquals(tomorrow, state.deliveryDate)
        assertEquals("倉庫", state.printer)
        assertEquals(Category.MILK, state.category)
        assertEquals(LoadStatus.NOT_QUERIED, state.loadStatus)
        assertEquals(emptyList<Pair<LocalDate, Category?>>(), source.asked)
    }

    @Test
    fun `查詢 lists the delivery notes for the chosen date and the printer's category`() {
        val viewModel = queried()
        assertEquals(listOf(milkTomorrow, iceCreamTomorrow), viewModel.state.value.deliveryNoteOptions)

        viewModel.usePrinter("倉庫", Category.ICE_CREAM)
        viewModel.query()
        assertEquals(tomorrow to Category.ICE_CREAM, source.asked.last())
        assertEquals(listOf(iceCreamTomorrow), viewModel.state.value.deliveryNoteOptions)

        viewModel.usePrinter("倉庫", null)
        viewModel.chooseDeliveryDate(today)
        viewModel.query()
        assertEquals(today to null, source.asked.last())
        assertEquals(listOf(milkToday), viewModel.state.value.deliveryNoteOptions)
    }

    @Test
    fun `shows loading until the delivery notes arrive`() {
        val answer = CompletableDeferred<List<DeliveryNote>>()
        val slowSource = object : DeliveryNoteSource {
            override suspend fun find(deliveryDate: LocalDate, category: Category?) = answer.await()
        }
        val viewModel = landing(slowSource)
        viewModel.query()

        assertEquals(LoadStatus.LOADING, viewModel.state.value.loadStatus)

        answer.complete(listOf(milkTomorrow))
        assertEquals(LoadStatus.LOADED, viewModel.state.value.loadStatus)
        assertEquals(listOf(milkTomorrow), viewModel.state.value.deliveryNoteOptions)
    }

    @Test
    fun `a failed load can be retried`() {
        source.failing = true
        val viewModel = queried()

        assertEquals(LoadStatus.FAILED, viewModel.state.value.loadStatus)
        assertEquals(emptyList<DeliveryNote>(), viewModel.state.value.deliveryNoteOptions)

        source.failing = false
        viewModel.retry()
        assertEquals(LoadStatus.LOADED, viewModel.state.value.loadStatus)
        assertEquals(listOf(milkTomorrow, iceCreamTomorrow), viewModel.state.value.deliveryNoteOptions)
    }

    @Test
    fun `a printer of another category clears the list and the ticks`() {
        val viewModel = queried()
        viewModel.toggle(milkTomorrow)

        viewModel.usePrinter("寫字樓", Category.MILK)

        val state = viewModel.state.value
        assertEquals("寫字樓", state.printer)
        assertEquals(Category.MILK, state.category)
        assertEquals(LoadStatus.NOT_QUERIED, state.loadStatus)
        assertEquals(emptyList<DeliveryNote>(), state.deliveryNoteOptions)
        assertFalse(state.canPrint)
    }

    @Test
    fun `a printer of the same category keeps the list and the ticks`() {
        val viewModel = queried(Category.MILK)
        viewModel.toggle(milkTomorrow)

        viewModel.usePrinter("寫字樓", Category.MILK)

        assertEquals("寫字樓", viewModel.state.value.printer)
        assertEquals(listOf(milkTomorrow), viewModel.state.value.toPreview)
    }

    @Test
    fun `changing the delivery date clears the list and the ticks`() {
        val viewModel = queried()
        viewModel.toggle(milkTomorrow)

        viewModel.chooseDeliveryDate(today)

        assertEquals(LoadStatus.NOT_QUERIED, viewModel.state.value.loadStatus)
        assertFalse(viewModel.state.value.canPrint)
    }

    @Test
    fun `choosing the same date again keeps the list`() {
        val viewModel = queried()
        viewModel.toggle(milkTomorrow)

        viewModel.chooseDeliveryDate(tomorrow)

        assertEquals(listOf(milkTomorrow), viewModel.state.value.toPreview)
    }

    @Test
    fun `starting over drops the list and goes back to tomorrow, keeping the printer`() {
        val viewModel = landing()
        viewModel.usePrinter("寫字樓", Category.MILK)
        viewModel.chooseDeliveryDate(today)
        viewModel.query()
        viewModel.toggle(milkToday)

        viewModel.startOver()

        val state = viewModel.state.value
        assertEquals(tomorrow, state.deliveryDate)
        assertEquals("寫字樓", state.printer)
        assertEquals(Category.MILK, state.category)
        assertEquals(LoadStatus.NOT_QUERIED, state.loadStatus)
        assertEquals(emptyList<DeliveryNote>(), state.deliveryNoteOptions)
        assertFalse(state.canPrint)
    }

    @Test
    fun `查詢 again starts with nothing ticked`() {
        val viewModel = queried()
        viewModel.toggle(milkTomorrow)

        viewModel.query()

        assertFalse(viewModel.state.value.canPrint)
    }

    @Test
    fun `previews the ticked delivery notes in list order`() {
        val viewModel = queried()
        assertFalse(viewModel.state.value.canPrint)

        viewModel.toggle(iceCreamTomorrow)
        viewModel.toggle(milkTomorrow)
        assertEquals(listOf(milkTomorrow, iceCreamTomorrow), viewModel.state.value.toPreview)

        viewModel.toggle(milkTomorrow)
        assertEquals(listOf(iceCreamTomorrow), viewModel.state.value.toPreview)
    }

    @Test
    fun `全選 ticks every delivery note, and again unticks them all`() {
        val viewModel = queried()
        viewModel.toggle(milkTomorrow)

        viewModel.toggleAll()
        assertTrue(viewModel.state.value.allSelected)
        assertEquals(listOf(milkTomorrow, iceCreamTomorrow), viewModel.state.value.toPreview)

        viewModel.toggleAll()
        assertFalse(viewModel.state.value.canPrint)
    }

    @Test
    fun `the 未打印提醒 opens tomorrow, the printer's 類別, with the 未打印 ones ticked`() {
        val printedTomorrow = DeliveryNote("D004", tomorrow, Category.MILK, printed = true)
        val source = FakeSource(listOf(milkTomorrow, iceCreamTomorrow, printedTomorrow, milkToday))
        val viewModel = landing(source, Category.MILK)
        viewModel.chooseDeliveryDate(today)

        viewModel.showUnprinted()

        val state = viewModel.state.value
        assertEquals(listOf(tomorrow to Category.MILK), source.asked)
        assertEquals(tomorrow, state.deliveryDate)
        assertEquals(Category.MILK, state.category)
        assertEquals(setOf("D001"), state.selected)
    }

    @Test
    fun `with a 全部 printer the 未打印提醒 ticks every 類別`() {
        val viewModel = landing()

        viewModel.showUnprinted()

        assertEquals(listOf(tomorrow to null), source.asked)
        assertEquals(setOf("D001", "D002"), viewModel.state.value.selected)
    }
}
