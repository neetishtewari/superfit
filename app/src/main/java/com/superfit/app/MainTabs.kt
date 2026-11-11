package com.superfit.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.superfit.app.data.AppAnalytics
import com.superfit.app.theme.ThemeBgStart
import com.superfit.app.ui.MainMenuBar
import com.superfit.app.ui.MainTab

/**
 * The bottom menu and the tab it shows. The app always opens on Today; the phone's back button on
 * Progress or Coach goes to Today. Each tab keeps its scroll position and switches while you move
 * between them. Tapping the tab you are on asks it to scroll back to the top.
 *
 * [goToToday] turns true when a widget or reminder shortcut needs the Today tab (mic, camera).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainTabs(
    goToToday: Boolean,
    today: @Composable (reselectCount: Int, openCoach: (how: String) -> Unit) -> Unit,
    progress: @Composable (reselectCount: Int) -> Unit,
    coach: @Composable () -> Unit
) {
    var tab by rememberSaveable { mutableStateOf(MainTab.TODAY) }
    var how by remember { mutableStateOf("start") }
    val reselects = remember { MainTab.entries.associateWith { mutableIntStateOf(0) } }
    val saveable = rememberSaveableStateHolder()

    fun select(next: MainTab, via: String) {
        if (next == tab) {
            reselects.getValue(next).intValue++
        } else {
            how = via
            tab = next
        }
    }

    LaunchedEffect(goToToday) {
        if (goToToday) tab = MainTab.TODAY
    }
    LaunchedEffect(tab) {
        when (tab) {
            MainTab.TODAY -> AppAnalytics.screenOpened("home")
            MainTab.PROGRESS -> AppAnalytics.screenOpened("progress", how)
            MainTab.COACH -> AppAnalytics.screenOpened("coach", how)
        }
    }
    BackHandler(enabled = tab != MainTab.TODAY) { select(MainTab.TODAY, "back") }

    val keyboardOpen = WindowInsets.isImeVisible
    Scaffold(
        containerColor = ThemeBgStart,
        // Each screen handles the top of the phone itself; the menu handles the bottom.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (!keyboardOpen) MainMenuBar(tab, onSelect = { select(it, "menu") })
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
        ) {
            saveable.SaveableStateProvider(tab.name) {
                when (tab) {
                    MainTab.TODAY -> today(reselects.getValue(MainTab.TODAY).intValue) { via -> select(MainTab.COACH, via) }
                    MainTab.PROGRESS -> progress(reselects.getValue(MainTab.PROGRESS).intValue)
                    MainTab.COACH -> coach()
                }
            }
        }
    }
}
