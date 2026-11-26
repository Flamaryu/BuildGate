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
import dev.buildgate.app.shared.platform.InstalledPackageInfo
import dev.buildgate.app.shared.platform.PlatformVerifier
import dev.buildgate.app.shared.session.GlobalQaSession
import dev.buildgate.app.shared.session.QaSessionCoordinator
import dev.buildgate.app.shared.session.QaSessionPhase
import dev.buildgate.app.shared.time.BuildGateClock
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SessionUiState(
    val selectedApp: AppListing? = null,
    val packageInfo: InstalledPackageInfo = InstalledPackageInfo(isInstalled = false),
    val isInstalled: Boolean = false,
    val enrollment: Enrollment? = null,
    val missions: List<TestMission> = emptyList(),
    val isSessionRunning: Boolean = false,
    val isSessionPaused: Boolean = false,
    val isTargetBackgrounded: Boolean = false,
    val secondsRemaining: Int = 60,
    val sessionToken: String = "",
    val isVerifiedToday: Boolean = false,
    val supportsDeviceVerification: Boolean = true,
    val canDrawOverlays: Boolean = false,
    val hasUsageStatsPermission: Boolean = false
)

sealed interface SessionUiEvent {
    data class ShowMessage(val msg: String) : SessionUiEvent
    data object SessionCompleted : SessionUiEvent
}

