# Photo, Barcode and Label Logging Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Log a meal from a photo, a packet barcode or a nutrition-label photo, always confirmed in a "Check your meal" sheet before saving.

**Architecture:** Pure parsing and amount logic lives in `domain/` and is unit-tested. Network sources are Gemini (via a new image call on `GeminiClient`) and Open Food Facts (`data/OpenFoodFactsClient`). The UI is a full-screen CameraX screen in `ui/camera/` that hands its result to a confirm sheet. Saving goes through `DashboardViewModel`, the same way voice does.

**Tech Stack:** Kotlin, Jetpack Compose, CameraX, ML Kit barcode-scanning (bundled), Firebase AI Logic (Gemini), org.json, HttpURLConnection, JUnit + Robolectric.

**Spec:** `docs/superpowers/specs/2026-10-18-photo-barcode-label-logging-design.md`

## Global Constraints

- Nothing is saved until the user taps **Log meal**; **Retake** goes back to the same camera mode.
- No hard-coded food-name lists anywhere (standing project rule).
- Photos are held in memory only. Never write them to disk. Only the barcode number goes to Open Food Facts.
- Gemini images: downscale to 1024 px on the long edge, send as JPEG, and use the same failover and Firebase wording as `generateContent`.
- Barcode formats: EAN-13, EAN-8, UPC-A, UPC-E.
- Open Food Facts URL: `https://world.openfoodfacts.org/api/v2/product/{code}.json`, no key.
- No database change. Use the existing `items` and `qualityTags` columns.
- Packaged amount chooser: ½, 1, 2 servings, or grams in 25 g steps. Default is 1 serving, or 100 g when no serving size is known.
- Exact copy:
  - "Reading your meal…", "Looking up product…", "Reading the label…"
  - "Check your meal", "Retake", "Log meal"
  - "Fit the nutrition table here"
  - "We couldn't find this product. Snap its nutrition label instead."
  - "We couldn't see any food. Try again with more light."
  - "We couldn't read this label. Try a straight, close-up photo."
  - "Meal photos are sent to Google's Gemini AI to estimate nutrition and are not stored."
- Manifest: `android.permission.CAMERA` and `<uses-feature android:name="android.hardware.camera" android:required="false"/>`.
- Unit-test command: `./gradlew :app:testDebugUnitTest`. The cloud has no Android build, so CI is the verifier: push, then read the CI result.

## Review Focus

1. **Open Food Facts gives only kJ, or gives calories as a string.** Use `energy-kcal_100g` if present; otherwise use `energy_100g` (kJ) ÷ 4.184. Parse numbers whether they arrive as numbers or strings. Test in Task 1.
2. **A packet with a serving size but no per-serving nutrients.** Compute per-serving values from per-100 g × serving grams ÷ 100. Test in Task 1.
3. **A barcode is seen many times in a row while the camera is open.** Look it up once only. Ignore further detections until Retake. Enforced in Task 5 with a `scanLocked` flag.
4. **Gemini returns `invalid` for a label, or values of 0 kcal.** Treat it as unreadable and show the label error, never a 0-kcal meal. Test in Task 1 (`fromLabelJson` returns null).
5. **Grams amount stepped below 25 g, or servings below ½.** Clamp grams to 25 g and servings to ½; never allow 0. Test in Task 1.

---

### Task 1: PackagedFood model and parsers

**Files:**
- Create: `app/src/main/java/com/superfit/app/domain/PackagedFood.kt`
- Test: `app/src/test/java/com/superfit/app/domain/PackagedFoodTest.kt`

