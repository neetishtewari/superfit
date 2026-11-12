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
       - 5b-5h: Today's Activity side on iPhone. The Food / Activity switch (FoodActivitySwitch),
         the per-day totals behind "vs your usual" (DayTotalsBuilder; Android's InsightsData
         hands its work to it) and the usual-weekday sums (TodayState.usualDay/vsUsual) move to
         shared. The iPhone shows steps and workouts next to the usual, voice-logged workouts
         (delete works) and sleep, from data already on the phone or restored from backup.
         Sharing a workout card waits for share cards; steps, sleep and other apps' workouts
         keep coming from Android's backup until Apple Health. Android shows the same as before.
       - 5b-5i: today's coach tip and step goal on iPhone. CoachInsightStore moves to shared (the
         account's settings, same keys), and TodayCoach.dailyTip/stepGoal share the tip and
         step-goal work; Android's Today and evening reminder call them. The iPhone shows the
         coach card ("Get today's tip"; chat comes with coach chat), the step goal on the rings
         with its sheet, and the "new step goal" note.
       - 5b-5j: the "log anything" logic and buttons move to shared, ready for iPhone voice and
         camera. SmartLogger (repeat meals, food/exercise split, both parsers, partial success
         messages) is what Android's mic logging now calls; TodayModel.smartLog uses it too. The
         Snap your plate / Say what you ate / Say your workout buttons move to shared
         (QuickLogCard.kt); the iPhone shows them once its voice and camera arrive (5b-6).
       - 5c-1: Progress starts on iPhone with its History page, and a bottom menu (Today,
         Progress; Coach joins with coach chat). HistoryLog (shared) works out the month
         calendar, the 30-day consistency, the 7-day trends and the picked day, the same sums
         Android's History used; Android's HistoryViewModel now calls it. The calendar, the
         "eaten vs what you burn" chart, the trend cards and the bottom menu move to shared. The
         iPhone shows the chart, the calendar and a read-only view of the picked day; editing
         past days, Insights and Journey come next. The share offer, new-best and widget-tip
         cards on Today wait for Apple Health, share cards and the widget (5b-6).
       - 5c-2: the picked day's card (DayBalanceCard) moves to shared, with the same edit, delete
         and "log a missed meal" as Android; HistoryModel does them on iPhone and
         HistoryLog.missedMeal times a late meal at midday for both apps. Share buttons are
         hidden on iPhone until share cards arrive.
       - 5c-3: Insights and Journey move to shared (InsightsTab, JourneyTab, the body cards and
         the goal dialog), with GoalDatePref now on shared Prefs and kotlinx dates. ProgressLog
         (shared) works out the week report, the journey forecast, the body summary and trend
         lines, the same sums Android's HistoryViewModel used; Android now calls it. The iPhone
         Progress page gets the Insights | Journey | History switch, weigh-ins and goal edits.
         Personal bests and the coach report wait for share cards and the iPhone PDF; scale
         readings (body fat and more) wait for Apple Health.
       - 5d-1: Settings, first part. The account card, "Your plan" (goal, step goal, own
         calorie and macro targets), Appearance and Sign out / Delete account move to shared
         cards (ui/settings); Android's Settings uses them. The iPhone gets a Settings page from
         the gear on Today with the account, your plan, Appearance (Auto, Light, Dark, kept for
         the whole app as on Android) and Sign out. Health data, the AI coach, reminders, the
         widget, sharing, privacy and deleting the account follow with their iPhone parts.
       - 5d-2: Settings, second part. "My nutritionist's advice" moves to shared (its mic shows
         where the phone has voice typing) and the once-a-day re-rating of recent meals becomes
         shared MealTagBackfill; Android calls both. The iPhone Settings page gets the advice box
         and the AI coach style, and the iPhone now loads the person's coach style and advice
         (with confirmed targets and "Fine for me" foods) at sign-in, as Android does at start.
         "What my coach knows" joins with coach chat.
     - 5b-5: ViewModels (lifecycle-viewmodel multiplatform) and screens move one at a time:
       login, onboarding, Today, History/Progress, Settings, coach chat, recap, share cards. Each
       move ships in an Android release first (phone-checked), then lights up on iPhone.
     - 5b-6: iPhone-only parts behind small shared interfaces: camera, barcode and label reading
       (AVFoundation/Vision), voice (Speech framework), Apple Health in place of Health Connect,
       notifications and daily reminders, background refresh, home-screen widget (WidgetKit,
       SwiftUI), share sheet, PDF report, recap video with music, in-app review, analytics and
       crash reports.
       Order (Neetish, 11 Nov): Apple Health, voice, camera and barcode, then the widget,
       reminders and the PDF report, before the remaining screens.
       - 6a: Apple Health, first part. Steps, active calories, walking distance and sleep are
         read from Apple Health (HealthKit, read only) by Swift (HealthKitBridge) through a small
         shared HealthSource; shared HealthSync saves today and yesterday the way Android's sync
         does, and never saves an empty or failed read over a saved day. Shared SleepNight joins
         Apple Health's many sleep pieces (watch and sleep app overlapping) into the night that
         ended that day, deep sleep estimated at a fifth as on Android. Today's Activity side
         asks to connect first with a short explanation, then Apple's own sheet; Settings has
         an Apple Health section. Read again at every app open and return to the front (no
         background reads yet). On-screen "Health Connect" becomes the phone's own health app
         name. Workouts from other apps (6b) and smart-scale body data (6c) follow.
       - 6b: Apple Health, workouts. Workouts the watch, Strava or any app saved to Apple
         Health come into imported_workouts through shared WorkoutSync (90 days the first time,
         then the last week; deleted there, gone here), with distance, active calories and
         average heart rate, named after the app or watch that recorded them. Apple's workout
         kinds map onto the same numbers Android uses (AppleWorkoutTypes), so titles, paces,
         doubles across apps, "vs your usual", History, Insights and the calorie add-back work as
         on Android. Connecting Apple Health turns the import on; people who connected before see
         the card once more for the new part, and their steps and sleep keep coming in meanwhile.
         Hiding a workout works as on Android; the import's settings page (main app, add-back)
         joins with the rest of Settings.
       - 6c: Apple Health, weight and body. Weigh-ins with body fat and lean mass from a smart
         scale (or typed into the Health app) come in through shared ScaleImport, the same rules
         as Android's BodyImporter: a reading close to the person's own weight goes into the
         weight log ("from Withings"), one far from it (or the first with body fat) waits for
         "Is this you?" on Today, and "Not me" sets a partner's readings aside from then on. Body
         fat sets the calorie target as on Android and feeds the Journey Body card. Apple Health
         has no bone mass or scale BMR, so those stay Android-only.
       - 7: Voice. Today's "Say what you ate" (Food) and "Say your workout" (Activity) mics,
         the same buttons as Android, through shared VoiceLog on top of a small VoiceRecognizer
         that Swift provides with Apple's Speech framework (SpeechBridge). The same behaviour as
         Android's: listens through pauses with live words, finishes 2 s after the last word or
         after a minute, "Checking what you said…" waits up to 1.5 s for the corrected words, and
         up to 5 of Apple's guesses go to the AI (SpokenInput) so it can pick the one that makes
         sense as food. The phone's language and region are used (Indian English on an Indian
         English phone). A refused mic shows the same "Microphone is off" note with Open
         Settings. The mics in meal editing ("Say what it was") and "My nutritionist's advice"
         show on iPhone too, with a small Listening window, since iPhone has no voice-typing box.
         Results slide in at the top as on Android. Camera, barcode and label reading follow.
       - 8: Camera and barcode. "Snap your plate" on Today's Food side offers Meal, Barcode or
         Label, the three modes of Android's food camera. Apple's own camera takes the meal or
         label photo (CameraBridge; the simulator picks a library photo instead), shrunk to
         1280 px and sent to the AI once through shared FoodPhotoParser; Apple's live barcode
         scanner (VisionKit) reads packets, looked up in Open Food Facts through shared
         OpenFoodFacts (Android now uses its barcode forms too). Shared PhotoLog has Android's
         words for working, not found ("Snap its nutrition label instead") and failures, and the
         check sheets (CheckMealSheet, CheckPackSheet) move to shared, so nothing is saved until
         Log meal, as on Android. Saved meals get quality tags the same way.
       - 9: App icon. The Play Store's 1024 px Superfit icon (store/app_icon.png) becomes the
         iPhone's home-screen icon through an asset catalog (Assets.xcassets/AppIcon), so the
         app no longer shows a blank grid. It is cropped to the inner square, so iOS's own
         rounded mask doesn't show a box inside a box.
       - 10: Home-screen widget. Like Android's widget: today's calories left ("1,240 kcal
         left", "of 2,100", or "230 kcal over"), rounded like the Home ring, back to the full
         target at midnight, and "Open Superfit to start" before the first open or after sign
         out. Medium size adds the mic and camera buttons, which open Today on the Food side and
         start listening for a meal or open the camera for one. The small size and the rest of
         the widget just open Superfit, since Apple allows only one tap area on small widgets.
         Lock-screen versions show the same line. A WidgetKit extension (SuperfitWidget) reads
         the numbers that Today saves in an app group (WidgetBridge), and the app asks for a
         redraw only when they change.
       - 11: Daily reminder. Settings › Daily reminder as on Android (on by default, 9 PM,
         "Remind me" and the time). The iPhone doesn't let apps run at a set time, so shared
         ReminderPlan writes the next 7 evenings ahead as Apple notifications (ReminderBridge)
         and writes them again whenever today's log, today's coach tip or the settings change.
         Same rules and words as Android: under 75% of the target logged gets "Complete your
         food log?", a full day gets today's coach tip from Home (or "Daily log complete").
         Tapping it, or "Say what you ate", opens Today listening. Apple asks to allow
         notifications once, after sign-in; if they're off, Settings says so and opens the
         phone's settings. Signing out removes them. The monthly recap's notification comes with
         the recap.
       - 12: Share my report. Progress › Insights › "Share my report" (2 or 4 weeks), the same
         PDF as Android. Its layout moved to shared code (CoachReportPdf on a small PdfSurface),
         so both apps draw the same pages; Android's PdfDocument drawing is now that surface,
         and the iPhone's is Apple's PDF renderer (ReportBridge) in the bundled Manrope with the
         app icon, then Apple's share sheet. The report's numbers come from shared
         CoachExportBuilder via HistoryModel.buildReport, as on Android. The footer links to
         Google Play until Superfit is on the App Store (then StoreLink gets the App Store link).
       - 13: Coach chat. The Coach tab on the iPhone, the same chat as Android: one running
         conversation kept between visits (a file per account, backed up to
         users/{uid}/coach/chat), ready-made questions (Roast chip for Roast me), the mic sends the
         question when the person stops talking, "Tell me more", "Log this" on suggested meals,
         and "Remembered: … Undo" when the coach picks out a note. The chat screen moved to shared
         CoachChatView (Android's screen now only runs Android's mic) and the instructions to
         shared CoachChatPrompt, so both apps ask the coach the same way. The iPhone's
         CoachChatModel mirrors Android's chat logic. Workouts from Apple Health now reach the
         coach (chat and daily tip) as on Android (WorkoutSync.coachEntry). Settings › AI coach
         adds "What my AI coach knows" (shared CoachMemoryCard: notes, add, remove, Clear chat
         history). Sharing a roast as a card comes with share cards.
       - 14: Share cards. The share screen (card design, colours, Light/Dark, your own photo
         with pinch and drag, chips, the coach line or roast, "Roast again") moved to shared
         ShareCardSheet/ShareCardViews, with the saved choices (ShareCardPrefs), the AI lines
         (RoastCardData, WorkoutCardData) and the strength tags (StrengthTags) beside it. Each
         phone gives a ShareCardHost: Android's gallery picker, share sheet and toasts as before;
         the iPhone's ShareBridge.swift uses Apple's photo picker (no photo-library access needed)
         and Apple's share sheet. Share buttons on iPhone: the streak card (today's card), every
         workout on Activity and in History, the "ready to share" run or walk card on Today, and
         any roast in the coach chat. Workout cards read heart rate from Apple Health during the
         workout (the watch or app that recorded it first), as Android does from Health Connect.
         The "new personal best" card and Settings › Sharing come with the rest of Settings.
       - 15: Onboarding (moved ahead of the monthly recap, Neetish 12 Nov). A new account on the
         iPhone sets up its profile on the phone with the same six steps as Android (welcome,
         health app, about you, goal, pick your coach, your plan), now shared OnboardingScreen and
         OnboardingModel. Each phone names its own health app (Health Connect, Apple Health) and
         shows its own access sheet; "About you" fills height and weight from it (the iPhone now
         also reads height). Android keeps "Read aloud" (its text to speech); the iPhone hides
         the speaker for now. Back on the first step signs out on iPhone, for a wrong account.
       - 16: Rest of Settings. Settings › Account shows the name and how the account signs in
         (Google, Apple or email). Apple Health gets the workouts-from-other-apps choices (shared
         WorkoutImportCard: on/off, main workout app, add workout calories Off/Half/All, show
         hidden workouts), backed up like Android's. Settings › Sharing (shared
         SharingSettingsCard). A home-screen widget card says how to add it on iPhone. Delete
         account (the App Store requires it): the cloud backup first, then the account, then
         everything this account kept on the phone (its database, chat and settings), as on
         Android; a Sign in with Apple account confirms with Apple once more so Apple's link to
         Superfit is revoked too. Today now offers a "New personal best" card and Progress lists
         every personal best with Share, from shared TodayModel.personalBests. The usage and crash
         reports switch comes with analytics.
       - 17: Usage and crash reports. The events move into shared UsageEvents (Android's
         AppAnalytics now only switches Firebase on/off and sends them), so both phones count the
         same things: meals logged by voice/typed/photo/barcode/label, edits, weigh-ins, goals,
         coach messages and notes, share cards and offers, report shared, step goal, coach style
         and which tab or screen was opened. The iPhone sends them with Firebase Analytics and
         Crashlytics (UsageReportsBridge.swift); a crash in the shared Kotlin code is noted with
         its Kotlin stack before the app closes. Settings › Privacy has the same "Share anonymous
         usage and crash reports" switch on both phones (shared UsageReportsCard). Release builds
         upload their symbol file to Crashlytics. Not on iPhone: "widget added" (iOS doesn't say
         when a widget is added) and the speech-service key (Apple has one).
         Store readiness: the iPhone's share cards, share text and report footer name the App
         Store instead of Google Play (shared ThisAppStore; the link is added once App Store
         Connect gives the app its id, until then the label shows without a link); Apple's
         privacy manifests (PrivacyInfo.xcprivacy for the app and the widget); version 1.0.0.
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

