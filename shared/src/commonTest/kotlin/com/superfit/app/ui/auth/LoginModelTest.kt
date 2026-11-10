package com.superfit.app.ui.auth

import com.superfit.app.data.AuthClient
import com.superfit.app.data.AuthFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent

class LoginModelTest {
    private class FakeAuth(var failWith: String? = null) : AuthClient {
        val calls = mutableListOf<String>()
        override suspend fun signIn(email: String, password: String) = record("signIn $email")
        override suspend fun createAccount(email: String, password: String) = record("create $email")
        override suspend fun sendPasswordReset(email: String) = record("reset $email")
        private fun record(call: String) {
            calls += call
            failWith?.let { throw AuthFailure(it) }
        }
    }

    @Test
    fun signInTrimsTheEmailAndRunsSuccessOnce() {
        val scope = TestScope()
        val auth = FakeAuth()
        var uploads = 0
        val model = LoginModel(auth, scope) { uploads++ }
        var successes = 0
        model.onEmailChange("  me@example.com ")
        model.onPasswordChange("secret1")
        model.authenticate { successes++ }
        scope.runCurrent()
        assertEquals(listOf("signIn me@example.com"), auth.calls)
        assertEquals(1, successes)
        assertEquals(0, uploads, "signing in must not upload; the restore runs once, after")
        assertFalse(model.isLoading)
    }

    @Test
    fun newAccountUploadsWhatIsOnThePhone() {
        val scope = TestScope()
        val auth = FakeAuth()
        var uploads = 0
        val model = LoginModel(auth, scope) { uploads++ }
        model.toggleMode()
        model.onEmailChange("me@example.com")
        model.onPasswordChange("secret1")
        model.onConfirmPasswordChange("secret1")
        model.authenticate {}
        scope.runCurrent()
        assertEquals(listOf("create me@example.com"), auth.calls)
        assertEquals(1, uploads)
    }

    @Test
    fun problemsShowAPlainMessageAndNoSuccess() {
        val scope = TestScope()
        val model = LoginModel(FakeAuth(failWith = "Wrong email or password."), scope)
        var successes = 0
        model.onEmailChange("me@example.com")
        model.onPasswordChange("bad")
        model.authenticate { successes++ }
        scope.runCurrent()
        assertEquals("Wrong email or password.", model.errorMessage)
        assertEquals(0, successes)
        assertFalse(model.isLoading)
    }

    @Test
    fun emptyFieldsAndMismatchedPasswordsNeverCallFirebase() {
        val scope = TestScope()
        val auth = FakeAuth()
        val model = LoginModel(auth, scope)
        model.authenticate {}
        assertEquals(AuthMessages.EMPTY_FIELDS, model.errorMessage)
        model.toggleMode()
        model.onEmailChange("me@example.com")
        model.onPasswordChange("secret1")
        model.onConfirmPasswordChange("secret2")
        model.authenticate {}
        scope.runCurrent()
        assertEquals(AuthMessages.PASSWORDS_DIFFER, model.errorMessage)
        assertTrue(auth.calls.isEmpty())
    }

    @Test
    fun googleOrAppleSignInClearsOldErrors() {
        val scope = TestScope()
        val model = LoginModel(FakeAuth(), scope)
        model.setCustomErrorMessage("old")
        var successes = 0
        model.signInWith({ successes++ }) {}
        scope.runCurrent()
        assertNull(model.errorMessage)
        assertEquals(1, successes)
    }

    @Test
    fun resetNeedsAnEmailAndConfirmsWhereItWent() {
        val scope = TestScope()
        val auth = FakeAuth()
        val model = LoginModel(auth, scope)
        var result: Pair<Boolean, String?>? = null
        model.resetPassword(" ") { ok, msg -> result = ok to msg }
        assertEquals(false to AuthMessages.NEED_EMAIL, result)
        model.resetPassword(" me@example.com") { ok, msg -> result = ok to msg }
        scope.runCurrent()
        assertEquals(true to AuthMessages.resetSent("me@example.com"), result)
        assertEquals(listOf("reset me@example.com"), auth.calls)
    }
}
