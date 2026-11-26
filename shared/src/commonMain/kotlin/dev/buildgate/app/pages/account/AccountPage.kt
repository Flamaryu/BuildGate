package dev.buildgate.app.pages.account

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.buildgate.app.entities.enrollment.model.EnrollmentStatus
import dev.buildgate.app.entities.enrollment.repository.EnrollmentRepository
import dev.buildgate.app.features.auth.AuthUiEvent
import dev.buildgate.app.features.auth.AuthViewModel
import dev.buildgate.app.shared.theme.BuildGateThemeMode
import dev.buildgate.app.shared.theme.BuildGateThemePreferences
import dev.buildgate.app.widgets.devicetable.DeviceMetadataTable
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountPage(
    viewModel: AuthViewModel,
    modifier: Modifier = Modifier
) {
    val session by viewModel.sessionState.collectAsState()
    val karmaLedger by viewModel.karmaLedger.collectAsState()
    val emailInput by viewModel.emailInput.collectAsState()
    val usernameInput by viewModel.usernameInput.collectAsState()
    val passwordInput by viewModel.passwordInput.collectAsState()
    val isBusy by viewModel.isBusy.collectAsState()
    val themeMode by BuildGateThemePreferences.themeMode.collectAsState()
    val enrollmentRepo: EnrollmentRepository = koinInject()
    val activeUserId = session.user?.id ?: if (session.isDemoMode) "tester_1" else ""
    val enrollmentsFlow = remember(activeUserId) { enrollmentRepo.getEnrollmentsForTester(activeUserId) }
    val userEnrollments by enrollmentsFlow.collectAsState(initial = emptyList())
    val activeTestsCount = userEnrollments.count { it.status == EnrollmentStatus.ACTIVE }
    val totalVerifiedDays = userEnrollments.sumOf { it.verifiedDaysCount }
    var isCreateAccountMode by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is AuthUiEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Unified Developer & Tester Account",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (session.isDemoMode) {
                                "Offline Demo Mode • Set SUPABASE_URL in local.properties for Hosted Cloud"
                            } else {
                                "Connected to Hosted Supabase Cloud (PKCE + RLS Active)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
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
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Appearance",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            BuildGateThemeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = themeMode == mode,
                                    onClick = { BuildGateThemePreferences.setThemeMode(mode) },
                                    label = { Text("${mode.icon} ${mode.label}") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            if (session.isLoggedIn && session.profile != null) {
                val profile = session.profile!!
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
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = profile.username,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = session.user?.email.orEmpty(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(
                                    onClick = { viewModel.signOut() },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Sign Out")
                                }
                            }

                            HorizontalDivider()

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    StatPill(
                                        label = "⚡ Karma",
                                        value = "${profile.karmaPoints}",
                                        subtitle = "Boosts your apps",
                                        modifier = Modifier.weight(1f)
                                    )
                                    StatPill(
                                        label = "⭐ Trust Score",
                                        value = "${profile.trustedScore}",
                                        subtitle = "Tester reputation",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    StatPill(
                                        label = "🧪 Active Tests",
                                        value = "$activeTestsCount",
                                        subtitle = if (totalVerifiedDays == 1) "1 verified day" else "$totalVerifiedDays verified days",
                                        modifier = Modifier.weight(1f)
                                    )
                                    StatPill(
                                        label = "🏁 14-Day Finishes",
                                        value = "${profile.completedCampaignsCount}",
                                        subtitle = "Full 14d completed",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                if (profile.registeredDevices.isNotEmpty()) {
                    item {
                        DeviceMetadataTable(metadata = profile.registeredDevices.first())
                    }
                }

                item {
                    Text(
                        text = "Karma Reciprocity Ledger",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(karmaLedger, key = { it.id }) { entry ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.note.ifBlank { entry.reason.name.replace("_", " ") },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = entry.reason.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = if (entry.delta >= 0) "+${entry.delta} ⚡" else "${entry.delta} ⚡",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            } else {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                FilterChip(
                                    selected = !isCreateAccountMode,
                                    onClick = { isCreateAccountMode = false },
                                    label = { Text("Sign In") }
                                )
                                FilterChip(
                                    selected = isCreateAccountMode,
                                    onClick = { isCreateAccountMode = true },
                                    label = { Text("Create Account (+50⚡)") }
                                )
                            }

                            Text(
                                text = if (isCreateAccountMode) "Create Your BuildGate Account" else "Welcome Back — Sign In",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Use the same Google email you use for Google Groups & Google Play Closed Testing so opt-in links work seamlessly.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Button(
                                onClick = { viewModel.signInWithGoogle() },
                                enabled = !isBusy,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Continue with Google (Recommended)")
                            }

                            HorizontalDivider()

                            if (isCreateAccountMode) {
                                OutlinedTextField(
                                    value = usernameInput,
                                    onValueChange = { viewModel.onUsernameChanged(it) },
                                    label = { Text("Developer / Tester Handle") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }

                            OutlinedTextField(
                                value = emailInput,
                                onValueChange = { viewModel.onEmailChanged(it) },
                                label = { Text("Email Address") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )

                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = { viewModel.onPasswordChanged(it) },
                                label = { Text("Password") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Button(
                                onClick = {
                                    if (isCreateAccountMode) viewModel.signUpWithEmail()
                                    else viewModel.signInWithEmail()
                                },
                                enabled = !isBusy,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    when {
                                        isBusy -> "Working..."
                                        isCreateAccountMode -> "Create Account (+50⚡)"
                                        else -> "Sign In"
                                    }
                                )
                            }

                            TextButton(
                                onClick = { isCreateAccountMode = !isCreateAccountMode },
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Text(
                                    if (isCreateAccountMode) "Already have an account? Sign In"
                                    else "New to BuildGate? Create Account (+50⚡)"
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
private fun StatPill(
    label: String,
    value: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                )
            }
        }
    }
}
