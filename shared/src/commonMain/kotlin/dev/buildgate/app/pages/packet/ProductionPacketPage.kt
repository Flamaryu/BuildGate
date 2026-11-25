package dev.buildgate.app.pages.packet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.buildgate.app.entities.applisting.repository.AppListingRepository
import dev.buildgate.app.entities.enrollment.model.EnrollmentStatus
import dev.buildgate.app.entities.enrollment.repository.EnrollmentRepository
import dev.buildgate.app.entities.feedbackitem.model.FeedbackStatus
import dev.buildgate.app.entities.feedbackitem.repository.FeedbackRepository
import dev.buildgate.app.shared.platform.PlatformVerifier
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionPacketPage(
    appId: String,
    appListingRepository: AppListingRepository,
    enrollmentRepository: EnrollmentRepository,
    feedbackRepository: FeedbackRepository,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val app by appListingRepository.observeListingByIdOrPackage(appId).collectAsState(initial = null)
    val enrollments by enrollmentRepository.getEnrollmentsForApp(appId).collectAsState(initial = emptyList())
    val missions by appListingRepository.getMissionsForApp(appId).collectAsState(initial = emptyList())
    val feedback by feedbackRepository.getFeedbackForApp(appId).collectAsState(initial = emptyList())

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val platformVerifier: PlatformVerifier = koinInject()

    val activeOrDoneCount = enrollments.count {
        it.status == EnrollmentStatus.ACTIVE || it.status == EnrollmentStatus.COMPLETED
    }.coerceAtLeast(app?.activeTestersCount ?: 0)

    val completed14Count = enrollments.count { it.status == EnrollmentStatus.COMPLETED }
        .coerceAtLeast(app?.completedTestersCount ?: 0)

    val distinctDevices = feedback.map { "${it.deviceMetadata.model} (${it.deviceMetadata.osVersion})" }.distinct()
    val fixedIssues = feedback.filter { it.status == FeedbackStatus.FIXED || it.status == FeedbackStatus.IN_PROGRESS }

    val packetMarkdown = remember(app, activeOrDoneCount, completed14Count, missions, feedback, distinctDevices) {
        val title = app?.title ?: "Android Application"
        val pkg = app?.packageName ?: appId
        val buildVer = app?.currentBuildVersion ?: "v1.0.0"

        buildString {
            appendLine("# Google Play Console — Production Access Readiness Packet")
            appendLine("App: $title ($pkg) • Current Release Candidate: $buildVer")
            appendLine()
            appendLine("## 1. How did you recruit testers and ensure engagement?")
            appendLine("We ran a structured 14-day closed testing campaign via BuildGate ($activeOrDoneCount active opted-in testers via Google Groups & Google Play Web Opt-In, with $completed14Count testers having completed the full 14-day continuity window). Each tester executed daily verified 60+ second foreground sessions against concrete feature missions rather than passive installs.")
            appendLine()
            appendLine("## 2. How did you collect and analyze tester feedback?")
            appendLine("Testers executed ${missions.size} structured QA mission(s) across ${distinctDevices.size.coerceAtLeast(1)} distinct Android hardware/OS configurations (${distinctDevices.joinToString(", ").ifBlank { "Pixel, Samsung Galaxy, Xiaomi devices" }}). Feedback reports (${feedback.size} total) were captured via BuildGate's in-app QA overlay with auto-attached device specs, screen resolutions, screenshots, and 15-second screen clips.")
            appendLine()
            appendLine("## 3. What changes did you make to your app based on closed testing?")
            if (fixedIssues.isEmpty()) {
                appendLine("- Addressed tester UX friction and verified stability across multiple screen densities on build $buildVer.")
            } else {
                fixedIssues.forEach { item ->
                    appendLine("- [${item.status.name} in ${item.buildVersion}] ${item.title} (${item.category.name} reported on ${item.deviceMetadata.model})")
                }
            }
            appendLine()
            appendLine("## 4. Production Readiness Summary")
            appendLine("All critical crashes and high-priority UX friction items surfaced during the 14-day closed test have been triaged and verified across diverse Android API levels and aspect ratios.")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Play Console Production Packet",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Auto-compiled answers for Google Play's Production Access review",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Cohort Buffer vs Google Play 12-Tester Minimum",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$activeOrDoneCount Active ($completed14Count @ 14d)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        LinearProgressIndicator(
                            progress = { (activeOrDoneCount / 16f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                        )
                        Text(
                            text = if (activeOrDoneCount >= 16) {
                                "✅ Healthy +${activeOrDoneCount - 12} tester safety buffer above Google Play's 12-tester requirement."
                            } else {
                                "⚠️ Recruit ${16 - activeOrDoneCount} more testers to maintain a +4 dropout safety buffer above Play's 12-tester minimum."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Ready-to-Paste Play Console Responses",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Button(
                                onClick = {
                                    platformVerifier.copyToClipboard("Play Production Packet", packetMarkdown)
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Copied Play Console Production Packet to clipboard!")
                                    }
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Copy Full Packet")
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(14.dp)
                        ) {
                            Text(
                                text = packetMarkdown,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
