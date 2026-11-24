package dev.buildgate.app.app.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.buildgate.app.app.di.appModule
import dev.buildgate.app.app.navigation.AppDestination
import dev.buildgate.app.entities.applisting.repository.AppListingRepository
import dev.buildgate.app.entities.auth.repository.AuthRepository
import dev.buildgate.app.entities.enrollment.repository.EnrollmentRepository
import dev.buildgate.app.entities.feedbackitem.repository.FeedbackRepository
import dev.buildgate.app.features.submitfeedback.SubmitFeedbackViewModel
import dev.buildgate.app.features.triagefeedback.TriageFeedbackViewModel
import dev.buildgate.app.features.verifysession.VerifySessionViewModel
import dev.buildgate.app.pages.discovery.DiscoveryPage
import dev.buildgate.app.pages.sessionverification.SessionVerificationPage
import dev.buildgate.app.pages.triageboard.TriageBoardPage
import dev.buildgate.app.shared.platform.getPlatformVerifier
import dev.buildgate.app.shared.theme.BuildGateTheme
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject

@Composable
fun AppShell() {
    KoinApplication(application = { modules(appModule) }) {
        BuildGateTheme {
            var currentDestination by remember { mutableStateOf(AppDestination.DISCOVERY) }
            var selectedAppId by remember { mutableStateOf("app_1") }

            val appListingRepo: AppListingRepository = koinInject()
            val enrollmentRepo: EnrollmentRepository = koinInject()
            val authRepo: AuthRepository = koinInject()
            val feedbackRepo: FeedbackRepository = koinInject()

            val verifySessionViewModel = remember {
                VerifySessionViewModel(appListingRepo, enrollmentRepo, getPlatformVerifier())
            }
            val triageViewModel = remember { TriageFeedbackViewModel(feedbackRepo) }
            val submitViewModel = remember { SubmitFeedbackViewModel(feedbackRepo, getPlatformVerifier()) }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isWideScreen = maxWidth >= 800.dp

                if (isWideScreen) {
                    // Wide / Web Screen: Side Navigation Rail + Main Content
                    Row(modifier = Modifier.fillMaxSize()) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ) {
                            AppDestination.entries.forEach { destination ->
                                NavigationRailItem(
                                    selected = currentDestination == destination,
                                    onClick = { currentDestination = destination },
                                    icon = { Text(destination.icon) },
                                    label = { Text(destination.title) }
                                )
                            }
                        }

                        MainContentArea(
                            destination = currentDestination,
                            selectedAppId = selectedAppId,
                            appListingRepo = appListingRepo,
                            enrollmentRepo = enrollmentRepo,
                            authRepo = authRepo,
                            verifySessionViewModel = verifySessionViewModel,
                            triageViewModel = triageViewModel,
                            submitViewModel = submitViewModel,
                            onNavigateToSession = { appId ->
                                selectedAppId = appId
                                currentDestination = AppDestination.SESSION_VERIFICATION
                            },
                            onNavigateBack = {
                                currentDestination = AppDestination.DISCOVERY
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    // Mobile Screen: Bottom Navigation Bar + Main Content
                    Scaffold(
                        bottomBar = {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ) {
                                AppDestination.entries.forEach { destination ->
                                    NavigationBarItem(
                                        selected = currentDestination == destination,
                                        onClick = { currentDestination = destination },
                                        icon = { Text(destination.icon) },
                                        label = { Text(destination.title) }
                                    )
                                }
                            }
                        }
                    ) { innerPadding ->
                        MainContentArea(
                            destination = currentDestination,
                            selectedAppId = selectedAppId,
                            appListingRepo = appListingRepo,
                            enrollmentRepo = enrollmentRepo,
                            authRepo = authRepo,
                            verifySessionViewModel = verifySessionViewModel,
                            triageViewModel = triageViewModel,
                            submitViewModel = submitViewModel,
                            onNavigateToSession = { appId ->
                                selectedAppId = appId
                                currentDestination = AppDestination.SESSION_VERIFICATION
                            },
                            onNavigateBack = {
                                currentDestination = AppDestination.DISCOVERY
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MainContentArea(
    destination: AppDestination,
    selectedAppId: String,
    appListingRepo: AppListingRepository,
    enrollmentRepo: EnrollmentRepository,
    authRepo: AuthRepository,
    verifySessionViewModel: VerifySessionViewModel,
    triageViewModel: TriageFeedbackViewModel,
    submitViewModel: SubmitFeedbackViewModel,
    onNavigateToSession: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (destination) {
        AppDestination.DISCOVERY -> {
            DiscoveryPage(
                appListingRepository = appListingRepo,
                enrollmentRepository = enrollmentRepo,
                authRepository = authRepo,
                onNavigateToSession = onNavigateToSession,
                modifier = modifier
            )
        }
        AppDestination.SESSION_VERIFICATION -> {
            SessionVerificationPage(
                appId = selectedAppId,
                testerId = "tester_1",
                viewModel = verifySessionViewModel,
                onNavigateBack = onNavigateBack,
                modifier = modifier
            )
        }
        AppDestination.TRIAGE_BOARD -> {
            TriageBoardPage(
                triageViewModel = triageViewModel,
                submitViewModel = submitViewModel,
                testerId = "tester_1",
                modifier = modifier
            )
        }
    }
}
