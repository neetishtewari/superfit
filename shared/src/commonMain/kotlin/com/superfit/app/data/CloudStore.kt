package com.superfit.app.data

/** One document read back from the cloud: its id (last part of its path) and its fields. */
data class CloudDoc(val id: String, val data: Map<String, Any>)

/**
 * The cloud database as shared code sees it: documents addressed by slash paths such as
 * "users/{uid}/nutrition/{cloudId}". Android implements it with Firestore's Android SDK, the
 * iPhone with Firestore's iPhone SDK. Paths and fields are the same on both, so either app can
 * restore what the other backed up.
 */
interface CloudStore {
    /** The document's fields, or null if it doesn't exist. */
    suspend fun get(path: String): Map<String, Any>?

    /** Every document directly in the collection at [collectionPath]. */
    suspend fun list(collectionPath: String): List<CloudDoc>

    /** Writes the document, replacing what was there. */
    suspend fun set(path: String, data: Map<String, Any>)

    /** Writes up to 400 documents together: all or none. */
    suspend fun setAll(writes: List<Pair<String, Map<String, Any>>>)

    suspend fun delete(path: String)

    /** Waits until writes made offline have reached the server. */
    suspend fun waitForPendingWrites()
}
