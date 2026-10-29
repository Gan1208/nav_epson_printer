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
    private val note2 = DeliveryNote("HDDN230451800", LocalDate.of(2026, 10, 20), Category.MILK)
    private val note3 = DeliveryNote("HDDN230451801", LocalDate.of(2026, 10, 20), Category.MILK)
    private val allPages = ReceiptPage.entries.toList()
    private val render: RenderPage = { _, _ -> error("the fake printer never draws") }

    private fun receiptFor(note: DeliveryNote) =
        Receipt(note, Customer("Patrick Lau", "", "", emptyList()), listOf("92"), emptyList(), null)

    private fun job(note: DeliveryNote, pages: List<ReceiptPage> = allPages) = PrintJob(note, pages)

    private inner class FakeSource : ReceiptSource {
        val asked = mutableListOf<DeliveryNote>()
        var failing = false
        var failingFor: DeliveryNote? = null

        override suspend fun load(note: DeliveryNote): Receipt {
            asked += note
            if (failing || note == failingFor) throw IOException("no connection")
            return receiptFor(note)
        }
    }

    private class FakePrinter : ReceiptPrinter {
        val printed = mutableListOf<List<ReceiptPage>>()
        var failWith: Exception? = null
        /** When set, only the job with this index (counting from 0) fails, and only once. */
        var failOnceAt: Int? = null
        var hold: CompletableDeferred<Unit>? = null

        override suspend fun print(pages: List<ReceiptPage>, render: suspend (ReceiptPage) -> Bitmap) {
            val index = printed.size
            printed += pages
            hold?.await()
            failWith?.let { throw it }
            if (index == failOnceAt) {
                failOnceAt = null
                throw PrintFailure(PrintProblem.NO_PAPER)
            }
        }

        override suspend fun printTest() = Unit
    }

    private class FakePrintedRecord : PrintedRecord {
        val marked = mutableListOf<String>()

        override suspend fun markPrinted(receipt: Receipt) {
            marked += receipt.deliveryNote.number
        }
    }

    private val source = FakeSource()
    private val printer = FakePrinter()
    private val printedRecord = FakePrintedRecord()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(vararg notes: DeliveryNote = arrayOf(note)) =
        ReceiptPreviewViewModel(notes.toList(), source, printer, printedRecord)

    @Test
    fun `records each 送貨單 as printed once its three pages are out`() {
        printer.failOnceAt = 1
        val viewModel = viewModel(note, note2, note3)

        viewModel.print(listOf(job(note), job(note2), job(note3)), render)
        assertEquals(listOf(note.number), printedRecord.marked)

        viewModel.retryPrint(render)
        assertEquals(listOf(note.number, note2.number, note3.number), printedRecord.marked)
    }

    @Test
    fun `one page alone does not make a 送貨單 printed`() {
        val viewModel = viewModel()

        viewModel.print(listOf(job(note, listOf(ReceiptPage.PACKING))), render)

        assertEquals(PrintStatus.Printed, viewModel.printStatus.value)
        assertEquals(emptyList<String>(), printedRecord.marked)
    }

    @Test
    fun `loads the 收據 for the chosen 送貨單`() {
        val viewModel = viewModel()

        assertEquals(listOf(note), source.asked)
        assertEquals(ReceiptPreviewState.Loaded(listOf(receiptFor(note))), viewModel.state.value)
    }

    @Test
    fun `loads every 收據 of the day, in order`() {
        val viewModel = viewModel(note, note2, note3)

        assertEquals(listOf(note, note2, note3), source.asked)
        assertEquals(
            ReceiptPreviewState.Loaded(listOf(receiptFor(note), receiptFor(note2), receiptFor(note3))),
            viewModel.state.value,
        )
    }

    @Test
    fun `shows a failure, and retry loads again`() {
        source.failing = true
        val viewModel = viewModel()
        assertEquals(ReceiptPreviewState.Failed, viewModel.state.value)

        source.failing = false
        viewModel.retry()

        assertEquals(ReceiptPreviewState.Loaded(listOf(receiptFor(note))), viewModel.state.value)
    }

    @Test
    fun `one 收據 that will not load fails the whole day`() {
        source.failingFor = note2
        val viewModel = viewModel(note, note2, note3)

        assertEquals(ReceiptPreviewState.Failed, viewModel.state.value)
    }

    @Test
    fun `prints the pages asked for and says when done`() {
        val viewModel = viewModel()

        viewModel.print(listOf(job(note)), render)

        assertEquals(listOf(allPages), printer.printed)
        assertEquals(PrintStatus.Printed, viewModel.printStatus.value)
    }

    @Test
    fun `shows printing while the printer works, and ignores a second tap`() {
        val viewModel = viewModel()
        printer.hold = CompletableDeferred()

        viewModel.print(listOf(job(note)), render)
        viewModel.print(listOf(job(note, listOf(ReceiptPage.PACKING))), render)

        assertEquals(PrintStatus.Printing(0, 1), viewModel.printStatus.value)
        assertEquals(listOf(allPages), printer.printed)

        printer.hold!!.complete(Unit)
        assertEquals(PrintStatus.Printed, viewModel.printStatus.value)
    }

    @Test
    fun `prints one 送貨單 at a time, all pages each`() {
        val viewModel = viewModel(note, note2, note3)
        printer.hold = CompletableDeferred()

        viewModel.print(listOf(job(note), job(note2), job(note3)), render)
        assertEquals(PrintStatus.Printing(0, 3), viewModel.printStatus.value)
        assertEquals(listOf(allPages), printer.printed)

        printer.hold!!.complete(Unit)
        assertEquals(listOf(allPages, allPages, allPages), printer.printed)
        assertEquals(PrintStatus.Printed, viewModel.printStatus.value)
    }

    @Test
    fun `a failure says why and keeps the pages for printing again`() {
        val viewModel = viewModel()
        printer.failWith = PrintFailure(PrintProblem.NO_PAPER)

        viewModel.print(listOf(job(note, listOf(ReceiptPage.CUSTOMER))), render)

        assertEquals(
            PrintStatus.Failed(PrintProblem.NO_PAPER, listOf(job(note, listOf(ReceiptPage.CUSTOMER))), 0, 1),
            viewModel.printStatus.value,
        )
    }

    @Test
    fun `an unexpected error is a general print failure`() {
        val viewModel = viewModel()
        printer.failWith = IllegalStateException("boom")

        viewModel.print(listOf(job(note)), render)

        assertEquals(PrintStatus.Failed(PrintProblem.OTHER, listOf(job(note)), 0, 1), viewModel.printStatus.value)
    }

    @Test
    fun `a failure partway keeps the failed 送貨單 and the rest`() {
        val viewModel = viewModel(note, note2, note3)
        printer.failOnceAt = 1

        viewModel.print(listOf(job(note), job(note2), job(note3)), render)

        assertEquals(
            PrintStatus.Failed(PrintProblem.NO_PAPER, listOf(job(note2), job(note3)), 1, 3),
            viewModel.printStatus.value,
        )
    }

    @Test
    fun `retry carries on from the failed 送貨單 without printing earlier ones again`() {
        val drawn = mutableListOf<DeliveryNote>()
        val recordingRender: RenderPage = { n, _ -> drawn += n; error("not drawn") }
        val drawingPrinter = object : ReceiptPrinter {
            var failed = false

            override suspend fun print(pages: List<ReceiptPage>, render: suspend (ReceiptPage) -> Bitmap) {
                runCatching { render(pages.first()) }
                if (!failed && drawn.last() == note2) {
                    failed = true
                    throw PrintFailure(PrintProblem.NO_PAPER)
                }
            }

            override suspend fun printTest() = Unit
        }
        val viewModel = ReceiptPreviewViewModel(listOf(note, note2, note3), source, drawingPrinter, printedRecord)

        viewModel.print(listOf(job(note), job(note2), job(note3)), recordingRender)
        viewModel.retryPrint(recordingRender)

        assertEquals(listOf(note, note2, note2, note3), drawn)
        assertEquals(PrintStatus.Printed, viewModel.printStatus.value)
    }

    @Test
    fun `stop finishes the 送貨單 printing now, then stops`() {
        val viewModel = viewModel(note, note2, note3)
        printer.hold = CompletableDeferred()

        viewModel.print(listOf(job(note), job(note2), job(note3)), render)
        viewModel.stop()
        assertEquals(PrintStatus.Printing(0, 3, stopping = true), viewModel.printStatus.value)

        printer.hold!!.complete(Unit)
        assertEquals(listOf(allPages), printer.printed)
        assertEquals(PrintStatus.Stopped(1, 3), viewModel.printStatus.value)
    }

    @Test
    fun `dismissing goes back to idle`() {
        val viewModel = viewModel()
        viewModel.print(listOf(job(note)), render)

        viewModel.dismissPrintStatus()

        assertEquals(PrintStatus.Idle, viewModel.printStatus.value)
    }
}
