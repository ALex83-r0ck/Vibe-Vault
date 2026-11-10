package com.alexander.vibevault.domain

import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.exp
import kotlin.math.ln

/**
 * Parameter des pharmakokinetischen Modells für Nikotin-Pouches/Snus.
 *
 * Literaturwerte (Mittelwerte, individuelle Streuung ist groß – v. a. durch CYP2A6-Genetik):
 * - Halbwertszeit Nikotin ≈ 2 h, Verteilungsvolumen ≈ 2,6 L/kg
 * - Halbwertszeit Cotinin ≈ 16 h (12–20 h), Verteilungsvolumen ≈ 0,7–1,0 L/kg
 * - 70–80 % des aufgenommenen Nikotins werden zu Cotinin verstoffwechselt
 *   (Benowitz NL et al., Handb Exp Pharmacol 2009;192:29–60)
 * - Aus Pouches werden je nach Produkt ca. 24–59 % des Nikotingehalts extrahiert
 *   (u. a. Lunell E et al., Nicotine Tob Res 2020;22(10):1757–1763); ein Teil davon wird verschluckt
 *   und unterliegt dem First-Pass-Effekt. Daher ist der aufgenommene Anteil einstellbar.
 * - Aufnahmerate 3/h ergibt ein Maximum nach ≈ 50 min, passend zu typischen Pouch-Nutzungsdauern.
 */
data class PkParameters(
    val bodyWeightKg: Double = 75.0,
    val absorbedFraction: Double = 0.30,
    val nicotineHalfLifeH: Double = 2.0,
    val cotinineHalfLifeH: Double = 16.0,
    val absorptionRatePerH: Double = 3.0,
    val nicotineVdLPerKg: Double = 2.6,
    val cotinineVdLPerKg: Double = 0.85,
    val fractionToCotinine: Double = 0.75
)

data class Dose(val time: LocalDateTime, val contentMg: Double)

/**
 * Ein-Kompartiment-Modell mit Aufnahme erster Ordnung (Bateman-Funktion) für Nikotin
 * und daraus gebildetem Cotinin (Kette Mundraum → Nikotin → Cotinin → Ausscheidung).
 * Mehrere Pouches werden linear überlagert (Superposition).
 */
class NicotineModel(private val p: PkParameters) {
    private val ka = p.absorptionRatePerH
    private val ke = ln(2.0) / p.nicotineHalfLifeH
    private val kc = ln(2.0) / p.cotinineHalfLifeH

    init {
        require(ka != ke && ke != kc && ka != kc) { "Raten müssen verschieden sein" }
    }

    /** Nikotinmenge im Körper in mg. */
    fun nicotineMg(doses: List<Dose>, at: LocalDateTime): Double = doses.sumOf { dose ->
        val t = hoursBetween(dose.time, at)
        if (t <= 0.0) 0.0 else absorbed(dose) * ka / (ka - ke) * (exp(-ke * t) - exp(-ka * t))
    }

    /** Cotininmenge im Körper in mg (mit Molmassen-Umrechnung Nikotin → Cotinin). */
    fun cotinineMg(doses: List<Dose>, at: LocalDateTime): Double = doses.sumOf { dose ->
        val t = hoursBetween(dose.time, at)
        if (t <= 0.0) {
            0.0
        } else {
            val chain = exp(-ka * t) / ((ke - ka) * (kc - ka)) +
                exp(-ke * t) / ((ka - ke) * (kc - ke)) +
                exp(-kc * t) / ((ka - kc) * (ke - kc))
            p.fractionToCotinine * absorbed(dose) * ka * ke * chain * COTININE_PER_NICOTINE_MASS
        }
    }

    /** mg im Körper → ng/ml im Blut. 1 mg/L = 1000 ng/ml. */
    fun nicotineNgPerMl(amountMg: Double): Double = amountMg / (p.nicotineVdLPerKg * p.bodyWeightKg) * 1000.0

    fun cotinineNgPerMl(amountMg: Double): Double = amountMg / (p.cotinineVdLPerKg * p.bodyWeightKg) * 1000.0

    fun nicotineNgPerMl(doses: List<Dose>, at: LocalDateTime) = nicotineNgPerMl(nicotineMg(doses, at))

    fun cotinineNgPerMl(doses: List<Dose>, at: LocalDateTime) = cotinineNgPerMl(cotinineMg(doses, at))

    /**
     * Erster Zeitpunkt ab [from], ab dem die Konzentration dauerhaft unter [thresholdNgPerMl] bleibt.
     * null, wenn sie schon jetzt und im gesamten Horizont darunter liegt.
     */
    fun timeWhenBelow(
        from: LocalDateTime,
        thresholdNgPerMl: Double,
        horizon: Duration,
        step: Duration,
        concentration: (LocalDateTime) -> Double
    ): LocalDateTime? {
        var lastAbove: LocalDateTime? = null
        var t = from
        val end = from.plus(horizon)
        while (!t.isAfter(end)) {
            if (concentration(t) >= thresholdNgPerMl) lastAbove = t
            t = t.plus(step)
        }
        return lastAbove?.plus(step)
    }

    private fun absorbed(dose: Dose) = dose.contentMg * p.absorbedFraction

    private fun hoursBetween(from: LocalDateTime, to: LocalDateTime) =
        Duration.between(from, to).toMillis() / 3_600_000.0

    companion object {
        /** Molmasse Cotinin (176,21 g/mol) / Nikotin (162,23 g/mol). */
        const val COTININE_PER_NICOTINE_MASS = 176.21 / 162.23

        /** Unterhalb davon gilt man praktisch als nikotinfrei. */
        const val NICOTINE_FREE_NG_PER_ML = 1.0

        /** Serum-Cotinin-Grenzwert Raucher/Nichtraucher (Benowitz NL et al., Am J Epidemiol 2009;169(2):236–248). */
        const val COTININE_CUTOFF_NG_PER_ML = 3.0

        /** Ältere Dosen tragen praktisch nichts mehr bei (Cotinin nach 14 Tagen < 0,001 %). */
        val RELEVANT_HISTORY: Duration = Duration.ofDays(14)
    }
}
