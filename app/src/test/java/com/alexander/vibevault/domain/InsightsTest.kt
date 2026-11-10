package com.alexander.vibevault.domain

import com.alexander.vibevault.data.model.ConsumptionEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class InsightsTest {
    private val today = LocalDate.of(2026, 11, 10) // Dienstag
    private fun at(h: Int, m: Int = 0, daysAgo: Long = 0): LocalDateTime = today.minusDays(daysAgo).atTime(h, m)

    private fun entry(
        time: LocalDateTime,
        mg: Float = 6f,
        duration: Int? = 30,
        trigger: Trigger? = null,
        side: Side? = null,
        feeling: Feeling? = null,
        id: Int = 0
    ) = ConsumptionEntry(
        id = id, timestamp = time, nicotineStrength = mg, brand = "X", durationMinutes = duration,
        trigger = trigger?.key, side = side?.key, feeling = feeling?.key
    )

    // ---------- Pause ----------

    @Test
    fun `Pause läuft nach dem Rausnehmen`() {
        val entries = listOf(entry(at(10), duration = 20))
        assertEquals(at(11, 5), Insights.pauseEndsAt(entries, null, 45, 30, at(10, 30)))
        // vorbei
        assertNull(Insights.pauseEndsAt(entries, null, 45, 30, at(11, 10)))
        // aus oder Pouch gerade drin
        assertNull(Insights.pauseEndsAt(entries, null, 0, 30, at(10, 30)))
        assertNull(Insights.pauseEndsAt(entries, 1, 45, 30, at(10, 30)))
    }

    @Test
    fun `Pausen-Statistik ignoriert die Nacht`() {
        val entries = listOf(
            entry(at(22, daysAgo = 1), duration = 30), // Nacht bis 8:00 → zählt nicht
            entry(at(8), duration = 30),               // raus 8:30
            entry(at(9)),                              // 30 min Abstand → nicht gehalten
            entry(at(11))                              // 90 min → gehalten
        )
        val stats = Insights.pauseStats(entries, 45, 30)
        assertEquals(2, stats.total)
        assertEquals(1, stats.kept)
        assertEquals(60L, stats.averageGapMinutes)
    }

    // ---------- Ampel ----------

    @Test
    fun `Ampel nach Blutspiegel`() {
        assertEquals(LevelLight.GREEN, Insights.light(2.0))
        assertEquals(LevelLight.YELLOW, Insights.light(8.0))
        assertEquals(LevelLight.RED, Insights.light(15.0))
    }

    // ---------- Auslöser ----------

    @Test
    fun `Auslöser werden gezählt und sortiert`() {
        val entries = listOf(
            entry(at(8), trigger = Trigger.COFFEE),
            entry(at(10), trigger = Trigger.BORED),
            entry(at(12), trigger = Trigger.BORED),
            entry(at(14))
        )
        assertEquals(listOf(Trigger.BORED to 2, Trigger.COFFEE to 1), Insights.triggerCounts(entries))
    }

    // ---------- Schlaf ----------

    @Test
    fun `nächste Schlafenszeit heute oder morgen`() {
        assertEquals(at(23), Insights.nextBedtime(at(20), LocalTime.of(23, 0)))
        assertEquals(at(23).plusDays(1), Insights.nextBedtime(at(23, 30), LocalTime.of(23, 0)))
    }

    @Test
    fun `Schlaf-Fenster - letzter Pouch liegt vor der Schlafenszeit und passt`() {
        val model = NicotineModel(PkParameters())
        val entries = listOf(entry(at(8), mg = 12f), entry(at(12), mg = 12f))
        val window = Insights.sleepWindow(model, entries, null, 30, LocalTime.of(23, 0), at(14))
        val last = assertNotNull(window.lastPouchAt).let { window.lastPouchAt!! }
        assertTrue(last.isBefore(at(23)))
        assertTrue(last.isAfter(at(14)))
        assertEquals(12f, window.typicalStrength(), 0.01f)
        assertTrue(window.levelAtBedtime < Insights.SLEEP_OK_NG_PER_ML)
    }

    private fun SleepWindow.typicalStrength() = typicalStrengthMg

    @Test
    fun `Schlaf-Fenster - kurz vor dem Schlafen kein weiterer Pouch mehr`() {
        val model = NicotineModel(PkParameters())
        val entries = listOf(entry(at(22), mg = 20f, duration = null, id = 1))
        val window = Insights.sleepWindow(model, entries, 1, 30, LocalTime.of(23, 0), at(22, 10))
        assertNull(window.lastPouchAt)
    }

    // ---------- Erster Pouch ----------

    @Test
    fun `erster Pouch nach dem Aufstehen - Wochenvergleich`() {
        val entries = listOf(
            entry(at(7, 30)), entry(at(12)),          // heute: 30 min
            entry(at(8, 0, daysAgo = 1)),             // gestern: 60 min
            entry(at(7, 10, daysAgo = 8)),            // Vorwoche: 10 min
            entry(at(6, 30, daysAgo = 9))             // vor dem Aufstehen → 0
        )
        val trend = Insights.firstPouchTrend(entries, LocalTime.of(7, 0), today)
        assertEquals(45L, trend.thisWeekMinutes)
        assertEquals(5L, trend.previousWeekMinutes)
    }

    // ---------- Stärke ----------

    @Test
    fun `Stärke-Tipp - eine Stufe leichter`() {
        val entries = listOf(entry(at(8), mg = 20f), entry(at(10), mg = 20f), entry(at(12), mg = 6f))
        val tip = Insights.strengthTip(entries)!!
        assertEquals(20f, tip.fromMg)
        assertEquals(16f, tip.toMg)
        assertEquals(8f, tip.savedMg)
        assertEquals(listOf(6f to 1, 20f to 2), Insights.strengthMix(entries))
    }

    // ---------- Kosten ----------

    @Test
    fun `Kosten pro Monat und Jahr`() {
        val entries = (0 until 10).map { entry(at(8 + it)) } // 10 Pouches heute
        val cost = Insights.cost(entries, 5f, 20, today)!!
        assertEquals(0.25, cost.pricePerPouchEuro, 0.0001)
        assertEquals(2.5, cost.thisMonthEuro, 0.0001)
        assertEquals(2.5 * 365, cost.perYearEuro, 0.01) // erst seit 1 Tag dabei → hochgerechnet
        assertNull(Insights.cost(entries, null, 20, today))
    }

    // ---------- Seite / Gefühl ----------

    @Test
    fun `Seite wechseln und Gefühl merken`() {
        val entries = listOf(
            entry(at(8), side = Side.LEFT, mg = 20f, feeling = Feeling.DIZZY),
            entry(at(10), mg = 20f, feeling = Feeling.SICK),
            entry(at(12), mg = 6f, feeling = Feeling.GOOD)
        )
        assertEquals(Side.RIGHT, Insights.suggestedSide(entries))
        assertNull(Insights.suggestedSide(listOf(entry(at(8)))))
        assertEquals(2, Insights.badFeelingsWith(entries, 20f, today))
        assertEquals(0, Insights.badFeelingsWith(entries, 25f, today))
    }

    // ---------- Wochenrückblick ----------

    @Test
    fun `Wochenrückblick vergleicht mit der Vorwoche`() {
        val entries = listOf(
            entry(at(8), mg = 10f, trigger = Trigger.STRESS),
            entry(at(12, daysAgo = 2), mg = 10f, trigger = Trigger.STRESS),
            entry(at(12, daysAgo = 3), mg = 10f, trigger = Trigger.COFFEE),
            entry(at(12, daysAgo = 8), mg = 40f)
        )
        val review = Insights.weeklyReview(entries, 15f, 45, 30, today)
        assertEquals(30f, review.totalMg)
        assertEquals(3, review.pouches)
        assertEquals(-25, review.changePercent)
        assertEquals(7, review.daysInLimit)
        assertEquals(7, review.daysTracked)
        assertEquals(Trigger.STRESS, review.topTrigger)
        assertEquals(2.0 / 3, review.topTriggerShare, 0.001)
    }

    @Test
    fun `Wochenrückblick am nächsten Sonntag um 19 Uhr`() {
        assertEquals(LocalDate.of(2026, 11, 15).atTime(19, 0), Insights.nextWeeklyReviewAt(at(10)))
        val sundayEvening = LocalDate.of(2026, 11, 15).atTime(20, 0)
        assertEquals(LocalDate.of(2026, 11, 22).atTime(19, 0), Insights.nextWeeklyReviewAt(sundayEvening))
    }
}
