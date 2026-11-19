package com.superfit.app.ui.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.superfit.app.ui.settings.CoachMemoryCard

/**
 * "What my AI coach knows": every note the AI coach keeps about the user, from chats or typed here.
 * Each can be removed. Also clears the chat history. The card is shared with the iPhone.
 */
@Composable
fun CoachMemorySettings(viewModel: DashboardViewModel) {
    val notes by viewModel.coachNotes.collectAsState()
    CoachMemoryCard(
        notes = notes,
        onRemove = viewModel::deleteCoachNote,
        onAdd = viewModel::addCoachNote,
        onClearChat = viewModel::clearChatHistory
    )
}
