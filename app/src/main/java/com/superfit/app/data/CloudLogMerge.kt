package com.superfit.app.data

/**
 * Works out how to line up the phone's logged items (meals, workouts, weigh-ins) with the copies
 * in Firestore without ever losing either side.
 *
 * Every item is stored in the cloud under the time it was logged ([cloudKey]). Room's own ids
 * can't be used for this: they start again at 1 on every install, and a new item's id is still
 * 0 when it is uploaded, which made every meal overwrite the same cloud document.
 */
object CloudLogMerge {

    fun cloudKey(timestamp: Long): String = timestamp.toString()

    data class CloudDoc(val id: String, val timestamp: Long)

    data class Plan(
        /** Items that are only in the cloud and need copying onto the phone. */
        val restoreTimestamps: Set<Long>,
        /** Items that are only on the phone (or only under an old cloud name) and need uploading. */
        val uploadTimestamps: Set<Long>,
        /**
         * Cloud documents saved under an old name (the Room id). Each one is safe to remove once
         * a document under [cloudKey] exists for its timestamp.
         */
        val staleDocs: List<CloudDoc>
    )

    fun plan(localTimestamps: Collection<Long>, cloudDocs: List<CloudDoc>): Plan {
        val local = localTimestamps.toSet()
        val validCloud = cloudDocs.filter { it.timestamp > 0L }
        val cloudTimestamps = validCloud.map { it.timestamp }.toSet()
        val keyedTimestamps = validCloud.filter { it.id == cloudKey(it.timestamp) }.map { it.timestamp }.toSet()

        return Plan(
            restoreTimestamps = cloudTimestamps - local,
            uploadTimestamps = (local + cloudTimestamps) - keyedTimestamps,
            staleDocs = validCloud.filter { it.id != cloudKey(it.timestamp) }
        )
    }
}
