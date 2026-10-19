# One smart mic and a home-screen widget: design

Date: 2026-10-19. Approved in chat by Neetish (Phase 2, part 2 of "Making Superfit the natural choice"). Neetish: "this sounds super useful for me".

## Goal

Make logging take a few seconds:
- **One mic** that works out what you said without a switch.
- **A home-screen widget** that shows calories left and logs without hunting for the app.

## 1. One smart mic

### What the user sees
- They tap the mic and speak. Superfit decides whether it was food, exercise or both:
  - "2 roti and dal" logs a meal.
  - "ran 5k" logs a workout.
  - "had poha, then walked 30 minutes" logs both.
- The MEAL / WORKOUT switch on the Home card becomes a view tab labelled **FOOD | ACTIVITY**. It only changes which cards are shown below it, never what the mic does.
- Hint text under the mic: "Tap the mic and say what you ate or did" (plus ", or snap a photo" on the Food tab when a camera exists). While working: "Working it out…".
- Voice logs save straight away, as today. The banner names everything logged:
  - "Logged: poha"
  - "Logged workout: walk 30 min"
  - "Logged: poha · Workout: walk 30 min"
- If nothing recognisable was said: "Couldn't catch any food or exercise. Try 'two roti and dal' or 'ran 5k'."
- The typed meal box stays meal-only.
- The reminder notification's "Log now" action opens the mic as before.

### How it works
- **Exact repeat first.** `RepeatMealCache` is checked first. An exact repeat of one of the user's own past meals goes straight to the meal path with no AI call, as today.
- **Otherwise, one fast Gemini call splits the words** (the "router"). It returns `{"food": String, "exercise": String}`, each holding only the words about that, or "" if none. The text is copied from what was said, never rewritten or translated, and nothing is invented.
- **Each non-empty part then goes to the existing parser**, `NutritionParser` or `WorkoutParser`, run in parallel. These are unchanged, so meals keep their items, tags, swaps and editing.
- If the router fails because the AI is busy or offline, the friendly error is shown and nothing is logged.
- No hard-coded word lists decide food versus exercise. That is the AI's job (standing rule).

## 2. Home-screen widget

### What the user sees
- A Superfit widget, about 3×1 cells and resizable. On a rounded card in the app's colours it shows:
  - **Today:** "1,240 kcal left", or "180 kcal over" when past the target.
  - A small "of 2,100" under it.
  - Two round buttons: **mic** and **camera**. The camera button is hidden on phones without a camera.
- Tapping the mic opens Superfit on Home already listening, using the same path the reminder uses. Tapping the camera opens the food camera in Meal mode. Tapping the numbers opens Superfit.
- The numbers update right after any meal is logged, edited or deleted. A new day shows the full target until the first meal is logged.
- Before Superfit has ever calculated a target (a fresh install), the widget shows "Open Superfit to start" with both buttons.

### How people find it
1. **One-time tip on Home.** It appears after the user has 3 or more meals logged. It shows only if the phone supports pinning widgets, no Superfit widget is placed yet, and the tip hasn't been dismissed. Text: "Log faster: add Superfit to your home screen." Buttons: **Add**, which uses Android's own "add widget" prompt (`requestPinAppWidget`), and **Not now**, which hides the tip for good.
2. **Settings row** "Add home-screen widget" with the same Add action. It is hidden where pinning is unsupported.
3. **Release notes.**
4. **Android's widget list**, automatically.

### How it works
- Jetpack Glance (`androidx.glance:glance-appwidget`).
- The dashboard already computes today's target and calories eaten. Every time it does, it saves a small snapshot `{date, targetKcal, eatenKcal}` to SharedPreferences and asks the widget to refresh.
- The widget reads the snapshot. If the snapshot's date isn't today, it treats eaten as 0, so the full target shows.
- `updatePeriodMillis` is 30 minutes, so a new day shows within half an hour even if the app isn't opened.
- The mic intent reuses `com.superfit.app.ACTION_VOICE_LOG`. A new `com.superfit.app.ACTION_PHOTO_LOG` opens the food camera.

## Not in this release
- Logging workouts by photo.
- A typed box that routes to workouts.
- A lock-screen widget.
- A widget with a meal list.

## Testing
- **Unit tests:**
  - router JSON parsing: food only, exercise only, both, neither, malformed, code fences;
  - the widget's calories-left text: under, over, exactly on target, stale date, no snapshot.
- **CI:** existing tests, debug and release builds.
- **Dev app on Neetish's phone:**
  - "2 roti and dal", "ran 5k" and a sentence with both;
  - add the widget from the tip;
  - log from the widget mic and camera;
  - check the widget numbers update;
  - large text size.
