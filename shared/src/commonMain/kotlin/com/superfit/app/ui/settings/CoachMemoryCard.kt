package com.superfit.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superfit.app.data.Now
import com.superfit.app.domain.CoachNote
import com.superfit.app.domain.CoachNotes
import com.superfit.app.domain.EnglishDates
import com.superfit.app.domain.NoteKind
import com.superfit.app.theme.DeleteRed
import com.superfit.app.theme.SuperfitTheme
import com.superfit.app.ui.components.FieldActionButton
import com.superfit.app.ui.components.LocalVoiceTyping
import com.superfit.app.ui.components.MicButton
import com.superfit.app.ui.components.SuperfitField
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/**
 * "What my AI coach knows", the same on Android and iPhone: every note the AI coach keeps about
 * the person, from chats or typed here. Each can be removed. Also clears the chat history.
 */
@Composable
fun CoachMemoryCard(
    notes: List<CoachNote>,
    onRemove: (String) -> Unit,
    onAdd: (String) -> Unit,
    onClearChat: () -> Unit
) {
    val today = Now.today()
    val active = notes.filter { it.isActive(today) }
    var newNote by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "What my AI coach knows", fontWeight = FontWeight.Bold, color = SuperfitTheme.colors.textPrimary, fontSize = 14.sp)
        Text(
            text = "Notes Superfit's AI coach picked up from your chats, like an injury or a food you don't eat. It uses them for its answers and your daily tip. Remove anything that's wrong. " +
                "This is separate from your nutritionist's advice, which comes from a real person and always comes first.",
            color = SuperfitTheme.colors.textSecondary,
            fontSize = 12.sp,
            lineHeight = 15.sp
        )
        if (active.isEmpty()) {
            Text(text = "Nothing yet. Chat with your AI coach, or add something below.", color = SuperfitTheme.colors.textSecondary, fontSize = 12.sp)
        }
        active.forEach { note -> NoteRow(note = note, today = today, onRemove = { onRemove(note.id) }) }
        Text(text = "Add things yourself, one per line. You can paste a list.", color = SuperfitTheme.colors.textSecondary, fontSize = 12.sp)
        val speakNote = LocalVoiceTyping.current("Say something your coach should know") { said ->
            newNote = (if (newNote.isBlank()) said else newNote.trimEnd() + "\n" + said).take(CoachNotes.MAX_USER_PASTE)
        }
        // Tap an example to start from it; each adds one line. Hide ones already saved or typed.
        val known = (active.map { it.text } + newNote.lines()).map { it.trim().lowercase() }.toSet()
        val examples = NOTE_EXAMPLES.filter { it.lowercase() !in known }
        if (examples.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(examples.size) { i ->
                    val example = examples[i]
                    Text(
                        text = "+ $example",
                        color = SuperfitTheme.colors.textPrimary,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(SuperfitTheme.colors.fieldBg)
                            .clickable { newNote = if (newNote.isBlank()) example else newNote.trimEnd() + "\n" + example }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
        }
        // Mic and Add sit inside the field (like the coach chat) so the box gets the full width.
        SuperfitField(
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 6.dp)) {
                    speakNote?.let { MicButton(onClick = it) }
                    FieldActionButton(
                        icon = Icons.Default.Add,
                        contentDescription = "Add note",
                        enabled = newNote.isNotBlank(),
                        onClick = {
                            onAdd(newNote)
                            newNote = ""
                        }
                    )
                }
            },
            value = newNote,
            onValueChange = { newNote = it.take(CoachNotes.MAX_USER_PASTE) },
            placeholder = { Text("e.g. Vegetarian on Tuesdays", fontSize = 13.sp) },
            maxLines = 6,
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(fontSize = 13.sp, color = SuperfitTheme.colors.textPrimary)
        )
        Text(
            text = "Clear chat history",
            color = DeleteRed,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clickable { confirmClear = true }
                .padding(vertical = 6.dp)
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear chat history?") },
            text = { Text("Your conversations with the AI coach are deleted from this phone and your backup. What your AI coach knows (above) stays until you remove it.") },
            confirmButton = {
                TextButton(onClick = {
                    onClearChat()
                    confirmClear = false
                }) { Text("Clear", color = DeleteRed) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun NoteRow(note: CoachNote, today: LocalDate, onRemove: () -> Unit) {
    val added = note.added
    val source = when {
        note.byUser -> "Added by you, ${EnglishDates.dayMonth(added)}"
        added == today -> "From chat, today"
        added == today.minus(1, DateTimeUnit.DAY) -> "From chat, yesterday"
        else -> "From chat, ${EnglishDates.dayMonth(added)}"
    }
    val fades = note.expires?.let { " · fades ${EnglishDates.dayMonth(it)}" }.orEmpty()
    val kind = if (note.kind == NoteKind.PLAN) "Plan: " else ""
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = kind + note.text, color = SuperfitTheme.colors.textPrimary, fontSize = 13.sp)
            Text(text = source + fades, color = SuperfitTheme.colors.textSecondary, fontSize = 12.sp)
        }
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Remove ${note.text}",
            tint = SuperfitTheme.colors.textSecondary,
            modifier = Modifier
                .clickable(onClick = onRemove)
                .padding(8.dp)
        )
    }
}

private val NOTE_EXAMPLES = listOf(
    "Vegetarian on Tuesdays",
    "Night shifts Mon to Thu",
    "Bad knee, no running",
    "Training for a 10k"
)
