package com.superfit.app

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug builds (the "Superfit (dev)" app) can't pass Play Integrity, so they use App Check's debug
 * provider. On first launch it logs a debug token to logcat; add that token in the Firebase console
 * under App Check > Apps > Manage debug tokens so the dev app's AI requests are accepted.
 */
internal object AppCheckProviders {
    fun factory(): AppCheckProviderFactory = DebugAppCheckProviderFactory.getInstance()
}
