package com.alexander.vibevault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexander.vibevault.data.ConsumptionRepository
import com.alexander.vibevault.data.SettingsRepository
import com.alexander.vibevault.domain.Achievement
import com.alexander.vibevault.domain.Gamification
import com.alexander.vibevault.domain.TimeTicker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AchievementsUiState(
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val dailyLimitMg: Float = SettingsRepository.DEFAULT_DAILY_LIMIT_MG,
    val achievements: List<Achievement> = emptyList()
)

class AchievementsViewModel(
    repository: ConsumptionRepository,
    settings: SettingsRepository
) : ViewModel() {

    val uiState: StateFlow<AchievementsUiState> =
        combine(
            repository.getAllEntries(),
            settings.dailyLimitMg,
            settings.pouchTargetMinutes,
            TimeTicker.days()
        ) { entries, limit, target, today ->
            val totals = Gamification.dailyTotals(entries)
            AchievementsUiState(
                currentStreak = Gamification.currentStreak(totals, limit, today),
                bestStreak = Gamification.bestStreak(totals, limit, today),
                dailyLimitMg = limit,
                achievements = Gamification.achievements(entries, limit, today, target)
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AchievementsUiState())
}
