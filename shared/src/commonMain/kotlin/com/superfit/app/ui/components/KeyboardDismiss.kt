package com.superfit.app.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedScroll.NestedScrollConnection
import androidx.compose.ui.input.nestedScroll.NestedScrollSource
import androidx.compose.ui.input.nestedScroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager

/**
 * Closes the keyboard when someone taps a blank part of the screen or scrolls, so a text box never
 * keeps the keyboard up over the bottom menu (iPhone has no back button to close it).
 */
fun Modifier.dismissKeyboardOnTapOrScroll(): Modifier = composed {
    val focus: FocusManager = LocalFocusManager.current
    val connection = remember(focus) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available != Offset.Zero) focus.clearFocus()
                return Offset.Zero
            }
        }
    }
    this
        .nestedScroll(connection)
        .pointerInput(focus) { detectTapGestures(onTap = { focus.clearFocus() }) }
}
