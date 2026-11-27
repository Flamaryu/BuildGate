package dev.buildgate.app.shared.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.buildgate.app.shared.platform.getAndroidAppContext
import java.util.Calendar
import java.util.TimeZone

private const val PREFS_NAME = "buildgate_ui_prefs"
private const val KEY_REMINDERS_ENABLED = "daily_reminder_enabled"
private const val KEY_PENDING_COUNT = "daily_reminder_pending_count"
private const val KEY_PENDING_TITLES = "daily_reminder_pending_titles"

private const val CHANNEL_ID = "buildgate_daily_reminders"
private const val NOTIFICATION_ID = 4201
private const val ALARM_REQUEST_CODE = 4202

actual val supportsLocalReminders: Boolean = true

internal actual fun loadDailyReminderEnabled(): Boolean = runCatching {
    val ctx = getAndroidAppContext() ?: return true
    ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_REMINDERS_ENABLED, true)
}.getOrDefault(true)

internal actual fun saveDailyReminderEnabled(enabled: Boolean) {
    runCatching {
        val ctx = getAndroidAppContext() ?: return
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_REMINDERS_ENABLED, enabled)
            .apply()
    }
}

actual fun syncDailyQueueReminder(pendingCount: Int, pendingAppTitles: List<String>) {
    val ctx = getAndroidAppContext() ?: return
    val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val enabled = prefs.getBoolean(KEY_REMINDERS_ENABLED, true)

    prefs.edit()
        .putInt(KEY_PENDING_COUNT, pendingCount)
        .putString(KEY_PENDING_TITLES, pendingAppTitles.take(3).joinToString(", "))
        .apply()

    if (!enabled || pendingCount <= 0) {
        cancelScheduledDailyQueueReminder()
        return
    }

    val alarmManager = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    val triggerAtMillis = computeNextReminderTriggerMillis()
    val pendingIntent = buildAlarmPendingIntent(ctx, pendingCount, pendingAppTitles)

    runCatching {
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )
    }
}

actual fun cancelScheduledDailyQueueReminder() {
    val ctx = getAndroidAppContext() ?: return
    val alarmManager = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    val pendingIntent = buildAlarmPendingIntent(ctx, 0, emptyList())
    runCatching { alarmManager?.cancel(pendingIntent) }
    val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    runCatching { nm?.cancel(NOTIFICATION_ID) }
}

actual fun sendTestDailyQueueReminder(pendingCount: Int, pendingAppTitles: List<String>): Boolean {
    val ctx = getAndroidAppContext() ?: return false
    val count = if (pendingCount > 0) pendingCount else 1
    val titles = if (pendingAppTitles.isNotEmpty()) pendingAppTitles else listOf("Spacefold")
    return postDailyQueueNotification(ctx, count, titles.take(3).joinToString(", "), isTest = true)
}

private fun computeNextReminderTriggerMillis(): Long {
    val now = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    val target = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        set(Calendar.HOUR_OF_DAY, 22)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    if (!target.after(now)) {
        // Already past 22:00 UTC today; schedule for tomorrow 22:00 UTC
        target.add(Calendar.DAY_OF_YEAR, 1)
    }
    return target.timeInMillis
}

private fun buildAlarmPendingIntent(
    ctx: Context,
    pendingCount: Int,
    pendingAppTitles: List<String>
): PendingIntent {
    val intent = Intent(ctx, DailyQueueReminderReceiver::class.java).apply {
        action = DailyQueueReminderReceiver.ACTION_FIRE_REMINDER
        putExtra(DailyQueueReminderReceiver.EXTRA_PENDING_COUNT, pendingCount)
        putExtra(DailyQueueReminderReceiver.EXTRA_PENDING_TITLES, pendingAppTitles.take(3).joinToString(", "))
    }
    return PendingIntent.getBroadcast(
        ctx,
        ALARM_REQUEST_CODE,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

internal fun postDailyQueueNotification(
    ctx: Context,
    pendingCount: Int,
    titlesCsv: String,
    isTest: Boolean = false
): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val granted = ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return false
    }

    val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Daily Streak Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Reminds you 2 hours before UTC midnight when apps remain in your BuildGate Daily Queue."
        }
        nm.createNotificationChannel(channel)
    }

    val openQueueIntent = Intent(Intent.ACTION_VIEW, Uri.parse("buildgate://queue")).apply {
        setPackage(ctx.packageName)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
    val contentIntent = PendingIntent.getActivity(
        ctx,
        NOTIFICATION_ID,
        openQueueIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val appLabel = if (pendingCount == 1) "1 app left" else "$pendingCount apps left"
    val title = if (isTest) "⚡ BuildGate Reminder (Test) • $appLabel" else "⚡ Daily Queue • $appLabel today"
    val body = if (titlesCsv.isNotBlank()) {
        "Verify $titlesCsv for 60s before UTC midnight to keep your 14-day streak alive (+10⚡ each)."
    } else {
        "Complete your 60s check-ins before UTC midnight to keep your 14-day streak alive (+10⚡ each)."
    }

    val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
        .setSmallIcon(ctx.applicationInfo.icon)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(contentIntent)
        .build()

    nm.notify(NOTIFICATION_ID, notification)
    return true
}

class DailyQueueReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_REMINDERS_ENABLED, true)) return

        val pendingCount = intent?.getIntExtra(EXTRA_PENDING_COUNT, -1)
            ?.takeIf { it > 0 }
            ?: prefs.getInt(KEY_PENDING_COUNT, 0)
        if (pendingCount <= 0) return

        val titles = intent?.getStringExtra(EXTRA_PENDING_TITLES)
            ?.takeIf { it.isNotBlank() }
            ?: prefs.getString(KEY_PENDING_TITLES, "").orEmpty()

        postDailyQueueNotification(context, pendingCount, titles, isTest = false)
    }

    companion object {
        const val ACTION_FIRE_REMINDER = "dev.buildgate.app.DAILY_QUEUE_REMINDER"
        const val EXTRA_PENDING_COUNT = "extra_pending_count"
        const val EXTRA_PENDING_TITLES = "extra_pending_titles"
    }
}
