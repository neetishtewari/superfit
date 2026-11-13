package com.superfit.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoachChatSizeTest {

    private fun msg(id: Long, text: String) = ChatEntry(id = id, fromUser = id % 2 == 1L, text = text, time = id)

    @Test
    fun keepsTheLatestFiveHundredMessages() {
        val saved = CoachChat.decode(CoachChat.encode((1L..520L).map { msg(it, "message $it") }))
        assertEquals(CoachChat.KEEP, saved.size)
        assertEquals(21L, saved.first().id)
        assertEquals(520L, saved.last().id)
    }

    @Test
    fun longChatsDropTheOldestMessagesToFitTheBackup() {
        // 500 messages at the longest allowed length, in a three-byte script, would be about 6 MB.
        val long = "नमस्ते".repeat(CoachChat.MAX_TEXT / 6)
        val json = CoachChat.encode((1L..500L).map { msg(it, long) })

        assertTrue(json.encodeToByteArray().size <= CoachChat.MAX_SAVED_BYTES)
        val saved = CoachChat.decode(json)
        assertTrue(saved.isNotEmpty())
        assertEquals(500L, saved.last().id)
        assertEquals((saved.first().id..500L).toList(), saved.map { it.id })
    }
}
