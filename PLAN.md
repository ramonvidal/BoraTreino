# Bora pro Treino! plan

A gym logbook for Android: **simple, quick, out of the way**. Walk up to a machine,
see what you lifted last time, log today's sets in a tap or two, and watch the weight go up
over the weeks. The phone goes back in your pocket in under 10 seconds.

Built the same way as ChoreMe and PodOuvir: small Gradle modules with rules checked on
every build (see `CLAUDE.md`), logic in plain Kotlin with unit tests, every screen drawn
from its state and screenshotted, and GitHub Actions publishing an installable APK.

## The one rule: every interaction is quick

Everything below is judged against this. Between sets you have sweaty hands, a timer running
and no patience, so:

- **Logging a set that matches last time is one tap** ("Same again"). Changing the weight is
  one more tap on a big − / + stepper. No keyboard while training, ever.
- **No "Start workout" step.** The first set you log starts the day's session; it closes
  itself after 3 hours with nothing logged (or with "Done").
- **The app opens where you are**: on the machine you used last if you're mid-session,
  otherwise on today's routine.
- **Big targets, one hand**: steppers and the main button sit in the bottom half of the screen,
  at least 56 dp tall.
- **Nothing to fill in to get going**: a starter list of common machines, and a routine is
  optional. Typing (names, notes) only happens when setting things up, never mid-set.

## Decisions so far

| Topic | Decision |
|---|---|
| Platform | Android only: Kotlin + Jetpack Compose (Material 3). Minimum Android 8.0 (API 26) |
| Structure | **As modular as possible**: `:core:*` and `:feature:*` Gradle modules, rules in `CLAUDE.md`, checked on every build (same `module-rules` check as ChoreMe and PodOuvir) |
| Accounts and sync | **None.** Everything lives on the phone: no sign-in, no server, no Firebase. A backup file (export / import) moves your history to a new phone, and Android's own backup is on |
| Name and icon | The app is called **Bora pro Treino!** (repo and code: BoraTreino, `com.boratreino`). Icon: the guy with the dumbbell, the Salvador church and the palm tree (`docs/icon-original.png`) |
| Tab names | **Today, Calendar, Progress, Routines** (Baianês: Treino, Agenda, Evolução, Fichas) |
| Machines | Each machine (or exercise) has a name, a muscle group, a **kind** (weight × reps, bodyweight reps, or time for cardio), a weight **step** (1, 2.5 or 5 kg; or "level" for pin-stack machines numbered 1 to 20) and an optional **setup note** ("seat 4, back pad 2") shown every time you open it. A starter list of ~30 common machines in groups (Legs, Chest, Back, Shoulders, Arms, Core, Cardio) so nothing needs typing on day one |
| Logging a set | The machine screen shows **Last time: 3 × 10 at 40 kg** and today's sets so far. Below: weight stepper, reps stepper (both prefilled from last time) and one big button **Log set**. **Same again** logs the whole of last time's next set in one tap. A logged set can be fixed or removed by tapping it (undo snackbar for 5 s) |
| Session | Starts with the first set, ends after 3 h without a set or with **Done**. Done shows a one-line summary ("6 machines · 18 sets · 3 records") and nothing else to fill in. A session can be given a routine afterwards, or edited from the Calendar |
| Routines ("fichas") | Optional. A routine is a named list of machines, each with a target (sets × rep range, e.g. 3 × 8–12), like the paper ficha from the gym: **Treino A, B, C**. Today suggests the **next one in rotation** (after A comes B), with the machines in order and ticks on the ones done. Any other routine, or none, is one tap away |
| Progression | **Double progression**, worked out in `:core:model`: once every target set reaches the top of the rep range (e.g. 3 × 12), the machine suggests **"Try 42.5 kg"** next time (one step up, reps back to the bottom). If you fail the bottom of the range twice in a row it suggests going back one step. A suggestion is only shown, never forced: the stepper starts on it and one tap goes back |
| Records | A new best (heaviest weight, or most reps at a weight) shows **"New record! 45 kg"** with a tick and a buzz, the same moment as ChoreMe's "Well done" |
| Calendar | Month view, one dot per training day in its routine's colour; tap a day for what you did there. On top: **this week, 2 of 4** (weekly goal, set in Settings) and the current streak of weeks that met the goal (weeks, not days, so rest days never break it) |
| Progress | Per machine: a simple line chart of the **top set weight** per session (3 months, 1 year, all), the record, and the last sessions as a list. A front page with the machines that went up the most this month. Charts drawn with Compose Canvas, no chart library |
| Rest timer | Optional, on by default: logging a set starts a rest countdown (default 90 s, per machine if set) shown on the machine screen and as a notification; it buzzes when it's time. Tap to add 30 s or skip |
| Reminders | Optional: on chosen weekdays at a chosen time, "Bora pro treino? Today: Treino B". Off on days you already trained |
| Units | **kg**. Pounds later if ever asked for |
| Settings | One **Settings** screen from the Today tab's top bar: weekly goal, rest timer, reminders, language, backup export / import, app updates |
| Look | ChoreMe's **"Soft cards"**: light grey page, white cards, black and grey text, **one accent colour** for the main action and the selected tab, and routine colours as the only other colour. Dark mode from day one. Font: Figtree |
| Accent colour | Taken from the icon: the **sunset orange** of the "Treino!" underline, with the icon's **navy** for headings in the logo only (to confirm, see open questions) |
| Pictures | **No emoji.** Muscle groups and machines use line drawings from **Tabler Icons** (MIT); controls use Google's **Material Symbols** (Apache 2.0), copied as vector drawables into `:core:ui` |
| Languages | **English** or **Baianês** (Brazilian Portuguese with Bahia slang: "Bora!", "Arretado, recorde novo!"), picked in Settings or Android's app language settings. Each module's `values-pt-rBR` is made from `translations/baianes.csv`, as in ChoreMe |
| Screenshots | Every screen (light, dark, the largest text size, empty) is drawn from its state alone (`XxxContent`) and screenshotted with Roborazzi on each release (`screenshots.zip`) |
| Offline | Room database on the phone is the only source of truth. Works with no signal, which matters in basement gyms |
| Updates | Same as ChoreMe and PodOuvir: a release per CI build; the app checks the release page once a day and offers "Install" (`:core:update`, `:feature:update`) |
| Cost | **Everything free**: GitHub Actions and Releases. No paid service, no server of our own |

