package com.alexander.vibevault.domain

import kotlin.math.abs
import kotlin.math.roundToInt

/** Eine von der Texterkennung gefundene Zeile mit ihrer Höhe in Pixeln (größer = auffälliger). */
data class OcrLine(val text: String, val heightPx: Int)

enum class StrengthSource {
    /** Steht direkt als "mg/Pouch" o. Ä. auf der Dose. */
    PER_POUCH,
    /** Aus mg/g × Pouch-Gewicht berechnet. */
    COMPUTED,
    /** Eine mg-Angabe ohne Bezug – unsicher. */
    UNQUALIFIED
}

data class LabelScanResult(
    val brand: String?,
    val strengthMg: Float?,
    val strengthSource: StrengthSource? = null,
    /** Für die Anzeige "so wurde gerechnet", z. B. "16 mg/g × 0,7 g". */
    val computation: String? = null,
    /** Gefundene Angabe pro Gramm (z. B. 12 für "12 mg/g") – auch wenn kein Pouch-Gewicht gefunden wurde. */
    val perGramMg: Float? = null
)

/**
 * Wertet erkannten Text einer Pouch-Dose aus.
 *
 * Stärke, in dieser Reihenfolge:
 * 1. "X mg/pouch", "X mg per pouch", "X mg/Beutel" …
 * 2. "X mg/g" × Pouch-Gewicht ("0,7 g/pouch") oder × Nettogewicht / Anzahl ("16 g", "20 pouches")
 * 3. sonst eine mg-Angabe ohne Bezug, aber nie mg/g oder mg/ml
 *
 * Marke: bekannte Marken (eigene frühere Einträge + Liste verbreiteter Marken, tolerant gegen
 * Lesefehler), sonst die größte Textzeile, die keine Sorte, Stärke oder Produktbeschreibung ist.
 */
object LabelParser {

    // ---------- Stärke ----------

    private const val NUM = """(\d{1,3}(?:[.,]\d{1,2})?)"""
    private val unitWords = "pouch|pouches|portion|portions|portionen|beutel|sachet|sachets|piece|stück|stueck|stuck|stük|stk|pcs|unit"

    /** "6 mg/pouch", "6 mg per pouch" – und "20MG POUCHES" (Einheit direkt hinter mg). */
    private val mgPerUnit = Regex("""(?<![\d.,])$NUM\s*mg\s*(?:(?:/|per|pro|a|each)\s*)?(?:$unitWords)\b""", RegexOption.IGNORE_CASE)
    /**
     * "pro Gramm" hinter "mg", tolerant gegen Lesefehler: Schrägstrich als "|", "\", "l", "I", "1" gelesen,
     * "g" als "9" oder "q" – oder der Schrägstrich ganz verschluckt ("mg g").
     */
    private const val PER_GRAM =
        """(?:\s*(?:/|\||\\|[lI1]|per|pro)\s*(?:gramm|gram|gr|g|9|q)|\s+(?:gramm|gram|gr|g))(?![\p{L}\d])"""
    private const val PER_ML = """\s*(?:/|per|pro)\s*ml\b"""
    private val mgPerGram = Regex("""(?<![\d.,])$NUM\s*mg$PER_GRAM""", RegexOption.IGNORE_CASE)
    private val mgAny = Regex("""(?<![\d.,])$NUM\s*mg\b(?!$PER_GRAM|$PER_ML)""", RegexOption.IGNORE_CASE)
    private val gramPerUnit = Regex("""(?<![\d.,])$NUM\s*g\s*(?:/|per|pro)\s*(?:$unitWords)\b""", RegexOption.IGNORE_CASE)
    private val unitWeight = Regex("""(?:pouch|portion|beutel)\w*\s*(?:weight|gewicht)\s*[:=]?\s*$NUM\s*g\b""", RegexOption.IGNORE_CASE)
    private val netWeight = Regex("""(?:net\s*(?:wt|weight)?|netto\w*|gesamtgewicht|inhalt|content|füllmenge)\s*[:.]?\s*$NUM\s*g\b(?!\s*/)""", RegexOption.IGNORE_CASE)
    /** Anzahl hinter dem Wort: "TASCHEN: 22", "Pouches: 20", "Anzahl 24". */
    private val countLabel = Regex("""(?:taschen|pouches|portionen|portions|beutel|stück|stk|anzahl|quantity|count)\s*[:=]?\s*(\d{1,2})(?!\d|[.,]\d)""", RegexOption.IGNORE_CASE)
    /** "Ein Nikotinbeutel enthält ca. 12mg Nikotin", "each pouch contains 6 mg" (OCR: "enthat"). */
    private val mgInUnit = Regex(
        """(?:pouch|portion|beutel|sachet)\w*\s+(?:enth\w*|contain\w*|has)\s*(?:ca\.?|approx\.?|about|etwa|rund|~)?\s*$NUM\s*mg\b""",
        RegexOption.IGNORE_CASE
    )
    /** "Nikotinmenge pro Dose: 240mg", "per can 240 mg". */
    private val mgPerCan = Regex(
        """(?:pro|per|je)\s*(?:dose|can|box|packung|tin)\s*[:=]?\s*(\d{2,4}(?:[.,]\d{1,2})?)\s*mg\b""",
        RegexOption.IGNORE_CASE
    )
    private val unitCount = Regex("""(?<![\d.,])(\d{1,2})\s*(?:x\s*)?(?:$unitWords)\b""", RegexOption.IGNORE_CASE)

