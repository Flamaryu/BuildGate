package dev.buildgate.app.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
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
import dev.buildgate.app.entities.feedbackitem.repository.FeedbackRepository
import dev.buildgate.app.shared.session.QaMediaCaptureBus
import dev.buildgate.app.features.auth.AuthViewModel
import dev.buildgate.app.features.managemissions.ManageMissionsViewModel
import dev.buildgate.app.features.publishapp.PublishAppViewModel
import dev.buildgate.app.features.submitfeedback.SubmitFeedbackViewModel
import dev.buildgate.app.features.triagefeedback.TriageFeedbackViewModel
import dev.buildgate.app.features.verifysession.VerifySessionViewModel
import dev.buildgate.app.pages.account.AccountPage
import dev.buildgate.app.pages.dailyqueue.DailyQueuePage
import dev.buildgate.app.pages.discovery.DiscoveryPage
import dev.buildgate.app.pages.packet.ProductionPacketPage
import dev.buildgate.app.pages.publish.PublishAppPage
import dev.buildgate.app.pages.sessionverification.SessionVerificationPage
import dev.buildgate.app.pages.triageboard.TriageBoardPage
import dev.buildgate.app.app.di.koinScopedViewModel
import dev.buildgate.app.shared.theme.BuildGateTheme
import dev.buildgate.app.shared.ui.BuildGateLogo
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject

