package com.alexander.vibevault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexander.vibevault.data.ConsumptionRepository
import com.alexander.vibevault.data.model.ConsumptionEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: ConsumptionRepository) : ViewModel() {
    private val _recentEntries = MutableStateFlow<List<ConsumptionEntry>>(emptyList())
    val recentEntries: StateFlow<List<ConsumptionEntry>> = _recentEntries

    init {
        viewModelScope.launch {
            repository.getAllEntries().collect { entries ->
                _recentEntries.value = entries.take(5) // Zeige die letzten 5 Einträge
            }
        }
    }
}