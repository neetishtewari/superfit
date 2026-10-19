# Workouts from other apps (Phase 3): design

Date: 2026-10-19. Draft for Neetish's OK. Based on the workout check on Neetish's phone (318 workouts in 90 days: Samsung Health 207, Strava 46, Google Fit 65).

## What we learned from your phone
- **Every workout comes with:** type (run, walk, swim, other), start and end time, and distance.
- **Samsung Health also gives:** calories and average heart rate for almost every workout.
- **Strava gives:** distance and calories, no heart rate. Its numbers are exact copies of Samsung's, because Samsung passes your workouts to Strava.
- **Missing everywhere:** route maps, workout names, and "exercise-only" calories (apps share total calories only).
- **Duplicates:** 86 workouts were recorded twice (mostly Samsung + Strava, some Google Fit walks).

## What the user sees
- **One-time card on the Activity tab:** "Bring in your workouts from Samsung Health, Strava and other apps." Buttons: **Connect** (opens Health Connect's permission screen) and **Not now**. The same switch lives in Settings as "Import workouts from other apps".
- **Workouts appear on their own.** Each shows a small "from Samsung Health" label, the type, time, distance, pace (worked out from distance and time), average heart rate when available, and calories.
- **The first import brings in the last 90 days,** so History, the coach and later the personal-best cards have something to work with.
- **Voice-logged workouts stay** exactly as they are. Gym users without a watch keep logging by voice.
- **Hide a workout:** long-press, then "Hide". It's hidden in Superfit only and never deleted from the other app.
- **The coach** can mention workouts, for example "3 runs this week, 21 km, your longest since August".

## Duplicates
- When two apps have a workout of the **same kind** that **overlaps mostly in time**, Superfit shows it once.
- It keeps the copy with the **most detail** (heart rate, then calories, then distance).
- **Settings, "Main workout app":** Automatic (default) or a specific app. Whatever the user picks always wins a tie.
- If someone also says "ran 5k" by voice on a day an imported run exists, Superfit asks once: "Looks like your Samsung Health run. Keep just one?" with **Keep imported** / **Keep both**.

## Calories
- Apps share **total** calories, which include what the body burns anyway during that time. Superfit shows **exercise calories**: total minus the resting part (worked out from the user's BMR and the workout's length). For the 18 Oct run: 601 total, about 550 from the run itself.
- **Workouts raise that day's calorie target** (Neetish chose "Add back half", 2026-10-19, then "All" after seeing their own numbers the same day), only for people who connect their workouts:
  - The base target covers everyday life without workouts: resting burn (BMR) x 1.2, plus the goal adjustment as today.
  - Each day, that day's exercise calories are added (all of them by default). Example: the 18 Oct run (about 550 exercise kcal) adds about 550 kcal; a rest day gets the base only.
  - Settings, "Add workout calories to my target": Off / Half / All (default).
  - The Home ring shows where the number comes from, e.g. "2,100 + 275 from your run = 2,375 today", so it never changes without explanation.
  - Why it matters (Neetish's data, 4-19 Oct): workout calories ranged from 51 to 953 a day (about 220 exercise kcal on average), so a fixed target was ~700 kcal short on the big run day.
  - Voice-logged workouts count too, using their estimated calories.
  - People who don't connect their workouts keep today's target (BMR x the activity level they chose).
  - Protein stays steady; only carbs and fat grow with the extra calories.
- Workouts are never counted twice against the "burned today" number Superfit already reads from Health Connect.

## How it works
- Reads Health Connect workout sessions, plus distance, calories and heart rate for each one, when the app opens and in the existing background sync.
- Saves them in a new table on the phone (database update). They aren't backed up to the cloud, because they can always be read again from Health Connect. Hidden workouts and the "Main workout app" choice are backed up.
- Broken entries from other apps (for example an end time before the start) are skipped.
- No hard-coded food or exercise word lists. Workout types come from Health Connect's own types.

## Action needed from you (at release)
- **Play Console:** the Health Connect declaration must list the new data types (exercise, distance, heart rate, calories) and say why. I'll give you the exact wording.
- **Privacy policy:** one added line, which I'll write.

## Not in this release
- Route maps (no app shares them).
- Personal-best and activity share cards (Phase 4, built on this).
- A direct Strava connection (not needed: Strava data already arrives through Health Connect).

## Testing
- **Unit tests:** duplicate merging (Samsung + Strava, Google Fit walk inside a Samsung walk, two different workouts back to back), exercise-calorie maths, broken entries skipped, "Main workout app" wins.
- **Your phone:** connect, check the 90-day import, check the 18 Oct run shows once with heart rate, hide a workout, pick Samsung Health as main app.
