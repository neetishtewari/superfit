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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.superfit.app.domain.ChatEntry
import com.superfit.app.domain.CoachChat
import com.superfit.app.domain.CoachNote
import com.superfit.app.theme.*
import com.superfit.app.ui.components.SuperfitField
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One row of the chat list: a line between days, or a message. */
private sealed interface ChatRow {
    val key: String

    data class Day(val label: String) : ChatRow {
        override val key = "day_$label"
    }

    data class Message(val entry: ChatEntry, val isLast: Boolean, val isRoast: Boolean = false) : ChatRow {
        override val key = "msg_${entry.id}"
    }
}

private fun chatRows(entries: List<ChatEntry>, today: LocalDate, zone: ZoneId): List<ChatRow> {
    val rows = mutableListOf<ChatRow>()
    var lastDay: LocalDate? = null
    entries.forEachIndexed { index, entry ->
        val day = Instant.ofEpochMilli(entry.time).atZone(zone).toLocalDate()
        if (day != lastDay) {
            rows += ChatRow.Day(CoachChat.dayLabel(day, today))
            lastDay = day
        }
        rows += ChatRow.Message(
            entry,
            isLast = index == entries.lastIndex,
            isRoast = CoachChat.questionFor(entries, index) == DashboardViewModel.ROAST_MY_DAY
        )
    }
    return rows
}

