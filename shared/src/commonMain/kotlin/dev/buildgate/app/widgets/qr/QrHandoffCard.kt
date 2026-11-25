package dev.buildgate.app.widgets.qr

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun QrHandoffCard(
    appTitle: String,
    shareableUrl: String,
    androidDeepLink: String,
    onCopyUrl: () -> Unit,
    onOpenGoogleGroup: () -> Unit,
    onOpenPlayOptIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(132.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    DeterministicQrMatrix(payload = shareableUrl)
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "📲 Continue Daily Check-In on Android",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "To protect developers from fake browser sessions, BuildGate verifies real Android package installations and foreground testing time on physical/emulator Android devices.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = shareableUrl,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Deep link: $androidDeepLink",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onCopyUrl,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Copy Campaign Link")
                }
                OutlinedButton(
                    onClick = onOpenGoogleGroup,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("1. Join Google Group")
                }
                OutlinedButton(
                    onClick = onOpenPlayOptIn,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("2. Play Web Opt-In")
                }
            }
        }
    }
}

@Composable
private fun DeterministicQrMatrix(payload: String) {
    val gridSize = 21
    Canvas(modifier = Modifier.size(112.dp)) {
        val cell = size.width / gridSize

        fun isFinderModule(r: Int, c: Int): Boolean {
            fun inCorner(r0: Int, c0: Int): Boolean {
                val dr = r - r0
                val dc = c - c0
                if (dr !in 0..6 || dc !in 0..6) return false
                return dr == 0 || dr == 6 || dc == 0 || dc == 6 || (dr in 2..4 && dc in 2..4)
            }
            return inCorner(0, 0) || inCorner(0, gridSize - 7) || inCorner(gridSize - 7, 0)
        }

        fun inFinderQuiet(r: Int, c: Int): Boolean {
            return (r <= 7 && c <= 7) ||
                (r <= 7 && c >= gridSize - 8) ||
                (r >= gridSize - 8 && c <= 7)
        }

        val hashSeed = payload.hashCode()
        for (r in 0 until gridSize) {
            for (c in 0 until gridSize) {
                val dark = when {
                    isFinderModule(r, c) -> true
                    inFinderQuiet(r, c) -> false
                    else -> {
                        val charCode = payload[(r * gridSize + c) % payload.length.coerceAtLeast(1)].code
                        ((charCode * 31 + r * 17 + c * 13 + hashSeed) and 1) == 0
                    }
                }
                if (dark) {
                    drawRect(
                        color = Color(0xFF121218),
                        topLeft = Offset(c * cell, r * cell),
                        size = Size(cell, cell)
                    )
                }
            }
        }
    }
}
