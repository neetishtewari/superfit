# Room library rules
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Kotlin serialization rules
-keepclassmembers class * {
    *** Companion;
}
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Gemini AI SDK reflection support
-keep class com.google.ai.client.generativeai.** { *; }
-dontwarn com.google.ai.client.generativeai.**

# WorkManager creates the input merger of every one-off job by reflection. Without this, R8 removes
# the constructor and each OneTimeWorkRequest fails in release ("Could not create Input Merger"),
# which left the widget loading forever and stopped the daily reminder.
-keep class * extends androidx.work.InputMerger { <init>(); }
