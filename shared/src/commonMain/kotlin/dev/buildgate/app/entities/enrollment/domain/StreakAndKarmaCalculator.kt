package dev.buildgate.app.entities.enrollment.domain

import dev.buildgate.app.entities.dailyverification.model.VerificationMethod
import dev.buildgate.app.entities.enrollment.model.Enrollment
import dev.buildgate.app.entities.enrollment.model.EnrollmentStatus
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

data class VerificationOutcome(
    val updatedEnrollment: Enrollment,
    val karmaEarned: Int,
    val dayNumber: Int,
    val usedGraceDay: Boolean,
    val streakResetOccurred: Boolean,
    val campaignJustCompleted: Boolean,
    val alreadyVerifiedToday: Boolean = false
)

object StreakAndKarmaCalculator {
    const val BASE_DAILY_KARMA = 10
    const val DEEPLINK_HANDSHAKE_BONUS_KARMA = 5
    const val TRIAGED_BUG_BOUNTY_KARMA = 25
    const val DAY_14_COMPLETION_BONUS_KARMA = 100
    const val MAX_GRACE_DAYS = 1

    fun applyDailyVerification(
        enrollment: Enrollment,
        todayUtcDate: String,
        targetDays: Int = 14,
        method: VerificationMethod = VerificationMethod.ZERO_SDK_SESSION,
        installedVersionCode: Long? = null
    ): VerificationOutcome {
        if (enrollment.lastVerifiedUtcDate == todayUtcDate) {
            return VerificationOutcome(
                updatedEnrollment = enrollment,
                karmaEarned = 0,
                dayNumber = enrollment.currentStreak,
                usedGraceDay = false,
                streakResetOccurred = false,
                campaignJustCompleted = false,
                alreadyVerifiedToday = true
            )
        }

        val today = LocalDate.parse(todayUtcDate)
        val lastDate = enrollment.lastVerifiedUtcDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val daysSinceLast = lastDate?.daysUntil(today)

        var newStreak: Int
        var newMissedUsed = enrollment.missedDaysUsed
        var usedGrace = false
        var streakReset = false

        when {
            daysSinceLast == null -> {
                newStreak = 1
            }
            daysSinceLast == 1 -> {
                newStreak = enrollment.currentStreak + 1
            }
            daysSinceLast == 2 && enrollment.missedDaysUsed < MAX_GRACE_DAYS -> {
                // 1-day grace buffer preserves continuity!
                newStreak = enrollment.currentStreak + 1
                newMissedUsed += 1
                usedGrace = true
            }
            else -> {
                // Missed >1 day or exhausted grace buffer -> continuity resets
                newStreak = 1
                newMissedUsed = 0
                streakReset = enrollment.currentStreak > 0
            }
        }

        val clampedStreak = newStreak.coerceAtMost(targetDays)
        val newVerifiedTotal = enrollment.verifiedDaysCount + 1
        val wasCompleted = enrollment.status == EnrollmentStatus.COMPLETED
        val nowCompleted = clampedStreak >= targetDays
        val justCompleted = !wasCompleted && nowCompleted

        var karma = BASE_DAILY_KARMA
        if (method == VerificationMethod.VERIFIED_DEEPLINK_HANDSHAKE) {
            karma += DEEPLINK_HANDSHAKE_BONUS_KARMA
        }
        if (justCompleted) {
            karma += DAY_14_COMPLETION_BONUS_KARMA
        }

        val updated = enrollment.copy(
            verifiedDaysCount = newVerifiedTotal,
            currentStreak = clampedStreak,
            missedDaysUsed = newMissedUsed,
            lastVerifiedUtcDate = todayUtcDate,
            lastSeenVersionCode = installedVersionCode ?: enrollment.lastSeenVersionCode,
            status = if (nowCompleted) EnrollmentStatus.COMPLETED else EnrollmentStatus.ACTIVE
        )

        return VerificationOutcome(
            updatedEnrollment = updated,
            karmaEarned = karma,
            dayNumber = clampedStreak,
            usedGraceDay = usedGrace,
            streakResetOccurred = streakReset,
            campaignJustCompleted = justCompleted,
            alreadyVerifiedToday = false
        )
    }

    fun applyUninstallCheck(
        enrollment: Enrollment,
        isInstalled: Boolean,
        versionCode: Long? = null
    ): Enrollment {
        if (enrollment.status == EnrollmentStatus.COMPLETED) return enrollment
        return if (!isInstalled && enrollment.verifiedDaysCount > 0) {
            enrollment.copy(
                status = EnrollmentStatus.DROPPED_UNINSTALLED,
                currentStreak = 0
            )
        } else if (isInstalled) {
            enrollment.copy(
                status = if (enrollment.status == EnrollmentStatus.PENDING_INSTALL || enrollment.status == EnrollmentStatus.DROPPED_UNINSTALLED) {
                    EnrollmentStatus.ACTIVE
                } else {
                    enrollment.status
                },
                lastSeenVersionCode = versionCode ?: enrollment.lastSeenVersionCode
            )
        } else {
            enrollment
        }
    }
}
