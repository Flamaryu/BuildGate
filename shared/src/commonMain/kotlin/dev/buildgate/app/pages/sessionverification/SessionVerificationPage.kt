package dev.buildgate.app.pages.sessionverification

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.buildgate.app.features.verifysession.SessionUiEvent
import dev.buildgate.app.features.verifysession.VerifySessionViewModel
import dev.buildgate.app.widgets.checkin.DailyCheckInCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionVerificationPage(
    appId: String,
    testerId: String,
    viewModel: VerifySessionViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(appId) {
        viewModel.loadSessionForApp(appId, testerId)
    }

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is SessionUiEvent.ShowMessage -> snackbarHostState.showSnackbar(event.msg)
                SessionUiEvent.SessionCompleted -> { /* verified state updated */ }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Daily Verification Session",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text("⬅️")
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
                .padding(16.dp)
        ) {
            if (selectedApp != null) {
                DailyCheckInCard(
                    app = selectedApp,
                    isInstalled = state.isInstalled,
                    verifiedDaysCount = state.enrollment?.verifiedDaysCount ?: 0,
                    isSessionRunning = state.isSessionRunning,
                    secondsRemaining = state.secondsRemaining,
                    isVerifiedToday = state.isVerifiedToday,
                    missions = state.missions,
                    onToggleMission = { viewModel.toggleMission(it) },
                    onStartSessionClick = { viewModel.startDailyTestSession() },
                    onOptInClick = { /* open opt in */ }
                )
            } else {
                Text(
                    text = "Loading App Verification details...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
