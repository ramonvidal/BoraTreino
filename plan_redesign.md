# Bora pro Treino!: UI / UX redesign plan

Based on the CI screenshots of v0.210 (Today, machine screen, Routines / Gym / library, Calendar, Progress / Stats,
Settings, the training summary). Nothing here is built yet. Section 6 records the decisions taken; the next step is the mock-ups (R0).

## 1. Why it does not feel pleasant or intuitive today

**It looks flat and anonymous**

- Every screen is the same recipe: light grey page, white cards, one green. Nothing is more important than
  anything else, so the eye has nowhere to land.
- The new icon is warm and lively (yellow, orange, pink, a happy character). The app has none of that. The icon
  promises one product and the app delivers another.
- Text is nearly all one size and weight. "Next up: Routine B", "Training now", card titles and row names
  compete. Small grey labels (12 sp, light grey) are hard to read in a gym.
- Too many outlines: outlined chips, outlined buttons, the outlined "current" row, outlined legend squares.
  Outlines read as noise, not as structure.
- Exercise pictures are inconsistent: some rows have a line drawing, others a generic grey tile.

**It is not obvious what to do next**

- Today has no single main action. The thing you actually want (continue with the next exercise) is not a
  button at all; you have to know to tap a row. Meanwhile three large green or outlined buttons compete:
  "Same as last time", "Same, with suggested weights", "Add machine", plus "Done".
- The A / B / C / Free chips are unexplained. "Free" means "train without a routine", which nobody guesses.
- A rest day shows a list of machines under "Rest day", which reads as a contradiction.
- The machine screen states facts as loose text lines ("Seat 4", "Last time…", "Target…", "Try 125 kg").
  "Same again" and "Log set" sit side by side and it is not clear how they differ. "Warm-up" is a lone chip.
- Routines mixes three different jobs on one page: your routines, the schedule rules ("Which routine is next",
  training days) and, behind a second switch, the gym inventory. The 753-exercise library is hidden in a folded
  "Add from the list" row at the bottom of the Gym page.
- In the Gym list the only visible control on each row is a trash can: the most destructive action is the most
  prominent one.
- Stats is one scroll of 13 cards with equal weight. The period chips scroll away. There is no "what matters
  this week" at the top.
- Words change from screen to screen: machine / exercise, routine / ficha, Gym, Free, session / training.
- A new user must discover the order alone: Routines, then Gym, add machines, create a routine, go back to
  Today.

## 2. Principles for the redesign

1. **One obvious action per screen.** One filled button; everything else is quieter.
2. **The gym rule stays**: logging a set is one tap, big targets, no keyboard. The redesign may not add a tap
   to logging.
3. **Hierarchy by size and colour, not by boxes.** Fewer cards and outlines, more spacing and type contrast.
4. **The app should feel like its icon**: warm, friendly, a little playful, still calm enough to read numbers.
5. **Same word for the same thing everywhere.**
6. **Show state, then detail**: a summary first, the full list one tap away.

## 3. Visual language

### 3.1 Colour: "Sunny" (chosen), taken from the icon

