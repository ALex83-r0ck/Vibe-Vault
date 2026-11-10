package com.alexander.vibevault.data

import android.content.Context
import androidx.core.content.edit
import com.alexander.vibevault.domain.PkParameters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime

/** Der Pouch, der gerade im Mund ist. */
data class ActivePouch(val entryId: Int, val startedAt: LocalDateTime)

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

    private val _pouchTargetMinutes = MutableStateFlow(prefs.getInt(KEY_POUCH_TARGET, DEFAULT_POUCH_TARGET_MIN))
    val pouchTargetMinutes: StateFlow<Int> = _pouchTargetMinutes.asStateFlow()

    private val _activePouch = MutableStateFlow(readActivePouch())
    val activePouch: StateFlow<ActivePouch?> = _activePouch.asStateFlow()

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

    fun setPouchTarget(minutes: Int) {
        prefs.edit { putInt(KEY_POUCH_TARGET, minutes) }
        _pouchTargetMinutes.value = minutes
    }

    fun setActivePouch(pouch: ActivePouch?) {
        prefs.edit(commit = true) {
            if (pouch == null) {
                remove(KEY_ACTIVE_ID)
                remove(KEY_ACTIVE_START)
            } else {
                putInt(KEY_ACTIVE_ID, pouch.entryId)
                putString(KEY_ACTIVE_START, pouch.startedAt.toString())
            }
        }
        _activePouch.value = pouch
    }

    private fun readActivePouch(): ActivePouch? {
        val id = prefs.getInt(KEY_ACTIVE_ID, -1).takeIf { it >= 0 } ?: return null
        val start = prefs.getString(KEY_ACTIVE_START, null)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
            ?: return null
        return ActivePouch(id, start)
    }

    companion object {
        const val DEFAULT_DAILY_LIMIT_MG = 20f
        const val DEFAULT_POUCH_TARGET_MIN = 30
        val DEFAULT_PK = PkParameters()
        private const val KEY_DAILY_LIMIT = "daily_limit_mg"
        private const val KEY_BODY_WEIGHT = "body_weight_kg"
        private const val KEY_ABSORBED_FRACTION = "absorbed_fraction"
        private const val KEY_POUCH_TARGET = "pouch_target_min"
        private const val KEY_ACTIVE_ID = "active_pouch_id"
        private const val KEY_ACTIVE_START = "active_pouch_start"
    }
}
