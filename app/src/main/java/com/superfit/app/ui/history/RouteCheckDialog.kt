package com.superfit.app.ui.history

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import com.superfit.app.data.RouteCheck
import com.superfit.app.data.SourceAppNames
import com.superfit.app.ui.launchHealthConnectPermissions
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Dev app only: lists the last 30 days of workouts and whether each one came with a GPS route,
 * heights and a climb total, so we know which apps can feed route share cards.
 * Each result also goes to logcat under the tag RouteCheck.
 */
@Composable
fun RouteCheckDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var runs by remember { mutableIntStateOf(0) }
    var text by remember { mutableStateOf("Checking…") }
    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { runs++ }
    LaunchedEffect(runs) {
        val client = try {
            HealthConnectClient.getOrCreate(context)
        } catch (e: Exception) {
            text = "Health Connect isn't available on this phone."
            return@LaunchedEffect
        }
        val granted = client.permissionController.getGrantedPermissions()
        val perms = buildString {
            append("Routes permission: ").append(if (RouteCheck.ROUTES_PERMISSION in granted) "allowed" else "not allowed").append('\n')
            append("Climb permission: ").append(if (RouteCheck.ELEVATION_PERMISSION in granted) "allowed" else "not allowed").append("\n\n")
        }
        val fmt = DateTimeFormatter.ofPattern("d MMM HH:mm")
        text = RouteCheck.run(client).fold(
            onSuccess = { rows ->
                if (rows.isEmpty()) {
                    perms + "No workouts in the last 30 days."
                } else {
                    perms + rows.joinToString("\n\n") { r ->
                        val route = when (r.state) {
                            RouteCheck.RouteState.HAS_ROUTE -> r.route!!.let {
                                "ROUTE: ${it.points} points, ${"%.2f".format(it.km)} km, heights on ${it.withHeight}" +
                                    (it.climbFromHeights?.let { c -> ", climb from heights ${c.toInt()} m" } ?: "")
                            }
                            RouteCheck.RouteState.NEEDS_OK -> "ROUTE shared, but needs OK per workout"
                            RouteCheck.RouteState.NO_ROUTE -> "no route"
                        }
                        val climb = r.climbMetres?.let { "climb total ${it.toInt()} m" } ?: "no climb total"
                        "${r.start.atZone(ZoneId.systemDefault()).format(fmt)} · ${r.type} · ${SourceAppNames.label(context, r.sourcePackage)}\n$route; $climb"
                            .also { Log.i("RouteCheck", "${r.sourcePackage} | " + it.replace('\n', ' ')) }
                    }
                }
            },
            onFailure = { e -> perms + "Couldn't read workouts: ${e.message}" }
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Route check (dev app)") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            TextButton(onClick = { launchHealthConnectPermissions(context, launcher, RouteCheck.permissions) }) { Text("Allow routes") }
        }
    )
}
