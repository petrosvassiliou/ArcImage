package com.arcimage.app.model

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

enum class AppLanguage(
    val languageTag: String,
    val nativeDisplayName: String,
    val badgeCode: String
) {
    SYSTEM(languageTag = "", nativeDisplayName = "", badgeCode = "AUTO"),
    ENGLISH(languageTag = "en", nativeDisplayName = "English", badgeCode = "EN"),
    GERMAN(languageTag = "de", nativeDisplayName = "Deutsch", badgeCode = "DE"),
    ITALIAN(languageTag = "it", nativeDisplayName = "Italiano", badgeCode = "IT"),
    FRENCH(languageTag = "fr", nativeDisplayName = "Français", badgeCode = "FR"),
    SPANISH(languageTag = "es", nativeDisplayName = "Español", badgeCode = "ES"),
    GREEK(languageTag = "el", nativeDisplayName = "Ελληνικά", badgeCode = "EL");

    companion object {
        fun fromLanguageTag(tag: String?): AppLanguage {
            if (tag.isNullOrBlank()) return SYSTEM
            val normalized = tag.lowercase().substringBefore('-')
            return entries.firstOrNull { it.languageTag == normalized } ?: SYSTEM
        }
    }
}

object LocaleController {
    fun getCurrentAppLanguage(): AppLanguage {
        return runCatching {
            val currentLocales = AppCompatDelegate.getApplicationLocales()
            if (currentLocales == null || currentLocales.isEmpty) {
                AppLanguage.SYSTEM
            } else {
                AppLanguage.fromLanguageTag(currentLocales.toLanguageTags())
            }
        }.getOrDefault(AppLanguage.SYSTEM)
    }

    fun setAppLanguage(language: AppLanguage) {
        val localeList = if (language == AppLanguage.SYSTEM) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(language.languageTag)
        }
        AppCompatDelegate.setApplicationLocales(localeList)
    }
}
