# Body fat, muscle and resting burn in Insights and the recap

Neetish, 2026-11-02: when Health Connect has body fat and BMR data and it changed, show it in Insights and the monthly recap. When it didn't change, say nothing.

- Source: the user's own smart scale readings (`BodyImporter.mine`, already filtered for "is this you?").
  - Body fat, lean mass, BMR, and weight.
  - When the scale sends no lean mass, it is worked out as weight x (1 - body fat).
- Each end is the average of the readings near it, so one watery reading can't make news (`BodyTrend`).
  - Insights: the last 10 days against 3 to 5 weeks ago.
  - Recap: the month's start (from a week before to the 10th) against its last 10 days.
- Shown only when something moved more than a home scale wobbles: body fat 0.5 points, muscle 0.3 kg, or resting burn 20 kcal a day.
- Insights, Your week: one body line, goal-aware.
  - Going well: "Body fat down X points" (adds "with muscle kept" when it was), "Muscle up", or "Resting burn up".
  - Watch out: "Some of the loss is muscle" (losing, muscle down 0.3 kg or more, fat not clearly down), or "Body fat up X points" (1 point or more, unless building muscle and muscle went up).
  - A rise in resting burn rides along in the detail. A fall is never flagged, because it is normal as weight comes down.
- Recap: a "Body" card right after Goal.
  - Big number: body fat points, otherwise muscle kg, otherwise resting burn.
  - Rows: from → to for each measure the scale sends. Good moves are shown in the card colour, the rest in amber.
- Saved recap words moved to v3 so finished months are rewritten with the new card.
- Nothing is estimated and there are no new permissions. These Health Connect types were already read for the Journey Body card.
