package dev.buildgate.app.shared.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
