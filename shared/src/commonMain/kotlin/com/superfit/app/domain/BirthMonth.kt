package com.superfit.app.domain

import com.superfit.app.data.Now
import com.superfit.app.data.UserProfileEntity
import kotlinx.datetime.LocalDate

/**
 * Birth month and year, kept as one number (1994 March = 199403) so age stays right on its own.
 * Only the month, not the day: less personal, and close enough for calorie maths (a year of age
 * moves the target by about 5 kcal). 0 means not given (people who signed up before this typed
 * an age instead, which is used until they add their birth month).
 */
object BirthMonth {
    /** Superfit is for adults (see the family profiles decision); younger people are asked to wait. */
    const val MIN_AGE = 18
    const val MAX_AGE = 100

    val MONTH_NAMES = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    fun encode(year: Int, month: Int): Int = year * 100 + month

    fun year(yearMonth: Int): Int = yearMonth / 100

    fun month(yearMonth: Int): Int = yearMonth % 100

    /** Whole years on [today], counting the birthday as passed from the start of the birth month. */
    fun ageOn(yearMonth: Int, today: LocalDate): Int {
        val age = today.year - year(yearMonth)
        return if (today.month.ordinal + 1 < month(yearMonth)) age - 1 else age
    }

    /** "March 1994". */
    fun label(yearMonth: Int): String = "${MONTH_NAMES[month(yearMonth) - 1]} ${year(yearMonth)}"

    /** What's wrong with a typed birth month and year, in plain words, or null when it's fine. */
    fun problem(year: Int?, month: Int?, today: LocalDate): String? {
        if (month == null || month !in 1..12) return "Pick the month you were born"
        if (year == null) return "Enter the year you were born"
        val age = ageOn(encode(year, month), today)
        return when {
            age < 0 || age > MAX_AGE -> "Check the year you were born"
            age < MIN_AGE -> "Superfit is for adults (18 and over) for now"
            else -> null
        }
    }
}

/**
 * The age to use everywhere: from the birth month and year when the person gave one, otherwise
 * the age they typed when they signed up.
 */
fun UserProfileEntity.currentAge(today: LocalDate = Now.today()): Int =
    if (birthYearMonth > 0) BirthMonth.ageOn(birthYearMonth, today) else age

/**
 * The profile with its saved age brought up to date from the birth month, so older app versions
 * and the cloud backup (which read "age") see the right number too.
 */
fun UserProfileEntity.withCurrentAge(today: LocalDate = Now.today()): UserProfileEntity =
    if (birthYearMonth > 0) copy(age = currentAge(today)) else this