Step 5b-5h (reviewed 11 Nov): the same sums on the same on-phone data, moved; the iPhone adds
deleting a voice-logged workout, which removes it from users/{signed-in uid} only, the same call
Android makes. Nothing new is read, stored or sent. No new permissions.

Step 5b-5i (reviewed 11 Nov): the same tip request as Android, through Firebase AI Logic with App
Check; it carries the same history summary Android already sends. The tip and step goal are saved
in the account's own settings on the phone (same keys as Android), nothing new is backed up. No
new permissions.

Step 5b-5j (reviewed 11 Nov): the same logging logic, moved; the same text goes to the same AI
calls (Firebase AI Logic with App Check) and is saved the same way. No new permissions; the mic
and camera stay Android-only for now.

Step 5c-1 (reviewed 11 Nov): the same sums on the same on-phone data, moved; the iPhone's History
page only reads the account's own database. Nothing new is stored, backed up or sent. No new
permissions.

Step 5c-2 (reviewed 11 Nov): on iPhone, editing, deleting and logging a missed meal from History
use the same calls as Today (5b-5g): saved in the account's own database and backed up to
users/{signed-in uid} only; the typed meal goes to Gemini through Firebase AI Logic with App
Check, as on Android. No new permissions.

Step 5c-3 (reviewed 11 Nov): the same sums on the same on-phone data, moved. On iPhone a weigh-in
and a goal edit use the same calls as Android: saved in the account's own database and settings,
and the weigh-in and profile backed up to users/{signed-in uid} only. "Fine for me" is kept in
the account's own settings, as on Android. Nothing new is sent to the AI or anywhere else. No new
permissions.


