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
