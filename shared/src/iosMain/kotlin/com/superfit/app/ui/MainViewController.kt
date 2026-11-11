package com.superfit.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import com.superfit.app.data.CloudBackup
import com.superfit.app.data.IosAuth
import com.superfit.app.data.IosAuthClient
import com.superfit.app.data.IosBackupLocal
import com.superfit.app.data.IosCloudStore
import com.superfit.app.data.IosCloudStoreAdapter
import com.superfit.app.data.IosPrefs
import com.superfit.app.data.LogRepository
import com.superfit.app.data.openIosDatabase
import com.superfit.app.domain.AppLog
import com.superfit.app.domain.IosAi
import com.superfit.app.domain.installIosAi
import com.superfit.app.theme.SuperfitTheme
import com.superfit.app.ui.auth.LoginModel
import com.superfit.app.ui.auth.LoginScreen
import com.superfit.app.ui.components.PrimaryButton
import com.superfit.app.ui.dashboard.TodayModel
import com.superfit.app.ui.dashboard.TodayScreen
import com.superfit.app.ui.history.HistoryModel
import com.superfit.app.ui.history.ProgressHistoryPage
import platform.UIKit.UIViewController

/** The iPhone app's root screen, shown by the Swift app (ContentView.swift). */
@Suppress("FunctionName", "unused")
fun MainViewController(auth: IosAuth, cloudStore: IosCloudStore, ai: IosAi): UIViewController {
    installIosAi(ai)
    return ComposeUIViewController {
        SuperfitTheme(darkTheme = isSystemInDarkTheme()) {
            IosRoot(auth, cloudStore)
        }
    }
}

/** What the placeholder shows after the backup comes down: how many of each the iPhone now has. */
private data class Restored(val meals: Int, val workouts: Int, val weighIns: Int, val failed: Boolean)

@Composable
private fun IosRoot(auth: IosAuth, cloudStore: IosCloudStore) {
    var email by remember { mutableStateOf(auth.currentEmail()) }
    var signedIn by remember { mutableStateOf(auth.currentUid() != null) }
    val scope = rememberCoroutineScope()
    val client = remember { IosAuthClient(auth) }
    val onSignedIn = {
        email = auth.currentEmail()
        signedIn = auth.currentUid() != null
    }

    if (!signedIn) {
        val model = remember { LoginModel(client, scope) }
        LoginScreen(
            model = model,
            onLoginSuccess = onSignedIn,
            onGoogle = { model.signInWith(onSignedIn) { client.signInWithGoogle() } },
            onApple = { model.signInWith(onSignedIn) { client.signInWithApple() } }
        )
    } else {
        val uid = auth.currentUid() ?: return
        var restored by remember(uid) { mutableStateOf<Restored?>(null) }
        val database = remember(uid) { openIosDatabase(uid) }
        val local = remember(uid) { IosBackupLocal(uid) }
        val backup = remember(uid) {
            CloudBackup(IosCloudStoreAdapter(cloudStore), { auth.currentUid() }, { database }, local)
        }
        // Same as Android after sign-in: bring the backup down into this account's database
        // (merge only), then count what arrived.
        LaunchedEffect(uid) {
            val failed = try {
                backup.syncAllDown()
                false
            } catch (e: Exception) {
                AppLog.w("Restore", "Restoring the backup failed", e)
                true
            }
            restored = Restored(
                meals = database.nutritionDao().getAllEntries().size,
                workouts = database.workoutDao().getAllEntries().size,
                weighIns = database.weightDao().getAllWeightEntries().size,
                failed = failed
            )
        }
        if (restored == null) {
            // Today waits for the backup, so it never says "no profile" before it arrives.
            Box(Modifier.fillMaxSize().background(SuperfitTheme.colors.bgStart), contentAlignment = Alignment.Center) {
                Text(
                    "Bringing back your meals and progress…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = SuperfitTheme.colors.textPrimary
                )
            }
            return
        }
        // Lives while this account is signed in; signing out stops Today's updates.
        val accountScope = rememberCoroutineScope()
        val log = remember(uid) { LogRepository(database = { database }, backup = backup) }
        val today = remember(uid) {
            TodayModel(
                log = log,
                prefs = IosPrefs.forUser(uid),
                scope = accountScope,
                coachNotes = local::loadNotes
            )
        }
        val history = remember(uid) { HistoryModel(log = log, prefs = IosPrefs.forUser(uid), scope = accountScope) }
        var tab by remember(uid) { mutableStateOf(MainTab.TODAY) }
        Scaffold(
            containerColor = SuperfitTheme.colors.bgStart,
            // Each screen handles the top of the phone itself; the menu handles the bottom.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = { MainMenuBar(tab, onSelect = { tab = it }, tabs = listOf(MainTab.TODAY, MainTab.PROGRESS)) }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                when (tab) {
                    MainTab.PROGRESS -> ProgressHistoryPage(history)
                    else -> TodayScreen(today) {
                        // What the iPhone still shows below Today until Settings moves over.
                        Text(
                            text = when {
                                restored?.failed == true -> "Couldn't reach your backup. Check your internet and open the app again."
                                else -> restored?.let { "Signed in as ${email ?: "your Apple account"}. On this iPhone: ${it.meals} meals, ${it.workouts} workouts, ${it.weighIns} weigh-ins." }.orEmpty()
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = SuperfitTheme.colors.textSecondary
                        )
                        PrimaryButton(
                            text = "Sign out",
                            onClick = {
                                auth.signOut()
                                signedIn = false
                                email = null
                            }
                        )
                    }
                }
            }
        }
    }
}
