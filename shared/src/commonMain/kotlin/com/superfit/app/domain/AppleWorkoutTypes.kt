package com.superfit.app.domain

import com.superfit.app.domain.ExerciseTypes as E

/**
 * Apple Health's workout kinds (HKWorkoutActivityType raw values) turned into the Health Connect
 * numbers the shared code already understands, so names, paces and bests work the same on both
 * phones. Kinds Health Connect has no match for become a plain "Workout".
 */
object AppleWorkoutTypes {
    const val OTHER = 0

    fun exerciseType(appleType: Long, indoor: Boolean): Int = when (appleType) {
        4L -> E.BADMINTON
        6L -> E.BASKETBALL
        8L, 65L -> E.BOXING // boxing, kickboxing
        9L -> E.ROCK_CLIMBING
        10L -> E.CRICKET
        13L, 74L -> if (indoor) E.BIKING_STATIONARY else E.BIKING // cycling, hand cycling
        14L, 15L, 77L, 78L -> E.DANCING
        16L -> E.ELLIPTICAL
        20L, 50L, 59L -> E.STRENGTH_TRAINING // functional, traditional, core
        21L -> E.GOLF
        22L -> E.GYMNASTICS
        24L -> E.HIKING
        28L -> E.MARTIAL_ARTS
        30L, 63L, 73L -> E.HIGH_INTENSITY_INTERVAL_TRAINING // mixed cardio and HIIT
        31L -> E.PADDLING
        35L -> if (indoor) E.ROWING_MACHINE else E.ROWING
        37L -> if (indoor) E.RUNNING_TREADMILL else E.RUNNING
        40L, 60L, 61L -> E.SKIING
        41L -> E.SOCCER
        43L -> E.SQUASH
        44L, 68L -> if (indoor) E.STAIR_CLIMBING_MACHINE else E.STAIR_CLIMBING
        45L -> E.SURFING
        46L -> if (indoor) E.SWIMMING_POOL else E.SWIMMING_OPEN_WATER
        47L -> E.TABLE_TENNIS
        48L -> E.TENNIS
        51L -> E.VOLLEYBALL
        52L -> E.WALKING
        57L -> E.YOGA
        62L -> E.STRETCHING
        66L -> E.PILATES
        69L -> E.EXERCISE_CLASS
        else -> OTHER
    }
}
