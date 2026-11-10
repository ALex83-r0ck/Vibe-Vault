package com.alexander.vibevault.domain

import com.alexander.vibevault.data.model.ConsumptionEntry
import java.time.LocalDate

data class Achievement(
    val id: String,
    val emoji: String,
    val title: String,
    val description: String,
    val unlocked: Boolean,
    /** Optionaler Fortschritt für noch gesperrte Erfolge, z. B. "2/7". */
    val progress: String? = null
)

/**
 * Spiellogik von VibeVault. Belohnt wird Zurückhaltung, nicht Konsum:
 * Ein Tag zählt als Erfolg, wenn die Tagessumme das Limit nicht überschreitet.
 * Tage ohne Eintrag (nach dem ersten Eintrag) zählen als nikotinfrei.
 */
object Gamification {

    fun dailyTotals(entries: List<ConsumptionEntry>): Map<LocalDate, Float> =
        entries.groupBy { it.timestamp.toLocalDate() }
            .mapValues { (_, dayEntries) -> dayEntries.sumOf { it.nicotineStrength.toDouble() }.toFloat() }

    /** Aufeinanderfolgende Tage im Limit, rückwärts ab heute gezählt. */
    fun currentStreak(totals: Map<LocalDate, Float>, limitMg: Float, today: LocalDate): Int {
        val firstDay = totals.keys.minOrNull() ?: return 0
        var day = today
        var streak = 0
        while (!day.isBefore(firstDay) && (totals[day] ?: 0f) <= limitMg) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    fun bestStreak(totals: Map<LocalDate, Float>, limitMg: Float, today: LocalDate): Int {
        val firstDay = totals.keys.minOrNull() ?: return 0
        var best = 0
        var run = 0
        var day = firstDay
        while (!day.isAfter(today)) {
            if ((totals[day] ?: 0f) <= limitMg) {
                run++
                best = maxOf(best, run)
            } else {
                run = 0
            }
            day = day.plusDays(1)
        }
        return best
    }

    /** Gab es seit dem ersten Eintrag einen abgeschlossenen Tag ganz ohne Konsum? Heute zählt noch nicht. */
    fun hasNicotineFreeDay(totals: Map<LocalDate, Float>, today: LocalDate): Boolean {
        var day = totals.keys.minOrNull() ?: return false
        while (day.isBefore(today)) {
            if ((totals[day] ?: 0f) == 0f) return true
            day = day.plusDays(1)
        }
        return false
    }

    /** Weniger in den letzten 7 Tagen als in den 7 Tagen davor. */
    fun reducedComparedToPreviousWeek(totals: Map<LocalDate, Float>, today: LocalDate): Boolean {
        val lastWeek = sumRange(totals, today.minusDays(6), today)
        val previousWeek = sumRange(totals, today.minusDays(13), today.minusDays(7))
        return previousWeek > 0f && lastWeek < previousWeek
    }

    fun achievements(entries: List<ConsumptionEntry>, limitMg: Float, today: LocalDate): List<Achievement> {
        val totals = dailyTotals(entries)
        val best = bestStreak(totals, limitMg, today)

        fun streakAchievement(id: String, emoji: String, title: String, days: Int) = Achievement(
            id = id,
            emoji = emoji,
            title = title,
            description = "$days Tage in Folge im Limit",
            unlocked = best >= days,
            progress = "${minOf(best, days)}/$days"
        )

        return listOf(
            Achievement(
                id = "first_entry",
                emoji = "📝",
                title = "Der Anfang",
                description = "Ersten Eintrag angelegt – ehrlich hinschauen ist der erste Schritt",
                unlocked = entries.isNotEmpty()
            ),
            streakAchievement("streak_3", "🔥", "Dranbleiben", 3),
            streakAchievement("streak_7", "💪", "Eine Woche stark", 7),
            streakAchievement("streak_30", "🏆", "Monatsheld", 30),
            Achievement(
                id = "nicotine_free_day",
                emoji = "🌿",
                title = "Nikotinfrei",
                description = "Einen ganzen Tag ohne Nikotin geschafft",
                unlocked = hasNicotineFreeDay(totals, today)
            ),
            Achievement(
                id = "reduced_week",
                emoji = "📉",
                title = "Weniger ist mehr",
                description = "Weniger als in der Vorwoche konsumiert",
                unlocked = reducedComparedToPreviousWeek(totals, today)
            )
        )
    }

    private fun sumRange(totals: Map<LocalDate, Float>, from: LocalDate, to: LocalDate): Float =
        totals.filterKeys { !it.isBefore(from) && !it.isAfter(to) }.values.sum()
}
