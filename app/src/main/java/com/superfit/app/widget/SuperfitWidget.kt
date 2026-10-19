package com.superfit.app.widget

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.superfit.app.MainActivity
import com.superfit.app.R
import com.superfit.app.data.getUserSharedPrefs
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.LocalDate

/** Home-screen widget: calories left today, plus mic and camera buttons that open Superfit ready to log. */
class SuperfitWidget : GlanceAppWidget() {

    // Real size, so a narrow widget can stack the number above "kcal left".
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val hasCamera = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        provideContent {
            // Read inside the composition so a running widget session redraws when the snapshot changes.
            val snapshot by remember { snapshotChanges(context) }
                .collectAsState(initial = WidgetSnapshotStore.load(context))
            WidgetContent(context, widgetText(snapshot, LocalDate.now().toString()), hasCamera)
        }
    }

    companion object {
        const val ACTION_VOICE_LOG = "com.superfit.app.ACTION_VOICE_LOG"
        const val ACTION_PHOTO_LOG = "com.superfit.app.ACTION_PHOTO_LOG"

        /** Redraws every placed Superfit widget with the latest snapshot. */
        suspend fun refresh(context: Context) {
            runCatching { SuperfitWidget().updateAll(context) }
                .onFailure { android.util.Log.w("SuperfitWidget", "Widget refresh failed", it) }
        }
    }
}

/** Emits the saved snapshot each time the dashboard or a meal change writes a new one. */
private fun snapshotChanges(context: Context): Flow<WidgetSnapshot?> = callbackFlow {
    val prefs = getUserSharedPrefs(context)
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null && key.startsWith("widget_")) trySend(WidgetSnapshotStore.load(context))
    }
    prefs.registerOnSharedPreferenceChangeListener(listener)
    awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
}

class SuperfitWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SuperfitWidget()
}

private val TextPrimary = ColorProvider(Color.White)
private val TextSecondary = ColorProvider(Color(0xFFB4B4C8))

@Composable
private fun WidgetContent(context: Context, text: WidgetText, hasCamera: Boolean) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_background))
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .clickable(actionStartActivity(openIntent(context, null))),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val narrow = LocalSize.current.width < 250.dp
        Column(modifier = GlanceModifier.defaultWeight()) {
            when {
                text.subline == null -> Text(
                    text = text.headline,
                    style = TextStyle(color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    maxLines = 2
                )
                narrow -> {
                    // "1,240" on its own line, "kcal left" under it.
                    Text(
                        text = text.headline.substringBefore(' '),
                        style = TextStyle(color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1
                    )
                    Text(
                        text = text.headline.substringAfter(' '),
                        style = TextStyle(color = TextSecondary, fontSize = 12.sp),
                        maxLines = 1
                    )
                }
                else -> {
                    Text(text = "Today", style = TextStyle(color = TextSecondary, fontSize = 12.sp))
                    Text(
                        text = text.headline,
                        style = TextStyle(color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1
                    )
                    Text(text = text.subline, style = TextStyle(color = TextSecondary, fontSize = 12.sp), maxLines = 1)
                }
            }
        }
        RoundButton(context, R.drawable.ic_widget_mic, "Log by voice", SuperfitWidget.ACTION_VOICE_LOG)
        if (hasCamera) {
            Spacer(modifier = GlanceModifier.width(8.dp))
            RoundButton(context, R.drawable.ic_widget_camera, "Log with camera", SuperfitWidget.ACTION_PHOTO_LOG)
        }
    }
}

@Composable
private fun RoundButton(context: Context, icon: Int, description: String, action: String) {
    Box(
        modifier = GlanceModifier
            .size(44.dp)
            .background(ImageProvider(R.drawable.widget_button_background))
            .clickable(actionStartActivity(openIntent(context, action))),
        contentAlignment = Alignment.Center
    ) {
        Image(provider = ImageProvider(icon), contentDescription = description, modifier = GlanceModifier.size(24.dp))
    }
}

/**
 * Mic and camera use the reminder notification's flags, so Superfit opens on Home and handles the action.
 * A plain tap on the numbers just brings Superfit forward without losing the screen the user was on.
 */
private fun openIntent(context: Context, action: String?): Intent =
    Intent(context, MainActivity::class.java).apply {
        if (action != null) {
            this.action = action
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        } else {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
    }
