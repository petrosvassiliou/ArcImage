package com.arcimage.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ArcImage brand palette, matching the "Arc-Aperture" logo:
// Indigo/Violet primary, Cyan secondary, Amber tertiary (the logo's sun).

// Brand gradient stops (logo tile, hero headers)
val ArcBrandIndigo = Color(0xFF4F46E5)
val ArcBrandViolet = Color(0xFF7C3AED)
val ArcBrandCyan = Color(0xFF06B6D4)
val ArcBrandAmber = Color(0xFFFBBF24)

val ArcBrandGradient: Brush
    get() = Brush.linearGradient(listOf(ArcBrandIndigo, ArcBrandViolet, ArcBrandCyan))

// ---------- Light scheme ----------
val ArcPrimaryLight = Color(0xFF4F46E5)
val ArcOnPrimaryLight = Color(0xFFFFFFFF)
val ArcPrimaryContainerLight = Color(0xFFE2DFFF)
val ArcOnPrimaryContainerLight = Color(0xFF1A1265)

val ArcSecondaryLight = Color(0xFF00687A)
val ArcOnSecondaryLight = Color(0xFFFFFFFF)
val ArcSecondaryContainerLight = Color(0xFFCCF3FA)
val ArcOnSecondaryContainerLight = Color(0xFF001F26)

val ArcTertiaryLight = Color(0xFF8A5A00)
val ArcOnTertiaryLight = Color(0xFFFFFFFF)
val ArcTertiaryContainerLight = Color(0xFFFFE7B3)
val ArcOnTertiaryContainerLight = Color(0xFF2B1700)

val ArcErrorLight = Color(0xFFBA1A1A)
val ArcErrorContainerLight = Color(0xFFFFDAD6)

val ArcBackgroundLight = Color(0xFFFBF8FF)
val ArcOnBackgroundLight = Color(0xFF1B1B21)
val ArcSurfaceLight = Color(0xFFFBF8FF)
val ArcOnSurfaceLight = Color(0xFF1B1B21)
val ArcSurfaceVariantLight = Color(0xFFE4E1EC)
val ArcOnSurfaceVariantLight = Color(0xFF47464F)
val ArcOutlineLight = Color(0xFF787680)
val ArcOutlineVariantLight = Color(0xFFC8C5D0)

val ArcSurfaceContainerLowestLight = Color(0xFFFFFFFF)
val ArcSurfaceContainerLowLight = Color(0xFFF5F2FB)
val ArcSurfaceContainerLight = Color(0xFFEFECF6)
val ArcSurfaceContainerHighLight = Color(0xFFE9E6F0)
val ArcSurfaceContainerHighestLight = Color(0xFFE3E1EA)

// ---------- Dark scheme ----------
val ArcPrimaryDark = Color(0xFFC3C0FF)
val ArcOnPrimaryDark = Color(0xFF2A2185)
val ArcPrimaryContainerDark = Color(0xFF4038C8)
val ArcOnPrimaryContainerDark = Color(0xFFE2DFFF)

val ArcSecondaryDark = Color(0xFF5DD8EE)
val ArcOnSecondaryDark = Color(0xFF003640)
val ArcSecondaryContainerDark = Color(0xFF004E5C)
val ArcOnSecondaryContainerDark = Color(0xFFCCF3FA)

val ArcTertiaryDark = Color(0xFFFFC94D)
val ArcOnTertiaryDark = Color(0xFF482A00)
val ArcTertiaryContainerDark = Color(0xFF684000)
val ArcOnTertiaryContainerDark = Color(0xFFFFE7B3)

val ArcErrorDark = Color(0xFFFFB4AB)
val ArcErrorContainerDark = Color(0xFF93000A)

val ArcBackgroundDark = Color(0xFF111116)
val ArcOnBackgroundDark = Color(0xFFE4E1EA)
val ArcSurfaceDark = Color(0xFF111116)
val ArcOnSurfaceDark = Color(0xFFE4E1EA)
val ArcSurfaceVariantDark = Color(0xFF47464F)
val ArcOnSurfaceVariantDark = Color(0xFFC8C5D0)
val ArcOutlineDark = Color(0xFF928F9A)
val ArcOutlineVariantDark = Color(0xFF47464F)

val ArcSurfaceContainerLowestDark = Color(0xFF0C0C11)
val ArcSurfaceContainerLowDark = Color(0xFF19191F)
val ArcSurfaceContainerDark = Color(0xFF1E1D24)
val ArcSurfaceContainerHighDark = Color(0xFF28272E)
val ArcSurfaceContainerHighestDark = Color(0xFF333239)
