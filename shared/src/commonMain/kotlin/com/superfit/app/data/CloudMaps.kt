package com.superfit.app.data

// How each table row is written to and read back from the cloud backup
// (Firestore users/{uid}/...). Shared so the Android and iPhone apps back up in exactly the same
// shape, and either can restore what the other saved. Field names must never change: older
// backups depend on them. Numbers are read as any Number because the cloud hands back whole
// numbers as Long and the iPhone bridge may hand back other number types.

fun UserProfileEntity.toMap(): Map<String, Any> = mapOf(
    "id" to id,
    "age" to age,
    "heightCm" to heightCm,
    "weightKg" to weightKg,
    "isMale" to isMale,
    "activityMultiplier" to activityMultiplier,
    "goal" to goal,
    "calorieOffset" to calorieOffset,
    "startingWeightKg" to startingWeightKg,
    "targetWeightKg" to targetWeightKg,
    "startDateTimestamp" to startDateTimestamp,
    "fitnessLevel" to fitnessLevel,
    "birthYearMonth" to birthYearMonth
)

fun Map<String, Any>.toUserProfileEntity(): UserProfileEntity = UserProfileEntity(
    id = (this["id"] as? Number)?.toInt() ?: 0,
    age = (this["age"] as? Number)?.toInt() ?: 0,
    heightCm = (this["heightCm"] as? Number)?.toDouble() ?: 0.0,
    weightKg = (this["weightKg"] as? Number)?.toDouble() ?: 0.0,
    isMale = this["isMale"] as? Boolean ?: true,
    activityMultiplier = (this["activityMultiplier"] as? Number)?.toDouble() ?: 1.2,
    goal = this["goal"] as? String ?: "LOSE_WEIGHT",
    calorieOffset = (this["calorieOffset"] as? Number)?.toInt() ?: -500,
    startingWeightKg = (this["startingWeightKg"] as? Number)?.toDouble() ?: (this["weightKg"] as? Number)?.toDouble() ?: 75.0,
    targetWeightKg = (this["targetWeightKg"] as? Number)?.toDouble() ?: 70.0,
    startDateTimestamp = (this["startDateTimestamp"] as? Number)?.toLong() ?: 0L,
    fitnessLevel = this["fitnessLevel"] as? String ?: "INTERMEDIATE",
    birthYearMonth = (this["birthYearMonth"] as? Number)?.toInt() ?: 0
)

fun ActivityTelemetryEntity.toMap(): Map<String, Any> = mapOf(
    "date" to date,
    "steps" to steps,
    "activeCalories" to activeCalories
)

fun Map<String, Any>.toActivityTelemetry(): ActivityTelemetryEntity = ActivityTelemetryEntity(
    date = this["date"] as? String ?: "",
    steps = (this["steps"] as? Number)?.toInt() ?: 0,
    activeCalories = (this["activeCalories"] as? Number)?.toDouble() ?: 0.0
)

fun SleepTelemetryEntity.toMap(): Map<String, Any> = mapOf(
    "date" to date,
    "sleepDurationSeconds" to sleepDurationSeconds,
    "deepSleepDurationSeconds" to deepSleepDurationSeconds,
    "readinessScore" to readinessScore
)

fun Map<String, Any>.toSleepTelemetry(): SleepTelemetryEntity = SleepTelemetryEntity(
    date = this["date"] as? String ?: "",
    sleepDurationSeconds = (this["sleepDurationSeconds"] as? Number)?.toLong() ?: 0L,
    deepSleepDurationSeconds = (this["deepSleepDurationSeconds"] as? Number)?.toLong() ?: 0L,
    readinessScore = (this["readinessScore"] as? Number)?.toInt() ?: 0
)

fun NutritionEntryEntity.toMap(): Map<String, Any> = buildMap {
    put("id", id)
    put("foodText", foodText)
    put("calories", calories)
    put("proteinG", proteinG)
    put("carbsG", carbsG)
    put("fatG", fatG)
    put("timestamp", timestamp)
    qualityTags?.let { put("qualityTags", it) }
    items?.let { put("items", it) }
}

