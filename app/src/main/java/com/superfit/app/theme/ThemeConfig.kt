package com.superfit.app.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object ThemeConfig {
    private val _isDarkTheme = MutableStateFlow(true)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences("superfit_prefs", Context.MODE_PRIVATE)
        // Default to dark theme (true)
        val mode = prefs.getString("theme_mode", "dark")
        _isDarkTheme.value = (mode != "light")
    }

    fun setThemeMode(context: Context, isDark: Boolean) {
        val prefs = context.getSharedPreferences("superfit_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("theme_mode", if (isDark) "dark" else "light").apply()
        _isDarkTheme.value = isDark
    }
}
