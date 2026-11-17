package com.k1adili.atichat.util

import android.content.Context

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Small UI-only preferences (not part of the chat backup). */
object UiPrefs {
    private const val FILE = "ui_prefs"
    private const val KEY_THEME = "theme_mode"

    fun loadTheme(context: Context): ThemeMode {
        val saved = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_THEME, null)
        return ThemeMode.entries.firstOrNull { it.name == saved } ?: ThemeMode.SYSTEM
    }

    fun saveTheme(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY_THEME, mode.name).apply()
    }
}
