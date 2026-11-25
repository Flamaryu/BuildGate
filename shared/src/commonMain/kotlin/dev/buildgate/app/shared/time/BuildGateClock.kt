package dev.buildgate.app.shared.time

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

object BuildGateClock {
    fun nowIso(): String = Clock.System.now().toString()

    fun todayUtcDate(): String =
        Clock.System.now().toLocalDateTime(TimeZone.UTC).date.toString()
}
