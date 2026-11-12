package com.superfit.app.ui.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superfit.app.data.Now
import com.superfit.app.domain.ChatEntry
import com.superfit.app.domain.CoachChat
import com.superfit.app.domain.CoachNote
import com.superfit.app.domain.CoachStyle
import com.superfit.app.theme.SolarAmber
import com.superfit.app.theme.ThemeAmberSoft
import com.superfit.app.theme.ThemeBgStart
import com.superfit.app.theme.ThemeCoachSoft
import com.superfit.app.theme.ThemeErrorColor
import com.superfit.app.theme.ThemeGlassBorder
import com.superfit.app.theme.ThemeTertiaryAccent
import com.superfit.app.theme.ThemeTextPrimary
import com.superfit.app.theme.ThemeTextSecondary
import com.superfit.app.ui.components.FieldActionButton
import com.superfit.app.ui.components.IconBadge
import com.superfit.app.ui.components.MicButton
import com.superfit.app.ui.components.SuperfitField
import com.superfit.app.ui.dashboard.HyperVioletAccent
import com.superfit.app.ui.dashboard.NeonGreen
import com.superfit.app.ui.dashboard.chatMarkdown
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

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

@OptIn(ExperimentalTime::class)
private fun chatRows(entries: List<ChatEntry>, today: LocalDate, zone: TimeZone): List<ChatRow> {
    val rows = mutableListOf<ChatRow>()
    var lastDay: LocalDate? = null
    entries.forEachIndexed { index, entry ->
        val day = Instant.fromEpochMilliseconds(entry.time).toLocalDateTime(zone).date
        if (day != lastDay) {
            rows += ChatRow.Day(CoachChat.dayLabel(day, today))
            lastDay = day
        }
        rows += ChatRow.Message(
            entry,
            isLast = index == entries.lastIndex,
            isRoast = CoachChat.isRoastRequest(CoachChat.questionFor(entries, index))
        )
    }
    return rows
}

/** The phone's mic for the chat, as each app runs it; the question is sent when the person stops talking. */
data class ChatMic(
    val available: Boolean,
    val listening: Boolean,
    val liveText: String,
    /** A short line when listening went wrong, shown for a few seconds. */
    val problem: String?,
    val onStart: () -> Unit,
    val onStop: () -> Unit,
    val onProblemShown: () -> Unit = {}
)

/**
 * The coach chat, the same on Android and iPhone: one running conversation, kept between visits,
 * with ready-made questions, a mic that sends the question when the person stops talking, and
 * "Log this" on suggested meals. [onSend] gets the words and "typed", "voice" or "chip".
 */
