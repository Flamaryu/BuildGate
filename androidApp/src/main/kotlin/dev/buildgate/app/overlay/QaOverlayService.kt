package dev.buildgate.app.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.buildgate.app.MainActivity
import dev.buildgate.app.entities.feedbackitem.model.FeedbackCategory
import dev.buildgate.app.shared.platform.getPlatformVerifier
import dev.buildgate.app.shared.session.ActiveQaSessionState
import dev.buildgate.app.shared.session.CapturedQaMediaDraft
import dev.buildgate.app.shared.session.GlobalQaSession
import dev.buildgate.app.shared.session.OverlayBugSubmission
import dev.buildgate.app.shared.session.OverlayVerifyRequest
import dev.buildgate.app.shared.session.QaMediaCaptureBus
import dev.buildgate.app.shared.session.QaOverlayController
import dev.buildgate.app.shared.session.QaSessionPhase
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.abs

/**
 * Floating QA Overlay Pill (SYSTEM_ALERT_WINDOW) + MediaProjection 1-Tap Screenshot & 15s Clip
 * + Ongoing Notification Fallback for seamless Android closed-testing bug logging.
 */
class QaOverlayService : Service() {

    companion object {
        const val CHANNEL_ID = "buildgate_qa_overlay"
        const val NOTIFICATION_ID = 4201

        const val ACTION_SYNC_SESSION = "dev.buildgate.app.overlay.SYNC_SESSION"
        const val ACTION_PAUSE_AND_OPEN_FORM = "dev.buildgate.app.overlay.PAUSE_OPEN_FORM"
        const val ACTION_RESUME_SESSION = "dev.buildgate.app.overlay.RESUME_SESSION"
        const val ACTION_CAPTURE_SCREENSHOT = "dev.buildgate.app.overlay.CAPTURE_SCREENSHOT"
        const val ACTION_RECORD_CLIP_15S = "dev.buildgate.app.overlay.RECORD_CLIP_15S"
        const val ACTION_STOP_RECORDING = "dev.buildgate.app.overlay.STOP_RECORDING"
        const val ACTION_GRANT_PROJECTION = "dev.buildgate.app.overlay.GRANT_PROJECTION"
        const val ACTION_VERIFY_AND_LAUNCH_NEXT = "dev.buildgate.app.overlay.VERIFY_AND_LAUNCH_NEXT"
        const val ACTION_STOP_SERVICE = "dev.buildgate.app.overlay.STOP_SERVICE"

        const val EXTRA_PROJECTION_RESULT_CODE = "projection_result_code"
        const val EXTRA_PROJECTION_DATA = "projection_data"
        const val EXTRA_FOLLOW_UP_ACTION = "follow_up_action"

        private const val MAX_CLIP_DURATION_MS = 15_000L

        fun createBridge(context: Context): QaOverlayController = object : QaOverlayController {
            override fun onSessionUpdated(state: ActiveQaSessionState) {
                val appCtx = context.applicationContext
                if (state.phase == QaSessionPhase.IDLE) return
                val intent = Intent(appCtx, QaOverlayService::class.java).apply {
                    action = ACTION_SYNC_SESSION
                }
                runCatching { ContextCompat.startForegroundService(appCtx, intent) }
            }

            override fun onSessionStopped() {
                val appCtx = context.applicationContext
                val intent = Intent(appCtx, QaOverlayService::class.java).apply {
                    action = ACTION_STOP_SERVICE
                }
                runCatching { appCtx.startService(intent) }
            }
        }
    }

    private enum class OverlayMode {
        COLLAPSED_PILL,
        EXPANDED_MENU,
        INLINE_BUG_SHEET
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var rootOverlayContainer: LinearLayout? = null
    private var pillLabelView: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var overlayMode: OverlayMode = OverlayMode.COLLAPSED_PILL
    private var lastRenderedPhase: QaSessionPhase? = null

    private var mediaProjection: MediaProjection? = null
    private var activeVirtualDisplay: VirtualDisplay? = null
    private var activeRecorder: MediaRecorder? = null
    private var recordingOutputFile: File? = null
    private var recordingStartedAtMs: Long = 0L
    private var isRecordingClip: Boolean = false

    private var pendingScreenshotBytes: ByteArray? = null
    private var pendingVideoBytes: ByteArray? = null
    private var pendingMediaDurationMs: Int? = null
    private var pendingPreviewPath: String? = null

