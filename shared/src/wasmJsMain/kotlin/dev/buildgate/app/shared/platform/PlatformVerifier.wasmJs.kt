package dev.buildgate.app.shared.platform

class WasmJsPlatformVerifier : PlatformVerifier {
    override fun isAppInstalled(packageName: String): Boolean {
        // Safe Web stub for package verification
        return true
    }

    override fun launchTargetApp(packageName: String, sessionToken: String): Boolean {
        // Safe Web stub: simulate launching Play Store web link or deep link
        return true
    }

    override fun captureDeviceMetadata(): DeviceMetadata {
        return DeviceMetadata(
            model = "Web Browser (WasmJS / Chrome Engine)",
            osVersion = "Web Assembly OS Driver",
            screenResolution = "1920x1080 (Desktop Wide)",
            aspectRatio = "16:9",
            platformName = "Compose Web Dashboard"
        )
    }
}

actual fun getPlatformVerifier(): PlatformVerifier = WasmJsPlatformVerifier()
