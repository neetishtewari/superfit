package com.superfit.app.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers what the cloud backup relies on: a new entry gets a real id to be backed up under,
 * and a restore never overwrites or removes entries already on the phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class EntryDaoTest {

    private lateinit var db: SuperfitDatabase

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, SuperfitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun meal(id: Long, text: String, timestamp: Long) = NutritionEntryEntity(
        id = id, foodText = text, calories = 100.0, proteinG = 1.0, carbsG = 1.0, fatG = 1.0,
        timestamp = timestamp
    )

    @Test
    fun newEntriesGetDistinctIds() = runBlocking {
        val first = db.nutritionDao().insertEntry(meal(0, "eggs", 1))
        val second = db.nutritionDao().insertEntry(meal(0, "rice", 2))
        assertNotEquals(0L, first)
        assertNotEquals(first, second)
    }

    @Test
    fun restoreKeepsLocalEntries() = runBlocking {
        val localId = db.nutritionDao().insertEntry(meal(0, "eggs", 1))
        db.nutritionDao().insertEntriesIfMissing(
            listOf(meal(localId, "cloud version", 1), meal(0, "whey", 5))
        )
        val all = db.nutritionDao().getAllEntries().sortedBy { it.timestamp }
        assertEquals(listOf("eggs", "whey"), all.map { it.foodText })
    }

    @Test
    fun editingAMealReplacesItsTotalsAndItems() = runBlocking {
        val id = db.nutritionDao().insertEntry(meal(0, "2 roti", 1))
        val edited = meal(id, "3 roti", 1).copy(calories = 360.0, items = "{\"v\":1}")
        db.nutritionDao().updateEntry(edited)
        val all = db.nutritionDao().getAllEntries()
        assertEquals(1, all.size)
        assertEquals(edited, all.single())
    }
}
