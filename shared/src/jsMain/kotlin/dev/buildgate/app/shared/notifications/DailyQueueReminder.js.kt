package dev.buildgate.app.shared.notifications

actual val supportsLocalReminders: Boolean = false
internal actual fun loadDailyReminderEnabled(): Boolean = false
internal actual fun saveDailyReminderEnabled(enabled: Boolean) {}
actual fun syncDailyQueueReminder(pendingCount: Int, pendingAppTitles: List<String>) {}
actual fun cancelScheduledDailyQueueReminder() {}
actual fun sendTestDailyQueueReminder(pendingCount: Int, pendingAppTitles: List<String>): Boolean = false
