# A bottom menu: Today, Progress, Coach

Date: 2026-11-06. Status: option B approved by Neetish 2026-11-06 18:40Z ("there's a strong case"). Detailed design below awaits their OK before building.
Evaluation page: https://claude.ai/artifact/WxNp9AAfZvDFpAtCdevhkm
Plan page with mock-ups: https://claude.ai/artifact/EVSi4s96ftrJgBS35GixdF

## Why

Today everything starts on Home:

- **Progress** (Insights, Journey, Log) opens from a small top-right button labelled "Insights",
  but the screen it opens is titled "Progress". It is the hardest corner to reach with a thumb.
- **AI coach chat** is a pop-up sheet opened from the coach card halfway down Home, and only in
  the Food view. Not reachable from Activity or Progress.
- Moving between Home and Progress means back arrow, then icon.

A three-tab bottom menu makes the two places people come back for (their progress and their
coach) one tap away from anywhere, without moving the big log button off the top of Today.

## The menu

| Tab | Icon | Opens |
| --- | --- | --- |
| Today | sun / home | Today's screen (what Home is now) |
| Progress | chart | Insights, Journey, Log (as now) |
| Coach | sparkle | The coach chat, full screen |

- Material 3 `NavigationBar`, three items, always showing labels.
- Selected tab: brand green pill and label. **Coach uses coach purple when selected**, keeping
  the rule that purple means the AI coach.
- The app always opens on Today. The chosen tab is kept while the app stays open.
- Tapping the tab you are already on scrolls that screen back to the top.

## Each tab

**Today** (was Home)
- Header keeps the date, greeting and Settings gear (with its red dot when Health Connect needs
  attention). The "Insights" button goes; Progress is in the menu now.
- Food / Activity switch, log card, rings, streak, macros, weigh-in all stay as they are.
- The coach card stays as the daily tip. "Chat with coach" switches to the Coach tab;
  "Roast my day" switches to Coach and sends the roast.
- Monthly recap and new personal best cards stay.

**Progress**
- Same screen, title "Progress", Insights / Journey / Log switch. No back arrow.
- Recap card at the top in the first week of the month, as now.

**Coach**
- Today's chat sheet becomes a full screen: title "AI coach" with the current style
  (Supportive / Straight talk / Roast me), the chat, chips (Roast my day, Tell me more,
  suggested questions), "+ Log this", "Share as a card", and the text box with the mic.
- The text box sits just above the menu. While typing, the keyboard covers the menu.
- Opens scrolled to the latest message.

## Clearer switches (Neetish 2026-11-06: people miss Activity, Journey, Log)

Why they are missed: `SegmentedPills` looks like a quiet grey filter; on Home the Food / Activity
switch sits under the big green log buttons, which take the eye first; "Log" reads as an action.

- New switch style for Food / Activity and Insights / Journey / History: taller, each option
  has a Material icon and its short name (no description lines; Neetish 19:05Z).
- Food / Activity moves to the top of Today, above the log buttons.
- "Log" is renamed **"History"** (analytics key stays `log`).
- A small amber dot on a switch option never opened, cleared on first open (one pref per option).
- Swipe sideways between options (HorizontalPager), as well as tapping.
- "Next: your Journey" / "Next: History" link at the bottom of Insights and Journey.

## Calmer green (Neetish 2026-11-06 18:50Z: yes)

- Solid brand green only on the two log buttons on Today.
- Selected switch option (Food/Activity, Insights/Journey/History) and selected menu tab:
  light mode pale green (BrandGreenSoft) with dark green text (BrandGreenDeep) and a thin green
  outline; dark mode deeper green fill with white text (keeps the earlier dark-mode visibility fix).
- Macro bars: protein green, carbs slate blue, fat sand, fibre sage (quiet, not semantic colours).
- Steps teal, coach purple, amber warnings, red delete only: unchanged.

## Body: latest on Insights, change over time on Journey (Neetish 2026-11-06 19:05Z)

The BodyCard sits at the bottom of Journey today, so the latest readings are missed.

- **Insights (default tab): "Your body now"** card near the top. With scale data: weight, body
  fat, muscle (each with small latest change), and one line for BMI, bone mass, resting burn,
  plus source and date. Without scale data: weight and BMI only. No empty rows, no prompt here.
- **Journey: "How your body has changed"** below goal/milestones/weigh-in: one small trend line
  per measure (weight, body fat, muscle, bone mass, BMI) with change over the period.
  Period pills 1M / 3M / 6M / 1Y, **default 3M**, remembered.
- A measure appears only with 2+ readings in the period. BMI computed from weight log + height,
  so everyone gets weight + BMI.
- Change colour: green if it moved the right way for the goal, amber if not. No red.
- Data: BodyImporter already keeps up to 400 scale readings (`scale_mine`); weight from the
  weight log. No new permissions. The "Allow body measurements" line moves to the bottom of the
  Journey body section, shown only when access can be asked.

## "Ready to share" card for walks and runs (Neetish 2026-11-06 18:53Z)

People miss share-worthy workouts because they don't open every one in Activity.

- Card on Today under the log buttons, same pattern as NewBestCard (icon, one-line title,
  stats, Share button, ✕). Calm green outline. Shown in both Food and Activity views.
- Qualifies: any run; walks at or above the walk minimum (default 30 min; 20/30/45/60/off).
  Health Connect and voice workouts. Workouts from today or yesterday.
- One card at a time: the newest qualifying workout. Hidden once shared, dismissed (✕, remembered
  per workout cloudId) or replaced by a newer one.
- Priority: monthly recap card > new personal best > this card. A run that is a new best shows
  only the personal best card.
- Share opens the existing ShareCardSheet with ShareCards.activity(workout).
- Settings > Sharing (SharingSettings.kt): "Suggest walks to share" with minimum minutes;
  "Suggest runs to share" on/off.
- Analytics: share_offer_shown / share_offer_tapped / share_offer_dismissed with type walk|run.

## Back button and full-screen pages

- From Progress or Coach, the phone's back goes to Today. From Today it leaves the app (as now).
- These open full screen over the menu (menu hidden) and come back to the tab they came from:
  Settings, monthly recap, food camera, share card sheet, sign-in, onboarding.
- Widget and notification shortcuts (say a meal, photo a meal, favourites) open Today as now;
  the recap notification opens the recap.

## What does not change

- No new permissions, no change to stored data or backups.
- Coach chat history, notes and styles work exactly as now.
- Settings content, recap, share cards.

## How we'll know it worked

`screen_opened` already logs home, insights, journey, log, settings, recap. Add `coach` when the
Coach tab opens, and a `how` value (`menu` or `card`) for Progress and Coach. Compare the share
of weekly active users who open Progress and Coach, two weeks before and after.

## Tests

- Unit: back from Progress/Coach returns to Today; tab kept across rotation.
- Phone check (dev app, minified): each tab, back button, Settings from Today and back, recap
  from notification, coach "Roast my day" from Today card lands in Coach, widget mic shortcut,
  dark mode, large font size.

## Release

Next version after 1.0.53 (code 57). Release notes line: "New bottom menu: Today, Progress and
your AI coach are now one tap away."
