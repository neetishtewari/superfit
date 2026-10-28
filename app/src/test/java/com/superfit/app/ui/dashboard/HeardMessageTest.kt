package com.superfit.app.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test

class HeardMessageTest {

    @Test
    fun `a voice log shows what was heard above the result`() {
        val state = ParsingState.Success("6 poori and chana").withHeard(" cheh poori aur chane ")
        assertEquals("Heard: “cheh poori aur chane”\nLogged: 6 poori and chana", (state as ParsingState.Success).message)
    }

    @Test
    fun `an existing message is kept under the heard line`() {
        val state = ParsingState.Success("poha", message = "Logged: poha · Workout: walk").withHeard("poha and a walk")
        assertEquals("Heard: “poha and a walk”\nLogged: poha · Workout: walk", (state as ParsingState.Success).message)
    }

    @Test
    fun `errors are left alone`() {
        val error = ParsingState.Error("Couldn't understand that.")
        assertEquals(error, error.withHeard("mumble"))
    }
}
