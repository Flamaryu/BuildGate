package dev.buildgate.app.shared.platform

import dev.buildgate.app.shared.time.BuildGateClock

class JsPlatformVerifier : PlatformVerifier {
    override val supportsDeviceVerification: Boolean = false
    override val supportsFloatingOverlay: Boolean = false

    override fun inspectInstalledApp(packageName: String): InstalledPackageInfo =
        InstalledPackageInfo(isInstalled = false)

    override fun launchTargetApp(
        packageName: String,
        sessionToken: String,
        deepLinkScheme: String?
    ): Boolean = false

    override fun openExternalUrl(url: String) {
        if (url.isBlank()) return
        js("window.open(url, '_blank', 'noopener,noreferrer')")
    }

    override fun copyToClipboard(label: String, text: String) {
        js("if (navigator.clipboard) { navigator.clipboard.writeText(text); }")
    }

    override fun monotonicElapsedMs(): Long = BuildGateClock.nowEpochMs()

    override fun captureDeviceMetadata(): DeviceMetadata {
        val width = (js("window.innerWidth || 1920") as Int)
        val height = (js("window.innerHeight || 1080") as Int)
        return DeviceMetadata(
            model = "Web Browser (JS Companion)",
            osVersion = (js("navigator.userAgent || 'Web Client'") as String).take(64),
            screenResolution = "${width}x${height}",
            aspectRatio = "Desktop/Web",
            platformName = "BuildGate Web Command Center"
        )
    }
}

actual fun getPlatformVerifier(): PlatformVerifier = JsPlatformVerifier()
