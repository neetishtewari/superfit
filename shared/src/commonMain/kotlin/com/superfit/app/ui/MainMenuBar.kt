package com.superfit.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.superfit.app.theme.SuperfitTheme
import com.superfit.app.theme.ThemeCardBg
import com.superfit.app.theme.ThemeTextSecondary

/** The places in the bottom menu. */
enum class MainTab(val label: String, val selectedIcon: ImageVector, val icon: ImageVector) {
    TODAY("Today", Icons.Rounded.Today, Icons.Outlined.Today),
    PROGRESS("Progress", Icons.Rounded.Insights, Icons.Outlined.Insights),
    COACH("Coach", Icons.Rounded.AutoAwesome, Icons.Outlined.AutoAwesome)
}

/** The bottom menu, the same on Android and iPhone. [tabs] is every place the app has so far. */
@Composable
fun MainMenuBar(selected: MainTab, onSelect: (MainTab) -> Unit, tabs: List<MainTab> = MainTab.entries) {
    val colors = SuperfitTheme.colors
    NavigationBar(containerColor = ThemeCardBg, tonalElevation = 0.dp) {
        tabs.forEach { item ->
            val isOn = item == selected
            // Coach is purple, the coach's colour; the others use the calm green.
            val onColor = if (item == MainTab.COACH) colors.tertiaryAccent else colors.selectedFg
            val pill = if (item == MainTab.COACH) colors.coachSoft else colors.selectedBg
            NavigationBarItem(
                selected = isOn,
                onClick = { onSelect(item) },
                icon = { Icon(if (isOn) item.selectedIcon else item.icon, contentDescription = null) },
                label = { Text(item.label, fontWeight = if (isOn) FontWeight.Bold else FontWeight.Medium) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = onColor,
                    // Under the pill: dark green on light, bright green on dark, so the label stays easy to read.
                    selectedTextColor = if (item == MainTab.COACH) {
                        onColor
                    } else if (colors.isDark) {
                        colors.primaryAccent
                    } else {
                        colors.selectedFg
                    },
                    indicatorColor = pill,
                    unselectedIconColor = ThemeTextSecondary,
                    unselectedTextColor = ThemeTextSecondary
                )
            )
        }
    }
}
