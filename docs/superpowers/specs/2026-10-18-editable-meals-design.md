# Editable meals (Phase 1) — design

Date: 2026-10-18. Approved in conversation by Neetish; this spec records it.

## Goal

Voice logging stays exactly as it is. After a meal is logged, the user can make small fixes:
change the amount of any item, and set oil and sugar to Low / Normal / High. Numbers update
instantly on the phone with no AI call. Same release: friendly AI error messages, quick weight
logging on Home, and removal of the AI Trainer workout card.

Out of scope: fixing a meal by voice, changing which dish an item is, adding items to a logged
meal, photo logging (Phase 2).

## 1. Meal items

### Data

New nullable column `nutrition_entries.items TEXT` (Room v6 → v7, `ALTER TABLE ADD COLUMN`).
Existing rows keep `NULL`; nothing else in the table changes.

`items` holds a small JSON document, owned by a new `domain/MealItems.kt`:

```json
{"v":1,"oil":"NORMAL","sugar":"NORMAL","items":[
  {"name":"roti","qty":2,"unit":"piece","kcal":120,"p":3.5,"c":20,"f":3,"fib":2.5,"oil":2,"sug":0}
]}
```

- Per-item numbers are **per one unit** at Normal oil and sugar, so a quantity change is a
  multiplication. `oil` and `sug` are grams of added oil/ghee/butter and added sugar per unit.
- The meal's existing `calories`, `proteinG`, `carbsG`, `fatG` columns stay the source of truth for
  every other part of the app (rings, coach, Diet Quality, history, streak, backup). Editing
  rewrites them from the items, so no other code has to read `items`.

### Recalculation (pure function, unit tested)

For each item: `value × qty`, summed. Then oil and sugar:

- factor: Low 0.5, Normal 1.0, High 1.5
- `extraFat = (oilFactor − 1) × Σ(oil × qty)`; fat += extraFat; kcal += 9 × extraFat
- `extraSugar = (sugarFactor − 1) × Σ(sug × qty)`; carbs += extraSugar; kcal += 4 × extraSugar
- Nothing goes below zero. Values are rounded to one decimal.

Meal tags (`qualityTags`) follow the edit: fibre becomes the items' fibre total, and veg/fruit
servings and the swap's calories saved scale with the change in calories (`MealTags.scaled`). The
AI's judgements (fried, treat, processing) don't change.

`foodText` is rebuilt from the items after an edit, e.g. "3 roti, 1 katori dal".

### Parsing

The Gemini meal prompt (`NutritionParser`) also asks for `items`: name (short English), quantity,
unit (piece, katori, bowl, cup, glass, slice, tbsp, tsp, g, ml, serving), calories, protein,
carbs, fat, fibre, added oil grams and added sugar grams for that quantity. The parser divides by
quantity to store per-unit values.

When the items list is valid (non-empty, every quantity > 0, total calories > 0), the meal's totals
are the item sums so items and totals always agree. Otherwise the meal is saved exactly as today
with `items = NULL`.

The repeat-meal shortcut copies the past meal's items (quantities scaled when it scales the meal),
so repeats stay editable.

### Older meals

A meal with `items = NULL` opens as one item: its name, "1 serving", and its current totals as
the per-unit values. The oil and sugar switches are hidden for it because no oil or sugar
estimate exists. Saving an edit writes an `items` document for it.

### Edit sheet

Tapping a meal row (Home's today list and History's day list) opens a bottom sheet:

- one row per item: name, "2 piece", its calories, and − / + buttons
  - step 0.5 for count units (piece, katori…), 25 for g and ml; minimum is one step
  - a remove button per item while more than one item remains
- Oil: Low / Normal / High, and Sugar: Low / Normal / High (only when items carry estimates, and
  only shown if the meal has some oil or sugar respectively)
- a live total line: kcal, P, C, F
- Save and Cancel. Save updates the Room row (`@Update`) and re-uploads it to the cloud backup
  under the same id. Cancel changes nothing.

The trash icon on the row still deletes the whole meal.

### Backup

`FirebaseSyncManager` writes `items` when present and reads it back when present. Older backups
without it restore with `items = NULL`. Restore stays additive.

## 2. Friendly AI errors

- Remove both "Open API Settings" buttons from the meal error banners (`DashboardSections.kt`).
- Add a "Try again" button that re-sends the last meal text.
- `GeminiClient.sanitizeError` only says "your own key in Settings isn't working; remove it to use
  Superfit's built-in AI" when the user actually set their own key. Otherwise a key-type error
  shows the plain "AI assistant is unavailable right now" message.
- The Settings icon's accessibility label becomes "Settings".

## 3. Weight on Home

A compact row on Home, under the nutrient card: latest weight and how long ago, plus a "Log
weight" button that opens the existing `WeighInDialog` and calls the existing `logWeight`.

## 4. Remove the AI Trainer card

Delete `WorkoutRecommenderEngine`, `WorkoutRecommendationCard`, the dashboard state fields, and the
Settings toggle and preference for it. Manual workout logging and the Activity card are unchanged.

## Testing

- Unit tests: `MealItems` encode/decode round trip, bad JSON → null, recalculation including oil and
  sugar levels and the no-negative rule, legacy single-item fallback, foodText rebuild, tag scaling.
- Unit test: parser JSON with items → per-unit items and matching totals; missing or broken items
  → today's behaviour.
- Unit test: repeat-meal cache carries and scales items.
- Migration test: every old version opens at v7 with `items` NULL and data intact.
- CI: unit tests, debug build, release compile, committed `app/schemas/7.json`.
- Neetish checks on the dev app: log "2 roti and dal", change to 3 roti, set oil High, and confirm
  the rings, coach and history update. Then open an older meal and scale it.
