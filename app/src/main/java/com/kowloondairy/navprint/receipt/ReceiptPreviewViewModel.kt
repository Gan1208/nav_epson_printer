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
    /** One [Receipt] per 送貨單, in the order they were asked for. */
    data class Loaded(val receipts: List<Receipt>) : ReceiptPreviewState
    data object Failed : ReceiptPreviewState
}

/** Pages of one 送貨單's 收據, sent to the printer as one job. */
data class PrintJob(val deliveryNote: DeliveryNote, val pages: List<ReceiptPage>)

/** Draws one page of one 送貨單 as a 576-dot-wide image. */
typealias RenderPage = suspend (DeliveryNote, ReceiptPage) -> Bitmap

/** [done] and [total] count jobs, i.e. 送貨單. */
sealed interface PrintStatus {
    data object Idle : PrintStatus
    data class Printing(val done: Int, val total: Int, val stopping: Boolean = false) : PrintStatus
    data object Printed : PrintStatus
    data class Stopped(val done: Int, val total: Int) : PrintStatus
    /** [remaining] starts with the job that failed; it is what to send again if the person retries. */
    data class Failed(
        val problem: PrintProblem,
        val remaining: List<PrintJob>,
        val done: Int,
        val total: Int,
    ) : PrintStatus
}

class ReceiptPreviewViewModel(
    val deliveryNotes: List<DeliveryNote>,
    private val receipts: ReceiptSource,
    private val printer: ReceiptPrinter,
) : ViewModel() {

    private val _state = MutableStateFlow<ReceiptPreviewState>(ReceiptPreviewState.Loading)
    val state: StateFlow<ReceiptPreviewState> = _state.asStateFlow()

    private val _printStatus = MutableStateFlow<PrintStatus>(PrintStatus.Idle)
    val printStatus: StateFlow<PrintStatus> = _printStatus.asStateFlow()

    private var stopRequested = false

    init {
        load()
    }

    fun retry() = load()

    /**
     * Sends [jobs] to the printer one at a time, so each 送貨單's pages come out together.
     * A tap while already printing is ignored.
     */
    fun print(jobs: List<PrintJob>, render: RenderPage) = run(jobs, done = 0, total = jobs.size, render)

    /** After a failure, sends the failed job again, then the ones after it. */
    fun retryPrint(render: RenderPage) {
        val failed = _printStatus.value as? PrintStatus.Failed ?: return
        run(failed.remaining, failed.done, failed.total, render)
    }

    /** Stops once the job printing now is done, so no 收據 comes out half printed. */
    fun stop() {
        val printing = _printStatus.value as? PrintStatus.Printing ?: return
        stopRequested = true
        _printStatus.value = printing.copy(stopping = true)
    }

    fun dismissPrintStatus() {
        if (_printStatus.value !is PrintStatus.Printing) _printStatus.value = PrintStatus.Idle
    }

    private fun run(jobs: List<PrintJob>, done: Int, total: Int, render: RenderPage) {
        if (_printStatus.value is PrintStatus.Printing) return
        stopRequested = false
        _printStatus.value = PrintStatus.Printing(done, total)
        viewModelScope.launch {
            jobs.forEachIndexed { i, job ->
                val printed = done + i
                if (stopRequested) {
                    _printStatus.value = PrintStatus.Stopped(printed, total)
                    return@launch
                }
                val problem = try {
                    printer.print(job.pages) { page -> render(job.deliveryNote, page) }
                    null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: PrintFailure) {
                    e.problem
                } catch (e: Exception) {
                    PrintProblem.OTHER
                }
                if (problem != null) {
                    _printStatus.value = PrintStatus.Failed(problem, jobs.drop(i), printed, total)
                    return@launch
                }
                _printStatus.value = PrintStatus.Printing(printed + 1, total, stopRequested)
            }
            _printStatus.value = PrintStatus.Printed
        }
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
