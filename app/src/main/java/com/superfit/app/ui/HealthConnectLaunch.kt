package com.superfit.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.health.connect.client.HealthConnectClient
import com.superfit.app.data.AppAnalytics

private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"

/**
 * Asks for Health Connect permissions without crashing on phones that don't have it.
 * Android 13 and older need the Health Connect app from the Play Store; without it the
 * permission screen doesn't exist and launching it throws ActivityNotFoundException.
 */
fun launchHealthConnectPermissions(
    context: Context,
    launcher: ActivityResultLauncher<Set<String>>,
    permissions: Set<String>
) {
    when (HealthConnectClient.getSdkStatus(context, HEALTH_CONNECT_PACKAGE)) {
        HealthConnectClient.SDK_AVAILABLE -> try {
            launcher.launch(permissions)
        } catch (e: Exception) {
            AppAnalytics.recordError(e)
            Toast.makeText(context, "Couldn't open Health Connect. You can connect later in Settings.", Toast.LENGTH_LONG).show()
        }
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
            Toast.makeText(context, "Install or update Health Connect, then come back and tap Connect.", Toast.LENGTH_LONG).show()
            openHealthConnectInPlayStore(context)
        }
        else -> Toast.makeText(context, "Health Connect isn't available on this phone. You can still log everything by hand.", Toast.LENGTH_LONG).show()
    }
}

private fun openHealthConnectInPlayStore(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$HEALTH_CONNECT_PACKAGE&url=healthconnect%3A%2F%2Fonboarding"))
        .setPackage("com.android.vending")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$HEALTH_CONNECT_PACKAGE"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
        }
    }
}

/**
 * Opens the screen where the person can see and change what Superfit may read. Android 14+
 * goes straight to Superfit's own page in Health Connect; older phones open the Health
 * Connect app's settings.
 */
fun openHealthConnectSettings(context: Context) {
    val intents = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            add(
                Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS")
                    .putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)
            )
        }
        add(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
    }
    for (intent in intents) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: Exception) {
            AppAnalytics.recordError(e)
        }
    }
    Toast.makeText(context, "Couldn't open Health Connect. Open it from your phone's Settings.", Toast.LENGTH_LONG).show()
}
