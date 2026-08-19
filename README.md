# Masterr — an MBA command ledger

A single-file, offline-first tracker for the whole spread of MBA life: subjects and
their projects / assignments / quizzes, case competitions with their many staged
deadlines and teammates, mentor sessions, club & committee work, admin errands, and
personal tasks like CV rounds — all in one place, every field yours to change.

No build step, no account, no server. Open `index.html` in any browser. Your data is
saved to that browser (`localStorage`) and never leaves your device. Export a JSON
backup anytime and import it on another machine.

## Views

- **Overview** — the day at a glance: overdue / due-in-7-days / active / completed
  counters, a deadline timeline grouped as Overdue → Today → Tomorrow → This week →
  Next week → Later, a progress bar per category, and a roll-up of every teammate and
  which task they're on.
- **Board** — a kanban you can drag across, grouped by **status** or by **category**.
- **Calendar** — a month grid dotted with deadlines (item due dates *and* each
  sub-task's own date); click a day for the detail.
- **List** — a dense, sortable table with inline status and one-click "done".
- **Settings** — where you make it yours.

## Highly customizable

Everything is one flexible item, and almost every part of the model is editable:

- **Categories** — rename, recolor, add, or delete your buckets (Academics, Case
  Comps, Clubs, …). Deleting one reassigns its tasks rather than losing them.
- **Statuses** — the board's columns. Add your own and flag which ones count as
  *complete*.
- **Custom fields** — add any labeled field to any task: text, number, date, url, or
  select. (Professor, weightage, portal link, prize, room, mentor — whatever you need.)
- **Sub-tasks as staged deadlines** — each with its own optional due date, so a case
  comp's internal reg / external reg / round 1 / jury call all live under one item and
  surface on the calendar and timeline.
- **People & teams** — name your teammates and their roles per task.
- Plus tags, priority, notes, a light / dark / auto theme, and six accent colors.

## Keyboard

- **N** — new task · **/** — focus search · **Esc** — close the editor

## Your data

Settings → *Your data*: **Export JSON** for a backup, **Import JSON** to restore or move
devices, **Load sample** to reset to the demo, or **Clear all** to start empty. Because
local storage is per-browser, download this page and open it from your own device to keep a
private, permanent copy.

## Cloud sync (optional — bring your own Firebase)

The app works fully offline on `localStorage`. To sync across devices in real time, connect
**your own** Firebase project — no credentials are baked into the code, and your Firebase
config never leaves your device (it's kept in `localStorage`, not in Firestore). The whole
ledger is stored as one document at `masterr/{your-uid}` and kept in sync live; last write
wins at the document level, which is the right trade-off for a single-user tracker.

**Setup (one time):**

1. In the [Firebase console](https://console.firebase.google.com) create a project and a
   **Web app**; copy its config object.
2. **Build → Firestore Database → Create** (production mode).
3. **Build → Authentication → Sign-in method** → enable **Google** and/or **Anonymous**.
4. For Google sign-in, add the domain you open the app from under **Authentication →
   Settings → Authorized domains** (e.g. your GitHub Pages domain, or `localhost`).
5. Paste these Firestore **security rules** so only you can read your data:

   ```
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       match /masterr/{uid} {
         allow read, write: if request.auth != null && request.auth.uid == uid;
       }
     }
   }
   ```

6. In the app: **Settings → Cloud sync → Firebase**, paste your config, **Connect project**,
   then **Sign in with Google**. The footer dot turns green when synced.

**Important:** Google sign-in requires an authorized domain, so it does **not** work from a
raw `file://` path — host the page (GitHub Pages or `localhost`) or use Anonymous sign-in.
The interactive preview inside Claude runs local-only because its sandbox blocks the Firebase
SDK; the copy in this repo does the real cloud sync once hosted.

### Host it free on GitHub Pages

Repo **Settings → Pages → Build and deployment → Deploy from a branch**, pick this branch and
the root folder. Your app is then at `https://<username>.github.io/<repo>/` — add that domain
to Firebase Authorized domains and Google sign-in works.

## Tech

One hand-written `index.html` — vanilla JS, no dependencies, no external requests except
Google Fonts (Instrument Serif · Hanken Grotesk · JetBrains Mono). Fully offline once the
page is loaded.
