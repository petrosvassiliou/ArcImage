package com.arcimage.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.arcimage.app.model.ThemePreference

private val LightColorScheme = lightColorScheme(
    primary = ArcBluePrimaryLight,
    onPrimary = ArcOnPrimaryLight,
    primaryContainer = ArcPrimaryContainerLight,
    onPrimaryContainer = ArcOnPrimaryContainerLight,
    secondary = ArcSecondaryLight,
    onSecondary = ArcOnSecondaryLight,
    secondaryContainer = ArcSecondaryContainerLight,
    onSecondaryContainer = ArcOnSecondaryContainerLight,
    tertiary = ArcTertiaryLight,
    onTertiary = ArcOnTertiaryLight,
    tertiaryContainer = ArcTertiaryContainerLight,
    onTertiaryContainer = ArcOnTertiaryContainerLight,
    background = ArcBackgroundLight,
    onBackground = ArcOnBackgroundLight,
    surface = ArcSurfaceLight,
    onSurface = ArcOnSurfaceLight,
    surfaceVariant = ArcSurfaceVariantLight,
    onSurfaceVariant = ArcOnSurfaceVariantLight
)

private val DarkColorScheme = darkColorScheme(
    primary = ArcBluePrimaryDark,
    onPrimary = ArcOnPrimaryDark,
    primaryContainer = ArcPrimaryContainerDark,
    onPrimaryContainer = ArcOnPrimaryContainerDark,
    secondary = ArcSecondaryDark,
    onSecondary = ArcOnSecondaryDark,
    secondaryContainer = ArcSecondaryContainerDark,
    onSecondaryContainer = ArcOnSecondaryContainerDark,
    tertiary = ArcTertiaryDark,
    onTertiary = ArcOnTertiaryDark,
    tertiaryContainer = ArcTertiaryContainerDark,
    onTertiaryContainer = ArcOnTertiaryContainerDark,
    background = ArcBackgroundDark,
    onBackground = ArcOnBackgroundDark,
    surface = ArcSurfaceDark,
    onSurface = ArcOnSurfaceDark,
    surfaceVariant = ArcSurfaceVariantDark,
    onSurfaceVariant = ArcOnSurfaceVariantDark
)

@Composable
fun ArcImageTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themePreference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ArcImageTypography,
        content = content
    )
}
