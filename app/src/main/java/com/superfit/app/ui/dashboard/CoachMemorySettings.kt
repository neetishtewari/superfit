package com.superfit.app.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superfit.app.domain.CoachNote
import com.superfit.app.domain.NoteKind
import com.superfit.app.theme.DeleteRed
import com.superfit.app.theme.NeonMint
import com.superfit.app.theme.SuperfitTheme
import com.superfit.app.ui.components.SuperfitField
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * "What my AI coach knows": every note the AI coach keeps about the user, from chats or typed here.
 * Each can be removed. Also clears the chat history.
 */
@Composable
fun CoachMemorySettings(viewModel: DashboardViewModel) {
    val notes by viewModel.coachNotes.collectAsState()
    val today = LocalDate.now()
    val active = notes.filter { it.isActive(today) }
    var newNote by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "✨ What my AI coach knows",
            fontWeight = FontWeight.Bold,
            color = SuperfitTheme.colors.textPrimary,
            fontSize = 14.sp
        )
        Text(
            text = "Notes Superfit's AI coach picked up from your chats, like an injury or a food you don't eat. It uses them for its answers and your daily tip. Remove anything that's wrong. " +
                "This is separate from your nutritionist's advice, which comes from a real person and always comes first.",
            color = SuperfitTheme.colors.textSecondary,
            fontSize = 12.sp,
            lineHeight = 15.sp
        )
        if (active.isEmpty()) {
            Text(
                text = "Nothing yet. Chat with your AI coach, or add something below.",
                color = SuperfitTheme.colors.textSecondary,
                fontSize = 12.sp
            )
        }
        active.forEach { note ->
            NoteRow(note = note, today = today, onRemove = { viewModel.deleteCoachNote(note.id) })
        }
        Text(
            text = "Add things yourself, one per line. You can paste a list.",
            color = SuperfitTheme.colors.textSecondary,
            fontSize = 12.sp
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SuperfitField(
                value = newNote,
                onValueChange = { newNote = it.take(com.superfit.app.domain.CoachNotes.MAX_USER_PASTE) },
                placeholder = { Text("e.g. Vegetarian on Tuesdays\nNight shifts Mon to Thu", fontSize = 12.sp) },
                maxLines = 6,
                modifier = Modifier.weight(1f),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = SuperfitTheme.colors.textPrimary)
            )
            Button(
                enabled = newNote.isNotBlank(),
                onClick = {
                    viewModel.addCoachNote(newNote)
                    newNote = ""
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonMint)
            ) {
                Text("Add", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
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
                    viewModel.clearChatHistory()
                    confirmClear = false
                }) { Text("Clear", color = DeleteRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun NoteRow(note: CoachNote, today: LocalDate, onRemove: () -> Unit) {
    val dayFormat = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    val source = when {
        note.byUser -> "Added by you, ${note.added.format(dayFormat)}"
        note.added == today -> "From chat, today"
        note.added == today.minusDays(1) -> "From chat, yesterday"
        else -> "From chat, ${note.added.format(dayFormat)}"
    }
    val fades = note.expires?.let { " · fades ${it.format(dayFormat)}" }.orEmpty()
    val kind = if (note.kind == NoteKind.PLAN) "Plan: " else ""
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
