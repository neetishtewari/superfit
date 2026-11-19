package com.superfit.app.data

import com.superfit.app.domain.AppLog
import com.superfit.app.domain.Baseline
import com.superfit.app.domain.BodyReadings
import com.superfit.app.domain.BodyValue
import com.superfit.app.domain.MeasuredBody
import com.superfit.app.domain.ReadingMatch
import com.superfit.app.domain.ScaleReading
import com.superfit.app.json.JSONArray
import com.superfit.app.json.JSONObject
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Weigh-ins and body measurements from the phone's health app, each saved with the app's name as [ScaleReading.source]. */
data class BodySamples(val weights: List<ScaleReading>, val bodyFat: List<BodyValue>, val lean: List<BodyValue>)

/**
 * Smart-scale readings from the phone's health app (Apple Health on iPhone), the way Android's
 * BodyImporter brings them in from Health Connect: readings that look like the person's go into the
 * weight log (and body fat into the calorie target); readings that don't wait for an "Is this you?"
 * question on Today. The lists live in the account's own settings on the phone.
 */
@OptIn(ExperimentalTime::class)
class ScaleImport(
    private val prefs: Prefs,
    private val weighIns: suspend () -> List<WeightEntryEntity>,
    private val profileWeightKg: suspend () -> Double?,
    private val addWeight: suspend (weightKg: Double, timeMs: Long, note: String) -> Unit,
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() }
) {
    /** Reads and writes the account's own weight log. */
    constructor(prefs: Prefs, log: LogRepository) : this(
        prefs = prefs,
        weighIns = log::getAllWeightEntries,
        profileWeightKg = { log.getProfile()?.weightKg },
        addWeight = log::addImportedWeight
    )

    /** Puts the saved body fat into [MeasuredBody] so calorie targets use it. Call at sign-in. */
    fun load() {
        MeasuredBody.bodyFatPct = prefs.getString(KEY_FAT, null)?.toDoubleOrNull()
        MeasuredBody.measuredOn = prefs.getString(KEY_FAT_DATE, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    }

    suspend fun importNew(source: HealthSource, nowMs: Long) = lock.withLock {
        val since = prefs.getLong(KEY_LAST, 0L).takeIf { it > 0 }?.minus(HOUR_MS) ?: (nowMs - FIRST_LOOK_BACK_MS)
        val samples = try {
            source.readBody(since)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w("ScaleImport", "Reading weigh-ins failed", e)
            null
        } ?: return@withLock
        if (samples.weights.isEmpty()) return@withLock
        val readings = BodyReadings.group(samples.weights, samples.bodyFat, samples.lean, emptyList(), emptyList())
        val mine = load(KEY_MINE).associateBy { it.id }.toMutableMap()
        val notMe = load(KEY_NOT_ME).associateBy { it.id }.toMutableMap()
        val pending = load(KEY_PENDING).associateBy { it.id }.toMutableMap()
        for (r in readings) {
            when (r.id) {
                // Already decided: keep any body values that arrived since.
                in mine -> {
                    mine[r.id] = r
                    rememberBodyFat(r)
                }
                in notMe -> notMe[r.id] = r
                in pending -> pending[r.id] = r
                else -> when (BodyReadings.decide(r, baseline(mine.values), notMe.values.toList())) {
                    ReadingMatch.MINE -> {
                        mine[r.id] = r
                        addToLog(r)
                    }
                    ReadingMatch.NOT_ME -> notMe[r.id] = r
                    ReadingMatch.ASK -> pending[r.id] = r
                }
            }
        }
        save(mine.values, notMe.values, pending.values)
        prefs.edit { putLong(KEY_LAST, samples.weights.maxOf { it.timeMs }) }
    }

    /** The oldest reading still waiting for "Is this you?", or null. */
    fun nextPending(): ScaleReading? = load(KEY_PENDING).minByOrNull { it.timeMs }

    /**
     * The person's answer to "Is this you?". Other waiting readings are then looked at again, so one
     * answer usually settles a whole batch (a partner's readings stop being asked about).
     */
    suspend fun answer(id: String, isMe: Boolean) = lock.withLock {
        val mine = load(KEY_MINE).associateBy { it.id }.toMutableMap()
        val notMe = load(KEY_NOT_ME).associateBy { it.id }.toMutableMap()
        val pending = load(KEY_PENDING).associateBy { it.id }.toMutableMap()
        val r = pending.remove(id) ?: return@withLock
        if (isMe) {
            mine[id] = r
            addToLog(r)
        } else {
            notMe[id] = r
        }
        for (other in pending.values.sortedBy { it.timeMs }.toList()) {
            when (BodyReadings.decide(other, baseline(mine.values), notMe.values.toList())) {
                ReadingMatch.MINE -> {
                    pending.remove(other.id)
                    mine[other.id] = other
                    addToLog(other)
                }
                ReadingMatch.NOT_ME -> {
                    pending.remove(other.id)
                    notMe[other.id] = other
                }
                ReadingMatch.ASK -> Unit
            }
        }
        save(mine.values, notMe.values, pending.values)
    }

    /** The person's own scale readings, oldest first, for the Body card. */
    fun mine(): List<ScaleReading> = load(KEY_MINE).sortedBy { it.timeMs }

    private suspend fun baseline(mine: Collection<ScaleReading>): Baseline {
        val lastMine = mine.maxByOrNull { it.timeMs }
        val logged = weighIns().maxByOrNull { it.timestamp }
        val weight = when {
            lastMine != null && (logged == null || lastMine.timeMs >= logged.timestamp) -> lastMine.weightKg
            logged != null -> logged.weightKg
            else -> profileWeightKg()
        }
        val fat = mine.filter { it.bodyFatPct != null }.maxByOrNull { it.timeMs }?.bodyFatPct
        return Baseline(weight, fat)
    }

    private suspend fun addToLog(r: ScaleReading) {
        addWeight(r.weightKg, r.timeMs, NOTE_PREFIX + r.source)
        rememberBodyFat(r)
    }

    /** Keeps the newest body fat reading for the calorie target. */
    private fun rememberBodyFat(r: ScaleReading) {
        val fat = r.bodyFatPct ?: return
        val day = Instant.fromEpochMilliseconds(r.timeMs).toLocalDateTime(zone()).date
        val savedDay = prefs.getString(KEY_FAT_DATE, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (savedDay == null || day >= savedDay) {
            prefs.edit {
                putString(KEY_FAT, fat.toString())
                putString(KEY_FAT_DATE, day.toString())
            }
            MeasuredBody.bodyFatPct = fat
            MeasuredBody.measuredOn = day
        }
    }

    private fun save(mine: Collection<ScaleReading>, notMe: Collection<ScaleReading>, pending: Collection<ScaleReading>) {
        fun json(list: Collection<ScaleReading>) = JSONArray().apply {
            list.sortedByDescending { it.timeMs }.take(KEEP).forEach { put(toJson(it)) }
        }.toString()
        prefs.edit {
            putString(KEY_MINE, json(mine))
            putString(KEY_NOT_ME, json(notMe))
            putString(KEY_PENDING, json(pending))
        }
    }

    private fun load(key: String): List<ScaleReading> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { fromJson(array.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun toJson(r: ScaleReading) = JSONObject().apply {
        put("id", r.id)
        put("t", r.timeMs)
        put("w", r.weightKg)
        put("s", r.source)
        r.bodyFatPct?.let { put("f", it) }
        r.leanKg?.let { put("l", it) }
    }

    private fun fromJson(o: JSONObject) = ScaleReading(
        id = o.getString("id"),
        timeMs = o.getLong("t"),
        weightKg = o.getDouble("w"),
        source = o.optString("s"),
        bodyFatPct = if (o.has("f")) o.getDouble("f") else null,
        leanKg = if (o.has("l")) o.getDouble("l") else null
    )

    companion object {
        private const val KEY_LAST = "scale_last_import_ms"
        private const val KEY_MINE = "scale_mine"
        private const val KEY_NOT_ME = "scale_not_me"
        private const val KEY_PENDING = "scale_pending"
        private const val KEY_FAT = "scale_body_fat_text"
        private const val KEY_FAT_DATE = "scale_body_fat_date"
        private const val HOUR_MS = 3_600_000L
        private const val FIRST_LOOK_BACK_MS = 60 * 24 * HOUR_MS
        private const val KEEP = 400
        private const val NOTE_PREFIX = "from "

        // Opening the app and coming back to it must not import over each other.
        private val lock = Mutex()

        /** Weight log entries added from a scale app carry a "from <app>" note, as on Android. */
        fun isImported(entry: WeightEntryEntity): Boolean = entry.note.startsWith(NOTE_PREFIX)
    }
}
