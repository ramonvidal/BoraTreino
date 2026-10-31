# Bora pro Treino!

A quick gym logbook for Android: see what you lifted last time on each machine, log
today's sets in a tap or two, follow your routine (A, B, C...) with the app telling you which
one is next, and watch the weight go up. No account, no server: everything stays on your phone.

See [PLAN.md](PLAN.md) for the roadmap and design decisions, and [CLAUDE.md](CLAUDE.md)
for the rules every change follows.

## Installing on a phone

Download **[BoraTreino.apk](https://github.com/ramonvidal/BoraTreino/releases/latest/download/BoraTreino.apk)**
on the phone and open it (allow installing from unknown sources). That link always points
at the newest build and needs no GitHub account. Each build installs over the previous one.
Updates from inside the app come in Phase 6.

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

## Tests

- App logic: `./gradlew :core:model:test testDebugUnitTest`
- Module structure rules: `./gradlew -p build-logic :rules:test`

The UI tests also save screenshots of each tab (light, dark, largest text) to
`build/outputs/roborazzi`; CI uploads them as the `screenshots` artifact.
