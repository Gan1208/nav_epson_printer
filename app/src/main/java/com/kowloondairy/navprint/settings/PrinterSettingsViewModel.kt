package com.kowloondairy.navprint.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kowloondairy.navprint.print.PrintFailure
import com.kowloondairy.navprint.print.PrintProblem
import com.kowloondairy.navprint.print.PrinterAddressStore
import com.kowloondairy.navprint.print.ReceiptPrinter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface TestPrintStatus {
    data object Idle : TestPrintStatus
    data object Printing : TestPrintStatus
    data object Passed : TestPrintStatus
    data class Failed(val problem: PrintProblem) : TestPrintStatus
}

data class PrinterSettingsState(
    /** As typed, not yet saved. */
    val address: String,
    val test: TestPrintStatus = TestPrintStatus.Idle,
)

class PrinterSettingsViewModel(
    private val store: PrinterAddressStore,
    private val printer: ReceiptPrinter,
) : ViewModel() {

    private val _state = MutableStateFlow(PrinterSettingsState(store.address))
    val state: StateFlow<PrinterSettingsState> = _state.asStateFlow()

    fun editAddress(address: String) = _state.update { PrinterSettingsState(address) }

    fun save() {
        val address = _state.value.address.trim()
        store.address = address
        _state.update { it.copy(address = address) }
    }

    /** Saves what was typed, then prints a short slip on that printer. */
    fun testPrint() {
        if (_state.value.test == TestPrintStatus.Printing) return
        save()
        _state.update { it.copy(test = TestPrintStatus.Printing) }
        viewModelScope.launch {
            val result = try {
                printer.printTest()
                TestPrintStatus.Passed
            } catch (e: CancellationException) {
                throw e
            } catch (e: PrintFailure) {
                TestPrintStatus.Failed(e.problem)
            } catch (e: Exception) {
                TestPrintStatus.Failed(PrintProblem.OTHER)
            }
            _state.update { it.copy(test = result) }
        }
    }
}