    fun findStrength(lines: List<OcrLine>): Float? = findStrengthDetailed(lines)?.first

    /** Erste plausible "X mg/g"-Angabe. */
    fun findPerGram(lines: List<OcrLine>): Float? =
        mgPerGram.findAll(normalize(lines.joinToString(" ") { it.text }))
            .mapNotNull { it.number(1) }.firstOrNull { it in 1f..150f }

    /** Stärke + Quelle + ggf. Rechenweg. */
    fun findStrengthDetailed(lines: List<OcrLine>): Triple<Float, StrengthSource, String?>? {
        val text = normalize(lines.joinToString(" ") { it.text })

        (mgPerUnit.findAll(text) + mgInUnit.findAll(text)).mapNotNull { it.number(1) }.firstOrNull { it in 0.5f..50f }
            ?.let { return Triple(it, StrengthSource.PER_POUCH, null) }

        val count = countPouches(text)
        val perCan = mgPerCan.findAll(text).mapNotNull { it.number(1) }.firstOrNull()
        if (perCan != null && count != null) {
            val mg = (perCan / count * 10).roundToInt() / 10f
            if (mg in 0.5f..50f) return Triple(mg, StrengthSource.COMPUTED, "${fmt(perCan)} mg pro Dose ÷ ${fmt(count)} Pouches")
        }

        val perGram = mgPerGram.findAll(text).mapNotNull { it.number(1) }.firstOrNull { it in 1f..150f }
        if (perGram != null) {
            val weight = gramPerUnit.find(text)?.number(1)
                ?: unitWeight.find(text)?.number(1)
                ?: run {
                    val net = netWeight.find(text)?.number(1)
                    if (net != null && count != null) net / count else null
                }
            if (weight != null && weight in 0.2f..2f) {
                val mg = (perGram * weight * 10).roundToInt() / 10f
                if (mg in 0.5f..50f) {
                    return Triple(mg, StrengthSource.COMPUTED, "${fmt(perGram)} mg/g × ${fmt(weight)} g")
                }
            }
        }

        return mgAny.findAll(text).mapNotNull { it.number(1) }.firstOrNull { it in 0.5f..50f }
            ?.let { Triple(it, StrengthSource.UNQUALIFIED, null) }
    }

    /** Anzahl Pouches in der Dose: "20 pouches", "(20 Stück)", "TASCHEN: 22". */
    private fun countPouches(text: String): Float? =
        (unitCount.findAll(text) + countLabel.findAll(text)).mapNotNull { it.number(1) }.firstOrNull { it in 5f..50f }

    /** Typische Lesefehler der Texterkennung rund um mg-Angaben korrigieren. */
    internal fun normalize(raw: String): String {
        var text = raw
            .replace('\n', ' ')
            .replace(Regex("""(?<=[\dOoIl|])\s*(?:rn[gq9]|m[q9]|rng|nng)\b""", RegexOption.IGNORE_CASE), " mg")
        // Ziffern-Verwechslungen in Zahlen direkt vor "mg" (O→0, I/l/|→1, S→5), nur wenn mind. eine echte Ziffer dabei ist
        text = Regex("""(?<![\w.,])([0-9OoIl|S]{1,2}(?:[.,][0-9OoIl|S]{1,2})?)(?=\s*mg\b)""", RegexOption.IGNORE_CASE)
            .replace(text) { m ->
                val token = m.value
                if (token.any { it.isDigit() }) {
                    token.map { c ->
                        when (c) {
                            'O', 'o' -> '0'
                            'I', 'l', '|' -> '1'
                            'S' -> '5'
                            else -> c
                        }
                    }.joinToString("")
                } else token
            }
        return text
    }

    // ---------- Marke ----------

    /** Verbreitete Marken für Nikotin-Pouches und Snus (nur zum Abgleich des erkannten Texts). */
    val COMMON_BRANDS = listOf(
        "ZYN", "VELO", "Nordic Spirit", "Killa", "Pablo", "Siberia", "XQS", "LYFT", "White Fox", "LOOP",
        "Skruf", "Zone X", "FUMI", "Klint", "Thunder", "General", "Göteborgs Rapé", "Odens", "Kurwa",
        "Cuba", "Iceberg", "Volt", "Helwit", "Juice Head", "Kelly White", "Grant", "Swave", "Garant",
        "Ettan", "Knox", "Lundgrens", "Zeus", "Nois", "Fedrs", "Respect", "Shiro", "Taboo", "Aroma King", "SYX"
    )

