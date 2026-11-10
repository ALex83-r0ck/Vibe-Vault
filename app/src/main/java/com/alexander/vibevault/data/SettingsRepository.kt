package com.alexander.vibevault.data

import android.content.Context
import androidx.core.content.edit
import com.alexander.vibevault.domain.PkParameters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime
import java.time.LocalTime

/** Der Pouch, der gerade im Mund ist. */
data class ActivePouch(val entryId: Int, val startedAt: LocalDateTime)

/** Einstellungen rund um bewussteren Konsum. */
data class Habits(
    /** Mindestabstand nach dem Rausnehmen; 0 = aus. */
    val pauseMinutes: Int = 45,
    val bedtime: LocalTime = LocalTime.of(23, 0),
    val wakeTime: LocalTime = LocalTime.of(7, 0),
    /** Preis einer Dose in Euro; null = nicht angegeben. */
    val pricePerCan: Float? = null,
    val pouchesPerCan: Int = 20,
    /** Sonntags-Benachrichtigung mit der Woche in Zahlen. */
    val weeklyReview: Boolean = true,
    /** Abends daran erinnern, wann der letzte Pouch für guten Schlaf sein sollte. */
    val eveningReminder: Boolean = true
)

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

    private val _habits = MutableStateFlow(readHabits())
    val habits: StateFlow<Habits> = _habits.asStateFlow()

    fun setHabits(habits: Habits) {
        prefs.edit {
            putInt(KEY_PAUSE, habits.pauseMinutes)
            putString(KEY_BEDTIME, habits.bedtime.toString())
            putString(KEY_WAKE, habits.wakeTime.toString())
            if (habits.pricePerCan == null) remove(KEY_PRICE) else putFloat(KEY_PRICE, habits.pricePerCan)
            putInt(KEY_PER_CAN, habits.pouchesPerCan)
            putBoolean(KEY_WEEKLY, habits.weeklyReview)
            putBoolean(KEY_EVENING, habits.eveningReminder)
        }
        _habits.value = habits
    }

    private fun readHabits(): Habits {
        val d = Habits()
        fun time(key: String, default: LocalTime) =
            prefs.getString(key, null)?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: default
        return Habits(
            pauseMinutes = prefs.getInt(KEY_PAUSE, d.pauseMinutes),
            bedtime = time(KEY_BEDTIME, d.bedtime),
            wakeTime = time(KEY_WAKE, d.wakeTime),
            pricePerCan = if (prefs.contains(KEY_PRICE)) prefs.getFloat(KEY_PRICE, 0f) else null,
            pouchesPerCan = prefs.getInt(KEY_PER_CAN, d.pouchesPerCan),
            weeklyReview = prefs.getBoolean(KEY_WEEKLY, d.weeklyReview),
            eveningReminder = prefs.getBoolean(KEY_EVENING, d.eveningReminder)
        )
    }

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
        private const val KEY_PAUSE = "pause_min"
        private const val KEY_BEDTIME = "bedtime"
        private const val KEY_WAKE = "wake_time"
        private const val KEY_PRICE = "price_per_can"
        private const val KEY_PER_CAN = "pouches_per_can"
        private const val KEY_WEEKLY = "weekly_review"
        private const val KEY_EVENING = "evening_reminder"
    }
}
