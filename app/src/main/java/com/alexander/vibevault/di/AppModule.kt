package com.alexander.vibevault.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.alexander.vibevault.AchievementsViewModel
import com.alexander.vibevault.AddEntryViewModel
import com.alexander.vibevault.BodyViewModel
import com.alexander.vibevault.HomeViewModel
import com.alexander.vibevault.StatsViewModel
import com.alexander.vibevault.data.ConsumptionRepository
import com.alexander.vibevault.data.LabelScanner
import com.alexander.vibevault.data.PouchTimer
import com.alexander.vibevault.data.Reminders
import com.alexander.vibevault.SettingsViewModel
import com.alexander.vibevault.data.SettingsRepository
import com.alexander.vibevault.data.VibeVaultDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object AppModule {
    private lateinit var repository: ConsumptionRepository
    private lateinit var settings: SettingsRepository
    private lateinit var labelScanner: LabelScanner
    private lateinit var pouchTimer: PouchTimer
    private lateinit var reminders: Reminders

    /**
     * Für Schreibvorgänge, die das Schließen eines Screens überleben müssen
     * (viewModelScope wird beim Verlassen des Screens abgebrochen).
     */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Idempotent: wird bei jedem Activity-Start und vom Timer-Receiver aufgerufen. */
    @Synchronized
    fun initialize(context: Context) {
        if (::repository.isInitialized) return
        val appContext = context.applicationContext
        repository = ConsumptionRepository(VibeVaultDatabase.getDatabase(appContext))
        settings = SettingsRepository(appContext)
        labelScanner = LabelScanner(appContext)
        reminders = Reminders(appContext, repository, settings)
        pouchTimer = PouchTimer(appContext, repository, settings, reminders)
    }

    fun providePouchTimer(): PouchTimer = pouchTimer
    fun provideReminders(): Reminders = reminders

    fun provideViewModelFactory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
            HomeViewModel::class.java -> HomeViewModel(repository, settings, pouchTimer, appScope)
            AddEntryViewModel::class.java -> AddEntryViewModel(repository, settings, labelScanner, pouchTimer, reminders, appScope)
            StatsViewModel::class.java -> StatsViewModel(repository, settings, pouchTimer)
            AchievementsViewModel::class.java -> AchievementsViewModel(repository, settings)
            BodyViewModel::class.java -> BodyViewModel(repository, settings)
            SettingsViewModel::class.java -> SettingsViewModel(settings, reminders, appScope)
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        } as T
    }
}
