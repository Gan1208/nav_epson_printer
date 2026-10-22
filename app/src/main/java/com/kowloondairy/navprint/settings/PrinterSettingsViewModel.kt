package com.kowloondairy.navprint.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kowloondairy.navprint.print.PrintFailure
import com.kowloondairy.navprint.print.PrintProblem
import com.kowloondairy.navprint.print.PrinterAddressStore
import com.kowloondairy.navprint.print.PrinterFinder
import com.kowloondairy.navprint.print.ReceiptPrinter
import com.kowloondairy.navprint.print.SavedPrinter
import com.kowloondairy.navprint.print.SavedPrinterStore
import com.kowloondairy.navprint.print.withSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The printer this app is built for. Variants such as `TM-m30II-H` count too. */
private const val EXPECTED_MODEL = "TM-m30II"

/** `TM-m30II-H` is a variant; `TM-m30III` is a different printer. */
private fun isExpectedModel(model: String) = model == EXPECTED_MODEL || model.startsWith("$EXPECTED_MODEL-")

sealed interface TestPrintStatus {
    data object Idle : TestPrintStatus
    data object Searching : TestPrintStatus

    /** Waiting for the person to say 打印 or 取消. */
    data class Asking(val question: String) : TestPrintStatus
    data object Printing : TestPrintStatus
    data object Passed : TestPrintStatus
    data class Failed(val problem: PrintProblem) : TestPrintStatus
}

data class PrinterSettingsState(
    /** As typed, not yet saved. */
    val address: String,
    val test: TestPrintStatus = TestPrintStatus.Idle,
    /** The saved IP, which everything prints to. */
    val inUse: String = address,
    val saved: List<SavedPrinter> = emptyList(),
    /** The 已儲存打印機 whose 打印機名稱 is being typed, if any. */
    val naming: SavedPrinter? = null,
) {
    /** Searching or printing: the IP and buttons are locked until it finishes. */
    val busy: Boolean
        get() = test == TestPrintStatus.Searching || test == TestPrintStatus.Printing
}

class PrinterSettingsViewModel(
    private val store: PrinterAddressStore,
    private val printer: ReceiptPrinter,
    private val finder: PrinterFinder,
    private val savedPrinters: SavedPrinterStore,
) : ViewModel() {

    private val _state = MutableStateFlow(PrinterSettingsState(store.address, saved = savedPrinters.printers))
    val state: StateFlow<PrinterSettingsState> = _state.asStateFlow()

    /** What the last search found, kept for the 已儲存打印機 once the test print goes through. */
    private var foundModel: String? = null

    fun editAddress(address: String) = _state.update { it.copy(address = address, test = TestPrintStatus.Idle) }

    fun save() {
        val address = _state.value.address.trim()
        store.address = address
        _state.update { it.copy(address = address, inUse = address) }
    }

    /** A tap on a 已儲存打印機: it is in use from now on. */
    fun choose(printer: SavedPrinter) {
        if (_state.value.busy) return
        editAddress(printer.address)
        save()
    }

    /** The ✎ on a 已儲存打印機. */
    fun startNaming(printer: SavedPrinter) = _state.update { it.copy(naming = printer) }

    fun cancelNaming() = _state.update { it.copy(naming = null) }

    fun name(printer: SavedPrinter, name: String) {
        updateSaved { saved -> saved.map { if (it.address == printer.address) it.copy(name = name.trim()) else it } }
        cancelNaming()
    }

    /** After the person said 刪除. The IP in use stays in use. */
    fun remove(printer: SavedPrinter) = updateSaved { saved -> saved.filter { it.address != printer.address } }

    private fun updateSaved(change: (List<SavedPrinter>) -> List<SavedPrinter>) {
        val saved = change(_state.value.saved)
        savedPrinters.printers = saved
        _state.update { it.copy(saved = saved) }
    }

    /** Saves what was typed, then looks for the printer there and asks before printing. */
    fun testPrint() {
        if (_state.value.busy) return
        save()
        val address = _state.value.address
        if (address.isEmpty()) {
            _state.update { it.copy(test = TestPrintStatus.Failed(PrintProblem.NO_ADDRESS)) }
            return
        }
        _state.update { it.copy(test = TestPrintStatus.Searching) }
        viewModelScope.launch {
            val model = try {
                finder.findModel(address)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A broken search is no worse than a silent one: still offer to print.
                null
            }
            foundModel = model
            val question = when {
                model == null -> "找不到此 IP 的打印機型號。仍要打印測試頁？"
                isExpectedModel(model) -> "找到打印機 $model（$address）。確定打印測試頁？"
                else -> "此 IP 的打印機型號為 $model，不是 $EXPECTED_MODEL。仍要打印測試頁？"
            }
            _state.update { it.copy(test = TestPrintStatus.Asking(question)) }
        }
    }

    /** The person pressed 取消. */
    fun cancelTestPrint() {
        if (_state.value.test !is TestPrintStatus.Asking) return
        _state.update { it.copy(test = TestPrintStatus.Idle) }
    }

    /** The person pressed 打印. */
    fun confirmTestPrint() {
        if (_state.value.test !is TestPrintStatus.Asking) return
        _state.update { it.copy(test = TestPrintStatus.Printing) }
        viewModelScope.launch {
            val result = try {
                printer.printTest()
                rememberPrinter()
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

    /** A new 已儲存打印機 is asked for its 打印機名稱; one seen before keeps its own. */
    private fun rememberPrinter() {
        val address = _state.value.inUse
        val isNew = _state.value.saved.none { it.address == address }
        updateSaved { it.withSuccess(SavedPrinter(address, foundModel)) }
        if (isNew) _state.update { it.copy(naming = it.saved.first()) }
    }
}
