# Publishing Bora pro Treino! on Google Play: step by step

Package name in Play Console: **`com.punyplatypus.boratreino`** (the `play` build of this project).
Everything below was prepared in the repository; the parts only you can do (they need your Play Console and GitHub
logins) are marked **YOU**. Play Console menus get renamed now and then: if a name differs slightly, look for the closest one.

## What is already prepared

| Thing | Where |
|---|---|
| The app bundle (`.aab`) signed with your upload key | built by CI on every push, attached to the newest GitHub release as `BoraTreino.aab` (after step 2) |
| Store texts, English and Portuguese: name, short description, full description, release notes | `docs/play-store/listing/en-US/` and `.../pt-BR/` |
| App icon 512x512 and feature graphics 1024x500 (EN and PT) | `docs/play-store/graphics/` |
| Phone screenshots 1080x2160, English and Portuguese (7 each) | `docs/play-store/screenshots/en-US/` and `.../pt-BR/` (added once CI has drawn them) |
| Privacy policy page (English and Portuguese on one page) | `docs/privacy-policy.html` |
| Exact answers for every Play Console form (content rating, data safety, health, ads...) | `docs/play-store/console-answers.md` |
| Upload key (and its passwords), outside git | the `signing/play/` folder that was sent to you in the chat |

How the two builds differ: the **play** build (`com.punyplatypus.boratreino`) has no internet permission and no self-updater
(Google Play does not allow apps to update themselves; Play updates it). The **sideload** build (`com.boratreino`, the
GitHub APK you already use) keeps the in-app updater. They are two different apps on the phone and can coexist.

## 0. Check your Play developer account (YOU, 5 minutes)

1. Open <https://play.google.com/console> with the developer account that owns the app.
2. In **Settings > Developer account** make sure identity verification and the contact details are complete and the
   account is not "pending". A **payments profile** is only needed for paid apps, not for a free one.
3. Note whether it is a **personal** or an **organization** account: personal accounts created after 13 Nov 2023 must pass a
   closed test before production (step 8). Organization accounts do not.

## 1. Publish the privacy policy page (YOU, 3 minutes)

Google requires a public privacy policy URL. The page is `docs/privacy-policy.html`; GitHub can serve it for free:

1. GitHub > your repository **BoraTreino** > **Settings** > **Pages**.
2. Source: **Deploy from a branch**. Branch: **`claude/gym-tracking-app-pyg6li`** (it is the repository's default branch) and folder **`/docs`**. Save.
3. After a minute the page is at **<https://ramonvidal.github.io/BoraTreino/privacy-policy.html>**. Open it and check both languages show.
   (If you later merge into a branch named `main` and make that the default, point Pages at it.)

## 2. Give GitHub the upload key (YOU, 5 minutes)

CI signs the bundle with a **separate upload key** (not the key of your GitHub APK). In GitHub > **Settings > Secrets and variables > Actions
> New repository secret** create two secrets, copying each value from the files in the `signing/play/` folder you received:

| Secret name | Value (file) |
|---|---|
| `SIGNING_UPLOAD_KEYSTORE_BASE64` | the whole text of `UPLOAD_KEYSTORE_BASE64.txt` |
| `UPLOAD_STORE_PASSWORD` | `UPLOAD_STORE_PASSWORD.txt` |

(`UPLOAD_KEY_PASSWORD.txt` is not needed: this kind of keystore protects the key with the store password. If you already created a secret
with that name, it is simply ignored. Make sure `UPLOAD_STORE_PASSWORD` holds `UPLOAD_STORE_PASSWORD.txt`, not the other file.)

**Keep a safe copy of `boratreino-upload.keystore` and its password** (password manager, private drive). If you ever lose
the upload key you can ask Google to reset it (Play Console > App integrity), but it is a slow process. Never commit these files.

Then trigger a build **with a push** (a push to the branch is what attaches the bundle to a release; "Run workflow" only builds
it as an Actions artifact). Releases made *before* the secrets existed never contain the `.aab`: wait for the first build that ran after you
added them. When it is green, open the repository's **Releases** page: the newest release has **`BoraTreino.aab`** next to
`BoraTreino.apk`. Download the `.aab`.

## 3. Create the app entry (YOU, 5 minutes; skip what you already did)

In Play Console > **Create app** (or open the existing entry):
- App name: **Bora pro Treino!**; Default language: **English (United States)**; **App** (not game); **Free**.
- Accept the declarations (developer program policies, US export laws).
- The package name is fixed by the first bundle you upload: it will read `com.punyplatypus.boratreino`.

## 4. App content declarations (YOU, 15 minutes)

Dashboard > **Set up your app** > **App content**. Every answer is spelled out in `docs/play-store/console-answers.md`
(privacy policy URL, app access, ads, content rating, target audience, data safety, health apps, advertising ID...).
The short version: no ads, no account, no data collected or shared, rating "Everyone", target audience 18+.

## 4b. Declare Health Connect permissions (YOU, 10 minutes, BEFORE rolling out)

The app can write finished trainings to Health Connect (optional, off by default), so its manifest asks for three Health Connect
permissions: exercise, active calories burned and distance (write only). In Play Console go to **Policy and programs > App content >
Health apps** (Health Connect) and fill in the declaration exactly as in the "Health Connect declaration" section of
`docs/play-store/console-answers.md`, and update the data safety form as described there. **Google blocks the release until this
declaration is filled in and approved**, so do it before you roll out (internal testing can start earlier, but production cannot).

## 5. Store listing (YOU, 20 minutes)

**Grow > Store presence > Main store listing** (English):
1. App name, short description, full description: copy from `docs/play-store/listing/en-US/`.
2. App icon: `docs/play-store/graphics/icon-512.png`. Feature graphic: `feature-graphic-en-US.png`.
3. Phone screenshots: upload the seven files of `docs/play-store/screenshots/en-US/` (in numeric order).
4. Category: **Health & Fitness**. Tags: pick Fitness / Workout / Exercise if offered.
5. Contact details: an email address (shown publicly on the store page), optionally your website
   (you can use `https://github.com/ramonvidal/BoraTreino`).

Then **Manage translations > Add translation > Portuguese (Brazil)** and repeat with `pt-BR/` texts, `feature-graphic-pt-BR.png`
and `screenshots/pt-BR/`. The icon is the same.

## 6. Try it quickly: internal testing (YOU, 10 minutes, optional but recommended)

**Test and release > Testing > Internal testing > Create new release**:
1. First time only, Play asks about **Play App Signing**: keep the default **"Use Google-generated app signing key"**. Your upload key
   (the one in `signing/play/`) is then only used to prove uploads come from you.
2. Upload `BoraTreino.aab`. Release name: leave the default. Release notes: paste `listing/en-US/release_notes.txt`.
3. **Testers** tab: create a list with your own Gmail address. Save, then **Save and publish** (internal tests need no review).
4. Open the opt-in link on your phone, accept, install from Play. This is the real Play build: check it opens, logging works,
   Baianês switching works (Settings > Language) and that Settings has **no** "App updates" card.

## 7. Move your own data from the GitHub version (YOU, 3 minutes)

The Play app is a different app (different package name), so it starts empty. In the **GitHub version**: Settings > Backup >
**Export backup**; then in the **Play version**: Settings > Backup > **Import backup**. After that you can uninstall the GitHub one.

## 8. Closed test, then production

**Personal accounts created after 13 Nov 2023** must run a **closed test with at least 12 testers opted in for 14 days in a row**
before Google lets them apply for production (organization accounts are exempt). Check what your Dashboard says.
1. **Test and release > Testing > Closed testing > Create track** (name it e.g. "Friends"), create a release with the same `.aab`.
2. **Testers**: add 12 or more Gmail addresses (friends, family, gym mates) as an email list, then share the **opt-in link** with them.
   Each tester must open the link, accept, and **stay opted in for the whole 14 days** (installing the app is a good idea too).
3. After 14 days, the Dashboard shows **Apply for production**. Answer the short questionnaire (how you tested, what feedback you got).
4. Google reviews the request (it can take a few days up to about a week), then you can **Create production release** with the same
   bundle, choose countries (**Brazil** and anywhere else you like) and roll out. First production reviews of new apps can take several days.

## 9. Every later update

Each push to the repository builds a new `BoraTreino.aab` with a higher version code. To ship it: Play Console > the track
(internal, closed or production) > **Create new release** > upload the new `.aab` > release notes > **Save > Review > Roll out**.
Play updates the phones by itself. (The GitHub APK keeps updating itself through its own banner.)

## Things Google checks, and how this app answers

- **Target API level**: new apps must target Android 16 (API 36) since 31 Aug 2026. The project targets 36.
- **No self-updating outside Play**: the Play build contains no updater and no install-packages permission; CI fails if its manifest
  ever contains `INTERNET` or `REQUEST_INSTALL_PACKAGES`.
- **Data safety and privacy policy**: nothing is collected, nothing is shared, no internet permission; the policy page says so (including its Health Connect section).
- **Health Connect**: write-only permissions for exercise, active calories and distance, declared in step 4b; the release is blocked until that is done.
- **Permissions**: notifications (training reminders and the rest timer), vibration and the optional Health Connect write permissions. The notification permission is asked in
  Settings, not at first launch.
- **Languages**: the in-app English/Portuguese choice works because the bundle is built without language splits.

## If something goes wrong

| Message in Play Console | What to do |
|---|---|
| "You need to use a different package name" | The package is `com.punyplatypus.boratreino`; make sure you uploaded the `.aab` (not the GitHub `.apk`). |
| "Your Android App Bundle is signed with the wrong key" | After the first upload, always upload bundles signed with the same upload key (the CI secrets above). |
| "Version code N has already been used" | Push any commit: CI increases the version code on every run. |
| "Target API level too low" | Does not apply (target 36). If Google raises it next August, bump `COMPILE_SDK`/`TARGET_SDK` in `build-logic/.../ProjectExtensions.kt`. |
| Production access button missing | The closed test has not run 14 days with 12 opted-in testers yet. |

Sources for the account and API-level rules:
[Google: testing requirements for new personal accounts](https://support.google.com/googleplay/android-developer/answer/14151465),
[Google: target API level requirements](https://developer.android.com/google/play/requirements/target-sdk),
[Google: store listing assets](https://support.google.com/googleplay/android-developer/answer/9866151).
