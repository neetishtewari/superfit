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
       Done in two parts: first the theme (Color, Type, Theme; Compose Multiplatform 1.10.3 to match
       the app's Compose 1.10; Manrope from shared `composeResources`, so `Manrope` and
       `Typography` are now `@Composable` getters; the unused wallpaper-colour option removed) with a
       stand-in `ThemePreview` screen on iPhone; then the shared components. The PDF report keeps
       its own copy of the fonts in `app/res/font` until it moves.
       Part two (5b-2b): SuperfitField, MicButton, FieldActionButton, SuperfitCard, IconBadge,
       PrimaryButton, logo and brand header, GoalPicker and SegmentedPills move to commonMain with
       the same names and packages, so no Android screen changes. The Android voice typing box
       (`rememberVoiceTyping`) stays in the app. Shared code gets the Material icons library
       (JetBrains build of the same icons); the logo is a 288 px copy in shared resources (the
       1 MB original stays for the launcher, share cards and PDF). The iPhone stand-in screen now
       shows these pieces.
     - 5b-3: Firebase on iPhone. Neetish registers an iPhone app in the Firebase console
       (`GoogleService-Info.plist`); Auth (Google sign-in + Sign in with Apple, which Apple
       requires next to Google) and Firestore; Firebase AI Logic behind the shared `Ai` interface;
       App Check with App Attest.
       Decided 7 Nov: the iPhone uses Google's own Firebase iPhone SDK (Swift Package Manager in
       `project.yml`), wrapped in small Swift classes that implement shared Kotlin interfaces
       (cloud store, sign-in, AI). No third-party Kotlin Firebase wrapper: fewer moving parts and
       only Google's code touches accounts and data. The Swift side turns iPhone number types into
       Kotlin Long/Double before shared code reads them.
       - 5b-3a: the backup format moves to shared: `CloudMaps.kt` (how each table row is written to
         and read from Firestore, field names unchanged) and `withoutLegacyDuplicates`, with
         round-trip tests. Android's FirebaseSyncManager uses them; behaviour unchanged.
       - 5b-3b: Firebase iPhone SDK + `GoogleService-Info.plist` (committed like Android's
         google-services.json; it identifies the project, it is not a secret) and App Check.
         Firebase starts in `SuperfitApp.init` after App Check is set: App Attest on real
         iPhones, the debug provider in Debug/simulator builds (token added in the Firebase
         console, never in the repo). The App Attest entitlement is generated by XcodeGen.
       - 5b-3c: the sync logic itself moves to shared: `CloudBackup` (what is backed up, where,
         merge-on-restore, account data wipe) over a `CloudStore` interface (get, list, set, batch
         set, delete by slash path) and `CloudBackupLocal` (coach chat/notes and settings kept
         outside the database). Android's FirebaseSyncManager keeps its methods and only plugs in
         Firestore's Android SDK; deleting the Firebase account stays per app.
       - 5b-3d: the iPhone's CloudStore in Swift (Firestore iPhone SDK). Swift can't implement
         Kotlin suspend functions, so iosMain adds a callback version that Swift implements.
       - 5b-3e: the sign-in screen moves to shared (LoginScreen + LoginModel over an `AuthClient`
         for email; each app runs its own Google sheet). iPhone signs in with Firebase's iPhone SDK:
         email, Google (GoogleSignIn-iOS) and Sign in with Apple, shown first on iPhone because App
         Review asks for it whenever Google sign-in is offered. Android looks and works the same;
         its backup restore after sign-in now runs once (it used to run twice at the same time).
         Apple accounts can't sign in on Android yet: adding "Continue with Apple" to Android
         (Firebase's web flow, needs an Apple Services ID) is a follow-up before the iPhone launch.
       - 5b-3f: the iPhone's database (Room with bundled SQLite, one file per account in the app's
         private Application Support folder) and CloudBackup wired up: after sign-in the iPhone
         restores the backup (merge only, same code as Android) and the placeholder shows how many
         meals, workouts and weigh-ins arrived, to compare with the Android app. Coach chat and
         notes go to per-account files there too. Saving new entries comes with the screens.
       - 5b-3g: the AI client on iPhone. Model order, retries, timeouts and the wording of AI errors
         move to shared (AiFailover; Android's GeminiClient keeps only its Firebase calls). Swift
         makes each single call with Firebase AI Logic's iPhone SDK (FirebaseAiBridge), so both
         apps use the same models, App Check and billing. The placeholder gets a "Test the AI
         coach" button until the coach screen moves over.
     - 5b-4: data layer to shared: DataRepository, backup/restore (FirebaseSyncManager), settings
       stores (a multiplatform key-value store in place of SharedPreferences).
       - 5b-4a: a shared settings store (Prefs). Android wraps the user's existing
         SharedPreferences file (superfit_prefs_<uid>, same keys and types, so nothing moves or
         resets). iPhone uses a UserDefaults suite of the same name. The first stores move to
         shared on it: coach style, step goal, nutritionist advice and "Fine for me" foods, and the
         choices for workouts from other apps. The iPhone's coach notes, restored workout choices
         and backup-repair flag now sit in that store too, under Android's keys.
       - 5b-4b: the log itself moves to shared (LogRepository): profile, meals, logged workouts,
         weigh-ins (including the one-scale-reading-a-day rule), streak, water and habits, and
         saving steps and sleep read from the health app. Each change is saved to the database,
         then backed up, exactly as before. Android's DataRepository keeps its signatures and
         passes these through; it still owns Health Connect, the widget refresh (run after each
         meal change), imported workouts, coach chat files and clearing data. Tested on a real
         in-memory database (bundled SQLite) on the computer.
     - 5b-5 starts bottom-up with the Today screen's parts, then its ViewModel, then the screen:
       - 5b-5a: the self-contained Today cards and dialogs move to shared unchanged: streak header,
         goal progress card with weigh-in and edit-goal dialogs, step-goal note/sheet/chooser,
         Diet Quality card and detail sheet, coach style chooser, widget tip card, and the
         dashboard colour tokens. Numbers with decimals use LocalFormat.decimal (the phone's own
         style, as String.format did on Android); step counts keep "12,000".
         Also the Home "Log weight" row and the "Is this you?" scale dialog, on kotlinx-datetime;
         the dialog's time uses LocalFormat.shortClock (the phone's 12/24-hour style, as before).
       - 5b-5b: what the Today screen shows (DashboardUiState and its parts) moves to shared, so
         both apps describe Today the same way. The rings, macro bars, sleep pills, meal row and
         activity bar move too; the calories-left number uses LocalFormat.integer ("1,230").
       - 5b-5c: the sums behind Today (targets, own macro targets, eaten totals, streak, step and
         sleep ticks, Diet Quality, workout add-back) move to shared TodayState.build, tested on
         the computer. Android reads the pieces and calls it; the screen is unchanged.
       - 5b-5d: the Today sections move to shared: header (date, greeting), calorie rings card,
         nutrients card, coach card, activity card with "vs your usual", sleep card, and the
         imported-workout rows, connect card and duplicate dialog. Dates and times come from
         Now.today/hour/local and LocalFormat.longDay/weekday/shortClock (the phone's language,
         as before). The meals card stays in the Android app until the meal edit sheet moves.
       - 5b-5e: the Edit meal sheet and item editor move to shared. Each app plugs in two small
         things: voice typing for "what was it instead?" (LocalVoiceTyping; Android's voice
         typing box as before) and usage counts (UsageEvents; Android sends them through
         AppAnalytics, so the same off switch and dev-app silence apply).
       - 5b-5f: the meals card (type a meal, usual foods, logged list, edit) moves to shared; it
         takes its data and actions as parameters instead of the Android ViewModel.
       - 5b-5g: the iPhone shows Today's Food side after sign-in and restore: header, rings,
         streak, nutrients, weigh-in and the meals card (type a meal, usual foods, See all,
         edit, delete). A small shared TodayModel reads the shared LogRepository and builds
         the state with TodayState.build; logging uses the shared NutritionParser. Steps,
         sleep and other apps' workouts wait for Apple Health. Android is unchanged (only the
         food history dialog moved to shared).
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

