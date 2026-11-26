package dev.buildgate.app.shared.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BuildGateThemeMode(val label: String, val icon: String) {
    SYSTEM("System", "📱"),
    LIGHT("Light", "☀️"),
    DARK("Dark", "🌙");

    fun next(): BuildGateThemeMode = when (this) {
        SYSTEM -> LIGHT
        LIGHT -> DARK
        DARK -> SYSTEM
    }
}

object BuildGateThemePreferences {
    private val _themeMode = MutableStateFlow(BuildGateThemeMode.SYSTEM)
    val themeMode: StateFlow<BuildGateThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: BuildGateThemeMode) {
        _themeMode.value = mode
    }

    fun cycleThemeMode() {
        _themeMode.value = _themeMode.value.next()
    }
}

private val LightColorScheme = lightColorScheme(
    primary = PrimaryViolet,
    onPrimary = OnPrimaryWhite,
    primaryContainer = PrimaryContainerSoft,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryCoral,
    onSecondary = OnSecondaryWhite,
    secondaryContainer = SecondaryContainerSoft,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = VerificationMint,
    onTertiary = OnPrimaryWhite,
    tertiaryContainer = VerificationContainer,
    onTertiaryContainer = OnVerificationDark,
    background = LightBackground,
    onBackground = OnLightTextPrimary,
    surface = LightSurface,
    onSurface = OnLightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = OnLightTextSecondary
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryViolet,
    onPrimary = OnPrimaryWhite,
    primaryContainer = OnPrimaryContainerDark,
    onPrimaryContainer = PrimaryContainerSoft,
    secondary = SecondaryCoral,
    onSecondary = OnSecondaryWhite,
    secondaryContainer = OnSecondaryContainerDark,
    onSecondaryContainer = SecondaryContainerSoft,
    tertiary = VerificationMint,
    onTertiary = OnVerificationDark,
    tertiaryContainer = OnVerificationDark,
    onTertiaryContainer = VerificationContainer,
    background = DarkBackground,
    onBackground = OnDarkTextPrimary,
    surface = DarkSurface,
    onSurface = OnDarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = OnDarkTextSecondary
)

@Composable
fun BuildGateTheme(
    themeMode: BuildGateThemeMode = BuildGateThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themeMode) {
        BuildGateThemeMode.SYSTEM -> isSystemInDarkTheme()
        BuildGateThemeMode.LIGHT -> false
        BuildGateThemeMode.DARK -> true
    }
    val colorScheme = if (useDarkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = BuildGateTypography,
        content = content
    )
}
