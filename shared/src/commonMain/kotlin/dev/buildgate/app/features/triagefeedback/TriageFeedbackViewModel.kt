package dev.buildgate.app.features.triagefeedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.buildgate.app.entities.feedbackitem.model.FeedbackCategory
import dev.buildgate.app.entities.feedbackitem.model.FeedbackItem
import dev.buildgate.app.entities.feedbackitem.model.FeedbackStatus
import dev.buildgate.app.entities.feedbackitem.repository.FeedbackRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TriageUiState(
    val selectedAppId: String = "app_1",
    val items: List<FeedbackItem> = emptyList(),
    val buildFilter: String? = null,
    val categoryFilter: FeedbackCategory? = null,
    val selectedItem: FeedbackItem? = null
)

sealed interface TriageUiEvent {
    data class ShowMessage(val msg: String) : TriageUiEvent
}

class TriageFeedbackViewModel(
    private val feedbackRepository: FeedbackRepository
) : ViewModel() {

    private val _appIdFlow = MutableStateFlow("app_1")
    private val _buildFilter = MutableStateFlow<String?>(null)
    private val _categoryFilter = MutableStateFlow<FeedbackCategory?>(null)
    private val _selectedItem = MutableStateFlow<FeedbackItem?>(null)

    private val _eventChannel = Channel<TriageUiEvent>()
    val eventFlow = _eventChannel.receiveAsFlow()

    val uiState: StateFlow<TriageUiState> = combine(
        feedbackRepository.getFeedbackForApp("app_1"),
        _buildFilter,
        _categoryFilter,
        _selectedItem
    ) { items, build, category, selected ->
        val filtered = items.filter { item ->
            (build == null || item.buildVersion == build) &&
            (category == null || item.category == category)
        }
        TriageUiState(
            selectedAppId = "app_1",
            items = filtered,
            buildFilter = build,
            categoryFilter = category,
            selectedItem = selected
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TriageUiState())

    fun updateStatus(feedbackId: String, newStatus: FeedbackStatus) {
        viewModelScope.launch {
            feedbackRepository.updateFeedbackStatus(feedbackId, newStatus)
            _eventChannel.send(TriageUiEvent.ShowMessage("Status updated to ${newStatus.name}"))
        }
    }

    fun upvote(feedbackId: String) {
        viewModelScope.launch {
            feedbackRepository.upvoteFeedback(feedbackId)
        }
    }

    fun setBuildFilter(version: String?) { _buildFilter.value = version }
    fun setCategoryFilter(category: FeedbackCategory?) { _categoryFilter.value = category }
    fun selectItem(item: FeedbackItem?) { _selectedItem.value = item }
}
