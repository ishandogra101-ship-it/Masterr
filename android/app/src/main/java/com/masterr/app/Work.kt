package com.masterr.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.util.concurrent.TimeUnit

private const val DAY = 86_400_000L
private const val HR = 3_600_000L
private const val MONTH = 30L * DAY

object Scheduler {
    private const val UNIQUE = "masterr-loop"

    fun schedule(ctx: Context) {
        // WorkManager periodic run is a *backstop*. It is deferrable, so on its own it
        // won't fire reliably in Doze / under OEM battery killers — the AlarmManager
        // heartbeat below is what actually keeps reminders arriving while the app is closed.
        val periodic = PeriodicWorkRequestBuilder<MasterrWorker>(30, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(UNIQUE, ExistingPeriodicWorkPolicy.UPDATE, periodic)
        Alarms.schedule(ctx)
        runNow(ctx)
    }

    fun runNow(ctx: Context) {
        val once = OneTimeWorkRequestBuilder<MasterrWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueue(once)
    }
}

/**
 * AlarmManager heartbeat. Unlike a periodic WorkManager job, an alarm scheduled with
 * setAndAllowWhileIdle() still fires while the device is in Doze, so deadline reminders
 * and check-ins land on time instead of piling up until the app is next opened.
 * It needs no special permission and re-arms itself after every fire.
 */
object Alarms {
    private const val REQ = 7001
    const val ACTION = "com.masterr.app.HEARTBEAT"
    private const val INTERVAL = 15 * 60_000L   // 15 minutes

    private fun pi(ctx: Context): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java).setAction(ACTION)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(ctx, REQ, i, flags)
    }

    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val at = System.currentTimeMillis() + INTERVAL
        try {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(ctx))
        } catch (e: Exception) {
            try { am.set(AlarmManager.RTC_WAKEUP, at, pi(ctx)) } catch (_: Exception) {}
        }
    }

    fun cancel(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try { am.cancel(pi(ctx)) } catch (_: Exception) {}
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pending = goAsync()
        val appCtx = ctx.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try { Engine.run(appCtx) } catch (e: Exception) { }
            finally {
                Alarms.schedule(appCtx)   // chain the next heartbeat
                pending.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        // Exact/allow-while-idle alarms are cleared on reboot; re-arm everything.
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) Scheduler.schedule(ctx)
    }
}

class MasterrWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result =
        if (Engine.run(applicationContext)) Result.success() else Result.retry()
}

/**
 * The single evaluation pass shared by the WorkManager backstop and the AlarmManager
 * heartbeat: reads the current tasks and posts any reminders / check-ins that are due.
 * Returns false only when the data fetch failed and the caller should retry.
 */
