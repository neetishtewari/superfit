package com.superfit.app.data

/**
 * Picks which cloud entries a restore should add to the phone.
 *
 * Entries backed up by older versions all share id 0 (see
 * [FirebaseSyncManager.repairEntryBackupsOnce]). Such an entry is kept only when no entry with the
 * same timestamp is already on the phone or in the cloud under a real id, so a restore never shows
 * the same meal twice. Entries with a real id are always kept; the insert itself skips ids the
 * phone already has.
 */
internal fun <T> withoutLegacyDuplicates(
    cloudEntries: List<T>,
    localTimestamps: Set<Long>,
    id: (T) -> Long,
    timestamp: (T) -> Long
): List<T> {
    val (legacy, current) = cloudEntries.partition { id(it) == 0L }
    val knownTimestamps = localTimestamps + current.map(timestamp)
    return current + legacy.filter { timestamp(it) !in knownTimestamps }.distinctBy(timestamp)
}
