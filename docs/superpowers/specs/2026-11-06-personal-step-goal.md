# A daily step goal that fits the person

Date: 2026-11-06. Status: design, waiting for Neetish's OK. Nothing built yet.
Plan page: https://claude.ai/artifact/Nq5zmYK1H7pMnk7Bi746BY

## Why

Home shows "x of 10,000" steps to everyone (`DashboardSections.kt`, `stepsTarget = 10000.0`).
For someone who walks 3,000 a day, or a 71-year-old, 10,000 is out of reach, so the ring never
fills and the goal stops meaning anything. For someone already walking 14,000 it is too easy.
Other places use their own fixed numbers too: the Home habit check counts steps as done at 8,000
(`DashboardViewModel`), and an "active day" is 7,500 (`InsightsEngine.ACTIVE_STEPS`).

## What the science says (the numbers we lean on)

- Large 2022 study of 47,000 adults (Paluch et al., Lancet Public Health): the health benefit of
  walking levels off at about **6,000 to 8,000 steps a day for people 60 and over**, and about
  **8,000 to 10,000 for under 60**. Going past that adds little.
- The biggest gain is for people who walk the least: each extra 1,000 steps a day helps most at
  the low end.
- Habit research: small, reachable steps up (about +500 to +1,000 a day) stick far better than a
  big jump.

So the goal should start from what the person really does, rise slowly, and stop rising at a
healthy aim for their age.

## The rule

1. **Your usual.** The typical (median) daily steps over the last 14 days, using only days with
   at least 500 steps (so a day the phone sat on a table doesn't drag it down). Needs at least
   5 such days. Median, not average, so one wedding or one sick day doesn't swing it.
2. **Healthy aim by age.** Under 60: 10,000. 60 to 69: 8,000. 70 and over: 7,000. No age: 10,000.
3. **Goal = usual + a small step up**, rounded to the nearest 500:
   - usual under 5,000: +500
   - usual 5,000 and up: +1,000
   - never more than the healthy aim, never under 2,000.
4. **Already at or above the aim?** Goal = your usual, rounded down to the nearest 500, capped at
   15,000. The ring stays a fair challenge for very active people, and the app says they're at a
   healthy level ("Keep it up").
5. **Changes once a week, on Monday**, so the target doesn't move during a day.
   - Goes up only if you reached the current goal on at least 4 of the last 7 days.
   - Goes down if your usual has fallen more than 1,500 below the goal (illness, travel, bad
     week), to the usual + step up. No guilt: it says "Reset to fit this week".
   - Otherwise it stays.
6. **Not enough step data yet** (new user, Health Connect not allowed, fewer than 5 days):
   starter goal by age: under 60: 7,000; 60 to 69: 6,000; 70 and over: 5,000. Switches to the
   personal goal on the first Monday with enough days.

Examples:
- Neetish, under 60, usual 8,200: goal 9,000; then 10,000 after a good week; stays at 10,000 (or
  their usual if higher).
- Mother, 71, usual 3,100: goal 3,500, climbing about 500 a good week, stopping at 7,000.

## What the person sees

- **Home ring**: "3,200 of 3,500" instead of "of 10,000". The ring fills against their own goal.
- **Tap the steps line** for a small sheet: "Your goal: 3,500. You usually walk about 3,100 a day.
  It goes up 500 when you reach it on 4 days in a week. For your age, about 7,000 a day gives most
  of the health benefit." Plus a button "Set my own goal".
- **Monday note** on Home when it rises: "New step goal: 4,000. You reached 3,500 on 5 days last
  week." Shown that day only, can be dismissed.
- **Settings > Goals: Daily step goal**: "Automatic (recommended)" or "My own number" (1,000 to
  30,000, steps of 500). Choosing own number turns off the weekly changes. For people with a
  doctor's advice or a health condition.

## Other places that should use the same goal

- Home habit check "steps done" (today 8,000) uses the person's goal.
- Coach step finding text uses the age-right range ("6,000 to 8,000" for 60+, "8,000 to 10,000"
  under 60) instead of always "7,000 to 10,000". The coach can mention the goal ("2 of the last
  7 days at your 3,500 goal").
- "Active day" in Insights and the monthly recap stays at 7,500 for now (changing it would change
  past recaps). Can revisit.

## How it's built

- New `StepGoal` in the shared module (`shared/src/commonMain/.../domain/StepGoal.kt`), plain
  Kotlin, no Android, with tests in `shared/src/commonTest` (the iPhone app can reuse it).
  Input: daily steps history, age, current goal and when it was set, today. Output: goal, usual,
  healthy aim, reason (starter / up / down / same / own number).
- Daily steps already come from Health Connect into `activity_telemetry`, so **no new permission,
  no database change**. Goal, date set and own-number choice are kept in app preferences and in
  the existing Firestore profile backup so they survive a reinstall.
- Analytics: one event `step_goal_changed` (direction up/down, auto/own). No step numbers sent.

## Not in this version

- Goal based on medical conditions (we don't ask for them; the own-number option covers it).
- Different goals for weekdays and weekends.
- Step-goal notifications.
