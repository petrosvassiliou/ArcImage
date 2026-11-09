package com.arcimage.app.model

import androidx.annotation.StringRes
import com.arcimage.app.R

enum class AppDestination(
    @StringRes val labelRes: Int
) {
    ARCHIVE_MANAGER(R.string.nav_archive_manager),
    IMAGE_STUDIO(R.string.nav_image_studio)
}

enum class ThemePreference(
    @StringRes val labelRes: Int
) {
    SYSTEM(R.string.theme_system),
    LIGHT(R.string.theme_light),
    DARK(R.string.theme_dark)
}
