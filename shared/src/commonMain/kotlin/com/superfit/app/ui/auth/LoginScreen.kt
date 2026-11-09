package com.superfit.app.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superfit.app.theme.NeonMint
import com.superfit.app.theme.SuperfitTheme
import com.superfit.app.ui.components.PrimaryButton
import com.superfit.app.ui.components.SuperfitBrandHeader
import com.superfit.app.ui.components.SuperfitField
import com.superfit.shared.resources.Res
import com.superfit.shared.resources.ic_google_logo
import org.jetbrains.compose.resources.painterResource

/**
 * The sign-in screen, the same on Android and iPhone. Each app runs its own Google sheet
 * ([onGoogle]); the iPhone also passes [onApple] for Sign in with Apple.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    model: LoginModel,
    onLoginSuccess: () -> Unit,
    onGoogle: () -> Unit,
    modifier: Modifier = Modifier,
    onApple: (() -> Unit)? = null
) {
    val focusManager = LocalFocusManager.current
    var passwordVisible by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var resetEmail by remember { mutableStateOf("") }
    var resetSuccessMessage by remember { mutableStateOf<String?>(null) }
    var resetErrorMessage by remember { mutableStateOf<String?>(null) }

    val colors = SuperfitTheme.colors

    val isLogin = model.isLoginMode
    val passwordHint: @Composable () -> Unit = { Text("At least 6 characters") }
    val submit = {
        focusManager.clearFocus()
        model.authenticate(onLoginSuccess)
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

            // iPhone: Sign in with Apple first (Apple asks for it whenever Google sign-in is offered).
            if (onApple != null) {
                AppleSignInButton(
                    onClick = {
                        focusManager.clearFocus()
                        onApple()
                    },
                    enabled = !model.isLoading
                )
            }

            // Google: one tap for most people, and it works for new and returning accounts alike.
            OutlinedButton(
                onClick = {
                    focusManager.clearFocus()
                    onGoogle()
                },
                enabled = !model.isLoading,
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
                    painter = painterResource(Res.drawable.ic_google_logo),
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
                value = model.email,
                onValueChange = { model.onEmailChange(it) },
                label = { Text("Email") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            SuperfitField(
                value = model.password,
                onValueChange = { model.onPasswordChange(it) },
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
                    value = model.confirmPassword,
                    onValueChange = { model.onConfirmPasswordChange(it) },
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
                            resetEmail = model.email
                            resetSuccessMessage = null
                            resetErrorMessage = null
                            showForgotPasswordDialog = true
                        }
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                )
            }

            model.errorMessage?.let { message ->
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
                loading = model.isLoading
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
                        .clickable { model.toggleMode() }
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
                                model.resetPassword(resetEmail) { success, msg ->
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

/**
 * Apple's own look for its button (black, or white in dark mode, with the Apple logo), so it
 * passes App Review. The logo is the Apple symbol in the iPhone's system font.
 */
@Composable
private fun AppleSignInButton(onClick: () -> Unit, enabled: Boolean) {
    val dark = SuperfitTheme.colors.isDark
    val background = if (dark) Color.White else Color.Black
    val content = if (dark) Color.Black else Color.White
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = background, contentColor = content)
    ) {
        Text("\uF8FF", fontFamily = FontFamily.Default, fontSize = 20.sp)
        Spacer(modifier = Modifier.width(10.dp))
        Text("Continue with Apple", style = MaterialTheme.typography.titleMedium)
    }
}
