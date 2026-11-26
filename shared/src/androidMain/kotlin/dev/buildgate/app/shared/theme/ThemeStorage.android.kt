package dev.buildgate.app.shared.theme

import android.content.Context
import dev.buildgate.app.shared.platform.getAndroidAppContext

private const val PREFS_NAME = "buildgate_ui_prefs"
private const val KEY_THEME_MODE = "theme_mode"

internal actual fun loadPersistedThemeModeName(): String? = runCatching {
    val ctx = getAndroidAppContext() ?: return null
    ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_THEME_MODE, null)
}.getOrNull()

internal actual fun savePersistedThemeModeName(modeName: String) {
    runCatching {
        val ctx = getAndroidAppContext() ?: return
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, modeName)
            .apply()
    }
}
