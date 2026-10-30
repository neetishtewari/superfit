# Grams and ml for food amounts

Status: approved 2026-10-30 (simplified: no Settings switch). Target release 1.0.42 (code 46).

## Problem

Some users don't trust "1 katori" or "1 cup" as an amount. They weigh food or read the pack, and want
to see and set amounts in grams (solid food) and ml (drinks and other liquids).

## What the app does today

- The AI picks one unit per item from: piece, katori, bowl, cup, glass, slice, tbsp, tsp, g, ml, serving.
- Nutrients are stored per one unit (`MealItem`), so the Edit meal sheet's − / + is a multiplication.
- If someone types "200 g rice" the AI usually keeps grams, but nothing tells it to.
- There is no way to switch an item from katori to grams, and no setting.

## Design (approved version)

Neetish (2026-10-30): no Settings choice; the option lives where amounts are logged and edited.
Home-made aloo paratha is a third of a dhaba one, so seeing what the AI assumed matters most.

1. **The AI weighs every item.** Each item gets `grams` (weight of the whole amount, ml for drinks)
   and `liquid`. The AI decides solid or liquid; no food-name lists. Stored per unit in the items
   JSON as `w` and `m` (no database migration). Older meals don't have them and keep their units.
2. **Weight shown everywhere.** Meal text reads "1 aloo paratha (120 g), 1 cup chai (150 ml)", on
   Home and after edits. The Edit / Check meal sheets show "about 120 g" under the amount.
3. **Per-item switch** "katori | g" (or "cup | ml", "pc | g") flips one item. Lossless: 1 katori
   at 150 g becomes 150 g with the same calories; switching back uses the stored `hu`/`hw`.
4. **Tap the amount to type an exact number** (e.g. 40 g for a small home paratha).
5. **Steps for − / +** in g/ml: 5 under 50, 10 under 250, 25 above.
6. **The user's own words win**: "200 ml milk", "1 cup tea with rusk" keep exactly that amount and unit.
7. Saying the same meal again reuses the corrected meal (existing repeat-meal behaviour).
8. Event `meal_amount_changed` (how = typed | switch).

## Not changing

- Calorie maths, oil/sugar levels, Diet Quality, photo and barcode flows (photo uses the same prompt).
- No new permissions, no Data safety change.

## Tests

MealItems: encode/decode with weight, switching unit is lossless, steps by size, labels in both
modes, old meals without weight unchanged. Parser: weight and metric read from AI JSON.
