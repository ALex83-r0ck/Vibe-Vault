package com.alexander.vibevault.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("vibevault_settings", Context.MODE_PRIVATE)

    private val _dailyLimitMg = MutableStateFlow(prefs.getFloat(KEY_DAILY_LIMIT, DEFAULT_DAILY_LIMIT_MG))
    val dailyLimitMg: StateFlow<Float> = _dailyLimitMg.asStateFlow()

    fun setDailyLimit(limitMg: Float) {
        prefs.edit { putFloat(KEY_DAILY_LIMIT, limitMg) }
        _dailyLimitMg.value = limitMg
    }

    companion object {
        const val DEFAULT_DAILY_LIMIT_MG = 20f
        private const val KEY_DAILY_LIMIT = "daily_limit_mg"
    }
}
