package dev.buildgate.app.shared.platform

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

class AndroidPlatformVerifier(
    private val contextProvider: () -> Context?
) : PlatformVerifier {

    override fun isAppInstalled(packageName: String): Boolean {
        val context = contextProvider() ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    override fun launchTargetApp(packageName: String, sessionToken: String): Boolean {
        val context = contextProvider() ?: return false
        return try {
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.putExtra("BUILDGATE_SESSION_TOKEN", sessionToken)
                context.startActivity(intent)
                true
            } else {
                // Fallback to Play Store or Opt-in link if not direct launchable
                val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(marketIntent)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

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
