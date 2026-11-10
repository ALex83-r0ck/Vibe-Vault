package com.alexander.vibevault.data

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.alexander.vibevault.MainActivity
import com.alexander.vibevault.R
import com.alexander.vibevault.di.AppModule
import com.alexander.vibevault.domain.Insights
import com.alexander.vibevault.domain.NicotineModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Hinweise, die nicht an einen laufenden Pouch gebunden sind:
 * - Wochenrückblick sonntags um 19 Uhr
 * - abends: "Letzter Pouch für guten Schlaf bis ca. …" (30 min vorher)
 * Beides ungenau geplant (Android darf ein paar Minuten schieben) – reicht hier völlig.
 */
class Reminders(
    private val context: Context,
    private val repository: ConsumptionRepository,
    private val settings: SettingsRepository
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** Alle Hinweise neu planen, z. B. nach neuem Eintrag, geänderten Einstellungen oder Neustart. */
    suspend fun refresh() {
        val habits = settings.habits.value
        val now = LocalDateTime.now()

        if (habits.weeklyReview) schedule(ACTION_WEEKLY, Insights.nextWeeklyReviewAt(now))
        else alarmManager.cancel(intent(ACTION_WEEKLY))

        val eveningAt = if (habits.eveningReminder) eveningReminderTime(now) else null
        if (eveningAt != null) schedule(ACTION_EVENING, eveningAt)
        else alarmManager.cancel(intent(ACTION_EVENING))
    }

    /** 30 min vor dem spätesten Pouch für guten Schlaf – nur heute und nur, wenn das noch kommt. */
    private suspend fun eveningReminderTime(now: LocalDateTime): LocalDateTime? {
        val window = sleepWindow(now) ?: return null
        val last = window.lastPouchAt ?: return null
        val at = last.minusMinutes(30)
        return at.takeIf { it.isAfter(now) && it.toLocalDate() == now.toLocalDate() }
    }

    private suspend fun sleepWindow(now: LocalDateTime) = repository.getAllEntries().first().let { entries ->
        if (entries.isEmpty()) null
        else Insights.sleepWindow(
            NicotineModel(settings.pkParameters.value), entries, settings.activePouch.value?.entryId,
            settings.pouchTargetMinutes.value, settings.habits.value.bedtime, now
        )
    }

    suspend fun onWeeklyDue() {
        val entries = repository.getAllEntries().first()
        val habits = settings.habits.value
        if (entries.isNotEmpty()) {
            val r = Insights.weeklyReview(
                entries, settings.dailyLimitMg.value, habits.pauseMinutes,
                settings.pouchTargetMinutes.value, LocalDateTime.now().toLocalDate()
            )
            val mg = r.totalMg.roundToInt()
            val title = when (val c = r.changePercent) {
                null -> "🗓️ Deine Woche: $mg mg"
                in Int.MIN_VALUE..-1 -> "🗓️ Deine Woche: $mg mg (${-c} % weniger 📉)"
                0 -> "🗓️ Deine Woche: $mg mg (wie letzte Woche)"
                else -> "🗓️ Deine Woche: $mg mg ($c % mehr)"
            }
            val parts = buildList {
                add("${r.pouches} Pouches, ${r.daysInLimit}/${r.daysTracked} Tage im Limit")
                if (r.pauses.total > 0) add("${r.pauses.kept}/${r.pauses.total} Pausen gehalten")
                r.topTrigger?.let { add("Top-Auslöser: ${it.emoji} ${it.label}") }
            }
            notify(WEEKLY_ID, title, parts.joinToString(" · "))
        }
        refresh()
    }

    suspend fun onEveningDue() {
        val now = LocalDateTime.now()
        val window = sleepWindow(now)
        val last = window?.lastPouchAt
        if (window != null && last != null) {
            notify(
                EVENING_ID,
                "🌙 Bald Schluss für heute?",
                "Für guten Schlaf um ${window.bedtime.format(HHMM)}: letzter Pouch bis ca. ${last.format(HHMM)}."
            )
        }
    }

    private fun schedule(action: String, at: LocalDateTime) {
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent(action))
    }

    private fun intent(action: String) = PendingIntent.getBroadcast(
        context, action.hashCode(),
        Intent(context, ReminderReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun notify(id: Int, title: String, text: String) {
        if (!PouchTimer.canPostNotifications(context)) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Rückblick & Hinweise", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Wochenrückblick und abendlicher Hinweis zum Schlaf"
            }
        )
        val open = PendingIntent.getActivity(
            context, id, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        NotificationManagerCompat.from(context).notify(
            id,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_pouch)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
        )
    }

    companion object {
        const val ACTION_WEEKLY = "com.alexander.vibevault.action.WEEKLY_REVIEW"
        const val ACTION_EVENING = "com.alexander.vibevault.action.EVENING"
        private const val CHANNEL = "insights"
        private const val WEEKLY_ID = 1003
        private const val EVENING_ID = 1004
        private val HHMM = DateTimeFormatter.ofPattern("HH:mm")
    }
}

/** Wochenrückblick, Abend-Hinweis und Neustart des Geräts (Alarme gehen dabei verloren). */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AppModule.initialize(context)
        val reminders = AppModule.provideReminders()
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    Reminders.ACTION_WEEKLY -> reminders.onWeeklyDue()
                    Reminders.ACTION_EVENING -> reminders.onEveningDue()
                    Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                        AppModule.providePouchTimer().restoreAfterRestart()
                        reminders.refresh()
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
