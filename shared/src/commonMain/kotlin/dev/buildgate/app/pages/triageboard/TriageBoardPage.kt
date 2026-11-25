package dev.buildgate.app.pages.triageboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriageBoardPage(
    appId: String,
    triageViewModel: TriageFeedbackViewModel,
    submitViewModel: SubmitFeedbackViewModel,
    missionsViewModel: ManageMissionsViewModel,
    testerId: String,
    testerUsername: String = "Tester",
    onOpenProductionPacket: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val triageState by triageViewModel.uiState.collectAsState()
    val submitState by submitViewModel.uiState.collectAsState()
    val missionState by missionsViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val platformVerifier: PlatformVerifier = koinInject()

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
                SubmitFeedbackEvent.SubmissionSuccess -> Unit
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
                            text = "Triage, Missions & QA Hub",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${currentApp?.title ?: "App"} (${currentApp?.currentBuildVersion ?: "v1.0.0"})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    currentApp?.let { app ->
                        OutlinedButton(
                            onClick = { onOpenProductionPacket(app.id) },
                            modifier = Modifier.padding(end = 12.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Export Play Packet")
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

            if (isWideScreen) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(2f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AppSwitcherStrip(
                            apps = triageState.availableApps,
                            selectedAppId = triageState.selectedAppId,
                            onSelectApp = { triageViewModel.selectApp(it) }
                        )

                        triageState.selectedItem?.let { sel ->
                            SelectedFeedbackDetailCard(
                                item = sel,
                                onOpenMediaUrl = { platformVerifier.openExternalUrl(it) },
                                onStatusChange = { status -> triageViewModel.updateStatus(sel.id, status) },
                                onClose = { triageViewModel.selectItem(null) }
                            )
                        }

                        TriageKanbanBoard(
                            items = triageState.items,
                            onStatusChange = { id, status -> triageViewModel.updateStatus(id, status) },
                            onUpvote = { id -> triageViewModel.upvote(id) },
                            onItemSelect = { item -> triageViewModel.selectItem(item) }
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        RetentionTracker14Day(
                            verifiedDaysCount = bestStreak,
                            testerName = "${triageState.enrollmentsForApp.size} Enrolled Testers"
                        )

                        MissionComposerCard(
                            missionTitle = missionState.newTitle,
                            missionInstructions = missionState.newInstructions,
                            existingCount = missionState.missions.size,
                            onTitleChange = { missionsViewModel.onTitleChanged(it) },
                            onInstructionsChange = { missionsViewModel.onInstructionsChanged(it) },
                            onPublishMission = { missionsViewModel.addMission() }
                        )

                        FeedbackComposerSection(
                            submitViewModel = submitViewModel,
                            submitState = submitState,
                            onSubmit = { submitViewModel.submit(testerId, testerUsername) }
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        AppSwitcherStrip(
                            apps = triageState.availableApps,
                            selectedAppId = triageState.selectedAppId,
                            onSelectApp = { triageViewModel.selectApp(it) }
                        )
                    }

                    item {
                        FeedbackComposerSection(
                            submitViewModel = submitViewModel,
                            submitState = submitState,
                            onSubmit = { submitViewModel.submit(testerId, testerUsername) }
                        )
                    }

                    item {
                        MissionComposerCard(
                            missionTitle = missionState.newTitle,
                            missionInstructions = missionState.newInstructions,
                            existingCount = missionState.missions.size,
                            onTitleChange = { missionsViewModel.onTitleChanged(it) },
                            onInstructionsChange = { missionsViewModel.onInstructionsChanged(it) },
                            onPublishMission = { missionsViewModel.addMission() }
                        )
                    }

                    triageState.selectedItem?.let { sel ->
                        item {
                            SelectedFeedbackDetailCard(
                                item = sel,
                                onOpenMediaUrl = { platformVerifier.openExternalUrl(it) },
                                onStatusChange = { status -> triageViewModel.updateStatus(sel.id, status) },
                                onClose = { triageViewModel.selectItem(null) }
                            )
                        }
                    }

                    item {
                        Text(
                            text = "Triage Feed (${triageState.items.size} Reports — Tap Card to Inspect & Triage)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

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
    }
}

@Composable
private fun AppSwitcherStrip(
    apps: List<dev.buildgate.app.entities.applisting.model.AppListing>,
    selectedAppId: String,
    onSelectApp: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        apps.forEach { app ->
            FilterChip(
                selected = app.id == selectedAppId,
                onClick = { onSelectApp(app.id) },
                label = { Text(app.title.substringBefore(" -")) }
            )
        }
    }
}

@Composable
private fun SelectedFeedbackDetailCard(
    item: FeedbackItem,
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
                    fontWeight = FontWeight.Bold
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

            Row(
                modifier = Modifier.fillMaxWidth(),
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
