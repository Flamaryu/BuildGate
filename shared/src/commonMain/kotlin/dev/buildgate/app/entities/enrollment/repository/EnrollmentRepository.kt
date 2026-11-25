package dev.buildgate.app.entities.enrollment.repository

import dev.buildgate.app.entities.dailyverification.model.DailyVerification
import dev.buildgate.app.entities.dailyverification.model.VerificationMethod
import dev.buildgate.app.entities.enrollment.domain.StreakAndKarmaCalculator
import dev.buildgate.app.entities.enrollment.domain.VerificationOutcome
import dev.buildgate.app.entities.enrollment.model.Enrollment
import dev.buildgate.app.entities.enrollment.model.EnrollmentStatus
import dev.buildgate.app.shared.time.BuildGateClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

interface EnrollmentRepository {
    fun getEnrollmentsForTester(testerId: String): Flow<List<Enrollment>>
    fun getEnrollmentsForApp(appId: String): Flow<List<Enrollment>>
    fun observeEnrollment(appId: String, testerId: String): Flow<Enrollment?>
    suspend fun getEnrollment(appId: String, testerId: String): Enrollment?
    suspend fun enrollInApp(appId: String, testerId: String): Enrollment
    suspend fun markOnboardingStep(
        appId: String,
        testerId: String,
        joinedGoogleGroup: Boolean? = null,
        acceptedPlayOptIn: Boolean? = null
    ): Enrollment
    suspend fun syncInstallStatus(
        appId: String,
        testerId: String,
        isInstalled: Boolean,
        versionCode: Long? = null
    ): Enrollment?
    suspend fun recordDailyVerification(
        enrollmentId: String,
        sessionToken: String,
        activeDurationSeconds: Int,
        method: VerificationMethod,
        targetDays: Int = 14,
        installedVersionCode: Long? = null
    ): VerificationOutcome
    fun getVerificationsForEnrollment(enrollmentId: String): Flow<List<DailyVerification>>
}

class DemoEnrollmentRepository : EnrollmentRepository {

    private val yesterdayDate: String by lazy {
        LocalDate.parse(BuildGateClock.todayUtcDate()).minus(DatePeriod(days = 1)).toString()
    }

    private val enrollmentsState = MutableStateFlow(
        listOf(
            Enrollment(
                id = "enr_1",
                appId = "app_1",
                testerId = "tester_1",
                hasJoinedGoogleGroup = true,
                hasAcceptedPlayOptIn = true,
                verifiedDaysCount = 4,
                currentStreak = 4,
                missedDaysUsed = 0,
                lastVerifiedUtcDate = yesterdayDate,
                lastSeenVersionCode = 7L,
                status = EnrollmentStatus.ACTIVE,
                enrolledAtIso = "2026-11-20T10:00:00Z"
            ),
            Enrollment(
                id = "enr_2",
                appId = "app_2",
                testerId = "tester_1",
                hasJoinedGoogleGroup = true,
                hasAcceptedPlayOptIn = true,
                verifiedDaysCount = 3,
                currentStreak = 3,
                missedDaysUsed = 0,
                lastVerifiedUtcDate = yesterdayDate,
                lastSeenVersionCode = 7L,
                status = EnrollmentStatus.ACTIVE,
                enrolledAtIso = "2026-11-21T10:00:00Z"
            ),
            Enrollment(
                id = "enr_3",
                appId = "app_3",
                testerId = "tester_1",
                hasJoinedGoogleGroup = true,
                hasAcceptedPlayOptIn = true,
                verifiedDaysCount = 2,
                currentStreak = 2,
                missedDaysUsed = 0,
                lastVerifiedUtcDate = yesterdayDate,
                lastSeenVersionCode = 8L,
                status = EnrollmentStatus.ACTIVE,
                enrolledAtIso = "2026-11-22T10:00:00Z"
            ),
            Enrollment(
                id = "enr_4",
                appId = "app_1",
                testerId = "tester_2",
                hasJoinedGoogleGroup = true,
                hasAcceptedPlayOptIn = true,
                verifiedDaysCount = 14,
                currentStreak = 14,
                missedDaysUsed = 0,
                lastVerifiedUtcDate = yesterdayDate,
                lastSeenVersionCode = 7L,
                status = EnrollmentStatus.COMPLETED,
                enrolledAtIso = "2026-11-08T10:00:00Z"
            )
        )
    )

