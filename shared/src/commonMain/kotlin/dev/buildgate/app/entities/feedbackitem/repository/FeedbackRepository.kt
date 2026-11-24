package dev.buildgate.app.entities.feedbackitem.repository

import dev.buildgate.app.entities.feedbackitem.model.FeedbackCategory
import dev.buildgate.app.entities.feedbackitem.model.FeedbackItem
import dev.buildgate.app.entities.feedbackitem.model.FeedbackStatus
import dev.buildgate.app.shared.platform.DeviceMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

interface FeedbackRepository {
    fun getFeedbackForApp(appId: String): Flow<List<FeedbackItem>>
    suspend fun submitFeedback(
        appId: String,
        authorId: String,
        buildVersion: String,
        category: FeedbackCategory,
        title: String,
        description: String,
        deviceMetadata: DeviceMetadata
    ): FeedbackItem
    suspend fun updateFeedbackStatus(feedbackId: String, newStatus: FeedbackStatus)
    suspend fun upvoteFeedback(feedbackId: String)
}

class InMemoryFeedbackRepository : FeedbackRepository {

    private val feedbackState = MutableStateFlow(
        listOf(
            FeedbackItem(
                id = "fb_1",
                appId = "app_1",
                authorId = "tester_1",
                buildVersion = "v1.0.4-rc1",
                category = FeedbackCategory.BUG,
                status = FeedbackStatus.OPEN,
                title = "App crashes on launch when Bluetooth headset is active",
                description = "Steps to reproduce:\n1. Connect Sony WH-1000XM4 via Bluetooth.\n2. Open FocusPulse Pro.\n3. Screen flickers white and crashes immediately.",
                deviceMetadata = DeviceMetadata(
                    model = "Google Pixel 8 Pro",
                    osVersion = "Android 14 (API 34)",
                    screenResolution = "1344x2992",
                    aspectRatio = "20:9",
                    platformName = "Android Mobile"
                ),
                upvotes = 5
            ),
            FeedbackItem(
                id = "fb_2",
                appId = "app_1",
                authorId = "tester_2",
                buildVersion = "v1.0.4-rc1",
                category = FeedbackCategory.UX_FRICTION,
                status = FeedbackStatus.IN_PROGRESS,
                title = "Habit check-in button touch target is too small",
                description = "The round checkmark button on the daily widget is ~24dp. Hard to tap while walking/exercising.",
                deviceMetadata = DeviceMetadata(
                    model = "Samsung Galaxy S23 Ultra",
                    osVersion = "Android 14 (API 34)",
                    screenResolution = "1440x3088",
                    aspectRatio = "19.3:9",
                    platformName = "Android Mobile"
                ),
                upvotes = 12
            ),
            FeedbackItem(
                id = "fb_3",
                appId = "app_1",
                authorId = "tester_3",
                buildVersion = "v1.0.3",
                category = FeedbackCategory.IDEA,
                status = FeedbackStatus.FIXED,
                title = "Add dark OLED mode toggle in settings",
                description = "Pure black theme option for AMOLED screens during night pomodoro sessions.",
                deviceMetadata = DeviceMetadata(
                    model = "Xiaomi 13 Pro",
                    osVersion = "Android 13 (API 33)",
                    screenResolution = "1440x3200",
                    aspectRatio = "20:9",
                    platformName = "Android Mobile"
                ),
                upvotes = 28
            )
        )
    )

    override fun getFeedbackForApp(appId: String): Flow<List<FeedbackItem>> {
        val filtered = MutableStateFlow(feedbackState.value.filter { it.appId == appId })
        return filtered.asStateFlow()
    }

    override suspend fun submitFeedback(
        appId: String,
        authorId: String,
        buildVersion: String,
        category: FeedbackCategory,
        title: String,
        description: String,
        deviceMetadata: DeviceMetadata
    ): FeedbackItem {
        val item = FeedbackItem(
            id = "fb_${feedbackState.value.size + 1}",
            appId = appId,
            authorId = authorId,
            buildVersion = buildVersion,
            category = category,
            status = FeedbackStatus.OPEN,
            title = title,
            description = description,
            deviceMetadata = deviceMetadata,
            upvotes = 1
        )
        feedbackState.value = listOf(item) + feedbackState.value
        return item
    }

    override suspend fun updateFeedbackStatus(feedbackId: String, newStatus: FeedbackStatus) {
        feedbackState.value = feedbackState.value.map {
            if (it.id == feedbackId) it.copy(status = newStatus) else it
        }
    }

    override suspend fun upvoteFeedback(feedbackId: String) {
        feedbackState.value = feedbackState.value.map {
            if (it.id == feedbackId) it.copy(upvotes = it.upvotes + 1) else it
        }
    }
}
