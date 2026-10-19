# Share cards (Phase 4): design

Date: 2026-10-19. Draft for Neetish's OK. Mock-ups (look approved 2026-10-19): https://claude.ai/artifact/4bhYzyEehfAy47APWyWe8f

## What the user gets
Three kinds of picture to share, all Story size (tall, 9:16, made at 1080 x 1920), dark background, Superfit logo top left, "Tracked with Superfit · On Google Play" at the bottom.

1. **Daily card.** Date, "MY DAY", the meal streak, then four tiles: Protein (eaten vs goal, with a bar), Diet Quality (7-day score /100, with a bar), Steps, and the day's main workout. Under them, one coach line.
2. **Activity card.** One workout: type and start time ("Morning run · 6:42 AM"), a big distance, then Time, Pace (or Speed for rides), Average heart rate and Calories burned (exercise calories, the same number the app shows).
3. **Personal-best card.** "NEW PERSONAL BEST", which best it is, the big new number, and a strip with the previous best, its date and the gain ("+2.2 km").

## Where Share appears
- **Home, daily card:** a small Share icon on the day summary at the top of Home.
- **Workouts:** a Share icon on each workout row on Home (today's workouts) and on each workout in a History day.
- **Personal bests:** when a new best comes in, a card on Home says "New personal best: Longest run, 12.4 km" with **Share** and a close (X) button. It shows once per best. Settings gets a "Personal bests" list so older bests can be shared any time.
- **Preview first:** tapping any Share opens a sheet with the card picture, four colour dots (mint, cyan, violet, amber) and a **Share** button. Share opens the normal Android share sheet (WhatsApp, Instagram Story, Messages, save to Files and so on).

## What goes on each card
**Daily card**
- **Streak:** the existing meal streak. If it's 0, the line reads "Day 1" rather than "0 day streak".
- **Protein and Diet Quality:** the same numbers Home shows. On a day with no meals logged, these two tiles are left out instead of showing zero (blank days never read as under-eating).
- **Steps:** from Health Connect, as on Home. Left out if there are none.
- **Workout tile:** the day's longest workout (imported or voice-logged), shown as its distance or minutes. Left out on rest days.
- **Coach line:** the coach tip Home is already showing that day. Left out if there isn't one, so making a card never costs an AI call.
- Tiles that are left out close up, so the card never has empty boxes.

**Activity card**
- **Imported workouts:** everything Health Connect gave: distance, time, pace (runs, walks, hikes) or speed (rides), heart rate and exercise calories. A missing number is simply left out.
- **Voice-logged workouts:** what the user said ("Gym session: chest and back") with minutes and calories, since there's no distance or heart rate.
- **Never on a card:** the source app name, routes or location.

**Personal bests** (worked out on the phone from the workouts already imported)
- Longest run, longest ride, longest swim, longest walk (distance).
- Fastest run pace, on runs of 5 km or more.
- Longest meal streak.

## When a best counts as "new"
- The first 90-day import quietly sets everyone's starting bests. No pop-ups for old workouts.
- After that, a workout that beats every earlier one of its kind is a new best, and the Home card offers to share it. A best has to beat the old one by a little (at least 0.1 km, or 1 second per km for pace) so a GPS wobble doesn't count.
- **Streak:** a new best only once the current streak passes the old longest and is 7 days or more.
- Hidden workouts never count.

## Privacy and cost
- The picture is drawn on the phone and saved in the app's private cache only long enough to hand to the share sheet. Nothing is uploaded and nothing goes to AI.
- **Weight and calories eaten are never on a card.**
- The share text that comes with the picture is "Tracked with Superfit" and the Play Store link for Superfit.

## How it works
- Each card is a normal app screen element, drawn off-screen at 1080 x 1920 and saved as a PNG. Android's standard file-sharing route (a FileProvider) hands it to the share sheet.
- **Font:** the cards use the phone's own font in bold weights. (The mock-ups use Sora; bundling it can come later, it couldn't be downloaded from the build environment.)
- **Personal-best list:** a small engine on the phone recomputes bests from imported workouts and the streak. Bests already announced are remembered (backed up with the other settings), so the prompt never repeats, even after reinstalling.
- No database change.

## Not in this release
- Route maps (Health Connect doesn't share them; would need a direct Strava connection).
- Weekly or monthly summary cards.
- Square or landscape sizes.

## Testing
- **Unit tests:** personal-best engine (first import is silent, a real new best fires once, small wobbles don't count, hidden workouts skipped, pace only from 5 km+ runs, streak rule), daily-card tiles closing up on blank or rest days, voice-logged vs imported activity cards.
- **Minified dev build on your phone** (the new release check): share each card type to WhatsApp and to Instagram Story, and check the picture looks like the mock-ups.

## Action needed from you (at release)
- Nothing new in Play Console. No new permissions, and no new data leaves the phone.