    private val verificationsState = MutableStateFlow(
        listOf(
            DailyVerification("v1", "enr_1", 1, "token_d1", "2026-11-20", "2026-11-20T12:00:00Z"),
            DailyVerification("v2", "enr_1", 2, "token_d2", "2026-11-21", "2026-11-21T12:00:00Z"),
            DailyVerification("v3", "enr_1", 3, "token_d3", "2026-11-22", "2026-11-22T12:00:00Z"),
            DailyVerification("v4", "enr_1", 4, "token_d4", yesterdayDate, "2026-11-23T12:00:00Z")
        )
    )

    override fun getEnrollmentsForTester(testerId: String): Flow<List<Enrollment>> =
        enrollmentsState.map { list -> list.filter { it.testerId == testerId } }

    override fun getEnrollmentsForApp(appId: String): Flow<List<Enrollment>> =
        enrollmentsState.map { list -> list.filter { it.appId == appId } }

    override fun observeEnrollment(appId: String, testerId: String): Flow<Enrollment?> =
        enrollmentsState.map { list -> list.find { it.appId == appId && it.testerId == testerId } }

    override suspend fun getEnrollment(appId: String, testerId: String): Enrollment? =
        enrollmentsState.value.find { it.appId == appId && it.testerId == testerId }

    override suspend fun enrollInApp(appId: String, testerId: String): Enrollment {
        val existing = getEnrollment(appId, testerId)
        if (existing != null) return existing

        val newEnrollment = Enrollment(
            id = "enr_${enrollmentsState.value.size + 1}",
            appId = appId,
            testerId = testerId,
            verifiedDaysCount = 0,
            currentStreak = 0,
            status = EnrollmentStatus.PENDING_INSTALL,
            enrolledAtIso = BuildGateClock.nowIso()
        )
        enrollmentsState.update { it + newEnrollment }
        return newEnrollment
    }

    override suspend fun markOnboardingStep(
        appId: String,
        testerId: String,
        joinedGoogleGroup: Boolean?,
        acceptedPlayOptIn: Boolean?
    ): Enrollment {
        val base = enrollInApp(appId, testerId)
        val updated = base.copy(
            hasJoinedGoogleGroup = joinedGoogleGroup ?: base.hasJoinedGoogleGroup,
            hasAcceptedPlayOptIn = acceptedPlayOptIn ?: base.hasAcceptedPlayOptIn
        )
        enrollmentsState.update { list ->
            list.map { if (it.id == updated.id) updated else it }
        }
        return updated
    }

    override suspend fun syncInstallStatus(
        appId: String,
        testerId: String,
        isInstalled: Boolean,
        versionCode: Long?
    ): Enrollment? {
        val existing = getEnrollment(appId, testerId) ?: return null
        val updated = StreakAndKarmaCalculator.applyUninstallCheck(existing, isInstalled, versionCode)
        if (updated != existing) {
            enrollmentsState.update { list ->
                list.map { if (it.id == existing.id) updated else it }
            }
        }
        return updated
    }

    override suspend fun recordDailyVerification(
        enrollmentId: String,
        sessionToken: String,
        activeDurationSeconds: Int,
        method: VerificationMethod,
        targetDays: Int,
        installedVersionCode: Long?
    ): VerificationOutcome {
        val current = enrollmentsState.value.find { it.id == enrollmentId }
            ?: error("Enrollment $enrollmentId not found")
        val todayUtc = BuildGateClock.todayUtcDate()
        val outcome = StreakAndKarmaCalculator.applyDailyVerification(
            enrollment = current,
            todayUtcDate = todayUtc,
            targetDays = targetDays,
            method = method,
            installedVersionCode = installedVersionCode
        )

        if (!outcome.alreadyVerifiedToday) {
            val verification = DailyVerification(
                id = "v_${verificationsState.value.size + 1}",
                enrollmentId = enrollmentId,
                dayNumber = outcome.dayNumber,
                sessionToken = sessionToken,
                verifiedUtcDate = todayUtc,
                verifiedAtIso = BuildGateClock.nowIso(),
                activeDurationSeconds = activeDurationSeconds,
                karmaAwarded = outcome.karmaEarned,
                method = method
            )
            verificationsState.update { it + verification }
            enrollmentsState.update { list ->
                list.map { if (it.id == enrollmentId) outcome.updatedEnrollment else it }
            }
        }
        return outcome
    }

    override fun getVerificationsForEnrollment(enrollmentId: String): Flow<List<DailyVerification>> =
        verificationsState.map { list -> list.filter { it.enrollmentId == enrollmentId } }
}
