package com.alexander.vibevault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexander.vibevault.data.VibeVaultDatabase
import com.alexander.vibevault.data.model.ConsumptionEntry
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class AddEntryViewModel(private val database: VibeVaultDatabase) : ViewModel() {
    fun saveEntry(timestamp: LocalDateTime, nicotineStrength: Float, brand: String) {
        viewModelScope.launch {
            database.consumptionDao().insert(ConsumptionEntry(timestamp = timestamp, nicotineStrength = nicotineStrength, brand = brand))
        }
    }
}