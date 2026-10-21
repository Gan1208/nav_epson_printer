package com.kowloondairy.navprint.receipt

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kowloondairy.navprint.landing.DeliveryNote
import com.kowloondairy.navprint.print.PrintFailure
import com.kowloondairy.navprint.print.PrintProblem
import com.kowloondairy.navprint.print.ReceiptPrinter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ReceiptPreviewState {
    data object Loading : ReceiptPreviewState
    data class Loaded(val receipt: Receipt) : ReceiptPreviewState
    data object Failed : ReceiptPreviewState
}

sealed interface PrintStatus {
    data object Idle : PrintStatus
    data class Printing(val pages: List<ReceiptPage>) : PrintStatus
    data object Printed : PrintStatus
    /** [pages] are what to send again if the person retries. */
    data class Failed(val problem: PrintProblem, val pages: List<ReceiptPage>) : PrintStatus
}

class ReceiptPreviewViewModel(
    val deliveryNote: DeliveryNote,
    private val receipts: ReceiptSource,
    private val printer: ReceiptPrinter,
) : ViewModel() {

    private val _state = MutableStateFlow<ReceiptPreviewState>(ReceiptPreviewState.Loading)
    val state: StateFlow<ReceiptPreviewState> = _state.asStateFlow()

    private val _printStatus = MutableStateFlow<PrintStatus>(PrintStatus.Idle)
    val printStatus: StateFlow<PrintStatus> = _printStatus.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    /** Sends [pages] to the printer; a tap while already printing is ignored. */
    fun print(pages: List<ReceiptPage>, render: suspend (ReceiptPage) -> Bitmap) {
        if (_printStatus.value is PrintStatus.Printing) return
        _printStatus.value = PrintStatus.Printing(pages)
        viewModelScope.launch {
            _printStatus.value = try {
                printer.print(pages, render)
                PrintStatus.Printed
            } catch (e: CancellationException) {
                throw e
            } catch (e: PrintFailure) {
                PrintStatus.Failed(e.problem, pages)
            } catch (e: Exception) {
                PrintStatus.Failed(PrintProblem.OTHER, pages)
            }
        }
    }

    fun dismissPrintStatus() {
        if (_printStatus.value !is PrintStatus.Printing) _printStatus.value = PrintStatus.Idle
    }

    private fun load() {
        _state.value = ReceiptPreviewState.Loading
        viewModelScope.launch {
            _state.value = try {
                ReceiptPreviewState.Loaded(receipts.load(deliveryNote))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ReceiptPreviewState.Failed
            }
        }
    }
}
