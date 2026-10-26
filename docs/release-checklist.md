# Release checklist

Run through this on the dev app ("Superfit (Dev)") before each Play upload. The dev build is shrunk
like the Play build, so these checks catch what R8 might strip.

## On the phone

1. With the phone on USB, run `scripts/device-smoke-test.sh` from the release commit. It builds and
   installs the dev app, runs every background job and fails on crashes or WorkManager errors.
2. Log a meal by typing, by voice and with the camera.
3. Open the coach chat and ask a question by typing and by voice. Close and reopen it: the conversation
   is still there. Tell it something to remember (e.g. "my knee hurts") and check Settings → What my AI coach knows.
4. Add the home-screen widget. It shows calories left within a few seconds, not a loading circle.
5. Tap the widget's mic and camera buttons. Each opens Superfit ready to log.
6. If workouts from other apps are on, check today's workouts appear on Home.

## Security checks

Do these for every release too. CI already covers the code side on every pull request: Android Lint
(which includes Android's security checks), formatting, and `scripts/check-permissions.sh`, which
fails if the app requests a permission not listed in `app/permissions.txt`.

1. CI is green on the release commit.
2. If `app/permissions.txt` changed since the last release, update Play Console's Data safety form
   (and the Health apps declaration for Health Connect permissions) before submitting.
3. If `firestore.rules` changed, deploy it (`firebase deploy --only firestore:rules`). Every rule
   must keep data under `users/{userId}` readable and writable only by that signed-in user.
4. GitHub → Security → Secret scanning shows no open alerts (no keys or passwords committed).
5. Firebase console → App Check: requests are mostly verified, with no spike of unverified ones.
6. AI Studio → Billing: the Gemini prepaid balance covers at least the next month.

## Upload

Build the signed bundle (`./gradlew bundleRelease`), upload it to Internal testing, install it
from the Play link, repeat step 4, and promote to Production. Check the Production track in Play
Console shows the new version: a release that stays on Internal testing never reaches users.