    private val tickerRunnable = object : Runnable {
        override fun run() {
            val preSnap = GlobalQaSession.coordinator.state.value
            val activePkg = preSnap.activeApp?.packageName
            if (!activePkg.isNullOrBlank() &&
                (preSnap.phase == QaSessionPhase.RUNNING_IN_TARGET_APP ||
                 preSnap.phase == QaSessionPhase.PAUSED_TARGET_BACKGROUNDED)
            ) {
                // Allow a 3s grace period right after launch while Android transitions to the target app
                val sinceLaunchMs = SystemClock.elapsedRealtime() - preSnap.sessionLaunchedRealtimeMs
                if (sinceLaunchMs >= 3_000L) {
                    val inForeground = getPlatformVerifier().isPackageInForeground(activePkg)
                    if (inForeground != null) {
                        GlobalQaSession.coordinator.onTargetForegroundChanged(inForeground)
                    }
                }
            }
            val snap = GlobalQaSession.coordinator.evaluateProgress()
            updatePillAndNotificationText(snap)
            if (snap.phase != QaSessionPhase.IDLE) {
                mainHandler.postDelayed(this, 1000L)
            }
        }
    }

    private val autoStopClipRunnable = Runnable {
        if (isRecordingClip) {
            stopScreenClipAndOpenBugSheet()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_SYNC_SESSION

        if (action == ACTION_STOP_SERVICE) {
            tearDownOverlayAndProjection()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val currentSnap = GlobalQaSession.coordinator.state.value
        val hasProjection = mediaProjection != null
        startForegroundSafely(buildNotification(currentSnap), withMediaProjection = hasProjection)

        ensureOverlayAttachedIfPermitted()
        mainHandler.removeCallbacks(tickerRunnable)
        mainHandler.post(tickerRunnable)

        when (action) {
            ACTION_GRANT_PROJECTION -> {
                val resCode = intent?.getIntExtra(EXTRA_PROJECTION_RESULT_CODE, -1) ?: -1
                val resData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent?.getParcelableExtra(EXTRA_PROJECTION_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent?.getParcelableExtra(EXTRA_PROJECTION_DATA)
                }
                val followUp = intent?.getStringExtra(EXTRA_FOLLOW_UP_ACTION) ?: ACTION_CAPTURE_SCREENSHOT
                if (resCode == -1 && resData != null) {
                    // Upgrade FGS type to MEDIA_PROJECTION BEFORE obtaining MediaProjection (Android 14+ rule)
                    startForegroundSafely(buildNotification(currentSnap), withMediaProjection = true)
                    val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
                    mediaProjection?.stop()
                    mediaProjection = mgr?.getMediaProjection(resCode, resData)?.also { proj ->
                        proj.registerCallback(object : MediaProjection.Callback() {
                            override fun onStop() {
                                releaseVirtualDisplayOnly()
                                mediaProjection = null
                            }
                        }, mainHandler)
                    }
                    if (followUp == ACTION_RECORD_CLIP_15S) {
                        startScreenClip15s()
                    } else {
                        captureOneTapScreenshot()
                    }
                }
            }
            ACTION_PAUSE_AND_OPEN_FORM -> {
                GlobalQaSession.coordinator.pauseForFeedback("Paused from QA Overlay")
                if (Settings.canDrawOverlays(this)) {
                    switchOverlayMode(OverlayMode.INLINE_BUG_SHEET)
                } else {
                    openBuildGateTriageScreen(currentSnap.activeApp?.id ?: "app_1")
                }
            }
            ACTION_RESUME_SESSION -> {
                GlobalQaSession.coordinator.resumeFromFeedback()
                switchOverlayMode(OverlayMode.COLLAPSED_PILL)
            }
            ACTION_CAPTURE_SCREENSHOT -> {
                if (mediaProjection == null) {
                    requestMediaProjectionConsent(ACTION_CAPTURE_SCREENSHOT)
                } else {
                    captureOneTapScreenshot()
                }
            }
            ACTION_RECORD_CLIP_15S -> {
                if (isRecordingClip) {
                    stopScreenClipAndOpenBugSheet()
                } else if (mediaProjection == null) {
                    requestMediaProjectionConsent(ACTION_RECORD_CLIP_15S)
                } else {
                    startScreenClip15s()
                }
            }
            ACTION_STOP_RECORDING -> {
                stopScreenClipAndOpenBugSheet()
            }
            ACTION_VERIFY_AND_LAUNCH_NEXT -> {
                verifyCurrentAndLaunchNextApp()
            }
        }

        updatePillAndNotificationText(GlobalQaSession.coordinator.state.value)
        return START_STICKY
    }

    private fun startForegroundSafely(notification: Notification, withMediaProjection: Boolean) {
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (withMediaProjection) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            }
        } else {
            0
        }
        runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
        }
    }

