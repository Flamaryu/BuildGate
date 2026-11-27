package dev.buildgate.app.pages.triageboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.buildgate.app.entities.applisting.model.AppBoostType
import dev.buildgate.app.entities.applisting.model.AppListing
import dev.buildgate.app.entities.feedbackitem.model.FeedbackCategory
import dev.buildgate.app.entities.feedbackitem.model.FeedbackItem
import dev.buildgate.app.entities.feedbackitem.model.FeedbackStatus
import dev.buildgate.app.entities.feedbackitem.ui.FeedbackItemCard
import dev.buildgate.app.entities.testmission.model.TestMission
import dev.buildgate.app.features.managemissions.ManageMissionsViewModel
import dev.buildgate.app.features.managemissions.MissionUiEvent
import dev.buildgate.app.features.managemissions.MissionUiState
import dev.buildgate.app.features.submitfeedback.SubmitFeedbackEvent
import dev.buildgate.app.features.submitfeedback.SubmitFeedbackUiState
import dev.buildgate.app.features.submitfeedback.SubmitFeedbackViewModel
import dev.buildgate.app.features.triagefeedback.TriageFeedbackViewModel
import dev.buildgate.app.features.triagefeedback.TriageUiEvent
import dev.buildgate.app.shared.platform.PlatformVerifier
import dev.buildgate.app.widgets.devicetable.DeviceMetadataTable
import dev.buildgate.app.widgets.kanban.TriageKanbanBoard
import dev.buildgate.app.widgets.retention.RetentionTracker14Day
import org.koin.compose.koinInject

