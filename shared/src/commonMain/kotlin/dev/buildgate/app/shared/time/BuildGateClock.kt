@file:OptIn(kotlin.time.ExperimentalTime::class)

package dev.buildgate.app.shared.time

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

object BuildGateClock {
    fun nowIso(): String = Clock.System.now().toString()

    fun nowEpochMs(): Long = Clock.System.now().toEpochMilliseconds()

    fun todayUtcDate(): String =
        Clock.System.now().toLocalDateTime(TimeZone.UTC).date.toString()
}
