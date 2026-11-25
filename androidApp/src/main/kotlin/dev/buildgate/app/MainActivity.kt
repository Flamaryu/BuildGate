package dev.buildgate.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.buildgate.app.app.navigation.DeepLinkRouter
import dev.buildgate.app.shared.platform.initAndroidPlatformVerifier
import dev.buildgate.app.shared.session.GlobalQaSession

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        initAndroidPlatformVerifier(this)
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
