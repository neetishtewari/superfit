package com.superfit.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The phone's own voice typing for one short answer, such as the right name of a food. Each app
 * provides it: given the prompt to show and what to do with the words, it returns the function
 * that starts listening, or null when this phone can't (the mic button is then hidden).
 */
val LocalVoiceTyping: ProvidableCompositionLocal<@Composable (prompt: String, onText: (String) -> Unit) -> (() -> Unit)?> =
    staticCompositionLocalOf { { _, _ -> null } }
