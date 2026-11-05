# Bora pro Treino! plan

A gym logbook for Android: **simple, quick, out of the way**. Walk up to a machine,
see what you lifted last time, log today's sets in a tap or two, and watch the weight go up
over the weeks. The app also tells you **which routine is next** (A, B or C), whatever day
of the week it is. The phone goes back in your pocket in under 10 seconds.

Part of the same family as ChoreMe (Nos Corre!) and PodOuvir: the same look, the same way of
building (small Gradle modules with rules checked on every build, see `CLAUDE.md`; logic in
plain Kotlin with unit tests; every screen drawn from its state and screenshotted; GitHub
Actions publishing an installable APK) and the same in-app updates.

## The one rule: every interaction is quick

Everything below is judged against this. Between sets you have sweaty hands, a timer running
and no patience, so:

- **Logging a set that matches last time is one tap** ("Same again"). Changing the weight is
  one more tap on a big − / + stepper. No keyboard while training, ever.
- **No "Start workout" step.** The first set you log starts the day's session; it closes
  itself after 3 hours with nothing logged (or with "Done").
- **The app opens where you are**: on the machine you used last if you're mid-session,
  otherwise on the next routine.
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
| Name and icon | The app is called **Bora pro Treino!** (repo and code: BoraTreino, `com.boratreino`). Icon: the guy with the dumbbell, the Salvador church and the palm tree (`docs/icon-original.png`). The icon is the only place with illustration and warm colours; the app itself stays plain |
| Tab names | **Today, Calendar, Progress, Routines** |
| Machines | Each machine (or exercise) has a name, a muscle group, a weight **step** (1, 2.5 or 5 kg) and an optional **setup note** ("seat 4, back pad 2") shown every time you open it. Weights are always **kg, as shown on the machine**. A starter list of ~30 common machines in groups (Legs, Chest, Back, Shoulders, Arms, Core) so nothing needs typing on day one. Bodyweight exercises (pull-ups, dips) log reps only, with optional added weight |
| Your gym first | The **Routines tab starts with the gym's inventory**: every machine and exercise your gym has, grouped by muscle group, with a short explanation ("Register here everything your gym has. Your routines (fichas) are built from this list"). Routines come below it. On a fresh install the starter list is open, so registering the gym is the first thing you do |
| Starter list | About 100 common machines and exercises (machines, barbell, dumbbell, cable, bodyweight) in groups, searchable (accents ignored), each with an English and a Portuguese name: a machine added while the app is in Baianês gets the Portuguese name ("Supino reto"). Tick several and add them in one tap ("Add 5 selected"). Names already added (in either language) are not offered again |
| Deleting a machine | A trash icon on each row of the gym list, and **Delete** in the machine editor. A machine **without logged sets** is deleted after a confirmation that names the routines it will leave; a machine **with logged sets** can't be deleted without losing history, so the dialog offers **Archive instead** (hidden from lists, history kept) |
| Changing a ficha during a training | Not a form, only questions at the moment it matters, never on the logging path: picking a machine that is **not in the active ficha** from "Add machine" asks "Add it to Treino B?" (**Add to ficha** / **Just today**); a ficha machine you haven't started has a close icon: "Skip it today" (this training only) or "Remove from Treino B" (edits the ficha). Both have Undo. Free trainings (no ficha) never ask |
| Logging a set | The machine screen shows **Last time: 3 × 10 at 40 kg** and today's sets so far. Below: weight stepper, reps stepper (both prefilled from last time) and one big button **Log set**. **Same again** logs last time's next set in one tap. A logged set can be fixed or removed by tapping it (undo snackbar for 5 s) |
| Session | Starts with the first set, ends after 3 h without a set or with **Done**. Done shows a one-line summary ("6 machines · 18 sets · 3 records") and nothing else to fill in. Sessions can be edited from the Calendar |
| Routines | Optional, and **any number**: A / B / C, A / B, push / pull / legs, one full-body routine, or anything else. A routine is a named list of machines in order, each with a target (sets × rep range, e.g. 3 × 8–12) |
| Which routine is next | Two ways, picked per set of routines: **In turn** (the default, and the owner's case): the next one after the last you did, **whatever the weekday**, so with A / B / C four days a week, A lands on a different day each week and the app keeps count for you. **On fixed weekdays**: Monday is A, Wednesday is B, and so on, for people whose routine is tied to the day. Either way Today opens on the suggested routine, and any other (or none) is one tap away. Training a different routine than suggested moves the turn on from the one you actually did |
| Progression | **Double progression**, worked out in `:core:model`: once every target set reaches the top of the rep range (e.g. 3 × 12), the machine suggests **"Try 42.5 kg"** next time (one step up, reps back to the bottom). If you miss the bottom of the range twice in a row it suggests going back one step. A suggestion is only shown, never forced: the stepper starts on it and one tap goes back |
| Records | A new best (heaviest weight, or most reps at a weight) shows **"New record: 45 kg"** with a tick and a buzz, like ChoreMe's "Well done" |
| Calendar | Month view, one dot per training day, labelled with the routine's letter; tap a day for what you did there. On top: **this week, 2 of 4** (weekly goal, set in Settings) and the streak of weeks that met the goal (weeks, not days, so rest days never break it) |
| Reminders | Optional. On the weekdays and at the time you pick, a notification says what's next: **"Gym today? Next up: Routine B"**. Skipped on days you already trained. If the week is running behind your goal (two sessions left and two days to go), the last reminder of the week says so. Tapping it opens Today on that routine |
| Progress | Per machine: a simple line chart of the **top set weight** per session (3 months, 1 year, all), the record, and the last sessions as a list. A front page with the machines that went up the most this month. Charts drawn with Compose Canvas, no chart library |
| Rest timer | Optional, on by default: logging a set starts a rest countdown (default 90 s, per machine if set) shown on the machine screen and as a notification; it buzzes when it's time. Tap to add 30 s or skip |
| Cardio | **Later phase** (Phase 7). Cardio machines (treadmill, bike, elliptical, rower, stairs) log **time and/or calories**, as the machine's screen shows them, and optionally distance. Same quick logging: steppers prefilled from last time, one tap to log. Progress shows minutes and calories per week |
| Health Connect | **Yes, optional, later** (Phase 8). Google Fit's own APIs are being shut down; **Health Connect** is Google's replacement and what Fitbit, Samsung Health and Google's apps read from. Free, on the phone, no account. Each session is written as a strength-training exercise session with its exercises and reps; cardio adds calories and distance. Health Connect has no field for the kg of each set, so the weight history stays in the app. Write-only at first; reading (heart rate from a watch during the session) maybe later. Switched on in Settings, with Android's own permission screen. Needs Android 9 or newer, so on Android 8 the switch is hidden |
| Units | **kg** only |
| Settings | One **Settings** screen from the Today tab's top bar: weekly goal, reminders, rest timer, language, backup export / import, Health Connect, app updates |
| Look | **The family look, exactly ChoreMe's "Soft cards"**: light grey page (`#F4F4F2`), white cards, black and grey text, and **ChoreMe's green** (`#2E6B52`) as the one accent, for the main action and the selected tab. Red only for errors. Font: Figtree. Dark mode from day one. No mascot, illustrations or playful copy inside the app: plain, calm and quick. The theme is copied from ChoreMe's `:core:ui` so the three apps match, and stays in step with it |
| Routine colours | None. Routines are told apart by their **letter or short name** in a grey badge ("A", "B", "Push"), so the green stays the only colour |
| Pictures | **No emoji.** Muscle groups and machines use line drawings from **Tabler Icons** (MIT); controls use Google's **Material Symbols** (Apache 2.0), copied as vector drawables into `:core:ui`, the same sets ChoreMe uses |
| Languages | **English** by default, with **Baianês** (Brazilian Portuguese with Bahia slang) as an option in Settings or Android's app language settings. Each module's `values-pt-rBR` is made from `translations/baianes.csv` by `translations/i18n.py`, as in ChoreMe |
| Screenshots | Every screen (light, dark, the largest text size, empty) is drawn from its state alone (`XxxContent`) and screenshotted with Roborazzi on each release (`screenshots.zip`) |
| Offline | Room database on the phone is the only source of truth. Works with no signal, which matters in basement gyms |
| Updates | Same as ChoreMe and PodOuvir: a release per CI build; the app checks the release page once a day and offers "Install" (`:core:update`, `:feature:update`) |
| Cost | **Everything free**: GitHub Actions and Releases, Health Connect. No paid service, no server of our own |

## What a training looks like

1. Thursday, 18:00: "Gym today? Next up: Routine B". Tap it.
2. **Today** shows "Routine B · 6 machines", the first one on top with its last result.
3. Tap **Leg press**: "seat 5" at the top, "Last time: 3 × 12 at 120 kg · Try 125 kg".
   The weight stepper already reads 125.
4. Do the set, tap **Log set** (one tap). Rest timer starts.
5. Next set: **Log set** again; if you got fewer reps, tap − on reps first (two taps).
6. Go back: Leg press is ticked, the next machine is highlighted.
7. Leave the gym. The session closes itself; the Calendar shows "B" on today, the week reads
   3 of 4, and next time the app suggests Routine C.

## Data on the phone

Room database, owned by `:core:database`. Plain-Kotlin models in `:core:model`.

```
machines        id, name, muscleGroup, kind (WEIGHT_REPS | BODYWEIGHT_REPS | CARDIO),
                step, setupNote, restSeconds (null = default), archived
routinePlans    id, scheduleMode (IN_TURN | WEEKDAYS)          the set of routines in use
routines        id, planId, name, shortName, position, weekdays[] (WEEKDAYS mode only)
routineItems    routineId, machineId, position, targetSets, repsMin, repsMax
sessions        id, startedAt, endedAt (null = open), routineId (nullable), note,
                healthConnectId (null = not written)
sets            id, sessionId, machineId, position, weightKg, reps,
                seconds, calories, distanceM (cardio only), loggedAt
```

`CARDIO` and the cardio columns are in the schema from the start (empty until Phase 7), so
cardio needs no migration of existing data.

The rules for progression suggestions, records, session open/close, **which routine is
next**, reminder text and weekly streaks live in `core/model` (`ProgressionRules`,
`RecordRules`, `SessionRules`, `NextRoutineRules`, `ReminderRules`, `WeekGoalRules`) and are
unit tested on the plain JVM.

## Modules

```
app/                Activity, root screen, bottom navigation
core/model/         Plain Kotlin: machines, routines, sessions, sets and the rules above
core/database/      Room: tables, DAOs, migrations
core/data/          Repositories over the database, backup export / import (JSON)
core/settings/      This phone's choices (DataStore): weekly goal, reminders, rest timer, language
core/notify/        Rest timer notification and training-day reminders (WorkManager, AlarmManager)
core/health/        Health Connect: permission check, writing sessions
core/ui/            Theme (ChoreMe's), Figtree, steppers, machine row, small charts, shared strings, icons
core/update/        Updating the app itself from the release page (ported from PodOuvir)
feature/
  today/            Next routine, the machine screen with logging, "Done" summary
  calendar/         Month view, day detail, week goal and streak
  progress/         Per-machine charts, records, "most improved"
  routines/         Machines and routines: add, edit, reorder, schedule mode, starter list
  settings/         The Settings screen
  update/           "Bora pro Treino! 0.4 is available – Install" banner
build-logic/
  convention/       Shared Gradle setup for each kind of module
  rules/            The module structure check
translations/       baianes.csv and i18n.py (from ChoreMe)
```

## Phases

- [x] **Phase 0: Skeleton.** Modules above (empty where not needed yet; `:core:health` waits for Phase 8), convention plugins
      and the module-rules check copied from PodOuvir, ChoreMe's theme with Figtree,
      launcher icon from `docs/icon-original.png`, bottom navigation with
      Today / Calendar / Progress / Routines placeholders, GitHub Actions building the APK,
      running the tests and publishing a release, signing key secret.
- [x] **Phase 1: Log a set.** Starter machine list, add / edit / archive machines, the machine
      screen with "Last time", steppers, **Log set**, **Same again**, edit and undo a set.
      Sessions open and close on their own. Today lists the machines of the open session.
- [x] **Phase 2: Routines and progression.** Any number of routines with targets, "in turn"
      and "fixed weekdays" modes, next routine on Today (ticks as you go), double
      progression suggestions, records with a buzz.
- [x] **Phase 3: Calendar.** Month view with the routine letter on each training day, day
      detail (edit or delete a past session, log one you forgot), weekly goal and streak of
      weeks.
- [x] **Phase 4: Reminders and comfort.** Training-day reminders naming the next routine,
      behind-the-goal nudge, rest timer with notification, Settings screen, backup
      export / import.
- [x] **Phase 5: Progress.** Per-machine chart and history, records list, most improved
      this month.
- [x] **Phase 6: Updates and language.** Port `:core:update` and `:feature:update`; README
      with the fixed "latest APK" link; Baianês translation.
- [x] **Feedback round 1** (from the first gym use). The Routines tab leads with the gym's
      inventory and says so; delete a machine (or archive it when it has history); a starter list
      of about 100 machines and exercises in English and Portuguese with search and multi-select;
      the questions to update the ficha when a machine is added or skipped during a training; the
      new launcher icon (`docs/icon-original.png`).
- [x] **Exercise pictures.** Everkinetic illustrations (CC BY-SA 4.0) on matching starter
      machines, credited in Settings and the README (`docs/exercise-images/`).
- [x] **Phase 7: Cardio.** Cardio machines in the starter list, logging time and/or
      calories (and distance if wanted), weekly minutes and calories in Progress.
      No database migration: sets already had seconds, calories and distance.
- [x] **Phase 8: Health Connect.** `:core:health`, the Settings switch and permission,
      writing each finished session (and cardio calories and distance); sessions edited
      later are updated there too. There is no `healthConnectId` column: each session is written
      under a stable client record id (`boratreino-session-<id>`) with a content-hash version, so
      writing again replaces the record and deleted sessions are removed by id; the ids already
      sent are kept in `HealthSettings`. Needs the Play Console Health Connect declaration
      (`docs/PLAY_STORE_GUIDE.md`, step 4b) before rollout.
- [x] **Feedback round 2** (from recent gym use). Settings is its own tab; the rest timer has a big card
      on the machine screen, a global banner on every other screen and an optional beep; the
      ficha can be edited from Today (move up / down, remove) before or during a training;
      the Routines tab is split into **Routines | Gym**, routines are cards with a menu
      (edit, move, duplicate, delete) and the routine editor saves every change at once.
- [ ] **Phase 9: Set types.** A set is Normal (default), Warm-up, Drop or Failure. Warm-up sets
      never count for records, progression, the top-weight chart, volume or "sets done" ticks.
      Logging stays as quick as today: no extra tap, the type only changes when you ask.
      Needs a Room migration (`workout_sets.type`, default NORMAL) with its test, and a
      backup format bump (old backups import as Normal). Controls (decided by the owner): a small
      **Warm-up** chip next to Log on the machine screen (off by default, so the normal path
      is unchanged), and tapping a logged set opens its edit sheet, which also changes the
      type (Normal, Warm-up, Drop, Failure).
- [ ] **Phase 10: Warm-up calculator.** On a machine with a working weight, an optional
      suggestion of warm-up sets (for example 50 % x 8, 70 % x 5, 85 % x 3, rounded to the
      machine's step and never below the empty bar or stack). One tap logs them as warm-up
      sets; never forced, can be switched off in Settings. Rules in `:core:model`
      (`WarmupRules`), plain JVM tests. Needs Phase 9.
- [ ] **Phase 11: Estimated 1RM.** Estimated one-rep max from each working set (Epley by
      default: weight x (1 + reps / 30); only sets of 1 to 12 reps, warm-ups excluded). Shown
      per machine as a chart metric next to Top weight, as a "best estimated 1RM" card and in
      the records list. No schema change. Rules in `:core:model` (`OneRepMaxRules`).
- [ ] **Phase 12: Statistics.** A Stats section in the Progress tab with a period picker (4
      weeks, 3 months, 1 year, all) beyond the single-machine chart. Charts in Compose Canvas,
      no chart library; every number computed in `:core:model` (`StatsRules`) and unit tested.
      First list (the owner will add more):
      volume per week and per muscle group; hard sets per muscle group per week (balance);
      days since each muscle group was trained; training frequency, best week and consistency
      against the weekly goal; strength trend (estimated 1RM change over 4 / 12 weeks) per
      machine and overall; records timeline and records per month; plateau detection (no
      progress for N sessions) tied to the progression suggestion; rep-range distribution;
      average session length and time between sets; cardio weekly minutes, calories,
      distance and pace; body weight against strength once a body weight log exists.
- [ ] **Phase 13: Same as last time (do this first).** The owner's priority: it keeps the app
      usable on tired days. One tap on the Today card ("Same as last time") logs the whole
      routine exactly as the last session of that routine: every machine, the same number of
      sets, weights and reps (cardio: the same minutes, calories and distance). Machines without
      a last time are skipped; machines skipped last time stay skipped. It creates or fills the
      open session, shows one Undo snackbar for the whole thing, and everything can still be
      fixed afterwards (Today or Calendar). The same action is offered when logging a forgotten
      day from the Calendar. Today there is only **Same again** per machine and per set. Rules in
      `:core:model` (`RepeatSessionRules`, plain JVM tests); no schema change; a repeated
      session is written to Health Connect like any other. Cheaper variant to consider: a second
      button that applies the progression suggestions ("Same, with suggested weights").
- [ ] **Phase 14: Daily energy (basal metabolism).** The owner wants to know how many calories to
      eat on each day. The app **does not track food or calories eaten** (another app does):
      it only shows an estimate of the day's need. Proposal:
      * Settings gets a **Your body** card: sex, birth year, height and weight (the weight
        later comes from the body weight log), a goal adjustment (maintain by default, or a
        small surplus or deficit in %), and either the computed basal metabolic rate or a value the owner types
        in ("I know my BMR", for example from a smart scale or watch).
      * Basal rate by Mifflin-St Jeor: 10 x kg + 6.25 x cm - 5 x age + 5 (men) or - 161 (women).
      * The day's need: basal rate x an everyday activity factor, plus an estimate of the
        training burn when the day has a session (cardio calories logged, otherwise a MET
        estimate from duration and body weight), plus the goal adjustment.
      * Shown as one quiet line on Today ("About 2,450 kcal today; rest day about 2,150") and in
        the Calendar day detail; it can be hidden in Settings. Never on the logging path.
      * Everything stays on the phone; it is an estimate, worded as one, and not medical advice.
      Rules in `:core:model` (`EnergyRules`), plain JVM tests. Needs the body weight log from
      the Extras (moved up).
- [ ] **Extras (later).** Home-screen widget (opens the next routine), photo of a machine's
      setup, body weight log, reading heart rate from Health Connect, Wear OS tile.

## Settled questions

- **Language**: English by default, Baianês as an option.
- **Look**: the family look, ChoreMe's Soft cards and green, no in-app personality.
- **Routines**: the owner trains A / B / C in turn, four days a week; "in turn" is the
  default, "fixed weekdays" is there for others.
- **Weights**: kg, as shown on the machines.
- **Cardio**: done (Phase 7), time and/or calories, distance if wanted.
- **Health Connect**: yes, optional, write-only, done (Phase 8).
- **Supersets**: not planned (owner's decision). Each machine is logged on its own.
- **Set types**: yes, kept simple: Normal by default, the others only when asked (Phase 9); controls decided: a Warm-up chip next to Log and the type in the set's edit sheet.
- **Same as last time**: yes, one tap for the whole routine (Phase 13), the owner's top priority.
- **Food and calories eaten**: not planned, another app does it. The app only estimates the day's energy need (Phase 14).
- **Warm-up calculator and estimated 1RM**: yes (Phases 10 and 11).
- **More statistics**: yes, beyond the progress plots (Phase 12); the owner keeps adding ideas to that list.

## Open questions

- Phase 12: the owner keeps adding statistics ideas to the list; the suggested list stands for now.
- Phase 14: which activity factor to use for an ordinary day (proposal: 1.2, sedentary, since the
  training burn is added separately), and whether the goal adjustment (surplus or deficit) is wanted.
- Phase 13: whether "Same, with suggested weights" is wanted as a second button.

Suggested order: 13, 9, 11, 10, 12, 14.
