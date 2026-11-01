# Where your calories came from, treat calories and activity patterns

Approved by Neetish on 2026-11-01. Plan with mock-ups: https://claude.ai/artifact/Sh1Q21qxgHydDZvC49Y3jG

## Why

Insights and the monthly recap ranked foods by how often they were logged, so chai twice a day
beat pizza twice a week even though the pizza added far more. Foods are now ranked by the calories
they added, and the treat foods that added the most are called out, with good news when the biggest
treat stays out of the top 5.

## Calorie sources (`CalorieSourcesEngine`)

- Each meal is split into its items. The meal's logged calories are shared by each item's own
  calories, so the parts add up to what was logged. Meals without items count as one food.
- Foods are grouped by lower-case item name. Each food has total kcal, the number of meals it was
  in, its share of all calories and its treat calories.
- Top 5 by kcal; most-logged food kept separately (shown in the recap's AI facts).
- Treat calories: an item counts when the AI listed it in the meal's new `treatItems`. Meals rated
  before `treatItems` existed only count when the meal is a single food and its verdict is Treat or
  Avoid for now. Foods marked "Fine for me" never count.
- Small tastes: a treat food under 150 kcal in one sitting (a teaspoon of birthday cake) is portion
  control and never counts as treat calories or as a recap "worth a rethink" meal; the AI is told such
  tastes rate 6-7 (Neetish, 2026-11-01).
- The rule:
  - biggest treat NOT in the top 5: good news ("Treats stayed small");
  - biggest treat IN the top 5: honest heads-up with its rank, the everyday food it outweighs and the
    AI's swap when the meal was mostly that treat;
  - no treat foods: only said when at least 60% of the calories come from rated meals.

## AI change

`MealTags.PROMPT_FIELDS` asks for `"treatItems"`: the names, as in `"items"`, of the foods in the
meal that are a treat or best avoided. Stored as `ti=` in the meal's tags. The re-tagging call
(`MealTagger`) now sends each meal's item names. No extra AI calls.

## Insights, Your week

- New card "Where your calories came from" after "Eating vs target", for the same 7 days: five rows
  with kcal, how often ("about twice a day" when it is), share, a thin bar, and an amber Treat tag.
  Footer: total logged and the week's treat calories.
- Treat line as a finding: `treat_top` leads Watch out; `treat_small` / `treat_none` join Going well.
- Activity findings join Going well (up to 2 extra rows, only when the data shows them):
  - Fitter heart: runs, else walks, 1 km+ with heart rate; this week vs the 4 weeks before; same pace
    or faster (within 5 s/km) and at least 3 bpm lower.
  - Reps: voice-logged reps (50+); an exercise is named only when the workout summaries' numbers add
    up to the stored reps.
  - Better meals on move days: last 4 weeks, 4+ active and 4+ rest logged days, active days rated at
    least 0.7 higher; protein shown when 10+ points higher.
- Food Quality list is now sorted by total calories too.

## Monthly recap

- "Calories" card replaces "On repeat": colour strip of the top 5 shares, rows with times and kcal,
  treats in amber; the AI writes the line in the coach style from facts that state the treat rule.
- New cards when the data is there: Heart (month vs last month, 3+ outings each), Rhythm (usual
  workout hour from 6+ timed workouts within an hour of the most common hour, and biggest step
  weekday at 15%+ above average from 14+ step days), Reps (100+ a month).
- "Better meals on move days" is a "What moved the needle" fact.
- Saved recap words moved to a new key so finished months are written once more with the new cards.

## Not doing

- Grouping differently named versions of a food ("chai" and "masala chai").
- "Burned like food" comparisons.
