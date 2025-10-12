package com.alexander.vibevault.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "consumption_entries")
data class ConsumptionEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: LocalDateTime,
    val nicotineStrength: Float, // Angepasst von 'strength' zu 'nicotineStrength'
    val brand: String
)