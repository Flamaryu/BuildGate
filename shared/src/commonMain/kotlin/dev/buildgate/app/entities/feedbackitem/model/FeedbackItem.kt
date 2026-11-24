package dev.buildgate.app.entities.feedbackitem.model

import dev.buildgate.app.shared.platform.DeviceMetadata
import kotlinx.serialization.Serializable

enum class FeedbackCategory {
    BUG, UX_FRICTION, IDEA
}

enum class FeedbackStatus {
    OPEN, IN_PROGRESS, FIXED, WONT_FIX
}

@Serializable
data class FeedbackItem(
    val id: String,
    val appId: String,
    val authorId: String,
    val buildVersion: String,
    val category: FeedbackCategory,
    val status: FeedbackStatus = FeedbackStatus.OPEN,
    val title: String,
    val description: String,
    val deviceMetadata: DeviceMetadata,
    val upvotes: Int = 0
)