**Interfaces:**
- Produces:
  - `data class Nutrients(val kcal: Double, val protein: Double, val carbs: Double, val fat: Double, val fiber: Double)` with `fun times(f: Double): Nutrients`.
  - `data class PackagedFood(val name: String?, val per100g: Nutrients, val servingGrams: Double?, val perServing: Nutrients?)`.
  - `sealed interface PackAmount { data class Servings(val count: Double) : PackAmount; data class Grams(val grams: Double) : PackAmount }`.
  - `fun PackagedFood.defaultAmount(): PackAmount` returns `Servings(1.0)` when `perServing != null`, else `Grams(100.0)`.
  - `fun PackAmount.stepUp(): PackAmount` / `stepDown()`:
    - Servings step ½ → 1 → 2 → 3… and clamp at ½.
    - Grams step ±25 and clamp at 25.
  - `fun PackagedFood.toMealItems(amount: PackAmount): MealItems`:
    - Always exactly one item, with `oilG = 0.0`, `sugarG = 0.0`, `hasEstimates = false`.
    - Servings: `unit = "serving"`, `qty = count`, per-unit values = `perServing`.
    - Grams: `unit = "g"`, `qty = grams`, per-unit values = `per100g` ÷ 100.
    - Item name = `name ?: "Packaged food"`.
  - `object PackagedFoodParser`:
    - `fun fromOpenFoodFacts(json: String): PackagedFood?` returns null when `status != 1`, the product is missing, or calories per 100 g are missing or ≤ 0.
    - `fun fromLabelJson(json: String): PackagedFood?` returns null on `"invalid"` or calories ≤ 0.
    - `val LABEL_SYSTEM_INSTRUCTION: String` is the prompt asking Gemini for raw JSON `{"name": String|null, "per100g": {kcal, protein, carbs, fat, fiber}, "servingGrams": Double|null, "perServing": {…}|null}`. If no nutrition table is visible, `"name"` must be exactly `"invalid"`.

- [ ] **Step 1: Write failing tests** in `PackagedFoodTest` (plain JUnit, org.json works under Robolectric like `MealTagsTest`; copy its runner annotation):
  - `offFullProduct`: JSON with `status:1`, `product_name:"Aloo Bhujia"`, `nutriments:{"energy-kcal_100g":550,"proteins_100g":10,"carbohydrates_100g":45,"fat_100g":36,"fiber_100g":5,"energy-kcal_serving":165}`, `serving_quantity:"30"`.
    - Assert name "Aloo Bhujia", `per100g.kcal == 550.0`, `servingGrams == 30.0`, `perServing!!.kcal == 165.0`.
  - `offServingWithoutPerServingNutrients`: same, minus `*_serving` keys.
    - Assert `perServing!!.kcal == 165.0` and `perServing.protein == 3.0`.
  - `offNoServing`: no `serving_quantity`.
    - Assert `servingGrams == null`, `perServing == null`, `defaultAmount() == Grams(100.0)`.
  - `offKilojoulesOnly`: `energy_100g: 2301`.
    - Assert `per100g.kcal` within 0.5 of `550.0`.
  - `offMissingCaloriesIsNotFound` and `offStatusZeroIsNotFound`: both assert `null`.
  - `labelJsonParses`: the shape above with per100g kcal 480 and servingGrams 25.
    - Assert values.
  - `labelInvalidIsNull`: `"name":"invalid"` gives null.
  - `labelZeroKcalIsNull`: gives null.
  - `toMealItemsServings`:
    - `Servings(2.0)` gives one item with unit "serving", qty 2.0, `totals().kcal == 330.0`, `hasOil == false`.
  - `toMealItemsGrams`:
    - `Grams(50.0)` gives unit "g", qty 50.0, `totals().kcal == 275.0`.
  - `stepping`:
    - `Servings(0.5).stepDown() == Servings(0.5)`
    - `Servings(0.5).stepUp() == Servings(1.0)`
    - `Servings(1.0).stepUp() == Servings(2.0)`
    - `Grams(25.0).stepDown() == Grams(25.0)`
    - `Grams(100.0).stepUp() == Grams(125.0)`
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*PackagedFoodTest*'` (or push and read CI). Expected: compile FAIL.
- [ ] **Step 3: Implement** `PackagedFood.kt`.
  - Read numbers with a helper that accepts a JSON number or numeric string.
  - Strip ```json fences before parsing the label JSON, as `MealTagger.parse` does.
- [ ] **Step 4: Run the tests.** Expected: PASS.
- [ ] **Step 5: Commit** with `feat: packaged food model and Open Food Facts / label parsers`.

### Task 2: Open Food Facts client

**Files:**
- Create: `app/src/main/java/com/superfit/app/data/OpenFoodFactsClient.kt`

**Interfaces:**
- Consumes: `PackagedFoodParser.fromOpenFoodFacts`.
- Produces: `object OpenFoodFactsClient { suspend fun lookup(barcode: String): PackagedFood? }`.
  - Runs on `Dispatchers.IO`.
  - Uses `HttpURLConnection` with 8 s connect and read timeouts.
  - Header `User-Agent: Superfit Android - https://github.com/neetishtewari/superfit`, which Open Food Facts asks for.
  - Query `?fields=product_name,nutriments,serving_quantity` to keep the response small.
  - A 404 returns null (not found).
  - Other HTTP errors and `IOException` throw, so the UI shows the existing offline message via `GeminiClient.sanitizeError`.
  - The barcode must match `^\d{8,14}$` or the call returns null.

