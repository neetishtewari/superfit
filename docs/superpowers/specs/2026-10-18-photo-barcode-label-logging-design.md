# Photo, barcode and label logging: design

Date: 2026-10-18. Approved in chat by Neetish (Phase 2, part 1 of "Making Superfit the natural choice").

## Goal

Let people log food without typing or speaking:
- take a photo of a meal,
- scan the barcode on a packet,
- or photograph a nutrition label.

Neetish asked for an elegant design, and for Superfit to ask the user to confirm whenever it is guessing.

## What the user sees

1. **Camera button.** A camera icon sits next to the mic on the Home meal-log card. Tapping it opens a full-screen camera inside Superfit. The first time, Android asks for camera permission. If the user declines, a short message explains why the camera is needed and links to the app settings.
2. **Three modes**, chosen with a pill switcher along the bottom, like the phone's own camera modes:
   - **Meal** (the default): a large shutter button.
   - **Barcode**: a rounded scan frame. The barcode is detected by itself, with no tap, and a light vibration confirms it.
   - **Label**: a tall frame guide ("Fit the nutrition table here") and a shutter button.
3. **Working state.** The captured photo stays on screen, dimmed, with "Reading your meal…", "Looking up product…" or "Reading the label…".
4. **Check your meal sheet.** Every result opens a bottom sheet titled "Check your meal". Nothing is saved until the user taps **Log meal**.
   - **Meal photo**: the same item list as the existing Edit meal sheet (−/+ per item, remove, Oil/Sugar Low/Normal/High, live totals).
   - **Barcode or label**: the product name (when known), with an amount chooser: ½, 1, 2 servings, or grams in steps of 25 g. The chooser defaults to 1 serving, or 100 g when no serving size is known. Live totals are shown.
   - Buttons: **Retake** goes back to the same camera mode. **Log meal** saves and closes the camera.
5. **After saving**, the meal appears in Today's meals like any other. Tap to edit works on it.

## Where the numbers come from

| Mode | Source | Result |
|------|--------|--------|
| Meal photo | Gemini (the same Firebase AI Logic path and model cascade as voice), sent the photo plus the existing meal prompt, asked to describe what it sees | the same JSON as voice: totals, `items`, and the MealTags fields |
| Barcode | ML Kit barcode scanning on the phone (EAN-13, EAN-8, UPC-A, UPC-E), then Open Food Facts product lookup (`world.openfoodfacts.org/api/v2/product/{code}.json`, no key) | name, nutrients per 100 g and per serving, serving size in grams |
| Label photo | Gemini, sent the photo and asked to read the nutrition table | the same per-100 g and per-serving shape as a barcode result |

- **Barcode not found.** If Open Food Facts doesn't have the product, or has no calories for it, the app says "We couldn't find this product. Snap its nutrition label instead." and switches to Label mode.
- **Packaged food as a meal item.** A barcode or label result becomes one `MealItem`: unit `g` with per-gram values when choosing grams, or unit `serving` with per-serving values. That means the meal stays editable later through the existing items JSON. Oil and sugar estimates are 0 for packaged foods, so those switches stay hidden.
- **Meal name.** For packaged food, this is the product name ("Haldiram's Aloo Bhujia"), or "Packaged food" when the label shows none.
- **Diet Quality tags.**
  - Photo meals get them in the same Gemini call, as voice does.
  - Packaged meals are tagged right after saving with the existing `MealTagger`, so the score and coach treat them the same.
  - No hard-coded food lists anywhere (standing rule).

## Privacy

- Photos are held in memory only while they are analysed. They are never written to disk or uploaded anywhere except the Gemini request.
- For barcodes, only the barcode number is sent to Open Food Facts.
- The privacy wording in Settings gains one line: "Meal photos are sent to Google's Gemini AI to estimate nutrition and are not stored."

## Errors (friendly, with a way forward)

- **No food or too dark:** Gemini returns `invalid`, and the app shows "We couldn't see any food. Try again with more light." with Retake.
- **Unreadable label:** "We couldn't read this label. Try a straight, close-up photo."
- **Offline or AI busy:** the existing friendly messages and Try again.
- **Camera unavailable:** close the camera and show a toast.

## Technical shape

- **Dependencies:**
  - CameraX (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`) for the in-app viewfinder.
  - ML Kit `barcode-scanning` (bundled model, works offline).
  - Open Food Facts uses plain `HttpURLConnection` and org.json, so no new HTTP library.
- **New units:**
  - `domain/PackagedFood.kt`: the per-100 g / per-serving data class, `toMealItems(amount)`, plus parsers for an Open Food Facts response and the label JSON. Pure and unit-tested.
  - `domain/FoodPhotoParser.kt`: sends a Bitmap to Gemini for a meal photo (reusing the NutritionParser prompt via `ParsedNutritionResult.fromJson`) or for a label.
  - `data/OpenFoodFactsClient.kt`: fetches by barcode and returns `PackagedFood?`.
  - `GeminiClient.generateContentWithImage(apiKey, bitmap, prompt, systemInstruction)`: same failover and wording as `generateContent`. Images are downscaled to 1024 px on the long edge and sent as JPEG.
  - `ui/camera/FoodCameraScreen.kt`: viewfinder, mode switcher, shutter, scan frame and permission handling.
  - `ui/camera/CheckMealSheet.kt`: shows a `MealItems`, reusing the item rows and LevelPicker from `MealEditSheet` (made internal), or the packaged amount chooser.
- **Saving.** `DashboardViewModel.addPhotoMeal(entry)` saves through the same repository call as voice, refreshes, and tags packaged meals.
- **No database change.** Everything fits the existing columns (items, qualityTags).
- **Manifest.** Adds `android.permission.CAMERA` and `<uses-feature android:name="android.hardware.camera" android:required="false"/>`, so phones without a camera can still install; the camera button is hidden on them.

## Testing

- **Unit tests:**
  - Open Food Facts parsing: full, missing serving size, missing calories meaning not found.
  - Label JSON parsing.
  - `PackagedFood.toMealItems` for servings and grams.
  - Image downscaling dimensions.
- **CI:** existing unit tests, debug and release builds.
- **Dev app on Neetish's phone:** a thali photo, a packet barcode, a barcode not in Open Food Facts that falls back to the label, and a label photo. Also check camera permission denial and large text size.

## Not in this release

- Several photos for one meal.
- Choosing a photo from the gallery.
- Scanning restaurant menus.
- Saving photos to show in history.
