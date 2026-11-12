package com.superfit.app.domain

import com.superfit.app.data.UserProfileEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate

class BirthMonthTest {
    private val today = LocalDate(2026, 11, 12)

    @Test
    fun ageCountsTheBirthdayFromTheStartOfTheBirthMonth() {
        assertEquals(32, BirthMonth.ageOn(BirthMonth.encode(1994, 11), today))
        assertEquals(31, BirthMonth.ageOn(BirthMonth.encode(1994, 12), today))
        assertEquals(32, BirthMonth.ageOn(BirthMonth.encode(1994, 1), today))
    }

    @Test
    fun ageGoesUpOnItsOwnAYearLater() {
        val p = profile(age = 32, birth = BirthMonth.encode(1994, 3))
        assertEquals(32, p.currentAge(today))
        assertEquals(33, p.currentAge(LocalDate(2027, 3, 1)))
    }

    @Test
    fun typedAgeIsUsedUntilABirthMonthIsGiven() {
        assertEquals(41, profile(age = 41, birth = 0).currentAge(today))
    }

    @Test
    fun savedAgeFollowsTheBirthMonth() {
        assertEquals(32, profile(age = 20, birth = BirthMonth.encode(1994, 3)).withCurrentAge(today).age)
        assertEquals(20, profile(age = 20, birth = 0).withCurrentAge(today).age)
    }

    @Test
    fun problemsAreInPlainWords() {
        assertNull(BirthMonth.problem(1994, 3, today))
        assertEquals("Pick the month you were born", BirthMonth.problem(1994, null, today))
        assertEquals("Enter the year you were born", BirthMonth.problem(null, 3, today))
        assertEquals("Check the year you were born", BirthMonth.problem(2030, 3, today))
        assertEquals("Check the year you were born", BirthMonth.problem(1900, 3, today))
        assertEquals("Superfit is for adults (18 and over) for now", BirthMonth.problem(2010, 3, today))
        // 18 this month counts as 18.
        assertNull(BirthMonth.problem(2008, 11, today))
        assertEquals("Superfit is for adults (18 and over) for now", BirthMonth.problem(2008, 12, today))
    }

    @Test
    fun labelNamesTheMonth() {
        assertEquals("March 1994", BirthMonth.label(199403))
    }

    private fun profile(age: Int, birth: Int) =
        UserProfileEntity(age = age, heightCm = 175.0, weightKg = 80.0, isMale = true, birthYearMonth = birth)
}
