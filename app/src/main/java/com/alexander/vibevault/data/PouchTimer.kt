package com.alexander.vibevault.data

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.alexander.vibevault.MainActivity
import com.alexander.vibevault.R
import com.alexander.vibevault.di.AppModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Timer für den Pouch im Mund: laufende Benachrichtigung mit Countdown, Erinnerung bei Ablauf
 * der Zieldauer und "Raus genommen" – auch bei geschlossener App.
 */
class PouchTimer(
    private val context: Context,
    private val repository: ConsumptionRepository,
    private val settings: SettingsRepository
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** Startet den Timer. Ein noch laufender Pouch gilt als jetzt herausgenommen. */
    suspend fun start(entryId: Int, startedAt: LocalDateTime) {
        if (settings.activePouch.value != null) stop(LocalDateTime.now())
        settings.setActivePouch(ActivePouch(entryId, startedAt))
        val remindAt = startedAt.plusMinutes(settings.pouchTargetMinutes.value.toLong())
        showRunning(startedAt, remindAt, overdue = false)
        scheduleReminder(remindAt)
    }

    /** Pouch raus: Dauer speichern, Timer beenden. */
    suspend fun stop(removedAt: LocalDateTime) {
        val active = settings.activePouch.value ?: return
        val minutes = Duration.between(active.startedAt, removedAt).toMinutes().coerceAtLeast(1)
        repository.setDuration(active.entryId, minutes.toInt())
        clear()
    }

    /** Timer verwerfen, ohne eine Dauer zu speichern (z. B. Eintrag gelöscht). */
    fun discard(entryId: Int) {
        if (settings.activePouch.value?.entryId == entryId) clear()
    }

    /** Vom Alarm aufgerufen. */
    fun onReminderDue() {
        val active = settings.activePouch.value ?: return
        val target = settings.pouchTargetMinutes.value
        notify(
            REMINDER_ID,
            NotificationCompat.Builder(context, CHANNEL_REMINDER)
                .setSmallIcon(R.drawable.ic_stat_pouch)
                .setContentTitle("⏰ $target Minuten um")
                .setContentText("Zeit, den Pouch rauszunehmen.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(openAppIntent())
                .addAction(0, "Raus genommen", stopIntent())
                .build()
        )
        showRunning(active.startedAt, active.startedAt.plusMinutes(target.toLong()), overdue = true)
    }

    private fun clear() {
        settings.setActivePouch(null)
        alarmManager.cancel(reminderIntent())
        NotificationManagerCompat.from(context).apply {
            cancel(RUNNING_ID)
            cancel(REMINDER_ID)
        }
    }

    /**
     * Ohne die Sondererlaubnis "Wecker & Erinnerungen" (Android 12+) darf Android die Erinnerung
     * um bis zu ~10 min verschieben.
     */
    fun canRemindOnTime(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /** Erinnerung neu einplanen, z. B. nachdem die Erlaubnis für pünktliche Alarme erteilt wurde. */
    fun rescheduleIfActive() {
        val active = settings.activePouch.value ?: return
        val remindAt = active.startedAt.plusMinutes(settings.pouchTargetMinutes.value.toLong())
        if (remindAt.isAfter(LocalDateTime.now())) scheduleReminder(remindAt)
    }

    private fun scheduleReminder(at: LocalDateTime) {
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (canRemindOnTime()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, reminderIntent())
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, reminderIntent())
        }
    }

    /** Laufende Benachrichtigung: vor Ablauf Countdown, danach Zeit seit dem Einlegen. */
    private fun showRunning(startedAt: LocalDateTime, remindAt: LocalDateTime, overdue: Boolean) {
        val time = DateTimeFormatter.ofPattern("HH:mm")
        val base = if (overdue) startedAt else remindAt
        notify(
            RUNNING_ID,
            NotificationCompat.Builder(context, CHANNEL_RUNNING)
                .setSmallIcon(R.drawable.ic_stat_pouch)
                .setContentTitle(if (overdue) "Pouch ist über der Zeit" else "Pouch drin")
                .setContentText("Seit ${startedAt.format(time)} · Ziel ${remindAt.format(time)}")
                .setUsesChronometer(true)
                .setChronometerCountDown(!overdue)
                .setWhen(base.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                .setShowWhen(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setContentIntent(openAppIntent())
                .addAction(0, "Raus genommen", stopIntent())
                .build()
        )
    }

    private fun notify(id: Int, notification: android.app.Notification) {
        if (!canPostNotifications(context)) return
        ensureChannels()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun ensureChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_RUNNING, "Laufender Pouch", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Zeigt, wie lange der aktuelle Pouch schon drin ist"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDER, "Pouch-Erinnerung", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Erinnert dich, den Pouch nach deiner Zieldauer rauszunehmen"
            }
        )
    }

    private fun openAppIntent() = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun stopIntent() = receiverIntent(PouchTimerReceiver.ACTION_STOP, 1)
    private fun reminderIntent() = receiverIntent(PouchTimerReceiver.ACTION_REMIND, 2)

    private fun receiverIntent(action: String, requestCode: Int) = PendingIntent.getBroadcast(
        context, requestCode,
        Intent(context, PouchTimerReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    companion object {
        private const val CHANNEL_RUNNING = "pouch_running"
        private const val CHANNEL_REMINDER = "pouch_reminder"
        private const val RUNNING_ID = 1001
        private const val REMINDER_ID = 1002

        fun canPostNotifications(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
    }
}

/** Empfängt Erinnerungs-Alarm und "Raus genommen" aus der Benachrichtigung. */
class PouchTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AppModule.initialize(context)
        val timer = AppModule.providePouchTimer()
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    ACTION_REMIND -> timer.onReminderDue()
                    ACTION_STOP -> timer.stop(LocalDateTime.now())
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMIND = "com.alexander.vibevault.action.POUCH_REMIND"
        const val ACTION_STOP = "com.alexander.vibevault.action.POUCH_STOP"
    }
}