Step 5d-1 (reviewed 11 Nov): the same settings under the same keys, kept in the account's own
settings on the phone (theme in the app's settings); a goal change saves the profile, backed up to
users/{signed-in uid} only, as on Android. Signing out on the iPhone is the same Firebase sign-out
as before. Nothing new is read, stored or sent. No new permissions.

Step 5d-2 (reviewed 11 Nov): the same settings under the same keys, kept on the phone. The advice
text goes to Gemini through Firebase AI Logic with App Check only when the person saves it (to
read targets) and inside the meal-rating prompt, exactly as on Android; re-rated tags are saved to
the account's own database and backed up to users/{signed-in uid} only. Targets from the advice
are used only after the person confirms them. No new permissions.

Step 6a (reviewed 11 Nov): Apple Health is read only: Superfit asks to read steps, active energy,
walking distance and sleep, and asks to write nothing. Access is asked only after the person taps
Connect, with the reason shown first and in Apple's sheet (NSHealthShareUsageDescription). What is
read stays in the account's own database on the phone; day totals (steps, active calories) and the
night's sleep length are backed up to users/{signed-in uid} only, exactly what Android already
backs up from Health Connect. Distance is shown, not stored. Nothing from Apple Health goes to the
AI beyond the same day summaries Android's coach already uses, and nothing is shared or used for
ads (App Store rule 5.1.3). An empty or refused read never overwrites a saved day, so a second
phone on the same account can't wipe steps. The entitlement is HealthKit only (no clinical
records). No new Android permissions; Android unchanged apart from the shared text naming its
health app.

