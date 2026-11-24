package dev.buildgate.app.entities.dailyverification.model

import kotlinx.serialization.Serializable

enum class VerificationMethod {
    DEEP_LINK_SDK, MISSION_PROOF
}

@Serializable
data class DailyVerification(
    val id: String,
    val enrollmentId: String,
    val dayNumber: Int,
    val sessionToken: String,
    val verifiedAtIso: String,
    val method: VerificationMethod = VerificationMethod.DEEP_LINK_SDK
)