private enum class TriageSubTab(val label: String, val ownerOnly: Boolean) {
    OVERVIEW("Overview", ownerOnly = true),
    ISSUES("Triage Board", ownerOnly = false),
    MISSIONS("QA Missions", ownerOnly = true),
    REPORT("Report Bug", ownerOnly = false),
    HEALTH("14-Day Health", ownerOnly = true)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriageBoardPage(
    appId: String,
    triageViewModel: TriageFeedbackViewModel,
    submitViewModel: SubmitFeedbackViewModel,
    missionsViewModel: ManageMissionsViewModel,
    testerId: String,
    testerUsername: String = "Tester",
    isSignedIn: Boolean = false,
    onOpenProductionPacket: (String) -> Unit = {},
    onEditCampaign: (String) -> Unit = {},
    onPublishNewApp: () -> Unit = {},
    onNavigateToAccount: () -> Unit = {},
    onNavigateToDiscovery: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val triageState by triageViewModel.uiState.collectAsState()
    val submitState by submitViewModel.uiState.collectAsState()
    val missionState by missionsViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val platformVerifier: PlatformVerifier = koinInject()

    var selectedSubTabName by rememberSaveable { mutableStateOf(TriageSubTab.ISSUES.name) }
    val requestedSubTab = TriageSubTab.entries.find { it.name == selectedSubTabName } ?: TriageSubTab.ISSUES
    val activeSubTab = if (requestedSubTab.ownerOnly && !triageState.canTriage) {
        TriageSubTab.ISSUES
    } else {
        requestedSubTab
    }

    LaunchedEffect(testerId, isSignedIn) {
        triageViewModel.setUserContext(userId = testerId, isSignedIn = isSignedIn)
    }

    LaunchedEffect(appId) {
        triageViewModel.selectApp(appId)
    }

    LaunchedEffect(triageState.selectedApp?.id, triageState.selectedApp?.currentBuildVersion) {
        val currentApp = triageState.selectedApp ?: return@LaunchedEffect
        submitViewModel.setAppContext(currentApp.id, currentApp.currentBuildVersion)
        missionsViewModel.selectApp(currentApp.id, currentApp.currentBuildVersion)
    }

    LaunchedEffect(Unit) {
        submitViewModel.eventFlow.collect { event ->
            when (event) {
                is SubmitFeedbackEvent.ShowMessage -> snackbarHostState.showSnackbar(event.msg)
                SubmitFeedbackEvent.SubmissionSuccess -> {
                    selectedSubTabName = TriageSubTab.ISSUES.name
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        triageViewModel.eventFlow.collect { event ->
            when (event) {
                is TriageUiEvent.ShowMessage -> snackbarHostState.showSnackbar(event.msg)
            }
        }
    }

    LaunchedEffect(Unit) {
        missionsViewModel.eventFlow.collect { event ->
            when (event) {
                is MissionUiEvent.ShowMessage -> snackbarHostState.showSnackbar(event.msg)
            }
        }
    }

    val currentApp = triageState.selectedApp
    val bestStreak = triageState.enrollmentsForApp.maxOfOrNull { it.currentStreak } ?: 0

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isCommandCenter = maxWidth >= 900.dp

        Scaffold(
            topBar = {
                if (!isCommandCenter) {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = if (triageState.canTriage) "Developer QA & Triage" else "Known Issues & QA",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = currentApp?.let {
                                        "${it.title} (${it.currentBuildVersion})"
                                    } ?: if (isSignedIn) "Select a published or enrolled app" else "Sign in to manage your QA boards",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        actions = {
                            if (triageState.canTriage && currentApp != null) {
                                TextButton(onClick = { onEditCampaign(currentApp.id) }) {
                                    Text("✏️ Edit")
                                }
                                OutlinedButton(
                                    onClick = { onOpenProductionPacket(currentApp.id) },
                                    modifier = Modifier.padding(end = 12.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Play Packet")
                                }
                            }
                        }
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            if (currentApp == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(20.dp)
                ) {
                    EmptyOrSignedOutTriageState(
                        isSignedIn = isSignedIn,
                        onNavigateToAccount = onNavigateToAccount,
                        onNavigateToDiscovery = onNavigateToDiscovery,
                        onPublishNewApp = onPublishNewApp
                    )
                }
            } else if (isCommandCenter) {
                // =====================================================================
                // WEB / WIDE-SCREEN DEVELOPER COMMAND CENTER (2-PANE WORKSPACE)
                // =====================================================================
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    DesktopAppSwitcherPanel(
                        selectedApp = currentApp,
                        ownedApps = triageState.ownedApps,
                        enrolledApps = triageState.enrolledApps,
                        onSelectApp = { triageViewModel.selectApp(it) },
                        onPublishNewApp = onPublishNewApp
                    )

                    VerticalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 24.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CommandCenterHeader(
                            app = currentApp,
                            canTriage = triageState.canTriage,
                            onEditCampaign = { onEditCampaign(currentApp.id) },
                            onOpenProductionPacket = { onOpenProductionPacket(currentApp.id) }
                        )

                        SubTabRow(
                            activeSubTab = activeSubTab,
                            canTriage = triageState.canTriage,
                            issuesCount = triageState.items.size,
                            missionsCount = missionState.missions.size,
                            onSelectTab = { selectedSubTabName = it.name }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            when (activeSubTab) {
                                TriageSubTab.OVERVIEW -> {
                                    DeveloperOverviewTab(
                                        app = currentApp,
                                        items = triageState.items,
                                        missions = missionState.missions,
                                        enrolledCount = maxOf(currentApp.activeTestersCount, triageState.enrollmentsForApp.size),
                                        bestStreak = bestStreak,
                                        onAddMissionClick = { selectedSubTabName = TriageSubTab.MISSIONS.name },
                                        onEditCampaignClick = { onEditCampaign(currentApp.id) },
                                        onOpenPacketClick = { onOpenProductionPacket(currentApp.id) },
                                        onPurchaseBoost = { boost -> triageViewModel.purchaseAppBoost(boost) },
                                        onInspectIssue = { item ->
                                            triageViewModel.selectItem(item)
                                            selectedSubTabName = TriageSubTab.ISSUES.name
                                        }
                                    )
                                }

                                TriageSubTab.ISSUES -> {
                                    DesktopTriageWorkspace(
                                        currentApp = currentApp,
                                        items = triageState.items,
                                        selectedItem = triageState.selectedItem,
                                        canTriage = triageState.canTriage,
                                        onStatusChange = { id, st -> triageViewModel.updateStatus(id, st) },
                                        onUpvote = { id -> triageViewModel.upvote(id) },
                                        onSelectItem = { item -> triageViewModel.selectItem(item) },
                                        onOpenMediaUrl = { url -> platformVerifier.openExternalUrl(url) }
                                    )
                                }

                                TriageSubTab.MISSIONS -> {
                                    QaMissionsStudio(
                                        isWide = true,
                                        app = currentApp,
                                        missionState = missionState,
                                        missionsViewModel = missionsViewModel
                                    )
                                }

                                TriageSubTab.REPORT -> {
                                    ReportBugTabContent(
                                        isSignedIn = isSignedIn,
                                        submitViewModel = submitViewModel,
                                        submitState = submitState,
                                        testerId = testerId,
                                        testerUsername = testerUsername,
                                        onNavigateToAccount = onNavigateToAccount
                                    )
                                }

                                TriageSubTab.HEALTH -> {
                                    HealthTabContent(
                                        app = currentApp,
                                        bestStreak = bestStreak,
                                        enrolledCount = maxOf(currentApp.activeTestersCount, triageState.enrollmentsForApp.size),
                                        onOpenProductionPacket = { onOpenProductionPacket(currentApp.id) },
                                        onEditCampaign = { onEditCampaign(currentApp.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // =====================================================================
                // MOBILE / COMPACT LAYOUT
                // =====================================================================
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GroupedAppPickerBar(
                        selectedApp = currentApp,
                        canTriage = triageState.canTriage,
                        ownedApps = triageState.ownedApps,
                        enrolledApps = triageState.enrolledApps,
                        onSelectApp = { triageViewModel.selectApp(it) }
                    )

                    SubTabRow(
                        activeSubTab = activeSubTab,
                        canTriage = triageState.canTriage,
                        issuesCount = triageState.items.size,
                        missionsCount = missionState.missions.size,
                        onSelectTab = { selectedSubTabName = it.name }
                    )

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (activeSubTab) {
                            TriageSubTab.OVERVIEW -> {
                                DeveloperOverviewTab(
                                    app = currentApp,
                                    items = triageState.items,
                                    missions = missionState.missions,
                                    enrolledCount = maxOf(currentApp.activeTestersCount, triageState.enrollmentsForApp.size),
                                    bestStreak = bestStreak,
                                    onAddMissionClick = { selectedSubTabName = TriageSubTab.MISSIONS.name },
                                    onEditCampaignClick = { onEditCampaign(currentApp.id) },
                                    onOpenPacketClick = { onOpenProductionPacket(currentApp.id) },
                                    onPurchaseBoost = { boost -> triageViewModel.purchaseAppBoost(boost) },
                                    onInspectIssue = { item ->
                                        triageViewModel.selectItem(item)
                                        selectedSubTabName = TriageSubTab.ISSUES.name
                                    }
                                )
                            }

                            TriageSubTab.ISSUES -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    triageState.selectedItem?.let { sel ->
                                        item(key = "selected_issue_${sel.id}") {
                                            SelectedFeedbackDetailCard(
                                                item = sel,
                                                canTriage = triageState.canTriage,
                                                onOpenMediaUrl = { platformVerifier.openExternalUrl(it) },
                                                onStatusChange = { status -> triageViewModel.updateStatus(sel.id, status) },
                                                onClose = { triageViewModel.selectItem(null) }
                                            )
                                        }
                                    }

                                    if (!triageState.canTriage) {
                                        item(key = "readonly_notice") {
                                            ReadOnlyNoticeBanner()
                                        }
                                    }

                                    if (triageState.items.isEmpty()) {
                                        item(key = "empty_issues") {
                                            EmptyIssuesCard(currentApp.title)
                                        }
                                    } else {
                                        items(triageState.items, key = { it.id }) { item ->
                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                FeedbackItemCard(
                                                    item = item,
                                                    onUpvoteClick = { triageViewModel.upvote(item.id) },
                                                    onCardClick = { triageViewModel.selectItem(item) }
                                                )
                                                if (triageState.canTriage) {
                                                    val (nextStatus, nextLabel) = when (item.status) {
                                                        FeedbackStatus.OPEN -> FeedbackStatus.IN_PROGRESS to "Move to Progress ➔"
                                                        FeedbackStatus.IN_PROGRESS -> FeedbackStatus.FIXED to "Mark Fixed ✅"
                                                        else -> FeedbackStatus.OPEN to "Reopen ↺"
                                                    }
                                                    OutlinedButton(
                                                        onClick = { triageViewModel.updateStatus(item.id, nextStatus) },
                                                        modifier = Modifier.fillMaxWidth(),
                                                        shape = RoundedCornerShape(10.dp)
                                                    ) {
                                                        Text(nextLabel, style = MaterialTheme.typography.labelMedium)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            TriageSubTab.MISSIONS -> {
                                QaMissionsStudio(
                                    isWide = false,
                                    app = currentApp,
                                    missionState = missionState,
                                    missionsViewModel = missionsViewModel
                                )
                            }

                            TriageSubTab.REPORT -> {
                                ReportBugTabContent(
                                    isSignedIn = isSignedIn,
                                    submitViewModel = submitViewModel,
                                    submitState = submitState,
                                    testerId = testerId,
                                    testerUsername = testerUsername,
                                    onNavigateToAccount = onNavigateToAccount
                                )
                            }

                            TriageSubTab.HEALTH -> {
                                HealthTabContent(
                                    app = currentApp,
                                    bestStreak = bestStreak,
                                    enrolledCount = maxOf(currentApp.activeTestersCount, triageState.enrollmentsForApp.size),
                                    onOpenProductionPacket = { onOpenProductionPacket(currentApp.id) },
                                    onEditCampaign = { onEditCampaign(currentApp.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopAppSwitcherPanel(
    selectedApp: AppListing,
    ownedApps: List<AppListing>,
    enrolledApps: List<AppListing>,
    onSelectApp: (String) -> Unit,
    onPublishNewApp: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(264.dp)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "APP WORKSPACE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )

                if (ownedApps.isNotEmpty()) {
                    Text(
                        text = "MY PUBLISHED APPS (${ownedApps.size})",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                    ownedApps.forEach { app ->
                        AppSwitcherTile(
                            app = app,
                            isSelected = app.id == selectedApp.id,
                            roleBadge = "Owner",
                            onClick = { onSelectApp(app.id) }
                        )
                    }
                }

                if (enrolledApps.isNotEmpty()) {
                    if (ownedApps.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                    Text(
                        text = "APPS I'M TESTING (${enrolledApps.size})",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                    enrolledApps.forEach { app ->
                        AppSwitcherTile(
                            app = app,
                            isSelected = app.id == selectedApp.id,
                            roleBadge = "Tester",
                            onClick = { onSelectApp(app.id) }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Button(
                    onClick = onPublishNewApp,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("+ Publish New App")
                }
            }
        }
    }
}

@Composable
private fun AppSwitcherTile(
    app: AppListing,
    isSelected: Boolean,
    roleBadge: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = app.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = app.currentBuildVersion,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${app.activeTestersCount}/${app.targetTesters} testers",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = roleBadge,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (roleBadge == "Owner") MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CommandCenterHeader(
    app: AppListing,
    canTriage: Boolean,
    onEditCampaign: () -> Unit,
    onOpenProductionPacket: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = app.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "${app.currentBuildVersion} · code ${app.minVersionCode}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (canTriage) MaterialTheme.colorScheme.tertiaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (canTriage) "🛠️ Owner Access" else "👀 Read-Only Tester",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (canTriage) MaterialTheme.colorScheme.onTertiaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = "${app.packageName}  •  ${app.category}  •  ${if (app.deepLinkScheme.isNullOrBlank()) "No-Code Verification" else "Deep-Link Handshake"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (canTriage) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onEditCampaign,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("✏️ Edit App & Build")
                }
                Button(
                    onClick = onOpenProductionPacket,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("📦 Play Packet")
                }
            }
        }
    }
}

@Composable
private fun SubTabRow(
    activeSubTab: TriageSubTab,
    canTriage: Boolean,
    issuesCount: Int,
    missionsCount: Int,
    onSelectTab: (TriageSubTab) -> Unit
) {
    val visibleTabs = TriageSubTab.entries.filter { !it.ownerOnly || canTriage }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        visibleTabs.forEach { tab ->
            val displayLabel = when (tab) {
                TriageSubTab.ISSUES -> if (canTriage) "🐞 Triage Board ($issuesCount)" else "👀 Known Issues ($issuesCount)"
                TriageSubTab.MISSIONS -> "🎯 QA Missions ($missionsCount)"
                TriageSubTab.OVERVIEW -> "📊 Overview"
                TriageSubTab.REPORT -> "➕ Report Bug"
                TriageSubTab.HEALTH -> "📈 14-Day Health"
            }
            FilterChip(
                selected = activeSubTab == tab,
                onClick = { onSelectTab(tab) },
                label = { Text(displayLabel) }
            )
        }
    }
}

@Composable
private fun DeveloperOverviewTab(
    app: AppListing,
    items: List<FeedbackItem>,
    missions: List<TestMission>,
    enrolledCount: Int,
    bestStreak: Int,
    onAddMissionClick: () -> Unit,
    onEditCampaignClick: () -> Unit,
    onOpenPacketClick: () -> Unit,
    onPurchaseBoost: (AppBoostType) -> Unit,
    onInspectIssue: (FeedbackItem) -> Unit
) {
    val openIssues = items.filter { it.status == FeedbackStatus.OPEN }
    val fixedIssues = items.filter { it.status == FeedbackStatus.FIXED }
    val requiredMissions = missions.count { it.isRequired }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val isCompactOverview = maxWidth < 640.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // KPI Stat Cards (2x2 grid on mobile, 4-across row on desktop)
            if (isCompactOverview) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        KpiStatCard(
                            label = "Active Testers",
                            value = "$enrolledCount / ${app.targetTesters}",
                            subtitle = "Google Play min: 12",
                            modifier = Modifier.weight(1f)
                        )
                        KpiStatCard(
                            label = "Open Bugs",
                            value = "${openIssues.size}",
                            subtitle = "${fixedIssues.size} fixed · ${items.size} total",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        KpiStatCard(
                            label = "QA Missions",
                            value = "${missions.size}",
                            subtitle = "$requiredMissions req · ${app.currentBuildVersion}",
                            modifier = Modifier.weight(1f)
                        )
                        KpiStatCard(
                            label = "Best Streak",
                            value = "$bestStreak / ${app.targetDays}d",
                            subtitle = "Verified daily check-ins",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    KpiStatCard(
                        label = "Active Testers",
                        value = "$enrolledCount / ${app.targetTesters}",
                        subtitle = "Google Play min: 12",
                        modifier = Modifier.weight(1f)
                    )
                    KpiStatCard(
                        label = "Open Bugs",
                        value = "${openIssues.size}",
                        subtitle = "${fixedIssues.size} fixed · ${items.size} total",
                        modifier = Modifier.weight(1f)
                    )
                    KpiStatCard(
                        label = "QA Missions",
                        value = "${missions.size}",
                        subtitle = "$requiredMissions required for ${app.currentBuildVersion}",
                        modifier = Modifier.weight(1f)
                    )
                    KpiStatCard(
                        label = "Best 14-Day Streak",
                        value = "$bestStreak / ${app.targetDays}d",
                        subtitle = "Verified daily check-ins",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 14-Day Retention Pulse
            RetentionTracker14Day(
                verifiedDaysCount = bestStreak,
                testerName = "$enrolledCount Enrolled Testers · ${app.title}"
            )

            // Quick Actions Bar (stacked on mobile, horizontal row on desktop)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                if (isCompactOverview) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onAddMissionClick,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("🎯 Manage QA Missions")
                        }
                        OutlinedButton(
                            onClick = onEditCampaignClick,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("✏️ Bump Build / Edit Details")
                        }
                        OutlinedButton(
                            onClick = onOpenPacketClick,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("📦 Export Play Packet")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(onClick = onAddMissionClick, shape = RoundedCornerShape(12.dp)) {
                            Text("🎯 Manage QA Missions")
                        }
                        OutlinedButton(onClick = onEditCampaignClick, shape = RoundedCornerShape(12.dp)) {
                            Text("✏️ Bump Build / Edit Details")
                        }
                        OutlinedButton(onClick = onOpenPacketClick, shape = RoundedCornerShape(12.dp)) {
                            Text("📦 Export Play Packet")
                        }
                    }
                }
            }

            // Karma Boosts Store (Spend Karma for High-Impact App Boosts)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "⚡ Karma Boosts Store",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Spend earned Karma to accelerate your 12-tester closed test (Balance: ${app.ownerKarmaBalance}⚡)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    AppBoostType.entries.forEach { boost ->
                        val activeBadge = when (boost) {
                            AppBoostType.FEATURED_SPOTLIGHT -> if (app.isFeaturedSpotlight) "ACTIVE" else null
                            AppBoostType.PRIORITY_BOUNTY_POOL -> if (app.priorityBountyRemaining > 0) "${app.priorityBountyRemaining} left" else null
                            AppBoostType.BUILD_RETEST_PING -> if (!app.retestPingUntilIso.isNullOrBlank()) "PINGED" else null
                            AppBoostType.VETERAN_WAVE -> if (!app.veteranWaveUntilIso.isNullOrBlank()) "ACTIVE" else null
                        }
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = boost.icon, style = MaterialTheme.typography.titleMedium)
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = boost.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (activeBadge != null) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = activeBadge,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = boost.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(
                                    onClick = { onPurchaseBoost(boost) },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("-${boost.costKarma}⚡", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Recent Open Issues Preview
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Recent Open Reports (${openIssues.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (openIssues.isEmpty()) {
                        Text(
                            text = "No open bugs right now — your testers' bug reports will appear here for triage.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        openIssues.take(5).forEach { bug ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onInspectIssue(bug) },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = bug.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${bug.category.name} · ${bug.buildVersion} · ${bug.deviceMetadata.model}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "Inspect ➔",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiStatCard(
    label: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun DesktopTriageWorkspace(
    currentApp: AppListing,
    items: List<FeedbackItem>,
    selectedItem: FeedbackItem?,
    canTriage: Boolean,
    onStatusChange: (String, FeedbackStatus) -> Unit,
    onUpvote: (String) -> Unit,
    onSelectItem: (FeedbackItem?) -> Unit,
    onOpenMediaUrl: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!canTriage) {
            ReadOnlyNoticeBanner()
        }

        if (items.isEmpty()) {
            EmptyIssuesCard(currentApp.title)
        } else {
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TriageKanbanBoard(
                    items = items,
                    canTriage = canTriage,
                    onStatusChange = onStatusChange,
                    onUpvote = onUpvote,
                    onItemSelect = { onSelectItem(it) },
                    modifier = Modifier.weight(1f)
                )

                if (selectedItem != null) {
                    Surface(
                        modifier = Modifier
                            .width(360.dp)
                            .fillMaxHeight(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SelectedFeedbackDetailContent(
                                item = selectedItem,
                                canTriage = canTriage,
                                onOpenMediaUrl = onOpenMediaUrl,
                                onStatusChange = { status -> onStatusChange(selectedItem.id, status) },
                                onClose = { onSelectItem(null) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QaMissionsStudio(
    isWide: Boolean,
    app: AppListing,
    missionState: MissionUiState,
    missionsViewModel: ManageMissionsViewModel
) {
    if (isWide) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Left: Active Missions List
            Column(
                modifier = Modifier
                    .weight(1.15f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Active QA Missions for ${app.title} (${missionState.missions.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (missionState.missions.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "No QA missions yet",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Use the Mission Editor on the right to add concrete test scenarios. Testers see these right inside the Floating QA Overlay.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    missionState.missions.forEach { m ->
                        MissionStudioCard(
                            mission = m,
                            isBeingEdited = missionState.editingMissionId == m.id,
                            onEdit = { missionsViewModel.startEditing(m) },
                            onToggleRequired = { missionsViewModel.toggleMissionRequired(m) },
                            onDelete = { missionsViewModel.deleteMission(m.id) }
                        )
                    }
                }
            }

            // Right: Create / Edit Mission Editor
            Column(
                modifier = Modifier
                    .weight(0.85f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
            ) {
                MissionEditorCard(
                    state = missionState,
                    onTitleChange = { missionsViewModel.onTitleChanged(it) },
                    onBuildVersionChange = { missionsViewModel.onBuildVersionChanged(it) },
                    onInstructionsChange = { missionsViewModel.onInstructionsChanged(it) },
                    onRequiredChange = { missionsViewModel.onRequiredChanged(it) },
                    onSave = { missionsViewModel.saveMission() },
                    onCancelEdit = { missionsViewModel.cancelEditing() }
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MissionEditorCard(
                state = missionState,
                onTitleChange = { missionsViewModel.onTitleChanged(it) },
                onBuildVersionChange = { missionsViewModel.onBuildVersionChanged(it) },
                onInstructionsChange = { missionsViewModel.onInstructionsChanged(it) },
                onRequiredChange = { missionsViewModel.onRequiredChanged(it) },
                onSave = { missionsViewModel.saveMission() },
                onCancelEdit = { missionsViewModel.cancelEditing() }
            )
            missionState.missions.forEach { m ->
                MissionStudioCard(
                    mission = m,
                    isBeingEdited = missionState.editingMissionId == m.id,
                    onEdit = { missionsViewModel.startEditing(m) },
                    onToggleRequired = { missionsViewModel.toggleMissionRequired(m) },
                    onDelete = { missionsViewModel.deleteMission(m.id) }
                )
            }
        }
    }
}

@Composable
private fun MissionStudioCard(
    mission: TestMission,
    isBeingEdited: Boolean,
    onEdit: () -> Unit,
    onToggleRequired: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isBeingEdited) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isBeingEdited) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = mission.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = mission.buildVersion,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (mission.isRequired) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable(onClick = onToggleRequired)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (mission.isRequired) "Required" else "Optional",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (mission.isRequired) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                text = mission.instructions,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onToggleRequired) {
                    Text(if (mission.isRequired) "Set Optional" else "Set Required")
                }
                TextButton(onClick = onEdit) {
                    Text(if (isBeingEdited) "Editing..." else "✏️ Edit")
                }
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("🗑️ Delete")
                }
            }
        }
    }
}

@Composable
private fun MissionEditorCard(
    state: MissionUiState,
    onTitleChange: (String) -> Unit,
    onBuildVersionChange: (String) -> Unit,
    onInstructionsChange: (String) -> Unit,
    onRequiredChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onCancelEdit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (state.isEditing) "✏️ Edit QA Mission" else "🎯 New QA Mission",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (state.isEditing) {
                    TextButton(onClick = onCancelEdit) {
                        Text("Cancel Edit")
                    }
                }
            }

            OutlinedTextField(
                value = state.newTitle,
                onValueChange = onTitleChange,
                label = { Text("Mission Title (min 3 chars)") },
                placeholder = { Text("e.g. Complete Tutorial & Test Pause Recovery") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = state.newBuildVersion,
                onValueChange = onBuildVersionChange,
                label = { Text("Target Build Version") },
                placeholder = { Text("e.g. v1.3.0") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = state.newInstructions,
                onValueChange = onInstructionsChange,
                label = { Text("Step-by-Step Instructions for Testers") },
                placeholder = { Text("Describe exactly what testers should tap, play, or verify...") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Required Mission",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Highlight as priority inside the Floating QA Overlay",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.newIsRequired,
                    onCheckedChange = onRequiredChange
                )
            }

            Button(
                onClick = onSave,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    when {
                        state.isSaving -> "Saving..."
                        state.isEditing -> "Save Mission Changes"
                        else -> "+ Publish QA Mission"
                    }
                )
            }
        }
    }
}

@Composable
private fun ReportBugTabContent(
    isSignedIn: Boolean,
    submitViewModel: SubmitFeedbackViewModel,
    submitState: SubmitFeedbackUiState,
    testerId: String,
    testerUsername: String,
    onNavigateToAccount: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!isSignedIn) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Sign In to Report a Bug",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Sign in so your bug report earns +25⚡ Bounty Karma when the developer accepts it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onNavigateToAccount, shape = RoundedCornerShape(12.dp)) {
                        Text("Go to Account & Sign In")
                    }
                }
            }
        } else {
            FeedbackComposerSection(
                submitViewModel = submitViewModel,
                submitState = submitState,
                onSubmit = { submitViewModel.submit(testerId, testerUsername) }
            )
        }
    }
}

@Composable
private fun HealthTabContent(
    app: AppListing,
    bestStreak: Int,
    enrolledCount: Int,
    onOpenProductionPacket: () -> Unit,
    onEditCampaign: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        RetentionTracker14Day(
            verifiedDaysCount = bestStreak,
            testerName = "$enrolledCount Enrolled Testers · ${app.title}"
        )
        Button(
            onClick = onOpenProductionPacket,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("📦 Open Google Play Production Access Packet")
        }
        OutlinedButton(
            onClick = onEditCampaign,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("✏️ Edit Campaign, Build Version & Deep-Link Settings")
        }
    }
}

@Composable
private fun ReadOnlyNoticeBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = "👀 Read-Only Known Issues — Check existing bugs before filing a duplicate. Only the app owner can change statuses.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyIssuesCard(appTitle: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "No issues reported yet for $appTitle",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Bugs logged from the Floating QA Overlay or the Report Bug tab will appear here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyOrSignedOutTriageState(
    isSignedIn: Boolean,
    onNavigateToAccount: () -> Unit,
    onNavigateToDiscovery: () -> Unit,
    onPublishNewApp: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (isSignedIn) "No Published or Enrolled Apps Yet" else "Sign In for Developer Hub & QA",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (isSignedIn) {
                    "Your Developer Hub shows apps you've published (full QA Missions Studio, Triage Kanban & Build Settings) and apps you're testing (read-only Known Issues)."
                } else {
                    "Sign in to manage QA missions, triage bugs, and update build versions for your published apps."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!isSignedIn) {
                    Button(onClick = onNavigateToAccount, shape = RoundedCornerShape(12.dp)) {
                        Text("Sign In / Create Account")
                    }
                } else {
                    Button(onClick = onPublishNewApp, shape = RoundedCornerShape(12.dp)) {
                        Text("+ Publish Your App")
                    }
                }
                OutlinedButton(onClick = onNavigateToDiscovery, shape = RoundedCornerShape(12.dp)) {
                    Text("Browse Discover")
                }
            }
        }
    }
}

@Composable
private fun GroupedAppPickerBar(
    selectedApp: AppListing,
    canTriage: Boolean,
    ownedApps: List<AppListing>,
    enrolledApps: List<AppListing>,
    onSelectApp: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val totalScoped = ownedApps.size + enrolledApps.size

    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(enabled = totalScoped > 1) { expanded = true },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedApp.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (totalScoped > 1) "Tap to switch app ($totalScoped in your hub) ▾" else selectedApp.packageName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (canTriage) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (canTriage) "🛠️ Owner" else "👀 Read-Only",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (canTriage) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                searchQuery = ""
            }
        ) {
            if (totalScoped > 5) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Filter apps...") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            val q = searchQuery.trim().lowercase()
            val filteredOwned = ownedApps.filter { q.isBlank() || it.title.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
            val filteredEnrolled = enrolledApps.filter { q.isBlank() || it.title.lowercase().contains(q) || it.packageName.lowercase().contains(q) }

            if (filteredOwned.isNotEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "MY PUBLISHED APPS (OWNER)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    onClick = {},
                    enabled = false
                )
                filteredOwned.forEach { app ->
                    DropdownMenuItem(
                        text = { Text("🛠️ ${app.title} (${app.currentBuildVersion})") },
                        onClick = {
                            onSelectApp(app.id)
                            expanded = false
                            searchQuery = ""
                        }
                    )
                }
            }

            if (filteredEnrolled.isNotEmpty()) {
                if (filteredOwned.isNotEmpty()) HorizontalDivider()
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "APPS I'M TESTING (READ-ONLY)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = {},
                    enabled = false
                )
                filteredEnrolled.forEach { app ->
                    DropdownMenuItem(
                        text = { Text("🧪 ${app.title} (${app.currentBuildVersion})") },
                        onClick = {
                            onSelectApp(app.id)
                            expanded = false
                            searchQuery = ""
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedFeedbackDetailCard(
    item: FeedbackItem,
    canTriage: Boolean,
    onOpenMediaUrl: (String) -> Unit,
    onStatusChange: (FeedbackStatus) -> Unit,
    onClose: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SelectedFeedbackDetailContent(
                item = item,
                canTriage = canTriage,
                onOpenMediaUrl = onOpenMediaUrl,
                onStatusChange = onStatusChange,
                onClose = onClose
            )
        }
    }
}

@Composable
private fun SelectedFeedbackDetailContent(
    item: FeedbackItem,
    canTriage: Boolean,
    onOpenMediaUrl: (String) -> Unit,
    onStatusChange: (FeedbackStatus) -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Inspecting: ${item.title}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onClose) { Text("Close ✕") }
    }

    Text(
        text = "${item.category.name} · Build ${item.buildVersion} · By ${item.authorUsername}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary
    )

    Text(text = item.description, style = MaterialTheme.typography.bodyMedium)

    if (item.screenshotUrl != null || item.videoClipUrl != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item.screenshotUrl?.let { url ->
                OutlinedButton(onClick = { onOpenMediaUrl(url) }, shape = RoundedCornerShape(10.dp)) {
                    Text("📸 Open Screenshot")
                }
            }
            item.videoClipUrl?.let { url ->
                OutlinedButton(onClick = { onOpenMediaUrl(url) }, shape = RoundedCornerShape(10.dp)) {
                    Text("🎥 Play Screen Clip")
                }
            }
        }
    }

    DeviceMetadataTable(metadata = item.deviceMetadata)

    if (canTriage) {
        Text(
            text = "Triage Status",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FeedbackStatus.entries.forEach { st ->
                FilterChip(
                    selected = item.status == st,
                    onClick = { onStatusChange(st) },
                    label = { Text(st.name) }
                )
            }
        }
    }
}

@Composable
private fun FeedbackComposerSection(
    submitViewModel: SubmitFeedbackViewModel,
    submitState: SubmitFeedbackUiState,
    onSubmit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Log Bug or UX Friction (+25⚡ Bounty When Triaged)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FeedbackCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = submitState.category == cat,
                        onClick = { submitViewModel.onCategoryChanged(cat) },
                        label = { Text(cat.name.replace("_", " ")) }
                    )
                }
            }

            if (submitState.attachedScreenshotBytes != null || submitState.attachedVideoBytes != null) {
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (submitState.attachedScreenshotBytes != null) {
                            "📸 Overlay Screenshot Ready (${submitState.attachedScreenshotBytes.size / 1024} KB)"
                        } else {
                            "🎥 Overlay ${(submitState.attachedMediaDurationMs ?: 5000) / 1000}s Clip Ready"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    TextButton(onClick = { submitViewModel.clearAttachments() }) {
                        Text("Remove")
                    }
                }
            }

            OutlinedTextField(
                value = submitState.title,
                onValueChange = { submitViewModel.onTitleChanged(it) },
                label = { Text("Issue Title") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = submitState.description,
                onValueChange = { submitViewModel.onDescriptionChanged(it) },
                label = { Text("Reproduction Steps / Expected vs Actual") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                shape = RoundedCornerShape(12.dp)
            )

            submitState.capturedSpecs?.let { specs ->
                Text(
                    text = "📱 Auto-attached Specs: ${specs.model} (${specs.screenResolution})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Button(
                onClick = onSubmit,
                enabled = !submitState.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (submitState.isSubmitting) "Uploading..." else "Submit Report with Hardware Specs")
            }
        }
    }
}
