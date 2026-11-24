package dev.buildgate.app.features.verifysession

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.buildgate.app.entities.applisting.model.AppListing
import dev.buildgate.app.entities.applisting.repository.AppListingRepository
import dev.buildgate.app.entities.dailyverification.model.VerificationMethod
import dev.buildgate.app.entities.enrollment.model.Enrollment
import dev.buildgate.app.entities.enrollment.repository.EnrollmentRepository
import dev.buildgate.app.entities.testmission.model.TestMission
import dev.buildgate.app.shared.platform.PlatformVerifier
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class SessionUiState(
    val selectedApp: AppListing? = null,
    val isInstalled: Boolean = false,
    val enrollment: Enrollment? = null,
    val missions: List<TestMission> = emptyList(),
    val isSessionRunning: Boolean = false,
    val secondsRemaining: Int = 60,
    val sessionToken: String = "",
    val isVerifiedToday: Boolean = false
)

sealed interface SessionUiEvent {
    data class ShowMessage(val msg: String) : SessionUiEvent
    object SessionCompleted : SessionUiEvent
}

class VerifySessionViewModel(
    private val appListingRepository: AppListingRepository,
    private val enrollmentRepository: EnrollmentRepository,
    private val platformVerifier: PlatformVerifier
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    private val _eventChannel = Channel<SessionUiEvent>()
    val eventFlow = _eventChannel.receiveAsFlow()

    private var timerJob: Job? = null

    fun loadSessionForApp(appId: String, testerId: String) {
        viewModelScope.launch {
            val app = appListingRepository.getListingById(appId)
            val isInst = app?.let { platformVerifier.isAppInstalled(it.packageName) } ?: false
            val enrollment = app?.let { enrollmentRepository.enrollInApp(it.id, testerId) }

            _uiState.value = SessionUiState(
                selectedApp = app,
                isInstalled = isInst,
                enrollment = enrollment,
                sessionToken = "bg_sess_${(100000..999999).random()}"
            )

            if (app != null) {
                appListingRepository.getMissionsForApp(app.id).collect { missionsList ->
                    _uiState.value = _uiState.value.copy(missions = missionsList)
                }
            }
        }
    }

    fun startDailyTestSession() {
        val currentApp = _uiState.value.selectedApp ?: return
        val sessionToken = _uiState.value.sessionToken

        // Launch target app via Intent / Platform verifier
        val launched = platformVerifier.launchTargetApp(currentApp.packageName, sessionToken)
        if (launched) {
            viewModelScope.launch {
                _eventChannel.send(SessionUiEvent.ShowMessage("Launching ${currentApp.title}... Keep active for 60s."))
            }
        }

        // Start countdown timer
        timerJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isSessionRunning = true,
            secondsRemaining = 60
        )

        timerJob = viewModelScope.launch {
            while (_uiState.value.secondsRemaining > 0) {
                delay(1000)
                _uiState.value = _uiState.value.copy(
                    secondsRemaining = _uiState.value.secondsRemaining - 1
                )
            }
            // Timer complete -> verify daily check-in
            verifyDailySession()
        }
    }

    fun toggleMission(missionId: String) {
        viewModelScope.launch {
            appListingRepository.toggleMissionCompletion(missionId)
        }
    }

    private fun verifyDailySession() {
        val state = _uiState.value
        val enrollment = state.enrollment ?: return

        viewModelScope.launch {
            enrollmentRepository.recordDailyVerification(
                enrollmentId = enrollment.id,
                dayNumber = enrollment.verifiedDaysCount + 1,
                sessionToken = state.sessionToken,
                method = VerificationMethod.DEEP_LINK_SDK
            )

            _uiState.value = _uiState.value.copy(
                isSessionRunning = false,
                isVerifiedToday = true
            )

            _eventChannel.send(SessionUiEvent.SessionCompleted)
            _eventChannel.send(SessionUiEvent.ShowMessage("🎉 Day ${enrollment.verifiedDaysCount + 1} Verified! +25 Karma Earned!"))
        }
    }
}
