# Release checklist

Run through this on the dev app ("Superfit (Dev)") before each Play upload. The dev build is shrunk
like the Play build, so these checks catch what R8 might strip.

1. With the phone on USB, run `scripts/device-smoke-test.sh` from the release commit. It builds and
   installs the dev app, runs every background job and fails on crashes or WorkManager errors.
2. Log a meal by typing, by voice and with the camera.
3. Open the coach chat and send a message.
4. Add the home-screen widget. It shows calories left within a few seconds, not a loading circle.
5. Tap the widget's mic and camera buttons. Each opens Superfit ready to log.
6. If workouts from other apps are on, check today's workouts appear on Home.

Then build the signed bundle (`./gradlew bundleRelease`), upload it to Internal testing, install it
from the Play link, repeat step 4, and promote to Production.