## What a training looks like

1. Open the app: **Today** shows "Treino B · 6 machines", the first one on top with its last
   result.
2. Tap **Leg press**: "seat 5" at the top, "Last time: 3 × 12 at 120 kg · Try 125 kg".
   The weight stepper already reads 125.
3. Do the set, tap **Log set** (one tap). Rest timer starts.
4. Next set: **Log set** again; if you got fewer reps, tap − on reps first (two taps).
5. Swipe or tap back: Leg press is ticked, the next machine is highlighted.
6. Leave the gym. The session closes itself; the Calendar has a dot for today.

## Data on the phone

Room database, owned by `:core:database`. Plain-Kotlin models in `:core:model`.

```
machines        id, name, muscleGroup, kind (WEIGHT_REPS | BODYWEIGHT_REPS | TIME), step,
                unit (KG | LEVEL), setupNote, restSeconds (null = default), archived
routines        id, name, colour, position
routineItems    routineId, machineId, position, targetSets, repsMin, repsMax
sessions        id, startedAt, endedAt (null = open), routineId (nullable), note
sets            id, sessionId, machineId, position, weight, reps, seconds, loggedAt
```

The rules for progression suggestions, records, session open/close, routine rotation and
weekly streaks live in `core/model` (`ProgressionRules`, `RecordRules`, `SessionRules`,
`RotationRules`, `WeekGoalRules`) and are unit tested on the plain JVM.

## Modules

```
app/                Activity, root screen, bottom navigation
core/model/         Plain Kotlin: machines, routines, sessions, sets and the rules above
core/database/      Room: tables, DAOs, migrations
core/data/          Repositories over the database, backup export / import (JSON)
core/settings/      This phone's choices (DataStore): weekly goal, rest timer, reminders, language
core/notify/        Rest timer notification and training-day reminders (WorkManager, AlarmManager)
core/ui/            Theme, Figtree, steppers, machine row, small charts, shared strings, icons
core/update/        Updating the app itself from the release page (ported from PodOuvir)
feature/
  today/            Today's routine, the machine screen with logging, "Done" summary
  calendar/         Month view, day detail, week goal and streak
  progress/         Per-machine charts, records, "most improved"
  routines/         Machines and routines: add, edit, reorder, starter list
  settings/         The Settings screen
  update/           "Bora pro Treino! 0.4 is available – Install" banner
build-logic/
  convention/       Shared Gradle setup for each kind of module
  rules/            The module structure check
translations/       baianes.csv and i18n.py (from ChoreMe)
```

## Phases

- [ ] **Phase 0: Skeleton.** Modules above (empty where not needed yet), convention plugins
      and the module-rules check copied from PodOuvir, theme with Figtree and the accent
      colour, launcher icon from `docs/icon-original.png`, bottom navigation with
      Today / Calendar / Progress / Routines placeholders, GitHub Actions building the APK,
      running the tests and publishing a release, signing key secret.
- [ ] **Phase 1: Log a set.** Starter machine list, add / edit / archive machines, the machine
      screen with "Last time", steppers, **Log set**, **Same again**, edit and undo a set.
      Sessions open and close on their own. Today lists the machines of the open session.
- [ ] **Phase 2: Routines and progression.** Routines with targets, rotation (next one
      suggested on Today, ticks as you go), double progression suggestions, records with
      "New record!" and a buzz.
- [ ] **Phase 3: Calendar.** Month view with routine-coloured dots, day detail (edit or
      delete a past session, log one you forgot), weekly goal and streak of weeks.
- [ ] **Phase 4: Progress.** Per-machine chart and history, records list, most improved
      this month.
- [ ] **Phase 5: Comfort.** Rest timer with notification, training-day reminders, Settings
      screen, backup export / import, Baianês translation.
- [ ] **Phase 6: Updates from inside the app.** Port `:core:update` and `:feature:update`;
      README with the fixed "latest APK" link.
- [ ] **Extras (later).** Home-screen widget ("Bora!" opens today's routine), photo of a
      machine's setup, body weight log, pounds, Health Connect export, Wear OS tile for
      logging from the watch.

## Open questions

Each has a proposed answer, used unless you say otherwise.

1. **Default language**: English with Baianês as an option (like ChoreMe), or **Baianês by
   default** given the name and icon? *Proposed: Baianês by default, English as the option.*
2. **Accent colour**: sunset orange from the icon, or ChoreMe's green to keep the apps as one
   family? *Proposed: orange.*
3. **Routines**: do you train from a fixed ficha (A / B / C rotation) or pick freely each day?
   *Proposed: rotation suggested, free choice one tap away.*
4. **Pin-stack machines**: does your gym show kg on the machines, or numbered plates?
   *Proposed: kg by default, "level" per machine.*
5. **Cardio**: is time on the treadmill / bike worth logging from Phase 1, or later?
   *Proposed: the TIME kind exists from Phase 1, minutes only, no distance.*
