package dev.buildgate.app.pages.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import dev.buildgate.app.entities.auth.repository.AuthRepository
import dev.buildgate.app.entities.enrollment.repository.EnrollmentRepository
import dev.buildgate.app.entities.testerprofile.ui.TesterProfileBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryPage(
    appListingRepository: AppListingRepository,
    enrollmentRepository: EnrollmentRepository,
    authRepository: AuthRepository,
    onNavigateToSession: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listings by appListingRepository.getListings().collectAsState(initial = emptyList())
    val authSession by authRepository.getAuthSession().collectAsState(initial = dev.buildgate.app.entities.auth.model.AuthSession())
    val testerId = authSession.user?.id ?: "tester_1"
    val enrollments by enrollmentRepository.getEnrollmentsForTester(testerId).collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "BuildGate Hub",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Closed Testing & Verification Hub",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    authSession.profile?.let { profile ->
                        TesterProfileBadge(
                            profile = profile,
                            modifier = Modifier.padding(end = 12.dp)
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Apps Needing 14-Day Testers",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${listings.size} Available",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            items(listings) { app ->
                val enrollment = enrollments.find { it.appId == app.id }
                val isEnrolled = enrollment != null
                val verifiedDays = enrollment?.verifiedDaysCount ?: 0

                AppListingCard(
                    listing = app,
                    isEnrolled = isEnrolled,
                    verifiedDaysCount = verifiedDays,
                    onOptInClick = {
                        // Open Play Store opt-in URL
                    },
                    onOpenSessionClick = {
                        onNavigateToSession(app.id)
                    }
                )
            }
        }
    }
}
