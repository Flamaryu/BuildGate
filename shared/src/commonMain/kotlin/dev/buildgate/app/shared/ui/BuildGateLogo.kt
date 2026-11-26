package dev.buildgate.app.shared.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Official BuildGate "Dimensional Launch Portal" (Style 02) vector logo.
 * Mirrors webApp/src/webMain/resources/favicon.svg and Android adaptive launcher icon.
 */
@Composable
fun BuildGateLogo(
    size: Dp = 36.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension / 256f
        val cornerRadius = 58f * s

        // 1. Midnight Indigo Rounded-Square Backdrop
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF312E81), Color(0xFF1E1B4B), Color(0xFF0F172A)),
                start = Offset(20f * s, 10f * s),
                end = Offset(236f * s, 246f * s)
            ),
            size = Size(256f * s, 256f * s),
            cornerRadius = CornerRadius(cornerRadius, cornerRadius)
        )

        // 2. Emerald Portal Radial Glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x8C10B981), Color(0x0010B981)),
                center = Offset(128f * s, 142f * s),
                radius = 88f * s
            ),
            radius = 88f * s,
            center = Offset(128f * s, 142f * s)
        )

        // 3. Platform Base Shadow
        drawOval(
            color = Color(0xB30F172A),
            topLeft = Offset(52f * s, 186f * s),
            size = Size(152f * s, 36f * s)
        )

        // 4. Inner Portal Energy Arch
        val innerPortal = Path().apply {
            moveTo(92f * s, 198f * s)
            lineTo(92f * s, 120f * s)
            arcTo(
                rect = Rect(92f * s, 84f * s, 164f * s, 156f * s),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false
            )
            lineTo(164f * s, 198f * s)
            close()
        }
        drawPath(
            path = innerPortal,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xE634D399), Color(0x40059669)),
                startY = 68f * s,
                endY = 204f * s
            )
        )

        // 5. 3D Gradient Portal Arch (Coral -> Violet -> Indigo)
        val outerArch = Path().apply {
            moveTo(70f * s, 198f * s)
            lineTo(70f * s, 118f * s)
            arcTo(
                rect = Rect(70f * s, 60f * s, 186f * s, 176f * s),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false
            )
            lineTo(186f * s, 198f * s)
        }
        drawPath(
            path = outerArch,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFF43F5E), Color(0xFF8B5CF6), Color(0xFF6366F1)),
                start = Offset(60f * s, 44f * s),
                end = Offset(196f * s, 212f * s)
            ),
            style = Stroke(width = 26f * s, cap = StrokeCap.Round)
        )

        // 6. Floating Verified Launch Orb
        drawCircle(
            color = Color(0xFFF8FAFC),
            radius = 30f * s,
            center = Offset(128f * s, 134f * s)
        )

        // 7. Mint Verification Checkmark
        val checkPath = Path().apply {
            moveTo(115f * s, 134f * s)
            lineTo(124f * s, 143f * s)
            lineTo(143f * s, 123f * s)
        }
        drawPath(
            path = checkPath,
            color = Color(0xFF10B981),
            style = Stroke(
                width = 9f * s,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // 8. Floating Sparkles
        drawCircle(
            color = Color(0xFFF59E0B),
            radius = 7f * s,
            center = Offset(196f * s, 64f * s)
        )
        drawCircle(
            color = Color(0xFF34D399),
            radius = 5f * s,
            center = Offset(58f * s, 84f * s)
        )
    }
}
