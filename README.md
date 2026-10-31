# Bora pro Treino!

A quick gym logbook for Android: see what you lifted last time on each machine, log
today's sets in a tap or two, follow your routine (A, B, C...) with the app telling you which
one is next, and watch the weight go up. No account, no server: everything stays on your phone.

See [PLAN.md](PLAN.md) for the roadmap and design decisions, and [CLAUDE.md](CLAUDE.md)
for the rules every change follows.

## Status

Planning. The first installable build comes with Phase 0 (see the plan). Once it exists,
the APK will be at a fixed "latest release" link here, and the app will update itself from
inside, like ChoreMe and PodOuvir.

## Tech stack (planned)

Kotlin, Jetpack Compose (Material 3), Hilt, Room, DataStore, WorkManager.
Minimum Android 8.0 (API 26).
