@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package dev.buildgate.app.shared.theme

@JsFun("() => { try { return window.localStorage.getItem('buildgate_theme_mode') || ''; } catch (e) { return ''; } }")
private external fun wasmLoadThemeMode(): String

@JsFun("(mode) => { try { window.localStorage.setItem('buildgate_theme_mode', mode); } catch (e) {} }")
private external fun wasmSaveThemeMode(mode: String)

internal actual fun loadPersistedThemeModeName(): String? =
    wasmLoadThemeMode().takeIf { it.isNotBlank() }

internal actual fun savePersistedThemeModeName(modeName: String) {
    wasmSaveThemeMode(modeName)
}
