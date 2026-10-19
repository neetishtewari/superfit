# One Smart Mic and Home-Screen Widget Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:**
- One mic that logs food, exercise or both from a single sentence.
- A Glance home-screen widget showing calories left, with mic and camera buttons.

**Architecture:**
- **Smart mic.** A new `LogRouter` splits the spoken text with one Gemini call. `DashboardViewModel.smartLog` sends each part to the existing parsers.
- **Widget.** The dashboard writes a `WidgetSnapshot` to SharedPreferences. A Glance widget renders it, and its buttons launch `MainActivity` with intents.

**Tech Stack:** Kotlin, Compose, Jetpack Glance 1.1.1, Gemini via `GeminiClient`, JUnit + Robolectric.

**Spec:** `docs/superpowers/specs/2026-10-19-smart-mic-and-widget-design.md`

## Global Constraints

- No hard-coded word lists to tell food from exercise (standing project rule).
- An exact repeat of the user's own past meal (`RepeatMealCache.findRepeatMeal`) skips the router and logs the meal with no AI call.
- Exact copy:
  - Tabs: "FOOD", "ACTIVITY".
  - Hint text:
    - "Tap the mic and say what you ate or did" (add ", or snap a photo" on the Food tab when there is a camera)
    - "Working it out…"
  - Banners:
    - "Logged: {meal}"
    - "Logged workout: {workout}"
    - "Logged: {meal} · Workout: {workout}"
    - "Couldn't catch any food or exercise. Try 'two roti and dal' or 'ran 5k'."
  - Widget:
    - "{n} kcal left", "{n} kcal over", "of {target}"
    - "Open Superfit to start"
  - Tip: "Log faster: add Superfit to your home screen.", "Add", "Not now".
  - Settings: "Add home-screen widget".
- Intents: mic = `com.superfit.app.ACTION_VOICE_LOG` (existing); camera = `com.superfit.app.ACTION_PHOTO_LOG` (new).
- Widget `updatePeriodMillis` = 1800000. Tip shows at 3 or more meals logged.
- No database change.
- The cloud can't build Android, so CI is the verifier: push, then read the CI result.

## Review Focus

1. **The router returns text that isn't in what was said** (it paraphrases or invents). The prompt says to copy the words, and the parser trims each part. A part is empty when blank. Test in Task 1.
2. **One part fails and the other succeeds** (for example, the workout parser returns invalid). Log the part that worked and show only its banner. Never lose a good meal because the workout part failed. Handled in Task 2 by running each part in its own `runCatching`.
3. **Number formatting at large values.** Show "1,240 kcal left" with a thousands separator (Locale.US grouping); over target shows "180 kcal over". Test in Task 3.
4. **Widget tapped while the app is already open**: `onNewIntent` must trigger the mic or camera. `handleIntent` already runs from `onNewIntent`; ACTION_PHOTO_LOG is added there too. Task 4.
5. **The tip after the widget was removed again.** The tip stays dismissed once "Not now" was tapped. If the widget was added and later removed, the tip may show again; that's fine and needs no test.

---

### Task 1: LogRouter

**Files:**
- Create: `app/src/main/java/com/superfit/app/domain/LogRouter.kt`
- Test: `app/src/test/java/com/superfit/app/domain/LogRouterTest.kt`

**Interfaces:**
- Produces:
  - `data class RoutedLog(val food: String, val exercise: String)` with `val isEmpty: Boolean`.
  - `class LogRouter(apiKey: String) { suspend fun route(input: String): RoutedLog }`. It calls `GeminiClient.generateContent(apiKey, "Said: \"$input\"", SYSTEM_INSTRUCTION, isJson = true)`.
  - `companion object { val SYSTEM_INSTRUCTION: String; fun parse(json: String): RoutedLog }`.
  - `parse` strips ``` fences and returns `RoutedLog("", "")` on malformed JSON. It trims both fields and treats a missing field as "".

- [ ] **Step 1: Write failing tests**:
  - `parse` of `{"food":"2 roti and dal","exercise":""}` gives food "2 roti and dal", exercise "".
  - Exercise only works the same way.
  - Both fields filled gives both.
  - `{"food":"  ","exercise":""}` gives `isEmpty == true`.
  - `"not json"` gives `isEmpty`.
  - A body wrapped in ```json fences parses.
