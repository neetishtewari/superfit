package com.superfit.app.domain

/**
 * Health Connect's exercise type numbers (ExerciseSessionRecord.EXERCISE_TYPE_*), copied here so the
 * shared code can use them on iPhone too, where HealthKit types will be mapped onto them. They are
 * fixed by Health Connect; a test in the app checks each one against the library.
 */
object ExerciseTypes {
    const val BADMINTON = 2
    const val BASKETBALL = 5
    const val BIKING = 8
    const val BIKING_STATIONARY = 9
    const val BOOT_CAMP = 10
    const val BOXING = 11
    const val CALISTHENICS = 13
    const val CRICKET = 14
    const val DANCING = 16
    const val ELLIPTICAL = 25
    const val EXERCISE_CLASS = 26
    const val GOLF = 32
    const val GYMNASTICS = 34
    const val HIGH_INTENSITY_INTERVAL_TRAINING = 36
    const val HIKING = 37
    const val MARTIAL_ARTS = 44
    const val PADDLING = 46
    const val PILATES = 48
    const val ROCK_CLIMBING = 51
    const val ROWING = 53
    const val ROWING_MACHINE = 54
    const val RUNNING = 56
    const val RUNNING_TREADMILL = 57
    const val SKIING = 61
    const val SOCCER = 64
    const val SQUASH = 66
    const val STAIR_CLIMBING = 68
    const val STAIR_CLIMBING_MACHINE = 69
    const val STRENGTH_TRAINING = 70
    const val STRETCHING = 71
    const val SURFING = 72
    const val SWIMMING_OPEN_WATER = 73
    const val SWIMMING_POOL = 74
    const val TABLE_TENNIS = 75
    const val TENNIS = 76
    const val VOLLEYBALL = 78
    const val WALKING = 79
    const val WEIGHTLIFTING = 81
    const val YOGA = 83
}
