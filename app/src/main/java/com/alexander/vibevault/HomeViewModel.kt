package com.alexander.vibevault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexander.vibevault.data.ActivePouch
import com.alexander.vibevault.data.ConsumptionRepository
import com.alexander.vibevault.data.PouchTimer
import com.alexander.vibevault.data.SettingsRepository
import com.alexander.vibevault.data.model.ConsumptionEntry
import com.alexander.vibevault.domain.BodySnapshot
import com.alexander.vibevault.domain.Feeling
import com.alexander.vibevault.domain.Gamification
import com.alexander.vibevault.domain.Insights
import com.alexander.vibevault.domain.SleepWindow
import com.alexander.vibevault.data.Habits
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
    val recentEntries: List<ConsumptionEntry> = emptyList(),
    /** Ende der Pause nach dem letzten Pouch; null = keine Pause aktiv. */
    val pauseEndsAt: LocalDateTime? = null,
    val pauseMinutes: Int = 0,
    /** Nur abends (≤ 8 h vor dem Schlafen) gesetzt. */
    val sleepWindow: SleepWindow? = null,
    /** Kürzlich rausgenommener Pouch, zu dem noch "Wie war's?" offen ist. */
    val feelingCandidate: ConsumptionEntry? = null
)

private data class HomeSettings(val limit: Float, val target: Int, val active: ActivePouch?, val habits: Habits)

class HomeViewModel(
    private val repository: ConsumptionRepository,
    private val settings: SettingsRepository,
    private val pouchTimer: PouchTimer,
    private val appScope: CoroutineScope
) : ViewModel() {

    private val homeSettings = combine(
        settings.dailyLimitMg, settings.pouchTargetMinutes, settings.activePouch, settings.habits
    ) { l, t, a, h -> HomeSettings(l, t, a, h) }

    /** Einträge, bei denen "Wie war's?" weggeklickt wurde (nur für diese Sitzung). */
    private val dismissedFeelings = kotlinx.coroutines.flow.MutableStateFlow<Set<Int>>(emptySet())

    val uiState: StateFlow<HomeUiState> = combine(
        repository.getAllEntries(),
        homeSettings,
        settings.pkParameters,
        TimeTicker.minutes(),
        dismissedFeelings
    ) { entries, s, pk, now, dismissed ->
        val today = now.toLocalDate()
        val totals = Gamification.dailyTotals(entries)
        val achievements = Gamification.achievements(entries, s.limit, today, s.target, s.habits.pauseMinutes)
        val model = NicotineModel(pk)
        val sleep = Insights.sleepWindow(model, entries, s.active?.entryId, s.target, s.habits.bedtime, now)
            .takeIf { java.time.Duration.between(now, it.bedtime).toHours() < 8 }
        val feelingCandidate = entries.firstOrNull()?.takeIf { e ->
            e.id != s.active?.entryId && e.durationMinutes != null && e.feeling == null && e.id !in dismissed &&
                java.time.Duration.between(Insights.removalOf(e, s.target), now).toMinutes() in 0..60
        }
        HomeUiState(
            isLoading = false,
            now = now,
            todayMg = totals[today] ?: 0f,
            dailyLimitMg = s.limit,
            currentStreak = Gamification.currentStreak(totals, s.limit, today),
            unlockedAchievements = achievements.count { it.unlocked },
            totalAchievements = achievements.size,
            body = model.snapshot(entries.toDoses(now, s.active?.entryId, s.target), now),
            activePouch = s.active,
            activeEntry = s.active?.let { a -> entries.firstOrNull { it.id == a.entryId } },
            pouchTargetMinutes = s.target,
            recentEntries = entries.take(5),
            pauseEndsAt = Insights.pauseEndsAt(entries, s.active?.entryId, s.habits.pauseMinutes, s.target, now),
            pauseMinutes = s.habits.pauseMinutes,
            sleepWindow = sleep,
            feelingCandidate = feelingCandidate
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

    fun setFeeling(entry: ConsumptionEntry, feeling: Feeling) {
        appScope.launch { repository.setFeeling(entry.id, feeling.key) }
    }

    fun dismissFeeling(entry: ConsumptionEntry) {
        dismissedFeelings.value = dismissedFeelings.value + entry.id
    }
}
