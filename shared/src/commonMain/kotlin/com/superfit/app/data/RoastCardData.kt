package com.superfit.app.data

import com.superfit.app.domain.Ai
import com.superfit.app.domain.AppLog
import com.superfit.app.domain.CardLines
import com.superfit.app.domain.CardStat
import com.superfit.app.domain.CoachStyle
import com.superfit.app.domain.RoastCard
import kotlinx.coroutines.CancellationException

/**
 * The AI calls behind a "Roast my day" card: a short version of a long roast when it goes on a
 * photo (written once and kept), and a fresh roast from "Try another", a few times a day. The same
 * on Android and iPhone.
 */
object RoastCardData {
    private const val TAG = "RoastCardData"

    /** The short version of [roast] for a photo card, or null when the AI can't write one that passes the checks. */
    suspend fun short(prefs: ShareCardPrefs, roast: String, pool: List<CardStat>): String? {
        val key = "short|" + roast.hashCode()
        prefs.savedLine(key)?.let { return it }
        val facts = RoastCard.facts(pool)
        val line = write(RoastCard.SHORT_SYSTEM, RoastCard.shortPrompt(roast, facts), "$roast\n$facts", RoastCard.SHORT_MAX_WORDS)
        line?.let { prefs.saveLine(key, it) }
        return line
    }

    /**
     * Which of the day's numbers the roast is about, asked of the AI because a roast names real foods
     * ("three samosas") that no word list can catch. Kept per roast; null when the AI can't be reached.
     */
    suspend fun pick(prefs: ShareCardPrefs, roast: String, pool: List<CardStat>): List<String>? {
        if (pool.isEmpty()) return emptyList()
        val key = "pick|" + (roast + pool.joinToString { it.label }).hashCode()
        prefs.savedLine(key)?.let { saved -> return if (saved == "NONE") emptyList() else saved.split(',') }
        val raw = try {
            Ai.generate(prompt = RoastCard.pickPrompt(roast, pool), systemInstructionText = RoastCard.PICK_SYSTEM.trimIndent(), isJson = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w(TAG, "Picking the roast's numbers failed", e)
            return null
        }
        val picked = RoastCard.parsePick(raw, pool) ?: return null
        prefs.saveLine(key, picked.joinToString(",").ifEmpty { "NONE" })
        return picked
    }

    /** How many fresh roasts are left today. */
    fun againLeft(prefs: ShareCardPrefs): Int =
        (RoastCard.AGAIN_PER_DAY - prefs.roastsAgain(Now.today().toString())).coerceAtLeast(0)

    /** A different roast of the same day, or null when none are left today or the AI can't be reached. */
    suspend fun again(prefs: ShareCardPrefs, roast: String, pool: List<CardStat>): String? {
        if (againLeft(prefs) <= 0) return null
        val facts = RoastCard.facts(pool)
        val system = RoastCard.AGAIN_SYSTEM.trimIndent() + CoachStyle.ROAST.instruction()
        val line = write(system, RoastCard.againPrompt(roast, facts), "$roast\n$facts", CardLines.ROAST_MAX_WORDS)
        if (line != null) prefs.countRoastAgain(Now.today().toString())
        return line
    }

    private suspend fun write(system: String, prompt: String, allowed: String, maxWords: Int): String? {
        repeat(2) {
            val raw = try {
                Ai.generate(prompt = prompt, systemInstructionText = system.trimIndent(), isJson = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w(TAG, "Writing the roast failed", e)
                return null
            }
            CardLines.accept(raw, allowed, maxWords)?.let { return it }
        }
        return null
    }
}
