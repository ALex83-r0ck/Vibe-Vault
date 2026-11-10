package com.alexander.vibevault.ui

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm", Locale.GERMANY)
private val mgFormat = DecimalFormat("0.##", DecimalFormatSymbols(Locale.GERMANY))

fun LocalDateTime.formatForDisplay(): String = format(dateTimeFormatter)

fun formatMg(value: Float): String = "${mgFormat.format(value)} mg"
