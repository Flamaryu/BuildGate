package dev.buildgate.app.features.verifysession

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.buildgate.app.entities.applisting.model.AppListing
import dev.buildgate.app.entities.applisting.repository.AppListingRepository
import dev.buildgate.app.entities.auth.repository.AuthRepository
import dev.buildgate.app.entities.dailyverification.model.VerificationMethod
import dev.buildgate.app.entities.enrollment.domain.StreakAndKarmaCalculator
import dev.buildgate.app.entities.enrollment.model.Enrollment
import dev.buildgate.app.entities.enrollment.repository.EnrollmentRepository
import dev.buildgate.app.entities.testerprofile.model.KarmaReason
import dev.buildgate.app.entities.testmission.model.TestMission
import dev.buildgate.app.shared.platform.PlatformVerifier
import dev.buildgate.app.shared.time.BuildGateClock
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SessionUiState(
    val selectedApp: AppListing? = null,
    val isInstalled: Boolean = false,
    val enrollment: Enrollment? = null,
    val missions: List<TestMission> = emptyList(),
    val isSessionRunning: Boolean = false,
    val isSessionPaused: Boolean = false,
    val secondsRemaining: Int = 60,
    val sessionToken: String = "",
    val isVerifiedToday: Boolean = false,
    val supportsDeviceVerification: Boolean = true
)

sealed interface SessionUiEvent {
    data class ShowMessage(val msg: String) : SessionUiEvent
    data object SessionCompleted : SessionUiEvent
}

