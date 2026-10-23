package com.superfit.app.data

import android.content.Context
import android.content.SharedPreferences
import com.superfit.app.domain.Baseline
import com.superfit.app.domain.BodyReadings
import com.superfit.app.domain.MeasuredBody
import com.superfit.app.domain.ReadingMatch
import com.superfit.app.domain.ScaleReading
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/**
 * Brings smart scale readings in from Health Connect. Readings that look like the user's go into the
 * weight log (and body fat into the calorie target); readings that don't are held for an
 * "Is this you?" question on Home. Everything is kept in the user's own preferences on the phone.
 */
object BodyImporter {

    private const val KEY_LAST = "scale_last_import_ms"
    private const val KEY_MINE = "scale_mine"
    private const val KEY_NOT_ME = "scale_not_me"
    private const val KEY_PENDING = "scale_pending"
    private const val KEY_FAT = "scale_body_fat"
    private const val KEY_FAT_DATE = "scale_body_fat_date"
    private const val KEY_BODY_ASKED = "scale_body_permission_seen"

    /** How far back the first import looks. */
    private val FIRST_LOOK_BACK: Duration = Duration.ofDays(60)
    private const val KEEP = 400

    private const val NOTE_PREFIX = "from "

    private val lock = Mutex()

    /** Weight log entries added from a scale app carry a "from <app>" note. */
    fun isImported(entry: WeightEntryEntity): Boolean = entry.note.startsWith(NOTE_PREFIX)

    /** Loads the saved body fat into [MeasuredBody] so calorie targets use it. Call when the signed-in user may have changed. */
    fun load(context: Context) {
        val prefs = getUserSharedPrefs(context)
        MeasuredBody.bodyFatPct = if (prefs.contains(KEY_FAT)) prefs.getFloat(KEY_FAT, 0f).toDouble() else null
        MeasuredBody.measuredOn = prefs.getString(KEY_FAT_DATE, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    }

    suspend fun importNew(context: Context, repository: DataRepository) = lock.withLock {
        val hc = repository.healthConnectManager
        if (!hc.isAvailable()) return@withLock
        val prefs = getUserSharedPrefs(context)
        val now = Instant.now()
        val since = prefs.getLong(KEY_LAST, 0L).takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).minus(Duration.ofHours(1)) }
            ?: now.minus(FIRST_LOOK_BACK)

        val weights = hc.readWeighIns(since)
        if (weights.isEmpty()) return@withLock
        val withBody = hc.hasBodyPermissions()
        val readings = BodyReadings.group(
            weights,
            if (withBody) hc.readBodyFat(since) else emptyList(),
            if (withBody) hc.readLeanMass(since) else emptyList(),
            if (withBody) hc.readBoneMass(since) else emptyList(),
            if (withBody) hc.readScaleBmr(since) else emptyList()
        ).map { it.copy(source = SourceAppNames.label(context, it.source)) }

        val mine = load(prefs, KEY_MINE).associateBy { it.id }.toMutableMap()
        val notMe = load(prefs, KEY_NOT_ME).associateBy { it.id }.toMutableMap()
        val pending = load(prefs, KEY_PENDING).associateBy { it.id }.toMutableMap()