object Engine {
    suspend fun run(ctx: Context): Boolean {
        val cfg = Prefs.readConfig(ctx)
        if (!cfg.firebaseReady || !Repo.signedIn(ctx, cfg)) return true
        val s = Prefs.readSettings(ctx)

        val tasks = try { Repo.tasks(ctx, cfg) } catch (e: Exception) { return false }
        val now = System.currentTimeMillis()

        // Track the latest data change we've observed (for catch-up).
        val latest = tasks.maxOfOrNull { it.updatedAt } ?: 0L
        if (latest > Prefs.lastSeenDataAt(ctx)) Prefs.setLastSeenDataAt(ctx, latest)

        val quiet = isQuiet(s, now)
        val open = tasks.filter { !it.done }

        // 1) Deadline reminders (task-specific — allowed even close to quiet, but we respect quiet)
        if (s.remindersOn && !quiet) {
            for (t in open) {
                val due = t.dueMillis ?: continue
                if (due <= now) continue
                for (off in s.reminderOffsets) {
                    val thr = due - off * 60_000L
                    val key = "rem:${t.id}:$off"
                    if (now >= thr && now < thr + 100 * 60_000L && now < due && !Prefs.wasSent(ctx, key, MONTH)) {
                        Notifier.notifyReminder(ctx, t, overdue = false)
                        Prefs.markSent(ctx, key)
                        break
                    }
                }
            }
        }

        // 2) Overdue reminders
        if (s.overdueOn && !quiet) {
            for (t in open) {
                val due = t.dueMillis ?: continue
                if (due >= now) continue
                val bucket = ((now - due) / (s.overdueEveryHours.coerceAtLeast(1) * HR)).toInt()
                val key = "ovd:${t.id}:$bucket"
                if (!Prefs.wasSent(ctx, key, MONTH)) {
                    Notifier.notifyReminder(ctx, t, overdue = true)
                    Prefs.markSent(ctx, key)
                }
            }
        }

        // Generic proactive nudges are suppressed shortly after a "nothing new".
        val quietedByNothingNew = now - Prefs.lastNothingNew(ctx) < 6 * HR

        // 3) Scheduled check-ins
        if (s.assistantOn && !quiet && !quietedByNothingNew) {
            val today = todayIso()
            maybeCheckin(ctx, s.morningOn, s.morningTime, "morning", 1001, today,
                "☀️ Masterr check-in", morningText(open.size))
            maybeCheckin(ctx, s.middayOn, s.middayTime, "midday", 1002, today,
                "🔔 Quick check", middayText())
            maybeCheckin(ctx, s.eveningOn, s.eveningTime, "evening", 1003, today,
                "🌙 End-of-day check", eveningText())
        }

        // 4) Bug Me — persistent nudge when you've gone silent
        if (s.assistantOn && s.bugMe && !quiet && !quietedByNothingNew && open.isNotEmpty()) {
            val gap = s.bugEveryHours.coerceAtLeast(1) * HR
            if (now - Prefs.lastInteract(ctx) > gap) {
                val bucket = (now / gap).toInt()
                val key = "bug:$bucket"
                if (!Prefs.wasSent(ctx, key, MONTH)) {
                    val overdue = open.count { val d = it.dueMillis; d != null && d < now }
                    val txt = if (overdue > 0)
                        "You have $overdue overdue and ${open.size} open task(s). Anything new to add, or want a plan?"
                    else "You have ${open.size} upcoming task(s). Any new college work to capture?"
                    Notifier.notifyCheckin(ctx, 1004, "📚 Masterr", txt)
                    Prefs.markSent(ctx, key)
                }
            }
        }

        // 5) Inactivity catch-up — only if something actually needs attention
        if (s.inactivityOn && now - Prefs.lastInteract(ctx) >= s.inactivityDays.coerceAtLeast(1) * DAY) {
            val overdue = open.count { val d = it.dueMillis; d != null && d < now }
            val soon = open.count { val d = it.dueMillis; d != null && d in now..(now + DAY) }
            if (overdue > 0 || soon > 0) {
                val key = "catchup:${todayIso()}"
                if (!Prefs.wasSent(ctx, key, MONTH)) {
                    val parts = ArrayList<String>()
                    if (overdue > 0) parts.add("🔴 $overdue overdue")
                    if (soon > 0) parts.add("🟠 $soon due within a day")
                    Notifier.notifyCatchup(ctx, "⚠️ Masterr catch-up",
                        "You haven't checked in for a few days. " + parts.joinToString(" · ") + ". Tap to review.")
                    Prefs.markSent(ctx, key)
                }
            }
        }

        Widget.refresh(ctx)
        return true
    }

    private fun maybeCheckin(ctx: Context, on: Boolean, time: String, tag: String, id: Int, today: String, title: String, text: String) {
        if (!on) return
        if (!inWindow(time)) return
        val key = "checkin:$tag:$today"
        if (Prefs.wasSent(ctx, key, MONTH)) return
        Notifier.notifyCheckin(ctx, id, title, text)
        Prefs.markSent(ctx, key)
        Prefs.markCheckin(ctx)
    }

    private fun morningText(open: Int) = listOf(
        "Any assignments, quizzes, presentations or deadlines coming up that I haven't got yet?",
        "Morning! Anything new on your plate today I should track?",
        "New week/day — did any deadline land that isn't in Masterr yet?"
    ).random() + if (open > 0) " (You have $open open.)" else ""

    private fun middayText() = listOf(
        "Did any professor announce new work today?",
        "Midday check — anything new to capture before it slips?",
        "Any tasks pop up since this morning?"
    ).random()

    private fun eveningText() = listOf(
        "Before you finish up — did you get any college work today that isn't in Masterr?",
        "End of day: anything from classes to add before tomorrow?",
        "Quick recap — new assignments/quizzes to log?"
    ).random()

    private fun isQuiet(s: Settings, now: Long): Boolean {
        if (!s.quietOn) return false
        return try {
            val t = LocalTime.now()
            val start = LocalTime.parse(s.quietStart)
            val end = LocalTime.parse(s.quietEnd)
            if (start <= end) !t.isBefore(start) && t.isBefore(end)
            else !t.isBefore(start) || t.isBefore(end)
        } catch (e: Exception) { false }
    }

    private fun inWindow(scheduled: String): Boolean {
        return try {
            val sched = LocalTime.parse(scheduled)
            val nowT = LocalTime.now()
            val nowM = nowT.hour * 60 + nowT.minute
            val sM = sched.hour * 60 + sched.minute
            nowM >= sM && nowM < sM + 90
        } catch (e: Exception) { false }
    }
}
