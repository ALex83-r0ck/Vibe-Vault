package com.alexander.vibevault.domain

import com.alexander.vibevault.data.model.ConsumptionEntry
import java.time.Duration
import java.time.LocalDateTime

/** Zustand "jetzt" laut Modell. Zeitpunkte sind null, wenn der Schwellwert bereits unterschritten ist. */
data class BodySnapshot(
    val nicotineMg: Double = 0.0,
    val nicotineNgPerMl: Double = 0.0,
    val nicotineFreeAt: LocalDateTime? = null,
    val cotinineNgPerMl: Double = 0.0,
    val cotinineBelowCutoffAt: LocalDateTime? = null
)

data class CurvePoint(val time: LocalDateTime, val nicotineNgPerMl: Double)

fun List<ConsumptionEntry>.toDoses(now: LocalDateTime): List<Dose> {
    val oldest = now.minus(NicotineModel.RELEVANT_HISTORY)
    return asSequence()
        .filter { it.timestamp.isAfter(oldest) && !it.timestamp.isAfter(now) }
        .map { Dose(it.timestamp, it.nicotineStrength.toDouble()) }
        .toList()
}

fun NicotineModel.snapshot(doses: List<Dose>, now: LocalDateTime): BodySnapshot {
    val nicotineMg = nicotineMg(doses, now)
    return BodySnapshot(
        nicotineMg = nicotineMg,
        nicotineNgPerMl = nicotineNgPerMl(nicotineMg),
        nicotineFreeAt = timeWhenBelow(
            from = now,
            thresholdNgPerMl = NicotineModel.NICOTINE_FREE_NG_PER_ML,
            horizon = Duration.ofHours(48),
            step = Duration.ofMinutes(5)
        ) { nicotineNgPerMl(doses, it) },
        cotinineNgPerMl = cotinineNgPerMl(doses, now),
        cotinineBelowCutoffAt = timeWhenBelow(
            from = now,
            thresholdNgPerMl = NicotineModel.COTININE_CUTOFF_NG_PER_ML,
            horizon = Duration.ofDays(14),
            step = Duration.ofMinutes(30)
        ) { cotinineNgPerMl(doses, it) }
    )
}

/** Nikotin-Blutspiegel von [hoursBack] Stunden vor bis [hoursAhead] Stunden nach [now]. */
fun NicotineModel.curve(doses: List<Dose>, now: LocalDateTime, hoursBack: Long = 12, hoursAhead: Long = 12): List<CurvePoint> {
    val start = now.minusHours(hoursBack)
    val steps = (hoursBack + hoursAhead) * 4 // alle 15 min
    return (0..steps).map { i ->
        val t = start.plusMinutes(i * 15)
        CurvePoint(t, nicotineNgPerMl(doses, t))
    }
}
