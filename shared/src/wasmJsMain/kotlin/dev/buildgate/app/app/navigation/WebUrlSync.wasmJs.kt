@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package dev.buildgate.app.app.navigation

@JsFun("() => window.location.hash || ''")
private external fun wasmGetLocationHash(): String

@JsFun("(hash) => { if (window.location.hash !== hash) { window.history.replaceState(null, '', hash); } }")
private external fun wasmReplaceLocationHash(hash: String)

actual fun getInitialBrowserRouteHash(): String? =
    wasmGetLocationHash().takeIf { it.isNotBlank() }

actual fun syncBrowserRouteHash(hash: String) {
    wasmReplaceLocationHash(hash)
}
