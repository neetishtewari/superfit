package com.superfit.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class IosCloudStoreAdapterTest {

    private class FakeIos(private val error: String? = null) : IosCloudStore {
        val docs = mutableMapOf<String, Map<String, Any>>()
        override fun get(path: String, done: (Map<String, Any>?, String?) -> Unit) = done(docs[path], error)
        override fun list(collectionPath: String, done: (List<CloudDoc>?, String?) -> Unit) =
            done(docs.filterKeys { it.startsWith("$collectionPath/") }.map { CloudDoc(it.key.substringAfterLast('/'), it.value) }, error)
        override fun set(path: String, data: Map<String, Any>, done: (String?) -> Unit) {
            if (error == null) docs[path] = data
            done(error)
        }
        override fun setAll(paths: List<String>, data: List<Map<String, Any>>, done: (String?) -> Unit) {
            if (error == null) paths.zip(data).forEach { docs[it.first] = it.second }
            done(error)
        }
        override fun delete(path: String, done: (String?) -> Unit) {
            docs.remove(path)
            done(error)
        }
        override fun waitForPendingWrites(done: (String?) -> Unit) = done(error)
    }

    @Test
    fun passesCallsThrough() = runTest {
        val ios = FakeIos()
        val store = IosCloudStoreAdapter(ios)
        store.set("users/u/nutrition/a", mapOf("calories" to 200L))
        store.setAll(listOf("users/u/nutrition/b" to mapOf("calories" to 300L)))
        assertEquals(200L, store.get("users/u/nutrition/a")?.get("calories"))
        assertEquals(setOf("a", "b"), store.list("users/u/nutrition").map { it.id }.toSet())
        store.delete("users/u/nutrition/a")
        assertNull(store.get("users/u/nutrition/a"))
    }

    @Test
    fun anErrorFromSwiftBecomesAnException() = runTest {
        val store = IosCloudStoreAdapter(FakeIos(error = "offline"))
        assertFailsWith<CloudStoreException> { store.set("users/u/x/y", mapOf("a" to 1L)) }
        assertFailsWith<CloudStoreException> { store.get("users/u/x/y") }
    }
}
