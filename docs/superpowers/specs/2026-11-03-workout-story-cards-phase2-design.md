# Workout story cards, phase 2

Date: 2026-11-03. Status: proposal, awaiting Neetish's approval. Builds on 1.0.49
(`2026-11-03-workout-photo-card-design.md`). Plan page: https://claude.ai/artifact/HCk4cqJ5iY6dVMU3uGtXfU

Neetish's input (11:08Z): the coach line is optional, picked or unpicked while sharing, with a
default the user sets; Roast works on any workout, when picked for sharing; the "card is ready"
nudge is in-app, never a push notification (the app has no in-app notifications yet).

## 1. "Add to card" chips on the share screen

Replaces 1.0.49's "Just numbers / With one line" pills with one row of chips. Each adds one thing;
none is on unless chosen. Photo cards still default to numbers only.

| Chip | Adds | Shown when |
| --- | --- | --- |
| Stand-out | the app's own line (fitter heart, hardest since, longest, busiest week, sleep link) | a line exists |
| Coach | one line from the AI coach in the user's style (Supportive / Straight talk) | always |
| Roast | the card switches to the roast layout with a short roast of this workout | always |
| Journey | footer "Day 34 of my journey", plus "2.1 of 5 kg down" only if Show weight is on | a goal is set |

- **Coach and Roast are either/or**: picking one unpicks the other. Roast is offered to everyone on
  any workout (runs, walks, gym, HIIT, voice-logged), whatever coach style they chose. Roast me users
  get Roast instead of Coach as the second chip's default suggestion.
- **Defaults**: the last choice is remembered (photo and plain cards separately), and a small
  "Make this my default" link under the chips saves it on purpose. Settings > Sharing lists the same
  chips with switches, plus **Show weight on cards** (off by default; weight stays private unless
  turned on).
- **One AI call per chip per workout**, only when the chip is picked, cached so reopening the sheet
  costs nothing. While it loads the chip shows a small spinner; if it fails the chip says "Couldn't
  write it, tap to retry" and the card shares without it.
- **Coach line**: up to about 16 words, about this workout and how it fits the user's week and goal
  (the app hands the AI the facts: workout, effort minutes, this week's count, stand-out line). It can
  be chatty but not long. Never about food as payment for exercise.
- **Roast**: Roast me rules (short sentences, never about the body or weight, serious on injury, pain
  or low mood, never implies under-eating) and always ends with one fair, true line from the app
  ("Still your 5th movement day in a row"). Uses the existing roast share layout.
- Text limits keep photo cards clean: at most one line from Stand-out plus one from Coach/Roast.

## 2. Strength card for voice-logged workouts

Today a voice workout card shows only calories and what was said. New card:

- Big number: total reps when the words give them ("20 push-ups and 50 curls" = 70 reps), else minutes
  if said, else calories as today.
- Body areas worked (upper body, lower body, core, full body) from the strength tags the recap already
  caches (`recap_strength_tags`); an area not trained in the last 7 days shows faded with "not this
  week". Tags come from the same batched AI call, run for this one log if it isn't tagged yet.
- Week strip: Mon-Sun, green for cardio days, violet for strength days, today ringed.
- Exercises listed in small type (up to 3, "+2 more").
- Works with all chips above (Roast my workout included).

## 3. Sleep as a stand-out line, only when proven

Added to the stand-out line picker, ahead of the others when it qualifies: the user's own runs (or the
same workout type) with sleep data, at least 10, and the ones after 7+ hour nights are clearly better
(runs: at least 10 s/km faster at similar heart rate; other workouts: at least 5 bpm lower average
heart rate for the same minutes). Checked again on each share, so it disappears if the pattern fades.
Wording: "Slept 7h 40m. Your best runs follow 7+ hour nights." Never shown after a short night as a
telling-off.

## 4. "Ready to share" card on Home (in-app, not a push)

The app's first in-app nudge, built so later nudges can use the same slot.

- When a new workout arrives (Health Connect import or voice log), Home shows a slim card under
  today's workout: thumbnail of the card, "Your HIIT card is ready", **Share** and a close button.
- Only for today's workouts, one at a time (the newest), gone at midnight or when shared or closed.
  Closing three in a row without sharing pauses it for a week, so it never nags.
- Only for workouts worth showing: 15+ minutes, or any with a stand-out line, or a voice-logged
  strength session.
- **Walks only from 30 minutes** (Neetish 11:11Z, 11:17Z): Samsung Health and many watches and bands
  record every walk over about 10 minutes on their own, and these don't go to Strava by default
  either. Health Connect doesn't reliably say whether a walk was started by hand, so shorter walks
  never get the card; walks of 30+ minutes do. A walk that sets a personal best is already covered by
  the existing "New personal best" card. Any walk can still be shared from the workout list.
- Settings > Sharing: **Show "ready to share" on Home** switch (on by default).
- Never a system notification; nothing new in Android notification settings.

## 5. Weekly training card

- Ready on Sunday from 6 pm until Monday night, through the same Home slot ("Your week is ready").
  Also under Progress > This week as "Share my week".
- Shows days moved out of 7 with the week strip, km, strength days, workout minutes, one trend
  (pace over 4 weeks, or minutes, or days moved) as a small line, and one food line when it's true
  ("Protein target 5 of 7 days"). Sleep average only when there's sleep data.
- Same chips: Stand-out, Coach, Roast my week, Journey.
- Needs 2+ movement days in the week, otherwise no card.

## 6. Instagram sticker

- Share screen button **Save as sticker**: saves a see-through PNG of just the stats block (no
  background, white text with a soft shadow) to the gallery, so people can add it on top of their own
  photo or video in Instagram, WhatsApp or Snapchat. A one-line tip explains how the first time.
- No separate WhatsApp size: status uses the same 9:16 shape as the cards already have.

## Not doing

Route maps, kudos/feeds, comparing with other people, "you burned 2 gulab jamuns".

## Order

1. **1.0.50**: chips with Coach, Roast on any workout, Journey and defaults (Settings > Sharing), the
   strength card, sleep stand-out line.
2. **1.0.51**: Home "ready to share" card and the weekly card.
3. **1.0.52**: sticker.

## Analytics

`share_card_shared` adds `chips` (e.g. "standout,coach") and `from` (sheet / home_ready / weekly).
New `share_ready_shown`, `share_ready_closed`.

## Data safety

No new permissions. Coach and roast lines send workout facts to the AI the same way the existing
coach does: the coach already receives each imported workout's line with its average heart rate
(`ImportedWorkouts.coachEntry` uses `detailLine`). Phase 2 sends only what that already covers plus
derived effort minutes (e.g. "hard 10 min"); no raw heart-rate samples and no peak value. Before
release, re-check the Data safety form and the Health Connect declaration still describe this.

AI lines are checked before they go on a card: any number in the line that isn't one of the facts the
app supplied (minutes, reps, km, counts) is rejected and the chip retries once, then falls back to
the card without the line.
