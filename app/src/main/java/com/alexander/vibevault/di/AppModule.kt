package com.alexander.vibevault.di

import android.content.Context
import com.alexander.vibevault.data.ConsumptionRepository
import com.alexander.vibevault.data.VibeVaultDatabase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

object AppModule {
    private lateinit var database: VibeVaultDatabase
    private lateinit var repository: ConsumptionRepository

    fun initialize(context: Context) {
        database = VibeVaultDatabase.getDatabase(context)
        repository = ConsumptionRepository(database)
    }

    fun provideRepository(): ConsumptionRepository = repository
    fun provideDatabase(): VibeVaultDatabase = database

    fun provideViewModelFactory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return when {
                modelClass.isAssignableFrom(com.alexander.vibevault.HomeViewModel::class.java) ->
                    com.alexander.vibevault.HomeViewModel(repository) as T
                modelClass.isAssignableFrom(com.alexander.vibevault.AddEntryViewModel::class.java) ->
                    com.alexander.vibevault.AddEntryViewModel(repository) as T
                modelClass.isAssignableFrom(com.alexander.vibevault.StatsViewModel::class.java) ->
                    com.alexander.vibevault.StatsViewModel(repository) as T
                else -> throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
}