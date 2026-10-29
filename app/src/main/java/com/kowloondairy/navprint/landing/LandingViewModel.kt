package com.kowloondairy.navprint.landing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** [NOT_QUERIED]: 查詢 has not been pressed since the date or 類別 last changed. */
enum class LoadStatus { NOT_QUERIED, LOADING, LOADED, FAILED }

/**
 * [printer] says which printer is in use, e.g. 「倉庫」. [category] is its 類別; null means 全部.
 * [selected] holds the 送貨單號 of the ticked 送貨單.
 */
data class LandingUiState(
    val today: LocalDate,
    val deliveryDate: LocalDate,
    val printer: String,
    val category: Category?,
    val deliveryNoteOptions: List<DeliveryNote>,
    val loadStatus: LoadStatus,
    val selected: Set<String> = emptySet(),
) {
    /** What 預覽 opens: the ticked 送貨單, in list order. */
    val toPreview: List<DeliveryNote>
        get() = deliveryNoteOptions.filter { it.number in selected }

    val canPrint: Boolean get() = toPreview.isNotEmpty()

    val allSelected: Boolean
        get() = deliveryNoteOptions.isNotEmpty() && deliveryNoteOptions.all { it.number in selected }
}

/** [printer] and [category] are those of the printer in use when the app opens; see [usePrinter]. */
class LandingViewModel(
    private val today: LocalDate,
    private val deliveryNotes: DeliveryNoteSource,
    printer: String,
    category: Category?,
) : ViewModel() {

    private val start = LandingUiState(
        today = today,
        deliveryDate = today.plusDays(1),
        printer = printer,
        category = category,
        deliveryNoteOptions = emptyList(),
        loadStatus = LoadStatus.NOT_QUERIED,
    )

    private val _state = MutableStateFlow(start)
    val state: StateFlow<LandingUiState> = _state.asStateFlow()

    private var loading: Job? = null

    /** Back from the preview: the page as when the app opens, with no list from before. The printer stays. */
    fun startOver() {
        loading?.cancel()
        _state.value = start.copy(printer = _state.value.printer, category = _state.value.category)
    }

    fun chooseDeliveryDate(date: LocalDate) {
        if (date == _state.value.deliveryDate) return
        _state.update { it.copy(deliveryDate = date) }
        clear()
    }

    /** The printer in use, as 設定 left it. A new 類別 drops the list until 查詢 is pressed again. */
    fun usePrinter(printer: String, category: Category?) {
        _state.update { it.copy(printer = printer) }
        if (category == _state.value.category) return
        _state.update { it.copy(category = category) }
        clear()
    }

    fun toggle(note: DeliveryNote) {
        _state.update {
            it.copy(selected = if (note.number in it.selected) it.selected - note.number else it.selected + note.number)
        }
    }

    /** Ticks every 送貨單, or unticks them all if every one is already ticked. */
    fun toggleAll() {
        _state.update {
            it.copy(selected = if (it.allSelected) emptySet() else it.deliveryNoteOptions.map { n -> n.number }.toSet())
        }
    }

    fun query() = load()

    fun retry() = load()

    /** From the 未打印提醒: tomorrow's 送貨單 of the printer's 類別, with the 未打印 ones ticked. */
    fun showUnprinted() {
        _state.update { it.copy(deliveryDate = today.plusDays(1)) }
        load { notes -> notes.filterNot { it.printed }.map { it.number }.toSet() }
    }

    /** The list no longer matches the date and 類別, so drop it until 查詢 is pressed again. */
    private fun clear() {
        loading?.cancel()
        _state.update {
            it.copy(selected = emptySet(), deliveryNoteOptions = emptyList(), loadStatus = LoadStatus.NOT_QUERIED)
        }
    }

    /**
     * Replaces any load still running, so the list always matches the latest date and category.
     * [tick] picks which of the loaded 送貨單 start ticked.
     */
    private fun load(tick: (List<DeliveryNote>) -> Set<String> = { emptySet() }) {
        loading?.cancel()
        _state.update {
            it.copy(selected = emptySet(), deliveryNoteOptions = emptyList(), loadStatus = LoadStatus.LOADING)
        }
        val (deliveryDate, category) = _state.value.let { it.deliveryDate to it.category }
        loading = viewModelScope.launch {
            try {
                val notes = deliveryNotes.find(deliveryDate, category)
                _state.update {
                    it.copy(deliveryNoteOptions = notes, loadStatus = LoadStatus.LOADED, selected = tick(notes))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loadStatus = LoadStatus.FAILED) }
            }
        }
    }
}
