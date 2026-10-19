# Release checklist

Run through this on the dev app ("Superfit (Dev)") before each Play upload. The dev build is shrunk
like the Play build, so these checks catch what R8 might strip.

1. Build and install the dev app from the release commit: `./gradlew installDebug`.
2. Open the app and log a meal by typing, by voice and with the camera.
3. Open the coach chat and send a message.
4. Add the home-screen widget. It shows calories left within a few seconds, not a loading circle.
5. Tap the widget's mic and camera buttons. Each opens Superfit ready to log.
6. If workouts from other apps are on, check today's workouts appear on Home.
7. Check the logcat for crashes: `adb logcat -d | grep -E "FATAL|AndroidRuntime|WM-"`.

Then build the signed bundle (`./gradlew bundleRelease`), upload it to Internal testing, install it
from the Play link, repeat step 4, and promote to Production.
