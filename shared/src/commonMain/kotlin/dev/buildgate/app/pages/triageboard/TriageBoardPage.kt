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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import dev.buildgate.app.entities.feedbackitem.model.FeedbackCategory
import dev.buildgate.app.entities.feedbackitem.ui.FeedbackItemCard
import dev.buildgate.app.features.managemissions.ManageMissionsViewModel
import dev.buildgate.app.features.submitfeedback.SubmitFeedbackEvent
import dev.buildgate.app.features.submitfeedback.SubmitFeedbackViewModel
import dev.buildgate.app.features.triagefeedback.TriageFeedbackViewModel
import dev.buildgate.app.features.triagefeedback.TriageUiEvent
import dev.buildgate.app.widgets.devicetable.DeviceMetadataTable
import dev.buildgate.app.widgets.kanban.TriageKanbanBoard
import dev.buildgate.app.widgets.retention.RetentionTracker14Day

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
    val snackbarHostState = remember { SnackbarHostState() }

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

    val currentApp = triageState.selectedApp
    val bestStreak = triageState.enrollmentsForApp.maxOfOrNull { it.currentStreak } ?: 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Triage & QA Hub",
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

                        FeedbackComposerSection(
                            submitViewModel = submitViewModel,
                            submitTitle = submitState.title,
                            submitDescription = submitState.description,
                            selectedCategory = submitState.category,
                            specsSummary = submitState.capturedSpecs?.let { "${it.model} (${it.screenResolution})" },
                            onSubmit = { submitViewModel.submit(testerId, testerUsername) }
                        )

                        submitState.capturedSpecs?.let { specs ->
                            DeviceMetadataTable(metadata = specs)
                        }
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
                            submitTitle = submitState.title,
                            submitDescription = submitState.description,
                            selectedCategory = submitState.category,
                            specsSummary = submitState.capturedSpecs?.let { "${it.model} (${it.screenResolution})" },
                            onSubmit = { submitViewModel.submit(testerId, testerUsername) }
                        )
                    }

                    item {
                        Text(
                            text = "Triage Feed (${triageState.items.size} Reports)",
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
private fun FeedbackComposerSection(
    submitViewModel: SubmitFeedbackViewModel,
    submitTitle: String,
    submitDescription: String,
    selectedCategory: FeedbackCategory,
    specsSummary: String?,
    onSubmit: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Log Bug or UX Friction",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FeedbackCategory.entries.forEach { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { submitViewModel.onCategoryChanged(cat) },
                    label = { Text(cat.name.replace("_", " ")) }
                )
            }
        }

        OutlinedTextField(
            value = submitTitle,
            onValueChange = { submitViewModel.onTitleChanged(it) },
            label = { Text("Issue Title") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        OutlinedTextField(
            value = submitDescription,
            onValueChange = { submitViewModel.onDescriptionChanged(it) },
            label = { Text("Reproduction Steps / Expected vs Actual") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            shape = RoundedCornerShape(12.dp)
        )

        specsSummary?.let { summary ->
            Text(
                text = "📱 Auto-attached Specs: $summary",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Button(
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Submit Report with Hardware Specs")
        }
    }
}
