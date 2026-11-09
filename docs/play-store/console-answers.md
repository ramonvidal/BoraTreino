# Play Console answers, form by form

For the **play** build of Bora pro Treino! (`com.punyplatypus.boratreino`). Where Google's wording differs slightly, choose the
closest option; the facts below are what is true about the app.

## Facts the answers rest on
- No account, no login, no server, no analytics, no ads, no purchases, no in-app payments.
- All data (machines, routines, trainings, sets, settings) is stored only on the phone.
- The play build has **no internet permission** (CI checks it) and **no self-updater**.
- Permissions: `POST_NOTIFICATIONS` (reminders and rest timer, asked from Settings), `VIBRATE` (normal, no prompt).
  Health Connect write permissions `WRITE_EXERCISE`, `WRITE_ACTIVE_CALORIES_BURNED`, `WRITE_DISTANCE` (asked from Settings, optional, off by default).
  No location, contacts, camera, microphone, storage, exact alarms or foreground services.
- Backup file: created by the user through Android's file picker, never sent anywhere by the app.
- Training summary picture: made on the phone only when the user taps Share, and sent only to the app the user picks.
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
| Data safety: collect or share data? | **No** - "My app doesn't collect or share any of the required user data types". Nothing is sent to us or to any third party and there is no internet permission. A training summary picture is made on the phone only when the user taps Share and goes only to the app the user picks; it is not collected or shared. Health and fitness data (exercise sessions, active calories, distance) is only written to the user's own Health Connect, at the user's request, which is on-device and not "collection" or "sharing"; if the form asks, it is not collected, not shared, and the user can turn it off and delete it in Health Connect. Body details typed in Settings (sex, birth year, height, weight, weight entries) are used only on the device to estimate daily energy needs: stored on device only, not part of backups, not collected, not shared. Then confirm the declaration and add the privacy policy link. |
| Data safety: security practices | Not asked when no data is collected. |
| Health apps declaration | "Yes, my app has health features" > **Activity and fitness** (workout and exercise logging). Not a medical device, no medical or diagnostic claims, no health research, no clinical features. Health Connect: yes, see the declaration below. |
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

## Health Connect declaration
Play Console > App content > Health apps (Health Connect permissions). Must be filled in and approved before any rollout.
| Field | Answer |
|---|---|
| Data types | **Exercise** (write), **Active calories burned** (write), **Distance** (write). No read permission is requested. |
| Why the app needs them | Optional feature, off by default, switched on in Settings: each finished training is written to the user's Health Connect as an exercise session (start, end, type), and cardio trainings also write the active calories burned and the distance, so the user's workouts appear in their other health apps. |
| Read access | None. The app never reads Health Connect data. |
| Where it is shown | Settings > Health Connect card, with Android's own permission screen. A permissions rationale screen with a link to the privacy policy is included. |
| Data use | Only to write what the user logged in the app. Not sold, not used for ads, not shared with anyone; nothing is sent over the network by the app. |
| Privacy policy | https://ramonvidal.github.io/BoraTreino/privacy-policy.html (has a Health Connect section) |
| Removing the data | The user turns the Settings switch off (stops sending) and can delete or revoke the records in Health Connect. |

## Data safety form, Health Connect part
Health and fitness data (Fitness info: exercise, calories, distance) is written to Health Connect on the device at the user's request.
It is not collected by the developer, not transmitted off the device by the app and not shared with third parties.