Step 5b-3c (reviewed 7 Nov): the same paths (users/{uid}/..., only the signed-in user's own
uid, nothing written when signed out) and fields as before; Firestore rules unchanged. Account
deletion still clears every collection under the user before the account is deleted. Tests
cover paths, signed-out, failed writes and that a wipe touches only that user's data. No new
permissions or secrets; kotlinx-coroutines (already in the app) is now listed in shared.

Step 5b-3d (reviewed 8 Nov): the iPhone's Firestore store only adds the Swift side of the same
CloudStore; it writes nothing on its own and is not called until iPhone sign-in (5b-3e), so the
same users/{uid}/ paths and unchanged Firestore rules apply. Values keep their types both ways
(true/false stay booleans, whole numbers stay whole, decimals stay decimals) so Android reads
iPhone backups the same. No secrets, no new permissions; Firestore traffic is HTTPS and goes
through App Check like the rest of Firebase.

Step 5b-3e (reviewed 8 Nov): sign-in stays on Firebase Auth for both apps, so the same accounts,
uid-scoped Firestore rules and App Check apply. Passwords go only to Firebase and are never
stored or logged; error text shown to people is the shared plain wording, never raw Firebase
text. Sign in with Apple uses a fresh random nonce per attempt (SecRandomCopyBytes; SHA-256 to
Apple, raw to Firebase) so an Apple token can't be replayed. Google uses Google's own sheet with
the iPhone client id from GoogleService-Info.plist; its REVERSED_CLIENT_ID URL scheme is an app
identifier, not a secret. No new Android permissions; the iPhone adds the Sign in with Apple
entitlement only. Sign-out also signs out of Google so the next person picks their own account.

