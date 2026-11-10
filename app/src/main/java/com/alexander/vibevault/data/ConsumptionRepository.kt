package com.alexander.vibevault.data

import com.alexander.vibevault.data.model.ConsumptionEntry
import kotlinx.coroutines.flow.Flow

class ConsumptionRepository(private val database: VibeVaultDatabase) {
    /** Fügt einen Eintrag ein. Mit gesetzter id (z. B. beim Rückgängig-Machen) wird er wiederhergestellt. */
    suspend fun insertEntry(entry: ConsumptionEntry) {
        database.consumptionDao().insert(entry)
    }

    suspend fun deleteEntry(entry: ConsumptionEntry) {
        database.consumptionDao().delete(entry)
    }

    fun getAllEntries(): Flow<List<ConsumptionEntry>> = database.consumptionDao().getAll()
}
