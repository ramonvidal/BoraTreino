# Bora pro Treino!

A quick gym logbook for Android: see what you lifted last time on each machine, log
today's sets in a tap or two, follow your routine (A, B, C...) with the app telling you which
one is next, and watch the weight go up. No account, no server: everything stays on your phone.

See [PLAN.md](PLAN.md) for the roadmap and design decisions, and [CLAUDE.md](CLAUDE.md)
for the rules every change follows.

## Features

- One-tap logging with "Same again", steppers for weight and reps, no keyboard.
- Your gym registered once: about 100 starter machines (many with pictures) and your own.
- Routines (A, B, C...) with the app telling you which one is next; weight suggestions and records.
- Cardio machines: minutes, calories and optional distance, with weekly totals in Progress.
- Calendar with a weekly goal, charts per machine, reminders and a rest timer.
- Optional Health Connect: send each finished training (and cardio calories and distance) to
  Health Connect from Settings. Write-only, off by default.
- Optional daily energy estimate from your body details (typed in Settings, kept on the phone,
  never part of the backup); it only estimates, it does not track food.
- Backup / restore with a file; English and Baianês.

## Installing on a phone

Download **[BoraTreino.apk](https://github.com/ramonvidal/BoraTreino/releases/latest/download/BoraTreino.apk)**
on the phone and open it (allow installing from unknown sources). That link always points
at the newest build and needs no GitHub account. Each build installs over the previous one.

### Updates

After that first install, the app checks for new builds itself (every few hours, or right away
from **Settings → App updates → Check for updates**) and offers to install them: tap **Install**
on the banner at the top of the screen and confirm. The first time, Android asks you to allow
Bora pro Treino! to install apps.

### Languages

English by default, with **Baianês** (Brazilian Portuguese with Bahia slang) as an option under
**Settings → Language**, or in Android's own per-app language settings. The texts live in
[translations/baianes.csv](translations/baianes.csv); `python3 translations/i18n.py check` verifies
that nothing is missing and that placeholders match (CI runs it), and `apply` regenerates each
module's `values-pt-rBR` from the table.

## Tech stack

Kotlin, Jetpack Compose (Material 3), Hilt; Room, DataStore and WorkManager as the phases
that need them land. Minimum Android 8.0 (API 26).

## Building

Every push runs the **Android build** workflow: module rules tests, unit and screenshot
tests, the APKs (artifact `boratreino-debug-apk`), and a release with `BoraTreino.apk` and
`screenshots.zip`. Locally, open the folder in Android Studio and run `app`.

### Signing key

Android only installs an update signed with the same key as the installed app, so every
published build is signed with one fixed key (`boratreino-release.keystore`, alias and
passwords `boratreino`). It is never committed: CI writes it from the repository secret
`SIGNING_KEYSTORE_BASE64` (the keystore file, base64-encoded). Without the secret, CI still
builds and tests but publishes no release. Keep a copy of the keystore somewhere safe:
losing it means the next build can't update installed copies.

### Google Play build

The app has two flavors. `sideload` (`com.boratreino`) is the GitHub APK above, with the in-app
updater and the key above. `play` (`com.punyplatypus.boratreino`) is the Google Play bundle: no
updater, no `INTERNET` or `REQUEST_INSTALL_PACKAGES`, language splits off so the in-app language
choice still works. Build them with `./gradlew assembleSideloadRelease bundlePlayRelease`
(`app/build/outputs/apk/sideload/release/app-sideload-release.apk` and
`app/build/outputs/bundle/playRelease/app-play-release.aab`).

The bundle is signed with an upload key (alias `upload`), kept apart from the APK key and never
committed. CI writes it to `app/boratreino-upload.keystore` from the secrets
`SIGNING_UPLOAD_KEYSTORE_BASE64` (the keystore, base64-encoded) and `UPLOAD_STORE_PASSWORD` (a PKCS12
keystore uses it for the key as well). When both exist, the release also carries `BoraTreino.aab` (also the
`boratreino-play-aab` artifact); otherwise CI warns and signs with a throwaway key.

## Exercise pictures

Many machines and exercises show a drawing of the movement (start and end position). The illustrations are from
[Everkinetic](https://github.com/everkinetic/data) by Greg Priday, licensed
[CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/); they were converted to WebP, resized and placed on a white
background. The pictures are the only part of this repository under that license: see `docs/exercise-images/README.md` for the
mapping and the full notice. The app shows the credit in Settings and in the picture view.

## Tests

- App logic: `./gradlew :core:model:test testDebugUnitTest`
- Module structure rules: `./gradlew -p build-logic :rules:test`

The UI tests also save screenshots of each tab (light, dark, largest text) to
`build/outputs/roborazzi`; CI uploads them as the `screenshots` artifact.
