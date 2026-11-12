# Suggest past foods while typing a meal

Date: 2026-11-12. Status: approved by Neetish 2026-11-12 with the recommended picks (not in Edit meal for now; 4 suggestions). Built for 1.0.59 (code 63).
Plan page: https://claude.ai/artifact/NUWsREzM19ABCeFLnj59G4

## Why

Typing a meal on a phone is slow, and most people eat the same 20 to 40 things again and again.
Today the box shows "Your usual foods" chips only while it is empty; as soon as you start typing
they are gone and you type every word yourself. Google, WhatsApp and most food apps now finish the
word for you from what you have used before. Superfit already knows everything you have logged, so
it can do the same, with no AI call and no internet.

## Where

1. Today: the "Type what you ate" box (`TrackMealsCard`).
2. Progress: the "Log a missed meal" box (`DayBalanceCard`).

Both live in shared code, so the iPhone app gets it too. The mic, camera and coach chat are not
touched.

## What the person sees

After **2 letters**, a small list opens right under the box with **up to 4 suggestions**. It
updates on every letter and closes when nothing matches, when the box is cleared, or when the meal
is logged.

Two kinds of suggestion, both from the person's own logs only:

- **A whole past meal**, e.g. "2 roti, dal and salad · 420 kcal". Matched when any word in it
  starts with what was typed. Tapping it fills the box with that meal. Logging it then is instant
  and gives exactly the same numbers as last time, because the app already logs an exact repeat
  without asking the AI (`RepeatMealCache`). The kcal is shown because it is known.
- **A single food from past meals**, e.g. "paneer bhurji". Taken from the food list stored with
  each meal (`MealItems`). It is matched against **only the part being typed now**, after the last
  comma, "and", "with" or "+". Tapping it swaps that part for the food name, keeps any amount the
  person typed ("2 ro" becomes "2 roti"), and adds ", " so they can carry on typing the next food.
  No kcal shown, because the amount is still the person's to choose. The meal still goes to the AI
  as normal.

Example: typing "2 roti and pa" shows "paneer bhurji", "palak paneer" (single foods) and any whole
meal containing a word that starts with "pa".

## Order

1. Starts with the typed text before contains it at the start of a later word.
2. Then the score the app already uses for "Your usual foods" (`FoodPredictor`: how often, how
   recently, and how close to this time of day), so breakfast foods come first in the morning.
3. At most 2 whole meals and fill the rest with single foods; if one kind has none, the other
   fills all 4.

## Matching rules

- Ignores capitals, extra spaces and accents (so "acai" finds "açaí" for Brazil and Portugal).
- Ignores numbers and units when matching ("2 katori dal" matches "dal").
- No spelling-mistake guessing in this first version; "panner" will not find "paneer". Can be
  added later if it is missed.
- Never suggests what was just typed exactly, and never shows the same food twice.
- Deleted meals stop being suggested, since suggestions come from the meals that exist.

## What stays the same

- No hard-coded food list anywhere (project rule): only the person's own logs.
- No AI call, no internet, no new permission, no new data collected, so no Data safety change.
- "Your usual foods" chips still show while the box is empty.
- A brand-new user with nothing logged sees no list.

## Usage tracking

One anonymous event, `meal_suggestion_used`, with `kind` = `meal` or `food`, so we can see if
people use it. Follows the existing analytics switch in Settings.

## Building it

- `domain/MealSuggester.kt` (shared): builds the word list once from all meals (whole meal texts
  plus item names, with scores), and `suggest(typed)` returns up to 4. Pure function, unit-tested.
- `ui/components/MealSuggestions.kt` (shared): the list under the box, same look as the fields
  (soft filled, green highlight on the matched letters).
- `TodayModel` / `HistoryModel`: keep the list ready, refresh it after a meal is logged, edited or
  deleted.
- Tests: matching, order, the "part being typed" swap, accents, amounts kept, duplicates removed.

## Open questions for Neetish

1. Also in the Edit meal "change this item" box? Recommended: not in this first version, add if
   wanted.
2. 4 suggestions, or 3 to keep more of the screen visible? Recommended: 4.

## Also fixed while building

Text boxes (`SuperfitField`) now put the cursor at the end when their text is set from outside
(a suggestion or a "Your usual foods" chip). Before, it stayed where it was in the old text, so a
tapped chip left the cursor at the start.