| Role | Colour | Used for |
|---|---|---|
| Brand | warm yellow `#FEC204` | headers of hero cards, selected chips, progress, highlights |
| Ink | charcoal `#1F1B16` | text, the main filled button (yellow text or white text on charcoal) |
| Success | green `#2E6B52` (today's accent) | done, records, positive change only |
| Accent | pink `#E6397F` | sparingly: celebration, the mascot, one badge |
| Page / surface | warm off-white `#FAF7F0` / white | background / cards |
| Muted | warm grey `#6B645A` | secondary text (darker than today's grey, readable in a gym) |

Yellow needs dark text for contrast, so buttons are either charcoal with white text (main action) or yellow
with charcoal text (selected, highlighted). Red stays for errors only. The app stays light-only, as you chose.

Alternative A: keep green as the brand and only warm up the page and greys (smallest change, least personality).
Alternative B: charcoal and pink as the brand (strongest, least calm).

### 3.2 Type

- Four sizes only: display 32 (numbers and screen titles), title 20, body 16, label 13 (never lighter than the
  muted grey above).
- Numbers that matter (weight, reps, timers, totals) are always display size and bold.
- Section headers in small caps-style labels instead of every section being its own titled card.

### 3.3 Shapes and components

- One card style: white, 20 dp corners, no outline, soft shadow or none. Lists live inside one card with
  hairline dividers, not one card per row.
- Three button levels, used consistently: filled (one per screen), tonal (yellow tint), text.
- Chips: selected = filled yellow with charcoal text and no check mark (the fill already says "selected");
  unselected = tinted surface, no outline.
- Exercise thumbnail: always a rounded tile. If there is a line drawing it sits on a tinted tile; if not, the
  tile shows the muscle-group colour and a simple group glyph. No more mixed looks.
- A small mascot for empty states, rest day, first-run and training complete: the man or the woman, **chosen by
  the sex set in Settings** (man until a sex is chosen). Both come from the icon art; a `Mascot(sex)` composable in
  `:core:ui` picks the right image, so no screen decides on its own.

## 4. Structure and wording

- **Tabs stay five**: Treino, Agenda, Evolução, Fichas, Ajustes (you asked for Settings as a tab).
- **Fichas tab** keeps two segments: **Fichas** (routines) and **Academia** (your equipment). The schedule
  rules (rotation / fixed days / training days) move to the **Agenda** tab, next to the calendar they drive.
- **Vocabulary** (one list, used everywhere, EN / PT): exercise / exercício (not "machine" in the UI, even
  though the code says machine), routine / ficha, training / treino, set / série, "No routine" / "Sem ficha"
  instead of "Free".
- **First run**: three short steps instead of an empty Today: pick what your gym has (from the library, with
  search and filters), build or accept a starter routine, choose training days. Skippable.

## 5. Screen by screen

### 5.1 Today (Treino)

- **Hero card** at the top, yellow: date small, routine name large, "2 of 4 exercises" with a progress ring,
  and ONE filled button: **"Start: Leg press"** / **"Continue: Leg extension"** / **"Finish training"** when
  all are done. Tapping it opens that exercise. This is the main action the screen lacks today.
- The routine switcher becomes a compact control in the hero ("Ficha B ▾" opening a short sheet: A, B, C,
  Sem ficha, with "next in rotation" marked) instead of four chips across the top.
- The exercise list is one card: done rows dimmed with a green check, the current row tinted (no outline),
  each row shows "2 / 3" as small dots. Long-press menu stays.
- "Same as last time" and "Same, with suggested weights" move into one quiet row "Log everything like last
  time ▾" under the list. "Add exercise" becomes a text button at the end of the list.
- **Rest day**: mascot, "Rest day", "Next training: Wednesday, Ficha B", and a text button "Train anyway".
  No machine list.
- **Empty (no routines)**: the first-run steps, not a list of machines.

### 5.2 Exercise screen (the logging screen)

Keeps the current structure (table above, fixed panel below), with these changes:

- Info becomes three compact chips under the title: "Seat 4", "Last: 3 × 12 at 120 kg", and the suggestion
  as a tappable chip **"Try 125 kg"** that sets the stepper (one tap, optional).
- Set progress as dots next to the title (● ● ○) so "which set am I on" is visible at a glance.
- **One log button**, full width, that says what it will log: **"Log 125 kg × 8"**. "Same again" becomes a
  small "= last set" text button above it (it only fills the steppers, then you tap Log), or is removed since
  the steppers already start at the last values. This removes the two-button doubt.
- Warm-up: the chip moves into the set-type row that appears only when you tap "Set type" (normal is the
  default and needs no control on screen).
- Rest: the slim bar moves into the bottom panel, above the steppers, so the countdown and the next action
  are in one place; the table area stays calm.
- Exercise complete: keeps the new panel, restyled as a small celebration (check, "3 of 3", next exercises).

### 5.3 Fichas, Agenda da ficha, Academia

- Routine cards show more than a count: muscle-group chips, number of exercises, "last trained 3 days ago",
  and a small "next" badge on the one coming up.
- Schedule rules leave this page (own segment or the Agenda tab), with a one-sentence explanation and a small
  preview: "This week: Mon A, Wed B, Fri C".
- **Academia**: rows without the trash icon (delete and archive live in the row's menu and the editor).
  A filled **"Add exercises"** button opens the library as a full screen: search on top, filters as three
  compact dropdown chips (muscle, equipment, level), results as a list with thumbnails, tap to select, a
  sticky "Add 3" button. "How to do it" stays on the info icon.

### 5.4 Agenda (Calendar)

- A compact **week strip** on top (this week, with trained / planned marks) and the month below it, tighter
  rows, so the selected day's detail is visible without scrolling.
- Clear difference between trained (filled colour badge) and planned (dashed outline badge), with a legend
  line.
- The selected day's card leads with the action: "Plan a workout" as the filled button on future days, the
  sessions list on past days.
- The weekly goal moves into the week strip ("2 of 4") instead of its own card.

### 5.5 Evolução (Progress)

- The **period control is sticky** at the top of Stats.
- Stats is split into three sub-pages (segmented control), each with 3 to 5 cards, most important first:
  - **Corpo**: body map, muscle groups, balance, body weight and strength.
  - **Força**: strength trend, strength per body weight, records, rep ranges.
  - **Hábito**: summary, weekly volume, days since trained, session length.
- A **highlights strip** at the top of Evolução: three sentences the app picks ("Legs: 12 sets a week",
  "Back is 9 days behind", "Leg press +8 % in 3 months"), each opening its card.
- Charts get the new palette (yellow / charcoal / green) and larger labels.
- The Machines page (per-exercise progress) becomes "Exercícios", list rows with sparkline and the latest
  change as a coloured number.

### 5.6 Ajustes (Settings)

- A list of rows that open sub-pages (Training, Your body, Data, App) instead of one long scroll of cards;
  each row shows its current value ("Goal: 4 days", "Rest: 90 s").

### 5.7 Feedback and motion

- Logging a set: the new row slides in and the dot fills; a record gets a short yellow burst and a "Record"
  badge that stays on the row.
- Training complete: the summary opens with the mascot and the four numbers, then the details.
- Transitions between Today and an exercise use a shared-element feel (the row grows into the screen) if it
  stays cheap; otherwise a simple slide.

## 6. Decisions (settled with the owner, 11 November 2026)

1. **Palette**: Sunny.
2. **Mascot**: yes, both characters are used inside the app (`docs/mascots/man.png`, `woman.png`; only cropped
   or recoloured, no new poses). **Which one shows follows the sex chosen in Settings, "Your body"**: Male shows
   the man, Female shows the woman. Until a sex is chosen the man is shown. The mascot appears on the rest day,
   in empty states, in the first-run steps and in the training-complete summary.
7. **App icon**: the **male** icon (the owner finds it better). The launcher icon is static, so it cannot follow
   the sex setting; the in-app mascot does. The female icon stays available in `docs/mascots/` for the Play
   Store listing or a later "alternative icon" option.
3. **Schedule rules** ("which routine is next", training days): move to the Agenda tab.
4. **"Same again" button**: removed. One log button that says what it logs.
5. **Wording**: "exercício / exercise" everywhere instead of "aparelho / machine"; "Sem ficha / No routine"
   instead of "Free".
6. **Stats** split into Corpo / Força / Hábito, with a sticky period and a highlights strip.

## 7. How I would build it

Before any code: **static mock-ups** of Today, the exercise screen and Stats in the chosen palette (rendered
as images) for you to approve or correct. That is a day's work and avoids redoing screens.

| Phase | What | Notes |
|---|---|---|
| R0 | Mock-ups + your answers to section 6 | no app code |
| R1 | Design system in `:core:ui`: colours, type scale, card, buttons, chip, thumbnail tile, `Mascot(sex)` with the two characters (the sex comes from `BodySettings.profile`, passed down by each screen's view model) | every screen picks it up; screenshots change everywhere |
| R2 | Today + exercise screen | the two screens you use most; logging path stays one tap |
| R3 | Fichas / schedule / Academia / library full screen + first-run steps | |
| R4 | Agenda | |
| R5 | Evolução: highlights, sub-pages, chart palette | |
| R6 | Ajustes as sub-pages, wording pass (EN, PT, Baianês), motion | |
| R7 | Play Store screenshots and feature graphic in the new look | |

Each phase follows the project rules: contract, separate agents, tests by a separate agent, CI green, and I
review the generated screenshots before telling you it is ready. You test on the phone between phases; a phase
is done when you say the screen feels right, not when CI is green.

## 8. Risks

- R1 changes every screenshot test at once; they only record images, so this is noise, not breakage.
- Yellow as a brand colour has low contrast with white: it must never carry white text. The plan uses
  charcoal on yellow everywhere.
- Renaming "machine" to "exercise" touches many strings in three languages; code names stay as they are.
- Splitting Stats and moving the schedule rules change where things are; a one-time "what moved" note helps.