Step 6b (reviewed 11 Nov): still read only. Superfit now also asks to read workouts, cycling and
swimming distance and heart rate; heart rate is read only as each workout's average. Workouts stay
in the account's own database on the phone and are not backed up, exactly as Android keeps Health
Connect workouts; the app and watch names are kept in the account's own settings on the phone.
Nothing new goes to the AI or anywhere else. No new Android permissions; Android unchanged.

Step 6c (reviewed 12 Nov): still read only; adds weight, body fat and lean mass. Accepted weigh-ins
go into the weight log and are backed up to users/{signed-in uid} only, as Android does with Health
Connect weigh-ins; the scale lists (mine, not me, waiting) and the last body fat stay in the
account's own settings on the phone. Readings that may be someone else's are never added without
the person's answer. Nothing new goes to the AI or anywhere else. Android unchanged.

Step 7 (reviewed 12 Nov): asks for the microphone and speech recognition only when a mic button is
first tapped, with the reasons in Apple's sheets (NSMicrophoneUsageDescription,
NSSpeechRecognitionUsageDescription). The microphone is on only while the button shows Listening,
and stops after a minute at most. Sound goes to Apple's speech recognition (as it goes to Google's
on Android) and is never recorded, kept or sent to Superfit or Firebase; only the words go on, to
Gemini through Firebase AI Logic with App Check, exactly as typed words do. Nothing new is stored
or backed up. No new Android permissions; Android unchanged.

