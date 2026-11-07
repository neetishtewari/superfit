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
   - 3i: the Diet Quality score, meal ratings, calorie sources, treats and the coach's pattern
     engine move to `shared` together (they call each other). Dates use kotlinx-datetime; the
     coach's day names ("Sun 11 Oct") are written by hand in English, as before, with a test.
     Food names are capitalised without the phone's language rules (only Turkish "i" differs).
   - 3j: the Insights week report, "vs your usual" and activity patterns move to `shared`, with the
     day-by-day totals they read. Weekday names in the phone's language come from a small
     platform hook (`localWeekdayName`, `localMonthName`): Java's names on Android, Apple's on
     iPhone. Journey, the recap and the coach report still live in the app and convert dates at
     the edge until they move.
   - 3k: the workout card's "stands out" line and its sleep line move to `shared` (month names
     through `localMonthName`).
   - 3l: Journey's goal forecast (finish date, milestones, "what would speed it up") moves to
     `shared`; dates in the phone's language through `localMonthName`.
   - 3m: the monthly recap (which cards appear, their numbers, and the words for the AI) moves to
     `shared`, using kotlinx-datetime's YearMonth. The saved AI words keep the same key, so
     nobody's finished recap is rewritten.
   - 3n: the "Share my report" numbers (averages, weigh-ins, day-by-day meals and workouts) move
     to `shared`. The PDF drawing itself stays Android-only for now; the iPhone draws its own.
   - 3o: reading meals and workouts, deciding meal vs workout, meal tagging, changing one meal
     item and the nutritionist's note move to `shared`. They ask the AI through a small shared
     `Ai` interface; Android plugs in its Firebase AI Logic client (GeminiClient) at start-up, the
     iPhone app will plug in Firebase's iPhone SDK. Photo reading, coach chat and the model
     failover stay in the app for now.
   - 3p: coach chat history (earlier topics, day labels) and the coach's notes (what it remembers,
     when short notes and plans fade, plan follow-ups) move to `shared`, with a small shared
     `EnglishDates` for "Sat 10 Oct" style wording. Saved notes keep the same format, so nothing
     anyone saved changes.
   - 3q: the coach's daily tip (choosing today's topic, habit tips, follow-ups, the prompt and
     reading the answer) and the coach chat's picture of the user (meals, days, workouts, notes)
     move to `shared`. The tip asks the AI through `Ai`; a failed call shows the same friendly
     message, which each app supplies through `Ai.friendlyError`.
   - 3r: photo reading (meal photos and nutrition labels) moves to `shared`, and coach chat
     replies go through a shared `Ai.chat`. A photo is a small `AiPhoto` wrapper: a Bitmap on
     Android, a UIImage on iPhone. Android still shrinks the photo to 1024 px and sends it through
     Firebase AI Logic with the same model failover.
   - 3s: calorie and macro targets (PhysiologyEngine, onboarding targets, height units), the
     "log again" food suggestions, workout-card chips and journey line, and the recap's number
     count-up move to `shared`.
   - 3t: share-card text (ShareCards, CardLines, RoastCard) moves to `shared`. Dates, times and
     grouped numbers stay in the phone's language through a small `LocalFormat` that each app
     writes with its own formatters; km and pace use the shared `Decimals`.
   - Later: files that need `java.time` swapped for kotlinx-datetime, `String.format` for a
     shared formatter, and the AI calls behind a small shared interface.
4. **Move the database.** Room supports Kotlin Multiplatform, so the entities and DAOs move to `shared`.
   - 4a: Room's code generator switches from kapt to KSP (the multiplatform way). The database,
     its tables and saved schema stay exactly the same; only the build tool changes.
   - 4b: the DAOs and the database class move to `shared` (`@ConstructedBy`, KSP runs per
     platform, schemas now in `shared/schemas`). Android keeps its existing database file,
     per-user file name and migrations in `SuperfitDatabaseBuilder.kt`. Room's bundled SQLite
     driver is added with the iPhone target in step 5; the iPhone starts with a fresh database.
