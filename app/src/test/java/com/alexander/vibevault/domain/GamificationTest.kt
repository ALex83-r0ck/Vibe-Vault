package com.alexander.vibevault.domain

import com.alexander.vibevault.data.model.ConsumptionEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GamificationTest {
    private val today = LocalDate.of(2026, 11, 10)
    private val limit = 10f

    private fun entry(daysAgo: Long, mg: Float, hour: Int = 12) =
        ConsumptionEntry(timestamp = today.minusDays(daysAgo).atTime(hour, 0), nicotineStrength = mg, brand = "X")

    private fun totals(vararg entries: ConsumptionEntry) = Gamification.dailyTotals(entries.toList())

    @Test
    fun `ohne Einträge gibt es keine Serie`() {
        assertEquals(0, Gamification.currentStreak(emptyMap(), limit, today))
        assertEquals(0, Gamification.bestStreak(emptyMap(), limit, today))
    }

    @Test
    fun `Tagessumme addiert mehrere Einträge am selben Tag`() {
        val t = totals(entry(0, 4f, 8), entry(0, 1.5f, 20))
        assertEquals(5.5f, t.getValue(today), 0.001f)
    }

    @Test
    fun `Serie zählt Tage im Limit inklusive Tagen ohne Eintrag`() {
        // vor 4 Tagen über Limit, danach nur noch wenig bzw. nichts
        val t = totals(entry(4, 15f), entry(3, 5f), entry(1, 8f), entry(0, 2f))
        assertEquals(4, Gamification.currentStreak(t, limit, today))
    }

    @Test
    fun `Serie bricht ab, wenn heute das Limit überschritten ist`() {
        val t = totals(entry(2, 5f), entry(1, 5f), entry(0, 6f), entry(0, 6f))
        assertEquals(0, Gamification.currentStreak(t, limit, today))
        assertEquals(2, Gamification.bestStreak(t, limit, today))
    }

    @Test
    fun `Serie zählt nicht vor den ersten Eintrag zurück`() {
        val t = totals(entry(1, 3f))
        assertEquals(2, Gamification.currentStreak(t, limit, today))
    }

    @Test
    fun `genau auf dem Limit zählt noch als geschafft`() {
        val t = totals(entry(0, 10f))
        assertEquals(1, Gamification.currentStreak(t, limit, today))
    }

    @Test
    fun `nikotinfreier Tag zählt nur für abgeschlossene Tage`() {
        assertFalse(Gamification.hasNicotineFreeDay(totals(entry(0, 3f)), today))
        assertFalse(Gamification.hasNicotineFreeDay(totals(entry(1, 3f), entry(0, 3f)), today))
        assertTrue(Gamification.hasNicotineFreeDay(totals(entry(2, 3f), entry(0, 3f)), today))
    }

    @Test
    fun `Reduktion braucht Daten aus der Vorwoche`() {
        assertFalse(Gamification.reducedComparedToPreviousWeek(totals(entry(0, 1f)), today))
        assertTrue(Gamification.reducedComparedToPreviousWeek(totals(entry(10, 20f), entry(0, 5f)), today))
        assertFalse(Gamification.reducedComparedToPreviousWeek(totals(entry(10, 5f), entry(0, 20f)), today))
    }

    @Test
    fun `pünktlich herausgenommen zählt nur bekannte Dauern bis zum Ziel`() {
        val entries = listOf(
            entry(0, 5f).copy(durationMinutes = 25),
            entry(0, 5f).copy(durationMinutes = 30),
            entry(0, 5f).copy(durationMinutes = 31),
            entry(0, 5f) // unbekannt
        )
        assertEquals(2, Gamification.onTimeRemovals(entries, 30))
    }

    @Test
    fun `Erfolge werden passend freigeschaltet`() {
        val entries = listOf(entry(3, 5f), entry(0, 5f))
        val unlocked = Gamification.achievements(entries, limit, today).filter { it.unlocked }.map { it.id }
        assertEquals(listOf("first_entry", "streak_3", "nicotine_free_day"), unlocked)
    }
}
