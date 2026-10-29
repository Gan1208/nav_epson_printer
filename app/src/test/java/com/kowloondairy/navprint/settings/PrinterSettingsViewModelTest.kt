package com.kowloondairy.navprint.settings

import android.graphics.Bitmap
import com.kowloondairy.navprint.landing.Category
import com.kowloondairy.navprint.landing.DefaultCategoryStore
import com.kowloondairy.navprint.print.PrintFailure
import com.kowloondairy.navprint.print.PrintProblem
import com.kowloondairy.navprint.print.PrinterAddressStore
import com.kowloondairy.navprint.print.PrinterFinder
import com.kowloondairy.navprint.print.ReceiptPrinter
import com.kowloondairy.navprint.print.SavedPrinter
import com.kowloondairy.navprint.print.SavedPrinterStore
import com.kowloondairy.navprint.receipt.ReceiptPage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrinterSettingsViewModelTest {

    private class MemoryStore(override var address: String = "") : PrinterAddressStore

    private class MemorySavedPrinters(override var printers: List<SavedPrinter> = emptyList()) : SavedPrinterStore

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

        /** When set, the search throws this instead of answering. */
        var failWith: Throwable? = null

        override suspend fun findModel(address: String): String? {
            searched += address
            searchHeld?.await()
            failWith?.let { throw it }
            return model
        }
    }

    private val store = MemoryStore("192.168.1.50")
    private val printer = FakePrinter(store)
    private val finder = FakeFinder()
    private val saved = MemorySavedPrinters()

    private val defaultCategory = object : DefaultCategoryStore {
        override var category: Category? = null
    }

    private fun viewModel() = PrinterSettingsViewModel(store, printer, finder, saved, defaultCategory)

    private fun PrinterSettingsViewModel.passTestPrint(address: String) {
        editAddress(address)
        testPrint()
        confirmTestPrint()
    }

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
    fun `a search that breaks counts as no printer found`() {
        val viewModel = viewModel()
        finder.failWith = IllegalStateException("wifi off")

        viewModel.testPrint()

        assertEquals(
            TestPrintStatus.Asking("找不到此 IP 的打印機型號。仍要打印測試頁？"),
            viewModel.state.value.test,
        )
    }

    @Test
    fun `with no IP typed, a test print says so without searching`() {
        val viewModel = viewModel()

        viewModel.editAddress("  ")
        viewModel.testPrint()

        assertEquals(emptyList<String>(), finder.searched)
        assertEquals(TestPrintStatus.Failed(PrintProblem.NO_ADDRESS), viewModel.state.value.test)
    }

    @Test
    fun `pressing cancel prints nothing`() {
        val viewModel = viewModel()

        viewModel.testPrint()
        viewModel.cancelTestPrint()

        assertEquals(emptyList<String>(), printer.testedAt)
        assertEquals(TestPrintStatus.Idle, viewModel.state.value.test)
    }

    @Test
    fun `a test print that goes through saves the printer and asks for its name`() {
        val viewModel = viewModel()

        viewModel.passTestPrint("192.168.1.60")

        val printer = SavedPrinter("192.168.1.60", "TM-m30II")
        assertEquals(listOf(printer), saved.printers)
        assertEquals(printer, viewModel.state.value.editing)
    }

    @Test
    fun `a failed test print saves nothing`() {
        val viewModel = viewModel()
        printer.failWith = PrintFailure(PrintProblem.UNREACHABLE)

        viewModel.passTestPrint("192.168.1.60")

        assertEquals(emptyList<SavedPrinter>(), saved.printers)
        assertNull(viewModel.state.value.editing)
    }

    @Test
    fun `editing a printer keeps the name`() {
        val viewModel = viewModel()
        viewModel.passTestPrint("192.168.1.60")

        viewModel.edit(viewModel.state.value.editing!!, " 倉庫 ")

        assertEquals(SavedPrinter("192.168.1.60", "TM-m30II", "倉庫"), saved.printers.single())
        assertNull(viewModel.state.value.editing)
    }

    @Test
    fun `the default category starts as 全部 and is kept as soon as it is chosen`() {
        val viewModel = viewModel()
        assertNull(viewModel.state.value.defaultCategory)

        viewModel.chooseDefaultCategory(Category.ICE_CREAM)

        assertEquals(Category.ICE_CREAM, defaultCategory.category)
        assertEquals(Category.ICE_CREAM, viewModel.state.value.defaultCategory)
        assertEquals(Category.ICE_CREAM, viewModel().state.value.defaultCategory)
    }

    @Test
    fun `a printer seen before moves to the top, keeps its name and is not asked again`() {
        saved.printers = listOf(SavedPrinter("192.168.1.61", null, "寫字樓"), SavedPrinter("192.168.1.60", null, "倉庫"))
        val viewModel = viewModel()

        viewModel.passTestPrint("192.168.1.60")

        assertEquals(
            listOf(SavedPrinter("192.168.1.60", "TM-m30II", "倉庫"), SavedPrinter("192.168.1.61", null, "寫字樓")),
            saved.printers,
        )
        assertNull(viewModel.state.value.editing)
    }

    @Test
    fun `choosing a saved printer puts it in use`() {
        val other = SavedPrinter("192.168.1.61", "TM-m30II", "寫字樓")
        saved.printers = listOf(other)
        val viewModel = viewModel()

        viewModel.choose(other)

        assertEquals("192.168.1.61", store.address)
        assertEquals("192.168.1.61", viewModel.state.value.inUse)
        assertEquals("192.168.1.61", viewModel.state.value.address)
    }

    @Test
    fun `typing an IP does not put it in use until saved`() {
        val viewModel = viewModel()

        viewModel.editAddress("192.168.1.61")

        assertEquals("192.168.1.50", viewModel.state.value.inUse)
    }

    @Test
    fun `removing a saved printer keeps the IP in use`() {
        val inUse = SavedPrinter("192.168.1.50", "TM-m30II", "倉庫")
        saved.printers = listOf(inUse)
        val viewModel = viewModel()

        viewModel.remove(inUse)

        assertEquals(emptyList<SavedPrinter>(), saved.printers)
        assertEquals("192.168.1.50", store.address)
    }
}