5. **Then the iPhone work.**
   - 5a (no Apple account needed): `shared` also builds for iPhone (`iosArm64`,
     `iosSimulatorArm64`) as a static `Shared` framework, with iPhone versions of `AppLog`,
     `AiPhoto` (JPEG bytes), `LocalFormat` and `localWeekdayName`/`localMonthName` (Foundation's
     formatters in the phone's language), and Room's bundled SQLite. CI's `ios-shared` job runs
     the shared tests on the iPhone simulator and builds the framework on GitHub's Mac.
   - 5b (needs Neetish's Mac with Xcode, an Apple Developer account and an iPhone): an `iosApp`
     folder, shared screens, then the iPhone-only parts.
     - 5b-1: `iosApp/` app shell. The Xcode project is generated by XcodeGen from
       `iosApp/project.yml` (not committed), bundle id `com.superfit.aifitness`, iOS 17+, iPhone
       only. A build step runs Gradle's `embedAndSignAppleFrameworkForXcode` to link `Shared`.
       One placeholder screen shows numbers from the shared code. CI's `ios-app` job builds it for
       the simulator without signing. The signing team is set on Neetish's Mac, never in the repo.
     - Screens (Neetish, 7 Nov 2026): the iPhone reuses the Android screens through Compose
       Multiplatform, with iPhone touches (edge swipe back, bounce scrolling, Apple share sheet,
       keyboard, permission prompts, Larger Text). A screen can later be rebuilt in SwiftUI on its
       own if it feels wrong. Test device: iPhone 17.
     - 5b-2: Compose Multiplatform in `shared`; theme (colours, Manrope fonts, type scale) and the
       shared components (SuperfitField, MicButton, SegmentedPills...) move to commonMain; the
       iPhone shell shows them through `ComposeUIViewController`.
     - 5b-3: Firebase on iPhone. Neetish registers an iPhone app in the Firebase console
       (`GoogleService-Info.plist`); Auth (Google sign-in + Sign in with Apple, which Apple
       requires next to Google) and Firestore through a Kotlin Multiplatform Firebase library;
       Firebase AI Logic through Firebase's iPhone SDK behind the shared `Ai` interface; App Check
       with App Attest.
     - 5b-4: data layer to shared: DataRepository, backup/restore (FirebaseSyncManager), settings
       stores (a multiplatform key-value store in place of SharedPreferences).
     - 5b-5: ViewModels (lifecycle-viewmodel multiplatform) and screens move one at a time:
       login, onboarding, Today, History/Progress, Settings, coach chat, recap, share cards. Each
       move ships in an Android release first (phone-checked), then lights up on iPhone.
     - 5b-6: iPhone-only parts behind small shared interfaces: camera, barcode and label reading
       (AVFoundation/Vision), voice (Speech framework), Apple Health in place of Health Connect,
       notifications and daily reminders, background refresh, home-screen widget (WidgetKit,
       SwiftUI), share sheet, PDF report, recap video with music, in-app review, analytics and
       crash reports.
     - 5b-7: TestFlight build for Neetish's iPhone 17, then App Store review.

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

Step 5b-1 (reviewed 7 Nov): placeholder screen only; no sign-in, data, network calls,
permissions or secrets. No signing team, certificates or provisioning profiles in the repo; CI
builds unsigned. XcodeGen comes from Homebrew. `JSONObject.NULL` is named `jsonNull` in the
iPhone headers (NULL is a C macro); Android is unchanged.

Step 5a (reviewed 6 Nov): no iPhone app yet, so nothing ships to anyone. The iPhone log only
writes debug text (no meals, weights or personal data), the same rule as Android. SQLite is built
into the app from Google's Maven (androidx.sqlite 2.5.0); the database file will live in the
app's own private folder, as on Android. No secrets, keys or network calls added; the CI job uses
no secrets.

Step 4b (reviewed 6 Nov): same tables, queries and schema (version 9), same file per signed-in
user, so one person's data is never opened for another. All queries stay Room's bound
parameters (no text pasted into SQL). Room's runtime and compiler come from Google's Maven at the
same version as before. No new permissions, network calls or secrets.

Step 4a (reviewed 6 Nov): build-tool change only. KSP comes from Google's official Maven
repository; the generated database code and schema are unchanged. No new permissions, network
calls or secrets.

Step 3t (reviewed 6 Nov): card text built the same way; weight and calories eaten are still never
on a card. The AI prompts for card lines are unchanged and still only get facts the app already
shows. No new libraries, permissions, network calls or secrets.

Step 3s (reviewed 6 Nov): pure calculations moved without behaviour changes. No new libraries,
permissions, network calls or secrets; nothing here touches storage or the network.

Step 3r (reviewed 6 Nov): photos still go only to Firebase AI Logic (App Check), shrunk first and
never saved; the wrapper holds the photo in memory only. Chat history sent to the AI is the same
as before. No new libraries, permissions, network calls or secrets; the camera permission is
unchanged.

Step 3q (reviewed 6 Nov): prompts, rules and topic choice moved without changes; the coach still
reaches the AI only through Firebase AI Logic with App Check. Error text shown to people still goes
through GeminiClient's sanitiser, so no raw server errors or ids reach the screen. Coach memory is
stored in the same prefs key and format. No new libraries, permissions, network calls or secrets.

Step 3p (reviewed 6 Nov): code moved without behaviour changes. Notes are stored, backed up and
cleared exactly as before (same prefs key and Firestore documents); the note text sent to the AI is
unchanged. No new libraries, permissions, network calls or secrets.

Step 3o (reviewed 6 Nov): prompts and answer reading moved without changes. The AI is still
reached only through Firebase AI Logic with App Check, the same models and the same retries; the
new interface only passes text in and out. No keys or secrets in shared code, no new libraries,
permissions or network calls. Logging from shared code goes to Logcat as before.

Step 3n (reviewed 6 Nov): code moved without behaviour changes. No new libraries, permissions,
network calls or secrets. The report is still built on the phone and only leaves it when the user
shares it themselves.

Step 3m (reviewed 5 Nov): code moved without behaviour changes. No new libraries, permissions,
network calls or secrets. The recap still makes one AI call a month through Firebase AI Logic,
sending the same facts as before (numbers and logged food names; no photos or account details).

Step 3l (reviewed 5 Nov): code moved without behaviour changes. No new libraries, permissions,
network calls or secrets. Weight data still stays on the phone and in the user's own cloud backup.

Step 3k (reviewed 5 Nov): code moved without behaviour changes. No new libraries, permissions,
network calls or secrets. Sleep data still stays on the phone.

Step 3j (reviewed 5 Nov): code moved without behaviour changes. No new libraries, permissions,
network calls or secrets.

Step 3i (reviewed 5 Nov): code moved without behaviour changes. No new libraries, permissions,
network calls or secrets. What the coach sends the AI is built from the same numbers as before.

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