class VerifySessionViewModel(
    private val appListingRepository: AppListingRepository,
    private val enrollmentRepository: EnrollmentRepository,
    private val authRepository: AuthRepository,
    private val platformVerifier: PlatformVerifier,
    private val sessionCoordinator: QaSessionCoordinator
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SessionUiState(
            supportsDeviceVerification = platformVerifier.supportsDeviceVerification,
            canDrawOverlays = platformVerifier.canDrawOverlays(),
            hasUsageStatsPermission = platformVerifier.hasUsageStatsPermission()
        )
    )
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    private val _eventChannel = Channel<SessionUiEvent>(Channel.BUFFERED)
    val eventFlow = _eventChannel.receiveAsFlow()

    private var tickerJob: Job? = null
    private var observerJob: Job? = null
    private var activeTesterId: String = "tester_1"

    init {
        // Auto-refresh package installation & timer whenever host activity resumes
        viewModelScope.launch {
            GlobalQaSession.resumeTick.collect {
                refreshInstallStatus()
                val snap = sessionCoordinator.evaluateProgress()
                if (snap.phase == QaSessionPhase.READY_TO_VERIFY &&
                    snap.activeApp?.id == _uiState.value.selectedApp?.id
                ) {
                    verifyDailySession(method = VerificationMethod.ZERO_SDK_SESSION)
                }
            }
        }
    }

    fun loadSessionForApp(appIdOrPackage: String, testerId: String) {
        activeTesterId = testerId
        observerJob?.cancel()
        observerJob = viewModelScope.launch {
            val resolved = appListingRepository.getListingById(appIdOrPackage)
            if (resolved == null) {
                _uiState.value = SessionUiState(
                    supportsDeviceVerification = platformVerifier.supportsDeviceVerification,
                    canDrawOverlays = platformVerifier.canDrawOverlays()
                )
                return@launch
            }
            val pkgInfo = platformVerifier.inspectInstalledApp(resolved.packageName)
            if (testerId.isNotBlank()) {
                runCatching { enrollmentRepository.enrollInApp(resolved.id, testerId) }
                if (platformVerifier.supportsDeviceVerification) {
                    runCatching {
                        enrollmentRepository.syncInstallStatus(resolved.id, testerId, pkgInfo.isInstalled, pkgInfo.versionCode)
                    }
                }
            }

            combine(
                appListingRepository.observeListingByIdOrPackage(resolved.id),
                enrollmentRepository.observeEnrollment(resolved.id, testerId),
                appListingRepository.getMissionsForApp(resolved.id),
                sessionCoordinator.state
            ) { app, enrollment, missions, coordState ->
                val actualApp = app ?: resolved
                val currentPkg = platformVerifier.inspectInstalledApp(actualApp.packageName)
                val verifiedToday = enrollment?.lastVerifiedUtcDate == BuildGateClock.todayUtcDate()
                val isActiveForThisApp = coordState.activeApp?.id == actualApp.id &&
                    (coordState.phase == QaSessionPhase.RUNNING_IN_TARGET_APP ||
                     coordState.phase == QaSessionPhase.PAUSED_FOR_FEEDBACK ||
                     coordState.phase == QaSessionPhase.PAUSED_TARGET_BACKGROUNDED)
                val remSec = if (isActiveForThisApp) {
                    coordState.secondsRemaining(platformVerifier.monotonicElapsedMs())
                } else {
                    60
                }

                SessionUiState(
                    selectedApp = actualApp,
                    packageInfo = currentPkg,
                    isInstalled = currentPkg.isInstalled,
                    enrollment = enrollment,
                    missions = missions,
                    isSessionRunning = isActiveForThisApp,
                    isSessionPaused = coordState.phase == QaSessionPhase.PAUSED_FOR_FEEDBACK,
                    isTargetBackgrounded = coordState.phase == QaSessionPhase.PAUSED_TARGET_BACKGROUNDED,
                    secondsRemaining = remSec,
                    sessionToken = coordState.sessionToken.ifBlank {
                        _uiState.value.sessionToken.ifBlank { "bg_sess_${(100000..999999).random()}" }
                    },
                    isVerifiedToday = verifiedToday,
                    supportsDeviceVerification = platformVerifier.supportsDeviceVerification,
                    canDrawOverlays = platformVerifier.canDrawOverlays(),
                    hasUsageStatsPermission = platformVerifier.hasUsageStatsPermission()
                )
            }.collect { combined ->
                _uiState.value = combined
            }
        }
    }

    fun refreshInstallStatus() {
        val app = _uiState.value.selectedApp ?: return
        val pkgInfo = platformVerifier.inspectInstalledApp(app.packageName)
        _uiState.update {
            it.copy(
                packageInfo = pkgInfo,
                isInstalled = pkgInfo.isInstalled,
                canDrawOverlays = platformVerifier.canDrawOverlays(),
                hasUsageStatsPermission = platformVerifier.hasUsageStatsPermission()
            )
        }
        if (platformVerifier.supportsDeviceVerification) {
            viewModelScope.launch {
                enrollmentRepository.syncInstallStatus(app.id, activeTesterId, pkgInfo.isInstalled, pkgInfo.versionCode)
            }
        }
    }

    fun openGoogleGroupAndMarkJoined() {
        val app = _uiState.value.selectedApp ?: return
        platformVerifier.openExternalUrl(app.googleGroupUrl)
        viewModelScope.launch {
            enrollmentRepository.markOnboardingStep(app.id, activeTesterId, joinedGoogleGroup = true)
        }
    }

    fun openPlayOptInAndMarkAccepted() {
        val app = _uiState.value.selectedApp ?: return
        platformVerifier.openExternalUrl(app.playStoreOptInUrl)
        viewModelScope.launch {
            enrollmentRepository.markOnboardingStep(app.id, activeTesterId, acceptedPlayOptIn = true)
        }
    }

    fun markAcceptedPlayOptIn() = openPlayOptInAndMarkAccepted()

    fun requestOverlayPermission() {
        platformVerifier.requestOverlayPermission()
    }

    fun requestUsageStatsPermission() {
        platformVerifier.requestUsageStatsPermission()
    }

    fun pauseSessionForFeedback(reason: String = "Logging QA Bug") {
        sessionCoordinator.pauseForFeedback(reason)
    }

    fun resumeSessionFromFeedback() {
        sessionCoordinator.resumeFromFeedback()
    }

    fun startDailyTestSession() {
        val currentApp = _uiState.value.selectedApp ?: return
        if (!platformVerifier.supportsDeviceVerification) {
            viewModelScope.launch {
                _eventChannel.send(
                    SessionUiEvent.ShowMessage("Daily verification runs on Android devices only — scan or open the deep link on your phone.")
                )
            }
            return
        }
        val pkgInfo = platformVerifier.inspectInstalledApp(currentApp.packageName)
        if (!pkgInfo.isInstalled) {
            viewModelScope.launch {
                _eventChannel.send(SessionUiEvent.ShowMessage("Install ${currentApp.title} from Google Play first"))
            }
            return
        }
        val sessionToken = _uiState.value.sessionToken.ifBlank { "bg_sess_${(100000..999999).random()}" }
        val enrollment = _uiState.value.enrollment

        viewModelScope.launch {
            val session = authRepository.getAuthSession().first()
            if (!session.isLoggedIn) {
                _eventChannel.send(
                    SessionUiEvent.ShowMessage("Please sign in on the Account tab first to start a verified 60s session.")
                )
                return@launch
            }
            val effectiveTesterId = session.user?.id ?: activeTesterId
            if (effectiveTesterId.isNotBlank()) {
                runCatching { enrollmentRepository.enrollInApp(currentApp.id, effectiveTesterId) }
            }

            // Ensure the QA overlay always knows the full remaining daily queue — even when launched
            // from Discovery or after closing the pill via stopSession().
            val existingQueue = sessionCoordinator.state.value.queueApps
            if (existingQueue.isEmpty() || existingQueue.none { it.id == currentApp.id }) {
                val allListings = appListingRepository.getListings().first()
                val testerEnrollments = enrollmentRepository.getEnrollmentsForTester(effectiveTesterId).first()
                val todayUtc = BuildGateClock.todayUtcDate()
                val remainingEnrolled = testerEnrollments
                    .filter {
                        it.status != dev.buildgate.app.entities.enrollment.model.EnrollmentStatus.COMPLETED &&
                            it.lastVerifiedUtcDate != todayUtc &&
                            it.appId != currentApp.id
                    }
                    .mapNotNull { enr -> allListings.find { it.id == enr.appId } }
                val orderedQueue = listOf(currentApp) + remainingEnrolled
                sessionCoordinator.startQueue(orderedQueue, effectiveTesterId)
            }

            sessionCoordinator.startAppSession(
                app = currentApp,
                enrollmentId = enrollment?.id,
                testerId = effectiveTesterId,
                sessionToken = sessionToken,
                targetDurationMs = 60_000L
            )

            val launched = platformVerifier.launchTargetApp(
                packageName = currentApp.packageName,
                sessionToken = sessionToken,
                deepLinkScheme = currentApp.deepLinkScheme
            )
            if (!launched) {
                sessionCoordinator.stopSession()
                _eventChannel.send(SessionUiEvent.ShowMessage("Unable to launch ${currentApp.packageName}"))
                return@launch
            }

            _eventChannel.send(SessionUiEvent.ShowMessage("Testing ${currentApp.title} — QA timer active (60s)"))
            startMonotonicTicker(currentApp.id)
        }
    }

    private fun startMonotonicTicker(expectedAppId: String) {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (true) {
                delay(500)
                val snap = sessionCoordinator.evaluateProgress()
                if (snap.activeApp?.id != expectedAppId) break
                if (snap.phase == QaSessionPhase.RUNNING_IN_TARGET_APP) {
                    val rem = snap.secondsRemaining(platformVerifier.monotonicElapsedMs())
                    _uiState.update { it.copy(secondsRemaining = rem, isSessionRunning = true, isSessionPaused = false, isTargetBackgrounded = false) }
                } else if (snap.phase == QaSessionPhase.PAUSED_FOR_FEEDBACK) {
                    val rem = snap.secondsRemaining(platformVerifier.monotonicElapsedMs())
                    _uiState.update { it.copy(secondsRemaining = rem, isSessionRunning = true, isSessionPaused = true, isTargetBackgrounded = false) }
                } else if (snap.phase == QaSessionPhase.PAUSED_TARGET_BACKGROUNDED) {
                    val rem = snap.secondsRemaining(platformVerifier.monotonicElapsedMs())
                    _uiState.update { it.copy(secondsRemaining = rem, isSessionRunning = true, isSessionPaused = false, isTargetBackgrounded = true) }
                } else if (snap.phase == QaSessionPhase.READY_TO_VERIFY) {
                    verifyDailySession(method = VerificationMethod.ZERO_SDK_SESSION)
                    break
                } else {
                    break
                }
            }
        }
    }

    fun handleDeepLinkCallbackVerification(callbackToken: String) {
        val coordToken = sessionCoordinator.state.value.sessionToken
        val localToken = _uiState.value.sessionToken
        if (callbackToken.isNotBlank() && (callbackToken == coordToken || callbackToken == localToken)) {
            tickerJob?.cancel()
            sessionCoordinator.markVerified(viaDeepLink = true)
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

        sessionCoordinator.markVerified(viaDeepLink = method == VerificationMethod.VERIFIED_DEEPLINK_HANDSHAKE)

        viewModelScope.launch {
            val outcome = enrollmentRepository.recordDailyVerification(
                enrollmentId = enrollment.id,
                sessionToken = state.sessionToken,
                activeDurationSeconds = 60,
                method = method,
                targetDays = app.targetDays,
                installedVersionCode = state.packageInfo.versionCode
            )

            _uiState.update {
                it.copy(
                    isSessionRunning = false,
                    isSessionPaused = false,
                    secondsRemaining = 0,
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
