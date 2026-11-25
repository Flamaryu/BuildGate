package dev.buildgate.app.entities.testerprofile.model

import dev.buildgate.app.shared.platform.DeviceMetadata
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class KarmaReason {
    SIGNUP_WELCOME,
    DAILY_VERIFICATION,
    DEEPLINK_VERIFIED_BONUS,
    TRIAGED_BUG_BOUNTY,
    DAY_14_COMPLETION_BONUS
}

@Serializable
data class KarmaEntry(
    val id: String,
    @SerialName("user_id") val userId: String,
    val delta: Int,
    val reason: KarmaReason,
    @SerialName("app_id") val appId: String? = null,
    val note: String = "",
    @SerialName("created_at") val createdAtIso: String
)

@Serializable
data class TesterProfile(
    val id: String,
    val username: String,
    @SerialName("google_email") val googleEmail: String = "",
    @SerialName("karma_points") val karmaPoints: Int = 50,
    @SerialName("trusted_score") val trustedScore: Double = 5.0,
    @SerialName("completed_campaigns_count") val completedCampaignsCount: Int = 0,
    @SerialName("registered_devices") val registeredDevices: List<DeviceMetadata> = emptyList()
)
