package com.alexander.vibevault.data

import android.content.Context
import androidx.core.content.edit
import com.alexander.vibevault.domain.PkParameters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("vibevault_settings", Context.MODE_PRIVATE)

    private val _dailyLimitMg = MutableStateFlow(prefs.getFloat(KEY_DAILY_LIMIT, DEFAULT_DAILY_LIMIT_MG))
    val dailyLimitMg: StateFlow<Float> = _dailyLimitMg.asStateFlow()

    private val _pkParameters = MutableStateFlow(
        PkParameters(
            bodyWeightKg = prefs.getFloat(KEY_BODY_WEIGHT, DEFAULT_PK.bodyWeightKg.toFloat()).toDouble(),
            absorbedFraction = prefs.getFloat(KEY_ABSORBED_FRACTION, DEFAULT_PK.absorbedFraction.toFloat()).toDouble()
        )
    )
    val pkParameters: StateFlow<PkParameters> = _pkParameters.asStateFlow()

    fun setDailyLimit(limitMg: Float) {
        prefs.edit { putFloat(KEY_DAILY_LIMIT, limitMg) }
        _dailyLimitMg.value = limitMg
    }

    fun setBodyModel(bodyWeightKg: Double, absorbedFraction: Double) {
        prefs.edit {
            putFloat(KEY_BODY_WEIGHT, bodyWeightKg.toFloat())
            putFloat(KEY_ABSORBED_FRACTION, absorbedFraction.toFloat())
        }
        _pkParameters.value = _pkParameters.value.copy(bodyWeightKg = bodyWeightKg, absorbedFraction = absorbedFraction)
    }

    companion object {
        const val DEFAULT_DAILY_LIMIT_MG = 20f
        val DEFAULT_PK = PkParameters()
        private const val KEY_DAILY_LIMIT = "daily_limit_mg"
        private const val KEY_BODY_WEIGHT = "body_weight_kg"
        private const val KEY_ABSORBED_FRACTION = "absorbed_fraction"
    }
}