Step 5b-3b (reviewed 7 Nov): GoogleService-Info.plist is committed like Android's
google-services.json: its API key only identifies the Firebase project; access is guarded by
Firestore rules (each user only their own users/{uid}) and App Check. To do in Google Cloud
(Neetish): restrict the iPhone API key to the bundle id com.superfit.aifitness, and register the
iPhone app for App Check with App Attest (needs the Apple Team ID) before TestFlight. Firebase
comes only from Google's official firebase-ios-sdk package. Analytics stays off in the plist.

Step 5b-3a (reviewed 7 Nov): moves code only. The backup still goes to the same place
(users/{uid}/..., only the signed-in user's own uid; Firestore rules unchanged) with the same
fields, so nothing new is stored or sent. No new libraries, permissions or secrets.

Step 5b-2b (reviewed 7 Nov): look-and-feel pieces only. The mic button on iPhone does nothing
yet (no microphone access asked for). Material icons come from JetBrains on Maven Central. No new
permissions, network calls, data or secrets on either phone.

Step 5b-2a (reviewed 7 Nov): colours, fonts and shapes only. Compose Multiplatform and its
resources library come from JetBrains on Maven Central; fonts are the same Manrope files. No new
permissions, network calls, data or secrets on either phone.

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

Step 5b-3f (reviewed 9 Nov): the iPhone database and coach files live in the app's own
Application Support folder (sandboxed, not shared with other apps, removed with the app), one
file per account so a second person on the same iPhone never sees the first one's data. The
restore only reads users/{signed-in uid}; nothing is uploaded in this step. No new permissions.

Step 5b-3g (reviewed 9 Nov): the iPhone calls Gemini only through Firebase AI Logic with App
Check (enforced for AI Logic), exactly like Android: no API key in the app or repo, billed to
the Superfit project. Photos are sent as JPEG and never stored. Raw AI errors stay in the logs;
people only see the shared plain messages. Android behaviour is unchanged (same models, retries
and messages, now from shared code with the same tests).

Step 5b-4a (reviewed 10 Nov): settings stay on the phone, one file (Android) or UserDefaults
suite (iPhone) per account, inside each app's own sandbox and not shared with other apps. Android
reads and writes exactly the keys it did before; Android's own device backup of preferences is
unchanged. No secrets are stored in settings (the old own-Gemini-key store is untouched and still
only clears itself). Nothing new is sent anywhere. No new permissions.

Step 5b-4b (reviewed 10 Nov): no change to what is stored or backed up, or where. Every write
still goes only to users/{signed-in uid}; signed out, nothing is uploaded (CloudBackup checks
the uid on every call). The new test library (bundled SQLite) is used by the computer tests only
and is not in either app. No new permissions.

Step 5b-5a (reviewed 10 Nov): display code only; no data, storage, network or permission changes.

Step 5b-5b (reviewed 10 Nov): display code and state types only; no data, storage, network or
permission changes.

Step 5b-5c (reviewed 10 Nov): the same sums on the same on-phone data, moved; nothing new is read,
stored or sent. No new permissions.

Step 5b-5d (reviewed 10 Nov): display code only; no data, storage, network or permission changes.

Step 5b-5e (reviewed 10 Nov): the same two anonymous events (meal_item_changed, meal_amount_changed)
with the same "how" values; still nothing about the food. Voice words stay on the phone until the
person taps Change, as before. No new permissions.

Step 5b-5f (reviewed 10 Nov): display code only; the same actions are wired to the same ViewModel calls.
No data, storage, network or permission changes.

Step 5b-5g (reviewed 10 Nov): from this step the iPhone writes too. Meals, edits, deletes and
weigh-ins made on the iPhone are saved in its own database and backed up to users/{signed-in
uid} only, exactly as Android does (CloudBackup checks the uid on every call). Meal text goes to
Gemini through Firebase AI Logic with App Check, as on Android. No new permissions.
