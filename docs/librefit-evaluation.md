# LibreFit: evaluation and plan

Repository: https://github.com/LibreFitOrg/LibreFit (checked at commit 98b4dd4, 4 Oct 2026, v0.5.0 plus unreleased). One main developer, Kotlin + Compose + Room, about 180 Kotlin files, 20+ languages
through Weblate, F-Droid release. Offline, no account, same privacy idea as Bora pro Treino!.

## 1. What it is, side by side

| | LibreFit | Bora pro Treino! |
|---|---|---|
| Idea | Library of 900+ exercises, build workouts, follow a guided workout screen | Your gym's machines first, one-tap logging "same as last time" |
| Exercise data | 912 exercises with muscles, equipment, level, force, mechanic, step-by-step instructions, 2 images each | About 100 starter machines (EN/PT), about 54 with a picture |
| Pictures | 1,823 AI-generated photo-style WebP (800 px, about 17 KB each, 38 MB in total) | about 106 Everkinetic line drawings (CC BY-SA), 1.2 MB |
| Logging speed | Fill in sets on a workout screen, scroll-wheel number pickers | One tap repeats the last set (the product rule) |
| Stats | Charts, body measurements screen, calendar | 1RM, weekly volume, muscle balance, records, plateaus, rep ranges, energy estimate |
| Planning | Workout plans, "schedule sessions" | Rotation + training days + fixed weekdays, plan on the Calendar |
| Extras | Material You and custom exercises for supporters (paid/donor build) | Health Connect, backup, rest timer with alarm, warm-up calculator, update from GitHub |

## 2. The exercise pictures: should they replace ours?

What I saw (Leg Press, their frames next to ours):
- Their pictures are realistic photos of a person on the machine, **AI generated**. In the frame pair I compared, the two frames show different people, the machine hardware differs between frames and
  there are stray "ghost" shapes in the corner. The README itself warns: "may contain inaccuracies and/or artifacts, provided as is". They are "continuously reviewed and regenerated".
- Ours are consistent line drawings (same artist), which show the movement clearly and look the same across the whole app, and are tiny (1.2 MB).
- Size: all of theirs would add about 38 MB to the app (and each frame is 800 px: we would resize to 360 px, which brings it to roughly 10 to 12 MB). Possible, not free.
- Licence: they state the images are AI generated, "not subject to copyright" and "provided without restriction". That is the project's own claim (the legal status of AI images differs by country); it is
  more permissive than our current CC BY-SA obligation (no attribution required), but I would still credit LibreFit. The **code and JSON are GPL v3 or later with extra terms** (keep author credits,
  no use of the LibreFit name or logo). Copying code or the dataset text would put our app under GPL v3: we should not copy code. This is a decision for you if you want to share the source
  (the repo has no licence file today).
- Coverage: 27 of our 103 starter names match a LibreFit name exactly, a similar number match loosely; machines like Pec deck, Lat pulldown, Seated row, Hip thrust, Dips would need hand mapping (as we did for Everkinetic).

My recommendation: **do not replace ours wholesale.** Offer the LibreFit pictures where we have none (we have a picture for 54 of 103 starters), keep the line drawings where they exist, and review each one by eye because of the AI artefacts.
A small, careful import (about 40 to 50 exercises, resized) is realistic.

## 3. What else is worth taking (ideas only, we write our own code)

Ranked by value for you:

1. **Exercise library with instructions** (large value): browse by muscle group / equipment / difficulty, see "how to do it" steps and the two pictures. Our starter list could grow from about 100 to several hundred with
   proper names in Portuguese. The dataset text is GPL-covered and the original data looks like the public free-exercise-db format; I would write our own short instructions or take only the pictures and names. (Needs your OK on licence.)
2. **Body measurements** (medium): weight is already there in Settings (entries); LibreFit has a full measurement screen with chart (waist, arms, etc.). We could add a small "Measurements" card on Progress with a line chart, no new tab.
3. **Filters in the starter list**: filter by equipment (barbell, dumbbell, cable, machine, bodyweight) and level. Small and quick.
4. **Update the routine from what you actually lifted** (their newest feature, issue #44): at the end of a session offer "update the routine's target weights/sets with today's". Fits our "quick" rule if it is one tap on the summary.
5. **Translations**: they use Weblate (Brazilian Portuguese among 20+). Their strings are not usable for us directly (GPL), but Weblate-style community translation could replace our CSV later. Low priority.
6. **Tutorial / welcome screens**: they have a short onboarding. We have none. Small, optional.

Not worth taking: supporter paywall (custom exercises behind a donation, we already have custom machines for free), the guided workout screen (it adds taps, against our rule 0), Material You (nice but cosmetic).

## 4. Options for you to pick

- **A. Pictures only, careful**: map our starters without a picture (about 49) to LibreFit images, resize, review each, credit LibreFit in Settings and README, keep Everkinetic drawings where they exist. About 6 MB.
- **B. A + exercise library**: add a searchable library with filters (equipment, muscle group, level) and short instructions in EN/PT; adding an exercise from it creates a machine with its picture. Bigger (new screen, data file, about 1 to 2 phases).
- **C. Measurements card** on Progress (chart + entry), independent of the above.
- **D. Update routine after workout** (one-tap suggestion in the session summary).
- **E. Replace all our pictures with LibreFit's**: I advise against it (AI artefacts, 3x the size, loses visual consistency).
- **F. Nothing from LibreFit**: keep the current app and continue the open items.

I can combine them; my suggested order if you say "go": A, then D, then B, then C.
