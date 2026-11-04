package com.superfit.app.data

import android.content.Context
import android.util.Log
import com.superfit.app.domain.CardLines
import com.superfit.app.domain.CardStat
import com.superfit.app.domain.CoachStyle
import com.superfit.app.domain.GeminiClient
import com.superfit.app.domain.RoastCard
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException

/**
 * The AI calls behind a "Roast my day" card: a short version of a long roast when it goes on a
 * photo (written once and kept), and a fresh roast from "Try another", a few times a day.
 */
object RoastCardData {
    private const val TAG = "RoastCardData"

    /** The short version of [roast] for a photo card, or null when the AI can't write one that passes the checks. */
    suspend fun short(context: Context, roast: String, pool: List<CardStat>): String? {
        val prefs = ShareCardPrefs.of(context.applicationContext)
        val key = "short|" + roast.hashCode()
        prefs.savedLine(key)?.let { return it }
        val facts = RoastCard.facts(pool)
        val line = write(RoastCard.SHORT_SYSTEM, RoastCard.shortPrompt(roast, facts), "$roast\n$facts", RoastCard.SHORT_MAX_WORDS)
        line?.let { prefs.saveLine(key, it) }
        return line
    }

    /** How many fresh roasts are left today. */
    fun againLeft(context: Context): Int =
        (RoastCard.AGAIN_PER_DAY - ShareCardPrefs.of(context.applicationContext).roastsAgain(LocalDate.now().toString())).coerceAtLeast(0)

    /** A different roast of the same day, or null when none are left today or the AI can't be reached. */
    suspend fun again(context: Context, roast: String, pool: List<CardStat>): String? {
        if (againLeft(context) <= 0) return null
        val facts = RoastCard.facts(pool)
        val system = RoastCard.AGAIN_SYSTEM.trimIndent() + CoachStyle.ROAST.instruction()
        val line = write(system, RoastCard.againPrompt(roast, facts), "$roast\n$facts", CardLines.ROAST_MAX_WORDS)
        if (line != null) ShareCardPrefs.of(context.applicationContext).countRoastAgain(LocalDate.now().toString())
        return line
    }

    private suspend fun write(system: String, prompt: String, allowed: String, maxWords: Int): String? {
        repeat(2) {
            val raw = try {
                GeminiClient.generateContent(prompt = prompt, systemInstructionText = system.trimIndent(), isJson = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Writing the roast failed", e)
                return null
            }
            CardLines.accept(raw, allowed, maxWords)?.let { return it }
        }
        return null
    }
}
