package com.superfit.app.data

import android.content.Context
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.superfit.app.domain.InviteLink

/**
 * This phone's own invite code (made once, kept in the normal app backup so it survives a
 * reinstall) and, if this install came from someone's invite, their code.
 */
object InviteStore {
    private const val PREFS = "superfit_invites"
    private const val KEY_MY_CODE = "my_code"
    private const val KEY_INVITED_BY = "invited_by"
    private const val KEY_REFERRER_CHECKED = "referrer_checked"
    private const val KEY_REFERRER_TRIES = "referrer_tries"
    private const val MAX_TRIES = 3

    fun myCode(context: Context): String {
        val prefs = prefs(context)
        prefs.getString(KEY_MY_CODE, null)?.takeIf { InviteLink.isValidCode(it) }?.let { return it }
        val code = InviteLink.newCode()
        prefs.edit().putString(KEY_MY_CODE, code).apply()
        return code
    }

    /** The code of whoever invited this install, or null. Kept for gangs to use later. */
    fun invitedBy(context: Context): String? = prefs(context).getString(KEY_INVITED_BY, null)

    /**
     * Reads the Play install referrer once per install. Play keeps it for 90 days after the
     * install, and the first app open is well inside that. Retries on a later open if the Play
     * Store couldn't be reached, up to [MAX_TRIES] times.
     */
    fun checkInstallReferrer(context: Context) {
        val app = context.applicationContext
        val prefs = prefs(app)
        if (prefs.getBoolean(KEY_REFERRER_CHECKED, false)) return
        val tries = prefs.getInt(KEY_REFERRER_TRIES, 0)
        if (tries >= MAX_TRIES) return
        prefs.edit().putInt(KEY_REFERRER_TRIES, tries + 1).apply()

        val client = try {
            InstallReferrerClient.newBuilder(app).build()
        } catch (e: Exception) {
            android.util.Log.w("InviteStore", "Install referrer unavailable", e)
            return
        }
        try {
            client.startConnection(object : InstallReferrerStateListener {
                override fun onInstallReferrerSetupFinished(responseCode: Int) {
                    try {
                        when (responseCode) {
                            InstallReferrerClient.InstallReferrerResponse.OK -> {
                                val code = InviteLink.codeFromReferrer(client.installReferrer.installReferrer)
                                val edit = prefs.edit().putBoolean(KEY_REFERRER_CHECKED, true)
                                if (code != null && code != prefs.getString(KEY_MY_CODE, null)) {
                                    edit.putString(KEY_INVITED_BY, code)
                                    AppAnalytics.inviteJoined()
                                }
                                edit.apply()
                            }
                            // Not installed from Play (for example the dev app): nothing to read, ever.
                            InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED ->
                                prefs.edit().putBoolean(KEY_REFERRER_CHECKED, true).apply()
                            else -> Unit
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("InviteStore", "Could not read install referrer", e)
                    } finally {
                        runCatching { client.endConnection() }
                    }
                }

                override fun onInstallReferrerServiceDisconnected() = Unit
            })
        } catch (e: Exception) {
            android.util.Log.w("InviteStore", "Install referrer connection failed", e)
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
