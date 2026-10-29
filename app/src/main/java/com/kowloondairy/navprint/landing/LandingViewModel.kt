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

/** [NOT_QUERIED]: 查詢 has not been pressed since the date last changed. */
enum class LoadStatus { NOT_QUERIED, LOADING, LOADED, FAILED }

/**
 * [category] is the 類別 to look up; null means 全部.
 * [selected] holds the 送貨單號 of the ticked 送貨單.
 */
data class LandingUiState(
    val today: LocalDate,
    val deliveryDate: LocalDate,
    val category: Category?,
    val deliveryNoteOptions: List<DeliveryNote>,
    val loadStatus: LoadStatus,
    val selected: Set<String> = emptySet(),
) {
    /** What 預覽 opens and 打印 prints: the ticked 送貨單, in list order. */
    val ticked: List<DeliveryNote>
        get() = deliveryNoteOptions.filter { it.number in selected }

    val canPrint: Boolean get() = ticked.isNotEmpty()

    val allSelected: Boolean
        get() = deliveryNoteOptions.isNotEmpty() && deliveryNoteOptions.all { it.number in selected }
}

/** [category] is the 本裝置預設類別, which the page starts on. */
class LandingViewModel(
    private val today: LocalDate,
    private val deliveryNotes: DeliveryNoteSource,
    category: Category?,
) : ViewModel() {

    private val _state = MutableStateFlow(
        LandingUiState(
            today = today,
            deliveryDate = today.plusDays(1),
            category = category,
            deliveryNoteOptions = emptyList(),
            loadStatus = LoadStatus.NOT_QUERIED,
        ),
    )
    val state: StateFlow<LandingUiState> = _state.asStateFlow()

    private var loading: Job? = null

    /** After printing: the list again, so the new ✓ show, with nothing ticked so nothing prints twice. */
    fun reload() {
        if (_state.value.loadStatus != LoadStatus.NOT_QUERIED) load()
    }

    fun chooseDeliveryDate(date: LocalDate) {
        if (date == _state.value.deliveryDate) return
        _state.update { it.copy(deliveryDate = date) }
        clear()
    }

    /** [category] null means 全部. A list already shown is looked up again for it, with nothing ticked. */
    fun chooseCategory(category: Category?) {
        if (category == _state.value.category) return
        _state.update { it.copy(category = category) }
        reload()
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

    /** From the 未打印提醒: tomorrow's 送貨單 of every 類別, as the alert counts, with the 未打印 ones ticked. */
    fun showUnprinted() {
        _state.update { it.copy(deliveryDate = today.plusDays(1), category = null) }
        load { notes -> notes.filterNot { it.printed }.map { it.number }.toSet() }
    }

    /** The list no longer matches the date, so drop it until 查詢 is pressed again. */
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
