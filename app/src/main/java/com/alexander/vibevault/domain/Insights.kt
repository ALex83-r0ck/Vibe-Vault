package com.alexander.vibevault.domain

import com.alexander.vibevault.data.model.ConsumptionEntry
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Was gerade los war, als der Pouch reinkam. */
enum class Trigger(val key: String, val emoji: String, val label: String) {
    COFFEE("coffee", "☕", "Kaffee"),
    STRESS("stress", "😤", "Stress"),
    WORK("work", "💻", "Arbeit"),
    BORED("bored", "🥱", "Langeweile"),
    MEAL("meal", "🍽️", "Nach dem Essen"),
    SOCIAL("social", "🍻", "Mit Leuten"),
    CRAVING("craving", "🫠", "Verlangen");

    companion object {
        fun of(key: String?): Trigger? = entries.firstOrNull { it.key == key }
    }
}

/** Wie es sich danach angefühlt hat. */
enum class Feeling(val key: String, val emoji: String, val label: String, val bad: Boolean) {
    GOOD("good", "😊", "Gut", false),
    DIZZY("dizzy", "😵‍💫", "Schwindelig", true),
    SICK("sick", "🤢", "Übel", true);

    companion object {
        fun of(key: String?): Feeling? = entries.firstOrNull { it.key == key }
    }
}

enum class Side(val key: String, val label: String) {
    LEFT("L", "Links"), RIGHT("R", "Rechts");

    val other: Side get() = if (this == LEFT) RIGHT else LEFT

    companion object {
        fun of(key: String?): Side? = entries.firstOrNull { it.key == key }
    }
}

/** Ampel für den aktuellen Nikotinspiegel. */
enum class LevelLight(val emoji: String, val label: String) {
    GREEN("🟢", "niedrig"),
    YELLOW("🟡", "noch spürbar"),
    RED("🔴", "noch hoch")
}

data class PauseStats(val kept: Int, val total: Int, val averageGapMinutes: Long?)

data class SleepWindow(
    val bedtime: LocalDateTime,
    /** Nikotinspiegel zur Schlafenszeit mit dem, was bisher eingetragen ist. */
    val levelAtBedtime: Double,
    /** Spätester Zeitpunkt für einen weiteren (typischen) Pouch; null = heute lieber keinen mehr. */
    val lastPouchAt: LocalDateTime?,
    val typicalStrengthMg: Float
)

data class FirstPouchTrend(
    /** Ø Minuten nach dem Aufstehen bis zum ersten Pouch, letzte 7 Tage. */
    val thisWeekMinutes: Long?,
    val previousWeekMinutes: Long?
)

data class StrengthTip(val fromMg: Float, val toMg: Float, val count: Int, val savedMg: Float)

data class CostSummary(val thisMonthEuro: Double, val perYearEuro: Double, val pricePerPouchEuro: Double)

data class WeeklyReview(
    val totalMg: Float,
    val pouches: Int,
    val previousTotalMg: Float,
    val daysInLimit: Int,
    /** Tage der Woche, an denen die App schon benutzt wurde (max. 7). */
    val daysTracked: Int,
    val pauses: PauseStats,
    val topTrigger: Trigger?,
    val topTriggerShare: Double
) {
    val changePercent: Int? =
        if (previousTotalMg > 0f) ((totalMg - previousTotalMg) / previousTotalMg * 100).roundToInt() else null
}

/**
 * Auswertungen für bewussteren Konsum. Alles reine Funktionen auf den Einträgen –
 * nichts wird verboten, die App zeigt nur Muster und Zusammenhänge.
 */
object Insights {

    /** Unterhalb davon gilt der Spiegel zur Schlafenszeit als "weitgehend abgebaut". */
    const val SLEEP_OK_NG_PER_ML = 5.0
    const val LIGHT_YELLOW_NG_PER_ML = 5.0
    const val LIGHT_RED_NG_PER_ML = 12.0

    /** Übliche Stärken – für den Vorschlag "eine Stufe leichter". */
    val STRENGTH_STEPS = listOf(3f, 4f, 6f, 8f, 10f, 12f, 16f, 20f, 25f, 30f, 50f)

    fun light(ngPerMl: Double): LevelLight = when {
        ngPerMl >= LIGHT_RED_NG_PER_ML -> LevelLight.RED
        ngPerMl >= LIGHT_YELLOW_NG_PER_ML -> LevelLight.YELLOW
        else -> LevelLight.GREEN
    }

    // ---------- Pause ----------

    /** Wann der Pouch raus kam; ohne gespeicherte Dauer wird die Zieldauer angenommen. */
    fun removalOf(entry: ConsumptionEntry, targetMinutes: Int): LocalDateTime =
        entry.timestamp.plusMinutes((entry.durationMinutes ?: targetMinutes).toLong())