Step 8 (reviewed 12 Nov): asks for the camera only when it first opens, with the reason in
Apple's sheet (NSCameraUsageDescription). Photos are shrunk, sent once to Gemini through Firebase
AI Logic with App Check, and never saved to the phone's photos, the database or the backup. Only
the barcode number goes to Open Food Facts (over HTTPS, a plain app name as User-Agent, no
account or device details). The simulator's photo picker needs no library permission. What is
logged is saved and backed up like any meal. No new Android permissions; Android's camera is
unchanged apart from the check sheets now coming from shared code.

Step 9 (reviewed 12 Nov): the app icon only adds an image to the app bundle. There is no new
permission, data, network call or code.

Step 10 (reviewed 12 Nov): the widget sees only today's date, calorie target and eaten total. They
sit in an app group that only Superfit's app and widget can read, and they are cleared on sign
out, so the next person never sees someone else's numbers (a known gap on Android). The widget
has no network access and no other data. The mic and camera links (superfit://voice,
superfit://camera) carry a random key made on the phone and kept in that app group. Another app
or a web page can open Superfit with a superfit:// link, but without the key the app only opens:
it never starts listening or opens the camera. Listening still shows Apple's mic indicator, as
always. No new permissions; Android unchanged.

Step 11 (reviewed 12 Nov): reminders are local notifications made on the phone. Nothing is sent
anywhere, no server push and no new data. They contain only today's calories and the coach tip
already on Home, and they are removed on sign out. Apple's notification prompt is the only new
permission, and it can be refused: the reminder just doesn't show. A reminder tap can only start
listening on Today (Apple's mic indicator shows), as the widget's mic does. Android unchanged.

Step 12 (reviewed 12 Nov): the PDF is made on the phone from the person's own data, saved in the
app's temporary folder with file protection (replaced each time), and leaves the phone only
through the share sheet, where the person picks where it goes. Nothing is uploaded and there are no
new permissions. The name at the top comes from the signed-in account, as on Android. Android's
PDF is drawn by the same shared layout, with the same fonts, colours, positions and Play Store link.

Step 13 (reviewed 12 Nov): the chat and the coach's notes are the person's own and stay under
their account: a file in the app's own folder named after their account, the notes in their
account's settings, and the backup in their own users/{uid}/coach documents (the same Firestore
rules as everything else). Questions go to Gemini through Firebase AI Logic with App Check, with
the person's own logs, exactly as on Android; nothing else leaves the phone. A long paste is cut
to 4,000 characters (notes to 3,000). Clear chat history deletes the file and the backup copy;
notes stay until removed. The mic is the same as Today's (Apple's indicator shows, nothing
recorded). No new permissions on either phone; Android's chat works as before, its screen now
drawn by the shared view.

