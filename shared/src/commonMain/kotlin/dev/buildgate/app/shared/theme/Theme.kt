package dev.buildgate.app.shared.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

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
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = BuildGateTypography,
        content = content
    )
}
