package dev.buildgate.app.overlay

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * Zero-UI transparent trampoline Activity that obtains Android 14-17 MediaProjection
 * consent from the system sheet and immediately forwards the token into QaOverlayService.
 */
class QaCapturePermissionActivity : ComponentActivity() {

    private var followUpAction: String = QaOverlayService.ACTION_CAPTURE_SCREENSHOT

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, QaOverlayService::class.java).apply {
                action = QaOverlayService.ACTION_GRANT_PROJECTION
                putExtra(QaOverlayService.EXTRA_PROJECTION_RESULT_CODE, result.resultCode)
                putExtra(QaOverlayService.EXTRA_PROJECTION_DATA, result.data)
                putExtra(QaOverlayService.EXTRA_FOLLOW_UP_ACTION, followUpAction)
            }
            ContextCompat.startForegroundService(this, serviceIntent)
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        followUpAction = intent?.getStringExtra(QaOverlayService.EXTRA_FOLLOW_UP_ACTION)
            ?: QaOverlayService.ACTION_CAPTURE_SCREENSHOT

        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        if (manager == null) {
            finish()
            return
        }
        projectionLauncher.launch(manager.createScreenCaptureIntent())
    }
}