- [ ] **Step 2: Implement.** SYSTEM_INSTRUCTION says to:
  - split the user's words into the part about food or drink and the part about exercise or physical activity;
  - copy the user's own words, never rewrite, translate or add anything;
  - use "" for a part that isn't there;
  - return raw JSON `{"food": String, "exercise": String}`.
- [ ] **Step 3: Commit** with `feat: router that splits spoken logs into food and exercise`.

### Task 2: smartLog and the Food | Activity tab

**Files:**
- Modify: `app/src/main/java/com/superfit/app/ui/dashboard/DashboardViewModel.kt` (next to `parseAndAddMeal`, line 427)
- Modify: `app/src/main/java/com/superfit/app/ui/dashboard/DashboardScreen.kt` (speechHelper `onResult`, line 125: always call `smartLog`)
- Modify: `app/src/main/java/com/superfit/app/ui/dashboard/VoiceQuickLog.kt` (tab labels "FOOD"/"ACTIVITY"; hint text from Global Constraints; `logType` values stay "MEAL"/"WORKOUT" internally)

**Interfaces:**
- Consumes: `LogRouter`, `RepeatMealCache.findRepeatMeal(input, history)`, `NutritionParser`, `WorkoutParser`.
- Produces: `fun DashboardViewModel.smartLog(input: String)`.
  - Sets `_parsingState` to Loading.
  - Checks for an exact repeat first. If there is one, it calls the existing meal save path.
  - Otherwise it routes, then runs the meal and workout parts concurrently with `async`, each in `runCatching`.
  - It saves what succeeded, exactly like `parseAndAddMeal` and `parseAndAddWorkout` do. Extract their save bodies into `private suspend fun saveMeal(result, input)` and `saveWorkout(result)` so there is no duplication.
  - It sets `_parsingState` to Success with the combined banner text from Global Constraints. If nothing was logged, it shows the friendly error from the first failure; if there was no failure, it shows the "Couldn't catch…" text.
- `ParsingState.Success(text)` already drives the banner as "Logged: …". The banner composition moves into `smartLog`, so add `ParsingState.Success(message: String, isFullMessage: Boolean = false)`, or simply pass the full text. Pick the smaller change and keep the existing callers' banners identical.

- [ ] **Step 1: Implement**, then push and confirm CI is green.
- [ ] **Step 2: Commit** with `feat: one mic logs food, exercise or both`.

### Task 3: Widget snapshot and text

**Files:**
- Create: `app/src/main/java/com/superfit/app/widget/WidgetSnapshot.kt`
- Test: `app/src/test/java/com/superfit/app/widget/WidgetSnapshotTest.kt`

**Interfaces:**
- Produces:
  - `data class WidgetSnapshot(val date: String, val targetKcal: Int, val eatenKcal: Int)`.
  - `object WidgetSnapshotStore { fun save(context, snapshot); fun load(context): WidgetSnapshot? }`. It uses `getUserSharedPrefs(context)` with keys `widget_date`, `widget_target`, `widget_eaten`.
  - `data class WidgetText(val headline: String, val subline: String?)`.
  - `fun widgetText(snapshot: WidgetSnapshot?, today: String): WidgetText`:
    - null or target ≤ 0 gives ("Open Superfit to start", null).
    - A stale date means eaten is 0.
    - Left ≥ 0 gives "{n} kcal left"; otherwise "{n} kcal over".
    - Subline is "of {target}".
    - Use US thousands grouping.

- [ ] **Step 1: Write failing tests:**
  - (2100, 860, today) → "1,240 kcal left" / "of 2,100"
  - (2000, 2180) → "180 kcal over"
  - (2000, 2000) → "0 kcal left"
  - stale date (2000, 1500, yesterday) → "2,000 kcal left"
  - null → "Open Superfit to start" with a null subline
