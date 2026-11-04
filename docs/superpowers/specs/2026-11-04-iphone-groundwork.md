# iPhone groundwork

Approved by Neetish on 4 Nov 2026 ("lets start the groundwork"). Background: `notes/superfit-on-ios.md`
and the growth review (https://claude.ai/artifact/CTtuwaPTo3AvjPygxjcwrs).

The goal is to get the Android app ready to share most of its code with an iPhone app (Kotlin
Multiplatform). Users don't notice anything. Each step ships in a normal Android release and is
tested on the dev app first.

## Steps

1. **Cloud ids for entries** (this change). Every meal, workout and weight gets a `cloudId`, which
   becomes its name in the cloud backup. New entries get a random id (`kotlin.uuid`, which also
   works on iPhone). Existing entries keep their old number as their cloud id, so their backups
   still match and nothing is uploaded again. A restore gives entries new local numbers and skips
   any whose cloud id the phone already has. Two devices on one account no longer overwrite each
   other's entries. Database version 8 to 9.
2. **Shared module.** Add a `shared` Kotlin Multiplatform module next to `app`, Android target only
   at first. The app builds and behaves exactly as before.
3. **Move the brains.** Move the Android-free logic (coaching patterns, Diet Quality, streaks,
   calories, recap, meal items) into `shared`. Replace `org.json` with kotlinx.serialization and
   `java.time` with kotlinx-datetime where needed. Existing unit tests move with the code.
4. **Move the database.** Room supports Kotlin Multiplatform, so the entities and DAOs move to `shared`.
5. **Then the iPhone work** (needs Neetish's Mac with Xcode, an Apple Developer account and an
   iPhone): an `iosApp` folder, shared screens, then the iPhone-only parts.

## Not in scope

- Live sync between two devices that are both in use. The backup still restores onto a new device;
  this change only stops the two from overwriting each other.
- Renaming the cloud documents of existing entries.

## Testing for step 1

- Migration test: databases from versions 1 to 3 upgrade to 9, and existing meals get their number as their cloud id.
- DAO tests: a restore skips an entry already on the phone with the same cloud id, and adds another
  device's entry even when both devices numbered it the same.
- Dev app on the phone: log, edit and delete a meal, a workout and a weight. Each should appear, change
  and disappear under its new id in Firestore (`users/{uid}/nutrition`, `workouts`, `weights`).
- Sign out and back in on the dev app: history comes back once, with no duplicates.
