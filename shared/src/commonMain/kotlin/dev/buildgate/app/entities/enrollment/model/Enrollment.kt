package dev.buildgate.app.entities.enrollment.model

import kotlinx.serialization.Serializable

enum class EnrollmentStatus {
    ACTIVE, COMPLETED, DROPPED
}

@Serializable
data class Enrollment(
    val id: String,
    val appId: String,
    val testerId: String,
    val verifiedDaysCount: Int = 0,
    val status: EnrollmentStatus = EnrollmentStatus.ACTIVE,
    val enrolledAtIso: String = ""
)
