package dev.buildgate.app.shared.theme

import kotlinx.browser.window

private const val KEY_THEME_MODE = "buildgate_theme_mode"

internal actual fun loadPersistedThemeModeName(): String? = runCatching {
    window.localStorage.getItem(KEY_THEME_MODE)?.takeIf { it.isNotBlank() }
}.getOrNull()

internal actual fun savePersistedThemeModeName(modeName: String) {
    runCatching {
        window.localStorage.setItem(KEY_THEME_MODE, modeName)
    }
}
