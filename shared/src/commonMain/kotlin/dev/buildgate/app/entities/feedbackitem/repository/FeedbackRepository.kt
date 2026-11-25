package dev.buildgate.app.entities.feedbackitem.repository

import dev.buildgate.app.entities.feedbackitem.model.FeedbackCategory
import dev.buildgate.app.entities.feedbackitem.model.FeedbackItem
import dev.buildgate.app.entities.feedbackitem.model.FeedbackStatus
import dev.buildgate.app.shared.platform.DeviceMetadata
import dev.buildgate.app.shared.time.BuildGateClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

data class StatusUpdateResult(
    val updatedItem: FeedbackItem?,
    val newlyAwardedBountyToAuthorId: String? = null
)

interface FeedbackRepository {
    fun getFeedbackForApp(appId: String): Flow<List<FeedbackItem>>
    suspend fun submitFeedback(
        appId: String,
        authorId: String,
        authorUsername: String = "Tester",
        sessionId: String? = null,
        buildVersion: String,
        category: FeedbackCategory,
        title: String,
        description: String,
        deviceMetadata: DeviceMetadata,
        screenshotBytes: ByteArray? = null,
        screenshotFileName: String? = null,
        videoClipBytes: ByteArray? = null,
        videoFileName: String? = null,
        mediaDurationMs: Int? = null,
        localPreviewUri: String? = null
    ): FeedbackItem
    suspend fun updateFeedbackStatus(feedbackId: String, newStatus: FeedbackStatus): StatusUpdateResult
    suspend fun upvoteFeedback(feedbackId: String)
}

class DemoFeedbackRepository : FeedbackRepository {

    private val feedbackState = MutableStateFlow(
        listOf(
            FeedbackItem(
                id = "fb_1",
                appId = "app_1",
                authorId = "tester_1",
                authorUsername = "AlexDeveloper",
                buildVersion = "v1.0.4-rc1",
                category = FeedbackCategory.BUG,
                status = FeedbackStatus.OPEN,
                title = "App crashes on launch when Bluetooth headset is active",
                description = "Steps to reproduce:\n1. Connect Sony WH-1000XM4 via Bluetooth.\n2. Open FocusPulse Pro.\n3. Screen flickers white and crashes immediately.",
                deviceMetadata = DeviceMetadata(
                    model = "Google Pixel 8 Pro",
                    osVersion = "Android 15 (API 35)",
                    screenResolution = "1344x2992",
                    aspectRatio = "20:9",
                    platformName = "Android Mobile"
                ),
                upvotes = 5,
                createdAtIso = "2026-11-22T14:10:00Z"
            ),
            FeedbackItem(
                id = "fb_2",
                appId = "app_1",
                authorId = "tester_2",
                authorUsername = "MayaQA",
                buildVersion = "v1.0.4-rc1",
                category = FeedbackCategory.UX_FRICTION,
                status = FeedbackStatus.IN_PROGRESS,
                title = "Habit check-in button touch target is too small",
                description = "The round checkmark button on the daily widget is ~24dp. Hard to tap while walking/exercising.",
                deviceMetadata = DeviceMetadata(
                    model = "Samsung Galaxy S24 Ultra",
                    osVersion = "Android 15 (API 35)",
                    screenResolution = "1440x3120",
                    aspectRatio = "19.5:9",
                    platformName = "Android Mobile"
                ),
                upvotes = 12,
                bountyAwarded = true,
                createdAtIso = "2026-11-21T09:30:00Z"
            ),
            FeedbackItem(
                id = "fb_3",
                appId = "app_1",
                authorId = "tester_3",
                authorUsername = "KenjiBuilds",
                buildVersion = "v1.0.3",
                category = FeedbackCategory.IDEA,
                status = FeedbackStatus.FIXED,
                title = "Add dark OLED mode toggle in settings",
                description = "Pure black theme option for AMOLED screens during night Pomodoro sessions.",
                deviceMetadata = DeviceMetadata(
                    model = "Xiaomi 14 Pro",
                    osVersion = "Android 14 (API 34)",
                    screenResolution = "1440x3200",
                    aspectRatio = "20:9",
                    platformName = "Android Mobile"
                ),
                upvotes = 28,
                bountyAwarded = true,
                createdAtIso = "2026-11-19T18:45:00Z"
            )
        )
    )

    override fun getFeedbackForApp(appId: String): Flow<List<FeedbackItem>> =
        feedbackState.map { list -> list.filter { it.appId == appId } }

    override suspend fun submitFeedback(
        appId: String,
        authorId: String,
        authorUsername: String,
        sessionId: String?,
        buildVersion: String,
        category: FeedbackCategory,
        title: String,
        description: String,
        deviceMetadata: DeviceMetadata,
        screenshotBytes: ByteArray?,
        screenshotFileName: String?,
        videoClipBytes: ByteArray?,
        videoFileName: String?,
        mediaDurationMs: Int?,
        localPreviewUri: String?
    ): FeedbackItem {
        val screenshotTag = when {
            !localPreviewUri.isNullOrBlank() && videoClipBytes == null -> localPreviewUri
            screenshotBytes != null -> "demo://media/screenshot_${feedbackState.value.size + 1}_${screenshotFileName ?: "capture.webp"}"
            else -> null
        }
        val videoTag = when {
            !localPreviewUri.isNullOrBlank() && videoClipBytes != null -> localPreviewUri
            videoClipBytes != null -> "demo://media/clip_${feedbackState.value.size + 1}_${videoFileName ?: "clip.mp4"}"
            else -> null
        }

        val item = FeedbackItem(
            id = "fb_${feedbackState.value.size + 1}",
            appId = appId,
            authorId = authorId,
            authorUsername = authorUsername,
            sessionId = sessionId,
            buildVersion = buildVersion,
            category = category,
            status = FeedbackStatus.OPEN,
            title = title.trim(),
            description = description.trim(),
            deviceMetadata = deviceMetadata,
            screenshotUrl = screenshotTag,
            videoClipUrl = videoTag,
            mediaDurationMs = mediaDurationMs,
            upvotes = 1,
            createdAtIso = BuildGateClock.nowIso()
        )
        feedbackState.update { listOf(item) + it }
        return item
    }

    override suspend fun updateFeedbackStatus(feedbackId: String, newStatus: FeedbackStatus): StatusUpdateResult {
        var awardedAuthor: String? = null
        var updatedRef: FeedbackItem? = null

        feedbackState.update { list ->
            list.map { item ->
                if (item.id == feedbackId) {
                    val shouldAward = !item.bountyAwarded &&
                        (newStatus == FeedbackStatus.IN_PROGRESS || newStatus == FeedbackStatus.FIXED)
                    if (shouldAward) {
                        awardedAuthor = item.authorId
                    }
                    val next = item.copy(
                        status = newStatus,
                        bountyAwarded = item.bountyAwarded || shouldAward
                    )
                    updatedRef = next
                    next
                } else {
                    item
                }
            }
        }
        return StatusUpdateResult(updatedRef, awardedAuthor)
    }

    override suspend fun upvoteFeedback(feedbackId: String) {
        feedbackState.update { list ->
            list.map { if (it.id == feedbackId) it.copy(upvotes = it.upvotes + 1) else it }
        }
    }
}
