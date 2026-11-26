package dev.buildgate.app.shared.session

import dev.buildgate.app.entities.feedbackitem.model.FeedbackCategory
import dev.buildgate.app.shared.platform.DeviceMetadata
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

data class CapturedQaMediaDraft(
    val appId: String,
    val appTitle: String,
    val buildVersion: String,
    val sessionToken: String,
    val screenshotBytes: ByteArray? = null,
    val screenshotFileName: String? = null,
    val videoClipBytes: ByteArray? = null,
    val videoFileName: String? = null,
    val mediaDurationMs: Int? = null,
    val localPreviewUri: String? = null
)

data class OverlayBugSubmission(
    val appId: String,
    val sessionToken: String,
    val buildVersion: String,
    val category: FeedbackCategory,
    val title: String,
    val description: String,
    val deviceMetadata: DeviceMetadata,
    val screenshotBytes: ByteArray? = null,
    val screenshotFileName: String? = null,
    val videoClipBytes: ByteArray? = null,
    val videoFileName: String? = null,
    val mediaDurationMs: Int? = null,
    val localPreviewUri: String? = null
)

data class OverlayVerifyRequest(
    val appId: String,
    val appTitle: String,
    val enrollmentId: String?,
    val testerId: String,
    val sessionToken: String,
    val targetDays: Int,
    val installedVersionCode: Long?
)

data class OverlayMissionResult(
    val missionId: String,
    val appId: String,
    val testerId: String,
    val passed: Boolean
)

object QaMediaCaptureBus {
    private val _pendingDraft = MutableStateFlow<CapturedQaMediaDraft?>(null)
    val pendingDraft: StateFlow<CapturedQaMediaDraft?> = _pendingDraft.asStateFlow()

    private val _overlaySubmissions = Channel<OverlayBugSubmission>(Channel.BUFFERED)
    val overlaySubmissions = _overlaySubmissions.receiveAsFlow()

    private val _overlayVerifyRequests = Channel<OverlayVerifyRequest>(Channel.BUFFERED)
    val overlayVerifyRequests = _overlayVerifyRequests.receiveAsFlow()

    private val _overlayMissionResults = Channel<OverlayMissionResult>(Channel.BUFFERED)
    val overlayMissionResults = _overlayMissionResults.receiveAsFlow()

    fun publishCapturedDraft(draft: CapturedQaMediaDraft) {
        _pendingDraft.value = draft
    }

    fun consumeDraft() {
        _pendingDraft.value = null
    }

    fun submitFromOverlay(submission: OverlayBugSubmission) {
        _overlaySubmissions.trySend(submission)
    }

    fun requestVerifyFromOverlay(request: OverlayVerifyRequest) {
        _overlayVerifyRequests.trySend(request)
    }

    fun publishMissionResult(result: OverlayMissionResult) {
        _overlayMissionResults.trySend(result)
    }
}
