package dev.buildgate.app.entities.enrollment.repository

import dev.buildgate.app.entities.dailyverification.model.DailyVerification
import dev.buildgate.app.entities.dailyverification.model.VerificationMethod
import dev.buildgate.app.entities.enrollment.model.Enrollment
import dev.buildgate.app.entities.enrollment.model.EnrollmentStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

interface EnrollmentRepository {
    fun getEnrollmentsForTester(testerId: String): Flow<List<Enrollment>>
    fun getEnrollmentsForApp(appId: String): Flow<List<Enrollment>>
    suspend fun getEnrollment(appId: String, testerId: String): Enrollment?
    suspend fun enrollInApp(appId: String, testerId: String): Enrollment
    suspend fun recordDailyVerification(
        enrollmentId: String,
        dayNumber: Int,
        sessionToken: String,
        method: VerificationMethod
    ): DailyVerification
    fun getVerificationsForEnrollment(enrollmentId: String): Flow<List<DailyVerification>>
}

class InMemoryEnrollmentRepository : EnrollmentRepository {

    private val enrollmentsState = MutableStateFlow(
        listOf(
            Enrollment(
                id = "enr_1",
                appId = "app_1",
                testerId = "tester_1",
                verifiedDaysCount = 4,
                status = EnrollmentStatus.ACTIVE,
                enrolledAtIso = "2025-02-15T10:00:00Z"
            ),
            Enrollment(
                id = "enr_2",
                appId = "app_2",
                testerId = "tester_1",
                verifiedDaysCount = 11,
                status = EnrollmentStatus.ACTIVE,
                enrolledAtIso = "2025-02-08T10:00:00Z"
            ),
            Enrollment(
                id = "enr_3",
                appId = "app_1",
                testerId = "tester_2",
                verifiedDaysCount = 14,
                status = EnrollmentStatus.COMPLETED,
                enrolledAtIso = "2025-02-01T10:00:00Z"
            )
        )
    )

    private val verificationsState = MutableStateFlow(
        listOf(
            DailyVerification("v1", "enr_1", 1, "token_d1", "2025-02-15T12:00:00Z"),
            DailyVerification("v2", "enr_1", 2, "token_d2", "2025-02-16T12:00:00Z"),
            DailyVerification("v3", "enr_1", 3, "token_d3", "2025-02-17T12:00:00Z"),
            DailyVerification("v4", "enr_1", 4, "token_d4", "2025-02-18T12:00:00Z")
        )
    )

    override fun getEnrollmentsForTester(testerId: String): Flow<List<Enrollment>> {
        val filtered = MutableStateFlow(enrollmentsState.value.filter { it.testerId == testerId })
        return filtered.asStateFlow()
    }

    override fun getEnrollmentsForApp(appId: String): Flow<List<Enrollment>> {
        val filtered = MutableStateFlow(enrollmentsState.value.filter { it.appId == appId })
        return filtered.asStateFlow()
    }

    override suspend fun getEnrollment(appId: String, testerId: String): Enrollment? {
        return enrollmentsState.value.find { it.appId == appId && it.testerId == testerId }
    }

    override suspend fun enrollInApp(appId: String, testerId: String): Enrollment {
        val existing = getEnrollment(appId, testerId)
        if (existing != null) return existing

        val newEnrollment = Enrollment(
            id = "enr_${enrollmentsState.value.size + 1}",
            appId = appId,
            testerId = testerId,
            verifiedDaysCount = 1,
            status = EnrollmentStatus.ACTIVE,
            enrolledAtIso = "2025-02-19T10:00:00Z"
        )
        enrollmentsState.value = enrollmentsState.value + newEnrollment
        return newEnrollment
    }

    override suspend fun recordDailyVerification(
        enrollmentId: String,
        dayNumber: Int,
        sessionToken: String,
        method: VerificationMethod
    ): DailyVerification {
        val verification = DailyVerification(
            id = "v_${verificationsState.value.size + 1}",
            enrollmentId = enrollmentId,
            dayNumber = dayNumber,
            sessionToken = sessionToken,
            verifiedAtIso = "2025-02-19T14:30:00Z",
            method = method
        )
        verificationsState.value = verificationsState.value + verification

        enrollmentsState.value = enrollmentsState.value.map { enr ->
            if (enr.id == enrollmentId) {
                val newCount = (enr.verifiedDaysCount + 1).coerceAtMost(14)
                val newStatus = if (newCount >= 14) EnrollmentStatus.COMPLETED else EnrollmentStatus.ACTIVE
                enr.copy(verifiedDaysCount = newCount, status = newStatus)
            } else enr
        }

        return verification
    }

    override fun getVerificationsForEnrollment(enrollmentId: String): Flow<List<DailyVerification>> {
        val filtered = MutableStateFlow(verificationsState.value.filter { it.enrollmentId == enrollmentId })
        return filtered.asStateFlow()
    }
}