- [ ] **Step 2: Implement. Step 3: Commit** with `feat: widget snapshot and calories-left text`.

### Task 4: Glance widget, intents and dashboard updates

**Files:**
- Modify: `gradle/libs.versions.toml` (`glance = "1.1.1"`, `androidx-glance-appwidget`, `androidx-glance-material3`)
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/superfit/app/widget/SuperfitWidget.kt` (`GlanceAppWidget` + `SuperfitWidgetReceiver : GlanceAppWidgetReceiver`)
- Create: `app/src/main/res/xml/superfit_widget_info.xml` (minWidth 180dp, minHeight 60dp, resizeMode horizontal|vertical, updatePeriodMillis 1800000, widgetCategory home_screen, description string)
- Modify: `app/src/main/AndroidManifest.xml` (receiver with APPWIDGET_UPDATE and meta-data)
- Modify: `app/src/main/java/com/superfit/app/MainActivity.kt` (`handleIntent`: ACTION_PHOTO_LOG sets `triggerPhotoLog`, passed down like `triggerVoiceLog`)
- Modify: `app/src/main/java/com/superfit/app/Navigation.kt` and `DashboardScreen.kt` (`triggerPhotoLog`: when true, run `foodCameraController.reset()`, set `showFoodCamera = true`, then call the handled callback)
- Modify: `DashboardViewModel.kt`: after computing `DashboardUiState.Success`, call `WidgetSnapshotStore.save` with today's date, `macroTargets.calories.roundToInt()` and `caloriesEaten.roundToInt()`, then `SuperfitWidget.refresh(context)`. Do this only when the value changed, so refresh isn't spammed.

**Interfaces:**
- Produces: `object SuperfitWidget { suspend fun refresh(context: Context) }`. It calls `SuperfitWidgetImpl().updateAll(context)`.
- Widget UI:
  - Rounded background (app card colour) holding `widgetText`.
  - Two 44dp circular buttons with mic and camera icons. These use vector drawables `ic_widget_mic.xml` and `ic_widget_camera.xml` added under `res/drawable`.
  - The buttons use `actionStartActivity` with an `Intent(context, MainActivity::class.java)` carrying the action and `FLAG_ACTIVITY_SINGLE_TOP or FLAG_ACTIVITY_CLEAR_TOP`.
  - The camera button is hidden without `FEATURE_CAMERA_ANY`.

- [ ] **Step 1: Implement**, then push and confirm CI builds pass.
- [ ] **Step 2: Commit** with `feat: home-screen widget with calories left, mic and camera`.

### Task 5: Tip card, Settings row, release prep

**Files:**
- Create: `app/src/main/java/com/superfit/app/widget/WidgetPin.kt` (`fun canPin(context): Boolean`, `fun isPlaced(context): Boolean`, `fun requestPin(context)`)
- Modify: `DashboardScreen.kt` (tip card above the Food cards). Show it when:
  - `WidgetPin.canPin`,
  - `!isPlaced`,
  - pref `widget_tip_dismissed` is false,
  - and the all-time meal count is at least 3. Use `allNutritionHistory.size`, exposed on `DashboardUiState.Success` as `totalMealsLogged: Int`.
- Modify: `SettingsScreen.kt` (row "Add home-screen widget" when `canPin`)
- Modify: `app/build.gradle.kts` (versionCode 29, versionName "1.0.25") and add `notes/superfit-1.0.25-release-notes.txt` (under 500 characters).

- [ ] **Step 1: Implement**, then push and confirm CI is green.
- [ ] **Step 2: Commit** with `feat: widget tip and Settings row; version 1.0.25`.
- [ ] **Step 3: Get an independent review of the whole branch**, then fix the findings.
- [ ] **Step 4: Open the PR.** Install on the dev app for Neetish's checks from the spec. After Neetish's OK, merge, then build the signed bundle on the Mac.
