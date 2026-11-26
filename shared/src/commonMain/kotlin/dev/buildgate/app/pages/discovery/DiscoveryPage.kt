package dev.buildgate.app.pages.discovery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.buildgate.app.entities.applisting.repository.AppListingRepository
import dev.buildgate.app.entities.applisting.ui.AppListingCard
import dev.buildgate.app.entities.auth.model.AuthSession
import dev.buildgate.app.entities.enrollment.repository.EnrollmentRepository
import dev.buildgate.app.entities.testerprofile.ui.TesterProfileBadge
import dev.buildgate.app.shared.time.BuildGateClock
import dev.buildgate.app.shared.ui.BuildGateLogo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryPage(
    appListingRepository: AppListingRepository,
    enrollmentRepository: EnrollmentRepository,
    authSession: AuthSession,
    onOpenCampaign: (String) -> Unit,
    onOpenDailyQueue: () -> Unit,
    onOpenPublishWizard: () -> Unit,
    onOpenAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listings by appListingRepository.getListings().collectAsState(initial = emptyList())
    val testerId = authSession.user?.id ?: "tester_1"
    val enrollments by enrollmentRepository.getEnrollmentsForTester(testerId).collectAsState(initial = emptyList())
    val todayUtc = BuildGateClock.todayUtcDate()
    val pendingDailyCount = enrollments.count {
        it.lastVerifiedUtcDate != todayUtc && it.currentStreak < 14
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BuildGateLogo(size = 32.dp)
                        Column {
                            Text(
                                text = "BuildGate Hub",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Karma-Ranked Android Closed Testing & QA Pipeline",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    authSession.profile?.let { profile ->
                        TesterProfileBadge(
                            profile = profile,
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .clickable { onOpenAccount() }
                        )
                    }
                }
            )
        },
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
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (pendingDailyCount > 0) {
                                    "$pendingDailyCount Enrolled App(s) Waiting Today"
                                } else {
                                    "All Daily Check-Ins Complete!"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Earn +10 Karma/day, +25/bug, and +100 Day-14 bonus to boost your own app.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = onOpenPublishWizard,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Publish App")
                            }
                            Button(
                                onClick = onOpenDailyQueue,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Run Queue")
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Karma-Boosted Apps Needing 14-Day Testers",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${listings.size} Active Campaigns",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            items(listings, key = { it.id }) { app ->
                val enrollment = enrollments.find { it.appId == app.id }
                val isEnrolled = enrollment != null
                val streakDays = enrollment?.currentStreak ?: 0

                AppListingCard(
                    listing = app,
                    isEnrolled = isEnrolled,
                    verifiedDaysCount = streakDays,
                    onOptInClick = {
                        onOpenCampaign(app.packageName)
                    },
                    onOpenSessionClick = {
                        onOpenCampaign(app.packageName)
                    }
                )
            }
        }
    }
}
