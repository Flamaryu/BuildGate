package dev.buildgate.app.entities.feedbackitem.model

import dev.buildgate.app.shared.platform.DeviceMetadata
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class FeedbackCategory {
    BUG, UX_FRICTION, IDEA
}

@Serializable
enum class FeedbackStatus {
    OPEN, IN_PROGRESS, FIXED, WONT_FIX, SPAM
}

@Serializable
data class FeedbackItem(
    val id: String,
    @SerialName("app_id") val appId: String,
    @SerialName("author_id") val authorId: String,
    @SerialName("author_username") val authorUsername: String = "Tester",
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("build_version") val buildVersion: String,
    val category: FeedbackCategory,
    val status: FeedbackStatus = FeedbackStatus.OPEN,
    val title: String,
    val description: String,
    @SerialName("device_metadata") val deviceMetadata: DeviceMetadata,
    @SerialName("screenshot_path") val screenshotUrl: String? = null,
    @SerialName("video_clip_path") val videoClipUrl: String? = null,
    @SerialName("media_duration_ms") val mediaDurationMs: Int? = null,
    val upvotes: Int = 0,
    @SerialName("bounty_awarded") val bountyAwarded: Boolean = false,
    @SerialName("created_at") val createdAtIso: String = ""
)
