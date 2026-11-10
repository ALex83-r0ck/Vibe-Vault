package com.alexander.vibevault.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "consumption_entries")
data class ConsumptionEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    /** Zeitpunkt, an dem der Pouch in den Mund kam. */
    val timestamp: LocalDateTime,
    /** Nikotingehalt pro Pouch in mg. */
    val nicotineStrength: Float,
    val brand: String,
    /** Wie lange der Pouch im Mund war; null = unbekannt (ältere Einträge oder Timer läuft noch). */
    val durationMinutes: Int? = null,
    /** Auslöser, Schlüssel aus [com.alexander.vibevault.domain.Trigger]; null = nicht angegeben. */
    val trigger: String? = null,
    /** Mundseite: "L" oder "R"; null = nicht angegeben. */
    val side: String? = null,
    /** Wie es sich danach angefühlt hat, Schlüssel aus [com.alexander.vibevault.domain.Feeling]. */
    val feeling: String? = null
)