class VerifySessionViewModel(
    private val appListingRepository: AppListingRepository,
    private val enrollmentRepository: EnrollmentRepository,
    private val authRepository: AuthRepository,
    private val platformVerifier: PlatformVerifier
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    private val _eventChannel = Channel<SessionUiEvent>(Channel.BUFFERED)
    val eventFlow = _eventChannel.receiveAsFlow()

    private var timerJob: Job? = null
    private var observerJob: Job? = null
    private var activeTesterId: String = "tester_1"

    fun loadSessionForApp(appIdOrPackage: String, testerId: String) {
        activeTesterId = testerId
        observerJob?.cancel()
        observerJob = viewModelScope.launch {
            val resolved = appListingRepository.getListingById(appIdOrPackage)
            if (resolved == null) {
                _uiState.value = SessionUiState()
                return@launch
            }
            val isInst = platformVerifier.isAppInstalled(resolved.packageName)
            enrollmentRepository.enrollInApp(resolved.id, testerId)
            enrollmentRepository.syncInstallStatus(resolved.id, testerId, isInst)

            combine(
                appListingRepository.observeListingByIdOrPackage(resolved.id),
                enrollmentRepository.observeEnrollment(resolved.id, testerId),
                appListingRepository.getMissionsForApp(resolved.id)
            ) { app, enrollment, missions ->
                Triple(app ?: resolved, enrollment, missions)
            }.collect { (app, enrollment, missions) ->
                val verifiedToday = enrollment?.lastVerifiedUtcDate == BuildGateClock.todayUtcDate()
                _uiState.update { prev ->
                    prev.copy(
                        selectedApp = app,
                        isInstalled = platformVerifier.isAppInstalled(app.packageName),
                        enrollment = enrollment,
                        missions = missions,
                        sessionToken = prev.sessionToken.ifBlank { "bg_sess_${(100000..999999).random()}" },
                        isVerifiedToday = verifiedToday
                    )
                }
            }
        }
    }

    fun refreshInstallStatus() {
        val app = _uiState.value.selectedApp ?: return
        val installed = platformVerifier.isAppInstalled(app.packageName)
        _uiState.update { it.copy(isInstalled = installed) }
        viewModelScope.launch {
            enrollmentRepository.syncInstallStatus(app.id, activeTesterId, installed)
        }
    }

    fun markJoinedGoogleGroup() {
        val app = _uiState.value.selectedApp ?: return
        viewModelScope.launch {
            enrollmentRepository.markOnboardingStep(app.id, activeTesterId, joinedGoogleGroup = true)
        }
    }

    fun markAcceptedPlayOptIn() {
        val app = _uiState.value.selectedApp ?: return
        viewModelScope.launch {
            enrollmentRepository.markOnboardingStep(app.id, activeTesterId, acceptedPlayOptIn = true)
        }
    }

    fun pauseSessionForFeedback() {
        if (_uiState.value.isSessionRunning) {
            _uiState.update { it.copy(isSessionPaused = true) }
        }
    }

    fun resumeSessionFromFeedback() {
        if (_uiState.value.isSessionRunning) {
            _uiState.update { it.copy(isSessionPaused = false) }
        }
    }

    fun startDailyTestSession() {
        val currentApp = _uiState.value.selectedApp ?: return
        if (!_uiState.value.isInstalled) {
            viewModelScope.launch {
                _eventChannel.send(SessionUiEvent.ShowMessage("Install ${currentApp.title} from Google Play first"))
            }
            return
        }
        val sessionToken = _uiState.value.sessionToken

        val launched = platformVerifier.launchTargetApp(currentApp.packageName, sessionToken)
        if (!launched) {
            viewModelScope.launch {
                _eventChannel.send(SessionUiEvent.ShowMessage("Could not launch ${currentApp.packageName}"))
            }
            return
        }

        viewModelScope.launch {
            _eventChannel.send(SessionUiEvent.ShowMessage("Launched ${currentApp.title} — testing session active (60s)"))
        }

        timerJob?.cancel()
        _uiState.update {
            it.copy(
                isSessionRunning = true,
                isSessionPaused = false,
                secondsRemaining = 60
            )
        }

        timerJob = viewModelScope.launch {
            while (_uiState.value.secondsRemaining > 0) {
                delay(1000)
                if (!_uiState.value.isSessionPaused) {
                    _uiState.update { it.copy(secondsRemaining = (it.secondsRemaining - 1).coerceAtLeast(0)) }
                }
            }
            verifyDailySession(method = VerificationMethod.ZERO_SDK_SESSION)
        }
    }

    fun handleDeepLinkCallbackVerification(callbackToken: String) {
        val state = _uiState.value
        if (state.sessionToken.isNotBlank() && state.sessionToken == callbackToken) {
            timerJob?.cancel()
            verifyDailySession(method = VerificationMethod.VERIFIED_DEEPLINK_HANDSHAKE)
        }
    }

    fun toggleMission(missionId: String) {
        viewModelScope.launch {
            appListingRepository.toggleMissionCompletion(missionId)
        }
    }

    private fun verifyDailySession(method: VerificationMethod) {
        val state = _uiState.value
        val enrollment = state.enrollment ?: return
        val app = state.selectedApp ?: return

        viewModelScope.launch {
            val outcome = enrollmentRepository.recordDailyVerification(
                enrollmentId = enrollment.id,
                sessionToken = state.sessionToken,
                activeDurationSeconds = 60 - state.secondsRemaining.coerceIn(0, 60),
                method = method,
                targetDays = app.targetDays
            )

            _uiState.update {
                it.copy(
                    isSessionRunning = false,
                    isSessionPaused = false,
                    isVerifiedToday = true
                )
            }

            if (!outcome.alreadyVerifiedToday && outcome.karmaEarned > 0) {
                authRepository.awardKarma(
                    userId = enrollment.testerId,
                    delta = StreakAndKarmaCalculator.BASE_DAILY_KARMA,
                    reason = KarmaReason.DAILY_VERIFICATION,
                    appId = app.id,
                    note = "Day ${outcome.dayNumber}/${app.targetDays} check-in for ${app.title}"
                )
                if (method == VerificationMethod.VERIFIED_DEEPLINK_HANDSHAKE) {
                    authRepository.awardKarma(
                        userId = enrollment.testerId,
                        delta = StreakAndKarmaCalculator.DEEPLINK_HANDSHAKE_BONUS_KARMA,
                        reason = KarmaReason.DEEPLINK_VERIFIED_BONUS,
                        appId = app.id,
                        note = "Verified Deep-Link Handshake proof"
                    )
                }
                if (outcome.campaignJustCompleted) {
                    authRepository.awardKarma(
                        userId = enrollment.testerId,
                        delta = StreakAndKarmaCalculator.DAY_14_COMPLETION_BONUS_KARMA,
                        reason = KarmaReason.DAY_14_COMPLETION_BONUS,
                        appId = app.id,
                        note = "14-Day Closed Test Completed for ${app.title}!"
                    )
                }
            }

            _eventChannel.send(SessionUiEvent.SessionCompleted)
            _eventChannel.send(
                SessionUiEvent.ShowMessage(
                    if (outcome.alreadyVerifiedToday) {
                        "Already verified today (Day ${outcome.dayNumber}/${app.targetDays})"
                    } else {
                        "Day ${outcome.dayNumber}/${app.targetDays} Verified! +${outcome.karmaEarned} Karma Earned!"
                    }
                )
            )
        }
    }
}
