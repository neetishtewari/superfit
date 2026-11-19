package com.superfit.app.ui.dashboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.superfit.app.ui.coach.ChatMic
import com.superfit.app.ui.coach.CoachChatView

/**
 * The coach chat, as the Coach tab: one running conversation, kept between visits, with
 * ready-made questions, a mic that sends the question when the user stops talking, and
 * "Log this" on suggested meals.
 */
@Composable
internal fun CoachChatScreen(
    viewModel: DashboardViewModel,
    modifier: Modifier = Modifier,
    onShareRoast: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val chatMessages by viewModel.chatMessages.collectAsState()
    val chatLoading by viewModel.chatLoading.collectAsState()
    val notes by viewModel.coachNotes.collectAsState()
    val coachStyle by viewModel.coachStyle.collectAsState()

    var isListening by remember { mutableStateOf(false) }
    var liveText by remember { mutableStateOf("") }
    var voiceProblem by remember { mutableStateOf<String?>(null) }
    var showMicOffDialog by remember { mutableStateOf(false) }

    // The phone's own voice-typing dialog, used when the in-app recognizer can't run on this phone.
    val systemSpeechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!text.isNullOrBlank()) viewModel.sendChatMessage(text, how = "voice")
    }

    val speechHelper = remember {
        SpeechRecognizerHelper(
            context = context,
            onResult = { heard ->
                liveText = ""
                viewModel.sendChatMessage(heard.text, how = "voice")
            },
            onError = { error ->
                liveText = ""
                voiceProblem = if (error == "No speech input detected.") "I didn't catch that. Tap the mic and try again." else error
            },
            onListeningStateChange = { isListening = it },
            onLiveText = { liveText = it },
            onNeedsSystemDialog = {
                try {
                    systemSpeechLauncher.launch(SpeechRecognizerHelper.systemDialogIntent(context))
                } catch (e: Exception) {
                    voiceProblem = "Voice input isn't available on this phone. Type your question instead."
                }
            }
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) speechHelper.startListening() else showMicOffDialog = true
    }

    DisposableEffect(Unit) {
        onDispose { speechHelper.destroy() }
    }

    fun startMic() {
        voiceProblem = null
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) speechHelper.startListening() else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    if (showMicOffDialog) {
        AlertDialog(
            onDismissRequest = { showMicOffDialog = false },
            title = { Text("Microphone is off for Superfit") },
            text = {
                Text(
                    "Superfit needs the microphone to hear your question. Nothing is recorded or kept. " +
                        "You can turn it on in Settings, or type your question instead."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showMicOffDialog = false
                    try {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                        )
                    } catch (e: Exception) {
                        Toast.makeText(context, "Please open Settings and allow the microphone for Superfit.", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Open Settings") }
            },
            dismissButton = {
                TextButton(onClick = { showMicOffDialog = false }) { Text("Not now") }
            }
        )
    }

    // The chat itself is shared with the iPhone; this screen runs Android's mic.
    CoachChatView(
        messages = chatMessages,
        loading = chatLoading,
        notes = notes,
        style = coachStyle,
        questions = viewModel::coachQuestions,
        mic = ChatMic(
            available = true,
            listening = isListening,
            liveText = liveText,
            problem = voiceProblem,
            onStart = { startMic() },
            onStop = { speechHelper.stopListening() },
            onProblemShown = { voiceProblem = null }
        ),
        onSend = { text, how -> viewModel.sendChatMessage(text, how = how) },
        onUndo = viewModel::undoChatNotes,
        onLogMeal = viewModel::logSuggestedMeal,
        modifier = modifier,
        onShareRoast = onShareRoast
    )
}

/** The Coach tab of the bottom menu: the chat, and the share preview for a roast. */
@Composable
fun CoachTab(viewModel: DashboardViewModel) {
    val dashboardState by viewModel.dashboardState.collectAsState()
    val coachingState by viewModel.coachingState.collectAsState()
    var shareCard by remember { mutableStateOf<com.superfit.app.domain.ShareCard?>(null) }
    CoachChatScreen(
        viewModel = viewModel,
        // Any roast can go on a card, even one about an empty log.
        onShareRoast = { line ->
            (dashboardState as? DashboardUiState.Success)?.let { day ->
                shareCard = com.superfit.app.domain.ShareCards.roast(dailyShareCard(day, coachingState), line)
            }
        }
    )
    shareCard?.let { card ->
        com.superfit.app.ui.share.ShareCardSheet(card = card, onDismiss = { shareCard = null })
    }
}
