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
