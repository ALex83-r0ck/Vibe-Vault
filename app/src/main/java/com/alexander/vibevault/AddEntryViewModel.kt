package com.alexander.vibevault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexander.vibevault.data.ConsumptionRepository
import com.alexander.vibevault.data.model.ConsumptionEntry
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class AddEntryViewModel(private val repository: ConsumptionRepository) : ViewModel() {

    fun saveEntry(timestamp: LocalDateTime, nicotineStrength: Float, brand: String) {
        viewModelScope.launch {
            repository.insertEntry(ConsumptionEntry(
                timestamp = timestamp,
                nicotineStrength = nicotineStrength,
                brand = brand
            ))
        }
    }
}