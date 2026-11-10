package com.superfit.app.ui.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

/** The shared sign-in screen with Google's Android sign-in sheet. */
@Composable
fun AndroidLoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val webClientId = try {
        resources.getString(com.superfit.app.R.string.default_web_client_id)
    } catch (e: Exception) {
        null
    }

    val googleSignInClient = remember(webClientId) {
        webClientId?.let {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(it)
                .requestEmail()
                .build()
            GoogleSignIn.getClient(context, gso)
        }
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val idToken = task.getResult(ApiException::class.java).idToken
            if (idToken != null) {
                viewModel.signInWithGoogle(idToken, onLoginSuccess)
            } else {
                viewModel.model.setCustomErrorMessage(AuthMessages.forGoogle(-1))
            }
        } catch (e: ApiException) {
            viewModel.model.setCustomErrorMessage(AuthMessages.forGoogle(e.statusCode))
        }
    }

    LoginScreen(
        model = viewModel.model,
        onLoginSuccess = onLoginSuccess,
        onGoogle = {
            if (googleSignInClient != null) {
                googleSignInLauncher.launch(googleSignInClient.signInIntent)
            } else {
                viewModel.model.setCustomErrorMessage(AuthMessages.NOT_SET_UP)
            }
        },
        modifier = modifier
    )
}
