package dev.buildgate.app.entities.enrollment.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class EnrollmentStatus {
    PENDING_INSTALL,
    ACTIVE,
    COMPLETED,
    DROPPED_UNINSTALLED,
    DROPPED_INACTIVE
}

@Serializable
data class Enrollment(
    val id: String,
    @SerialName("app_id") val appId: String,
    @SerialName("tester_id") val testerId: String,
    @SerialName("has_joined_google_group") val hasJoinedGoogleGroup: Boolean = false,
    @SerialName("has_accepted_play_opt_in") val hasAcceptedPlayOptIn: Boolean = false,
    @SerialName("verified_days_count") val verifiedDaysCount: Int = 0,
    @SerialName("current_streak") val currentStreak: Int = 0,
    @SerialName("missed_days_used") val missedDaysUsed: Int = 0,
    @SerialName("last_verified_utc_date") val lastVerifiedUtcDate: String? = null,
    @SerialName("last_seen_version_code") val lastSeenVersionCode: Long? = null,
    val status: EnrollmentStatus = EnrollmentStatus.ACTIVE,
    @SerialName("enrolled_at") val enrolledAtIso: String = ""
)
