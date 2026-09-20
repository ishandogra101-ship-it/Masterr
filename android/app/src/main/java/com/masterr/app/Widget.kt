package com.masterr.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MasterrWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        Widget.updateAll(ctx)
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        if (intent.action == "com.masterr.app.WIDGET_REFRESH") Widget.updateAll(ctx)
    }
}

object Widget {
    fun refresh(ctx: Context) {
        val intent = Intent(ctx, MasterrWidget::class.java).setAction("com.masterr.app.WIDGET_REFRESH")
        ctx.sendBroadcast(intent)
    }

    fun updateAll(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val ids = mgr.getAppWidgetIds(ComponentName(ctx, MasterrWidget::class.java))
        if (ids.isEmpty()) return
        CoroutineScope(Dispatchers.IO).launch {
            val cfg = Prefs.readConfig(ctx)
            val lines: List<String>
            var next = ""
            if (!cfg.firebaseReady || !Repo.signedIn(ctx, cfg)) {
                lines = listOf("Tap to set up sync", "", "")
            } else {
                val tasks = try { Assistant.sortedForFocus(Repo.tasks(ctx, cfg)) } catch (e: Exception) { emptyList() }
                if (tasks.isEmpty()) {
                    lines = listOf("All clear 🎉", "", "")
                } else {
                    lines = tasks.take(3).map { t ->
                        val dot = dotFor(t)
                        val whenStr = t.dueMillis?.let { " — " + relLabel(it) } ?: ""
                        "$dot ${t.title}$whenStr"
                    } + List(3) { "" }
                    next = "NEXT: " + tasks.first().title
                }
            }
            val views = RemoteViews(ctx.packageName, R.layout.widget_masterr)
            views.setTextViewText(R.id.widget_line1, lines.getOrElse(0) { "" })
            views.setTextViewText(R.id.widget_line2, lines.getOrElse(1) { "" })
            views.setTextViewText(R.id.widget_line3, lines.getOrElse(2) { "" })
            views.setTextViewText(R.id.widget_next, next)

            val addPi = PendingIntent.getActivity(
                ctx, 71, Intent(ctx, QuickCaptureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val openPi = PendingIntent.getActivity(
                ctx, 72, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_add, addPi)
            views.setOnClickPendingIntent(R.id.widget_title, openPi)
            views.setOnClickPendingIntent(R.id.widget_line1, openPi)
            mgr.updateAppWidget(ids, views)
        }
    }

    private fun dotFor(t: Task): String {
        val d = t.dueMillis ?: return "⚪"
        val now = System.currentTimeMillis()
        val h = (d - now) / 3_600_000.0
        return when {
            h <= 0 -> "🔴"      // overdue red
            h < 24 -> "🔴"      // due today red
            h < 72 -> "🟠"      // soon orange
            else -> "🟢"        // green
        }
    }
}
