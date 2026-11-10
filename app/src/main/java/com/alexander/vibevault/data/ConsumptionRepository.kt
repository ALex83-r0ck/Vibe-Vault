package com.alexander.vibevault.data

import com.alexander.vibevault.data.model.ConsumptionEntry
import kotlinx.coroutines.flow.Flow

class ConsumptionRepository(private val database: VibeVaultDatabase) {
    suspend fun insertEntry(entry: ConsumptionEntry) {
        database.consumptionDao().insert(entry)
    }

    fun getAllEntries(): Flow<List<ConsumptionEntry>> = database.consumptionDao().getAll()
}
