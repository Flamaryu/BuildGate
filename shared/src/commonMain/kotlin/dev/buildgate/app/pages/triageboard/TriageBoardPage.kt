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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import dev.buildgate.app.entities.feedbackitem.ui.FeedbackItemCard
import dev.buildgate.app.features.submitfeedback.SubmitFeedbackEvent
import dev.buildgate.app.features.submitfeedback.SubmitFeedbackViewModel
import dev.buildgate.app.features.triagefeedback.TriageFeedbackViewModel
import dev.buildgate.app.widgets.devicetable.DeviceMetadataTable
import dev.buildgate.app.widgets.kanban.TriageKanbanBoard
import dev.buildgate.app.widgets.retention.RetentionTracker14Day

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriageBoardPage(
    triageViewModel: TriageFeedbackViewModel,
    submitViewModel: SubmitFeedbackViewModel,
    testerId: String,
    modifier: Modifier = Modifier
) {
    val triageState by triageViewModel.uiState.collectAsState()
    val submitState by submitViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        submitViewModel.eventFlow.collect { event ->
            when (event) {
                is SubmitFeedbackEvent.ShowMessage -> snackbarHostState.showSnackbar(event.msg)
                SubmitFeedbackEvent.SubmissionSuccess -> { /* submitted */ }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Triage & Feedback Hub",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "FocusPulse Pro (v1.0.4-rc1)",
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
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            val isWideScreen = maxWidth >= 800.dp

            if (isWideScreen) {
                // Expanded Web / Desktop Layout: Wide 3-column Kanban + Retention Tracker & Specs side-panel
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Left 2/3: Multi-column Kanban Board
                    Column(
                        modifier = Modifier
                            .weight(2f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TriageKanbanBoard(
                            items = triageState.items,
                            onStatusChange = { id, status -> triageViewModel.updateStatus(id, status) },
                            onUpvote = { id -> triageViewModel.upvote(id) },
                            onItemSelect = { item -> triageViewModel.selectItem(item) }
                        )
                    }

                    // Right 1/3: 14-day Retention Progress & Device Specs Table
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        RetentionTracker14Day(
                            verifiedDaysCount = 11,
                            testerName = "FocusPulse Active Testers"
                        )

                        submitState.capturedSpecs?.let { specs ->
                            DeviceMetadataTable(metadata = specs)
                        }
                    }
                }
            } else {
                // Mobile Layout: Fast Bug Submission Sheet + Triage List
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Submit Bug or UX Friction Report",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

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
                                label = { Text("Detailed Steps / Friction Description") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3,
                                shape = RoundedCornerShape(12.dp)
                            )

                            // Specs preview tag
                            submitState.capturedSpecs?.let { specs ->
                                Text(
                                    text = "📱 Auto-attached Specs: ${specs.model} (${specs.screenResolution})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Button(
                                onClick = { submitViewModel.submit(testerId) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Submit Report with Hardware Specs")
                            }
                        }
                    }

                    item {
                        Text(
                            text = "Triage Feed (${triageState.items.size} Reports)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }

                    items(triageState.items) { item ->
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
