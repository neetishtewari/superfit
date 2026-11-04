package com.superfit.app.data

/**
 * Picks which cloud entries a restore should add to the phone.
 *
 * Entries backed up by older versions all share the cloud id "0" (see
 * [FirebaseSyncManager.repairEntryBackupsOnce]). Such an entry is kept only when no entry with the
 * same timestamp is already on the phone or in the cloud under a real id, so a restore never shows
 * the same meal twice. Entries with a real cloud id are always kept; the insert itself skips cloud
 * ids the phone already has.
 */
internal fun <T> withoutLegacyDuplicates(
    cloudEntries: List<T>,
    localTimestamps: Set<Long>,
    cloudId: (T) -> String,
    timestamp: (T) -> Long
): List<T> {
    val (legacy, current) = cloudEntries.partition { cloudId(it) == CloudIds.LEGACY_UNNUMBERED }
    val knownTimestamps = localTimestamps + current.map(timestamp)
    return current + legacy.filter { timestamp(it) !in knownTimestamps }.distinctBy(timestamp)
}
