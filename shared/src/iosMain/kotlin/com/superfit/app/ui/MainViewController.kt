package com.superfit.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import com.superfit.app.data.CloudBackup
import com.superfit.app.data.IosAuth
import com.superfit.app.data.IosAuthClient
import com.superfit.app.data.IosBackupLocal
import com.superfit.app.data.IosCloudStore
import com.superfit.app.data.IosCloudStoreAdapter
import com.superfit.app.data.openIosDatabase
import com.superfit.app.domain.Ai
import com.superfit.app.domain.AppLog
import com.superfit.app.domain.IosAi
import com.superfit.app.domain.installIosAi
import com.superfit.app.theme.SuperfitTheme
import com.superfit.app.ui.auth.LoginModel
import com.superfit.app.ui.auth.LoginScreen
import com.superfit.app.ui.components.PrimaryButton
import com.superfit.app.ui.components.SuperfitBrandHeader
import kotlinx.coroutines.launch
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
        // Same as Android after sign-in: bring the backup down into this account's database
        // (merge only), then count what arrived.
        LaunchedEffect(uid) {
            val database = openIosDatabase(uid)
            val backup = CloudBackup(IosCloudStoreAdapter(cloudStore), { auth.currentUid() }, { database }, IosBackupLocal(uid))
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
        // Placeholder until the real screens move over (step 5b-5).
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SuperfitTheme.colors.bgStart)
                .systemBarsPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SuperfitBrandHeader()
            Text(
                text = "Signed in as ${email ?: "your Apple account"}",
                style = MaterialTheme.typography.titleMedium,
                color = SuperfitTheme.colors.textPrimary
            )
            Text(
                text = when {
                    restored == null -> "Bringing back your meals and progress…"
                    restored?.failed == true -> "Couldn't reach your backup. Check your internet and open the app again."
                    else -> restored?.let { "On this iPhone: ${it.meals} meals, ${it.workouts} workouts, ${it.weighIns} weigh-ins." }.orEmpty()
                },
                style = MaterialTheme.typography.bodyLarge,
                color = SuperfitTheme.colors.textPrimary
            )
            Text(
                text = "The rest of the app comes next.",
                style = MaterialTheme.typography.bodyMedium,
                color = SuperfitTheme.colors.textSecondary
            )
            // A one-off check that the AI answers on this iPhone (placeholder until the coach moves over).
            var aiReply by remember { mutableStateOf<String?>(null) }
            var aiBusy by remember { mutableStateOf(false) }
            PrimaryButton(
                text = "Test the AI coach",
                loading = aiBusy,
                onClick = {
                    aiBusy = true
                    scope.launch {
                        aiReply = try {
                            Ai.generate("Greet a Superfit user in one short, friendly sentence.", isJson = false).trim()
                        } catch (e: Exception) {
                            Ai.friendlyError(e)
                        }
                        aiBusy = false
                    }
                }
            )
            aiReply?.let {
                Text(text = it, style = MaterialTheme.typography.bodyLarge, color = SuperfitTheme.colors.textPrimary)
            }
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
