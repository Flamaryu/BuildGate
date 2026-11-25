package dev.buildgate.app.pages.dailyqueue

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.buildgate.app.app.navigation.DeepLinkRouter
import dev.buildgate.app.entities.applisting.repository.AppListingRepository
import dev.buildgate.app.entities.enrollment.model.EnrollmentStatus
import dev.buildgate.app.entities.enrollment.repository.EnrollmentRepository
import dev.buildgate.app.features.verifysession.SessionUiEvent
import dev.buildgate.app.features.verifysession.VerifySessionViewModel
import dev.buildgate.app.shared.platform.PlatformVerifier
import dev.buildgate.app.shared.session.QaSessionCoordinator
import dev.buildgate.app.shared.time.BuildGateClock
import dev.buildgate.app.widgets.checkin.DailyCheckInCard
import dev.buildgate.app.widgets.qr.QrHandoffCard
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyQueuePage(
    testerId: String,
    appListingRepository: AppListingRepository,
    enrollmentRepository: EnrollmentRepository,
    verifySessionViewModel: VerifySessionViewModel,
    onOpenTriageForApp: (String) -> Unit,
    onBrowseDiscovery: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allListings by appListingRepository.getListings().collectAsState(initial = emptyList())
    val enrollments by enrollmentRepository.getEnrollmentsForTester(testerId).collectAsState(initial = emptyList())
    val sessionState by verifySessionViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coordinator: QaSessionCoordinator = koinInject()
    val platformVerifier: PlatformVerifier = koinInject()

    val todayUtc = BuildGateClock.todayUtcDate()

    val enrolledApps = remember(allListings, enrollments) {
        enrollments
            .filter { it.status != EnrollmentStatus.COMPLETED }
            .mapNotNull { enr -> allListings.find { it.id == enr.appId } }
    }

    val coordinatorState by coordinator.state.collectAsState()
    var currentIndex by remember { mutableIntStateOf(0) }
    val safeIndex = currentIndex.coerceIn(0, (enrolledApps.size - 1).coerceAtLeast(0))
    val currentQueueApp = enrolledApps.getOrNull(safeIndex)

    // Keep queue UI index in sync if tester advanced directly from the Floating QA Overlay pill
    LaunchedEffect(coordinatorState.currentQueueIndex, enrolledApps.size) {
        if (coordinatorState.queueApps.isNotEmpty() &&
            coordinatorState.currentQueueIndex in enrolledApps.indices
        ) {
            currentIndex = coordinatorState.currentQueueIndex
        }
    }

    LaunchedEffect(enrolledApps) {
        if (enrolledApps.isNotEmpty() && coordinator.state.value.queueApps.map { it.id } != enrolledApps.map { it.id }) {
            coordinator.startQueue(enrolledApps, testerId)
        }
    }

    LaunchedEffect(currentQueueApp?.id, testerId) {
        if (currentQueueApp != null) {
            verifySessionViewModel.loadSessionForApp(currentQueueApp.id, testerId)
        }
    }

    LaunchedEffect(Unit) {
        verifySessionViewModel.eventFlow.collect { event ->
            when (event) {
                is SessionUiEvent.ShowMessage -> snackbarHostState.showSnackbar(event.msg)
                SessionUiEvent.SessionCompleted -> Unit
            }
        }
    }

    val remainingUnverifiedCount = enrollments.count {
        it.status != EnrollmentStatus.COMPLETED && it.lastVerifiedUtcDate != todayUtc
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Daily Testing Queue Playlist",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (remainingUnverifiedCount > 0) {
                                "$remainingUnverifiedCount app(s) left today • Pause-safe bug logging active"
                            } else {
                                "All queued apps verified for today!"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (enrolledApps.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Your Daily Queue is Empty",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Enroll in closed-testing apps from Discovery to step through all your daily 60-second check-ins in one streamlined playlist.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(onClick = onBrowseDiscovery, shape = RoundedCornerShape(12.dp)) {
                                Text("Browse Discovery Hub")
                            }
                        }
                    }
                }
            } else {
                // Queue Playlist Strip
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Queue Step ${safeIndex + 1} of ${enrolledApps.size}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            currentQueueApp?.let {
                                                verifySessionViewModel.pauseSessionForFeedback("Paused from Queue to log bug")
                                                onOpenTriageForApp(it.id)
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("⏸️ Log Bug")
                                    }
                                    Button(
                                        onClick = {
                                            coordinator.advanceToNextQueuedApp()
                                            if (safeIndex + 1 < enrolledApps.size) {
                                                currentIndex = safeIndex + 1
                                            }
                                        },
                                        enabled = !sessionState.isSessionPaused && safeIndex + 1 < enrolledApps.size,
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Next App ➔")
                                    }
                                }
                            }
                        }
                    }
                }

                // Active Queue Card
                if (currentQueueApp != null) {
                    item {
                        if (!sessionState.supportsDeviceVerification) {
                            val shareUrl = DeepLinkRouter.buildShareableCampaignUrl(currentQueueApp.packageName)
                            QrHandoffCard(
                                appTitle = currentQueueApp.title,
                                shareableUrl = shareUrl,
                                androidDeepLink = DeepLinkRouter.buildAndroidDeepLinkUrl(currentQueueApp.packageName),
                                onCopyUrl = { platformVerifier.copyToClipboard("Campaign Link", shareUrl) },
                                onOpenGoogleGroup = { verifySessionViewModel.openGoogleGroupAndMarkJoined() },
                                onOpenPlayOptIn = { verifySessionViewModel.openPlayOptInAndMarkAccepted() }
                            )
                        } else {
                            DailyCheckInCard(
                                app = currentQueueApp,
                                isInstalled = sessionState.isInstalled,
                                installedVersionLabel = sessionState.packageInfo.versionName,
                                hasJoinedGoogleGroup = sessionState.enrollment?.hasJoinedGoogleGroup ?: false,
                                hasAcceptedPlayOptIn = sessionState.enrollment?.hasAcceptedPlayOptIn ?: false,
                                verifiedDaysCount = sessionState.enrollment?.currentStreak ?: 0,
                                isSessionRunning = sessionState.isSessionRunning,
                                isSessionPaused = sessionState.isSessionPaused,
                                isTargetBackgrounded = sessionState.isTargetBackgrounded,
                                secondsRemaining = sessionState.secondsRemaining,
                                isVerifiedToday = sessionState.isVerifiedToday,
                                canDrawOverlays = sessionState.canDrawOverlays,
                                hasUsageStatsPermission = sessionState.hasUsageStatsPermission,
                                missions = sessionState.missions,
                                onToggleMission = { verifySessionViewModel.toggleMission(it) },
                                onStartSessionClick = { verifySessionViewModel.startDailyTestSession() },
                                onOpenGoogleGroupClick = { verifySessionViewModel.openGoogleGroupAndMarkJoined() },
                                onOptInClick = { verifySessionViewModel.openPlayOptInAndMarkAccepted() },
                                onRefreshInstallClick = { verifySessionViewModel.refreshInstallStatus() },
                                onEnableOverlayClick = { verifySessionViewModel.requestOverlayPermission() },
                                onEnableUsageAccessClick = { verifySessionViewModel.requestUsageStatsPermission() }
                            )
                        }
                    }
                }

                // Upcoming Playlist Items
                item {
                    Text(
                        text = "Playlist Overview",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                itemsIndexed(enrolledApps, key = { _, app -> app.id }) { idx, app ->
                    val enr = enrollments.find { it.appId == app.id }
                    val doneToday = enr?.lastVerifiedUtcDate == todayUtc
                    val isSelected = idx == safeIndex

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface
                            )
                            .clickable {
                                if (!sessionState.isSessionPaused) {
                                    currentIndex = idx
                                }
                            }
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "${idx + 1}. ${app.title}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Streak: Day ${enr?.currentStreak ?: 0}/${app.targetDays}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = if (doneToday) "✅ Verified Today" else if (isSelected) "▶️ Current" else "⏳ Queued",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
