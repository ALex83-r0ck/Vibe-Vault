package com.alexander.vibevault.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.alexander.vibevault.AchievementsViewModel
import com.alexander.vibevault.AddEntryViewModel
import com.alexander.vibevault.HomeViewModel
import com.alexander.vibevault.StatsViewModel
import com.alexander.vibevault.data.ConsumptionRepository
import com.alexander.vibevault.data.SettingsRepository
import com.alexander.vibevault.data.VibeVaultDatabase

object AppModule {
    private lateinit var repository: ConsumptionRepository
    private lateinit var settings: SettingsRepository

    /** Idempotent: wird bei jedem Activity-Start aufgerufen, legt die Instanzen aber nur einmal an. */
    fun initialize(context: Context) {
        if (::repository.isInitialized) return
        repository = ConsumptionRepository(VibeVaultDatabase.getDatabase(context))
        settings = SettingsRepository(context.applicationContext)
    }

    fun provideViewModelFactory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
            HomeViewModel::class.java -> HomeViewModel(repository, settings)
            AddEntryViewModel::class.java -> AddEntryViewModel(repository)
            StatsViewModel::class.java -> StatsViewModel(repository, settings)
            AchievementsViewModel::class.java -> AchievementsViewModel(repository, settings)
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        } as T
    }
}
