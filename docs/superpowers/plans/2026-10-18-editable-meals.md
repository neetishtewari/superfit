# Editable Meals Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users fix a logged meal's item amounts and oil/sugar level instantly, plus friendly AI errors, weight on Home, and removal of the AI Trainer card.

**Architecture:** A pure `MealItems` model (JSON in a new nullable `nutrition_entries.items` column) owns parsing, encoding and recalculation. Editing rewrites the meal's existing total columns, so the rest of the app is untouched. A shared Compose bottom sheet edits meals from Home and History.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Room (v6 → v7), org.json, JUnit + Robolectric.

**Spec:** `docs/superpowers/specs/2026-10-18-editable-meals-design.md`

## Global Constraints

- Oil/sugar factors: Low 0.5, Normal 1.0, High 1.5. 9 kcal per g of oil (fat), 4 kcal per g of sugar (carbs).
- Steps: 0.5 for count units, 25 for `g` and `ml`; minimum quantity is one step.
- No hard-coded food word lists anywhere (standing rule).
- Gradle can't run in the cloud sandbox; every task's verification is the CI run (`testDebugUnitTest`, `assembleDebug`, `compileReleaseKotlin`, schema check). `app/schemas/7.json` is taken from the CI log on the first push.

## Review Focus

1. Gemini returns items whose sums disagree wildly with its own totals, or zero-calorie items → item sums are used only when valid; otherwise `items = NULL` (test in Task 2).
2. An older meal with no items is edited → it becomes one "1 serving" item; oil/sugar hidden (test in Task 1).
3. Setting Low on a meal with no oil, or many reductions → no value goes below zero (test in Task 1).
4. A cloud backup written before this release is restored → `items` is null, nothing crashes (Task 3, Firebase map read is null-safe).
5. A repeat meal of an edited meal scales its items, not only totals (test in Task 2).

---

### Task 1: `MealItems` model and recalculation

**Files:**
- Create: `app/src/main/java/com/superfit/app/domain/MealItems.kt`
- Test: `app/src/test/java/com/superfit/app/domain/MealItemsTest.kt`

**Interfaces — Produces:**
- `enum class Level(val factor: Double) { LOW(0.5), NORMAL(1.0), HIGH(1.5) }`
- `data class MealItem(name: String, qty: Double, unit: String, kcal: Double, protein: Double, carbs: Double, fat: Double, fiber: Double, oilG: Double, sugarG: Double)` — nutrient fields are per one unit; `val step: Double` (25.0 for g/ml else 0.5); `fun label(): String` ("2 roti", "1.5 katori dal", "150 g rice").
- `data class MealItems(items: List<MealItem>, oil: Level = NORMAL, sugar: Level = NORMAL, hasEstimates: Boolean = true)`:
  - `fun totals(): MealTotals` (`MealTotals(kcal, protein, carbs, fat, fiber)`), rounded to 1 decimal, never negative.
  - `val hasOil: Boolean` / `val hasSugar: Boolean` (estimates present and Σ > 0).
  - `fun foodText(): String` — item labels joined with ", ".
  - `fun encode(): String`; `companion fun decode(raw: String?): MealItems?` (null on null/garbage/empty list).
  - `companion fun legacy(entry: NutritionEntryEntity): MealItems` — one item `entry.foodText`, qty 1, unit "serving", totals as per-unit values, fibre from tags, `hasEstimates = false`.
  - `companion fun fromGemini(arr: JSONArray?): MealItems?` — per-quantity values divided by qty; null unless non-empty, every qty > 0 and Σ kcal > 0.
- `fun NutritionEntryEntity.withItems(items: MealItems): NutritionEntryEntity` — rewrites foodText, totals, `items` and `qualityTags` (tags `scaled(newKcal/oldKcal)` when old kcal > 0, then `fiberG` = items fibre).

- [ ] **Step 1: Write failing tests** (`MealItemsTest`):
  - `totalsMultiplyPerUnitByQuantity`: roti (kcal 120, p 3.5, c 20, f 3, fib 2.5, oil 2) qty 2 → kcal 240, protein 7, fat 6.
  - `highOilAddsHalfTheOilAsFatAndNineKcalPerGram`: same roti qty 2, oil HIGH → fat 6 + 2 = 8, kcal 240 + 18 = 258.
  - `lowSugarRemovesHalfTheSugar`: chai (kcal 80, c 12, sugarG 8) qty 1, sugar LOW → carbs 8, kcal 64.
  - `neverNegative`: item with kcal 5, fat 0.5, oilG 4 at LOW → fat ≥ 0, kcal ≥ 0.
  - `encodeDecodeRoundTrip`, `decodeGarbageIsNull` ("{", "", null, `{"items":[]}`).
  - `legacyMealIsOneServingWithoutEstimates`: hasOil false, hasSugar false, totals equal the entry's.
  - `fromGeminiDividesByQuantityAndRejectsBadLists`: qty 2 kcal 240 → per-unit 120; qty 0 or empty → null.
  - `foodTextIsRebuiltFromItems`: "3 roti, 1 katori dal".
  - `withItemsRewritesTotalsAndScalesTags`: tags fibre becomes item fibre; vegFruit scales by kcal ratio.
  - `stepIs25ForGramsAndHalfOtherwise`.
- [ ] **Step 2: Implement `MealItems.kt`.** JSON keys as in the spec (`v, oil, sugar, items[name, qty, unit, kcal, p, c, f, fib, oil, sug]`, plus `est` for hasEstimates).
- [ ] **Step 3: Commit** `feat: add MealItems model for editable meals`.

### Task 2: Database column, parser and repeat meals

