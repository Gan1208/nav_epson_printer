package com.kowloondairy.navprint.landing

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/** [category] null means 全部. */
data class LandingUiState(
    val today: LocalDate,
    val deliveryDate: LocalDate,
    val category: Category?,
    val selectedDeliveryNote: DeliveryNote?,
    val deliveryNoteOptions: List<DeliveryNote>,
) {
    val canPrint: Boolean get() = selectedDeliveryNote != null
}

class LandingViewModel(
    private val today: LocalDate,
    private val deliveryNotes: DeliveryNoteSource,
) : ViewModel() {

    private val _state = MutableStateFlow(
        stateFor(deliveryDate = today.plusDays(1), category = null)
    )
    val state: StateFlow<LandingUiState> = _state.asStateFlow()

    fun chooseDeliveryDate(date: LocalDate) {
        _state.update { stateFor(deliveryDate = date, category = it.category) }
    }

    fun chooseCategory(category: Category?) {
        _state.update { stateFor(deliveryDate = it.deliveryDate, category = category) }
    }

    fun chooseDeliveryNote(note: DeliveryNote) {
        _state.update { it.copy(selectedDeliveryNote = note) }
    }

    private fun stateFor(deliveryDate: LocalDate, category: Category?) = LandingUiState(
        today = today,
        deliveryDate = deliveryDate,
        category = category,
        selectedDeliveryNote = null,
        deliveryNoteOptions = deliveryNotes.all().filter {
            it.deliveryDate == deliveryDate && (category == null || it.category == category)
        },
    )
}
