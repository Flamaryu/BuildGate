package dev.buildgate.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import dev.buildgate.app.app.navigation.DeepLinkRouter
import dev.buildgate.app.overlay.QaOverlayService
import dev.buildgate.app.shared.platform.initAndroidPlatformVerifier
import dev.buildgate.app.shared.session.GlobalQaSession

class MainActivity : ComponentActivity() {

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Ongoing QA notification fallback enabled when granted */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        initAndroidPlatformVerifier(this)
        GlobalQaSession.coordinator.attachOverlayController(QaOverlayService.createBridge(this))
        requestPostNotificationsIfNeeded()
        handleIncomingIntent(intent)

        setContent {
            App()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        GlobalQaSession.notifyHostResumed()
    }

    override fun onStop() {
        super.onStop()
        GlobalQaSession.notifyHostStopped()
    }

    private fun requestPostNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val uriString = intent?.dataString
        if (!uriString.isNullOrBlank()) {
            DeepLinkRouter.dispatchUri(uriString)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
