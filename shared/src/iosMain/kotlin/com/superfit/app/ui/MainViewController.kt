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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import com.superfit.app.data.IosAuth
import com.superfit.app.data.IosAuthClient
import com.superfit.app.theme.SuperfitTheme
import com.superfit.app.ui.auth.LoginModel
import com.superfit.app.ui.auth.LoginScreen
import com.superfit.app.ui.components.PrimaryButton
import com.superfit.app.ui.components.SuperfitBrandHeader
import platform.UIKit.UIViewController

/** The iPhone app's root screen, shown by the Swift app (ContentView.swift). */
@Suppress("FunctionName", "unused")
fun MainViewController(auth: IosAuth): UIViewController = ComposeUIViewController {
    SuperfitTheme(darkTheme = isSystemInDarkTheme()) {
        IosRoot(auth)
    }
}

@Composable
private fun IosRoot(auth: IosAuth) {
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
                text = "The rest of the app comes next.",
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
