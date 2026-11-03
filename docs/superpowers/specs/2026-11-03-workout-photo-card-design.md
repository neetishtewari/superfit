# Workout card with heart rate, and a photo you can adjust

Date: 2026-11-03. Status: approved by Neetish 2026-11-03; built in 1.0.49. Release target: 1.0.49 (code 53).
Plan page: https://claude.ai/artifact/HCk4cqJ5iY6dVMU3uGtXfU (section "Design to approve").

## Why

Most workout cards today show only minutes, average heart rate and calories, the same numbers
every fitness app shows. People share with their own photo, so the card must stay light on words.
And the photo is fixed and centred (`ContentScale.Crop`), so faces get cut off.

## Part 1: adjust the photo

Applies to every card that takes a photo (workout and personal best).

- Once a photo is added, the card preview in the share sheet becomes adjustable:
  - Pinch to zoom, from "fills the card" (smallest allowed) up to 4x.
  - Drag to move. The photo always covers the whole card, so no empty edges.
  - Double-tap resets to fills-the-card, centred.
  - Light rule-of-thirds grid lines show only while a finger is down. They are drawn on top of
    the preview, outside the captured layer, so they never end up in the shared picture.
- New pills under the preview, only with a photo: **Stats at bottom** (default) / **Stats at top**.
  Top puts the stats block just under the header (still below `SAFE_TOP` so Instagram's profile
  row doesn't cover it), and the dark fade flips to the top.
- What you see is what is shared. The zoom and position are kept in card units (fractions of the
  1080 x 1920 card), not preview pixels, so the 70%-wide preview and the full-size capture match.
  Gesture distances are divided by the preview scale from `CapturableStory`.
- The photo is loaded at up to about twice the card size (longest edge capped near 3000 px) so
  zooming in stays sharp without using too much memory. It still lives only in memory while the
  sheet is open; never saved or uploaded. Removing the photo resets zoom, position and stats place.
- Hint text "Pinch to zoom · drag to move" shows under the preview the first time a photo is added
  (once per install).

## Part 2: a better workout card

For workouts imported from Health Connect. Voice-logged workouts are unchanged in this release
(their strength card is a later step).

### Heart-rate line, coloured by effort

- When the share sheet opens for an imported workout, the app reads that workout's heart-rate
  samples from Health Connect (same app that recorded the workout, same time range). One read,
  at share time only. **No new permission** (heart rate is already asked for) and **no database
  change**.
- If heart rate isn't allowed, or there are fewer than about 20 samples, the card looks as it does
  today. Nothing is made up.
- The card shows a small line of the heart rate across the workout (smoothed down to about 60
  points), with the peak marked by a dot. The line is coloured by effort, so one picture shows both
  the shape and how hard it was:
  - Easy below 70% of max heart rate, Steady 70-80%, Hard 80-90%, Max 90% and up. Max is drawn in a hot orange, not red: red is kept for delete only.
  - Max heart rate is estimated from the user's age (208 - 0.7 x age). If age is missing, the line
    is drawn in one colour without effort bands.
- A tiny legend under the line gives the minutes in each effort band ("Easy 5 · Steady 9 · Hard 10
  · Max 4 min"), dropping bands with under 1 minute.

### Stats

- Peak heart rate is added next to the average.
- Photo card keeps three stats. Without distance: AVG HR, PEAK, BURNED (minutes are the big number).
  With distance: TIME, PACE (or SPEED), AVG HR; peak shows as a small label at the dot on the line.
- The plain (no-photo) card shows all of them.

### Workout names

- The heading uses the workout's name ("STRENGTH WORKOUT", "HIIT") instead of the time of day; the
  time of day stays in the date line.
- `WorkoutDisplay.title` learns more Health Connect types so fewer cards say just "Workout":
  boxing, martial arts, calisthenics, stretching, exercise class, boot camp, gymnastics, skating,
  squash, table tennis, volleyball, surfing, paddling, skiing, climbing (exact list checked against
  the Health Connect library at build time).

### Words: "Just numbers" or "With one line"

- New pills on the share sheet: **Just numbers** / **With one line**. With a photo the default is
  Just numbers; without a photo, With one line. The choice is remembered.
- The pills only appear when there is a line worth saying. The line is worked out by the app from
  the user's own imported workouts (no AI), first match wins:
  1. Fitter heart (runs and walks of 1 km+): similar pace to last month at 5+ bpm lower average
     heart rate, needing at least 3 outings in each period. Reuses `ActivityPatterns`.
  2. Hardest {workout} since {month}: highest average heart rate of that workout type in at least
     30 days, with 4+ earlier sessions of that type.
  3. Longest {workout} in {N} days: longest of that type in 30+ days, with 3+ earlier sessions.
  4. Most workouts in a week since {month}: this week's count is 3+ and the highest in 8+ weeks.
- Compared only with the user's own past. Never with other people, never about food or weight.

## Not in this release

Sleep line (only ever shown when a user's own runs prove a real link: 10+ runs with sleep data and
clearly faster, about 10 s/km or more, after 7+ hour nights), coach line, voice-logged strength
card, weekly card, "card is ready" notification, Instagram sticker, journey footer.

## Analytics

`share_card_shared` gains `photo` (yes/no), `adjusted` (yes/no: zoomed or moved), `words`
(numbers/line), `stats_place` (top/bottom) and `heart_line` (yes/no). Lets us see whether people
use the photo controls and which mode gets shared.

## Tests

Unit tests for: effort-band minutes from samples and age; downsampling and peak; the line picker
(each rule, its minimum-history guard, and order); photo transform maths (minimum cover scale,
clamping so no edge shows, reset). Manual check on the phone with a minified dev build: pinch,
drag, double-tap, stats top/bottom, shared picture matches the preview, card with and without
heart-rate permission.

## Release

1.0.49 (code 53). No new permissions, no Data safety change (heart rate is already read and stays
on the phone), no database migration.
