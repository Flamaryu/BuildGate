package dev.buildgate.app.pages.triageboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import dev.buildgate.app.entities.applisting.model.AppListing
import dev.buildgate.app.entities.feedbackitem.model.FeedbackCategory
import dev.buildgate.app.entities.feedbackitem.model.FeedbackItem
import dev.buildgate.app.entities.feedbackitem.model.FeedbackStatus
import dev.buildgate.app.entities.feedbackitem.ui.FeedbackItemCard
import dev.buildgate.app.features.managemissions.ManageMissionsViewModel
import dev.buildgate.app.features.managemissions.MissionUiEvent
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
    ISSUES("Known Issues", ownerOnly = false),
    REPORT("Report Bug", ownerOnly = false),
    MISSIONS("QA Missions", ownerOnly = true),
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

    Scaffold(
        topBar = {
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
                        TextButton(
                            onClick = { onEditCampaign(currentApp.id) }
                        ) {
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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            val isWideScreen = maxWidth >= 840.dp

            if (currentApp == null) {
                EmptyOrSignedOutTriageState(
                    isSignedIn = isSignedIn,
                    onNavigateToAccount = onNavigateToAccount,
                    onNavigateToDiscovery = onNavigateToDiscovery
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Scalable Grouped App Picker Dropdown (only My Published Apps + Apps I'm Testing)
                    GroupedAppPickerBar(
                        selectedApp = currentApp,
                        canTriage = triageState.canTriage,
                        ownedApps = triageState.ownedApps,
                        enrolledApps = triageState.enrolledApps,
                        onSelectApp = { triageViewModel.selectApp(it) }
                    )

                    // Role-Scoped Sub-Tabs
                    val visibleTabs = TriageSubTab.entries.filter { !it.ownerOnly || triageState.canTriage }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        visibleTabs.forEach { tab ->
                            val badge = when (tab) {
                                TriageSubTab.ISSUES -> " (${triageState.items.size})"
                                TriageSubTab.MISSIONS -> " (${missionState.missions.size})"
                                else -> ""
                            }
                            FilterChip(
                                selected = activeSubTab == tab,
                                onClick = { selectedSubTabName = tab.name },
                                label = { Text("${tab.label}$badge") }
                            )
                        }
                    }

                    // Active Sub-Tab Body
                    when (activeSubTab) {
                        TriageSubTab.ISSUES -> {
                            triageState.selectedItem?.let { sel ->
                                SelectedFeedbackDetailCard(
                                    item = sel,
                                    canTriage = triageState.canTriage,
                                    onOpenMediaUrl = { platformVerifier.openExternalUrl(it) },
                                    onStatusChange = { status -> triageViewModel.updateStatus(sel.id, status) },
                                    onClose = { triageViewModel.selectItem(null) }
                                )
                            }

                            if (!triageState.canTriage) {
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

                            if (triageState.items.isEmpty()) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "No issues reported yet for ${currentApp.title}",
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
                            } else if (isWideScreen) {
                                TriageKanbanBoard(
                                    items = triageState.items,
                                    canTriage = triageState.canTriage,
                                    onStatusChange = { id, status -> triageViewModel.updateStatus(id, status) },
                                    onUpvote = { id -> triageViewModel.upvote(id) },
                                    onItemSelect = { item -> triageViewModel.selectItem(item) },
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(triageState.items, key = { it.id }) { item ->
                                        FeedbackItemCard(
                                            item = item,
                                            onUpvoteClick = { triageViewModel.upvote(item.id) },
                                            onCardClick = { triageViewModel.selectItem(item) }
                                        )
                                    }
                                }
                            }
                        }

                        TriageSubTab.REPORT -> {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
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

                        TriageSubTab.MISSIONS -> {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                MissionComposerCard(
                                    missionTitle = missionState.newTitle,
                                    missionInstructions = missionState.newInstructions,
                                    existingCount = missionState.missions.size,
                                    onTitleChange = { missionsViewModel.onTitleChanged(it) },
                                    onInstructionsChange = { missionsViewModel.onInstructionsChanged(it) },
                                    onPublishMission = { missionsViewModel.addMission() }
                                )
                                missionState.missions.forEach { m ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "${m.title} (${m.buildVersion})",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = m.instructions,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        TriageSubTab.HEALTH -> {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                RetentionTracker14Day(
                                    verifiedDaysCount = bestStreak,
                                    testerName = "${triageState.enrollmentsForApp.size} Enrolled Testers"
                                )
                                Button(
                                    onClick = { onOpenProductionPacket(currentApp.id) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text("📦 Open Google Play Production Access Packet")
                                }
                                OutlinedButton(
                                    onClick = { onEditCampaign(currentApp.id) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text("✏️ Edit Campaign, Build Version & Deep-Link Settings")
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
private fun EmptyOrSignedOutTriageState(
    isSignedIn: Boolean,
    onNavigateToAccount: () -> Unit,
    onNavigateToDiscovery: () -> Unit
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
                text = if (isSignedIn) "No Published or Enrolled Apps Yet" else "Sign In for QA & Triage",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (isSignedIn) {
                    "Your QA Hub only shows apps you've published (full Developer Triage) and apps you're actively testing (read-only Known Issues). Publish an app or enroll in one from Discover to get started."
                } else {
                    "Sign in to triage bugs on your published apps and track known issues for apps you're testing. You can still browse any app's read-only Known Issues directly from Discover."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!isSignedIn) {
                    Button(onClick = onNavigateToAccount, shape = RoundedCornerShape(12.dp)) {
                        Text("Sign In / Create Account")
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

            Text(text = item.description, style = MaterialTheme.typography.bodyMedium)

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

            DeviceMetadataTable(metadata = item.deviceMetadata)

            if (canTriage) {
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
    }
}

@Composable
private fun MissionComposerCard(
    missionTitle: String,
    missionInstructions: String,
    existingCount: Int,
    onTitleChange: (String) -> Unit,
    onInstructionsChange: (String) -> Unit,
    onPublishMission: () -> Unit
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
                text = "🎯 Developer Build Missions ($existingCount Active)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            OutlinedTextField(
                value = missionTitle,
                onValueChange = onTitleChange,
                label = { Text("New QA Mission Title") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = missionInstructions,
                onValueChange = onInstructionsChange,
                label = { Text("Instructions for Testers") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedButton(
                onClick = onPublishMission,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("+ Add Build Mission")
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