@Composable
fun CoachChatView(
    messages: List<ChatEntry>,
    loading: Boolean,
    notes: List<CoachNote>,
    style: CoachStyle,
    questions: () -> List<String>,
    mic: ChatMic,
    onSend: (text: String, how: String) -> Unit,
    onUndo: (Long) -> Unit,
    onLogMeal: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onShareRoast: ((String) -> Unit)? = null
) {
    var input by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(mic.problem) {
        if (mic.problem != null) {
            kotlinx.coroutines.delay(4000)
            mic.onProblemShown()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(ThemeBgStart)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconBadge(icon = Icons.Default.AutoAwesome, background = HyperVioletAccent, tint = Color.White, size = 36)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = "AI coach", style = MaterialTheme.typography.titleLarge, color = ThemeTextPrimary)
                    val activeNotes = notes.count { it.isActive(Now.today()) }
                    Text(
                        text = if (activeNotes > 0) {
                            "Knows your meals, workouts and goal, plus $activeNotes ${if (activeNotes == 1) "note" else "notes"} you've shared"
                        } else {
                            "Knows your meals, workouts and goal, and remembers what you tell it"
                        },
                        fontSize = 12.sp,
                        color = ThemeTextSecondary
                    )
                }
                // The coach's current style; changed in Settings.
                Text(
                    text = style.label,
                    color = ThemeTertiaryAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(ThemeCoachSoft)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 4.dp)) {
                val rows = remember(messages) { chatRows(messages, Now.today(), TimeZone.currentSystemDefault()) }
                val listState = rememberLazyListState()
                LaunchedEffect(rows.size, loading) {
                    if (rows.isNotEmpty()) listState.animateScrollToItem(rows.size - 1 + if (loading) 1 else 0)
                }
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                textAlign = TextAlign.Center
                            )
                            is ChatRow.Message -> ChatMessageRow(
                                entry = row.entry,
                                notes = notes,
                                showTellMeMore = row.isLast && !row.entry.fromUser && !row.entry.isError && !loading,
                                onUndo = { onUndo(row.entry.id) },
                                onLogMeal = { onLogMeal(row.entry.id) },
                                onTellMeMore = { onSend(CoachChat.TELL_ME_MORE, "chip") },
                                onShareRoast = onShareRoast?.takeIf { row.isRoast }?.let { share -> { share(row.entry.text) } }
                            )
                        }
                    }
                    if (loading) {
                        item(key = "thinking") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = NeonGreen, strokeWidth = 2.dp)
                                Text(text = "AI coach is thinking...", color = ThemeTextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            mic.problem?.let { Text(text = it, color = ThemeErrorColor, fontSize = 12.sp) }

            if (!loading && !mic.listening) {
                val chips = remember(messages.size, style) { questions() }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(chips) { q ->
                        if (q == CoachChat.ROAST_MY_DAY) {
                            // The roast is the fun bit of Roast me style, so it looks like a button, not a question.
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(SolarAmber)
                                    .clickable { onSend(q, "chip") }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Text(text = q, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Text(
                                text = q,
                                color = ThemeTextPrimary,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(1.dp, ThemeGlassBorder, RoundedCornerShape(16.dp))
                                    .clickable { onSend(q, "chip") }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SuperfitField(
                    value = if (mic.listening) mic.liveText else input,
                    onValueChange = { if (!mic.listening) input = it.take(CoachChat.MAX_TEXT) },
                    placeholder = { Text(if (mic.listening) "Listening… speak your question" else "Ask your coach…") },
                    readOnly = mic.listening,
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                    trailingIcon = {
                        Row(
                            modifier = Modifier.padding(end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // The same mic as everywhere else; while listening it becomes Stop and send.
                            if (mic.listening) {
                                FieldActionButton(icon = Icons.Default.Stop, contentDescription = "Stop and send", enabled = true, onClick = mic.onStop)
                            } else {
                                if (input.isBlank() && mic.available) {
                                    MicButton(onClick = { if (!loading) mic.onStart() }, contentDescription = "Ask by voice")
                                }
                                FieldActionButton(
                                    icon = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send",
                                    enabled = input.isNotBlank() && !loading,
                                    onClick = {
                                        onSend(input, "typed")
                                        input = ""
                                    }
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun CoachBubble(text: String, isError: Boolean = false) {
    Box(
        modifier = Modifier
            .widthIn(max = 300.dp)
            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp))
            .background(if (isError) ThemeAmberSoft else ThemeCoachSoft)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = if (isError) AnnotatedString(text) else chatMarkdown(text),
            color = ThemeTextPrimary,
            fontSize = 14.sp,
            lineHeight = 20.sp
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
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp))
                    .background(NeonGreen)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                if (entry.spoken) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Mic, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(14.dp))
                        Text(text = "Spoken", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(text = entry.text, color = Color.White, fontSize = 14.sp, lineHeight = 20.sp)
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
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.heightIn(min = 32.dp)
                ) {
                    Text("+ Log this: $meal", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        if (onShareRoast != null) {
            Button(
                onClick = onShareRoast,
                colors = ButtonDefaults.buttonColors(containerColor = SolarAmber, contentColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.heightIn(min = 32.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Share as a card", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
