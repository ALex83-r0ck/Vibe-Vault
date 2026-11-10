package com.alexander.vibevault.data

import com.alexander.vibevault.data.model.ConsumptionEntry
import kotlinx.coroutines.flow.Flow

class ConsumptionRepository(private val database: VibeVaultDatabase) {
    /**
     * Fügt einen Eintrag ein und gibt seine id zurück.
     * Mit gesetzter id (z. B. beim Rückgängig-Machen) wird er wiederhergestellt.
     */
    suspend fun insertEntry(entry: ConsumptionEntry): Int =
        database.consumptionDao().insert(entry).toInt()

    suspend fun deleteEntry(entry: ConsumptionEntry) {
        database.consumptionDao().delete(entry)
    }

    suspend fun setDuration(entryId: Int, minutes: Int) {
        database.consumptionDao().setDuration(entryId, minutes)
    }

    fun getAllEntries(): Flow<List<ConsumptionEntry>> = database.consumptionDao().getAll()
}