fun Map<String, Any>.toNutritionEntry(cloudId: String): NutritionEntryEntity = NutritionEntryEntity(
    cloudId = cloudId,
    foodText = this["foodText"] as? String ?: "",
    calories = (this["calories"] as? Number)?.toDouble() ?: 0.0,
    proteinG = (this["proteinG"] as? Number)?.toDouble() ?: 0.0,
    carbsG = (this["carbsG"] as? Number)?.toDouble() ?: 0.0,
    fatG = (this["fatG"] as? Number)?.toDouble() ?: 0.0,
    timestamp = (this["timestamp"] as? Number)?.toLong() ?: 0L,
    qualityTags = this["qualityTags"] as? String,
    items = this["items"] as? String
)

fun WorkoutEntryEntity.toMap(): Map<String, Any> = mapOf(
    "id" to id,
    "description" to description,
    "caloriesBurned" to caloriesBurned,
    "workoutType" to workoutType,
    "setsCount" to setsCount,
    "repsCount" to repsCount,
    "timestamp" to timestamp,
    "difficultyRating" to difficultyRating
)

fun Map<String, Any>.toWorkoutEntry(cloudId: String): WorkoutEntryEntity = WorkoutEntryEntity(
    cloudId = cloudId,
    description = this["description"] as? String ?: "",
    caloriesBurned = (this["caloriesBurned"] as? Number)?.toDouble() ?: 0.0,
    workoutType = this["workoutType"] as? String ?: "Cardio",
    setsCount = (this["setsCount"] as? Number)?.toInt() ?: 0,
    repsCount = (this["repsCount"] as? Number)?.toInt() ?: 0,
    timestamp = (this["timestamp"] as? Number)?.toLong() ?: 0L,
    difficultyRating = this["difficultyRating"] as? String ?: "JUST_RIGHT"
)

fun WeightEntryEntity.toMap(): Map<String, Any> = mapOf(
    "id" to id,
    "weightKg" to weightKg,
    "timestamp" to timestamp,
    "note" to note
)

fun Map<String, Any>.toWeightEntry(cloudId: String): WeightEntryEntity = WeightEntryEntity(
    cloudId = cloudId,
    weightKg = (this["weightKg"] as? Number)?.toDouble() ?: 0.0,
    timestamp = (this["timestamp"] as? Number)?.toLong() ?: 0L,
    note = this["note"] as? String ?: ""
)

fun StreakStateEntity.toMap(): Map<String, Any> = mapOf(
    "id" to id,
    "currentStreak" to currentStreak,
    "longestStreak" to longestStreak,
    "masteryStreak" to masteryStreak,
    "graceDaysRemaining" to graceDaysRemaining,
    "lastLoggedDate" to lastLoggedDate,
    "lastGraceUsedDate" to lastGraceUsedDate
)

fun Map<String, Any>.toStreakState(): StreakStateEntity = StreakStateEntity(
    id = (this["id"] as? Number)?.toInt() ?: 0,
    currentStreak = (this["currentStreak"] as? Number)?.toInt() ?: 0,
    longestStreak = (this["longestStreak"] as? Number)?.toInt() ?: 0,
    masteryStreak = (this["masteryStreak"] as? Number)?.toInt() ?: 0,
    graceDaysRemaining = (this["graceDaysRemaining"] as? Number)?.toInt() ?: 1,
    lastLoggedDate = this["lastLoggedDate"] as? String ?: "",
    lastGraceUsedDate = this["lastGraceUsedDate"] as? String ?: ""
)

fun HabitEntryEntity.toMap(): Map<String, Any> = mapOf(
    "date" to date,
    "waterMl" to waterMl,
    "workoutMins" to workoutMins,
    "cleanEatsCompleted" to cleanEatsCompleted,
    "stepsCompleted" to stepsCompleted,
    "sleepCompleted" to sleepCompleted
)

fun Map<String, Any>.toHabitEntry(): HabitEntryEntity = HabitEntryEntity(
    date = this["date"] as? String ?: "",
    waterMl = (this["waterMl"] as? Number)?.toInt() ?: 0,
    workoutMins = (this["workoutMins"] as? Number)?.toInt() ?: 0,
    cleanEatsCompleted = this["cleanEatsCompleted"] as? Boolean ?: false,
    stepsCompleted = this["stepsCompleted"] as? Boolean ?: false,
    sleepCompleted = this["sleepCompleted"] as? Boolean ?: false
)
