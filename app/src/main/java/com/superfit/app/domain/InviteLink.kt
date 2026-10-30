package com.superfit.app.domain

import java.net.URLDecoder
import java.net.URLEncoder
import kotlin.random.Random

/**
 * Invite links: the normal Play Store link plus a Play install referrer carrying the inviter's
 * invite code. Play hands that referrer to the new install, so the friend's app knows it came
 * from an invite and Firebase counts the install under "superfit_invite". The code is random and
 * says nothing about the person (no name, email or account id).
 */
object InviteLink {
    const val PLAY_URL = "https://play.google.com/store/apps/details?id=com.superfit.aifitness"
    const val SOURCE = "superfit_invite"
    const val CODE_LENGTH = 8

    // No 0/O, 1/I/L, so a code read aloud or retyped can't be mixed up.
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    fun newCode(random: Random = Random.Default): String {
        return (1..CODE_LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
    }

    fun isValidCode(code: String?): Boolean = code != null && code.length == CODE_LENGTH && code.all { it in ALPHABET }

    /** The Play Store link for [code], or the plain link when there's no valid code. */
    fun url(code: String?): String {
        if (!isValidCode(code)) return PLAY_URL
        val referrer = "utm_source=$SOURCE&utm_medium=share&utm_campaign=friend_invite&utm_content=$code"
        return "$PLAY_URL&referrer=${URLEncoder.encode(referrer, "UTF-8")}"
    }

    /** The invite code in a Play install referrer, or null when the install didn't come from an invite. */
    fun codeFromReferrer(referrer: String?): String? {
        if (referrer.isNullOrBlank()) return null
        val params = referrer.split('&').mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq <= 0) {
                null
            } else {
                part.substring(0, eq) to runCatching { URLDecoder.decode(part.substring(eq + 1), "UTF-8") }.getOrNull()
            }
        }.toMap()
        if (params["utm_source"] != SOURCE) return null
        return params["utm_content"]?.takeIf { isValidCode(it) }
    }

    /** The message sent with "Invite family and friends". */
    fun inviteMessage(code: String?): String {
        return "I'm using Superfit to keep up with food and workouts. You just say or snap what you ate. " +
            "Join me, it's more fun together: ${url(code)}"
    }

    /** The line sent with share cards. */
    fun shareCardText(code: String?): String = "Tracked with Superfit ${url(code)}"
}