Step 14 (reviewed 12 Nov): a share card is drawn on the phone and only leaves where the person
sends it. The picture is written to the app's own temporary folder (Android: its cache, shared
through its own FileProvider; iPhone: a "share-cards" temporary folder with complete file
protection), replaced each time. A background photo is picked with the phone's own picker, which
hands over only that one photo; it is held in memory while the share screen is open and never
saved or uploaded. Heart rate is read from the health app only when a workout card opens, for that
workout's time, and never stored (on iPhone it uses the heart-rate read access already asked for).
The coach line and roast go to Gemini through Firebase AI Logic with App Check, with only the
card's own facts, as before. The iPhone adds one Info.plist line: "Save Image" on the share sheet
may ask to add that picture to Photos (NSPhotoLibraryAddUsageDescription); the app never reads the
photo library. No new Android permissions.

Step 15 (reviewed 12 Nov): sign-up saves the profile on the phone and in the person's own
users/{uid} backup, as on Android. Height and weight are read once from the health app, only after
the person allows it, and only fill the boxes. The iPhone asks Apple Health for one more read-only
type (height), named in the access sheet's description. No new Android permissions.

Step 16 (reviewed 12 Nov): Delete account removes the person's users/{uid} data in the cloud before
the account (it can't be reached afterwards; offline, nothing is deleted and they stay signed in),
then the Firebase account, then the phone's copy (database file, chat file, account settings). For
Sign in with Apple, Apple's sheet confirms the person and the app revokes its Apple token with the
one-time code (Apple's rule for account deletion); the code is used once and never stored. Workout
choices go to the same users/{uid} backup document as Android's. No new permissions on either phone.

Step 17 (reviewed 12 Nov): usage counts carry only how something was done or which screen opened,
never food, numbers, health data, photos, names or email (UsageEvents is the one list for both
phones). Both phones keep the person's switch (on unless turned off) and never report from the dev
app (Android: .debug package; iPhone: Debug builds). On iPhone, Analytics and Crashlytics start
switched off in Info.plist and are only turned on once the switch has been checked, so nothing is
sent before that; no advertising ID is collected (no AdSupport, no tracking prompt,
GOOGLE_ANALYTICS_ADID_COLLECTION_ENABLED off) and ad personalisation signals are off. Crash
reports and handled errors include the error message and stack, as on Android. The symbol upload
uses the Firebase config already in the app, no new key. No new permissions on either phone. App
Store privacy labels (before the first App Store submission, Neetish): Usage data (product
interaction) and Diagnostics (crash data), plus Device ID (Firebase's install ID), not linked to
identity, not used for tracking; this matches Android's Data safety entry.
The privacy manifest declares the same data as the App Store labels (name, email, user ID, health,
fitness and the person's own logs for the app to work, linked to the account; usage, crash data
and the Firebase install ID for analytics, not linked; nothing for tracking) and why the app uses
settings storage (its own and the widget's shared group), file dates and the phone's uptime.
