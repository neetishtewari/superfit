package com.superfit.app.ui.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider
import com.superfit.app.theme.*
import com.superfit.app.ui.components.PrimaryButton
import com.superfit.app.ui.components.SuperfitBrandHeader
import com.superfit.app.ui.components.SuperfitField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    var passwordVisible by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var resetEmail by remember { mutableStateOf("") }
    var resetSuccessMessage by remember { mutableStateOf<String?>(null) }
    var resetErrorMessage by remember { mutableStateOf<String?>(null) }

    val colors = SuperfitTheme.colors

    val context = LocalContext.current
    val resources = LocalResources.current
    val webClientId = try {
        resources.getString(com.superfit.app.R.string.default_web_client_id)
    } catch (e: Exception) {
        null
    }

    val googleSignInClient = remember(webClientId) {
        if (webClientId != null) {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build()
            GoogleSignIn.getClient(context, gso)
        } else {
            null
        }
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken != null) {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                viewModel.signInWithCredential(credential, onLoginSuccess)
            } else {
                viewModel.setCustomErrorMessage(AuthMessages.forGoogle(-1))
            }
        } catch (e: ApiException) {
            viewModel.setCustomErrorMessage(AuthMessages.forGoogle(e.statusCode))
        }
    }

    val isLogin = viewModel.isLoginMode
    val passwordHint: @Composable () -> Unit = { Text("At least 6 characters") }
    val submit = {
        focusManager.clearFocus()
        viewModel.authenticate(onLoginSuccess)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgStart)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            SuperfitBrandHeader()
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (isLogin) "Welcome back" else "Create your account",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary
            )
            Text(
                text = if (isLogin) {
                    "Sign in to pick up where you left off. Your meals and progress are backed up."
                } else {
                    "Free, no ads. Your meals and progress are backed up to your account."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary
            )

            // Google first: one tap for most people, and it works for new and returning accounts alike.
            OutlinedButton(
                onClick = {
                    focusManager.clearFocus()
                    if (webClientId != null && googleSignInClient != null) {
                        googleSignInLauncher.launch(googleSignInClient.signInIntent)
                    } else {
                        viewModel.setCustomErrorMessage(AuthMessages.NOT_SET_UP)
                    }
                },
                enabled = !viewModel.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = colors.cardBg,
                    contentColor = colors.textPrimary
                ),
                border = BorderStroke(1.dp, colors.textTertiary.copy(alpha = 0.35f))
            ) {
                Icon(
                    painter = painterResource(id = com.superfit.app.R.drawable.ic_google_logo),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color.Unspecified
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("Continue with Google", style = MaterialTheme.typography.titleMedium)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = colors.textTertiary.copy(alpha = 0.25f))
                Text(
                    text = "or use email",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = colors.textTertiary.copy(alpha = 0.25f))
            }

            SuperfitField(
                value = viewModel.email,
                onValueChange = { viewModel.onEmailChange(it) },
                label = { Text("Email") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            SuperfitField(
                value = viewModel.password,
                onValueChange = { viewModel.onPasswordChange(it) },
                label = { Text("Password") },
                supportingText = if (isLogin) null else passwordHint,
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = if (isLogin) ImeAction.Done else ImeAction.Next
                ),
                keyboardActions = KeyboardActions(onDone = { if (isLogin) submit() }),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            AnimatedVisibility(
                visible = !isLogin,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                SuperfitField(
                    value = viewModel.confirmPassword,
                    onValueChange = { viewModel.onConfirmPasswordChange(it) },
                    label = { Text("Password again") },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            if (isLogin) {
                Text(
                    text = "Forgot password?",
                    color = colors.primaryAccent,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .align(Alignment.End)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            resetEmail = viewModel.email
                            resetSuccessMessage = null
                            resetErrorMessage = null
                            showForgotPasswordDialog = true
                        }
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                )
            }

            viewModel.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = colors.errorColor,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.amberSoft)
                        .padding(12.dp)
                )
            }

            PrimaryButton(
                text = if (isLogin) "Sign in" else "Create account",
                onClick = submit,
                loading = viewModel.isLoading
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isLogin) "New to Superfit? " else "Already have an account? ",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = if (isLogin) "Create an account" else "Sign in",
                    color = colors.primaryAccent,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { viewModel.toggleMode() }
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                )
            }
        }

        if (showForgotPasswordDialog) {
            AlertDialog(
                onDismissRequest = { showForgotPasswordDialog = false },
                containerColor = colors.cardBg,
                title = {
                    Text(
                        text = "Reset your password",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "We'll email you a link to set a new one.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary
                        )

                        SuperfitField(
                            value = resetEmail,
                            onValueChange = { resetEmail = it },
                            label = { Text("Email") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        if (resetErrorMessage != null) {
                            Text(
                                text = resetErrorMessage!!,
                                color = colors.errorColor,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (resetSuccessMessage != null) {
                            Text(
                                text = resetSuccessMessage!!,
                                color = colors.primaryAccent,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (resetSuccessMessage != null) {
                                showForgotPasswordDialog = false
                            } else {
                                viewModel.resetPassword(resetEmail) { success, msg ->
                                    if (success) {
                                        resetSuccessMessage = msg
                                        resetErrorMessage = null
                                    } else {
                                        resetErrorMessage = msg
                                        resetSuccessMessage = null
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonMint,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (resetSuccessMessage != null) "Close" else "Send link",
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    if (resetSuccessMessage == null) {
                        TextButton(
                            onClick = { showForgotPasswordDialog = false }
                        ) {
                            Text("Cancel", color = colors.textSecondary)
                        }
                    }
                }
            )
        }
    }
}
