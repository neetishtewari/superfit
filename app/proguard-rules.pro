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

# WorkManager builds the input merger by reflection. Without this, R8 drops its constructor and
# every one-off job (the home-screen widget, the evening reminder) fails in the release build.
-keep class * extends androidx.work.InputMerger { <init>(); }
