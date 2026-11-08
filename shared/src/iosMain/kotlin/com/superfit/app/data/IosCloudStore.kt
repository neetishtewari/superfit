package com.superfit.app.data

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * The cloud database as the iPhone's Swift code provides it (Firestore's iPhone SDK). Swift can't
 * implement Kotlin suspend functions, so each call reports back through [done]: an error message,
 * or null when it worked.
 *
 * Numbers: Swift hands back whole numbers as Kotlin Long, decimals as Double and true/false as
 * Boolean, and turns Kotlin numbers and Booleans into real iPhone numbers and Booleans before
 * writing, so Android reads exactly what it would have written itself.
 */
interface IosCloudStore {
    fun get(path: String, done: (Map<String, Any>?, String?) -> Unit)
    fun list(collectionPath: String, done: (List<CloudDoc>?, String?) -> Unit)
    fun set(path: String, data: Map<String, Any>, done: (String?) -> Unit)
    fun setAll(paths: List<String>, data: List<Map<String, Any>>, done: (String?) -> Unit)
    fun delete(path: String, done: (String?) -> Unit)
    fun waitForPendingWrites(done: (String?) -> Unit)
}

/** A cloud call that failed on the iPhone, with Firestore's message. */
class CloudStoreException(message: String) : Exception(message)

/** Lets shared code use the Swift [IosCloudStore] as an ordinary [CloudStore]. */
class IosCloudStoreAdapter(private val ios: IosCloudStore) : CloudStore {
    override suspend fun get(path: String): Map<String, Any>? = suspendCancellableCoroutine { c ->
        ios.get(path) { data, error -> if (error != null) c.resumeWithException(CloudStoreException(error)) else c.resume(data) }
    }

    override suspend fun list(collectionPath: String): List<CloudDoc> = suspendCancellableCoroutine { c ->
        ios.list(collectionPath) { docs, error ->
            if (error != null) c.resumeWithException(CloudStoreException(error)) else c.resume(docs.orEmpty())
        }
    }

    override suspend fun set(path: String, data: Map<String, Any>) = done { ios.set(path, data, it) }

    override suspend fun setAll(writes: List<Pair<String, Map<String, Any>>>) =
        done { ios.setAll(writes.map { w -> w.first }, writes.map { w -> w.second }, it) }

    override suspend fun delete(path: String) = done { ios.delete(path, it) }

    override suspend fun waitForPendingWrites() = done { ios.waitForPendingWrites(it) }

    private suspend fun done(call: ((String?) -> Unit) -> Unit) = suspendCancellableCoroutine<Unit> { c ->
        call { error -> if (error != null) c.resumeWithException(CloudStoreException(error)) else c.resume(Unit) }
    }
}