- [ ] **Step 1: Implement** the client. It has no unit test: it is a thin wrapper and the parser is tested in Task 1.
- [ ] **Step 2: Commit** with `feat: Open Food Facts barcode lookup`.

### Task 3: Gemini image call and photo parsers

**Files:**
- Modify: `app/src/main/java/com/superfit/app/domain/GeminiClient.kt` (add next to `generateContent`, around line 132)
- Modify: `app/src/main/java/com/superfit/app/domain/NutritionParser.kt`: change `private val systemInstructionText` to `internal val systemInstructionText`, so the photo parser reuses it.
- Create: `app/src/main/java/com/superfit/app/domain/FoodPhotoParser.kt`
- Test: `app/src/test/java/com/superfit/app/domain/GeminiClientTest.kt` (add cases)

**Interfaces:**
- Produces:
  - `fun GeminiClient.scaledSize(width: Int, height: Int, maxEdge: Int = 1024): Pair<Int, Int>`: pure function. It keeps the aspect ratio and never upscales.
  - `suspend fun GeminiClient.generateContentWithImage(apiKey: String, image: Bitmap, prompt: String, systemInstructionText: String?, isJson: Boolean = true): String`:
    - It downscales with `Bitmap.createScaledBitmap` using `scaledSize`.
    - It wraps `runWithFailover` inside `withFirebaseWording`.
    - The Firebase path sends `content { image(scaled); text(prompt) }`.
    - The own-key path sends the `com.google.ai.client.generativeai` `content { image(scaled); text(prompt) }`.
    - Both SDKs encode the bitmap as JPEG themselves, so pass the scaled Bitmap.
  - `class FoodPhotoParser(private val apiKey: String)`:
    - `suspend fun parseMeal(photo: Bitmap): ParsedNutritionResult`. It uses `NutritionParser(apiKey).systemInstructionText` with prompt `"Estimate the nutrition of the meal in this photo. Describe what you see as the user's meal."`, then `ParsedNutritionResult.fromJson(json, "photo")`. The result's `foodText == "invalid"` signals no food.
    - `suspend fun parseLabel(photo: Bitmap): PackagedFood?`. It uses `PackagedFoodParser.LABEL_SYSTEM_INSTRUCTION`, prompt `"Read the nutrition table on this package."`, and `fromLabelJson`.

- [ ] **Step 1: Write failing tests** in `GeminiClientTest`:
  - `scaledSize(4000, 3000) == 1024 to 768`
  - `scaledSize(3000, 4000) == 768 to 1024`
  - `scaledSize(800, 600) == 800 to 600`
- [ ] **Step 2: Run them.** Expected: compile FAIL.
- [ ] **Step 3: Implement** `scaledSize`, `generateContentWithImage`, the `internal` change and `FoodPhotoParser`.
- [ ] **Step 4: Run the full unit suite.** Expected: PASS, including the existing `NutritionParserTest`.
- [ ] **Step 5: Commit** with `feat: send photos to Gemini for meals and nutrition labels`.

### Task 4: Dependencies and manifest

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces:
  - Catalog versions `camerax = "1.4.2"` and `mlkitBarcode = "17.3.0"`.
  - Libraries `androidx-camera-core`, `androidx-camera-camera2`, `androidx-camera-lifecycle`, `androidx-camera-view` (all `version.ref = "camerax"`).
  - Library `mlkit-barcode-scanning = { module = "com.google.mlkit:barcode-scanning", version.ref = "mlkitBarcode" }`.
  - Before committing, check that these exact versions resolve on Google Maven and Maven Central. If one doesn't, use the newest stable version that does and note it in the commit.

