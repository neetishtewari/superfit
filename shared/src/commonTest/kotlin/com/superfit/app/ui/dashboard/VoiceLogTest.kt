package com.superfit.app.ui.dashboard

import com.superfit.app.domain.LogKind
import com.superfit.app.domain.SpokenInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VoiceLogTest {
    private class FakeRecognizer : VoiceRecognizer {
        var live: (String) -> Unit = {}
        var checking: () -> Unit = {}
        var heard: (List<List<String>>) -> Unit = {}
        var error: (String) -> Unit = {}
        var starts = 0
        var stops = 0
        var cancels = 0

        override fun isAvailable() = true
        override fun start(onLiveText: (String) -> Unit, onChecking: () -> Unit, onHeard: (List<List<String>>) -> Unit, onError: (String) -> Unit) {
            starts++
            live = onLiveText
            checking = onChecking
            heard = onHeard
            error = onError
        }
        override fun stop() {
            stops++
        }
        override fun cancel() {
            cancels++
        }
    }

    private val recognizer = FakeRecognizer()
    private val logged = mutableListOf<Pair<SpokenInput, LogKind>>()
    private val voice = VoiceLog(recognizer) { input, kind -> logged += input to kind }

    @Test
    fun loggedAsTheKindOfTheMicTapped_withThePhonesOtherGuesses() {
        voice.start(LogKind.WORKOUT)
        assertTrue(voice.ui.value.listening)
        assertEquals(LogKind.WORKOUT, voice.ui.value.kind)
        recognizer.live("20 push")
        assertEquals("20 push", voice.ui.value.liveText)
        recognizer.checking()
        assertTrue(voice.ui.value.checking)
        recognizer.heard(listOf(listOf("20 push-ups", "20 push ups and")))

        assertFalse(voice.ui.value.listening)
        assertEquals(listOf(SpokenInput("20 push-ups", listOf("20 push ups and")) to LogKind.WORKOUT), logged)
    }

    @Test
    fun nothingSaid_saysSoInsteadOfLogging() {
        voice.start(LogKind.MEAL)
        recognizer.heard(listOf(listOf("  ")))
        assertTrue(logged.isEmpty())
        assertEquals(VoiceLog.NOTHING_HEARD_LINE, voice.notice.value)

        voice.start(LogKind.MEAL)
        assertNull(voice.notice.value)
        recognizer.error(VoiceRecognizer.NOTHING_HEARD)
        assertEquals(VoiceLog.NOTHING_HEARD_LINE, voice.notice.value)
    }

    @Test
    fun refusedMic_offersSettings() {
        voice.start(LogKind.MEAL)
        recognizer.error(VoiceRecognizer.MIC_OFF)
        assertTrue(voice.micOff.value)
        assertFalse(voice.ui.value.listening)
        voice.dismissMicOff()
        assertFalse(voice.micOff.value)
    }

    @Test
    fun cancelled_aLateAnswerIsNotLogged() {
        voice.start(LogKind.MEAL)
        val lateAnswer = recognizer.heard
        voice.cancel()
        assertEquals(1, recognizer.cancels)
        lateAnswer(listOf(listOf("2 roti and dal")))
        assertTrue(logged.isEmpty())
        assertFalse(voice.ui.value.listening)
    }

    @Test
    fun oneMicAtATime_andStopOnlyWhileListening() {
        voice.stop()
        assertEquals(0, recognizer.stops)
        voice.start(LogKind.MEAL)
        voice.start(LogKind.WORKOUT)
        assertEquals(1, recognizer.starts)
        assertEquals(LogKind.MEAL, voice.ui.value.kind)
        voice.stop()
        assertEquals(1, recognizer.stops)
    }
}
