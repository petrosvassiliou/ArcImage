package com.arcimage.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arcimage.app.model.AppLanguage
import java.util.Locale

/** The 6 concrete country-flag languages selectable in ArcImage. */
val SelectableFlagLanguages: List<AppLanguage> = listOf(
    AppLanguage.ENGLISH,
    AppLanguage.GERMAN,
    AppLanguage.ITALIAN,
    AppLanguage.FRENCH,
    AppLanguage.SPANISH,
    AppLanguage.GREEK
)

/**
 * Resolves [AppLanguage.SYSTEM] to the matching country flag of the active device locale
 * (defaulting to [AppLanguage.ENGLISH] if outside the 6 supported languages), so the UI
 * always renders a real country flag instead of text codes like "AUTO" or "EN".
 */
fun resolveFlagLanguage(language: AppLanguage): AppLanguage {
    if (language != AppLanguage.SYSTEM) return language
    val deviceTag = Locale.getDefault().language.lowercase()
    return SelectableFlagLanguages.firstOrNull { it.languageTag == deviceTag }
        ?: AppLanguage.ENGLISH
}

/**
 * Crisp vector country flag rendered directly via Compose [Canvas] for sharp display
 * across all Android & Googlebook OS high-DPI screens without font/emoji inconsistencies.
 */
@Composable
fun CountryFlagIcon(
    language: AppLanguage,
    width: Dp = 32.dp,
    height: Dp = 22.dp,
    cornerRadius: Dp = 5.dp,
    modifier: Modifier = Modifier
) {
    val resolved = resolveFlagLanguage(language)
    val shape = RoundedCornerShape(cornerRadius)
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)

    Canvas(
        modifier = modifier
            .size(width = width, height = height)
            .clip(shape)
            .border(width = 0.75.dp, color = borderColor, shape = shape)
    ) {
        val w = size.width
        val h = size.height
        clipRect(0f, 0f, w, h) {
            when (resolved) {
                AppLanguage.GERMAN -> {
                    // Germany: Black, Red, Gold horizontal bands
                    val band = h / 3f
                    drawRect(Color(0xFF000000), Offset(0f, 0f), Size(w, band + 0.5f))
                    drawRect(Color(0xFFDD0000), Offset(0f, band), Size(w, band + 0.5f))
                    drawRect(Color(0xFFFFCE00), Offset(0f, band * 2f), Size(w, band))
                }

                AppLanguage.ITALIAN -> {
                    // Italy: Green, White, Red vertical bands
                    val col = w / 3f
                    drawRect(Color(0xFF009246), Offset(0f, 0f), Size(col + 0.5f, h))
                    drawRect(Color(0xFFFFFFFF), Offset(col, 0f), Size(col + 0.5f, h))
                    drawRect(Color(0xFFCE2B37), Offset(col * 2f, 0f), Size(col, h))
                }

                AppLanguage.FRENCH -> {
                    // France: Blue, White, Red vertical bands
                    val col = w / 3f
                    drawRect(Color(0xFF002395), Offset(0f, 0f), Size(col + 0.5f, h))
                    drawRect(Color(0xFFFFFFFF), Offset(col, 0f), Size(col + 0.5f, h))
                    drawRect(Color(0xFFED2939), Offset(col * 2f, 0f), Size(col, h))
                }

                AppLanguage.SPANISH -> {
                    // Spain: Red (1/4), Yellow (1/2), Red (1/4) + crest emblem on hoist side
                    drawRect(Color(0xFFAA151B), Offset(0f, 0f), Size(w, h))
                    drawRect(Color(0xFFF1BF00), Offset(0f, h * 0.25f), Size(w, h * 0.5f))
                    // Simplified heraldic shield accent
                    val shieldW = w * 0.12f
                    val shieldH = h * 0.24f
                    drawRect(
                        color = Color(0xFFAA151B),
                        topLeft = Offset(w * 0.24f, (h - shieldH) / 2f),
                        size = Size(shieldW, shieldH)
                    )
                }

                AppLanguage.GREEK -> {
                    // Greece: 9 alternating blue & white stripes + top-left canton with white cross
                    val blue = Color(0xFF0D5EAF)
                    val white = Color(0xFFFFFFFF)
                    val stripe = h / 9f
                    drawRect(white, Offset.Zero, Size(w, h))
                    for (i in 0 until 9 step 2) {
                        drawRect(blue, Offset(0f, i * stripe), Size(w, stripe + 0.4f))
                    }
                    val cantonSize = stripe * 5f
                    drawRect(blue, Offset.Zero, Size(cantonSize, cantonSize))
                    // Vertical & horizontal arms of the canton cross
                    drawRect(white, Offset(cantonSize * 0.4f, 0f), Size(cantonSize * 0.2f, cantonSize))
                    drawRect(white, Offset(0f, cantonSize * 0.4f), Size(cantonSize, cantonSize * 0.2f))
                }

                AppLanguage.ENGLISH,
                AppLanguage.SYSTEM -> {
                    // United Kingdom (Union Jack): Blue field, white & red saltires + St George cross
                    val navy = Color(0xFF012169)
                    val red = Color(0xFFC8102E)
                    val white = Color(0xFFFFFFFF)
                    drawRect(navy, Offset.Zero, Size(w, h))
                    // Diagonal white saltires
                    drawLine(white, Offset(0f, 0f), Offset(w, h), strokeWidth = h * 0.22f)
                    drawLine(white, Offset(w, 0f), Offset(0f, h), strokeWidth = h * 0.22f)
                    // Diagonal red saltires
                    drawLine(red, Offset(0f, 0f), Offset(w, h), strokeWidth = h * 0.10f)
                    drawLine(red, Offset(w, 0f), Offset(0f, h), strokeWidth = h * 0.10f)
                    // Central white fimbriation
                    drawRect(white, Offset(0f, h * 0.34f), Size(w, h * 0.32f))
                    drawRect(white, Offset(w * 0.40f, 0f), Size(w * 0.20f, h))
                    // Central red St George cross
                    drawRect(red, Offset(0f, h * 0.40f), Size(w, h * 0.20f))
                    drawRect(red, Offset(w * 0.44f, 0f), Size(w * 0.12f, h))
                }
            }
        }
    }
}
