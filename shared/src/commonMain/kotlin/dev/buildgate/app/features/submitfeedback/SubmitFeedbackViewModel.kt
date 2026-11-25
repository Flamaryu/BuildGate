package dev.buildgate.app.features.submitfeedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.buildgate.app.entities.feedbackitem.model.FeedbackCategory
import dev.buildgate.app.entities.feedbackitem.repository.FeedbackRepository
import dev.buildgate.app.shared.platform.DeviceMetadata
import dev.buildgate.app.shared.platform.PlatformVerifier
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SubmitFeedbackUiState(
    val appId: String = "app_1",
    val sessionId: String? = null,
    val buildVersion: String = "v1.0.4-rc1",
    val title: String = "",
    val description: String = "",
    val category: FeedbackCategory = FeedbackCategory.BUG,
    val capturedSpecs: DeviceMetadata? = null,
    val attachedScreenshotName: String? = null,
    val attachedScreenshotBytes: ByteArray? = null,
    val attachedVideoName: String? = null,
    val attachedVideoBytes: ByteArray? = null,
    val attachedMediaDurationMs: Int? = null,
    val localPreviewUri: String? = null,
    val isSubmitting: Boolean = false
)

sealed interface SubmitFeedbackEvent {
    data class ShowMessage(val msg: String) : SubmitFeedbackEvent
    data object SubmissionSuccess : SubmitFeedbackEvent
}

class SubmitFeedbackViewModel(
    private val feedbackRepository: FeedbackRepository,
    private val platformVerifier: PlatformVerifier
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubmitFeedbackUiState())
    val uiState: StateFlow<SubmitFeedbackUiState> = _uiState.asStateFlow()

    private val _eventChannel = Channel<SubmitFeedbackEvent>(Channel.BUFFERED)
    val eventFlow = _eventChannel.receiveAsFlow()

    init {
        val specs = platformVerifier.captureDeviceMetadata()
        _uiState.update { it.copy(capturedSpecs = specs) }
    }

    fun setAppContext(appId: String, buildVersion: String? = null, sessionId: String? = null) {
        _uiState.update {
            it.copy(
                appId = appId,
                buildVersion = buildVersion ?: it.buildVersion,
                sessionId = sessionId ?: it.sessionId
            )
        }
    }

    fun onTitleChanged(t: String) { _uiState.update { it.copy(title = t) } }
    fun onDescriptionChanged(d: String) { _uiState.update { it.copy(description = d) } }
    fun onCategoryChanged(c: FeedbackCategory) { _uiState.update { it.copy(category = c) } }
    fun onBuildVersionChanged(v: String) { _uiState.update { it.copy(buildVersion = v) } }

    fun attachScreenshot(bytes: ByteArray, fileName: String = "screenshot.webp", previewUri: String? = null) {
        _uiState.update {
            it.copy(
                attachedScreenshotBytes = bytes,
                attachedScreenshotName = fileName,
                localPreviewUri = previewUri
            )
        }
    }

    fun attachVideoClip(bytes: ByteArray, durationMs: Int, fileName: String = "clip.mp4", previewUri: String? = null) {
        _uiState.update {
            it.copy(
                attachedVideoBytes = bytes,
                attachedVideoName = fileName,
                attachedMediaDurationMs = durationMs,
                localPreviewUri = previewUri
            )
        }
    }

    fun clearAttachments() {
        _uiState.update {
            it.copy(
                attachedScreenshotBytes = null,
                attachedScreenshotName = null,
                attachedVideoBytes = null,
                attachedVideoName = null,
                attachedMediaDurationMs = null,
                localPreviewUri = null
            )
        }
    }

    fun submit(authorId: String, authorUsername: String = "Tester") {
        val state = _uiState.value
        if (state.title.isBlank() || state.description.isBlank()) {
            viewModelScope.launch {
                _eventChannel.send(SubmitFeedbackEvent.ShowMessage("Please enter both an issue title and description"))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            val specs = state.capturedSpecs ?: platformVerifier.captureDeviceMetadata()

            feedbackRepository.submitFeedback(
                appId = state.appId,
                authorId = authorId,
                authorUsername = authorUsername,
                sessionId = state.sessionId,
                buildVersion = state.buildVersion,
                category = state.category,
                title = state.title,
                description = state.description,
                deviceMetadata = specs,
                screenshotBytes = state.attachedScreenshotBytes,
                screenshotFileName = state.attachedScreenshotName,
                videoClipBytes = state.attachedVideoBytes,
                videoFileName = state.attachedVideoName,
                mediaDurationMs = state.attachedMediaDurationMs,
                localPreviewUri = state.localPreviewUri
            )

            _uiState.update {
                it.copy(
                    isSubmitting = false,
                    title = "",
                    description = "",
                    attachedScreenshotBytes = null,
                    attachedScreenshotName = null,
                    attachedVideoBytes = null,
                    attachedVideoName = null,
                    attachedMediaDurationMs = null,
                    localPreviewUri = null
                )
            }
            _eventChannel.send(SubmitFeedbackEvent.ShowMessage("Feedback logged with hardware specs & media attached!"))
            _eventChannel.send(SubmitFeedbackEvent.SubmissionSuccess)
        }
    }
}
