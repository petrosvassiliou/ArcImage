package com.arcimage.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.arcimage.app.model.ThemePreference

private val LightColorScheme = lightColorScheme(
    primary = ArcPrimaryLight,
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
    error = ArcErrorLight,
    errorContainer = ArcErrorContainerLight,
    background = ArcBackgroundLight,
    onBackground = ArcOnBackgroundLight,
    surface = ArcSurfaceLight,
    onSurface = ArcOnSurfaceLight,
    surfaceVariant = ArcSurfaceVariantLight,
    onSurfaceVariant = ArcOnSurfaceVariantLight,
    outline = ArcOutlineLight,
    outlineVariant = ArcOutlineVariantLight,
    surfaceContainerLowest = ArcSurfaceContainerLowestLight,
    surfaceContainerLow = ArcSurfaceContainerLowLight,
    surfaceContainer = ArcSurfaceContainerLight,
    surfaceContainerHigh = ArcSurfaceContainerHighLight,
    surfaceContainerHighest = ArcSurfaceContainerHighestLight
)

private val DarkColorScheme = darkColorScheme(
    primary = ArcPrimaryDark,
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
    error = ArcErrorDark,
    errorContainer = ArcErrorContainerDark,
    background = ArcBackgroundDark,
    onBackground = ArcOnBackgroundDark,
    surface = ArcSurfaceDark,
    onSurface = ArcOnSurfaceDark,
    surfaceVariant = ArcSurfaceVariantDark,
    onSurfaceVariant = ArcOnSurfaceVariantDark,
    outline = ArcOutlineDark,
    outlineVariant = ArcOutlineVariantDark,
    surfaceContainerLowest = ArcSurfaceContainerLowestDark,
    surfaceContainerLow = ArcSurfaceContainerLowDark,
    surfaceContainer = ArcSurfaceContainerDark,
    surfaceContainerHigh = ArcSurfaceContainerHighDark,
    surfaceContainerHighest = ArcSurfaceContainerHighestDark
)

/** Generous, modern corner radii used throughout ArcImage (cards, sheets, chips). */
val ArcImageShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun ArcImageTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    dynamicColor: Boolean = false,
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
        shapes = ArcImageShapes,
        content = content
    )
}
