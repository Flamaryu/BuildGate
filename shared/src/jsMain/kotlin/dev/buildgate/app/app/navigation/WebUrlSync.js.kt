package dev.buildgate.app.app.navigation

actual fun getInitialBrowserRouteHash(): String? {
    val raw = js("window.location.hash || ''") as String
    return raw.takeIf { it.isNotBlank() }
}

actual fun syncBrowserRouteHash(hash: String) {
    val current = js("window.location.hash || ''") as String
    if (current != hash) {
        js("window.history.replaceState(null, '', hash)")
    }
}