    private val nonBrandWords = setOf(
        // Produkt
        "nicotine", "nikotin", "pouch", "pouches", "portion", "portions", "portionen", "snus", "white", "all",
        "tobacco", "tabak", "free", "tabakfrei", "product", "produkt", "contains", "enthält", "warning", "warnung",
        "dieses", "this", "your", "health", "gesundheit", "addictive", "abhängig", "net", "wt", "content",
        // Stärke
        "strong", "extra", "ultra", "max", "slim", "mini", "light", "medium", "regular", "original", "strength",
        "stärke", "super", "hard", "soft", "normal", "large", "x-strong", "xstrong",
        // Sorten
        "mint", "menthol", "berry", "berries", "ice", "icy", "frost", "frosted", "cool", "fresh", "freeze", "citrus",
        "lemon", "lime", "cherry", "mango", "peach", "apple", "melon", "watermelon", "cola", "coffee", "espresso",
        "wintergreen", "spearmint", "peppermint", "eucalyptus", "licorice", "lakritz", "vanilla", "grape",
        "strawberry", "blueberry", "raspberry", "tropical", "elderflower", "holunder", "cinnamon", "ginger",
        "blackcurrant", "cassis", "passion", "orange", "pineapple", "bubble", "gum", "fruit", "flavour", "flavor",
        "smooth", "polar", "arctic", "glacier", "chill", "spicy", "sweet", "kiwi", "banana", "pear"
    )

    fun parse(lines: List<OcrLine>, knownBrands: List<String>): LabelScanResult {
        val strength = findStrengthDetailed(lines)
        return LabelScanResult(
            brand = findBrand(lines, knownBrands),
            strengthMg = strength?.first,
            strengthSource = strength?.second,
            computation = strength?.third,
            perGramMg = findPerGram(lines)
        )
    }

    fun findBrand(lines: List<OcrLine>, knownBrands: List<String>): String? {
        val candidates = (knownBrands.filter { it.isNotBlank() } + COMMON_BRANDS)
            .distinctBy { key(it) }
            .filter { key(it).length >= 3 }
            .sortedByDescending { key(it).length } // "Velo Max" vor "Velo"

        // Ganzer Text plus einzelne Zeilen und benachbarte Zeilenpaare (Markennamen über zwei Zeilen)
        val windows = buildList {
            lines.forEachIndexed { i, line ->
                add(line.text)
                if (i + 1 < lines.size) add(line.text + " " + lines[i + 1].text)
                addAll(line.text.split(Regex("\\s+")))
            }
        }.map(::key).filter { it.isNotEmpty() }
        val fullKey = key(lines.joinToString(" ") { it.text })

        candidates.firstOrNull { brand ->
            val b = key(brand)
            fullKey.contains(b) || windows.any { w -> fuzzyEquals(w, b) }
        }?.let { return it }

        return lines
            .map { it.copy(text = it.text.trim()) }
            .filter { line ->
                val t = line.text
                val words = t.lowercase().split(Regex("[^\\p{L}!-]+")).filter { it.isNotEmpty() }
                t.length in 2..24 &&
                    t.none { it.isDigit() } &&
                    t.count { it.isLetter() } >= 2 &&
                    words.isNotEmpty() &&
                    words.none { it in nonBrandWords }
            }
            .maxByOrNull { it.heightPx }
            ?.text
            ?.let(::normalizeCase)
    }

    /** Vergleichsschlüssel: nur Buchstaben/Ziffern, klein, Umlaute vereinfacht. */
    private fun key(s: String): String = s.lowercase()
        .replace("ä", "a").replace("ö", "o").replace("ü", "u").replace("é", "e")
        .filter { it.isLetterOrDigit() }

    /** Erlaubt ~1 Lesefehler pro 5 Zeichen, bei kurzen Marken (≤ 4) nur exakte Treffer. */
    private fun fuzzyEquals(candidate: String, brand: String): Boolean {
        if (brand.length <= 4) return candidate == brand
        if (abs(candidate.length - brand.length) > 2) return false
        return levenshtein(candidate, brand) <= brand.length / 5
    }

    private fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            prev = cur
        }
        return prev[b.length]
    }

    /** "NORDIC SPIRIT" → "Nordic Spirit"; kurze Großschreibung wie "ZYN" bleibt erhalten. */
    private fun normalizeCase(text: String): String =
        if (text.length > 4 && text == text.uppercase()) {
            text.lowercase().split(" ").joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
        } else {
            text
        }

    private fun MatchResult.number(group: Int): Float? = groupValues[group].replace(',', '.').toFloatOrNull()

    private fun fmt(v: Float): String =
        if (v == v.roundToInt().toFloat()) v.roundToInt().toString() else v.toString().replace('.', ',')
}
