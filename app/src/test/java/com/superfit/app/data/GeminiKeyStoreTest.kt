package com.superfit.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class GeminiKeyStoreTest {

    @Test
    fun `old own keys are deleted`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val secrets = context.getSharedPreferences("superfit_secrets", Context.MODE_PRIVATE)
        secrets.edit().putString("gemini_api_key_user1", "AIza-old-key").commit()

        GeminiKeyStore.forgetOldKeys(context)

        assertTrue(secrets.all.isEmpty())
    }
}
