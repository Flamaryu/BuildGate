package dev.buildgate.app.pages.publish

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
    isSignedIn: Boolean = true,
    editAppId: String? = null,
    viewModel: PublishAppViewModel,
    onPublished: (String) -> Unit,
    onNavigateToAccount: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val platformVerifier: PlatformVerifier = koinInject()

    LaunchedEffect(editAppId) {
        if (!editAppId.isNullOrBlank()) {
            viewModel.loadForEditing(editAppId)
        } else {
            viewModel.resetForNewCampaign()
        }
    }

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
                            text = if (state.isEditMode) "Edit Campaign & Build Version" else "Publish Closed-Testing Campaign",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when {
                                !isSignedIn -> "Sign in or create a free account to publish your closed-testing app"
                                state.isEditMode -> "Update build version, links, or upgrade to Tier-2 Deep-Link Handshake"
                                else -> "Your Karma ($ownerKarma ⚡) boosts your app's Discovery rank automatically"
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
        if (!isSignedIn || ownerId.isBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Sign In to Publish Your App",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Create a free BuildGate account (you'll get +50⚡ Starter Karma) or sign in so we can link your 14-day closed-testing campaign, QA triage board, and Google Play Production Access packet to your developer profile.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onNavigateToAccount,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Sign In / Create Account (+50⚡)")
                        }
                    }
                }
            }
            return@Scaffold
        }

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
                            enabled = !state.isEditMode,
                            label = {
                                Text(
                                    if (state.isEditMode) "Android Package ID (locked after publishing)"
                                    else "Android Package ID (e.g., com.studio.myapp)"
                                )
                            },
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
                                label = { Text("Build Version (e.g., v1.6.1)") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = state.minVersionCode,
                                onValueChange = { viewModel.onMinVersionCodeChanged(it) },
                                label = { Text("Min VersionCode") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        OutlinedTextField(
                            value = state.targetTesters,
                            onValueChange = { viewModel.onTargetTestersChanged(it) },
                            label = { Text("Target Testers (16+ recommended for Google Play)") },
                            modifier = Modifier.fillMaxWidth(),
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
                        var showUriExplainer by remember { mutableStateOf(false) }
                        var showAdvancedScheme by remember { mutableStateOf(false) }
                        var showAdbHelp by remember { mutableStateOf(false) }

                        Text(
                            text = "3. Choose How Testing Sessions Are Verified",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Already have a build live on Play Console? Start with No-Code mode now and upgrade anytime. Want tamper-proof verification and faster 14-day signups? Use Deep-Link Handshake.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Side-by-side (wide) or stacked (mobile) verification method comparison cards
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val isWide = maxWidth >= 600.dp
                            val noCodeCard: @Composable (Modifier) -> Unit = { cardMod ->
                                VerificationMethodOptionCard(
                                    title = "No-Code Mode",
                                    badge = "Quick Start",
                                    badgeHighlight = false,
                                    selected = !state.enableDeepLinkHandshake,
                                    howItWorks = "BuildGate's zero-friction OS workaround: checks PackageManager + tracks foreground time (with optional Usage Access).",
                                    pros = listOf(
                                        "0 code changes required",
                                        "Works with your existing Play Console build right now",
                                        "Publish your campaign in 60 seconds"
                                    ),
                                    cons = listOf(
                                        "Relies on OS workaround (testers who skip Usage Access have weaker proof)",
                                        "Your app itself never confirms the session to our server",
                                        "Standard Karma only (no Verified badge)"
                                    ),
                                    onClick = { viewModel.onToggleDeepLinkHandshake(false) },
                                    modifier = cardMod
                                )
                            }
                            val deepLinkCard: @Composable (Modifier) -> Unit = { cardMod ->
                                VerificationMethodOptionCard(
                                    title = "Deep-Link Handshake",
                                    badge = "⭐ Recommended",
                                    badgeHighlight = true,
                                    selected = state.enableDeepLinkHandshake,
                                    howItWorks = "BuildGate launches your app with a one-time secret URI and ~10 lines of code confirm the boot directly to our server.",
                                    pros = listOf(
                                        "Tamper-proof: proves your real binary booted and ran",
                                        "Zero extra tester permissions (works even without Usage Access)",
                                        "Unlocks 🔗 Verified badge & +5 bonus Karma/day for faster roster fill",
                                        "Stronger server-signed proof for Google Play Production Access review"
                                    ),
                                    cons = listOf(
                                        "~2-minute one-time setup (Manifest XML + 10 lines of code)",
                                        "Requires uploading a new closed-test build"
                                    ),
                                    onClick = { viewModel.onToggleDeepLinkHandshake(true) },
                                    modifier = cardMod
                                )
                            }

                            if (isWide) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    noCodeCard(Modifier.weight(1f))
                                    deepLinkCard(Modifier.weight(1f))
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    deepLinkCard(Modifier.fillMaxWidth())
                                    noCodeCard(Modifier.fillMaxWidth())
                                }
                            }
                        }

                        if (!state.enableDeepLinkHandshake) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = "💡 You can launch with No-Code mode today and upgrade this campaign to Deep-Link Handshake on your next Play Console build.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            HorizontalDivider()

                            // Unified URI Preview + Beginner Explainer Toggle
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Unified BuildGate URI (Auto-Generated)",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    TextButton(onClick = { showUriExplainer = !showUriExplainer }) {
                                        Text(
                                            if (showUriExplainer) "Hide Info" else "? What is a Custom URI?",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                                Text(
                                    text = state.unifiedFullUriPreview,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (showUriExplainer) {
                                    Text(
                                        text = "A Custom URI works like a private web link that Android routes straight into your app instead of a browser — carrying a one-time secret code (nonce). BuildGate auto-generates buildgate.<your.package.id> so it never collides with other apps and needs zero website domain or SDK.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = { showAdvancedScheme = !showAdvancedScheme }) {
                                    Text(
                                        if (showAdvancedScheme) "Hide custom scheme override" else "Advanced: use my own existing scheme",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                if (showAdvancedScheme) {
                                    OutlinedTextField(
                                        value = state.deepLinkScheme,
                                        onValueChange = { viewModel.onDeepLinkSchemeChanged(it) },
                                        label = { Text("Custom Scheme Override (blank = ${state.unifiedDefaultScheme})") },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                            }

                            // 6-Language Stack Selector
                            Text(
                                text = "Choose Your App's Language / Engine:",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
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

                            val split = state.currentSplitSnippet

                            // Step A: Manifest XML (separate copyable block)
                            SnippetStepBlock(
                                stepTitle = "Step A • Paste into Android Manifest",
                                filePathHint = split.manifestPath,
                                code = split.manifestXml,
                                copyButtonLabel = "Copy Manifest XML",
                                onCopy = {
                                    platformVerifier.copyToClipboard("Manifest Intent Filter", split.manifestXml)
                                    scope.launch { snackbarHostState.showSnackbar("Copied AndroidManifest.xml intent-filter!") }
                                }
                            )

                            // Step B: Language Handler Code (separate copyable block)
                            SnippetStepBlock(
                                stepTitle = "Step B • Paste ${state.selectedSnippetLanguage.label} Handler",
                                filePathHint = split.handlerPath,
                                code = split.handlerCode,
                                copyButtonLabel = "Copy ${state.selectedSnippetLanguage.label} Code",
                                onCopy = {
                                    val langLabel = state.selectedSnippetLanguage.label
                                    platformVerifier.copyToClipboard("$langLabel Deep-Link Handler", split.handlerCode)
                                    scope.launch { snackbarHostState.showSnackbar("Copied $langLabel handler code!") }
                                }
                            )

                            // Step C: ADB Local Test Command + Beginner Tooltip / Guide
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Step C • Test Locally in 5 Seconds (ADB)",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    TextButton(onClick = { showAdbHelp = !showAdbHelp }) {
                                        Text(
                                            if (showAdbHelp) "Hide Guide" else "? How do I run this?",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                                if (showAdbHelp) {
                                    Text(
                                        text = "ADB (Android Debug Bridge) is built into Android Studio:\n1. Plug in your Android phone (USB Debugging ON) or start an emulator with your app installed.\n2. Open the Terminal tab at the bottom of Android Studio.\n3. Paste the command below and press Enter — your app should launch immediately!",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = state.adbTestCommand,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        platformVerifier.copyToClipboard("ADB Deep-Link Test", state.adbTestCommand)
                                        scope.launch { snackbarHostState.showSnackbar("Copied ADB test command!") }
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Copy ADB Test Command")
                                }
                            }
                        }

                        Button(
                            onClick = { viewModel.publishCampaign(ownerId, ownerKarma) },
                            enabled = !state.isSubmitting,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                when {
                                    state.isSubmitting && state.isEditMode -> "Saving Changes..."
                                    state.isSubmitting -> "Publishing..."
                                    state.isEditMode -> "💾 Save Campaign Changes"
                                    else -> "🚀 Launch 14-Day Campaign"
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VerificationMethodOptionCard(
    title: String,
    badge: String,
    badgeHighlight: Boolean,
    selected: Boolean,
    howItWorks: String,
    pros: List<String>,
    cons: List<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selected) "● $title" else "○ $title",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (badgeHighlight) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (badgeHighlight) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = howItWorks,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                pros.forEach { pro ->
                    Text(
                        text = "✅ $pro",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                cons.forEach { con ->
                    Text(
                        text = "⚠️ $con",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SnippetStepBlock(
    stepTitle: String,
    filePathHint: String,
    code: String,
    copyButtonLabel: String,
    onCopy: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stepTitle,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = filePathHint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(10.dp)
        ) {
            Text(
                text = code,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        OutlinedButton(
            onClick = onCopy,
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(copyButtonLabel)
        }
    }
}