    /** Ende der Pause nach dem zuletzt herausgenommenen Pouch; null = keine Pause aktiv/eingestellt. */
    fun pauseEndsAt(
        entries: List<ConsumptionEntry>,
        activeEntryId: Int?,
        pauseMinutes: Int,
        targetMinutes: Int,
        now: LocalDateTime
    ): LocalDateTime? {
        if (pauseMinutes <= 0 || activeEntryId != null) return null
        val last = entries.filter { !it.timestamp.isAfter(now) }.maxByOrNull { it.timestamp } ?: return null
        val end = removalOf(last, targetMinutes).plusMinutes(pauseMinutes.toLong())
        return end.takeIf { it.isAfter(now) }
    }

    /**
     * Abstände zwischen Rausnehmen und nächstem Pouch am selben Tag (die Nacht zählt nicht mit).
     */
    fun pauseStats(entries: List<ConsumptionEntry>, pauseMinutes: Int, targetMinutes: Int): PauseStats {
        val sorted = entries.sortedBy { it.timestamp }
        val gaps = sorted.zipWithNext()
            .filter { (prev, next) -> prev.timestamp.toLocalDate() == next.timestamp.toLocalDate() }
            .map { (prev, next) -> Duration.between(removalOf(prev, targetMinutes), next.timestamp).toMinutes() }
            .filter { it >= 0 }
        if (gaps.isEmpty()) return PauseStats(0, 0, null)
        return PauseStats(
            kept = if (pauseMinutes > 0) gaps.count { it >= pauseMinutes } else 0,
            total = gaps.size,
            averageGapMinutes = gaps.average().roundToLong()
        )
    }

    // ---------- Auslöser ----------

    fun triggerCounts(entries: List<ConsumptionEntry>): List<Pair<Trigger, Int>> =
        entries.mapNotNull { Trigger.of(it.trigger) }
            .groupingBy { it }.eachCount()
            .toList()
            .sortedByDescending { it.second }

    // ---------- Schlaf ----------

