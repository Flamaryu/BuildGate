package dev.buildgate.app.shared.platform

data class InstalledPackageInfo(
    val isInstalled: Boolean,
    val versionName: String? = null,
    val versionCode: Long? = null
)

interface PlatformVerifier {
    val supportsDeviceVerification: Boolean
    val supportsFloatingOverlay: Boolean

    fun inspectInstalledApp(packageName: String): InstalledPackageInfo
    fun isAppInstalled(packageName: String): Boolean = inspectInstalledApp(packageName).isInstalled

    fun launchTargetApp(
        packageName: String,
        sessionToken: String,
        deepLinkScheme: String? = null
    ): Boolean

    fun openExternalUrl(url: String)
    fun copyToClipboard(label: String, text: String)

    fun canDrawOverlays(): Boolean = false
    fun requestOverlayPermission() {}

    fun monotonicElapsedMs(): Long
    fun captureDeviceMetadata(): DeviceMetadata
}

expect fun getPlatformVerifier(): PlatformVerifier
