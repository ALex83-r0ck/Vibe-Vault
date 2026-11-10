package com.alexander.vibevault.ui

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm", Locale.GERMANY)
private val mgFormat = DecimalFormat("0.##", DecimalFormatSymbols(Locale.GERMANY))

fun LocalDateTime.formatForDisplay(): String = format(dateTimeFormatter)

/** "1,5" – ohne Einheit. */
fun formatMgValue(value: Float): String = mgFormat.format(value)

/** "1,5 mg" */
fun formatMg(value: Float): String = "${formatMgValue(value)} mg"

/** "Mo", "Di", … */
fun DayOfWeek.shortGerman(): String = getDisplayName(TextStyle.SHORT, Locale.GERMANY).removeSuffix(".")

/** Akzeptiert "1,5" und "1.5"; null bei ungültiger oder negativer Eingabe. */
fun parseMg(input: String): Float? =
    input.trim().replace(',', '.').toFloatOrNull()?.takeIf { it >= 0f && it.isFinite() }

fun pluralDays(n: Int): String = if (n == 1) "1 Tag" else "$n Tage"

private val decimal2 = DecimalFormat("0.00", DecimalFormatSymbols(Locale.GERMANY))
private val decimal1 = DecimalFormat("0.0", DecimalFormatSymbols(Locale.GERMANY))
private val decimal0 = DecimalFormat("0", DecimalFormatSymbols(Locale.GERMANY))
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMANY)
private val dayTimeFormatter = DateTimeFormatter.ofPattern("EE dd.MM., HH:mm", Locale.GERMANY)

/** Menge im Körper, z. B. "1,24 mg" bzw. "0 mg". */
fun formatBodyMg(value: Double): String = when {
    value < 0.005 -> "0 mg"
    else -> "${decimal2.format(value)} mg"
}

/** Konzentration mit sinnvoller Genauigkeit: "0,4", "6,9", "221". */
fun formatNgPerMl(value: Double): String = when {
    value < 0.05 -> "0 ng/ml"
    value < 100 -> "${decimal1.format(value)} ng/ml"
    else -> "${decimal0.format(value)} ng/ml"
}

/** "heute 18:40", "morgen 07:15" oder "Fr 14.11., 08:00". */
fun LocalDateTime.relativeTo(now: LocalDateTime): String =
    when (java.time.temporal.ChronoUnit.DAYS.between(now.toLocalDate(), toLocalDate())) {
        0L -> "heute ${format(timeFormatter)}"
        1L -> "morgen ${format(timeFormatter)}"
        else -> format(dayTimeFormatter)
    }

fun formatKg(value: Double): String = "${mgFormat.format(value)} kg"

fun formatPercent(fraction: Double): String = "${decimal0.format(fraction * 100)} %"

/** "45 min", "1 h 20 min", "2 h". */
fun formatMinutes(minutes: Long): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0L -> "$m min"
        m == 0L -> "$h h"
        else -> "$h h $m min"
    }
}

private val euroFormat = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.GERMANY))
private val euroRound = DecimalFormat("#,##0", DecimalFormatSymbols(Locale.GERMANY))

/** "12,50 €"; ab 100 € ohne Cent. */
fun formatEuro(value: Double): String =
    if (value >= 100) "${euroRound.format(value)} €" else "${euroFormat.format(value)} €"
