@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package dev.buildgate.app.shared.platform

import dev.buildgate.app.shared.time.BuildGateClock

@JsFun("(url) => { window.open(url, '_blank', 'noopener,noreferrer'); }")
private external fun wasmOpenExternalUrl(url: String)

@JsFun("(text) => { if (navigator.clipboard) { navigator.clipboard.writeText(text); } }")
private external fun wasmCopyText(text: String)

@JsFun("() => window.innerWidth || 1920")
private external fun wasmInnerWidth(): Int

@JsFun("() => window.innerHeight || 1080")
private external fun wasmInnerHeight(): Int

class WasmJsPlatformVerifier : PlatformVerifier {
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
        if (url.isNotBlank()) {
            wasmOpenExternalUrl(url)
        }
    }

    override fun copyToClipboard(label: String, text: String) {
        wasmCopyText(text)
    }

    override fun monotonicElapsedMs(): Long = BuildGateClock.nowEpochMs()

    override fun captureDeviceMetadata(): DeviceMetadata {
        val w = wasmInnerWidth()
        val h = wasmInnerHeight()
        return DeviceMetadata(
            model = "Web Browser (WasmJS Companion)",
            osVersion = "WebAssembly Runtime",
            screenResolution = "${w}x${h}",
            aspectRatio = "Desktop/Web",
            platformName = "BuildGate Web Command Center"
        )
    }
}

actual fun getPlatformVerifier(): PlatformVerifier = WasmJsPlatformVerifier()