@Composable
fun AppShell() {
    KoinApplication(application = { modules(appModule) }) {
        BuildGateTheme {
            val navController = rememberNavController()
            var activeTabName by rememberSaveable { mutableStateOf(TopLevelTab.DISCOVERY.name) }
            val activeTab = TopLevelTab.entries.find { it.name == activeTabName } ?: TopLevelTab.DISCOVERY

            val authViewModel: AuthViewModel = koinScopedViewModel()
            val verifySessionViewModel: VerifySessionViewModel = koinScopedViewModel()
            val triageViewModel: TriageFeedbackViewModel = koinScopedViewModel()
            val submitViewModel: SubmitFeedbackViewModel = koinScopedViewModel()
            val missionsViewModel: ManageMissionsViewModel = koinScopedViewModel()
            val publishAppViewModel: PublishAppViewModel = koinScopedViewModel()

            val appListingRepo: AppListingRepository = koinInject()
            val enrollmentRepo: EnrollmentRepository = koinInject()
            val authRepo: AuthRepository = koinInject()
            val feedbackRepo: FeedbackRepository = koinInject()

            val authSession by authViewModel.sessionState.collectAsState()
            val allListings by appListingRepo.getListings().collectAsState(initial = emptyList())
            val currentUserId = authSession.user?.id ?: if (authSession.isDemoMode) "tester_1" else ""
            val currentUsername = authSession.profile?.username ?: "Tester"

            val incomingLink by DeepLinkRouter.incomingLinks.collectAsState()
            val pendingMediaDraft by QaMediaCaptureBus.pendingDraft.collectAsState()

            // Auto-attach screenshots / 15s clips captured from the Android Floating QA Overlay
            LaunchedEffect(pendingMediaDraft) {
                val draft = pendingMediaDraft ?: return@LaunchedEffect
                submitViewModel.setAppContext(draft.appId, draft.buildVersion, draft.sessionToken)
                if (draft.screenshotBytes != null) {
                    submitViewModel.attachScreenshot(
                        bytes = draft.screenshotBytes,
                        fileName = draft.screenshotFileName ?: "overlay_shot.webp",
                        previewUri = draft.localPreviewUri
                    )
                } else if (draft.videoClipBytes != null) {
                    submitViewModel.attachVideoClip(
                        bytes = draft.videoClipBytes,
                        durationMs = draft.mediaDurationMs ?: 5000,
                        fileName = draft.videoFileName ?: "overlay_clip.mp4",
                        previewUri = draft.localPreviewUri
                    )
                }
                QaMediaCaptureBus.consumeDraft()
            }

            // Persist inline bug reports submitted directly inside the Floating QA Overlay
            LaunchedEffect(currentUserId, currentUsername) {
                QaMediaCaptureBus.overlaySubmissions.collect { sub ->
                    feedbackRepo.submitFeedback(
                        appId = sub.appId,
                        authorId = currentUserId,
                        authorUsername = currentUsername,
                        sessionId = sub.sessionToken,
                        buildVersion = sub.buildVersion,
                        category = sub.category,
                        title = sub.title,
                        description = sub.description,
                        deviceMetadata = sub.deviceMetadata,
                        screenshotBytes = sub.screenshotBytes,
                        screenshotFileName = sub.screenshotFileName,
                        videoClipBytes = sub.videoClipBytes,
                        videoFileName = sub.videoFileName,
                        mediaDurationMs = sub.mediaDurationMs,
                        localPreviewUri = sub.localPreviewUri
                    )
                }
            }

            // Persist mission Pass/Fail checkoffs triggered directly inside the Floating QA Overlay
            LaunchedEffect(Unit) {
                QaMediaCaptureBus.overlayMissionResults.collect { res ->
                    runCatching { appListingRepo.toggleMissionCompletion(res.missionId) }
                }
            }

            // Persist daily verifications triggered directly from the Floating QA Overlay's "Verify & Next" button
            LaunchedEffect(currentUserId) {
                QaMediaCaptureBus.overlayVerifyRequests.collect { req ->
                    val tester = req.testerId.ifBlank { currentUserId }
                    val enrollment = enrollmentRepo.getEnrollment(req.appId, tester)
                        ?: enrollmentRepo.enrollInApp(req.appId, tester)
                    val outcome = enrollmentRepo.recordDailyVerification(
                        enrollmentId = enrollment.id,
                        sessionToken = req.sessionToken,
                        activeDurationSeconds = 60,
                        method = dev.buildgate.app.entities.dailyverification.model.VerificationMethod.ZERO_SDK_SESSION,
                        targetDays = req.targetDays,
                        installedVersionCode = req.installedVersionCode
                    )
                    if (!outcome.alreadyVerifiedToday && outcome.karmaEarned > 0) {
                        authRepo.awardKarma(
                            userId = tester,
                            delta = outcome.karmaEarned,
                            reason = dev.buildgate.app.entities.testerprofile.model.KarmaReason.DAILY_VERIFICATION,
                            appId = req.appId,
                            note = "Day ${outcome.dayNumber}/${req.targetDays} verified via QA Overlay for ${req.appTitle}"
                        )
                    }
                }
            }

            fun navigateTo(route: BuildGateRoute) {
                activeTabName = when (route) {
                    BuildGateRoute.Discovery -> TopLevelTab.DISCOVERY
                    BuildGateRoute.DailyQueue -> TopLevelTab.DAILY_QUEUE
                    is BuildGateRoute.AppCampaign -> TopLevelTab.DISCOVERY
                    is BuildGateRoute.TriageBoard, is BuildGateRoute.ProductionPacket -> TopLevelTab.TRIAGE_BOARD
                    BuildGateRoute.PublishApp, is BuildGateRoute.EditApp -> TopLevelTab.PUBLISH_APP
                    BuildGateRoute.Account -> TopLevelTab.ACCOUNT
                }.name
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
                    TopLevelTab.TRIAGE_BOARD -> {
                        if (!authSession.isLoggedIn) {
                            navigateTo(BuildGateRoute.TriageBoard(""))
                        } else {
                            val defaultAppId = allListings.firstOrNull { it.ownerId == currentUserId }?.id
                                ?: ""
                            navigateTo(BuildGateRoute.TriageBoard(defaultAppId))
                        }
                    }
                    TopLevelTab.PUBLISH_APP -> navigateTo(BuildGateRoute.PublishApp)
                    TopLevelTab.ACCOUNT -> navigateTo(BuildGateRoute.Account)
                }
            }

            val movableNavHost = remember {
                movableContentOf<Modifier> { contentModifier ->
                    val liveAuthSession by authViewModel.sessionState.collectAsState()
                    val liveUserId = liveAuthSession.user?.id ?: if (liveAuthSession.isDemoMode) "tester_1" else ""
                    val liveUsername = liveAuthSession.profile?.username ?: "Tester"

                    NavHost(
                        navController = navController,
                        startDestination = BuildGateRoute.Discovery,
                        modifier = contentModifier
                    ) {
                        composable<BuildGateRoute.Discovery> {
                            DiscoveryPage(
                                appListingRepository = appListingRepo,
                                enrollmentRepository = enrollmentRepo,
                                authSession = liveAuthSession,
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
                                testerId = liveUserId,
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
                                testerId = liveUserId,
                                testerUsername = liveUsername,
                                isSignedIn = liveAuthSession.isLoggedIn,
                                onOpenProductionPacket = { targetAppId ->
                                    navigateTo(BuildGateRoute.ProductionPacket(targetAppId))
                                },
                                onEditCampaign = { targetAppId ->
                                    navigateTo(BuildGateRoute.EditApp(targetAppId))
                                },
                                onPublishNewApp = {
                                    navigateTo(BuildGateRoute.PublishApp)
                                },
                                onNavigateToAccount = {
                                    navigateTo(BuildGateRoute.Account)
                                },
                                onNavigateToDiscovery = {
                                    navigateTo(BuildGateRoute.Discovery)
                                }
                            )
                        }

                        composable<BuildGateRoute.DailyQueue> {
                            DailyQueuePage(
                                testerId = liveUserId,
                                appListingRepository = appListingRepo,
                                enrollmentRepository = enrollmentRepo,
                                verifySessionViewModel = verifySessionViewModel,
                                onOpenTriageForApp = { appId ->
                                    navigateTo(BuildGateRoute.TriageBoard(appId))
                                },
                                onBrowseDiscovery = {
                                    navigateTo(BuildGateRoute.Discovery)
                                }
                            )
                        }

                        composable<BuildGateRoute.PublishApp> {
                            PublishAppPage(
                                ownerId = liveUserId,
                                ownerKarma = liveAuthSession.profile?.karmaPoints ?: 50,
                                isSignedIn = liveAuthSession.isLoggedIn,
                                editAppId = null,
                                viewModel = publishAppViewModel,
                                onPublished = { publishedPkg ->
                                    navigateTo(BuildGateRoute.AppCampaign(publishedPkg))
                                },
                                onNavigateToAccount = {
                                    navigateTo(BuildGateRoute.Account)
                                }
                            )
                        }

                        composable<BuildGateRoute.EditApp> { backStackEntry ->
                            val route: BuildGateRoute.EditApp = backStackEntry.toRoute()
                            PublishAppPage(
                                ownerId = liveUserId,
                                ownerKarma = liveAuthSession.profile?.karmaPoints ?: 50,
                                isSignedIn = liveAuthSession.isLoggedIn,
                                editAppId = route.appId,
                                viewModel = publishAppViewModel,
                                onPublished = { _ ->
                                    navigateTo(BuildGateRoute.TriageBoard(route.appId))
                                },
                                onNavigateToAccount = {
                                    navigateTo(BuildGateRoute.Account)
                                }
                            )
                        }

                        composable<BuildGateRoute.ProductionPacket> { backStackEntry ->
                            val route: BuildGateRoute.ProductionPacket = backStackEntry.toRoute()
                            ProductionPacketPage(
                                appId = route.appId,
                                appListingRepository = appListingRepo,
                                enrollmentRepository = enrollmentRepo,
                                feedbackRepository = feedbackRepo,
                                onNavigateBack = {
                                    navigateTo(BuildGateRoute.TriageBoard(route.appId))
                                }
                            )
                        }

                        composable<BuildGateRoute.Account> {
                            AccountPage(viewModel = authViewModel)
                        }
                    }
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isWideScreen = maxWidth >= 840.dp

                if (isWideScreen) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        DesktopBrandSidebar(
                            activeTab = activeTab,
                            isSignedIn = authSession.isLoggedIn,
                            isDemoMode = authSession.isDemoMode,
                            username = currentUsername,
                            karmaPoints = authSession.profile?.karmaPoints ?: 0,
                            onTabSelected = { onTabSelected(it) }
                        )
                        VerticalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        movableNavHost(Modifier.weight(1f))
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
                        movableNavHost(Modifier.padding(innerPadding))
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopBrandSidebar(
    activeTab: TopLevelTab,
    isSignedIn: Boolean,
    isDemoMode: Boolean,
    username: String,
    karmaPoints: Int,
    onTabSelected: (TopLevelTab) -> Unit
) {
    Surface(
        modifier = Modifier
            .width(220.dp)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BuildGateLogo(size = 34.dp)
                    Column {
                        Text(
                            text = "BuildGate",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Closed-Testing Hub",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(6.dp))

                TopLevelTab.entries.forEach { tab ->
                    val selected = activeTab == tab
                    val label = if (tab == TopLevelTab.TRIAGE_BOARD) "Developer Hub" else tab.title
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer
                                else androidx.compose.ui.graphics.Color.Transparent
                            )
                            .clickable { onTabSelected(tab) }
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = tab.icon,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .clickable { onTabSelected(TopLevelTab.ACCOUNT) }
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isSignedIn) username else "Not Signed In",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isDemoMode) "DEMO" else if (isSignedIn) "🟢 LIVE" else "⚪",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSignedIn && !isDemoMode) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = if (isSignedIn) "⚡ $karmaPoints Karma" else "Click to Sign In",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
