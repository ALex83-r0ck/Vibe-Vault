package com.alexander.vibevault.domain

/** Eine von der Texterkennung gefundene Zeile mit ihrer Höhe in Pixeln (größer = auffälliger). */
data class OcrLine(val text: String, val heightPx: Int)

data class LabelScanResult(val brand: String?, val strengthMg: Float?)

/**
 * Wertet erkannten Text einer Pouch-Dose aus.
 * - Stärke: bevorzugt Angaben "pro Pouch/Beutel/Portion"; "mg/g" und "mg/ml" sind Gehalt pro Gramm
 *   bzw. Milliliter und werden ignoriert.
 * - Marke: zuerst bekannte Marken aus früheren Einträgen, sonst die größte reine Textzeile.
 */
object LabelParser {
    private val mgRegex = Regex("""(?<![\d.,])(\d{1,2}(?:[.,]\d{1,2})?)\s*mg\b\s*(/\s*\w+|per\s+\w+|pro\s+\w+)?""", RegexOption.IGNORE_CASE)
    private val perUnitWords = listOf("pouch", "portion", "beutel", "sachet", "pcs", "stk", "piece", "unit")
    private val perMassOrVolume = Regex("""^(/\s*|per\s+|pro\s+)(g|gram|gramm|ml)$""", RegexOption.IGNORE_CASE)

    private val stopWords = setOf(
        "nicotine", "nikotin", "pouch", "pouches", "mg", "strong", "extra", "slim", "mini", "original",
        "warning", "warnung", "product", "produkt", "contains", "enthält", "white", "portion", "portions",
        "tobacco", "free", "tabakfrei", "snus", "mint", "cool", "strength", "stärke", "regular", "large"
    )

    fun parse(lines: List<OcrLine>, knownBrands: List<String>): LabelScanResult =
        LabelScanResult(brand = findBrand(lines, knownBrands), strengthMg = findStrength(lines))

    fun findStrength(lines: List<OcrLine>): Float? {
        val text = lines.joinToString(" ") { it.text }
        val candidates = mgRegex.findAll(text).mapNotNull { match ->
            val value = match.groupValues[1].replace(',', '.').toFloatOrNull() ?: return@mapNotNull null
            val suffix = match.groupValues[2].trim()
            when {
                value <= 0f || value > 50f -> null
                perMassOrVolume.matches(suffix) -> null
                else -> value to perUnitWords.any { suffix.contains(it, ignoreCase = true) }
            }
        }.toList()
        return (candidates.firstOrNull { it.second } ?: candidates.firstOrNull())?.first
    }

    fun findBrand(lines: List<OcrLine>, knownBrands: List<String>): String? {
        val fullText = lines.joinToString(" ") { it.text }
        knownBrands
            .filter { it.isNotBlank() }
            .sortedByDescending { it.length } // "Velo Max" vor "Velo"
            .firstOrNull { fullText.contains(it, ignoreCase = true) }
            ?.let { return it }

        return lines
            .map { it.copy(text = it.text.trim()) }
            .filter { line ->
                val t = line.text
                t.length in 3..24 &&
                    t.none { it.isDigit() } &&
                    t.count { it.isLetter() } >= 3 &&
                    t.split(Regex("\\s+")).none { it.lowercase() in stopWords }
            }
            .maxByOrNull { it.heightPx }
            ?.text
            ?.let(::normalizeCase)
    }

    /** "NORDIC SPIRIT" → "Nordic Spirit"; kurze Großschreibung wie "ZYN" bleibt erhalten. */
    private fun normalizeCase(text: String): String =
        if (text.length > 4 && text == text.uppercase()) {
            text.lowercase().split(" ").joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
        } else {
            text
        }
}
