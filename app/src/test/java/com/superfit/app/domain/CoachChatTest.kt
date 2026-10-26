package com.superfit.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric so org.json works in encode/decode.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CoachChatTest {

    private val hour = 3600_000L
    private val now = LocalDate.of(2026, 10, 26).atTime(18, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun msg(id: Long, fromUser: Boolean, text: String, hoursAgo: Long, isError: Boolean = false) =
        ChatEntry(id = id, fromUser = fromUser, text = text, time = now - hoursAgo * hour, isError = isError)

    @Test
    fun savedChatComesBackTheSame() {
        val note = CoachNote("n1", "Knee hurts after running", NoteKind.LASTING, LocalDate.of(2026, 10, 25))
        val entries = listOf(
            msg(1, true, "My knee hurts", 5).copy(spoken = true),
            msg(2, false, "Skip the run", 5).copy(rememberedIds = listOf("n1"), forgotten = listOf(note), mealToLog = "2 eggs", mealLogged = true)
        )
        assertEquals(entries, CoachChat.decode(CoachChat.encode(entries)))
    }

    @Test
    fun errorsAreNeverSaved() {
        val entries = listOf(msg(1, true, "Hi", 1), msg(2, false, "Couldn't reach the AI", 1, isError = true))
        assertEquals(listOf(entries[0]), CoachChat.decode(CoachChat.encode(entries)))
    }

    @Test
    fun onlyTheNewestMessagesAreKept() {
        val entries = (1..(CoachChat.KEEP + 20L)).map { msg(it, it % 2 == 1L, "m$it", 0) }
        val saved = CoachChat.decode(CoachChat.encode(entries))
        assertEquals(CoachChat.KEEP, saved.size)
        assertEquals(21L, saved.first().id)
    }

    @Test
    fun mergeKeepsEachMessageOnceInOrder() {
        val a = msg(1, true, "a", 3)
        val b = msg(2, false, "b", 2)
        val c = msg(3, true, "c", 1)
        assertEquals(listOf(a, b, c), CoachChat.merge(listOf(a, c), listOf(b, a)))
    }

    @Test
    fun historyIsRecentStartsWithUserAndAlternates() {
        val entries = listOf(
            msg(1, true, "old question", 100),
            msg(2, false, "old answer", 100),
            msg(3, false, "greeting-like coach message", 10),
            msg(4, true, "first", 9),
            msg(5, true, "second, after a failed reply", 8),
            msg(6, false, "error", 8, isError = true),
            msg(7, false, "answer", 7),
            msg(8, true, "unanswered", 6)
        )
        val turns = CoachChat.historyForAi(entries, now)
        assertEquals(listOf(AiTurn(true, "first\nsecond, after a failed reply"), AiTurn(false, "answer")), turns)
    }

    @Test
    fun earlierTopicsAreTheUsersOlderQuestionsNewestFirst() {
        val entries = listOf(
            msg(1, true, "Is oats ok for breakfast?", 120),
            msg(2, false, "Yes", 120),
            msg(3, true, CoachChat.TELL_ME_MORE, 119),
            msg(4, true, "My knee hurts", 100),
            msg(5, true, "Recent question", 2)
        )
        val topics = CoachChat.earlierTopics(entries, now, ZoneOffset.UTC)
        assertEquals(2, topics.size)
        assertTrue(topics[0].endsWith("My knee hurts"))
        assertTrue(topics[1].endsWith("Is oats ok for breakfast?"))
    }

    @Test
    fun logLineBecomesTheLogThisMeal() {
        val reply = CoachChat.parseReply("Add paneer to dinner.\nLOG: 2 roti with paneer bhurji and 1 bowl curd")
        assertEquals("Add paneer to dinner.", reply.text)
        assertEquals("2 roti with paneer bhurji and 1 bowl curd", reply.mealToLog)

        val bold = CoachChat.parseReply("Try this.\n**LOG:** 1 bowl dal.")
        assertEquals("1 bowl dal", bold.mealToLog)

        val none = CoachChat.parseReply("Walk 30 minutes today.")
        assertEquals("Walk 30 minutes today.", none.text)
        assertNull(none.mealToLog)
    }

    @Test
    fun aReplyKnowsWhichQuestionItAnswers() {
        val entries = listOf(
            msg(1, true, "Roast my day", 2),
            msg(2, false, "Two samosas. Bold.", 2),
            msg(3, false, "Couldn't reach the AI", 1, isError = true)
        )
        assertEquals("Roast my day", CoachChat.questionFor(entries, 1))
        assertNull(CoachChat.questionFor(entries, 0))
        assertNull(CoachChat.questionFor(entries, 2))
        assertNull(CoachChat.questionFor(entries, 5))
    }

    @Test
    fun dayLabels() {
        val today = LocalDate.of(2026, 10, 26)
        assertEquals("Today", CoachChat.dayLabel(today, today))
        assertEquals("Yesterday", CoachChat.dayLabel(today.minusDays(1), today))
        assertEquals("Fri 23 Oct", CoachChat.dayLabel(today.minusDays(3), today))
    }

    @Test
    fun questionsFitTheMoment() {
        val evening = CoachQuestions.forNow(19, DayOfWeek.WEDNESDAY, 2, 40, workedOutToday = false, hasTodayTip = true)
        assertEquals(CoachQuestions.ABOUT_TODAYS_TIP, evening[0])
        assertEquals("What should I eat for dinner to hit my protein?", evening[1])
        assertEquals(3, evening.size)

        val morning = CoachQuestions.forNow(8, DayOfWeek.SUNDAY, 0, 120, workedOutToday = false, hasTodayTip = false)
        assertEquals(listOf("What's a good breakfast for my goal?", "How did my week go?", "Is my diet okay?"), morning)
    }
}
