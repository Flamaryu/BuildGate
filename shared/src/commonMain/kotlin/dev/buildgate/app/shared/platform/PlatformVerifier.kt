package dev.buildgate.app.shared.platform

interface PlatformVerifier {
    fun isAppInstalled(packageName: String): Boolean
    fun launchTargetApp(packageName: String, sessionToken: String): Boolean
    fun captureDeviceMetadata(): DeviceMetadata
}

expect fun getPlatformVerifier(): PlatformVerifier
