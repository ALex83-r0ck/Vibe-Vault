package com.alexander.vibevault.di

import android.content.Context
import com.alexander.vibevault.data.ConsumptionRepository
import com.alexander.vibevault.data.VibeVaultDatabase

object AppModule {
    private lateinit var database: VibeVaultDatabase
    private lateinit var repository: ConsumptionRepository

    fun initialize(context: Context) {
        database = VibeVaultDatabase.getDatabase(context)
        repository = ConsumptionRepository(database)
    }

    fun provideRepository(): ConsumptionRepository = repository
    fun provideDatabase(): VibeVaultDatabase = database
}