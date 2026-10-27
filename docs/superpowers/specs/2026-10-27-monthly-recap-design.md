# Monthly recap: design

Date: 2026-10-27. Draft for Neetish's OK. Plan page: https://claude.ai/artifact/GqEXk8QsWxttmRnBSZ7r9x. Card mock-ups: https://claude.ai/artifact/SMtbuijDx2nz2cs3d2aQ27

## What the user gets
On the 1st of each month, a short set of story cards about the month just gone, a bit like Spotify Wrapped. Each card is one highlight, Story size (9:16), with the "S" logo at the top and the Superfit badge with "Get it on Google Play" at the bottom, the same as the other share cards. Each card has its own **Share** button.

Cards are chosen each month from what actually happened. A card with no data is skipped, so there is no fixed template and never an empty card. In order:

**Your goal**
1. **Your goal.** Kilograms lost or gained this month, where you are on the journey (start, today, goal) and when you reach the goal at this pace.
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
- **Home:** a "Your October is ready" card from the 1st to the 3rd. Home stays about today, so it goes away after that.
- **Progress:** at the top for the first week, then under "Past months". The last 12 months are kept.
- **Notification:** one on the 1st. Can be turned off in Settings.

## How it works
- The app works out every number and pattern on the phone from meals, workouts and Health Connect. The AI only writes the words, in one call per month that is saved, so reopening costs nothing.
- Patterns use only fully logged days. Blank days count as unknown, never as eating less.
- Under 10 logged days: a shorter recap that leans on Health Connect steps, walks and sleep. Under 3: no recap, just a nudge to log.
- No hard-coded food lists: "On repeat" and the choices come from the user's own logged meals and their AI meal ratings.

## Privacy and cost
- Roast me jokes about choices, never about the person: no jokes about body, weight, looks or health, and nothing that praises eating too little. Serious topics (injury, low mood, disordered eating) are never joked about.
- Cards never show body data or coach notes. Other share cards never show weight; whether the goal card may (see the questions below) is still open.
- AI cost: one call per person per month. No new permissions, no Data safety change.
- Anonymous counts of recaps opened and cards shared, to decide on a yearly recap in mid-December.

## Not in this release
- Yearly recap (decide mid-December from open and share counts).
- Separate Home tips or "Did you know" facts (dropped).
- Animated or video cards.

## Testing
- Unit tests for the month patterns (goal pace, most-logged food, macro pattern, faster pace, movement days, strength count) and for the light-month rules (under 10 and under 3 logged days).
- A test that a card with no data is skipped and "Get more from Superfit" is skipped when everything is set up.
- Minified (R8) dev build on the phone, with a fake October month, in all three coach styles.

## Action needed from you
- Say "go" to build this for 1.0.39. Aim for Play by about 31 Oct so the October recap appears on 1 Nov.
- Defaults unless you say otherwise: notification on the 1st (can be turned off), 12 months kept, Roast me preselected at sign-up.

## Open questions for Neetish
1. **Weight on the goal card.** Other share cards never show weight. Should the shared goal card show actual weights (start, today, goal) or only "1.4 kg down"? Suggested: in the app show all three, on the shared picture only the kg change and the % of the way to goal.
2. **Notifications off.** For people who turned notifications off, the Home card on the 1st to 3rd is the only prompt. Suggested: that's enough; don't ask again.
3. **First recap: 1 Nov or 1 Dec.** 1 Nov needs 1.0.39 live on Play by about 31 Oct. If it slips, the October recap can still appear when people update (until 7 Nov), otherwise start on 1 Dec.
