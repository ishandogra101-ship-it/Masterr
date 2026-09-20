package com.masterr.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object Notifier {
    const val CH_REMINDERS = "reminders"
    const val CH_CHECKINS = "checkins"
    const val CH_ASSISTANT = "assistant"

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CH_REMINDERS, "Deadline reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Reminders about your tasks and deadlines"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_CHECKINS, "Check-ins", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Proactive nudges to capture new work"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ASSISTANT, "Assistant", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Catch-ups and status"
            }
        )
    }

    private fun pi(ctx: Context, req: Int, intent: Intent, activity: Boolean): PendingIntent {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        return if (activity) PendingIntent.getActivity(ctx, req, intent, flags)
        else PendingIntent.getBroadcast(ctx, req, intent, flags)
    }

    private fun openAppPi(ctx: Context, req: Int, taskId: String? = null): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (taskId != null) { action = Intent.ACTION_VIEW; data = Uri.parse("masterr://task/$taskId") }
        }
        return pi(ctx, req, intent, true)
    }

    private fun addTaskPi(ctx: Context, req: Int): PendingIntent {
        val intent = Intent(ctx, QuickCaptureActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return pi(ctx, req, intent, true)
    }

    private fun actionPi(ctx: Context, req: Int, action: String, taskId: String?, minutes: Long, notifId: Int): PendingIntent {
        val intent = Intent(ctx, ActionReceiver::class.java).apply {
            this.action = action
            putExtra("taskId", taskId)
            putExtra("minutes", minutes)
            putExtra("notifId", notifId)
        }
        return pi(ctx, req, intent, false)
    }

    fun notifyReminder(ctx: Context, t: Task, overdue: Boolean) {
        ensureChannels(ctx)
        val notifId = ((if (overdue) "ovd" else "rem") + t.id).hashCode()
        val bits = ArrayList<String>()
        t.dueMillis?.let { bits.add(relLabel(it) + (t.dueTime.takeIf { s -> s.isNotBlank() }?.let { " · " + fmt12(t.dueTime) } ?: "")) }
        if (t.estimateMin != null) bits.add("~" + fmtDur(t.estimateMin))
        bits.add(t.statusName)
        val title = if (overdue) "⚠️ Overdue · ${t.title}" else "⏰ Due ${t.dueMillis?.let { relLabel(it) } ?: "soon"} · ${t.title}"
        var base = t.id.hashCode()
        val b = NotificationCompat.Builder(ctx, CH_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_masterr)
            .setContentTitle(title)
            .setContentText(bits.joinToString(" · "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(t.title + "\n" + bits.joinToString(" · ")))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppPi(ctx, base++, t.id))
            .addAction(0, "Done", actionPi(ctx, base++, ActionReceiver.ACTION_DONE, t.id, 0, notifId))
        if (overdue) {
            b.addAction(0, "Reschedule +1d", actionPi(ctx, base++, ActionReceiver.ACTION_SNOOZE, t.id, 1440, notifId))
        } else {
            b.addAction(0, "Start", openAppPi(ctx, base++, t.id))
            b.addAction(0, "Snooze 2h", actionPi(ctx, base++, ActionReceiver.ACTION_SNOOZE, t.id, 120, notifId))
        }
        post(ctx, notifId, b)
    }

    fun notifyCheckin(ctx: Context, notifId: Int, title: String, text: String) {
        ensureChannels(ctx)
        var base = notifId * 10
        val b = NotificationCompat.Builder(ctx, CH_CHECKINS)
            .setSmallIcon(R.drawable.ic_stat_masterr)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(addTaskPi(ctx, base++))
            .addAction(0, "Add task", addTaskPi(ctx, base++))
            .addAction(0, "Nothing new", actionPi(ctx, base++, ActionReceiver.ACTION_NOTHING_NEW, null, 0, notifId))
        post(ctx, notifId, b)
    }

    fun notifyCatchup(ctx: Context, title: String, text: String) {
        ensureChannels(ctx)
        val notifId = 1005
        val b = NotificationCompat.Builder(ctx, CH_ASSISTANT)
            .setSmallIcon(R.drawable.ic_stat_masterr)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openAppPi(ctx, notifId * 10))
            .addAction(0, "Open Masterr", openAppPi(ctx, notifId * 10 + 1))
        post(ctx, notifId, b)
    }

    private fun post(ctx: Context, id: Int, b: NotificationCompat.Builder) {
        try {
            if (NotificationManagerCompat.from(ctx).areNotificationsEnabled()) {
                NotificationManagerCompat.from(ctx).notify(id, b.build())
            }
        } catch (e: SecurityException) { /* no POST_NOTIFICATIONS permission yet */ }
    }

    fun cancel(ctx: Context, id: Int) = NotificationManagerCompat.from(ctx).cancel(id)
}
