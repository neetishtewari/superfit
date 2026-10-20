# Insights, Journey and a richer Home Activity (approved 2026-10-20)

Mock-ups: https://claude.ai/artifact/MyjRRdX4hvNZmETX2XgCLA (v6 approved).

## History becomes Insights
The History screen is renamed **Insights** (Home header icon too) with three views: **Insights** (default), **Journey**, **Log**.

**Insights** stays lean:
- One-line verdict on the last 7 full days (today left out), "X, but Y."
- 4 numbers with change vs the week before: move time, km, and either net kcal + protein days (when meals are logged) or workouts + active days.
- Up to 2 "Working" and 2 "Watch" findings (`InsightsEngine`). Watch links to Journey.
- Eating vs target bar chart for 7 days; days with under 2 meals show as "not fully logged", never as under-eating.
- Food Quality and swaps, Personal bests, All trends (streaks, consistency, weekly trends) as rows that expand.

**Journey** (`GoalForecast`):
- Goal covered %, kg done of total, day number, start/now/goal.
- Forecast date at current habits. Pace comes from the weigh-in trend (2+ weigh-ins at least 10 days apart), else from 4 weeks of logged eating vs target.
- Status: on track / at risk (pace slowed by a third, or later than the target date) / off track (not moving) / reached / needs more data.
- What's slowing you down (weekend eating, fewer workouts, over target, logging gaps) and fixes with "X weeks sooner".
- Milestones (first 2 kg, halfway, goal) with the date each was reached.
- Weekly weigh-in card; the button is highlighted once 7 days have passed.
- Edit goal: start weight, goal weight and an optional "reach it by" date (stored per user in SharedPreferences, backed up).
- Without a real goal (new users, or the old 75 → 70 default never changed) Journey asks the user to set one.

**Log**: the old calendar, day balance and calorie chart.

## Home Activity
Today only, but richer: 4 tiles (steps, estimated distance, move time, burned in workouts), each with "Usual <n>" for the same weekday averaged over the last 4 weeks (needs 2+ of them). A short line appears when the user is past their usual, or after 6 pm when steps are well short. The hourly steps chart from the mock-up is left out (hourly steps aren't stored).

## Coach
The chat context gets a "today vs a usual <weekday>" line (steps, workout time, workout kcal; today flagged as still in progress).

## Also fixed
Onboarding no longer resets goal weights; new profiles start with start = goal = current weight instead of 75 → 70.
