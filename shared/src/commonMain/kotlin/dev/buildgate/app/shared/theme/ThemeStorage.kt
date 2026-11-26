package dev.buildgate.app.shared.theme

internal expect fun loadPersistedThemeModeName(): String?
internal expect fun savePersistedThemeModeName(modeName: String)
