package dev.buildgate.app.widgets.checkin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.buildgate.app.entities.applisting.model.AppListing
import dev.buildgate.app.entities.testmission.model.TestMission

@Composable
fun DailyCheckInCard(
    app: AppListing,
    isInstalled: Boolean,
    installedVersionLabel: String? = null,
    hasJoinedGoogleGroup: Boolean = false,
    hasAcceptedPlayOptIn: Boolean = false,
    verifiedDaysCount: Int,
    isSessionRunning: Boolean,
    isSessionPaused: Boolean = false,
    isTargetBackgrounded: Boolean = false,
    secondsRemaining: Int,
    isVerifiedToday: Boolean,
    canDrawOverlays: Boolean = false,
    hasUsageStatsPermission: Boolean = false,
    missions: List<TestMission>,
    onToggleMission: (String) -> Unit,
    onStartSessionClick: () -> Unit,
    onOpenGoogleGroupClick: () -> Unit = {},
    onOptInClick: () -> Unit,
    onRefreshInstallClick: () -> Unit = {},
    onEnableOverlayClick: () -> Unit = {},
    onEnableUsageAccessClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showUsageDisclosure by remember { mutableStateOf(false) }

    if (showUsageDisclosure) {
        AlertDialog(
            onDismissRequest = { showUsageDisclosure = false },
            title = {
                Text(
                    text = "🛡️ Usage Access Disclosure",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "BuildGate optionally uses Android Usage Access (PACKAGE_USAGE_STATS) ONLY while an active 60-second QA timer is running.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• What is checked: Whether the enrolled closed-test app (${app.title}) stays in the foreground so the timer pauses if you switch away or lock your screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "• What is NEVER collected: BuildGate never reads your messages, browsing history, or uploads your list of unrelated apps.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "You can revoke Usage Access anytime in Android Settings. See our Privacy Policy at buildgatehub.com/privacy.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUsageDisclosure = false
                        onEnableUsageAccessClick()
                    }
                ) {
                    Text("Continue to Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUsageDisclosure = false }) {
                    Text("Not Now")
                }
            }
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = buildString {
                            append("Target Build ${app.currentBuildVersion}")
                            if (!installedVersionLabel.isNullOrBlank()) {
                                append(" • Installed $installedVersionLabel")
                            }
                            if (app.hasDeepLinkHandshake) {
                                append(" • 🔗 Deep-Link Verified")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isInstalled) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isInstalled) "📲 Installed" else "⚠️ Not Installed",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isInstalled) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // Guided 3-Step Closed-Testing Onboarding Stepper
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "3-Step Closed Testing Onboarding",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                OnboardingStepRow(
                    stepNumber = 1,
                    title = "Join Developer's Google Group",
                    subtitle = "Unlocks Google Play Closed Test access for your Google email",
                    isComplete = hasJoinedGoogleGroup || isInstalled,
                    actionText = if (hasJoinedGoogleGroup) "Open Again" else "Join Group ➔",
                    onClick = onOpenGoogleGroupClick
                )

                OnboardingStepRow(
                    stepNumber = 2,
                    title = "Accept Google Play Web Opt-In",
                    subtitle = "Click 'Become a Tester' on Play Store testing page",
                    isComplete = hasAcceptedPlayOptIn || isInstalled,
                    actionText = if (hasAcceptedPlayOptIn) "Play Link" else "Opt-In ➔",
                    onClick = onOptInClick
                )

                OnboardingStepRow(
                    stepNumber = 3,
                    title = "Install App & Auto-Detect Package",
                    subtitle = app.packageName,
                    isComplete = isInstalled,
                    actionText = if (isInstalled) "Verified ✅" else "Re-check",
                    onClick = onRefreshInstallClick
                )
            }

            // Floating QA Overlay Quick Enable Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (canDrawOverlays) {
                            "🟢 Floating QA Overlay Pill Enabled"
                        } else {
                            "💡 Floating QA Pill (1-Tap Bug, Screenshot & 15s Clip)"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (canDrawOverlays) {
                            "Tap the floating pill inside ${app.title} to pause & log bugs anytime."
                        } else {
                            "Optional: Enable overlay for in-app capture (Ongoing Notification fallback is always active)."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!canDrawOverlays) {
                    TextButton(onClick = onEnableOverlayClick) {
                        Text("Enable")
                    }
                }
            }

            // Optional Foreground Usage Verification Strip (Anti-Cheat without Deep Links)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (hasUsageStatsPermission) {
                            "🛡️ Strict Foreground Verification Active"
                        } else {
                            "🛡️ Foreground Anti-Cheat (Usage Access)"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (hasUsageStatsPermission) {
                            "Timer automatically pauses if ${app.title} is backgrounded or screen locks."
                        } else {
                            "Optional: Grant Usage Access to verify the target app stays on screen for the full 60s."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!hasUsageStatsPermission) {
                    TextButton(onClick = { showUsageDisclosure = true }) {
                        Text("Enable")
                    }
                }
            }

            HorizontalDivider()

            // 14-Day Progress Bar
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "14-Day Continuity Streak (1-Day Grace Active)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Day $verifiedDaysCount / ${app.targetDays}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                LinearProgressIndicator(
                    progress = { (verifiedDaysCount.toFloat() / app.targetDays.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            // Missions Checklist
            if (missions.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Today's Developer QA Missions:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    missions.forEach { mission ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = mission.isCompleted,
                                onCheckedChange = { onToggleMission(mission.id) }
                            )
                            Column {
                                Text(
                                    text = mission.title + if (mission.isRequired) " *" else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = mission.instructions,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Timer & Pause-Aware Status
            AnimatedVisibility(visible = isSessionRunning) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isSessionPaused) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.primaryContainer
                        )
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when {
                            isSessionPaused -> "⏸️ Session Paused for QA Bug Logging ($secondsRemaining s remaining — Queue Held)"
                            isTargetBackgrounded -> "⏸️ Timer Paused — Return to ${app.title} to resume ($secondsRemaining s left)"
                            else -> "⏱️ Active in Target App: $secondsRemaining s remaining"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isSessionPaused || isTargetBackgrounded) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            if (isVerifiedToday) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🎉 Verified Today! +${if (app.hasDeepLinkHandshake) 15 else 10} Karma Earned!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!isInstalled) {
                    OutlinedButton(
                        onClick = onOptInClick,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Install on Google Play")
                    }
                }

                Button(
                    onClick = onStartSessionClick,
                    enabled = isInstalled && !isSessionRunning && !isVerifiedToday,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        when {
                            isSessionPaused -> "Paused for Bug Log..."
                            isSessionRunning -> "Testing Active ($secondsRemaining s)..."
                            isVerifiedToday -> "Verified Today ✅"
                            else -> "Launch & Start 60s Check-In"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingStepRow(
    stepNumber: Int,
    title: String,
    subtitle: String,
    isComplete: Boolean,
    actionText: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isComplete) "✅" else "$stepNumber.",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        OutlinedButton(
            onClick = onClick,
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(actionText, style = MaterialTheme.typography.labelSmall)
        }
    }
}
