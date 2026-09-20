# Masterr for Android — the proactive assistant

This is the native Android app that turns Masterr from "a tracker you have to remember to
open" into an assistant that **captures tasks by chat, reminds you, and checks in on its own —
even when the app is closed.** It reads and writes the **same Firestore data** as your web
dashboard (no second database), so anything you do on your phone shows up on the web and vice
versa.

- **Conversational capture** — type "OB quiz Thursday 10am, ~45 min prep" and it files the task,
  asking only for what's genuinely missing (powered by Gemini).
- **Proactive check-ins** — morning / midday / evening nudges that *discover* work you forgot to
  enter, plus inactivity catch-ups and an optional "Bug Me" mode.
- **Deadline reminders** with **Done / Start / Snooze** buttons right on the notification.
- **"What should I work on?"** and **"Plan my day"** — grounded in your real tasks.
- **Home-screen widget** + a **Masterr Task** quick-capture icon.
- Fully customizable schedule, quiet hours, and reminder intervals in Settings.
- **Free-first**: no paid messaging service. Scheduling is on-device (WorkManager). AI uses your
  own free Gemini key and is isolated — reminders keep working even if AI is unavailable.

> Your Firebase config and keys are entered **in the app** and stored only on your device. The
> APK contains **no secrets**. Data is protected by your existing Firestore rules.

---

## Part A — Get the app (easiest: cloud build, no Android Studio)

Every push builds the APK for you in GitHub Actions.

1. On GitHub, open the repo → **Actions** tab. (If Actions is off, click **Enable**.)
2. Open the latest **"Build Android APK"** run → wait for the green check → scroll to
   **Artifacts** → download **`masterr-apk`**.
3. Unzip it — inside is **`app-debug.apk`**. Send it to your phone (email/Drive/USB) or download
   it directly on the phone.

*(Prefer Android Studio? See Part E.)*

---

## Part B — One-time Firebase setup for Android (~4 min)

Your web app already has the Firebase project, Firestore and Google sign-in. You only need to
tell Firebase about the Android app.

1. [Firebase console](https://console.firebase.google.com) → your project → **⚙ Project settings**.
2. Scroll to **Your apps** → **Add app** → **Android**.
3. **Android package name:** `com.masterr.app`  (must be exactly this).
   **App nickname:** Masterr.
4. **Debug signing certificate SHA-1** — paste this (it's the cloud build's signing fingerprint):
   ```
   18:FA:BD:FB:58:95:D5:EB:4A:42:30:33:E1:52:96:66:8F:20:F5:7C
   ```
5. Click **Register app**. On the next screen you can **skip downloading `google-services.json`**
   — Masterr configures Firebase at runtime, so you don't need that file. Click through /
   **Continue to console**.
6. **Authentication → Sign-in method → Google** — make sure it's **Enabled** (it already is from
   the web). Expand it → **Web SDK configuration** → copy the **Web client ID**
   (looks like `1234567890-abc123.apps.googleusercontent.com`). Keep it handy.

That's it for Firebase. (Firestore and the security rules from the web setup already cover the app.)

---

## Part C — Get a free Gemini key (~1 min)

1. Go to **https://aistudio.google.com/app/apikey** (sign in with Google).
2. **Create API key** → copy it. (Free tier is plenty for task capture.)

---

## Part D — Install & first run

1. On your phone, tap **`app-debug.apk`**. If prompted, allow your browser/Files app to
   "install unknown apps", then **Install**.
2. Open **Masterr**. On the **Connect** screen:
   - Paste your web **firebaseConfig `{ … }`** into the top box and tap **Fill from pasted config**
     (this fills API key / App ID / Project ID / Sender ID). Or type them by hand.
   - Paste the **Web client ID** from Part B step 6.
   - Paste your **Gemini API key** from Part C.
   - Tap **Save & continue**.
3. Tap **Sign in with Google** and choose the **same Google account** you use on the web.
4. Allow **notifications** when asked.
5. **Important for reliable reminders:** Android Settings → **Apps → Masterr → Battery →
   Unrestricted** (and on Xiaomi / Oppo / Realme / Vivo / Samsung, also enable **Autostart**).
   This stops the system from killing background check-ins.
6. Long-press your home screen → **Widgets** → add **Masterr**. You'll also see a **Masterr Task**
   icon in your app drawer for instant capture.

Done — try typing *"Finance assignment due Monday 11:59pm, about 2 hours"*.

---

## Part E — Android Studio (optional, for editing/rebuilding yourself)

1. Install **Android Studio** (Giraffe or newer) from developer.android.com/studio.
2. **File → Open** → select the **`android`** folder in this repo (not the repo root). Let Gradle
   sync (first sync downloads dependencies; give it a few minutes).
3. Plug in your phone with **USB debugging on** (or create an emulator), press **Run ▶**.
4. The debug build uses the committed keystore, so its SHA-1 matches the one you registered.

---

## How the assistant behaves (and how to tune it)

Open **Settings** (gear icon) to control everything:

- **Proactive assistant** — turn the whole thing on/off; toggle morning/midday/evening check-ins
  and set their exact times.
- **Bug Me** — persistent nudges every *N* hours when you've gone quiet; **Inactivity catch-up**
  after *N* days (only fires if something actually needs attention).
- **Deadline reminders** — set how many minutes before a deadline to remind (comma-separated,
  e.g. `4320,1440,180,60` = 3 days, 24 h, 3 h, 1 h). **Overdue reminders** repeat every *N* hours.
- **Quiet hours** — no non-urgent notifications in that window.

Smart suppression is built in: it won't double-notify, won't remind about completed tasks, pauses
generic check-ins for a while after you tap **Nothing new**, and won't spam you when there's
nothing to say.

**Notification buttons:** Reminders have **Done / Start / Snooze**; check-ins have **Add task /
Nothing new**. These update the same Firestore data your web dashboard reads.

---

## Where each config value comes from

| App field | Where to find it |
|---|---|
| API key, App ID, Project ID, Sender ID | Firebase → Project settings → *Your apps* → SDK config (or paste the whole `firebaseConfig`) |
| Web client ID | Firebase → Authentication → Sign-in method → Google → Web SDK configuration |
| Gemini API key | https://aistudio.google.com/app/apikey |
| Web dashboard URL | Pre-filled to your GitHub Pages site; editable in Settings |

## Notes

- **One source of truth:** the app edits the same `masterr/{your-uid}` document as the web app,
  changing only the fields it touches — nothing you have on the web is lost.
- **No secrets in the APK:** all keys live in the app's private storage on your device only.
- **Package name** `com.masterr.app` and the **SHA-1** above must match what you register in
  Firebase, or Google sign-in will fail with "no token".
