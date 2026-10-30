# Grams and ml for food amounts

Status: proposed 2026-10-30, awaiting Neetish's OK. Target release 1.0.42 (code 46).

## Problem

Some users don't trust "1 katori" or "1 cup" as an amount. They weigh food or read the pack, and want
to see and set amounts in grams (solid food) and ml (drinks and other liquids).

## What the app does today

- The AI picks one unit per item from: piece, katori, bowl, cup, glass, slice, tbsp, tsp, g, ml, serving.
- Nutrients are stored per one unit (`MealItem`), so the Edit meal sheet's − / + is a multiplication.
- If someone types "200 g rice" the AI usually keeps grams, but nothing tells it to.
- There is no way to switch an item from katori to grams, and no setting.

## Design

1. **The AI also weighs every item.** Each item gets `weight` (grams or ml of one unit) and
   `metric` ("g" for solids, "ml" for liquids). The AI decides solid or liquid; no food-name lists.
   Stored in the meal's items JSON as `w` and `m` (no database migration). Older meals don't have
   them and simply keep their units.
2. **Settings > Food amounts**: "Cups and katoris" (default, as today) or "Grams and ml".
   Stored in prefs key `food_units`. Event `food_units_changed`.
3. **Grams and ml mode**: bowls, katoris, cups, glasses, spoons and servings are shown and
   adjusted in g or ml. Countable foods (roti, egg, idli, slice of bread) keep their count, with
   the weight beside it ("2 roti · 80 g"). The AI is also asked to answer in g/ml.
4. **Cups and katoris mode**: the weight shows in small grey text beside the amount
   ("1 katori · about 150 g"), so everyone can see what the AI assumed.
5. **Edit meal sheet**: tap the amount to type an exact number (for people who weigh food).
   A small unit switch per item ("katori | g") changes just that item. Switching is lossless:
   1 katori at 150 g becomes 150 g with the same calories.
6. **Steps for − / +** in g/ml: 5 under 50, 10 under 250, 25 above.
7. **The user's own words win**: if they say "300 ml milk" or "2 katori dal", the AI keeps exactly
   that amount and unit, whatever the setting.
8. Everywhere meal text appears (Home list, History, coach, recap) follows the stored unit, so a
   switched item reads "150 g rice".

## Not changing

- Calorie maths, oil/sugar levels, Diet Quality, photo and barcode flows (photo uses the same prompt).
- No new permissions, no Data safety change.

## Open questions for Neetish

- Default stays "Cups and katoris"? (Recommended: yes; offer the switch, and pick Grams and ml
  automatically for people who type grams in their first meals? Recommended: no, keep it simple.)
- Countable foods keep counts in Grams mode? (Recommended: yes.)

## Tests

MealItems: encode/decode with weight, switching unit is lossless, steps by size, labels in both
modes, old meals without weight unchanged. Parser: weight and metric read from AI JSON.
