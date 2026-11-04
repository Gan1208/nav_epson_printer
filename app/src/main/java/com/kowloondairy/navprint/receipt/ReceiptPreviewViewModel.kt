package com.kowloondairy.navprint.receipt

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kowloondairy.navprint.landing.DeliveryNote
import com.kowloondairy.navprint.print.PrintFailure
import com.kowloondairy.navprint.print.PrintProblem
import com.kowloondairy.navprint.print.PrinterAddressStore
import com.kowloondairy.navprint.print.ReceiptPrinter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ReceiptPreviewState {
    data object Loading : ReceiptPreviewState
    /** One [Receipt] per 送貨單, in the order they were asked for. */
    data class Loaded(val receipts: List<Receipt>) : ReceiptPreviewState
    data object Failed : ReceiptPreviewState
}

private val allPages = ReceiptPage.entries.toList()

/** Printing waits after this many 收據, so staff can check them before more pile up. */
const val RECEIPTS_BEFORE_PAUSE = 10

/** Draws one page of one 送貨單 as a 576-dot-wide image. */
typealias RenderPage = suspend (DeliveryNote, ReceiptPage) -> Bitmap

/** [done] and [total] count 送貨單, each printed as one job of all three pages. */
sealed interface PrintStatus {
    data object Idle : PrintStatus
    data class Printing(val done: Int, val total: Int, val stopping: Boolean = false) : PrintStatus
    /** [done] 收據 are out and printing waits for staff to go on or stop. */
    data class Paused(val done: Int, val total: Int) : PrintStatus
    data object Printed : PrintStatus
    data class Stopped(val done: Int, val total: Int) : PrintStatus
    /** [remaining] starts with the 送貨單 that failed; it is what to send again if the person retries. */
    data class Failed(
        val problem: PrintProblem,
        val remaining: List<DeliveryNote>,
        val done: Int,
        val total: Int,
    ) : PrintStatus
}

/** The 收據 of [deliveryNotes], for the preview or for printing straight from the main page. */
class ReceiptPreviewViewModel(
    val deliveryNotes: List<DeliveryNote>,
    private val receipts: ReceiptSource,
    private val printer: ReceiptPrinter,
    private val printedRecord: PrintedRecord,
    /** Where [printer] sends to; also the printer the print dialog picks first next time. */
    private val printerAddress: PrinterAddressStore,
) : ViewModel() {

    private val _state = MutableStateFlow<ReceiptPreviewState>(ReceiptPreviewState.Loading)
    val state: StateFlow<ReceiptPreviewState> = _state.asStateFlow()

    private val _printStatus = MutableStateFlow<PrintStatus>(PrintStatus.Idle)
    val printStatus: StateFlow<PrintStatus> = _printStatus.asStateFlow()

    private var stopRequested = false

    /** What is left to print while [PrintStatus.Paused]. */
    private var pausedRemaining: List<DeliveryNote> = emptyList()

    init {
        load()
    }

    fun retry() = load()

    /**
     * Sends every 收據 to the printer at [address], one 送貨單 at a time, so each one's pages come out together.
     * A tap while already printing is ignored.
     */
    fun print(address: String, render: RenderPage) {
        if (_printStatus.value is PrintStatus.Printing) return
        printerAddress.address = address
        run(deliveryNotes, done = 0, total = deliveryNotes.size, render)
    }

    /** After a failure, sends the failed 送貨單 again, then the ones after it. */
    fun retryPrint(render: RenderPage) {
        val failed = _printStatus.value as? PrintStatus.Failed ?: return
        run(failed.remaining, failed.done, failed.total, render)
    }

    /** After a pause, prints the next 收據. */
    fun continuePrint(render: RenderPage) {
        val paused = _printStatus.value as? PrintStatus.Paused ?: return
        run(pausedRemaining, paused.done, paused.total, render)
    }

    /** Stops once the 送貨單 printing now is done, so no 收據 comes out half printed. */
    fun stop() {
        (_printStatus.value as? PrintStatus.Paused)?.let {
            _printStatus.value = PrintStatus.Stopped(it.done, it.total)
            return
        }
        val printing = _printStatus.value as? PrintStatus.Printing ?: return
        stopRequested = true
        _printStatus.value = printing.copy(stopping = true)
    }

    fun dismissPrintStatus() {
        if (_printStatus.value !is PrintStatus.Printing) _printStatus.value = PrintStatus.Idle
    }

    private fun run(notes: List<DeliveryNote>, done: Int, total: Int, render: RenderPage) {
        if (_printStatus.value is PrintStatus.Printing) return
        stopRequested = false
        _printStatus.value = PrintStatus.Printing(done, total)
        viewModelScope.launch {
            notes.forEachIndexed { i, note ->
                val printed = done + i
                if (stopRequested) {
                    _printStatus.value = PrintStatus.Stopped(printed, total)
                    return@launch
                }
                val problem = try {
                    printer.print(allPages) { page -> render(note, page) }
                    null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: PrintFailure) {
                    e.problem
                } catch (e: Exception) {
                    PrintProblem.OTHER
                }
                if (problem != null) {
                    _printStatus.value = PrintStatus.Failed(problem, notes.drop(i), printed, total)
                    return@launch
                }
                record(note)
                val nowDone = printed + 1
                val left = notes.drop(i + 1)
                if (!stopRequested && left.isNotEmpty() && nowDone % RECEIPTS_BEFORE_PAUSE == 0) {
                    pausedRemaining = left
                    _printStatus.value = PrintStatus.Paused(nowDone, total)
                    return@launch
                }
                _printStatus.value = PrintStatus.Printing(nowDone, total, stopRequested)
            }
            _printStatus.value = PrintStatus.Printed
        }
    }

    /** All three pages are out, so it is 已打印; recording runs beside printing so it never holds the next one up. */
    private fun record(note: DeliveryNote) {
        val receipt = (_state.value as? ReceiptPreviewState.Loaded)?.receipts
            ?.firstOrNull { it.deliveryNote.number == note.number } ?: return
        viewModelScope.launch { printedRecord.markPrinted(receipt) }
    }

    /** Loads every 收據, or none: a day is never shown with one missing. */
    private fun load() {
        _state.value = ReceiptPreviewState.Loading
        viewModelScope.launch {
            _state.value = try {
                ReceiptPreviewState.Loaded(deliveryNotes.map { receipts.load(it) })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ReceiptPreviewState.Failed
            }
        }
    }
}