/**
 * The coach chat: one running conversation, kept between visits, with ready-made questions,
 * a mic that sends the question when the user stops talking, and "Log this" on suggested meals.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun CoachChatSheet(
    viewModel: DashboardViewModel,
    onDismiss: () -> Unit,
    onShareRoast: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val chatMessages by viewModel.chatMessages.collectAsState()
    val chatLoading by viewModel.chatLoading.collectAsState()
    val notes by viewModel.coachNotes.collectAsState()
    var chatInputText by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
            onResult = { text ->
                liveText = ""
                viewModel.sendChatMessage(text, how = "voice")
            },
            onError = { error ->
                liveText = ""
                voiceProblem = if (error == "No speech input detected.") "I didn't catch that. Tap the mic and try again." else error
            },
            onListeningStateChange = { isListening = it },
            onLiveText = { liveText = it },
            onNeedsSystemDialog = {
                try {
                    systemSpeechLauncher.launch(SpeechRecognizerHelper.systemDialogIntent())
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

    LaunchedEffect(voiceProblem) {
        if (voiceProblem != null) {
            kotlinx.coroutines.delay(4000)
            voiceProblem = null
        }
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ThemeCardBg,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = ThemeTextSecondary) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.85f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "💬 Your AI coach",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = ThemeTextPrimary
                )
                val activeNotes = notes.count { it.isActive(LocalDate.now()) }
                Text(
                    text = if (activeNotes > 0) {
                        "Knows your meals, workouts and goal. Remembering $activeNotes ${if (activeNotes == 1) "note" else "notes"} so far (Settings → What my AI coach knows)"
                    } else {
                        "Knows your meals, workouts and goal, and remembers what you tell it"
                    },
                    fontSize = 12.sp,
                    color = NeonGreen
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ThemeTextPrimary.copy(alpha = 0.03f))
                    .border(1.dp, ThemeGlassBorder, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                val zone = remember { ZoneId.systemDefault() }
                val rows = remember(chatMessages) { chatRows(chatMessages, LocalDate.now(), zone) }
                val listState = rememberLazyListState()
                LaunchedEffect(rows.size, chatLoading) {
                    if (rows.isNotEmpty()) listState.animateScrollToItem(rows.size - 1 + if (chatLoading) 1 else 0)
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (rows.isEmpty()) {
                        item(key = "hello") {
                            CoachBubble(
                                text = "Hi! I'm Superfit's AI coach. I can see your meals, workouts and goal, and I remember what you tell me, " +
                                    "like an injury or a food you don't eat. Ask me anything, or tap the mic and just talk."
                            )
                        }
                    }
                    items(rows, key = { it.key }) { row ->
                        when (row) {
                            is ChatRow.Day -> Text(
                                text = row.label,
                                color = ThemeTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp,
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            is ChatRow.Message -> ChatMessageRow(
                                entry = row.entry,
                                notes = notes,
                                showTellMeMore = row.isLast && !row.entry.fromUser && !row.entry.isError && !chatLoading,
                                onUndo = { viewModel.undoChatNotes(row.entry.id) },
                                onLogMeal = { viewModel.logSuggestedMeal(row.entry.id) },
                                onTellMeMore = { viewModel.sendChatMessage(CoachChat.TELL_ME_MORE, how = "chip") },
                                onShareRoast = onShareRoast?.takeIf { row.isRoast }?.let { share -> { share(row.entry.text) } }
                            )
                        }
                    }
                    if (chatLoading) {
                        item(key = "thinking") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = NeonGreen, strokeWidth = 2.dp)
                                Text(text = "AI coach is thinking...", color = ThemeTextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            voiceProblem?.let {
                Text(text = it, color = EnergeticCoral, fontSize = 12.sp)
            }

            if (!chatLoading && !isListening) {
                val questions = remember(chatMessages.size) { viewModel.coachQuestions() }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(questions) { q ->
                        Text(
                            text = q,
                            color = ThemeTextPrimary,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, ThemeGlassBorder, RoundedCornerShape(16.dp))
                                .clickable { viewModel.sendChatMessage(q, how = "chip") }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SuperfitField(
                    value = if (isListening) liveText else chatInputText,
                    onValueChange = { if (!isListening) chatInputText = it.take(CoachChat.MAX_TEXT) },
                    placeholder = {
                        Text(if (isListening) "Listening… speak your question" else "Ask your AI coach…", fontSize = 13.sp)
                    },
                    readOnly = isListening,
                    modifier = Modifier.weight(1f),
                    maxLines = 4
                )

                if (chatInputText.isBlank() || isListening) {
                    IconButton(
                        onClick = { if (isListening) speechHelper.stopListening() else startMic() },
                        enabled = !chatLoading,
                        modifier = Modifier.size(52.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (isListening) EnergeticCoral else NeonGreen,
                            contentColor = Color.Black,
                            disabledContainerColor = NeonGreen.copy(alpha = 0.3f)
                        )
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = if (isListening) "Stop and send" else "Ask by voice"
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            viewModel.sendChatMessage(chatInputText)
                            chatInputText = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                        enabled = !chatLoading,
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text("Send", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CoachBubble(text: String, isError: Boolean = false) {
    Box(
        modifier = Modifier
            .widthIn(max = 300.dp)
            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 2.dp, bottomEnd = 12.dp))
            .background(if (isError) EnergeticCoral.copy(alpha = 0.10f) else ThemeTextPrimary.copy(alpha = 0.05f))
            .border(
                1.dp,
                if (isError) EnergeticCoral else ThemeGlassBorder,
                RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 2.dp, bottomEnd = 12.dp)
            )
            .padding(12.dp)
    ) {
        Text(
            text = if (isError) AnnotatedString(text) else chatMarkdown(text),
            color = ThemeTextPrimary,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun ChatMessageRow(
    entry: ChatEntry,
    notes: List<CoachNote>,
    showTellMeMore: Boolean,
    onUndo: () -> Unit,
    onLogMeal: () -> Unit,
    onTellMeMore: () -> Unit,
    onShareRoast: (() -> Unit)? = null
) {
    if (entry.fromUser) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            Column(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 2.dp))
                    .background(HyperVioletAccent.copy(alpha = 0.25f))
                    .border(
                        1.dp,
                        HyperVioletAccent,
                        RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 2.dp)
                    )
                    .padding(12.dp)
            ) {
                if (entry.spoken) {
                    Text(text = "🎙 Spoken", color = ThemeTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Text(text = entry.text, color = ThemeTextPrimary, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        CoachBubble(text = entry.text, isError = entry.isError)

        val remembered = entry.rememberedIds.mapNotNull { id -> notes.firstOrNull { it.id == id }?.text }
        if (remembered.isNotEmpty() || entry.forgotten.isNotEmpty()) {
            val line = listOfNotNull(
                remembered.takeIf { it.isNotEmpty() }?.let { "Remembered: ${it.joinToString("; ")}" },
                entry.forgotten.takeIf { it.isNotEmpty() }?.let { f -> "No longer noted: ${f.joinToString("; ") { it.text }}" }
            ).joinToString(" · ")
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, NeonGreen.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "✓ $line", color = NeonGreen, fontSize = 12.sp, modifier = Modifier.weight(1f, fill = false))
                Text(
                    text = "Undo",
                    color = ThemeTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onUndo)
                )
            }
        }

        val meal = entry.mealToLog
        if (meal != null) {
            if (entry.mealLogged) {
                Text(text = "✓ Added to today's meals: $meal", color = NeonGreen, fontSize = 12.sp)
            } else {
                Button(
                    onClick = onLogMeal,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("+ Log this: $meal", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
        }

        if (onShareRoast != null) {
            Button(
                onClick = onShareRoast,
                colors = ButtonDefaults.buttonColors(containerColor = com.superfit.app.theme.SolarAmber, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("🔥 Share as a card", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (showTellMeMore) {
            Text(
                text = CoachChat.TELL_ME_MORE,
                color = ThemeTextPrimary,
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .border(1.dp, ThemeGlassBorder, CircleShape)
                    .clickable(onClick = onTellMeMore)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}
