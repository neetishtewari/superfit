package com.superfit.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.window.ComposeUIViewController
import com.superfit.app.ui.preview.ThemePreview
import platform.UIKit.UIViewController

/** The iPhone app's root screen, shown by the Swift app (ContentView.swift). */
@Suppress("FunctionName", "unused")
fun MainViewController(): UIViewController = ComposeUIViewController {
    ThemePreview(darkTheme = isSystemInDarkTheme())
}
