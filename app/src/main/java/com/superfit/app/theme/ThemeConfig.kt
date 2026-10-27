package com.superfit.app.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object ThemeConfig {
    const val MODE_LIGHT = "light"
    const val MODE_DARK = "dark"
    const val MODE_SYSTEM = "system"

    private val _isDarkTheme = MutableStateFlow(false)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme

    private val _mode = MutableStateFlow(MODE_SYSTEM)

    /** "light", "dark" or "system" (match the phone). */
    val mode: StateFlow<String> = _mode

    private var systemIsDark = false

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences("superfit_prefs", Context.MODE_PRIVATE)
        // New installs follow the phone; people who picked light or dark keep their choice.
        _mode.value = prefs.getString("theme_mode", MODE_SYSTEM) ?: MODE_SYSTEM
        update()
    }

    fun setThemeMode(context: Context, isDark: Boolean) {
        setMode(context, if (isDark) MODE_DARK else MODE_LIGHT)
    }

    fun setMode(context: Context, mode: String) {
        val prefs = context.getSharedPreferences("superfit_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("theme_mode", mode).apply()
        _mode.value = mode
        update()
    }

    /** Called from the activity whenever the phone switches between light and dark. */
    fun onSystemDarkChanged(isDark: Boolean) {
        systemIsDark = isDark
        update()
    }

    private fun update() {
        _isDarkTheme.value = when (_mode.value) {
            MODE_DARK -> true
            MODE_LIGHT -> false
            else -> systemIsDark
        }
    }
}
