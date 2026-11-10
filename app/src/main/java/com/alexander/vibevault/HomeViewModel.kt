package com.alexander.vibevault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexander.vibevault.data.ConsumptionRepository
import com.alexander.vibevault.data.SettingsRepository
import com.alexander.vibevault.data.model.ConsumptionEntry
import com.alexander.vibevault.domain.BodySnapshot
import com.alexander.vibevault.domain.Gamification
import com.alexander.vibevault.domain.NicotineModel
import com.alexander.vibevault.domain.TimeTicker
import com.alexander.vibevault.domain.snapshot
import com.alexander.vibevault.domain.toDoses
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime

data class HomeUiState(
    val isLoading: Boolean = true,
    val now: LocalDateTime = LocalDateTime.now(),
    val todayMg: Float = 0f,
    val dailyLimitMg: Float = SettingsRepository.DEFAULT_DAILY_LIMIT_MG,
    val currentStreak: Int = 0,
    val unlockedAchievements: Int = 0,
    val totalAchievements: Int = 0,
    val body: BodySnapshot = BodySnapshot(),
    val recentEntries: List<ConsumptionEntry> = emptyList()
)

class HomeViewModel(
    private val repository: ConsumptionRepository,
    private val settings: SettingsRepository
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        repository.getAllEntries(),
        settings.dailyLimitMg,
        settings.pkParameters,
        TimeTicker.minutes()
    ) { entries, limit, pk, now ->
        val today = now.toLocalDate()
        val totals = Gamification.dailyTotals(entries)
        val achievements = Gamification.achievements(entries, limit, today)
        HomeUiState(
            isLoading = false,
            now = now,
            todayMg = totals[today] ?: 0f,
            dailyLimitMg = limit,
            currentStreak = Gamification.currentStreak(totals, limit, today),
            unlockedAchievements = achievements.count { it.unlocked },
            totalAchievements = achievements.size,
            body = NicotineModel(pk).snapshot(entries.toDoses(now), now),
            recentEntries = entries.take(5)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun deleteEntry(entry: ConsumptionEntry) {
        viewModelScope.launch { repository.deleteEntry(entry) }
    }

    fun restoreEntry(entry: ConsumptionEntry) {
        viewModelScope.launch { repository.insertEntry(entry) }
    }

    fun setDailyLimit(limitMg: Float) = settings.setDailyLimit(limitMg)
}
