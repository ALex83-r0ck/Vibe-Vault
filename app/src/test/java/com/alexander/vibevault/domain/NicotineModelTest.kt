package com.alexander.vibevault.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime

class NicotineModelTest {
    private val params = PkParameters(bodyWeightKg = 75.0, absorbedFraction = 0.30)
    private val model = NicotineModel(params)
    private val t0 = LocalDateTime.of(2026, 11, 10, 8, 0)
    private val pouch6 = listOf(Dose(t0, 6.0))

    private fun peakTime(doses: List<Dose>): Long =
        (0L..600L).maxBy { model.nicotineMg(doses, t0.plusMinutes(it)) }

    @Test
    fun `vor der Einnahme ist nichts im Körper`() {
        assertEquals(0.0, model.nicotineMg(pouch6, t0.minusMinutes(1)), 0.0)
        assertEquals(0.0, model.nicotineMg(pouch6, t0), 0.0)
        assertEquals(0.0, model.cotinineMg(pouch6, t0), 0.0)
    }

    @Test
    fun `Maximum nach etwa 50 Minuten`() {
        val peak = peakTime(pouch6)
        assertTrue("Peak bei $peak min", peak in 45..55)
    }

    @Test
    fun `nach der Aufnahme halbiert sich Nikotin alle 2 Stunden`() {
        val at10h = model.nicotineMg(pouch6, t0.plusHours(10))
        val at12h = model.nicotineMg(pouch6, t0.plusHours(12))
        assertEquals(0.5, at12h / at10h, 0.001)
    }

    @Test
    fun `Umrechnung in Blutspiegel`() {
        // 75 kg * 2,6 L/kg = 195 L; 1,95 mg / 195 L = 0,01 mg/L = 10 ng/ml
        assertEquals(10.0, model.nicotineNgPerMl(1.95), 1e-9)
    }

    @Test
    fun `Spitzenwert eines 6-mg-Pouches liegt im einstelligen ng-ml-Bereich`() {
        val peak = model.nicotineNgPerMl(pouch6, t0.plusMinutes(peakTime(pouch6)))
        assertEquals(6.96, peak, 0.1)
    }

    @Test
    fun `mehrere Pouches addieren sich linear`() {
        val two = listOf(Dose(t0, 6.0), Dose(t0.plusHours(1), 4.0))
        val at = t0.plusHours(3)
        val expected = model.nicotineMg(listOf(two[0]), at) + model.nicotineMg(listOf(two[1]), at)
        assertEquals(expected, model.nicotineMg(two, at), 1e-12)
    }

    @Test
    fun `Cotinin erreicht sein Maximum deutlich später als Nikotin und fällt dann langsam`() {
        val cotPeak = (0L..48L).maxBy { model.cotinineMg(pouch6, t0.plusHours(it)) }
        assertTrue("Cotinin-Peak bei $cotPeak h", cotPeak in 5L..10L)
        val at24 = model.cotinineMg(pouch6, t0.plusHours(24))
        val at40 = model.cotinineMg(pouch6, t0.plusHours(40))
        assertEquals(0.5, at40 / at24, 0.01) // Halbwertszeit 16 h
    }

    @Test
    fun `gebildete Cotininmenge entspricht dem verstoffwechselten Anteil`() {
        // Fläche: integral(kc * Cot dt) = gesamt ausgeschiedenes Cotinin = fc * F * D * Massenfaktor
        var excreted = 0.0
        val kc = kotlin.math.ln(2.0) / params.cotinineHalfLifeH
        for (m in 0 until 60 * 24 * 20) excreted += kc * model.cotinineMg(pouch6, t0.plusMinutes(m.toLong())) / 60.0
        val expected = 0.75 * 0.30 * 6.0 * NicotineModel.COTININE_PER_NICOTINE_MASS
        assertEquals(expected, excreted, expected * 0.01)
    }

    @Test
    fun `nikotinfrei etwa 6 Stunden 45 Minuten nach einem 6-mg-Pouch`() {
        val free = model.timeWhenBelow(t0, NicotineModel.NICOTINE_FREE_NG_PER_ML, Duration.ofHours(48), Duration.ofMinutes(5)) {
            model.nicotineNgPerMl(pouch6, it)
        }!!
        val minutes = Duration.between(t0, free).toMinutes()
        assertTrue("nikotinfrei nach $minutes min", minutes in 395L..420L)
    }

    @Test
    fun `bereits nikotinfrei ergibt null`() {
        val later = t0.plusDays(2)
        assertNull(model.timeWhenBelow(later, 1.0, Duration.ofHours(48), Duration.ofMinutes(5)) {
            model.nicotineNgPerMl(pouch6, it)
        })
    }

    @Test
    fun `Spiegel steigt während der Aufnahme noch an - Zeitpunkt liegt nach dem Peak`() {
        // Direkt nach der Einnahme ist der Spiegel noch niedrig, steigt aber über die Schwelle
        val free = model.timeWhenBelow(t0.plusMinutes(1), 1.0, Duration.ofHours(48), Duration.ofMinutes(5)) {
            model.nicotineNgPerMl(pouch6, it)
        }
        assertTrue(free!!.isAfter(t0.plusHours(6)))
    }
}
