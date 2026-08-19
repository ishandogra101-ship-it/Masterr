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
storage is per-browser, download this page and open it from your own device to keep a
private, permanent copy.

## Tech

One hand-written `index.html` — vanilla JS, no dependencies, no external requests except
Google Fonts (Instrument Serif · Hanken Grotesk · JetBrains Mono). Fully offline once the
page is loaded.