        for (r in readings) {
            when (r.id) {
                // Already decided: keep any body values that arrived since.
                in mine -> {
                    mine[r.id] = r
                    rememberBodyFat(context, r)
                }
                in notMe -> notMe[r.id] = r
                in pending -> pending[r.id] = r
                else -> when (BodyReadings.decide(r, baseline(repository, mine.values), notMe.values.toList())) {
                    ReadingMatch.MINE -> {
                        mine[r.id] = r
                        addToLog(context, repository, r)
                    }
                    ReadingMatch.NOT_ME -> notMe[r.id] = r
                    ReadingMatch.ASK -> pending[r.id] = r
                }
            }
        }
        save(prefs, mine.values, notMe.values, pending.values)
        prefs.edit().putLong(KEY_LAST, weights.maxOf { it.time }.toEpochMilli()).apply()
    }

    /** The oldest reading still waiting for "Is this you?", or null. */
    fun nextPending(context: Context): ScaleReading? = load(getUserSharedPrefs(context), KEY_PENDING).minByOrNull { it.time }

    /**
     * The user's answer to "Is this you?". Other waiting readings are then looked at again, so one
     * answer usually settles a whole batch (a partner's readings stop being asked about).
     */
    suspend fun answer(context: Context, repository: DataRepository, id: String, isMe: Boolean) = lock.withLock {
        val prefs = getUserSharedPrefs(context)
        val mine = load(prefs, KEY_MINE).associateBy { it.id }.toMutableMap()
        val notMe = load(prefs, KEY_NOT_ME).associateBy { it.id }.toMutableMap()
        val pending = load(prefs, KEY_PENDING).associateBy { it.id }.toMutableMap()
        val r = pending.remove(id) ?: return@withLock
        if (isMe) {
            mine[id] = r
            addToLog(context, repository, r)
        } else {
            notMe[id] = r
        }
        for (other in pending.values.sortedBy { it.time }.toList()) {
            when (BodyReadings.decide(other, baseline(repository, mine.values), notMe.values.toList())) {
                ReadingMatch.MINE -> {
                    pending.remove(other.id)
                    mine[other.id] = other
                    addToLog(context, repository, other)
                }
                ReadingMatch.NOT_ME -> {
                    pending.remove(other.id)
                    notMe[other.id] = other
                }
                ReadingMatch.ASK -> Unit
            }
        }
        save(prefs, mine.values, notMe.values, pending.values)
    }

    /** The user's own scale readings, oldest first, for the Body card and the coach. */
    fun mine(context: Context): List<ScaleReading> = load(getUserSharedPrefs(context), KEY_MINE).sortedBy { it.time }

    /** Whether the Body card has offered the body measurements permission; it offers it once, quietly. */
    fun bodyPermissionOffered(context: Context): Boolean = getUserSharedPrefs(context).getBoolean(KEY_BODY_ASKED, false)

    fun markBodyPermissionOffered(context: Context) {
        getUserSharedPrefs(context).edit().putBoolean(KEY_BODY_ASKED, true).apply()
    }

    /** After body measurements are allowed, look again at recent readings so their body fat comes in. */
    fun rescanRecent(context: Context) {
        val prefs = getUserSharedPrefs(context)
        val back = Instant.now().minus(FIRST_LOOK_BACK).toEpochMilli()
        if (prefs.getLong(KEY_LAST, 0L) > back) prefs.edit().putLong(KEY_LAST, back).apply()
    }

    private suspend fun baseline(repository: DataRepository, mine: Collection<ScaleReading>): Baseline {
        val lastMine = mine.maxByOrNull { it.time }
        val logged = repository.getAllWeightEntries().maxByOrNull { it.timestamp }
        val weight = when {
            lastMine != null && (logged == null || lastMine.time.toEpochMilli() >= logged.timestamp) -> lastMine.weightKg
            logged != null -> logged.weightKg
            else -> repository.getProfile()?.weightKg
        }
        val fat = mine.filter { it.bodyFatPct != null }.maxByOrNull { it.time }?.bodyFatPct
        return Baseline(weight, fat)
    }

    private suspend fun addToLog(context: Context, repository: DataRepository, r: ScaleReading) {
        repository.addImportedWeight(r.weightKg, r.time.toEpochMilli(), NOTE_PREFIX + r.source)
        rememberBodyFat(context, r)
    }

    /** Keeps the newest body fat reading for the calorie target. */
    private fun rememberBodyFat(context: Context, r: ScaleReading) {
        val fat = r.bodyFatPct ?: return
        val prefs = getUserSharedPrefs(context)
        val day = r.time.atZone(ZoneId.systemDefault()).toLocalDate()
        val savedDay = prefs.getString(KEY_FAT_DATE, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (savedDay == null || !day.isBefore(savedDay)) {
            prefs.edit().putFloat(KEY_FAT, fat.toFloat()).putString(KEY_FAT_DATE, day.toString()).apply()
            MeasuredBody.bodyFatPct = fat
            MeasuredBody.measuredOn = day
        }
    }

    private fun save(prefs: SharedPreferences, mine: Collection<ScaleReading>, notMe: Collection<ScaleReading>, pending: Collection<ScaleReading>) {
        fun json(list: Collection<ScaleReading>) = JSONArray().apply {
            list.sortedByDescending { it.time }.take(KEEP).forEach { put(toJson(it)) }
        }.toString()
        prefs.edit()
            .putString(KEY_MINE, json(mine))
            .putString(KEY_NOT_ME, json(notMe))
            .putString(KEY_PENDING, json(pending))
            .apply()
    }

    private fun load(prefs: SharedPreferences, key: String): List<ScaleReading> {
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
        put("t", r.time.toEpochMilli())
        put("w", r.weightKg)
        put("s", r.source)
        r.bodyFatPct?.let { put("f", it) }
        r.leanKg?.let { put("l", it) }
        r.boneKg?.let { put("b", it) }
        r.bmrKcal?.let { put("m", it) }
    }

    private fun fromJson(o: JSONObject) = ScaleReading(
        id = o.getString("id"),
        time = Instant.ofEpochMilli(o.getLong("t")),
        weightKg = o.getDouble("w"),
        source = o.optString("s"),
        bodyFatPct = o.optDouble("f").takeIf { o.has("f") },
        leanKg = o.optDouble("l").takeIf { o.has("l") },
        boneKg = o.optDouble("b").takeIf { o.has("b") },
        bmrKcal = o.optDouble("m").takeIf { o.has("m") }
    )
}
