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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.buildgate.app.app.di.appModule
import dev.buildgate.app.app.navigation.BuildGateRoute
import dev.buildgate.app.app.navigation.DeepLinkRouter
import dev.buildgate.app.app.navigation.IncomingDeepLink
import dev.buildgate.app.app.navigation.TopLevelTab
import dev.buildgate.app.app.navigation.getInitialBrowserRouteHash
import dev.buildgate.app.app.navigation.syncBrowserRouteHash
import dev.buildgate.app.entities.applisting.repository.AppListingRepository
import dev.buildgate.app.entities.auth.repository.AuthRepository
import dev.buildgate.app.entities.enrollment.repository.EnrollmentRepository
import dev.buildgate.app.features.auth.AuthViewModel
import dev.buildgate.app.features.managemissions.ManageMissionsViewModel
import dev.buildgate.app.features.submitfeedback.SubmitFeedbackViewModel
import dev.buildgate.app.features.triagefeedback.TriageFeedbackViewModel
import dev.buildgate.app.features.verifysession.VerifySessionViewModel
import dev.buildgate.app.pages.account.AccountPage
import dev.buildgate.app.pages.discovery.DiscoveryPage
import dev.buildgate.app.pages.sessionverification.SessionVerificationPage
import dev.buildgate.app.pages.triageboard.TriageBoardPage
import dev.buildgate.app.shared.theme.BuildGateTheme
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AppShell() {
    KoinApplication(application = { modules(appModule) }) {
        BuildGateTheme {
            val navController = rememberNavController()
            var activeTab by remember { mutableStateOf(TopLevelTab.DISCOVERY) }

            val authViewModel: AuthViewModel = koinViewModel()
            val verifySessionViewModel: VerifySessionViewModel = koinViewModel()
            val triageViewModel: TriageFeedbackViewModel = koinViewModel()
            val submitViewModel: SubmitFeedbackViewModel = koinViewModel()
            val missionsViewModel: ManageMissionsViewModel = koinViewModel()

            val appListingRepo: AppListingRepository = koinInject()
            val enrollmentRepo: EnrollmentRepository = koinInject()
            val authRepo: AuthRepository = koinInject()

            val authSession by authViewModel.sessionState.collectAsState()
            val currentUserId = authSession.user?.id ?: "tester_1"
            val currentUsername = authSession.profile?.username ?: "Tester"

            val incomingLink by DeepLinkRouter.incomingLinks.collectAsState()

            fun navigateTo(route: BuildGateRoute) {
                activeTab = when (route) {
                    BuildGateRoute.Discovery -> TopLevelTab.DISCOVERY
                    BuildGateRoute.DailyQueue -> TopLevelTab.DAILY_QUEUE
                    is BuildGateRoute.AppCampaign -> TopLevelTab.DISCOVERY
                    is BuildGateRoute.TriageBoard, is BuildGateRoute.ProductionPacket -> TopLevelTab.TRIAGE_BOARD
                    BuildGateRoute.PublishApp -> TopLevelTab.PUBLISH_APP
                    BuildGateRoute.Account -> TopLevelTab.ACCOUNT
                }
                syncBrowserRouteHash(DeepLinkRouter.toWebHash(route))
                navController.navigate(route) {
                    launchSingleTop = true
                }
            }

            // Apply initial Web URL hash on first load
            LaunchedEffect(Unit) {
                val initialHash = getInitialBrowserRouteHash()
                if (!initialHash.isNullOrBlank()) {
                    DeepLinkRouter.dispatchUri(initialHash)
                }
            }

            // React to deep links (Android App Links, custom scheme, OAuth callback, Web URL hash)
            LaunchedEffect(incomingLink) {
                when (val link = incomingLink) {
                    is IncomingDeepLink.Navigate -> {
                        navigateTo(link.route)
                        DeepLinkRouter.consume()
                    }
                    is IncomingDeepLink.VerificationCallback -> {
                        verifySessionViewModel.handleDeepLinkCallbackVerification(link.sessionToken)
                        DeepLinkRouter.consume()
                    }
                    is IncomingDeepLink.AuthCallback -> {
                        authRepo.handleAuthCallbackUri(link.rawUri)
                        DeepLinkRouter.consume()
                    }
                    null -> Unit
                }
            }

            fun onTabSelected(tab: TopLevelTab) {
                when (tab) {
                    TopLevelTab.DISCOVERY -> navigateTo(BuildGateRoute.Discovery)
                    TopLevelTab.DAILY_QUEUE -> navigateTo(BuildGateRoute.DailyQueue)
                    TopLevelTab.TRIAGE_BOARD -> navigateTo(BuildGateRoute.TriageBoard("app_1"))
                    TopLevelTab.PUBLISH_APP -> navigateTo(BuildGateRoute.PublishApp)
                    TopLevelTab.ACCOUNT -> navigateTo(BuildGateRoute.Account)
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isWideScreen = maxWidth >= 840.dp

                val navHostContent: @Composable (Modifier) -> Unit = { contentModifier ->
                    NavHost(
                        navController = navController,
                        startDestination = BuildGateRoute.Discovery,
                        modifier = contentModifier
                    ) {
                        composable<BuildGateRoute.Discovery> {
                            DiscoveryPage(
                                appListingRepository = appListingRepo,
                                enrollmentRepository = enrollmentRepo,
                                authSession = authSession,
                                onOpenCampaign = { appIdOrPkg ->
                                    navigateTo(BuildGateRoute.AppCampaign(appIdOrPkg))
                                },
                                onOpenDailyQueue = {
                                    navigateTo(BuildGateRoute.DailyQueue)
                                },
                                onOpenPublishWizard = {
                                    navigateTo(BuildGateRoute.PublishApp)
                                },
                                onOpenAccount = {
                                    navigateTo(BuildGateRoute.Account)
                                }
                            )
                        }

                        composable<BuildGateRoute.AppCampaign> { backStackEntry ->
                            val route: BuildGateRoute.AppCampaign = backStackEntry.toRoute()
                            SessionVerificationPage(
                                appId = route.appIdOrPackage,
                                testerId = currentUserId,
                                viewModel = verifySessionViewModel,
                                onNavigateBack = { navigateTo(BuildGateRoute.Discovery) },
                                onOpenTriageBoard = { resolvedAppId ->
                                    navigateTo(BuildGateRoute.TriageBoard(resolvedAppId))
                                }
                            )
                        }

                        composable<BuildGateRoute.TriageBoard> { backStackEntry ->
                            val route: BuildGateRoute.TriageBoard = backStackEntry.toRoute()
                            TriageBoardPage(
                                appId = route.appId,
                                triageViewModel = triageViewModel,
                                submitViewModel = submitViewModel,
                                missionsViewModel = missionsViewModel,
                                testerId = currentUserId,
                                testerUsername = currentUsername,
                                onOpenProductionPacket = { targetAppId ->
                                    navigateTo(BuildGateRoute.ProductionPacket(targetAppId))
                                }
                            )
                        }

                        // Placeholders wired cleanly in Phases 5-8
                        composable<BuildGateRoute.DailyQueue> {
                            SessionVerificationPage(
                                appId = "app_1",
                                testerId = currentUserId,
                                viewModel = verifySessionViewModel,
                                onNavigateBack = { navigateTo(BuildGateRoute.Discovery) },
                                onOpenTriageBoard = { navigateTo(BuildGateRoute.TriageBoard(it)) }
                            )
                        }

                        composable<BuildGateRoute.PublishApp> {
                            DiscoveryPage(
                                appListingRepository = appListingRepo,
                                enrollmentRepository = enrollmentRepo,
                                authSession = authSession,
                                onOpenCampaign = { navigateTo(BuildGateRoute.AppCampaign(it)) },
                                onOpenDailyQueue = { navigateTo(BuildGateRoute.DailyQueue) },
                                onOpenPublishWizard = {},
                                onOpenAccount = { navigateTo(BuildGateRoute.Account) }
                            )
                        }

                        composable<BuildGateRoute.ProductionPacket> { backStackEntry ->
                            val route: BuildGateRoute.ProductionPacket = backStackEntry.toRoute()
                            TriageBoardPage(
                                appId = route.appId,
                                triageViewModel = triageViewModel,
                                submitViewModel = submitViewModel,
                                missionsViewModel = missionsViewModel,
                                testerId = currentUserId,
                                testerUsername = currentUsername,
                                onOpenProductionPacket = {}
                            )
                        }

                        composable<BuildGateRoute.Account> {
                            AccountPage(viewModel = authViewModel)
                        }
                    }
                }

                if (isWideScreen) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ) {
                            TopLevelTab.entries.forEach { tab ->
                                NavigationRailItem(
                                    selected = activeTab == tab,
                                    onClick = { onTabSelected(tab) },
                                    icon = { Text(tab.icon) },
                                    label = { Text(tab.title) }
                                )
                            }
                        }
                        navHostContent(Modifier.weight(1f))
                    }
                } else {
                    Scaffold(
                        bottomBar = {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ) {
                                TopLevelTab.entries.forEach { tab ->
                                    NavigationBarItem(
                                        selected = activeTab == tab,
                                        onClick = { onTabSelected(tab) },
                                        icon = { Text(tab.icon) },
                                        label = { Text(tab.title) }
                                    )
                                }
                            }
                        }
                    ) { innerPadding ->
                        navHostContent(Modifier.padding(innerPadding))
                    }
                }
            }
        }
    }
}