**Files:**
- Modify: `data/Entities.kt` (`NutritionEntryEntity.items: String? = null`), `data/SuperfitDatabase.kt` (version 7, `MIGRATION_6_7` = `ALTER TABLE nutrition_entries ADD COLUMN items TEXT`, added to `ALL_MIGRATIONS`)
- Modify: `domain/NutritionParser.kt` (prompt adds `items`; `ParsedNutritionResult.items: MealItems? = null`; extract JSON handling into `companion fun fromJson(json: JSONObject, input: String): ParsedNutritionResult`)
- Modify: `domain/RepeatMealCache.kt` (copy `MealItems.decode(entry.items)`; scale every item's qty by `scaleFactor`)
- Modify: `ui/dashboard/DashboardViewModel.kt`, `ui/history/HistoryViewModel.kt` (save `items = result.items?.encode()`)
- Test: `SuperfitDatabaseMigrationTest` (expect version 7, `items` NULL), new `NutritionParserTest`, `RepeatMealCacheTest`

**Interfaces — Consumes:** Task 1. **Produces:** `NutritionEntryEntity.items`, `ParsedNutritionResult.items`.

- [ ] **Step 1: Tests:** migration asserts `db.version == 7` and `SELECT items` is NULL; `parserUsesItemSumsWhenItemsValid` (items roti×2 240 kcal + dal 180, top-level 400 → calories 420); `parserFallsBackWhenItemsMissing` (items null, totals from top level); `repeatOfEditedMealScalesItems` (past "2 roti" with items qty 2 → input "4 roti" gives qty 4).
- [ ] **Step 2: Implement.** Prompt text for items: `"items": Array of the separate foods, each {"name": String (short English, e.g. "roti"), "quantity": Double, "unit": one of piece, katori, bowl, cup, glass, slice, tbsp, tsp, g, ml, serving, "calories", "protein", "carbs", "fat", "fiber": Double for that quantity, "oilG": Double (grams of added oil, ghee or butter used in cooking it, 0 if none), "sugarG": Double (grams of added sugar, 0 if none)}`.
- [ ] **Step 3: Commit** `feat: store meal items (DB v7) and ask Gemini for them`.

### Task 3: Save edits and back them up

**Files:**
- Modify: `data/Daos.kt` (`@Update suspend fun updateEntry(entry: NutritionEntryEntity)`), `data/DataRepository.kt` (`suspend fun updateNutritionEntry(entry)` = update + `uploadNutrition`), `data/FirebaseSyncManager.kt` (`items` in `toMap` when non-null, `this["items"] as? String` in `toNutritionEntry`)
- Modify: `DashboardViewModel` and `HistoryViewModel`: `fun updateMeal(entry: NutritionEntryEntity)` → repository
- Test: `EntryDaoTest.updateEntryReplacesTotalsAndItems`

- [ ] Step 1 test, Step 2 implement, Step 3 commit `feat: save meal edits and include items in backup`.

### Task 4: Edit sheet UI

**Files:**
- Create: `ui/dashboard/MealEditSheet.kt` — `@Composable fun MealEditSheet(entry: NutritionEntryEntity, onSave: (NutritionEntryEntity) -> Unit, onDismiss: () -> Unit)` (ModalBottomSheet; state = `MealItems.decode(entry.items) ?: MealItems.legacy(entry)`; rows with −/+ and remove; Oil and Sugar segmented rows shown when `hasOil`/`hasSugar`; live totals line; Save calls `onSave(entry.withItems(state))`)
- Modify: `DashboardComponents.MealItemRow` (add `onClick: (() -> Unit)? = null`, row clickable, small "Tap to adjust" hint), `DashboardSections.TrackMealsCard` (sheet state + `viewModel.updateMeal`), `history/DayBalanceCard.kt` (same)

- [ ] Implement, CI compile, commit `feat: tap a meal to adjust amounts, oil and sugar`.

### Task 5: Friendly errors, weight on Home, remove AI Trainer

**Files:**
- Modify: `domain/GeminiClient.kt` — `sanitizeError(e: Throwable?, usingOwnKey: Boolean = false)`; key problems map to `OWN_KEY_MESSAGE = "Your own Gemini key in Settings isn't working. Remove it there to use Superfit's built-in AI."` only when `usingOwnKey`, else `AI_UNAVAILABLE_MESSAGE`. Callers pass `key.isNotBlank()`.
- Modify: `DashboardSections.kt` — remove both "Open API Settings" buttons; add "Try again" (calls `viewModel.retryLastMeal()`, which re-runs `parseAndAddMeal(lastMealInput)`); Settings icon label "Settings".
- Create: `ui/dashboard/WeightQuickRow.kt` — `@Composable fun WeightQuickRow(metrics: WeightGoalMetrics, onLogWeight: (Double, String) -> Unit)` using existing `WeighInDialog`; placed after `NutrientLedgerCard` in `DashboardScreen`.
- Delete: `domain/WorkoutRecommenderEngine.kt`, `WorkoutRecommendationCard` in `GamificationComponents.kt`, state fields `workoutRecommendation`/`showAiWorkoutRecommendations`, `setWorkoutRecommendationsEnabled`, the Settings toggle.
- Test: `GeminiClientTest` — `keyErrorWithoutOwnKeyIsUnavailableMessage`, `keyErrorWithOwnKeyMentionsSettings`.

- [ ] Tests, implement, commit `fix: friendly AI errors, weight on Home, remove AI Trainer card`.

### Task 6: Verify and ship

- [ ] Push; take `app/schemas/7.json` from the CI log, commit, push.
- [ ] CI green on the head commit (all four steps).
- [ ] Re-read spec line by line against the diff.
- [ ] Open PR; Neetish tests on the dev app per the spec's checklist.
