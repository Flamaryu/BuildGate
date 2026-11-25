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

object QaMediaCaptureBus {
    private val _pendingDraft = MutableStateFlow<CapturedQaMediaDraft?>(null)
    val pendingDraft: StateFlow<CapturedQaMediaDraft?> = _pendingDraft.asStateFlow()

    private val _overlaySubmissions = Channel<OverlayBugSubmission>(Channel.BUFFERED)
    val overlaySubmissions = _overlaySubmissions.receiveAsFlow()

    fun publishCapturedDraft(draft: CapturedQaMediaDraft) {
        _pendingDraft.value = draft
    }

    fun consumeDraft() {
        _pendingDraft.value = null
    }

    fun submitFromOverlay(submission: OverlayBugSubmission) {
        _overlaySubmissions.trySend(submission)
    }
}
