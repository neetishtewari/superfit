# Monthly recap: design

Date: 2026-10-27. Approved by Neetish 2026-10-27 12:28Z (target: first recap on 1 Nov). Plan page: https://claude.ai/artifact/GqEXk8QsWxttmRnBSZ7r9x. Card mock-ups: https://claude.ai/artifact/SMtbuijDx2nz2cs3d2aQ27

## What the user gets
On the 1st of each month, a short set of story cards about the month just gone, a bit like Spotify Wrapped. Each card is one highlight, Story size (9:16), with the "S" logo at the top and the Superfit badge with "Get it on Google Play" at the bottom, the same as the other share cards. Each card has its own **Share** button.

Cards are chosen each month from what actually happened. A card with no data is skipped, so there is no fixed template and never an empty card. In order:

**Your goal**
1. **Your goal.** Kilograms lost or gained this month, how much is down out of the total (for example "3.1 of 10 kg down") and the date you reach the goal at this pace. Shown on the shared picture too (Neetish, 2026-10-27).
2. **What moved the needle.** The two or three habits that helped most or held you back, worked out by the app from the logs.

**Your meals** (patterns only, no Diet Quality scores or ratings on the cards)
3. **On repeat.** The most-logged food, with one word on how good a choice it is ("Dal, 12 times. Solid pick.").
4. **Your macros.** The pattern behind protein, carbs and fibre, for example "protein strong on weekdays, drops at weekends".
5. **Choices that stood out.** A couple of good calls and a couple worth a rethink, by name.

**Your activity** (led by what changed)
6. **You got faster.** Only if run or walk pace improved.
7. **You kept moving.** Kilometres, steps and a calendar of the days you moved.
8. **Strength.** Sessions logged. With none: "Already lifting? Tap the mic and say it", because many people train but never log it.

**Last, not shareable**
9. **Get more from Superfit.** Only what's missing: Health Connect permissions, the mic, the widget, or logging fewer than about half the days. Each line has a one-tap fix. Skipped when everything is set up. This replaces the separate Home tips idea (dropped 2026-10-27).

## Coach voice
The card wording follows the person's coach style (Supportive, Straight talk, Roast me). The facts and numbers are the same in every style. There are no separate roast cards, and nothing on a card says "roast" (Neetish, 2026-10-27). Example, strength card:
- Supportive: "168 km walked this month. Two short strength sessions a week would help keep your muscle while you lose fat."
- Straight talk: "168 km walked, no strength work. Add two short sessions a week to keep your muscle."
- Roast me: "168 km walked, zero kg lifted. Your legs are marathon-ready; your arms are on paid leave. Two short strength sessions a week and they might clock back in."

## Pick your coach (new sign-up step)
- A new onboarding step where the Gemini key step used to be: three styles, each with one sample line, with **Roast me selected by default**.
- Existing users see the same choice once after updating.
- It can still be changed in Settings or at the top of the coach chat.

## Where it lives
- **Home:** a "Your October is ready" card from the 1st until the person has opened the recap, so someone who skips the app on the 1st and 2nd still sees it. It goes once opened (end of that day), and by the 10th at the latest.
- **Progress:** at the top for the first week, then under "Past months". The last 12 months are kept.
- **Notification:** one on the 1st. No extra switch in Settings: people can turn it off from Android's notification settings. With notifications off, the Home card and Progress are the way in. Email reminders come later (not in this release).

## How it works
- The app works out every number and pattern on the phone from meals, workouts and Health Connect. The AI only writes the words, in one call per month that is saved, so reopening costs nothing.
- Patterns use only fully logged days. Blank days count as unknown, never as eating less.
- Under 10 logged days: a shorter recap that leans on Health Connect steps, walks and sleep. Under 3: no recap, just a nudge to log.
- No hard-coded food lists: "On repeat" and the choices come from the user's own logged meals and their AI meal ratings.

## Privacy and cost
- Roast me jokes about choices, never about the person: no jokes about body, weight, looks or health, and nothing that praises eating too little. Serious topics (injury, low mood, disordered eating) are never joked about.
- Cards never show body data or coach notes. The goal card shows kilograms down out of the total and the goal date (Neetish, 2026-10-27), not the person's actual weight.
- AI cost: one call per person per month. No new permissions, no Data safety change.
- Anonymous counts of recaps opened and cards shared, to decide on a yearly recap in mid-December.

## Not in this release
- Yearly recap (decide mid-December from open and share counts).
- Separate Home tips or "Did you know" facts (dropped).
- Email reminders for people with notifications off (plan later).
- Animated or video cards.

## Testing
- Unit tests for the month patterns (goal pace, most-logged food, macro pattern, faster pace, movement days, strength count) and for the light-month rules (under 10 and under 3 logged days).
- A test that a card with no data is skipped and "Get more from Superfit" is skipped when everything is set up.
- Minified (R8) dev build on the phone, with a fake October month, in all three coach styles.

## Decisions (Neetish, 2026-10-27)
- Goal card: "X of Y kg down" plus the goal date, also on the shared picture.
- Home card stays until the recap is opened (latest the 10th).
- No recap switch in Settings; Android notification settings are enough.
- First recap on 1 Nov; a better version by 1 Dec from October's feedback.
- Email reminders later.

## Action needed from you (at release)
- Test the dev app with a sample October recap in all three coach styles.
- Upload 1.0.39 internal, then production, by about 31 Oct.
