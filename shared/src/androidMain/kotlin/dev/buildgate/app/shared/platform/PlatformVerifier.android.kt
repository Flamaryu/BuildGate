package dev.buildgate.app.shared.platform

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.provider.Settings

class AndroidPlatformVerifier(
    private val contextProvider: () -> Context?
) : PlatformVerifier {

    override val supportsDeviceVerification: Boolean = true
    override val supportsFloatingOverlay: Boolean = true

    override fun inspectInstalledApp(packageName: String): InstalledPackageInfo {
        val context = contextProvider() ?: return InstalledPackageInfo(isInstalled = false)
        if (packageName.isBlank()) return InstalledPackageInfo(isInstalled = false)
        return try {
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            val verCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.versionCode.toLong()
            }
            InstalledPackageInfo(
                isInstalled = true,
                versionName = pkgInfo.versionName,
                versionCode = verCode
            )
        } catch (_: PackageManager.NameNotFoundException) {
            InstalledPackageInfo(isInstalled = false)
        } catch (_: Exception) {
            InstalledPackageInfo(isInstalled = false)
        }
    }

    override fun launchTargetApp(
        packageName: String,
        sessionToken: String,
        deepLinkScheme: String?
    ): Boolean {
        val context = contextProvider() ?: return false
        if (!inspectInstalledApp(packageName).isInstalled) return false

        return try {
            // Tier 2: Attempt custom deep-link handshake URI first if configured by developer
            if (!deepLinkScheme.isNullOrBlank()) {
                val callbackParam = Uri.encode("buildgate://verify-callback")
                val deepUri = Uri.parse(
                    "${deepLinkScheme.trim()}://buildgate-verify?nonce=$sessionToken&callback=$callbackParam"
                )
                val deepIntent = Intent(Intent.ACTION_VIEW, deepUri).apply {
                    setPackage(packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (deepIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(deepIntent)
                    return true
                }
            }

            // Tier 1: Zero-SDK launcher intent
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return false
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            launchIntent.putExtra("BUILDGATE_SESSION_TOKEN", sessionToken)
            context.startActivity(launchIntent)
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun openExternalUrl(url: String) {
        val context = contextProvider() ?: return
        if (url.isBlank()) return
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    override fun copyToClipboard(label: String, text: String) {
        val context = contextProvider() ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    override fun canDrawOverlays(): Boolean {
        val context = contextProvider() ?: return false
        return Settings.canDrawOverlays(context)
    }

    override fun requestOverlayPermission() {
        val context = contextProvider() ?: return
        runCatching {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    override fun hasUsageStatsPermission(): Boolean {
        val context = contextProvider() ?: return false
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    override fun requestUsageStatsPermission() {
        val context = contextProvider() ?: return
        runCatching {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    override fun isPackageInForeground(packageName: String): Boolean? {
        val context = contextProvider() ?: return null
        if (!hasUsageStatsPermission()) return null

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (powerManager != null && !powerManager.isInteractive) return false

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val now = System.currentTimeMillis()
        // Query last 10 minutes so staying in a single game Activity for >60s still reports it as foreground
        val events = usm.queryEvents(now - 600_000L, now) ?: return null
        val event = UsageEvents.Event()
        var lastForegroundPkg: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            @Suppress("DEPRECATION")
            val isResumed = event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            if (isResumed && !event.packageName.isNullOrBlank()) {
                lastForegroundPkg = event.packageName
            }
        }
        return lastForegroundPkg == packageName
    }

    override fun monotonicElapsedMs(): Long = SystemClock.elapsedRealtime()

    override fun captureDeviceMetadata(): DeviceMetadata {
        val context = contextProvider()
        val displayMetrics = context?.resources?.displayMetrics
        val width = displayMetrics?.widthPixels ?: 1080
        val height = displayMetrics?.heightPixels ?: 2400
        val gcdValue = gcd(width, height)
        val aspect = "${width / gcdValue}:${height / gcdValue}"

        return DeviceMetadata(
            model = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            screenResolution = "${width}x${height}",
            aspectRatio = aspect,
            platformName = "Android Mobile"
        )
    }

    private fun gcd(a: Int, b: Int): Int {
        var x = a
        var y = b
        while (y != 0) {
            val t = y
            y = x % y
            x = t
        }
        return if (x == 0) 1 else x
    }
}

private var appContextProvider: (() -> Context?) = { null }

fun initAndroidPlatformVerifier(context: Context) {
    val appCtx = context.applicationContext
    appContextProvider = { appCtx }
}

actual fun getPlatformVerifier(): PlatformVerifier = AndroidPlatformVerifier(appContextProvider)
