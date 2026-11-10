package com.alexander.vibevault.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LabelParserTest {
    private fun lines(vararg pairs: Pair<String, Int>) = pairs.map { OcrLine(it.first, it.second) }
    private fun strength(vararg texts: String) = LabelParser.findStrength(texts.map { OcrLine(it, 20) })

    // ---------- Stärke ----------

    @Test
    fun `einfache Dose mit mg pro Pouch`() {
        val result = LabelParser.parse(lines("ZYN" to 120, "COOL MINT" to 40, "6 mg/pouch" to 30), emptyList())
        assertEquals("ZYN", result.brand)
        assertEquals(6f, result.strengthMg)
        assertEquals(StrengthSource.PER_POUCH, result.strengthSource)
    }

    @Test
    fun `mg pro Pouch schlägt mg pro Gramm`() {
        assertEquals(6f, strength("NICOTINE 10.9 mg/g", "Strength 6mg per pouch"))
    }

    @Test
    fun `nur mg pro Gramm ohne Gewicht ergibt keine Stärke`() {
        assertNull(strength("Nicotine content 16 mg/g"))
        assertNull(strength("Liquid 20 mg/ml"))
    }

    @Test
    fun `verlesenes mg pro Gramm wird nicht als mg pro Pouch genommen`() {
        // Kurwa-Dose: "12 mg/g" – Schrägstrich oder g von der Texterkennung verlesen
        for (text in listOf("12 mg/9", "12mg/q", "12 mg g", "12 mg | g", "12 mgl g", "12 MG/G", "12 mg /gram")) {
            assertNull(text, strength("KURWA", "DOUBLE APPLE", text))
            assertEquals(text, 12f, LabelParser.findPerGram(listOf(OcrLine(text, 20))))
        }
    }

    @Test
    fun `nur mg pro Gramm - Wert wird für die Gewichtsauswahl gemeldet`() {
        val result = LabelParser.parse(lines("KURWA" to 120, "DOUBLE APPLE" to 50, "12 mg/g" to 20), emptyList())
        assertEquals("Kurwa", result.brand)
        assertNull(result.strengthMg)
        assertEquals(12f, result.perGramMg)
    }

    @Test
    fun `echter Scan SYX - mg direkt vor POUCHES gilt als pro Pouch`() {
        val result = LabelParser.parse(
            lines("NICOTINE" to 119, "20MG POUCHES" to 290, "SYX" to 612, "Blueberry" to 142, "SLIM" to 96),
            emptyList()
        )
        assertEquals("SYX", result.brand)
        assertEquals(20f, result.strengthMg)
        assertEquals(StrengthSource.PER_POUCH, result.strengthSource)
    }

    private val noisFront = arrayOf(
        "HICOTINE PO UCHEs" to 478, "NOIS" to 1330, "COOL STRONG EXTREME" to 675,
        "22 POUCHES I 50 MG" to 403, "MAKE soM NOIS" to 558
    )
    private val noisBack = arrayOf(
        "Nois Nicotine Pouches: Enthält nikotin CAS: 54-11-5" to 203,
        "Hersteller: HRJ Production OU NOSORLDCOn G oSHOmLD" to 252,
        "18/08/26" to 161, "Natbar bis 18/08/27" to 151,
        "NETTOGEWICHT: 11 G, TASCHEN: 22, NIKOTIN: 50 MG/G" to 231, "18+" to 110
    )

    @Test
    fun `echter Scan NOIS Rückseite - mg pro g mal Nettogewicht durch TASCHEN`() {
        val result = LabelParser.parse(lines(*noisBack), emptyList())
        assertEquals(25f, result.strengthMg)
        assertEquals(StrengthSource.COMPUTED, result.strengthSource)
        assertEquals(50f, result.perGramMg)
    }

    @Test
    fun `echter Scan NOIS Vorder- und Rückseite zusammen`() {
        val result = LabelParser.parse(lines(*noisFront, *noisBack), emptyList())
        assertEquals("Nois", result.brand)
        assertEquals(25f, result.strengthMg)
        assertEquals(StrengthSource.COMPUTED, result.strengthSource)
    }

    private val kurwaBack = arrayOf(
        "Dieses" to 113, "Produkt enthält" to 115, "Nikotin: einen Stoff, der" to 136,
        "Batch nr:" to 70, "Gesamtgewicht: 15g. (20 Stük). Ein" to 82,
        "Nikotinbeutel enthat ca. 12mg Nikotin." to 59, "Nikotinmenge pro Dose: 240mg." to 58,
        "Nikotinbeutel K#RWA COLLECTION" to 46, "Sales @eurlit.comn, kurwa.store," to 64,
        "COLLECTION" to 84, "B.B.:04.08.2028" to 93, "KCOOLV23" to 95
    )

    @Test
    fun `echter Scan Kurwa Unterseite - Satz pro Beutel gilt als pro Pouch`() {
        val result = LabelParser.parse(lines(*kurwaBack), emptyList())
        assertEquals("Kurwa", result.brand)
        assertEquals(12f, result.strengthMg)
        assertEquals(StrengthSource.PER_POUCH, result.strengthSource)
    }

    @Test
    fun `mg pro Dose geteilt durch Anzahl`() {
        val result = LabelParser.parse(lines("Gesamtgewicht: 15g. (20 Stük)" to 50, "Nikotinmenge pro Dose: 240mg." to 50), emptyList())
        assertEquals(12f, result.strengthMg)
        assertEquals(StrengthSource.COMPUTED, result.strengthSource)
    }

    @Test
    fun `mg vor Pouch-Anzahl bleibt mg pro Pouch`() {
        assertEquals(6f, strength("6 mg 20 pouches"))
        assertEquals(9f, strength("9 mg gum flavour"))
    }

    @Test
    fun `mg pro Gramm mal Pouch-Gewicht wird berechnet`() {
        val r = LabelParser.findStrengthDetailed(listOf(OcrLine("Nicotine 16 mg/g", 20), OcrLine("0,7 g/pouch", 20)))!!
        assertEquals(11.2f, r.first, 0.01f)
        assertEquals(StrengthSource.COMPUTED, r.second)
        assertEquals("16 mg/g × 0,7 g", r.third)
    }

    @Test
    fun `mg pro Gramm mal Nettogewicht durch Anzahl`() {
        // 16 g / 20 Pouches = 0,8 g; 20 mg/g × 0,8 g = 16 mg
        assertEquals(16f, strength("20 mg/g", "NET WT 16g", "20 POUCHES"))
    }

    @Test
    fun `Komma, Großschreibung und fehlendes Leerzeichen`() {
        assertEquals(1.5f, strength("1,5 mg"))
        assertEquals(11f, strength("11MG"))
    }

    @Test
    fun `unplausible Werte und Teilzahlen werden verworfen`() {
        assertNull(strength("Inhalt 500 mg"))
        assertEquals(8f, strength("120 mg", "8 mg"))
    }

    @Test
    fun `typische OCR-Lesefehler werden korrigiert`() {
        assertEquals(6f, strength("6 rng/pouch"))
        assertEquals(10f, strength("1O mg/pouch"))
        assertEquals(11f, strength("l1 mg"))
        assertEquals(9f, strength("9mq per pouch"))
    }

    @Test
    fun `Zahl und mg auf getrennten Zeilen`() {
        assertEquals(6f, strength("6", "MG/POUCH"))
    }

    // ---------- Marke ----------

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
    fun `verbreitete Marke wird auch ohne frühere Einträge erkannt`() {
        // Sorte ist größer gedruckt als die Marke
        val text = lines("FREEZE" to 140, "ICE COOL" to 100, "velo" to 60, "10 mg/pouch" to 20)
        assertEquals("VELO", LabelParser.findBrand(text, emptyList()))
    }

    @Test
    fun `Marke mit Lesefehler wird trotzdem erkannt`() {
        assertEquals("Nordic Spirit", LabelParser.findBrand(lines("NORDlC SP1RIT" to 90), emptyList()))
        assertEquals("Siberia", LabelParser.findBrand(lines("SIBERlA" to 90), emptyList()))
    }

    @Test
    fun `Marke über zwei Zeilen`() {
        assertEquals("White Fox", LabelParser.findBrand(lines("WHITE" to 90, "FOX" to 90, "Full Charge" to 40), emptyList()))
    }

    @Test
    fun `kurze Marken nur bei exaktem Treffer`() {
        // "ZEN" darf nicht zu "ZYN" werden
        assertEquals("Zen", LabelParser.findBrand(lines("Zen" to 90), emptyList()))
    }

    @Test
    fun `unbekannte Marke - Sorten und Stärkewörter werden übersprungen`() {
        val text = lines("PEPPERMINT" to 150, "Extra Strong" to 120, "Mondhase" to 90, "Nicotine pouches" to 50)
        assertEquals("Mondhase", LabelParser.findBrand(text, emptyList()))
    }

    @Test
    fun `Warnhinweise und Zahlen werden nicht als Marke erkannt`() {
        assertNull(LabelParser.findBrand(lines("Nicotine pouches" to 80, "6 mg" to 90), emptyList()))
    }
}
