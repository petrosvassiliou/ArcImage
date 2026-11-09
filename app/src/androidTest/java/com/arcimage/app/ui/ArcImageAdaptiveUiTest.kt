package com.arcimage.app.ui

import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.os.LocaleListCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.arcimage.app.MainActivity
import com.arcimage.app.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class ArcImageAdaptiveUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @After
    fun resetLocale() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        }
    }

    @Test
    fun adaptiveShellDisplaysBrandingAndSwitchesBetweenArchiveAndImageStudio() {
        val context = composeRule.activity

        // Brand title "ArcImage" is displayed
        composeRule.onNodeWithText("ArcImage").assertIsDisplayed()

        // Navigate to Image Studio workspace
        val imageStudioLabel = context.getString(R.string.nav_image_studio)
        composeRule.onNodeWithText(imageStudioLabel).performClick()

        val emptyImageTitle = context.getString(R.string.image_empty_title)
        composeRule.onNodeWithText(emptyImageTitle).assertIsDisplayed()

        // Navigate back to Archive Manager workspace and switch to Explore Archive mode
        val archiveManagerLabel = context.getString(R.string.nav_archive_manager)
        composeRule.onNodeWithText(archiveManagerLabel).performClick()

        val exploreModeLabel = context.getString(R.string.archive_mode_explore)
        composeRule.onNodeWithText(exploreModeLabel).performClick()

        val emptyExploreTitle = context.getString(R.string.archive_empty_explore_title)
        composeRule.onNodeWithText(emptyExploreTitle).assertIsDisplayed()
    }

    @Test
    fun inAppLanguagePickerDisplaysAllSixLanguagesAndSwitchesToGreekRuntimeLocale() {
        val context = composeRule.activity
        val langDescription = context.getString(R.string.action_language)

        // Open Language Picker modal
        composeRule.onNodeWithContentDescription(langDescription).performClick()

        // Verify native names for all 6 supported languages appear
        composeRule.onNodeWithText("English").assertIsDisplayed()
        composeRule.onNodeWithText("Deutsch").assertIsDisplayed()
        composeRule.onNodeWithText("Italiano").assertIsDisplayed()
        composeRule.onNodeWithText("Français").assertIsDisplayed()
        composeRule.onNodeWithText("Español").assertIsDisplayed()
        composeRule.onNodeWithText("Ελληνικά").assertIsDisplayed()

        // Switch runtime language to Greek
        composeRule.onNodeWithText("Ελληνικά").performClick()
        composeRule.waitForIdle()

        assertEquals("el", AppCompatDelegate.getApplicationLocales().toLanguageTags())
        composeRule.onNodeWithText("Στούντιο Εικόνας").assertIsDisplayed()
    }

    @Test
    fun allSixLocaleResourceConfigurationsResolveLocalizedSubtitles() {
        val baseContext = InstrumentationRegistry.getInstrumentation().targetContext
        val expectedSubtitles = mapOf(
            "en" to "Archiver & Image Studio",
            "de" to "Archivmanager & Bildstudio",
            "it" to "Gestore Archivi & Studio Immagini",
            "fr" to "Gestionnaire d'archives & Studio d'images",
            "es" to "Gestor de Archivos y Estudio de Imagen",
            "el" to "Διαχειριστής Αρχείων & Στούντιο Εικόνας"
        )

        expectedSubtitles.forEach { (tag, expectedSubtitle) ->
            val config = Configuration(baseContext.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(tag))
            }
            val localizedCtx = baseContext.createConfigurationContext(config)
            assertEquals("ArcImage", localizedCtx.getString(R.string.app_name))
            assertEquals(expectedSubtitle, localizedCtx.getString(R.string.app_subtitle))
            assertTrue(localizedCtx.getString(R.string.widget_action_extract).isNotBlank())
        }
    }
}