- [ ] **Step 1: Add** the catalog entries, `implementation(...)` lines and the two manifest lines from Global Constraints.
- [ ] **Step 2: Push** and confirm CI's debug and release builds pass.
- [ ] **Step 3: Commit** with `build: add CameraX and ML Kit barcode scanning`.

### Task 5: Camera screen

**Files:**
- Create: `app/src/main/java/com/superfit/app/ui/camera/FoodCameraScreen.kt`
- Create: `app/src/main/java/com/superfit/app/ui/camera/FoodCameraViewModel.kt`

**Interfaces:**
- Consumes: `FoodPhotoParser`, `OpenFoodFactsClient.lookup`, `PackagedFood`, `ParsedNutritionResult`.
- Produces:
  - `enum class CameraMode { MEAL, BARCODE, LABEL }`.
  - `sealed interface CameraState`:
    - `Viewfinder`
    - `Working(val photo: Bitmap?, val message: String)`
    - `MealResult(val result: ParsedNutritionResult)`
    - `PackResult(val food: PackagedFood)`
    - `Failed(val message: String)`
  - `class FoodCameraViewModel(apiKey: String) : ViewModel`:
    - `val mode: StateFlow<CameraMode>`, `val state: StateFlow<CameraState>`.
    - `fun setMode(m)`, `fun onPhoto(bitmap)`, `fun onBarcode(code: String)`, `fun retake()`.
    - `onBarcode` ignores calls while `scanLocked`. `retake()` clears it.
    - Barcode not found sets mode LABEL and state `Failed("We couldn't find this product. Snap its nutrition label instead.")`. The screen shows this as a banner over the Label viewfinder, not a dead end.
    - Meal `invalid` gives `Failed("We couldn't see any food. Try again with more light.")`.
    - Label null gives `Failed("We couldn't read this label. Try a straight, close-up photo.")`.
    - Exceptions give `Failed(GeminiClient.sanitizeError(e))`.
  - `@Composable fun FoodCameraScreen(apiKey: String, onLogged: (NutritionEntryEntity, isPackaged: Boolean) -> Unit, onClose: () -> Unit)`.

