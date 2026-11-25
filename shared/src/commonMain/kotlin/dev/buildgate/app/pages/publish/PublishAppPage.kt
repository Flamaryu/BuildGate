package dev.buildgate.app.pages.publish

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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.buildgate.app.features.publishapp.DeepLinkSnippetLanguage
import dev.buildgate.app.features.publishapp.PublishAppEvent
import dev.buildgate.app.features.publishapp.PublishAppViewModel
import dev.buildgate.app.shared.platform.PlatformVerifier
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublishAppPage(
    ownerId: String,
    ownerKarma: Int,
    viewModel: PublishAppViewModel,
    onPublished: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val platformVerifier: PlatformVerifier = koinInject()

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is PublishAppEvent.ShowMessage -> snackbarHostState.showSnackbar(event.msg)
                is PublishAppEvent.Published -> onPublished(event.packageName)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Publish Closed-Testing Campaign",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Your Karma ($ownerKarma ⚡) boosts your app's Discovery rank automatically",
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
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "1. Google Play Closed-Testing Details",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedTextField(
                            value = state.title,
                            onValueChange = { viewModel.onTitleChanged(it) },
                            label = { Text("App Name (e.g., FocusPulse Pro)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = state.packageName,
                            onValueChange = { viewModel.onPackageNameChanged(it) },
                            label = { Text("Android Package ID (e.g., io.focuspulse.app)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = state.shortSummary,
                            onValueChange = { viewModel.onSummaryChanged(it) },
                            label = { Text("One-Sentence Pitch & What Testers Should Focus On") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = state.googleGroupUrl,
                            onValueChange = { viewModel.onGoogleGroupUrlChanged(it) },
                            label = { Text("Google Group URL (Step 1 for testers)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = state.playStoreOptInUrl,
                            onValueChange = { viewModel.onPlayOptInUrlChanged(it) },
                            label = { Text("Google Play Web Opt-In URL (Step 2 for testers)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = state.currentBuildVersion,
                                onValueChange = { viewModel.onBuildVersionChanged(it) },
                                label = { Text("Current Build Tag") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = state.targetTesters,
                                onValueChange = { viewModel.onTargetTestersChanged(it) },
                                label = { Text("Target Testers (16+ rec.)") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
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
                        Text(
                            text = "2. First Daily QA Mission (Recommended)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Give testers a concrete 60-second mission so Google Play sees genuine feature engagement and you get real feedback.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = state.initialMissionTitle,
                            onValueChange = { viewModel.onInitialMissionTitleChanged(it) },
                            label = { Text("Mission Title (e.g., Complete onboarding & create first note)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = state.initialMissionInstructions,
                            onValueChange = { viewModel.onInitialMissionInstructionsChanged(it) },
                            label = { Text("Step-by-step instructions") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            shape = RoundedCornerShape(12.dp)
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
                        Text(
                            text = "3. Optional Tier-2 Cryptographic Deep-Link Handshake",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Leave blank for Zero-SDK Mode (works immediately with 0 code changes). Or enter a custom URI scheme to earn the 🔗 Deep-Link Verified badge and reward testers +5 bonus Karma/day.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = state.deepLinkScheme,
                            onValueChange = { viewModel.onDeepLinkSchemeChanged(it) },
                            label = { Text("Optional Custom URI Scheme (e.g., focuspulse)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        HorizontalDivider()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DeepLinkSnippetLanguage.entries.forEach { lang ->
                                FilterChip(
                                    selected = state.selectedSnippetLanguage == lang,
                                    onClick = { viewModel.onSnippetLanguageSelected(lang) },
                                    label = { Text(lang.label) }
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = state.generatedSnippet,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                val langLabel = state.selectedSnippetLanguage.label
                                platformVerifier.copyToClipboard("$langLabel Deep-Link Snippet", state.generatedSnippet)
                                scope.launch { snackbarHostState.showSnackbar("Copied $langLabel Deep-Link snippet!") }
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Copy ${state.selectedSnippetLanguage.label} Handshake Snippet")
                        }

                        Button(
                            onClick = { viewModel.publishCampaign(ownerId, ownerKarma) },
                            enabled = !state.isSubmitting,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(if (state.isSubmitting) "Publishing..." else "🚀 Launch 14-Day Campaign")
                        }
                    }
                }
            }
        }
    }
}
