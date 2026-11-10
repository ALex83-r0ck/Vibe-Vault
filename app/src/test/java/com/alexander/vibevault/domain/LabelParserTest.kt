package com.alexander.vibevault.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LabelParserTest {
    private fun lines(vararg pairs: Pair<String, Int>) = pairs.map { OcrLine(it.first, it.second) }

    @Test
    fun `einfache Dose mit mg pro Pouch`() {
        val result = LabelParser.parse(
            lines("ZYN" to 120, "COOL MINT" to 40, "6 mg/pouch" to 30),
            knownBrands = emptyList()
        )
        assertEquals("ZYN", result.brand)
        assertEquals(6f, result.strengthMg)
    }

    @Test
    fun `mg pro Gramm wird ignoriert und Angabe pro Pouch bevorzugt`() {
        val text = lines("VELO" to 100, "NICOTINE 10.9 mg/g" to 20, "Strength 6mg per pouch" to 20)
        assertEquals(6f, LabelParser.findStrength(text))
    }

    @Test
    fun `nur mg pro Gramm ergibt keine Stärke`() {
        assertNull(LabelParser.findStrength(lines("Nicotine content 16 mg/g" to 20)))
        assertNull(LabelParser.findStrength(lines("Liquid 20 mg/ml" to 20)))
    }

    @Test
    fun `Komma, Großschreibung und fehlendes Leerzeichen`() {
        assertEquals(1.5f, LabelParser.findStrength(lines("1,5 mg" to 20)))
        assertEquals(11f, LabelParser.findStrength(lines("11MG" to 20)))
    }

    @Test
    fun `unplausible Werte werden verworfen`() {
        assertNull(LabelParser.findStrength(lines("Inhalt 500 mg" to 20)))
        assertEquals(8f, LabelParser.findStrength(lines("120 mg" to 20, "8 mg" to 20)))
    }

    @Test
    fun `bekannte Marke aus früheren Einträgen hat Vorrang`() {
        val text = lines("WARNUNG: Dieses Produkt enthält Nikotin" to 60, "nordic spirit" to 30)
        assertEquals("Nordic Spirit", LabelParser.findBrand(text, listOf("Velo", "Nordic Spirit")))
    }

    @Test
    fun `längere bekannte Marke schlägt kürzere`() {
        assertEquals("Velo Max", LabelParser.findBrand(lines("VELO MAX" to 50), listOf("Velo", "Velo Max")))
    }

    @Test
    fun `ohne bekannte Marke gewinnt die größte reine Textzeile`() {
        val text = lines("NORDIC SPIRIT" to 90, "Elderflower" to 40, "Nicotine pouches" to 50, "9 mg" to 30)
        assertEquals("Nordic Spirit", LabelParser.findBrand(text, emptyList()))
    }

    @Test
    fun `Warnhinweise und Zahlen werden nicht als Marke erkannt`() {
        assertNull(LabelParser.findBrand(lines("Nicotine pouches" to 80, "6 mg" to 90), emptyList()))
    }
}
