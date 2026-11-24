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
import kotlinx.coroutines.launch

data class SubmitFeedbackUiState(
    val appId: String = "app_1",
    val buildVersion: String = "v1.0.4-rc1",
    val title: String = "",
    val description: String = "",
    val category: FeedbackCategory = FeedbackCategory.BUG,
    val capturedSpecs: DeviceMetadata? = null,
    val isSubmitting: Boolean = false
)

sealed interface SubmitFeedbackEvent {
    data class ShowMessage(val msg: String) : SubmitFeedbackEvent
    object SubmissionSuccess : SubmitFeedbackEvent
}

class SubmitFeedbackViewModel(
    private val feedbackRepository: FeedbackRepository,
    private val platformVerifier: PlatformVerifier
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubmitFeedbackUiState())
    val uiState: StateFlow<SubmitFeedbackUiState> = _uiState.asStateFlow()

    private val _eventChannel = Channel<SubmitFeedbackEvent>()
    val eventFlow = _eventChannel.receiveAsFlow()

    init {
        // Auto-capture device metadata on load
        val specs = platformVerifier.captureDeviceMetadata()
        _uiState.value = _uiState.value.copy(capturedSpecs = specs)
    }

    fun onTitleChanged(t: String) { _uiState.value = _uiState.value.copy(title = t) }
    fun onDescriptionChanged(d: String) { _uiState.value = _uiState.value.copy(description = d) }
    fun onCategoryChanged(c: FeedbackCategory) { _uiState.value = _uiState.value.copy(category = c) }
    fun onBuildVersionChanged(v: String) { _uiState.value = _uiState.value.copy(buildVersion = v) }

    fun submit(authorId: String) {
        val state = _uiState.value
        if (state.title.isBlank() || state.description.isBlank()) {
            viewModelScope.launch {
                _eventChannel.send(SubmitFeedbackEvent.ShowMessage("Please enter title and description"))
            }
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true)
            val specs = state.capturedSpecs ?: platformVerifier.captureDeviceMetadata()

            feedbackRepository.submitFeedback(
                appId = state.appId,
                authorId = authorId,
                buildVersion = state.buildVersion,
                category = state.category,
                title = state.title,
                description = state.description,
                deviceMetadata = specs
            )

            _uiState.value = _uiState.value.copy(isSubmitting = false, title = "", description = "")
            _eventChannel.send(SubmitFeedbackEvent.ShowMessage("Feedback submitted with device specs auto-attached!"))
            _eventChannel.send(SubmitFeedbackEvent.SubmissionSuccess)
        }
    }
}
