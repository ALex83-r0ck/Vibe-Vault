package com.alexander.vibevault.data

import com.alexander.vibevault.data.model.ConsumptionEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ConsumptionRepository(private val database: VibeVaultDatabase) {
    suspend fun insert(entry: ConsumptionEntry) {
        database.consumptionDao().insert(entry)
    }

    fun getAllEntries(): Flow<List<ConsumptionEntry>> = flow {
        emit(database.consumptionDao().getAll())
    }
}