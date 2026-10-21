package com.kowloondairy.navprint.settings

import android.graphics.Bitmap
import com.kowloondairy.navprint.print.PrintFailure
import com.kowloondairy.navprint.print.PrintProblem
import com.kowloondairy.navprint.print.PrinterAddressStore
import com.kowloondairy.navprint.print.PrinterFinder
import com.kowloondairy.navprint.print.ReceiptPrinter
import com.kowloondairy.navprint.receipt.ReceiptPage
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

@OptIn(ExperimentalCoroutinesApi::class)
class PrinterSettingsViewModelTest {

    private class MemoryStore(override var address: String = "") : PrinterAddressStore

    private class FakePrinter(private val store: PrinterAddressStore) : ReceiptPrinter {
        val testedAt = mutableListOf<String>()
        var failWith: PrintFailure? = null

        override suspend fun print(pages: List<ReceiptPage>, render: suspend (ReceiptPage) -> Bitmap) = Unit

        override suspend fun printTest() {
            testedAt += store.address
            failWith?.let { throw it }
        }
    }

    private class FakeFinder(var model: String? = "TM-m30II") : PrinterFinder {
        val searched = mutableListOf<String>()

        /** When set, the search stays open until this completes. */
        var searchHeld: CompletableDeferred<Unit>? = null

        override suspend fun findModel(address: String): String? {
            searched += address
            searchHeld?.await()
            return model
        }
    }

    private val store = MemoryStore("192.168.1.50")
    private val printer = FakePrinter(store)
    private val finder = FakeFinder()

    private fun viewModel() = PrinterSettingsViewModel(store, printer, finder)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `starts with the saved address`() {
        val viewModel = viewModel()

        assertEquals("192.168.1.50", viewModel.state.value.address)
        assertEquals(TestPrintStatus.Idle, viewModel.state.value.test)
    }

    @Test
    fun `saving keeps the address without spaces`() {
        val viewModel = viewModel()

        viewModel.editAddress(" 192.168.1.60 ")
        viewModel.save()

        assertEquals("192.168.1.60", store.address)
        assertEquals("192.168.1.60", viewModel.state.value.address)
    }

    @Test
    fun `pressing print prints the test slip at the address typed in`() {
        val viewModel = viewModel()

        viewModel.editAddress("192.168.1.60")
        viewModel.testPrint()
        viewModel.confirmTestPrint()

        assertEquals(listOf("192.168.1.60"), printer.testedAt)
        assertEquals(TestPrintStatus.Passed, viewModel.state.value.test)
    }

    @Test
    fun `a failed test print says why`() {
        val viewModel = viewModel()
        printer.failWith = PrintFailure(PrintProblem.UNREACHABLE)

        viewModel.testPrint()
        viewModel.confirmTestPrint()

        assertEquals(TestPrintStatus.Failed(PrintProblem.UNREACHABLE), viewModel.state.value.test)
    }

    @Test
    fun `editing the address clears the last test result`() {
        val viewModel = viewModel()
        viewModel.testPrint()
        viewModel.confirmTestPrint()

        viewModel.editAddress("192.168.1.6")

        assertEquals(TestPrintStatus.Idle, viewModel.state.value.test)
    }

    @Test
    fun `a test print first asks, naming the printer found at that address`() {
        val viewModel = viewModel()

        viewModel.testPrint()

        assertEquals(listOf("192.168.1.50"), finder.searched)
        assertEquals(emptyList<String>(), printer.testedAt)
        assertEquals(
            "找到打印機 TM-m30II（192.168.1.50）。確定打印測試頁？",
            (viewModel.state.value.test as TestPrintStatus.Asking).question,
        )
    }

    @Test
    fun `a variant of the TM-m30II counts as the right printer`() {
        val viewModel = viewModel()
        finder.model = "TM-m30II-H"

        viewModel.testPrint()

        assertEquals(
            TestPrintStatus.Asking("找到打印機 TM-m30II-H（192.168.1.50）。確定打印測試頁？"),
            viewModel.state.value.test,
        )
    }

    @Test
    fun `pressing test print again while searching starts no second search`() {
        val viewModel = viewModel()
        finder.searchHeld = CompletableDeferred()

        viewModel.testPrint()
        viewModel.testPrint()

        assertEquals(TestPrintStatus.Searching, viewModel.state.value.test)
        assertEquals(1, finder.searched.size)
    }

    @Test
    fun `a printer of another model gets a warning`() {
        val viewModel = viewModel()
        finder.model = "TM-T82III"

        viewModel.testPrint()

        assertEquals(
            TestPrintStatus.Asking("此 IP 的打印機型號為 TM-T82III，不是 TM-m30II。仍要打印測試頁？"),
            viewModel.state.value.test,
        )
    }

    @Test
    fun `a TM-m30III is another model, not a variant of the TM-m30II`() {
        val viewModel = viewModel()
        finder.model = "TM-m30III"

        viewModel.testPrint()

        assertEquals(
            TestPrintStatus.Asking("此 IP 的打印機型號為 TM-m30III，不是 TM-m30II。仍要打印測試頁？"),
            viewModel.state.value.test,
        )
    }

    @Test
    fun `when no printer answers the search, it still offers to print`() {
        val viewModel = viewModel()
        finder.model = null

        viewModel.testPrint()
        assertEquals(
            TestPrintStatus.Asking("找不到此 IP 的打印機型號。仍要打印測試頁？"),
            viewModel.state.value.test,
        )
        viewModel.confirmTestPrint()

        assertEquals(listOf("192.168.1.50"), printer.testedAt)
    }

    @Test
    fun `pressing cancel prints nothing`() {
        val viewModel = viewModel()

        viewModel.testPrint()
        viewModel.cancelTestPrint()

        assertEquals(emptyList<String>(), printer.testedAt)
        assertEquals(TestPrintStatus.Idle, viewModel.state.value.test)
    }
}
