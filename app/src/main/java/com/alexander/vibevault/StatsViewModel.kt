package com.alexander.vibevault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexander.vibevault.data.ConsumptionRepository
import com.alexander.vibevault.data.PouchTimer
import com.alexander.vibevault.data.SettingsRepository
import com.alexander.vibevault.data.model.ConsumptionEntry
import com.alexander.vibevault.domain.CostSummary
import com.alexander.vibevault.domain.FirstPouchTrend
import com.alexander.vibevault.domain.Gamification
import com.alexander.vibevault.domain.Insights
import com.alexander.vibevault.domain.PauseStats
import com.alexander.vibevault.domain.StrengthTip
import com.alexander.vibevault.domain.Trigger
import com.alexander.vibevault.domain.WeeklyReview
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
    val entries: List<ConsumptionEntry> = emptyList(),
    val review: WeeklyReview? = null,
    /** Auslöser der letzten 30 Tage. */
    val triggers: List<Pair<Trigger, Int>> = emptyList(),
    val pauses: PauseStats = PauseStats(0, 0, null),
    val pauseMinutes: Int = 0,
    val firstPouch: FirstPouchTrend = FirstPouchTrend(null, null),
    /** Stärken der letzten 30 Tage. */
    val strengthMix: List<Pair<Float, Int>> = emptyList(),
    /** Bezogen auf die letzten 7 Tage. */
    val strengthTip: StrengthTip? = null,
    val cost: CostSummary? = null
)

class StatsViewModel(
    private val repository: ConsumptionRepository,
    settings: SettingsRepository,
    private val pouchTimer: PouchTimer
) : ViewModel() {

    val uiState: StateFlow<StatsUiState> =
        combine(
            repository.getAllEntries(),
            settings.dailyLimitMg,
            settings.habits,
            settings.pouchTargetMinutes,
            TimeTicker.days()
        ) { entries, limit, habits, target, today ->
            buildState(entries, limit, today).withInsights(entries, limit, habits, target, today)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun deleteEntry(entry: ConsumptionEntry) {
        pouchTimer.discard(entry.id)
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

    private fun StatsUiState.withInsights(
        entries: List<ConsumptionEntry>,
        limit: Float,
        habits: com.alexander.vibevault.data.Habits,
        target: Int,
        today: LocalDate
    ): StatsUiState {
        val last30 = entries.filter { !it.timestamp.toLocalDate().isBefore(today.minusDays(29)) }
        val last7 = entries.filter { !it.timestamp.toLocalDate().isBefore(today.minusDays(6)) }
        return copy(
            review = if (entries.isEmpty()) null else Insights.weeklyReview(entries, limit, habits.pauseMinutes, target, today),
            triggers = Insights.triggerCounts(last30),
            pauses = Insights.pauseStats(last30, habits.pauseMinutes, target),
            pauseMinutes = habits.pauseMinutes,
            firstPouch = Insights.firstPouchTrend(entries, habits.wakeTime, today),
            strengthMix = Insights.strengthMix(last30),
            strengthTip = Insights.strengthTip(last7),
            cost = Insights.cost(entries, habits.pricePerCan, habits.pouchesPerCan, today)
        )
    }

    private fun List<ConsumptionEntry>.summarize() =
        PeriodSummary(count = size, totalMg = sumOf { it.nicotineStrength.toDouble() }.toFloat())
}
