package dev.buildgate.app.shared.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DailyQueueReminderPreferences {
    private val _enabled = MutableStateFlow(loadDailyReminderEnabled())
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun reloadFromStorage() {
        _enabled.value = loadDailyReminderEnabled()
    }

    fun setEnabled(isEnabled: Boolean) {
        _enabled.value = isEnabled
        saveDailyReminderEnabled(isEnabled)
        if (!isEnabled) {
            cancelScheduledDailyQueueReminder()
        }
    }
}

expect val supportsLocalReminders: Boolean
internal expect fun loadDailyReminderEnabled(): Boolean
internal expect fun saveDailyReminderEnabled(enabled: Boolean)
expect fun syncDailyQueueReminder(pendingCount: Int, pendingAppTitles: List<String>)
expect fun cancelScheduledDailyQueueReminder()
expect fun sendTestDailyQueueReminder(pendingCount: Int, pendingAppTitles: List<String>): Boolean
