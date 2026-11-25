package dev.buildgate.app.pages.sessionverification

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.buildgate.app.app.navigation.DeepLinkRouter
import dev.buildgate.app.features.verifysession.SessionUiEvent
import dev.buildgate.app.features.verifysession.VerifySessionViewModel
import dev.buildgate.app.shared.platform.PlatformVerifier
import dev.buildgate.app.widgets.checkin.DailyCheckInCard
import dev.buildgate.app.widgets.qr.QrHandoffCard
import dev.buildgate.app.widgets.retention.RetentionTracker14Day
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionVerificationPage(
    appId: String,
    testerId: String,
    viewModel: VerifySessionViewModel,
    onNavigateBack: () -> Unit,
    onOpenTriageBoard: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val platformVerifier: PlatformVerifier = koinInject()

    LaunchedEffect(appId, testerId) {
        viewModel.loadSessionForApp(appId, testerId)
    }

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is SessionUiEvent.ShowMessage -> snackbarHostState.showSnackbar(event.msg)
                SessionUiEvent.SessionCompleted -> Unit
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.selectedApp?.title ?: "Campaign Verification",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        state.selectedApp?.let { app ->
                            Text(
                                text = DeepLinkRouter.buildShareableCampaignUrl(app.packageName),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text("⬅️")
                    }
                },
                actions = {
                    state.selectedApp?.let { app ->
                        TextButton(onClick = { onOpenTriageBoard(app.id) }) {
                            Text("QA & Bugs ➔")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        val selectedApp = state.selectedApp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (selectedApp != null) {
                if (!state.supportsDeviceVerification) {
                    val shareUrl = DeepLinkRouter.buildShareableCampaignUrl(selectedApp.packageName)
                    QrHandoffCard(
                        appTitle = selectedApp.title,
                        shareableUrl = shareUrl,
                        androidDeepLink = DeepLinkRouter.buildAndroidDeepLinkUrl(selectedApp.packageName),
                        onCopyUrl = {
                            platformVerifier.copyToClipboard("Campaign Link", shareUrl)
                            scope.launch { snackbarHostState.showSnackbar("Copied $shareUrl") }
                        },
                        onOpenGoogleGroup = { viewModel.openGoogleGroupAndMarkJoined() },
                        onOpenPlayOptIn = { viewModel.openPlayOptInAndMarkAccepted() }
                    )
                } else {
                    DailyCheckInCard(
                        app = selectedApp,
                        isInstalled = state.isInstalled,
                        installedVersionLabel = state.packageInfo.versionName,
                        hasJoinedGoogleGroup = state.enrollment?.hasJoinedGoogleGroup ?: false,
                        hasAcceptedPlayOptIn = state.enrollment?.hasAcceptedPlayOptIn ?: false,
                        verifiedDaysCount = state.enrollment?.currentStreak ?: 0,
                        isSessionRunning = state.isSessionRunning,
                        isSessionPaused = state.isSessionPaused,
                        isTargetBackgrounded = state.isTargetBackgrounded,
                        secondsRemaining = state.secondsRemaining,
                        isVerifiedToday = state.isVerifiedToday,
                        canDrawOverlays = state.canDrawOverlays,
                        hasUsageStatsPermission = state.hasUsageStatsPermission,
                        missions = state.missions,
                        onToggleMission = { viewModel.toggleMission(it) },
                        onStartSessionClick = { viewModel.startDailyTestSession() },
                        onOpenGoogleGroupClick = { viewModel.openGoogleGroupAndMarkJoined() },
                        onOptInClick = { viewModel.openPlayOptInAndMarkAccepted() },
                        onRefreshInstallClick = { viewModel.refreshInstallStatus() },
                        onEnableOverlayClick = { viewModel.requestOverlayPermission() },
                        onEnableUsageAccessClick = { viewModel.requestUsageStatsPermission() }
                    )
                }

                RetentionTracker14Day(
                    verifiedDaysCount = state.enrollment?.currentStreak ?: 0,
                    testerName = "Your 14-Day Streak"
                )
            } else {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Loading campaign details...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
