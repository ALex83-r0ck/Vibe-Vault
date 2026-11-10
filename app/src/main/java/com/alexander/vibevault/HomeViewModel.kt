package com.alexander.vibevault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexander.vibevault.data.ActivePouch
import com.alexander.vibevault.data.ConsumptionRepository
import com.alexander.vibevault.data.PouchTimer
import com.alexander.vibevault.data.SettingsRepository
import com.alexander.vibevault.data.model.ConsumptionEntry
import com.alexander.vibevault.domain.BodySnapshot
import com.alexander.vibevault.domain.Gamification
import com.alexander.vibevault.domain.NicotineModel
import com.alexander.vibevault.domain.TimeTicker
import com.alexander.vibevault.domain.snapshot
import com.alexander.vibevault.domain.toDoses
import kotlinx.coroutines.CoroutineScope
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
    val activePouch: ActivePouch? = null,
    val activeEntry: ConsumptionEntry? = null,
    val pouchTargetMinutes: Int = SettingsRepository.DEFAULT_POUCH_TARGET_MIN,
    val recentEntries: List<ConsumptionEntry> = emptyList()
)

private data class HomeSettings(val limit: Float, val target: Int, val active: ActivePouch?)

class HomeViewModel(
    private val repository: ConsumptionRepository,
    private val settings: SettingsRepository,
    private val pouchTimer: PouchTimer,
    private val appScope: CoroutineScope
) : ViewModel() {

    private val homeSettings = combine(settings.dailyLimitMg, settings.pouchTargetMinutes, settings.activePouch) { l, t, a ->
        HomeSettings(l, t, a)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        repository.getAllEntries(),
        homeSettings,
        settings.pkParameters,
        TimeTicker.minutes()
    ) { entries, s, pk, now ->
        val today = now.toLocalDate()
        val totals = Gamification.dailyTotals(entries)
        val achievements = Gamification.achievements(entries, s.limit, today, s.target)
        HomeUiState(
            isLoading = false,
            now = now,
            todayMg = totals[today] ?: 0f,
            dailyLimitMg = s.limit,
            currentStreak = Gamification.currentStreak(totals, s.limit, today),
            unlockedAchievements = achievements.count { it.unlocked },
            totalAchievements = achievements.size,
            body = NicotineModel(pk).snapshot(entries.toDoses(now, s.active?.entryId, s.target), now),
            activePouch = s.active,
            activeEntry = s.active?.let { a -> entries.firstOrNull { it.id == a.entryId } },
            pouchTargetMinutes = s.target,
            recentEntries = entries.take(5)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Pouch jetzt raus. */
    fun removePouchNow() {
        appScope.launch { pouchTimer.stop(LocalDateTime.now()) }
    }

    /** Vergessen zu stoppen: Dauer auf die Zieldauer setzen. */
    fun removePouchAtTarget() {
        val active = settings.activePouch.value ?: return
        appScope.launch { pouchTimer.stop(active.startedAt.plusMinutes(settings.pouchTargetMinutes.value.toLong())) }
    }

    fun deleteEntry(entry: ConsumptionEntry) {
        pouchTimer.discard(entry.id)
        viewModelScope.launch { repository.deleteEntry(entry) }
    }

    fun restoreEntry(entry: ConsumptionEntry) {
        viewModelScope.launch { repository.insertEntry(entry) }
    }

    fun setDailyLimit(limitMg: Float) = settings.setDailyLimit(limitMg)
}
