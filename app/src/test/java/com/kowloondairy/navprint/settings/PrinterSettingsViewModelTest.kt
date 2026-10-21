package com.kowloondairy.navprint.settings

import android.graphics.Bitmap
import com.kowloondairy.navprint.print.PrintFailure
import com.kowloondairy.navprint.print.PrintProblem
import com.kowloondairy.navprint.print.PrinterAddressStore
import com.kowloondairy.navprint.print.ReceiptPrinter
import com.kowloondairy.navprint.receipt.ReceiptPage
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

    private val store = MemoryStore("192.168.1.50")
    private val printer = FakePrinter(store)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `starts with the saved address`() {
        val viewModel = PrinterSettingsViewModel(store, printer)

        assertEquals("192.168.1.50", viewModel.state.value.address)
        assertEquals(TestPrintStatus.Idle, viewModel.state.value.test)
    }

    @Test
    fun `saving keeps the address without spaces`() {
        val viewModel = PrinterSettingsViewModel(store, printer)

        viewModel.editAddress(" 192.168.1.60 ")
        viewModel.save()

        assertEquals("192.168.1.60", store.address)
        assertEquals("192.168.1.60", viewModel.state.value.address)
    }

    @Test
    fun `a test print uses the address typed in`() {
        val viewModel = PrinterSettingsViewModel(store, printer)

        viewModel.editAddress("192.168.1.60")
        viewModel.testPrint()

        assertEquals(listOf("192.168.1.60"), printer.testedAt)
        assertEquals(TestPrintStatus.Passed, viewModel.state.value.test)
    }

    @Test
    fun `a failed test print says why`() {
        val viewModel = PrinterSettingsViewModel(store, printer)
        printer.failWith = PrintFailure(PrintProblem.UNREACHABLE)

        viewModel.testPrint()

        assertEquals(TestPrintStatus.Failed(PrintProblem.UNREACHABLE), viewModel.state.value.test)
    }

    @Test
    fun `editing the address clears the last test result`() {
        val viewModel = PrinterSettingsViewModel(store, printer)
        viewModel.testPrint()

        viewModel.editAddress("192.168.1.6")

        assertEquals(TestPrintStatus.Idle, viewModel.state.value.test)
    }
}
