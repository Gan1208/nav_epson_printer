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

enum class LoadStatus { LOADING, LOADED, FAILED }

/** [category] null means 全部. */
data class LandingUiState(
    val today: LocalDate,
    val deliveryDate: LocalDate,
    val category: Category?,
    val selectedDeliveryNote: DeliveryNote?,
    val deliveryNoteOptions: List<DeliveryNote>,
    val loadStatus: LoadStatus,
) {
    val canPrint: Boolean get() = selectedDeliveryNote != null
}

class LandingViewModel(
    private val today: LocalDate,
    private val deliveryNotes: DeliveryNoteSource,
) : ViewModel() {

    private val _state = MutableStateFlow(
        LandingUiState(
            today = today,
            deliveryDate = today.plusDays(1),
            category = null,
            selectedDeliveryNote = null,
            deliveryNoteOptions = emptyList(),
            loadStatus = LoadStatus.LOADING,
        )
    )
    val state: StateFlow<LandingUiState> = _state.asStateFlow()

    private var loading: Job? = null

    init {
        load()
    }

    fun chooseDeliveryDate(date: LocalDate) {
        _state.update { it.copy(deliveryDate = date) }
        load()
    }

    fun chooseCategory(category: Category?) {
        _state.update { it.copy(category = category) }
        load()
    }

    fun chooseDeliveryNote(note: DeliveryNote) {
        _state.update { it.copy(selectedDeliveryNote = note) }
    }

    fun retry() = load()

    /** Replaces any load still running, so the list always matches the latest date and category. */
    private fun load() {
        loading?.cancel()
        _state.update {
            it.copy(selectedDeliveryNote = null, deliveryNoteOptions = emptyList(), loadStatus = LoadStatus.LOADING)
        }
        val (deliveryDate, category) = _state.value.let { it.deliveryDate to it.category }
        loading = viewModelScope.launch {
            try {
                val notes = deliveryNotes.find(deliveryDate, category)
                _state.update { it.copy(deliveryNoteOptions = notes, loadStatus = LoadStatus.LOADED) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loadStatus = LoadStatus.FAILED) }
            }
        }
    }
}
