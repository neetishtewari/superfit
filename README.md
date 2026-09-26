# Superfit

Superfit is an Android app for tracking food, training and recovery. You log meals and workouts in plain language, and Gemini turns them into calories and macros using your own Gemini API key. Health Connect supplies steps, calories burned and sleep. On top of that, Superfit adds coaching, streaks, a diet quality score and daily reminders.

## Stack

- Kotlin, Jetpack Compose and Navigation 3
- Room for on-device storage (schemas are exported to `app/schemas`)
- Firebase Auth and Firestore for sign-in and cloud sync (`firestore.rules`)
- Health Connect for activity and sleep
- WorkManager for background sync and reminders
- Google AI client SDK for Gemini

## Project layout

| Path | What's there |
| --- | --- |
| `app/src/main/java/com/superfit/app/data` | Room entities, DAOs, migrations, Firebase sync, Health Connect, workers |
| `app/src/main/java/com/superfit/app/domain` | Engines for nutrition parsing, food quality, coaching, streaks and physiology |
| `app/src/main/java/com/superfit/app/ui` | Compose screens and view models (auth, onboarding, dashboard, history) |
| `app/src/test` | JVM unit tests, including Robolectric database migration tests |
| `public/` | Firebase Hosting site (privacy policy, account deletion page) |
| `store/` | Play Store listing assets and the Data safety form export |

## Building

Requirements: JDK 17 and the Android SDK (compileSdk 36).

```
./gradlew testDebugUnitTest   # unit tests
./gradlew assembleDebug       # debug APK (installs as "Superfit (Dev)")
```

CI (`.github/workflows/ci.yml`) runs both on every pull request and every push to `main`. It also fails if the build changes a Room schema file under `app/schemas`, so commit those files with any entity change.

### Release signing

Release signing secrets are never committed. Add them to `local.properties`, which is gitignored:

```
superfit.storePassword=...
superfit.keyPassword=...
# optional, these are the defaults:
superfit.storeFile=superfit-release.jks
superfit.keyAlias=superfit-key
```

You can also set them as the environment variables `SUPERFIT_STORE_PASSWORD`, `SUPERFIT_KEY_PASSWORD`, `SUPERFIT_STORE_FILE` and `SUPERFIT_KEY_ALIAS`. The keystore path is relative to the repository root. Without these secrets, `assembleRelease` builds an unsigned APK.

## Database changes

When you change an entity:

1. Bump `version` in `SuperfitDatabase`.
2. Add a `Migration` and include it in `SuperfitDatabase.ALL_MIGRATIONS`.
3. Extend `SuperfitDatabaseMigrationTest` to cover the new version.
4. Commit the new schema JSON that the build writes to `app/schemas`.