    private fun requestMediaProjectionConsent(followUpAction: String) {
        GlobalQaSession.coordinator.pauseForFeedback("Granting screen capture for bug attachment")
        val consentIntent = Intent(this, QaCapturePermissionActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(EXTRA_FOLLOW_UP_ACTION, followUpAction)
        }
        startActivity(consentIntent)
    }

    private fun captureOneTapScreenshot() {
        val projection = mediaProjection ?: return
        GlobalQaSession.coordinator.pauseForFeedback("Capturing QA screenshot")

        // Hide overlay pill for 1 frame so screenshot shows only the tested app
        rootOverlayContainer?.visibility = View.INVISIBLE

        mainHandler.postDelayed({
            val metrics = resources.displayMetrics
            val width = (metrics.widthPixels / 2).coerceAtLeast(540)
            val height = (metrics.heightPixels / 2).coerceAtLeast(960)
            val density = metrics.densityDpi

            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            val vd = projection.createVirtualDisplay(
                "BuildGateShot",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                mainHandler
            )

            mainHandler.postDelayed({
                runCatching {
                    val image = reader.acquireLatestImage()
                    if (image != null) {
                        val plane = image.planes[0]
                        val buffer = plane.buffer
                        val pixelStride = plane.pixelStride
                        val rowStride = plane.rowStride
                        val rowPadding = rowStride - pixelStride * width

                        val bmp = Bitmap.createBitmap(
                            width + rowPadding / pixelStride,
                            height,
                            Bitmap.Config.ARGB_8888
                        )
                        bmp.copyPixelsFromBuffer(buffer)
                        val cropped = Bitmap.createBitmap(bmp, 0, 0, width, height)
                        image.close()

                        val bos = ByteArrayOutputStream()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            cropped.compress(Bitmap.CompressFormat.WEBP_LOSSY, 82, bos)
                        } else {
                            @Suppress("DEPRECATION")
                            cropped.compress(Bitmap.CompressFormat.WEBP, 82, bos)
                        }
                        val bytes = bos.toByteArray()
                        val file = File(cacheDir, "bg_shot_${System.currentTimeMillis()}.webp")
                        file.writeBytes(bytes)

                        pendingScreenshotBytes = bytes
                        pendingVideoBytes = null
                        pendingMediaDurationMs = null
                        pendingPreviewPath = file.absolutePath

                        publishDraftToBus()
                    }
                }
                vd?.release()
                reader.close()
                rootOverlayContainer?.visibility = View.VISIBLE

                if (Settings.canDrawOverlays(this)) {
                    switchOverlayMode(OverlayMode.INLINE_BUG_SHEET)
                } else {
                    openBuildGateTriageScreen(GlobalQaSession.coordinator.state.value.activeApp?.id ?: "app_1")
                }
            }, 180L)
        }, 100L)
    }

    private fun startScreenClip15s() {
        val projection = mediaProjection ?: return
        if (isRecordingClip) return

        val outFile = File(cacheDir, "bg_clip_${System.currentTimeMillis()}.mp4")
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        val width = 720
        val height = 1280
        val density = resources.displayMetrics.densityDpi

        val started = runCatching {
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            recorder.setVideoEncodingBitRate(2_500_000)
            recorder.setVideoFrameRate(24)
            recorder.setVideoSize(width, height)
            recorder.setOutputFile(outFile.absolutePath)
            recorder.prepare()

            activeVirtualDisplay = projection.createVirtualDisplay(
                "BuildGateClip",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                recorder.surface,
                null,
                mainHandler
            )
            recorder.start()
            true
        }.getOrDefault(false)

        if (started) {
            activeRecorder = recorder
            recordingOutputFile = outFile
            recordingStartedAtMs = SystemClock.elapsedRealtime()
            isRecordingClip = true
            GlobalQaSession.coordinator.pauseForFeedback("Recording 15s bug clip")
            switchOverlayMode(OverlayMode.COLLAPSED_PILL)
            mainHandler.postDelayed(autoStopClipRunnable, MAX_CLIP_DURATION_MS)
            updatePillAndNotificationText(GlobalQaSession.coordinator.state.value)
        } else {
            runCatching { recorder.release() }
        }
    }

    private fun stopScreenClipAndOpenBugSheet() {
        mainHandler.removeCallbacks(autoStopClipRunnable)
        if (!isRecordingClip) return
        isRecordingClip = false

        val elapsed = (SystemClock.elapsedRealtime() - recordingStartedAtMs).toInt().coerceIn(1000, 15_000)
        runCatching { activeRecorder?.stop() }
        runCatching { activeRecorder?.reset() }
        runCatching { activeRecorder?.release() }
        activeRecorder = null
        releaseVirtualDisplayOnly()

        val clipFile = recordingOutputFile
        if (clipFile != null && clipFile.exists() && clipFile.length() > 0L) {
            pendingVideoBytes = clipFile.readBytes()
            pendingScreenshotBytes = null
            pendingMediaDurationMs = elapsed
            pendingPreviewPath = clipFile.absolutePath
            publishDraftToBus()
        }

        GlobalQaSession.coordinator.pauseForFeedback("Attaching ${elapsed / 1000}s screen clip")
        if (Settings.canDrawOverlays(this)) {
            switchOverlayMode(OverlayMode.INLINE_BUG_SHEET)
        } else {
            openBuildGateTriageScreen(GlobalQaSession.coordinator.state.value.activeApp?.id ?: "app_1")
        }
    }

    private fun publishDraftToBus() {
        val snap = GlobalQaSession.coordinator.state.value
        val app = snap.activeApp ?: return
        QaMediaCaptureBus.publishCapturedDraft(
            CapturedQaMediaDraft(
                appId = app.id,
                appTitle = app.title,
                buildVersion = app.currentBuildVersion,
                sessionToken = snap.sessionToken,
                screenshotBytes = pendingScreenshotBytes,
                screenshotFileName = if (pendingScreenshotBytes != null) "overlay_shot.webp" else null,
                videoClipBytes = pendingVideoBytes,
                videoFileName = if (pendingVideoBytes != null) "overlay_clip.mp4" else null,
                mediaDurationMs = pendingMediaDurationMs,
                localPreviewUri = pendingPreviewPath
            )
        )
    }

    private fun ensureOverlayAttachedIfPermitted() {
        if (!Settings.canDrawOverlays(this)) return
        if (rootOverlayContainer != null) return

        val wm = windowManager ?: return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(12)
            y = dp(140)
        }
        layoutParams = params

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        rootOverlayContainer = container
        rebuildOverlayContent(OverlayMode.COLLAPSED_PILL)

        runCatching { wm.addView(container, params) }
    }

    private fun switchOverlayMode(mode: OverlayMode) {
        overlayMode = mode
        val wm = windowManager ?: return
        val container = rootOverlayContainer ?: return
        val params = layoutParams ?: return

        params.flags = if (mode == OverlayMode.INLINE_BUG_SHEET) {
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }
        rebuildOverlayContent(mode)
        runCatching { wm.updateViewLayout(container, params) }
    }

    private fun rebuildOverlayContent(mode: OverlayMode) {
        val container = rootOverlayContainer ?: return
        container.removeAllViews()
        pillLabelView = null

        val snap = GlobalQaSession.coordinator.state.value
        val appTitle = snap.activeApp?.title?.substringBefore(" -") ?: "BuildGate QA"

        when (mode) {
            OverlayMode.COLLAPSED_PILL -> {
                val pillColors = pillColorsFor(snap)
                val pill = TextView(this).apply {
                    background = roundedBg(
                        fillColor = pillColors.first,
                        strokeColor = pillColors.second,
                        radiusDp = 24
                    )
                    setTextColor(Color.WHITE)
                    typeface = Typeface.DEFAULT_BOLD
                    textSize = 13f
                    setPadding(dp(14), dp(10), dp(14), dp(10))
                    text = formatCollapsedText(snap)
                }
                pillLabelView = pill
                attachDragAndClickHandler(pill) {
                    if (isRecordingClip) {
                        stopScreenClipAndOpenBugSheet()
                    } else {
                        switchOverlayMode(OverlayMode.EXPANDED_MENU)
                    }
                }
                container.addView(pill)
            }

            OverlayMode.EXPANDED_MENU -> {
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = roundedBg(Color.parseColor("#181825"), Color.parseColor("#6366F1"), 20)
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                }

                val header = TextView(this).apply {
                    text = "⚡ $appTitle (${snap.activeApp?.currentBuildVersion ?: "beta"})"
                    setTextColor(Color.WHITE)
                    typeface = Typeface.DEFAULT_BOLD
                    textSize = 13f
                    setPadding(0, 0, 0, dp(8))
                }
                card.addView(header)

                val isReadyForNext = (snap.phase == QaSessionPhase.READY_TO_VERIFY || snap.phase == QaSessionPhase.VERIFIED)
                val nextQueued = snap.nextQueuedApp
                if (isReadyForNext && nextQueued != null) {
                    val nextBtn = Button(this).apply {
                        text = "✅ Verify & Launch Next: ${nextQueued.title.substringBefore(":")} ➔"
                        isAllCaps = false
                        setTextColor(Color.WHITE)
                        typeface = Typeface.DEFAULT_BOLD
                        textSize = 12.5f
                        background = roundedBg(Color.parseColor("#059669"), Color.parseColor("#34D399"), 12)
                        setPadding(dp(12), dp(10), dp(12), dp(10))
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = dp(8) }
                        setOnClickListener { verifyCurrentAndLaunchNextApp() }
                    }
                    card.addView(nextBtn)
                } else if (isReadyForNext && nextQueued == null) {
                    val finishBtn = Button(this).apply {
                        text = "✅ Finish Queue & Close Pill"
                        isAllCaps = false
                        setTextColor(Color.WHITE)
                        typeface = Typeface.DEFAULT_BOLD
                        textSize = 12.5f
                        background = roundedBg(Color.parseColor("#059669"), Color.parseColor("#34D399"), 12)
                        setPadding(dp(12), dp(10), dp(12), dp(10))
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = dp(8) }
                        setOnClickListener { verifyCurrentAndLaunchNextApp() }
                    }
                    card.addView(finishBtn)
                } else if (snap.phase == QaSessionPhase.PAUSED_TARGET_BACKGROUNDED && snap.activeApp != null) {
                    val returnTargetBtn = Button(this).apply {
                        text = "▶️ Return to ${appTitle.substringBefore(":")} to Resume"
                        isAllCaps = false
                        setTextColor(Color.WHITE)
                        typeface = Typeface.DEFAULT_BOLD
                        textSize = 12.5f
                        background = roundedBg(Color.parseColor("#B45309"), Color.parseColor("#FBBF24"), 12)
                        setPadding(dp(12), dp(10), dp(12), dp(10))
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = dp(8) }
                        setOnClickListener {
                            val active = snap.activeApp ?: return@setOnClickListener
                            getPlatformVerifier().launchTargetApp(active.packageName, snap.sessionToken, active.deepLinkScheme)
                            switchOverlayMode(OverlayMode.COLLAPSED_PILL)
                        }
                    }
                    card.addView(returnTargetBtn)
                }

                card.addView(menuButton("📸 1-Tap Screenshot & Pause") {
                    if (mediaProjection == null) requestMediaProjectionConsent(ACTION_CAPTURE_SCREENSHOT)
                    else captureOneTapScreenshot()
                })
                card.addView(menuButton("🎥 Record 15s Clip & Pause") {
                    if (mediaProjection == null) requestMediaProjectionConsent(ACTION_RECORD_CLIP_15S)
                    else startScreenClip15s()
                })
                card.addView(menuButton("🐛 Quick Bug Note (Pause Queue)") {
                    GlobalQaSession.coordinator.pauseForFeedback("Quick Bug Note")
                    switchOverlayMode(OverlayMode.INLINE_BUG_SHEET)
                })
                if (!isReadyForNext && nextQueued != null) {
                    card.addView(menuButton("⏭️ Skip to Next: ${nextQueued.title.substringBefore(":")}") {
                        launchNextQueuedAppDirectly(verifyCurrent = false)
                    })
                }
                card.addView(menuButton("🏠 Return to BuildGate Queue") {
                    openBuildGateQueueScreen()
                    switchOverlayMode(OverlayMode.COLLAPSED_PILL)
                })
                card.addView(menuButton("✕ Minimize Pill") {
                    switchOverlayMode(OverlayMode.COLLAPSED_PILL)
                })
                card.addView(menuButton("🛑 Close QA Pill") {
                    dismissOverlayCompletely()
                })

                container.addView(card)
            }

            OverlayMode.INLINE_BUG_SHEET -> {
                val sheet = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = roundedBg(Color.parseColor("#181825"), Color.parseColor("#F59E0B"), 20)
                    setPadding(dp(16), dp(14), dp(16), dp(14))
                }

                val specs = getPlatformVerifier().captureDeviceMetadata()
                val titleHeader = TextView(this).apply {
                    text = "⏸️ Session Paused • Log Bug for $appTitle"
                    setTextColor(Color.parseColor("#FCD34D"))
                    typeface = Typeface.DEFAULT_BOLD
                    textSize = 13f
                }
                sheet.addView(titleHeader)

                val mediaStatus = when {
                    pendingScreenshotBytes != null -> "📸 Screenshot Attached (${pendingScreenshotBytes!!.size / 1024} KB)"
                    pendingVideoBytes != null -> "🎥 ${(pendingMediaDurationMs ?: 0) / 1000}s MP4 Clip Attached"
                    else -> "📱 Specs Attached: ${specs.model}"
                }
                val subHeader = TextView(this).apply {
                    text = "$mediaStatus • Queue Held"
                    setTextColor(Color.parseColor("#CBD5E1"))
                    textSize = 11f
                    setPadding(0, dp(4), 0, dp(8))
                }
                sheet.addView(subHeader)

                val titleInput = EditText(this).apply {
                    hint = "Bug / Friction Title"
                    setHintTextColor(Color.parseColor("#94A3B8"))
                    setTextColor(Color.WHITE)
                    background = roundedBg(Color.parseColor("#27273A"), Color.parseColor("#475569"), 10)
                    setPadding(dp(10), dp(8), dp(10), dp(8))
                    minWidth = dp(250)
                }
                sheet.addView(titleInput)

                val descInput = EditText(this).apply {
                    hint = "What happened?"
                    setHintTextColor(Color.parseColor("#94A3B8"))
                    setTextColor(Color.WHITE)
                    background = roundedBg(Color.parseColor("#27273A"), Color.parseColor("#475569"), 10)
                    setPadding(dp(10), dp(8), dp(10), dp(8))
                    minLines = 2
                }
                val descLp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(8); bottomMargin = dp(10) }
                sheet.addView(descInput, descLp)

                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                }

                val cancelBtn = Button(this).apply {
                    text = "Cancel & Resume"
                    isAllCaps = false
                    setOnClickListener {
                        clearPendingAttachments()
                        GlobalQaSession.coordinator.resumeFromFeedback()
                        switchOverlayMode(OverlayMode.COLLAPSED_PILL)
                    }
                }
                val submitBtn = Button(this).apply {
                    text = "Submit & Resume"
                    isAllCaps = false
                    setOnClickListener {
                        val activeApp = snap.activeApp
                        val issueTitle = titleInput.text?.toString()?.trim().orEmpty()
                            .ifBlank { "In-App QA Issue on $appTitle" }
                        val issueDesc = descInput.text?.toString()?.trim().orEmpty()
                            .ifBlank { "Captured directly via BuildGate Floating QA Overlay." }

                        if (activeApp != null) {
                            QaMediaCaptureBus.submitFromOverlay(
                                OverlayBugSubmission(
                                    appId = activeApp.id,
                                    sessionToken = snap.sessionToken,
                                    buildVersion = activeApp.currentBuildVersion,
                                    category = FeedbackCategory.BUG,
                                    title = issueTitle,
                                    description = issueDesc,
                                    deviceMetadata = specs,
                                    screenshotBytes = pendingScreenshotBytes,
                                    screenshotFileName = if (pendingScreenshotBytes != null) "overlay_shot.webp" else null,
                                    videoClipBytes = pendingVideoBytes,
                                    videoFileName = if (pendingVideoBytes != null) "overlay_clip.mp4" else null,
                                    mediaDurationMs = pendingMediaDurationMs,
                                    localPreviewUri = pendingPreviewPath
                                )
                            )
                        }
                        clearPendingAttachments()
                        GlobalQaSession.coordinator.resumeFromFeedback()
                        switchOverlayMode(OverlayMode.COLLAPSED_PILL)
                    }
                }
                row.addView(cancelBtn)
                row.addView(submitBtn)
                sheet.addView(row)

                container.addView(sheet)
            }
        }
    }

    private fun clearPendingAttachments() {
        pendingScreenshotBytes = null
        pendingVideoBytes = null
        pendingMediaDurationMs = null
        pendingPreviewPath = null
    }

    private fun menuButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 12f
            setOnClickListener { onClick() }
        }
    }

    private fun attachDragAndClickHandler(view: View, onTap: () -> Unit) {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        view.setOnTouchListener { v, event ->
            val params = layoutParams ?: return@setOnTouchListener false
            val wm = windowManager ?: return@setOnTouchListener false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (touchX - event.rawX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) {
                        moved = true
                    }
                    params.x = (initialX + dx).coerceAtLeast(0)
                    params.y = (initialY + dy).coerceAtLeast(0)
                    runCatching { wm.updateViewLayout(rootOverlayContainer, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        v.performClick()
                        onTap()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun pillColorsFor(snap: ActiveQaSessionState): Pair<Int, Int> = when {
        isRecordingClip -> Color.parseColor("#B91C1C") to Color.parseColor("#FCA5A5")
        snap.phase == QaSessionPhase.READY_TO_VERIFY || snap.phase == QaSessionPhase.VERIFIED ->
            Color.parseColor("#065F46") to Color.parseColor("#34D399")
        snap.phase == QaSessionPhase.PAUSED_TARGET_BACKGROUNDED ->
            Color.parseColor("#92400E") to Color.parseColor("#FBBF24")
        snap.phase == QaSessionPhase.PAUSED_FOR_FEEDBACK ->
            Color.parseColor("#3730A3") to Color.parseColor("#A5B4FC")
        else -> Color.parseColor("#1E1B4B") to Color.parseColor("#818CF8")
    }

    private fun verifyCurrentAndLaunchNextApp() = launchNextQueuedAppDirectly(verifyCurrent = true)

    private fun launchNextQueuedAppDirectly(verifyCurrent: Boolean) {
        val snap = GlobalQaSession.coordinator.state.value
        val currentApp = snap.activeApp
        if (verifyCurrent && currentApp != null) {
            val verifier = getPlatformVerifier()
            val pkgInfo = verifier.inspectInstalledApp(currentApp.packageName)
            QaMediaCaptureBus.requestVerifyFromOverlay(
                OverlayVerifyRequest(
                    appId = currentApp.id,
                    appTitle = currentApp.title,
                    enrollmentId = snap.enrollmentId,
                    testerId = snap.testerId ?: "tester_1",
                    sessionToken = snap.sessionToken.ifBlank { "bg_sess_${(100000..999999).random()}" },
                    targetDays = currentApp.targetDays,
                    installedVersionCode = pkgInfo.versionCode
                )
            )
            GlobalQaSession.coordinator.markVerified(viaDeepLink = false)
        }

        val newToken = "bg_sess_${(100000..999999).random()}"
        val nextApp = GlobalQaSession.coordinator.advanceAndStartNextQueuedApp(
            sessionToken = newToken,
            targetDurationMs = 60_000L
        )
        if (nextApp != null) {
            val launched = getPlatformVerifier().launchTargetApp(
                packageName = nextApp.packageName,
                sessionToken = newToken,
                deepLinkScheme = nextApp.deepLinkScheme
            )
            switchOverlayMode(OverlayMode.COLLAPSED_PILL)
            if (!launched) {
                openBuildGateQueueScreen()
            }
        } else {
            openBuildGateQueueScreen()
            dismissOverlayCompletely()
        }
    }

    private fun dismissOverlayCompletely() {
        GlobalQaSession.coordinator.stopSession()
        tearDownOverlayAndProjection()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun formatCollapsedText(snap: ActiveQaSessionState): String {
        if (isRecordingClip) {
            val recSec = ((MAX_CLIP_DURATION_MS - (SystemClock.elapsedRealtime() - recordingStartedAtMs)).coerceAtLeast(0L) / 1000L).toInt()
            return "🔴 REC ${recSec}s (Tap Stop)"
        }
        val shortTitle = snap.activeApp?.title?.substringBefore(":")?.substringBefore(" ") ?: "QA"
        val rem = snap.secondsRemaining(SystemClock.elapsedRealtime())
        return when (snap.phase) {
            QaSessionPhase.PAUSED_FOR_FEEDBACK -> "⏸️ Paused • $shortTitle"
            QaSessionPhase.PAUSED_TARGET_BACKGROUNDED -> "⏸️ Open $shortTitle • ${rem}s"
            QaSessionPhase.READY_TO_VERIFY, QaSessionPhase.VERIFIED -> {
                val nextShort = snap.nextQueuedApp?.title?.substringBefore(":")
                if (nextShort != null) "✅ Done • Next: $nextShort ➔" else "✅ $shortTitle Verified!"
            }
            QaSessionPhase.RUNNING_IN_TARGET_APP -> "⚡ ${rem}s • $shortTitle"
            QaSessionPhase.IDLE -> "⚡ BuildGate QA"
        }
    }

    private fun updatePillAndNotificationText(snap: ActiveQaSessionState) {
        if (overlayMode == OverlayMode.EXPANDED_MENU && snap.phase != lastRenderedPhase) {
            lastRenderedPhase = snap.phase
            rebuildOverlayContent(OverlayMode.EXPANDED_MENU)
        } else {
            lastRenderedPhase = snap.phase
        }
        pillLabelView?.let { pill ->
            val colors = pillColorsFor(snap)
            pill.background = roundedBg(colors.first, colors.second, 24)
            pill.text = formatCollapsedText(snap)
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.notify(NOTIFICATION_ID, buildNotification(snap))
    }

    private fun buildNotification(snap: ActiveQaSessionState): Notification {
        val appTitle = snap.activeApp?.title ?: "Active Closed Test"
        val rem = snap.secondsRemaining(SystemClock.elapsedRealtime())

        val statusLine = when {
            isRecordingClip -> "Recording 15s bug clip — tap Stop Clip when done"
            snap.phase == QaSessionPhase.PAUSED_FOR_FEEDBACK -> "Paused ($rem s left) • Queue held while logging bug"
            snap.phase == QaSessionPhase.PAUSED_TARGET_BACKGROUNDED -> "Timer paused ($rem s left) • Re-open $appTitle to continue"
            snap.phase == QaSessionPhase.VERIFIED || snap.phase == QaSessionPhase.READY_TO_VERIFY -> {
                val nextName = snap.nextQueuedApp?.title
                if (nextName != null) "60s Verified! Tap Verify & Next to launch $nextName"
                else "60s Verified! Return to BuildGate to claim Karma"
            }
            else -> "Keep $appTitle active ($rem s left) • Use overlay or actions below to log bugs"
        }

        val openQueuePending = PendingIntent.getActivity(
            this,
            100,
            Intent(this, MainActivity::class.java).apply {
                data = Uri.parse("buildgate://queue")
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseOrResumeIntent = Intent(this, QaOverlayService::class.java).apply {
            action = if (snap.phase == QaSessionPhase.PAUSED_FOR_FEEDBACK) {
                ACTION_RESUME_SESSION
            } else {
                ACTION_PAUSE_AND_OPEN_FORM
            }
        }
        val pauseOrResumePending = PendingIntent.getService(
            this,
            101,
            pauseOrResumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val shotIntent = Intent(this, QaOverlayService::class.java).apply {
            action = if (isRecordingClip) ACTION_STOP_RECORDING else ACTION_CAPTURE_SCREENSHOT
        }
        val shotPending = PendingIntent.getService(
            this,
            102,
            shotIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isReadyForNext = (snap.phase == QaSessionPhase.READY_TO_VERIFY || snap.phase == QaSessionPhase.VERIFIED) && snap.hasNextQueuedApp
        val nextIntent = Intent(this, QaOverlayService::class.java).apply {
            action = ACTION_VERIFY_AND_LAUNCH_NEXT
        }
        val nextPending = PendingIntent.getService(
            this,
            103,
            nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopPending = PendingIntent.getService(
            this,
            104,
            Intent(this, QaOverlayService::class.java).apply { action = ACTION_STOP_SERVICE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("BuildGate QA • $appTitle")
            .setContentText(statusLine)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openQueuePending)
        if (isReadyForNext) {
            builder.addAction(0, "Verify & Next App ➔", nextPending)
        } else if (snap.phase == QaSessionPhase.READY_TO_VERIFY || snap.phase == QaSessionPhase.VERIFIED) {
            builder.addAction(0, "Close Pill", stopPending)
        }
        return builder
            .addAction(
                0,
                if (snap.phase == QaSessionPhase.PAUSED_FOR_FEEDBACK) "Resume Timer" else "Pause & Log Bug",
                pauseOrResumePending
            )
            .addAction(
                0,
                if (isRecordingClip) "Stop 15s Clip" else "Capture Screenshot",
                shotPending
            )
            .build()
    }

    private fun openBuildGateTriageScreen(appId: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            data = Uri.parse("buildgate://triage/$appId")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    private fun openBuildGateQueueScreen() {
        val intent = Intent(this, MainActivity::class.java).apply {
            data = Uri.parse("buildgate://queue")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "BuildGate Active QA Overlay & Timer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls active closed-testing timers, bug pausing, screenshots, and 15s clips"
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun roundedBg(fillColor: Int, strokeColor: Int, radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(fillColor)
            setStroke(dp(1), strokeColor)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun releaseVirtualDisplayOnly() {
        runCatching { activeVirtualDisplay?.release() }
        activeVirtualDisplay = null
    }

    private fun tearDownOverlayAndProjection() {
        mainHandler.removeCallbacksAndMessages(null)
        runCatching { activeRecorder?.release() }
        activeRecorder = null
        releaseVirtualDisplayOnly()
        runCatching { mediaProjection?.stop() }
        mediaProjection = null

        val wm = windowManager
        val view = rootOverlayContainer
        if (wm != null && view != null) {
            runCatching { wm.removeView(view) }
        }
        rootOverlayContainer = null
        pillLabelView = null
    }

    override fun onDestroy() {
        tearDownOverlayAndProjection()
        super.onDestroy()
    }
}
