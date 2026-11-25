package dev.buildgate.app.entities.dailyverification.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class VerificationMethod {
    ZERO_SDK_SESSION,
    VERIFIED_DEEPLINK_HANDSHAKE
}

@Serializable
data class DailyVerification(
    val id: String,
    @SerialName("enrollment_id") val enrollmentId: String,
    @SerialName("day_number") val dayNumber: Int,
    @SerialName("session_token") val sessionToken: String,
    @SerialName("verified_utc_date") val verifiedUtcDate: String,
    @SerialName("verified_at") val verifiedAtIso: String,
    @SerialName("active_duration_seconds") val activeDurationSeconds: Int = 60,
    @SerialName("karma_awarded") val karmaAwarded: Int = 10,
    val method: VerificationMethod = VerificationMethod.ZERO_SDK_SESSION
)
