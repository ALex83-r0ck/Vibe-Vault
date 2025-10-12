package com.alexander.vibevault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexander.vibevault.data.ConsumptionRepository
import com.alexander.vibevault.data.model.ConsumptionEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class StatsViewModel(private val repository: ConsumptionRepository) : ViewModel() {
    private val _entries = MutableStateFlow<List<ConsumptionEntry>>(emptyList())
    val entries: StateFlow<List<ConsumptionEntry>> = _entries

    init {
        viewModelScope.launch {
            repository.getAllEntries().collect { entries ->
                _entries.value = entries
            }
        }
    }
}