package com.kowloondairy.navprint.receipt

import android.graphics.Bitmap
import com.kowloondairy.navprint.landing.Category
import com.kowloondairy.navprint.landing.DeliveryNote
import com.kowloondairy.navprint.print.PrintFailure
import com.kowloondairy.navprint.print.PrintProblem
import com.kowloondairy.navprint.print.ReceiptPrinter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiptPreviewViewModelTest {

    private val note = DeliveryNote("HDDN230451799", LocalDate.of(2026, 10, 20), Category.MILK)
    private val receipt = Receipt(note, Customer("Patrick Lau", "", "", emptyList()), listOf("92"), emptyList(), null)
    private val allPages = ReceiptPage.entries.toList()
    private val render: suspend (ReceiptPage) -> Bitmap = { error("the fake printer never draws") }

    private class FakeSource(private val receipt: Receipt) : ReceiptSource {
        val asked = mutableListOf<DeliveryNote>()
        var failing = false

        override suspend fun load(note: DeliveryNote): Receipt {
            asked += note
            if (failing) throw IOException("no connection")
            return receipt
        }
    }

    private class FakePrinter : ReceiptPrinter {
        val printed = mutableListOf<List<ReceiptPage>>()
        var failWith: Exception? = null
        var hold: CompletableDeferred<Unit>? = null

        override suspend fun print(pages: List<ReceiptPage>, render: suspend (ReceiptPage) -> Bitmap) {
            printed += pages
            hold?.await()
            failWith?.let { throw it }
        }

        override suspend fun printTest() = Unit
    }

    private val source = FakeSource(receipt)
    private val printer = FakePrinter()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = ReceiptPreviewViewModel(note, source, printer)

    @Test
    fun `loads the 收據 for the chosen 送貨單`() {
        val viewModel = viewModel()

        assertEquals(listOf(note), source.asked)
        assertEquals(ReceiptPreviewState.Loaded(receipt), viewModel.state.value)
    }

    @Test
    fun `shows a failure, and retry loads again`() {
        source.failing = true
        val viewModel = viewModel()
        assertEquals(ReceiptPreviewState.Failed, viewModel.state.value)

        source.failing = false
        viewModel.retry()

        assertEquals(ReceiptPreviewState.Loaded(receipt), viewModel.state.value)
    }

    @Test
    fun `prints the pages asked for and says when done`() {
        val viewModel = viewModel()

        viewModel.print(allPages, render)

        assertEquals(listOf(allPages), printer.printed)
        assertEquals(PrintStatus.Printed, viewModel.printStatus.value)
    }

    @Test
    fun `shows printing while the printer works, and ignores a second tap`() {
        val viewModel = viewModel()
        printer.hold = CompletableDeferred()

        viewModel.print(allPages, render)
        viewModel.print(listOf(ReceiptPage.PACKING), render)

        assertEquals(PrintStatus.Printing(allPages), viewModel.printStatus.value)
        assertEquals(listOf(allPages), printer.printed)

        printer.hold!!.complete(Unit)
        assertEquals(PrintStatus.Printed, viewModel.printStatus.value)
    }

    @Test
    fun `a failure says why and keeps the pages for printing again`() {
        val viewModel = viewModel()
        printer.failWith = PrintFailure(PrintProblem.NO_PAPER)

        viewModel.print(listOf(ReceiptPage.CUSTOMER), render)

        assertEquals(PrintStatus.Failed(PrintProblem.NO_PAPER, listOf(ReceiptPage.CUSTOMER)), viewModel.printStatus.value)
    }

    @Test
    fun `an unexpected error is a general print failure`() {
        val viewModel = viewModel()
        printer.failWith = IllegalStateException("boom")

        viewModel.print(allPages, render)

        assertEquals(PrintStatus.Failed(PrintProblem.OTHER, allPages), viewModel.printStatus.value)
    }

    @Test
    fun `dismissing goes back to idle`() {
        val viewModel = viewModel()
        viewModel.print(allPages, render)

        viewModel.dismissPrintStatus()

        assertEquals(PrintStatus.Idle, viewModel.printStatus.value)
    }
}
