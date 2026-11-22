package com.zebrasoftware.gdir.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

internal enum class ThemeMode(val label: String) {
    System("Phone"),
    Light("Light"),
    Dark("Dark")
}

/** App settings, saved on the device so they survive restarts. */
internal class GdirSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var themeMode by mutableStateOf(
        ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_THEME_MODE, null) }
            ?: ThemeMode.System
    )
        private set

    var countryCode by mutableStateOf(prefs.getString(KEY_COUNTRY, null))
        private set

    fun updateThemeMode(mode: ThemeMode) {
        themeMode = mode
        prefs.edit { putString(KEY_THEME_MODE, mode.name) }
    }

    fun updateCountryCode(code: String?) {
        countryCode = code
        prefs.edit {
            if (code == null) remove(KEY_COUNTRY) else putString(KEY_COUNTRY, code)
        }
    }

    private companion object {
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_COUNTRY = "country"
    }
}