    /** Nächste Schlafenszeit ab [now] (heute, oder morgen wenn schon vorbei). */
    fun nextBedtime(now: LocalDateTime, bedtime: LocalTime): LocalDateTime {
        val today = now.toLocalDate().atTime(bedtime)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    /** Typische Stärke: Median der letzten 10 Einträge. */
    fun typicalStrength(entries: List<ConsumptionEntry>): Float {
        val recent = entries.sortedByDescending { it.timestamp }.take(10).map { it.nicotineStrength }.sorted()
        return if (recent.isEmpty()) 6f else recent[recent.size / 2]
    }

    fun sleepWindow(
        model: NicotineModel,
        entries: List<ConsumptionEntry>,
        activeEntryId: Int?,
        targetMinutes: Int,
        bedtime: LocalTime,
        now: LocalDateTime
    ): SleepWindow {
        val bed = nextBedtime(now, bedtime)
        val doses = entries.toDoses(now, activeEntryId, targetMinutes)
        val typical = typicalStrength(entries)
        fun levelIfPouchAt(t: LocalDateTime): Double =
            model.nicotineNgPerMl(doses + Dose(t, typical.toDouble(), t.plusMinutes(targetMinutes.toLong())), bed)

        // Rückwärts ab Schlafenszeit in 15-min-Schritten: der späteste Zeitpunkt, der noch passt
        var lastOk: LocalDateTime? = null
        var t = bed.minusMinutes(15)
        while (!t.isBefore(now)) {
            if (levelIfPouchAt(t) <= SLEEP_OK_NG_PER_ML) {
                lastOk = t
                break
            }
            t = t.minusMinutes(15)
        }
        return SleepWindow(bed, model.nicotineNgPerMl(doses, bed), lastOk, typical)
    }

    // ---------- Erster Pouch am Morgen ----------

    fun firstPouchTrend(entries: List<ConsumptionEntry>, wakeTime: LocalTime, today: LocalDate): FirstPouchTrend {
        val firstPerDay = entries.groupBy { it.timestamp.toLocalDate() }
            .mapValues { (day, list) ->
                val first = list.minOf { it.timestamp }
                Duration.between(day.atTime(wakeTime), first).toMinutes().coerceAtLeast(0)
            }
        fun avg(from: LocalDate, to: LocalDate): Long? =
            firstPerDay.filterKeys { !it.isBefore(from) && !it.isAfter(to) }.values
                .takeIf { it.isNotEmpty() }?.average()?.roundToLong()
        return FirstPouchTrend(
            thisWeekMinutes = avg(today.minusDays(6), today),
            previousWeekMinutes = avg(today.minusDays(13), today.minusDays(7))
        )
    }

    // ---------- Stärke ----------

    /** Wie oft welche Stärke, aufsteigend nach Stärke. */
    fun strengthMix(entries: List<ConsumptionEntry>): List<Pair<Float, Int>> =
        entries.groupingBy { it.nicotineStrength }.eachCount().toList().sortedBy { it.first }

    /** "Mit X mg statt Y mg wären es Z mg weniger gewesen" für die häufigste Stärke. */
    fun strengthTip(entries: List<ConsumptionEntry>): StrengthTip? {
        val (strength, count) = entries.groupingBy { it.nicotineStrength }.eachCount()
            .maxByOrNull { it.value }?.toPair() ?: return null
        val lower = STRENGTH_STEPS.lastOrNull { it < strength } ?: return null
        return StrengthTip(strength, lower, count, (strength - lower) * count)
    }

    // ---------- Kosten ----------

    fun cost(entries: List<ConsumptionEntry>, pricePerCan: Float?, pouchesPerCan: Int, today: LocalDate): CostSummary? {
        if (pricePerCan == null || pricePerCan <= 0f || pouchesPerCan <= 0) return null
        val perPouch = pricePerCan.toDouble() / pouchesPerCan
        val thisMonth = entries.count {
            val d = it.timestamp.toLocalDate()
            d.year == today.year && d.month == today.month
        }
        val last30 = entries.count { !it.timestamp.toLocalDate().isBefore(today.minusDays(29)) }
        val firstDay = entries.minOfOrNull { it.timestamp.toLocalDate() } ?: today
        val days = (java.time.temporal.ChronoUnit.DAYS.between(firstDay, today) + 1).coerceIn(1, 30)
        return CostSummary(
            thisMonthEuro = thisMonth * perPouch,
            perYearEuro = last30.toDouble() / days * 365 * perPouch,
            pricePerPouchEuro = perPouch
        )
    }

    // ---------- Seite ----------

    /** Gegenüber der zuletzt benutzten Seite; null, wenn noch nie eine angegeben wurde. */
    fun suggestedSide(entries: List<ConsumptionEntry>): Side? =
        entries.sortedByDescending { it.timestamp }.firstNotNullOfOrNull { Side.of(it.side) }?.other

    // ---------- Gefühl ----------

    /** Wie oft es mit dieser Stärke in den letzten 30 Tagen schwindelig/übel war. */
    fun badFeelingsWith(entries: List<ConsumptionEntry>, strengthMg: Float, today: LocalDate): Int =
        entries.count {
            it.nicotineStrength >= strengthMg - 0.01f &&
                Feeling.of(it.feeling)?.bad == true &&
                !it.timestamp.toLocalDate().isBefore(today.minusDays(29))
        }

    // ---------- Wochenrückblick ----------

    fun weeklyReview(
        entries: List<ConsumptionEntry>,
        limitMg: Float,
        pauseMinutes: Int,
        targetMinutes: Int,
        today: LocalDate
    ): WeeklyReview {
        val from = today.minusDays(6)
        fun inRange(e: ConsumptionEntry, a: LocalDate, b: LocalDate) =
            e.timestamp.toLocalDate().let { !it.isBefore(a) && !it.isAfter(b) }
        val week = entries.filter { inRange(it, from, today) }
        val previous = entries.filter { inRange(it, today.minusDays(13), today.minusDays(7)) }
        val totals = Gamification.dailyTotals(week)
        val firstDay = entries.minOfOrNull { it.timestamp.toLocalDate() }
        val tracked = (0..6).map { from.plusDays(it.toLong()) }
            .filter { firstDay != null && !it.isBefore(firstDay) }
        val daysInLimit = tracked.count { (totals[it] ?: 0f) <= limitMg }
        val triggers = triggerCounts(week)
        val tagged = triggers.sumOf { it.second }
        return WeeklyReview(
            totalMg = week.sumOf { it.nicotineStrength.toDouble() }.toFloat(),
            pouches = week.size,
            previousTotalMg = previous.sumOf { it.nicotineStrength.toDouble() }.toFloat(),
            daysInLimit = daysInLimit,
            daysTracked = tracked.size,
            pauses = pauseStats(week, pauseMinutes, targetMinutes),
            topTrigger = triggers.firstOrNull()?.first,
            topTriggerShare = if (tagged > 0) triggers.first().second.toDouble() / tagged else 0.0
        )
    }

    /** Nächster Sonntag 19:00 (heute, wenn noch nicht vorbei). */
    fun nextWeeklyReviewAt(now: LocalDateTime): LocalDateTime {
        val sunday = now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atTime(19, 0)
        return if (sunday.isAfter(now)) sunday else sunday.plusWeeks(1)
    }
}
