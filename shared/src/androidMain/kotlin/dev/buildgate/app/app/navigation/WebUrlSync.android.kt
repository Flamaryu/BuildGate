package dev.buildgate.app.app.navigation

actual fun getInitialBrowserRouteHash(): String? = null

actual fun syncBrowserRouteHash(hash: String) {
    // No-op on Android native
}
