package com.arcimage.app.ui

import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
    fun adaptiveShellDisplaysBackButtonInEveryScreenAndNavigatesBack() {
        val context = composeRule.activity
        val backDesc = context.getString(R.string.action_back)

        // Brand title "ArcImage" and Back button are displayed on initial Files screen
        composeRule.onNodeWithText("ArcImage").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription(backDesc).onFirst().assertIsDisplayed()

        // Navigate to Image Studio workspace -> Back button displayed
        val imageStudioLabel = context.getString(R.string.nav_image_studio)
        composeRule.onAllNodesWithText(imageStudioLabel).onFirst().performClick()

        val emptyImageTitle = context.getString(R.string.image_empty_title)
        composeRule.onNodeWithText(emptyImageTitle).assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription(backDesc).onFirst().assertIsDisplayed()

        // Navigate to Archive Manager workspace -> Back button displayed
        val archiveManagerLabel = context.getString(R.string.nav_archive_manager)
        composeRule.onAllNodesWithText(archiveManagerLabel).onFirst().performClick()
        composeRule.onAllNodesWithContentDescription(backDesc).onFirst().assertIsDisplayed()

        val exploreModeLabel = context.getString(R.string.archive_mode_explore)
        composeRule.onNodeWithText(exploreModeLabel).performClick()

        val emptyExploreTitle = context.getString(R.string.archive_empty_explore_title)
        composeRule.onNodeWithText(emptyExploreTitle).assertIsDisplayed()

        // Clicking Back button pops Archive Manager -> Image Studio
        composeRule.onAllNodesWithContentDescription(backDesc).onFirst().performClick()
        composeRule.onNodeWithText(emptyImageTitle).assertIsDisplayed()
    }

    @Test
    fun appStartsOnRealDeviceFilesBrowserWithoutSampleContent() {
        val context = composeRule.activity

        // Default destination is the device "Files" browser (label in nav + top bar subtitle)
        val filesLabel = context.getString(R.string.nav_files)
        composeRule.onAllNodesWithText(filesLabel).onFirst().assertIsDisplayed()

        // Without "All files access" the onboarding card is shown - never fictional sample files
        if (!com.arcimage.app.data.files.DeviceFileRepository.hasAllFilesAccess(context)) {
            composeRule.onNodeWithText(context.getString(R.string.files_permission_title)).assertIsDisplayed()
            composeRule.onNodeWithText(context.getString(R.string.files_grant_access)).assertIsDisplayed()
            composeRule.onNodeWithText(context.getString(R.string.files_open_documents)).assertIsDisplayed()
        }
    }

    @Test
    fun inAppLanguagePickerDisplaysSixCountryFlagsAndSwitchesToGreekRuntimeLocale() {
        val context = composeRule.activity
        val langDescription = context.getString(R.string.action_language)

        // Open Country-Flag Language Picker modal
        composeRule.onAllNodesWithContentDescription(langDescription).onFirst().performClick()

        // Verify all 6 country flag cards appear (with accessibility content descriptions, no text labels)
        composeRule.onNodeWithContentDescription("English").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Deutsch").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Italiano").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Français").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Español").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Ελληνικά").assertIsDisplayed()

        // Switch runtime language to Greek via the Greek flag tile
        composeRule.onNodeWithContentDescription("Ελληνικά").performClick()
        composeRule.waitForIdle()

        assertEquals("el", AppCompatDelegate.getApplicationLocales().toLanguageTags())
        composeRule.onAllNodesWithText("Στούντιο Εικόνας").onFirst().assertIsDisplayed()
    }

    @Test
    fun updateDialogOpensAndDisplaysManualAndAutoUpdateControls() {
        val context = composeRule.activity
        val updatesDesc = context.getString(R.string.action_updates)

        composeRule.onAllNodesWithContentDescription(updatesDesc).onFirst().performClick()

        composeRule.onNodeWithText(context.getString(R.string.update_dialog_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.update_check_now))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.update_auto_check_label))
            .performScrollTo()
            .assertIsDisplayed()
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
