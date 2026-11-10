package com.alexander.vibevault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexander.vibevault.data.ConsumptionRepository
import com.alexander.vibevault.data.model.ConsumptionEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class PeriodSummary(val count: Int = 0, val totalMg: Float = 0f)

data class StatsUiState(
    val today: PeriodSummary = PeriodSummary(),
    val last7Days: PeriodSummary = PeriodSummary(),
    val entries: List<ConsumptionEntry> = emptyList()
)

class StatsViewModel(repository: ConsumptionRepository) : ViewModel() {

    val uiState: StateFlow<StatsUiState> = repository.getAllEntries()
        .map { entries -> buildState(entries, LocalDate.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    private fun buildState(entries: List<ConsumptionEntry>, today: LocalDate): StatsUiState {
        val weekStart = today.minusDays(6)
        return StatsUiState(
            today = entries.filter { it.timestamp.toLocalDate() == today }.summarize(),
            last7Days = entries.filter { !it.timestamp.toLocalDate().isBefore(weekStart) }.summarize(),
            entries = entries
        )
    }

    private fun List<ConsumptionEntry>.summarize() =
        PeriodSummary(count = size, totalMg = sumOf { it.nicotineStrength.toDouble() }.toFloat())
}
