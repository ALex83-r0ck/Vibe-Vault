package com.alexander.vibevault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexander.vibevault.data.ConsumptionRepository
import com.alexander.vibevault.data.model.ConsumptionEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** Zuletzt verwendete Kombination aus Marke und Stärke als Schnellauswahl. */
data class EntrySuggestion(val brand: String, val nicotineStrength: Float)

class AddEntryViewModel(private val repository: ConsumptionRepository) : ViewModel() {

    val suggestions: StateFlow<List<EntrySuggestion>> = repository.getAllEntries()
        .map { entries ->
            entries.asSequence()
                .filter { it.brand.isNotBlank() }
                .map { EntrySuggestion(it.brand, it.nicotineStrength) }
                .distinct()
                .take(4)
                .toList()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveEntry(timestamp: LocalDateTime, nicotineStrength: Float, brand: String) {
        viewModelScope.launch {
            repository.insertEntry(
                ConsumptionEntry(
                    timestamp = timestamp,
                    nicotineStrength = nicotineStrength,
                    brand = brand
                )
            )
        }
    }
}
