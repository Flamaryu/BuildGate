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
    val selectedAppId: String = "",
    val selectedApp: AppListing? = null,
    val ownedApps: List<AppListing> = emptyList(),
    val enrolledApps: List<AppListing> = emptyList(),
    val availableApps: List<AppListing> = emptyList(),
    val enrollmentsForApp: List<Enrollment> = emptyList(),
    val items: List<FeedbackItem> = emptyList(),
    val buildFilter: String? = null,
    val categoryFilter: FeedbackCategory? = null,
    val selectedItem: FeedbackItem? = null,
    val isSignedIn: Boolean = false,
    val currentUserId: String = "",
    val canTriage: Boolean = false
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

    private val _appIdFlow = MutableStateFlow("")
    private val _userContextFlow = MutableStateFlow(Pair("", false)) // (userId, isSignedIn)
    private val _buildFilter = MutableStateFlow<String?>(null)
    private val _categoryFilter = MutableStateFlow<FeedbackCategory?>(null)
    private val _selectedItem = MutableStateFlow<FeedbackItem?>(null)

    private val _eventChannel = Channel<TriageUiEvent>(Channel.BUFFERED)
    val eventFlow = _eventChannel.receiveAsFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val userEnrollmentsFlow = _userContextFlow.flatMapLatest { (uid, signedIn) ->
        if (signedIn && uid.isNotBlank()) {
            enrollmentRepository.getEnrollmentsForTester(uid)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }

    private data class ResolvedScope(
        val currentApp: AppListing?,
        val ownedApps: List<AppListing>,
        val enrolledApps: List<AppListing>,
        val scopedApps: List<AppListing>,
        val userId: String,
        val isSignedIn: Boolean
    )

    private val resolvedScopeFlow = combine(
        _appIdFlow,
        _userContextFlow,
        appListingRepository.getListings(),
        userEnrollmentsFlow
    ) { requestedId, (uid, signedIn), allApps, testerEnrollments ->
        val owned = if (signedIn && uid.isNotBlank()) {
            allApps.filter { it.ownerId == uid }
        } else {
            emptyList()
        }
        val ownedIds = owned.map { it.id }.toSet()
        val enrolled = if (signedIn && uid.isNotBlank()) {
            testerEnrollments
                .mapNotNull { enr -> allApps.find { it.id == enr.appId } }
                .filter { it.id !in ownedIds }
                .distinctBy { it.id }
        } else {
            emptyList()
        }
        val scoped = owned + enrolled

        // If a specific appId/package was requested (e.g., from Discover -> View Known Issues),
        // allow viewing it read-only even if not owned/enrolled. Otherwise default to owned -> enrolled.
        val currentApp = if (requestedId.isNotBlank()) {
            allApps.find { it.id == requestedId || it.packageName == requestedId }
                ?: scoped.firstOrNull()
        } else {
            scoped.firstOrNull()
        }
        ResolvedScope(
            currentApp = currentApp,
            ownedApps = owned,
            enrolledApps = enrolled,
            scopedApps = scoped,
            userId = uid,
            isSignedIn = signedIn
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val appScopedData = resolvedScopeFlow.flatMapLatest { scope ->
        val effectiveId = scope.currentApp?.id
        if (effectiveId.isNullOrBlank()) {
            kotlinx.coroutines.flow.flowOf(Triple(scope, emptyList<FeedbackItem>(), emptyList<Enrollment>()))
        } else {
            combine(
                feedbackRepository.getFeedbackForApp(effectiveId),
                enrollmentRepository.getEnrollmentsForApp(effectiveId)
            ) { feedback, enrollments ->
                Triple(scope, feedback, enrollments)
            }
        }
    }

    val uiState: StateFlow<TriageUiState> = combine(
        appScopedData,
        _buildFilter,
        _categoryFilter,
        _selectedItem
    ) { (scope, items, enrollments), build, category, selected ->
        val filtered = items.filter { item ->
            (build == null || item.buildVersion == build) &&
            (category == null || item.category == category)
        }
        val currentApp = scope.currentApp
        val canTriage = scope.isSignedIn &&
            scope.userId.isNotBlank() &&
            currentApp != null &&
            currentApp.ownerId == scope.userId
        TriageUiState(
            selectedAppId = currentApp?.id ?: _appIdFlow.value,
            selectedApp = currentApp,
            ownedApps = scope.ownedApps,
            enrolledApps = scope.enrolledApps,
            availableApps = scope.scopedApps,
            enrollmentsForApp = enrollments,
            items = filtered,
            buildFilter = build,
            categoryFilter = category,
            selectedItem = selected?.let { sel -> items.find { it.id == sel.id } },
            isSignedIn = scope.isSignedIn,
            currentUserId = scope.userId,
            canTriage = canTriage
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TriageUiState())

    fun setUserContext(userId: String, isSignedIn: Boolean) {
        val next = Pair(userId, isSignedIn)
        if (_userContextFlow.value != next) {
            _userContextFlow.value = next
        }
    }

    fun selectApp(appId: String) {
        if (_appIdFlow.value != appId) {
            _appIdFlow.value = appId
            _buildFilter.value = null
            _selectedItem.value = null
        }
    }

    fun updateStatus(feedbackId: String, newStatus: FeedbackStatus) {
        val current = uiState.value
        if (!current.canTriage) {
            viewModelScope.launch {
                _eventChannel.send(
                    TriageUiEvent.ShowMessage("Only the app developer who uploaded this app can move issue statuses.")
                )
            }
            return
        }
        viewModelScope.launch {
            val result = runCatching {
                feedbackRepository.updateFeedbackStatus(feedbackId, newStatus)
            }.getOrElse { err ->
                val raw = err.message.orEmpty()
                val msg = if (raw.contains("42501") || raw.contains("permission") || raw.contains("owner")) {
                    "Only the app owner can update issue statuses."
                } else {
                    raw.lineSequence().firstOrNull()?.take(120) ?: "Unable to update status right now."
                }
                _eventChannel.send(TriageUiEvent.ShowMessage(msg))
                return@launch
            }
            val bountyAuthor = result.newlyAwardedBountyToAuthorId
            if (bountyAuthor != null) {
                runCatching {
                    authRepository.awardKarma(
                        userId = bountyAuthor,
                        delta = StreakAndKarmaCalculator.TRIAGED_BUG_BOUNTY_KARMA,
                        reason = KarmaReason.TRIAGED_BUG_BOUNTY,
                        appId = current.selectedAppId,
                        note = "Bug report accepted: ${result.updatedItem?.title.orEmpty()}"
                    )
                }
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
        if (!uiState.value.isSignedIn) {
            viewModelScope.launch {
                _eventChannel.send(TriageUiEvent.ShowMessage("Sign in on the Account tab to upvote known issues."))
            }
            return
        }
        viewModelScope.launch {
            runCatching { feedbackRepository.upvoteFeedback(feedbackId) }
                .onFailure {
                    _eventChannel.send(TriageUiEvent.ShowMessage("Could not upvote right now."))
                }
        }
    }

    fun setBuildFilter(version: String?) { _buildFilter.value = version }
    fun setCategoryFilter(category: FeedbackCategory?) { _categoryFilter.value = category }
    fun selectItem(item: FeedbackItem?) { _selectedItem.value = item }
}
