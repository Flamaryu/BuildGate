package dev.buildgate.app.shared.platform

class JsPlatformVerifier : PlatformVerifier {
    override fun isAppInstalled(packageName: String): Boolean = true

    override fun launchTargetApp(packageName: String, sessionToken: String): Boolean = true

    override fun captureDeviceMetadata(): DeviceMetadata {
        return DeviceMetadata(
            model = "Web Browser (JavaScript Engine)",
            osVersion = "Web Client Driver",
            screenResolution = "1920x1080",
            aspectRatio = "16:9",
            platformName = "Compose Web Dashboard"
        )
    }
}

actual fun getPlatformVerifier(): PlatformVerifier = JsPlatformVerifier()
