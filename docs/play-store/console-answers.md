# Play Console answers, form by form

For the **play** build of Bora pro Treino! (`com.punyplatypus.boratreino`). Where Google's wording differs slightly, choose the
closest option; the facts below are what is true about the app.

## Facts the answers rest on
- No account, no login, no server, no analytics, no ads, no purchases, no in-app payments.
- All data (machines, routines, trainings, sets, settings) is stored only on the phone.
- The play build has **no internet permission** (CI checks it) and **no self-updater**.
- Permissions: `POST_NOTIFICATIONS` (reminders and rest timer, asked from Settings), `VIBRATE` (normal, no prompt).
  No location, contacts, camera, microphone, storage, exact alarms, foreground services or Health Connect (Health Connect is a later phase).
- Backup file: created by the user through Android's file picker, never sent anywhere by the app.
- Android's own automatic backup may copy app data to the user's Google account (OS feature, user-controlled).

## Main store listing
| Field | Value |
|---|---|
| App name | Bora pro Treino! |
| Short description | `listing/en-US/short_description.txt` (EN), `listing/pt-BR/short_description.txt` (PT) |
| Full description | `listing/*/full_description.txt` |
| App icon | `graphics/icon-512.png` |
| Feature graphic | `graphics/feature-graphic-en-US.png`, `feature-graphic-pt-BR.png` |
| Phone screenshots | `screenshots/en-US/*.png`, `screenshots/pt-BR/*.png` |
| Category | Health & Fitness (type: App) |
| Contact email | yours (public on the store page) |
| Website (optional) | https://github.com/ramonvidal/BoraTreino |

## App content
| Form | Answer |
|---|---|
| Privacy policy | https://ramonvidal.github.io/BoraTreino/privacy-policy.html (after enabling GitHub Pages, guide step 1) |
| App access | All functionality is available without special access (no login). |
| Ads | No, the app does not contain ads. |
| Content rating (IARC questionnaire) | Email: yours. Category: **Utility, Productivity, Communication, or Other**. Answer **No** to everything: violence, blood, sexuality/nudity, profanity, controlled substances, gambling, user-generated content, user-to-user communication, location sharing, digital purchases. Expected result: Everyone / PEGI 3 / Livre. |
| Target audience and content | Age group: **18 and over** only (the app is not designed for children). No "appeals to children" content. |
| Data safety: collect or share data? | **No** - "My app doesn't collect or share any of the required user data types". (Nothing leaves the phone and there is no internet permission.) Then confirm the declaration and add the privacy policy link. |
| Data safety: security practices | Not asked when no data is collected. |
| Health apps declaration | "Yes, my app has health features" > **Activity and fitness** (workout and exercise logging). Not a medical device, no medical or diagnostic claims, no health research, no clinical features. No Health Connect. |
| Advertising ID | No, the app does not use the advertising ID. |
| Financial features | None. |
| Government app | No. |
| News app | No. |
| Photo/video permissions, exact alarms, foreground services, SMS/call log, VPN, accessibility | Not used. |
| Permissions rationale (if asked) | Notifications: to show training reminders chosen by the user and the rest-timer countdown. Vibration: to buzz when rest ends. |

## Pricing and distribution
| Field | Value |
|---|---|
| Price | Free |
| Countries | Brazil plus any others you like (all countries is fine) |
| Contains ads | No |
| Device categories | Phone and tablet (default); not Wear OS, TV, Auto or XR |
| Export compliance / US export laws | Accept (standard encryption only; the app has no network code) |

## App signing and release
| Field | Value |
|---|---|
| Play App Signing | Use the Google-generated key (default). |
| Upload key SHA-256 (for reference) | `C2:08:F7:D5:DD:39:22:1E:D2:29:19:39:96:68:47:61:89:C3:DE:C7:BF:D0:E7:86:27:E3:21:D6:B3:33:8B:B0` |
| Bundle | `BoraTreino.aab` from the newest GitHub release |
| Release notes | `listing/en-US/release_notes.txt`, `listing/pt-BR/release_notes.txt` |

## Later: when Health Connect arrives (Phase 8)
Adding Health Connect means a new permission declaration in Play Console (App content > Health Connect: which data types and why), an
update of the data safety form (health and fitness data, shared with Health Connect only when the user turns it on) and of the privacy policy.
