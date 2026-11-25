package dev.buildgate.app.features.triagefeedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.buildgate.app.entities.applisting.model.AppListing
import dev.buildgate.app.entities.applisting.repository.AppListingRepository
import dev.buildgate.app.entities.auth.repository.AuthRepository
import dev.buildgate.app.entities.enrollment.domain.StreakAndKarmaCalculator
import dev.buildgate.app.entities.enrollment.model.Enrollment
import dev.buildgate.app.entities.enrollment.repository.EnrollmentRepository
import dev.buildgate.app.entities.feedbackitem.model.FeedbackCategory
import dev.buildgate.app.entities.feedbackitem.model.FeedbackItem
import dev.buildgate.app.entities.feedbackitem.model.FeedbackStatus
import dev.buildgate.app.entities.feedbackitem.repository.FeedbackRepository
import dev.buildgate.app.entities.testerprofile.model.KarmaReason
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TriageUiState(
    val selectedAppId: String = "app_1",
    val selectedApp: AppListing? = null,
    val availableApps: List<AppListing> = emptyList(),
    val enrollmentsForApp: List<Enrollment> = emptyList(),
    val items: List<FeedbackItem> = emptyList(),
    val buildFilter: String? = null,
    val categoryFilter: FeedbackCategory? = null,
    val selectedItem: FeedbackItem? = null
)

sealed interface TriageUiEvent {
    data class ShowMessage(val msg: String) : TriageUiEvent
}

class TriageFeedbackViewModel(
    private val feedbackRepository: FeedbackRepository,
    private val appListingRepository: AppListingRepository,
    private val enrollmentRepository: EnrollmentRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _appIdFlow = MutableStateFlow("app_1")
    private val _buildFilter = MutableStateFlow<String?>(null)
    private val _categoryFilter = MutableStateFlow<FeedbackCategory?>(null)
    private val _selectedItem = MutableStateFlow<FeedbackItem?>(null)

    private val _eventChannel = Channel<TriageUiEvent>(Channel.BUFFERED)
    val eventFlow = _eventChannel.receiveAsFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val appScopedData = _appIdFlow.flatMapLatest { appId ->
        combine(
            appListingRepository.getListings(),
            feedbackRepository.getFeedbackForApp(appId),
            enrollmentRepository.getEnrollmentsForApp(appId)
        ) { apps, feedback, enrollments ->
            Triple(apps, feedback, enrollments)
        }
    }

    val uiState: StateFlow<TriageUiState> = combine(
        _appIdFlow,
        appScopedData,
        _buildFilter,
        _categoryFilter,
        _selectedItem
    ) { appId, (apps, items, enrollments), build, category, selected ->
        val currentApp = apps.find { it.id == appId || it.packageName == appId } ?: apps.firstOrNull()
        val filtered = items.filter { item ->
            (build == null || item.buildVersion == build) &&
            (category == null || item.category == category)
        }
        TriageUiState(
            selectedAppId = currentApp?.id ?: appId,
            selectedApp = currentApp,
            availableApps = apps,
            enrollmentsForApp = enrollments,
            items = filtered,
            buildFilter = build,
            categoryFilter = category,
            selectedItem = selected?.let { sel -> items.find { it.id == sel.id } }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TriageUiState())

    fun selectApp(appId: String) {
        if (_appIdFlow.value != appId) {
            _appIdFlow.value = appId
            _buildFilter.value = null
            _selectedItem.value = null
        }
    }

    fun updateStatus(feedbackId: String, newStatus: FeedbackStatus) {
        viewModelScope.launch {
            val result = feedbackRepository.updateFeedbackStatus(feedbackId, newStatus)
            val bountyAuthor = result.newlyAwardedBountyToAuthorId
            if (bountyAuthor != null) {
                authRepository.awardKarma(
                    userId = bountyAuthor,
                    delta = StreakAndKarmaCalculator.TRIAGED_BUG_BOUNTY_KARMA,
                    reason = KarmaReason.TRIAGED_BUG_BOUNTY,
                    appId = _appIdFlow.value,
                    note = "Bug report accepted: ${result.updatedItem?.title.orEmpty()}"
                )
                _eventChannel.send(
                    TriageUiEvent.ShowMessage(
                        "Moved to ${newStatus.name} • +${StreakAndKarmaCalculator.TRIAGED_BUG_BOUNTY_KARMA} Karma awarded to reporter!"
                    )
                )
            } else {
                _eventChannel.send(TriageUiEvent.ShowMessage("Status updated to ${newStatus.name}"))
            }
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
