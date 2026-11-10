package com.alexander.vibevault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexander.vibevault.data.ConsumptionRepository
import com.alexander.vibevault.data.SettingsRepository
import com.alexander.vibevault.data.model.ConsumptionEntry
import com.alexander.vibevault.domain.Gamification
import com.alexander.vibevault.domain.TimeTicker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class PeriodSummary(val count: Int = 0, val totalMg: Float = 0f)

data class DayBar(val date: LocalDate, val totalMg: Float, val isToday: Boolean)

data class StatsUiState(
    val today: PeriodSummary = PeriodSummary(),
    val last7Days: PeriodSummary = PeriodSummary(),
    val week: List<DayBar> = emptyList(),
    val dailyLimitMg: Float = SettingsRepository.DEFAULT_DAILY_LIMIT_MG,
    val entries: List<ConsumptionEntry> = emptyList()
)

class StatsViewModel(
    private val repository: ConsumptionRepository,
    settings: SettingsRepository
) : ViewModel() {

    val uiState: StateFlow<StatsUiState> =
        combine(repository.getAllEntries(), settings.dailyLimitMg, TimeTicker.days()) { entries, limit, today ->
            buildState(entries, limit, today)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun deleteEntry(entry: ConsumptionEntry) {
        viewModelScope.launch { repository.deleteEntry(entry) }
    }

    fun restoreEntry(entry: ConsumptionEntry) {
        viewModelScope.launch { repository.insertEntry(entry) }
    }

    private fun buildState(entries: List<ConsumptionEntry>, limit: Float, today: LocalDate): StatsUiState {
        val weekStart = today.minusDays(6)
        val totals = Gamification.dailyTotals(entries)
        return StatsUiState(
            today = entries.filter { it.timestamp.toLocalDate() == today }.summarize(),
            last7Days = entries.filter { !it.timestamp.toLocalDate().isBefore(weekStart) }.summarize(),
            week = (0L..6L).map { offset ->
                val day = weekStart.plusDays(offset)
                DayBar(day, totals[day] ?: 0f, isToday = day == today)
            },
            dailyLimitMg = limit,
            entries = entries
        )
    }

    private fun List<ConsumptionEntry>.summarize() =
        PeriodSummary(count = size, totalMg = sumOf { it.nicotineStrength.toDouble() }.toFloat())
}
