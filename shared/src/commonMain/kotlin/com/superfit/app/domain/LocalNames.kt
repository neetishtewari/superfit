package com.superfit.app.domain

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Month

/** "Monday" in the phone's language, as Java's TextStyle.FULL wrote it. */
expect fun localWeekdayName(day: DayOfWeek): String

/** "October" in the phone's language, the form used in a date ("5 October"). */
expect fun localMonthName(month: Month): String
