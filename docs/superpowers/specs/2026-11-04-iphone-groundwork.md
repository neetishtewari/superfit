# iPhone groundwork

Approved by Neetish on 4 Nov 2026 ("lets start the groundwork"). Background: `notes/superfit-on-ios.md`
and the growth review (https://claude.ai/artifact/CTtuwaPTo3AvjPygxjcwrs).

The goal is to get the Android app ready to share most of its code with an iPhone app (Kotlin
Multiplatform). Users don't notice anything. Each step ships in a normal Android release and is
tested on the dev app first.

## Steps

1. **Cloud ids for entries** (this change). Every meal, workout and weight gets a `cloudId`, which
   becomes its name in the cloud backup. New entries get a random id (`kotlin.uuid`, which also
   works on iPhone). Existing entries keep their old number as their cloud id, so their backups
   still match and nothing is uploaded again. A restore gives entries new local numbers and skips
   any whose cloud id the phone already has. Two devices on one account no longer overwrite each
   other's entries. Database version 8 to 9.
2. **Shared module.** Add a `shared` Kotlin Multiplatform module next to `app` (Android and JVM
   targets for now; JVM only runs the shared tests in CI). Kotlin goes from 2.0.21 to 2.2.21, which
   the Android multiplatform library plugin needs. `CloudIds` is the first shared file. The app
   builds and behaves exactly as before.
3. **Move the brains.** Move the Android-free logic (coaching patterns, Diet Quality, streaks,
   calories, recap, meal items) into `shared`. Replace `org.json` with kotlinx.serialization and
   `java.time` with kotlinx-datetime where needed. Existing unit tests move with the code.
   Done in small pieces, each its own pull request:
   - 3a: files that need nothing from Android or Java (number input, goals, photo framing, heart
     zones, coach styles), with their tests.
   - 3b: a small shared JSON reader (`com.superfit.app.json`) that copies the parts of Android's
     `org.json` the app uses, with the same forgiving rules, so the AI's answers and saved data read
     exactly as before. The app's logic files switch to it; the files themselves move in 3c. It needs
     no new library.
   - 3c: the database entities (meals, workouts, weights, profile...) move to `shared`, since most
     logic reads them. Only Room's annotations come along; the DAOs and database stay in `app`
     until step 4. Same tables, so no database change.
   - 3d: meal items, meal tags, personal nutrition, packaged food, repeat-meal cache and the
     weight trend move to `shared` (they need no dates). `String.format("%.1f")` becomes
     `Decimals.one`, which rounds the same way.
   - 3e: adds kotlinx-datetime to `shared`; the meal streak moves first, as the smallest file that
     needs dates. Same day boundaries (the phone's time zone), so streaks don't change.
   - 3f: smart scale logic (grouping readings, "Is this you?", the Body card and 4-week body trend)
     moves to `shared`. Scale readings now carry their time as milliseconds; the app converts Health
     Connect times at the edge. Saved readings already stored milliseconds, so nothing on the phone
     changes.
   - 3g: workout merging (one workout from several apps' copies) and workout display (names, pace,
     exercise calories) move to `shared`. Health Connect's exercise type numbers are copied into
     `ExerciseTypes`, with an app test that checks every one against the Health Connect library;
     on iPhone, HealthKit types will map onto the same numbers.
   - 3h: personal bests and strength sessions move to `shared`. Insights, Journey, the monthly
     recap, the coach report and the Diet Quality score all share the same day-by-day data and the
     coach's pattern engine, so they follow bottom-up in the next pieces.
   - 3i onwards: files that need `java.time` swapped for kotlinx-datetime, `String.format` for a
     shared formatter, and the AI calls behind a small shared interface.
4. **Move the database.** Room supports Kotlin Multiplatform, so the entities and DAOs move to `shared`.
5. **Then the iPhone work** (needs Neetish's Mac with Xcode, an Apple Developer account and an
   iPhone): an `iosApp` folder, shared screens, then the iPhone-only parts.

## Launch goal

Neetish, 4 Nov 2026: the iPhone app launches at par with the Android app. Everything Android has at
that point ships on iPhone too, including the home-screen widget, the recap video with music,
workout share cards and the PDF report. Nothing is held back for a later version.

## Not in scope

- Live sync between two devices that are both in use. The backup still restores onto a new device;
  this change only stops the two from overwriting each other.
- Renaming the cloud documents of existing entries.

## Testing for step 1

- Migration test: databases from versions 1 to 3 upgrade to 9, and existing meals get their number as their cloud id.
- DAO tests: a restore skips an entry already on the phone with the same cloud id, and adds another
  device's entry even when both devices numbered it the same.
- Dev app on the phone: log, edit and delete a meal, a workout and a weight. Each should appear, change
  and disappear under its new id in Firestore (`users/{uid}/nutrition`, `workouts`, `weights`).
- Sign out and back in on the dev app: history comes back once, with no duplicates.

## Security (Neetish, 4 Nov 2026: security review after every step, for both apps)

Each step gets a security review before merge, on top of the release checklist's security section.

Step 3h (reviewed 5 Nov): code moved without behaviour changes. No new libraries, permissions,
network calls or secrets.

Step 3g (reviewed 5 Nov): code moved without behaviour changes. No new libraries, permissions,
network calls or secrets.

Step 3f (reviewed 5 Nov): code moved without behaviour changes; the saved scale readings keep
the same format. No new libraries, permissions, network calls or secrets. Body data still stays on
the phone.

Step 3e (reviewed 5 Nov): adds kotlinx-datetime 0.7.1 (JetBrains' official date library, from
Maven Central; on Android it uses the phone's own java.time underneath). The streak moves without
behaviour changes. No new permissions, network calls or secrets.

Step 3d (reviewed 5 Nov): code moved without behaviour changes; no new libraries, permissions,
network calls or secrets.

Step 3c (reviewed 5 Nov): entities move unchanged apart from the profile's start-date default,
which now reads the clock through Kotlin instead of Java. Adds Room's annotation library (Google's,
already in the app). No new permissions, network calls or secrets.

Step 3b (reviewed 4 Nov): the JSON reader only parses text the app already parsed (AI answers,
its own saved data); no network, files or permissions. It has no recursion limit, like org.json, so
an absurdly deeply nested AI answer could run out of stack, exactly as it could with org.json today.

Step 3a (reviewed 4 Nov): code moved without changes, apart from one maths call swapped for its
Kotlin version. No new permissions, network calls, secrets or libraries.

Steps 1-2 (reviewed 4 Nov): no new permissions, secrets or network calls. Cloud ids are random
(`kotlin.uuid`, secure random) but aren't secrets; access still rests on `firestore.rules`, which only
lets a signed-in user read and write `users/{their uid}`. New build plugins come from the official
Gradle and Google repositories.

For the iPhone app, before TestFlight:
- App Check with App Attest (Apple's version of Play Integrity), enforced for AI Logic like Android.
- The iPhone Firebase API key restricted to the iPhone bundle id in Google Cloud.
- Sign-in tokens stay in the Keychain (Firebase does this by default); nothing personal in UserDefaults.
- No AI or other secret keys in the app; AI goes through Firebase AI Logic as on Android.
- App Transport Security left on (HTTPS only), no exceptions.
- Apple Health data used only for the app's features, never for ads, and described honestly in the
  App Store privacy answers and the Health permission text.
- The widget and app share data through an App Group, readable only by Superfit.
- Same Firestore rules for both apps; any rules change is reviewed and deployed once.