- [ ] **Step 1: Build the screen.**
  - Full-screen black `PreviewView` via `AndroidView`, with a close X top-left.
  - Bottom pill switcher Meal / Barcode / Label (selected pill uses the primary container colour).
  - Meal and Label: a 72 dp round shutter, with `ImageCapture.takePicture` into memory (`OnImageCapturedCallback` → `toBitmap()` rotated by `imageInfo.rotationDegrees`).
  - Label: tall rounded frame with "Fit the nutrition table here".
  - Barcode: rounded square frame, with `ImageAnalysis` + ML Kit `BarcodeScanning.getClient(BarcodeScannerOptions` with the four formats`)`. On the first value, call `HapticFeedbackType.LongPress` and `onBarcode`.
  - Working: show the captured bitmap dimmed with a spinner and the message (barcode has no bitmap: keep the preview frozen behind a scrim).
  - Permission:
    - `rememberLauncherForActivityResult(RequestPermission)` on first open.
    - If denied, a centred explanation "Superfit needs the camera to read your meal or a barcode." with an "Open settings" button (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`).
    - Camera bind failure → `Toast` "Camera isn't available right now" and `onClose()`.
- [ ] **Step 2: Push** and confirm CI builds pass.
- [ ] **Step 3: Commit** with `feat: in-app food camera with meal, barcode and label modes`.

### Task 6: Check your meal sheet

**Files:**
- Modify: `app/src/main/java/com/superfit/app/ui/dashboard/MealEditSheet.kt`: change `MealItemEditRow`, `StepButton` and `LevelPicker` from `private` to `internal`. Extract the totals row into `internal fun MealTotalsRow(totals: MealTotals)` if it is inline.
- Create: `app/src/main/java/com/superfit/app/ui/camera/CheckMealSheet.kt`

**Interfaces:**
- Consumes: Task 1 `PackagedFood.toMealItems`, `PackAmount`, `stepUp/stepDown`; existing `MealItems`, `NutritionEntryEntity.withItems`.
- Produces:
  - `@Composable fun CheckMealSheet(result: ParsedNutritionResult, onLog: (NutritionEntryEntity) -> Unit, onRetake: () -> Unit)`.
    - It shows item rows, Oil/Sugar pickers when `hasOil`/`hasSugar`, and live totals.
    - The entry is built like `parseAndAddMeal`'s, then `.withItems(meal)`, keeping `qualityTags`.
  - `@Composable fun CheckPackSheet(food: PackagedFood, onLog: (NutritionEntryEntity) -> Unit, onRetake: () -> Unit)`.
    - It shows the product name (or "Packaged food"), a segmented "Servings | Grams" switch (Servings hidden when `perServing == null`), a −/+ amount stepper and live totals.
    - Its entry has `foodText = name ?: "Packaged food"`, totals from `toMealItems(amount)`, `items = encode()` and `qualityTags = null`.
  - Both use the title "Check your meal" with the buttons Retake (outlined) and Log meal (filled).
  - Both are `ModalBottomSheet`s with `skipPartiallyExpanded = true`. Dismissing the sheet counts as Retake.
  - Rows must wrap at large text sizes, as in PR #5: give text `Modifier.weight(1f)` and never fixed widths.

- [ ] **Step 1: Make the shared rows internal** and build both sheets.
- [ ] **Step 2: Push** and confirm the CI builds and the existing tests pass.
- [ ] **Step 3: Commit** with `feat: Check your meal sheet for photo and packaged results`.

### Task 7: Wire into Home, save, tag, privacy line

**Files:**
- Modify: `app/src/main/java/com/superfit/app/data/DataRepository.kt:50`: `addNutritionEntry` returns the new `Long` id.
- Modify: `app/src/main/java/com/superfit/app/ui/dashboard/DashboardViewModel.kt`: add next to `parseAndAddMeal` (line 427).
- Modify: `app/src/main/java/com/superfit/app/ui/dashboard/VoiceQuickLog.kt` (`VoiceQuickLogCard`, line 180): add an `onCameraClick: (() -> Unit)?` parameter. A camera icon button sits beside the mic, shown only when it is non-null.
- Modify: `app/src/main/java/com/superfit/app/ui/dashboard/DashboardScreen.kt:360`:
  - Hold `var showCamera by rememberSaveable { mutableStateOf(false) }`.
  - Pass `onCameraClick` only when `packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)`.
  - Show `FoodCameraScreen` in a full-screen `Dialog(properties = DialogProperties(usePlatformDefaultWidth = false))`.
- Modify: `app/src/main/java/com/superfit/app/ui/dashboard/SettingsScreen.kt`: add the privacy line from Global Constraints next to the existing AI privacy wording.

**Interfaces:**
- Consumes: Task 5 `FoodCameraScreen`, Task 6 sheets (used inside the camera screen).
- Produces: `fun DashboardViewModel.addPhotoMeal(entry: NutritionEntryEntity, isPackaged: Boolean)`.
  - It saves through `repository.addNutritionEntry` and calls `loadFrequentMeals()`.
  - It sets `_parsingState.value = ParsingState.Success(entry.foodText)`, so the existing confirmation shows.
  - When `isPackaged`, it runs `MealTagger(_apiKey.value).tag(listOf(entry.copy(id = id)))` and `repository.saveMealTags`. It catches and logs tagging failures; the backfill retries later because `needsTags` is true.

- [ ] **Step 1: Implement** the wiring.
- [ ] **Step 2: Run the full unit suite and push.** Expected: CI green (unit, migration, debug and release builds).
- [ ] **Step 3: Commit** with `feat: log meals from a photo, barcode or nutrition label`.
- [ ] **Step 4: Open the PR** (draft → ready when green). Then run the dev-app manual checks from the spec on Neetish's phone:
  - a thali photo
  - a packet barcode
  - an unknown barcode, which should fall back to Label
  - a label photo
  - permission denied
  - large text size
