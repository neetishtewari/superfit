package com.superfit.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoachChatSizeTest {

    private fun msg(id: Long, text: String) = ChatEntry(id = id, fromUser = id % 2 == 1L, text = text, time = id)

    @Test
    fun keepsTheLatestMessagesUpToTheLimit() {
        val total = CoachChat.KEEP + 20L
        val saved = CoachChat.decode(CoachChat.encode((1L..total).map { msg(it, "message $it") }))
        assertEquals(CoachChat.KEEP, saved.size)
        assertEquals(21L, saved.first().id)
        assertEquals(total, saved.last().id)
    }

    @Test
    fun longChatsDropTheOldestMessagesToFitTheBackup() {
        // A full chat at the longest allowed length, in a three-byte script, would be over 2 MB.
        val long = "नमस्ते".repeat(CoachChat.MAX_TEXT / 6)
        val last = CoachChat.KEEP.toLong()
        val json = CoachChat.encode((1L..last).map { msg(it, long) })

        assertTrue(json.encodeToByteArray().size <= CoachChat.MAX_SAVED_BYTES)
        val saved = CoachChat.decode(json)
        assertTrue(saved.isNotEmpty())
        assertEquals(last, saved.last().id)
        assertEquals((saved.first().id..last).toList(), saved.map { it.id })
    }
}
