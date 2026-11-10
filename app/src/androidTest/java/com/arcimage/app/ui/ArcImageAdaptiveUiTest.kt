package com.arcimage.app.ui

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.view.KeyEvent
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
import java.io.File
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
    fun inAppLanguageDropdownDisplaysSixCountryFlagsAndSwitchesToGreekRuntimeLocale() {
        val context = composeRule.activity
        val langDescription = context.getString(R.string.action_language)

        // Open Country-Flag Language DropdownMenu
        composeRule.onAllNodesWithContentDescription(langDescription).onFirst().performClick()

        // Verify all 6 country flag items appear in the dropdown menu (with accessibility content descriptions)
        composeRule.onNodeWithContentDescription("English").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Deutsch").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Italiano").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Français").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Español").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Ελληνικά").assertIsDisplayed()

        // Switch runtime language to Greek via the Greek flag dropdown item
        composeRule.onNodeWithContentDescription("Ελληνικά").performClick()
        composeRule.waitForIdle()

        assertEquals("el", AppCompatDelegate.getApplicationLocales().toLanguageTags())
        composeRule.onAllNodesWithText("Στούντιο Εικόνας").onFirst().assertIsDisplayed()
    }

    @Test
    fun hardwareKeyboardShortcutsWorkWithoutManualFocus() {
        val activity = composeRule.activity
        val tempFile = File(activity.cacheDir, "shortcut_verify.txt").apply {
            writeText("keyboard shortcut verification")
        }

        // Route a real file into Archive Manager without touching or focusing any list element
        composeRule.runOnUiThread {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, Uri.fromFile(tempFile))
            }
            InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(activity, shareIntent)
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("shortcut_verify.txt").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("shortcut_verify.txt").assertIsDisplayed()

        // Press Ctrl+A (Select All) directly on the Activity without focusing any Compose node first
        composeRule.runOnUiThread {
            activity.dispatchKeyEvent(
                KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, 0, KeyEvent.META_CTRL_ON)
            )
            activity.dispatchKeyEvent(
                KeyEvent(0L, 0L, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_A, 0, KeyEvent.META_CTRL_ON)
            )
        }
        composeRule.waitForIdle()
        val selectedLabel = activity.getString(R.string.selected_count_format, 1)
        composeRule.onNodeWithText(selectedLabel).assertIsDisplayed()

        // Press Esc (Clear Selection) -> selection pill disappears, item remains
        composeRule.runOnUiThread {
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE))
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ESCAPE))
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("shortcut_verify.txt").assertIsDisplayed()

        // Press Ctrl+A then Delete/Entf (KEYCODE_FORWARD_DEL) -> removes item and shows empty state
        composeRule.runOnUiThread {
            activity.dispatchKeyEvent(
                KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, 0, KeyEvent.META_CTRL_ON)
            )
        }
        composeRule.waitForIdle()
        composeRule.runOnUiThread {
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_FORWARD_DEL))
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(activity.getString(R.string.archive_empty_compress_title)).assertIsDisplayed()

        tempFile.delete()
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
