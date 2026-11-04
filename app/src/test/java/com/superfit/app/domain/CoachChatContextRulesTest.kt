package com.superfit.app.domain

import org.junit.Assert.assertTrue
import org.junit.Test

class CoachChatContextRulesTest {

    @Test
    fun chatRulesKeepTheShortWordLimit() {
        assertTrue(CoachChatContext.RULES.contains("at most 16 words"))
    }
}
