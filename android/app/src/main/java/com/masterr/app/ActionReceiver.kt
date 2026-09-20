package com.masterr.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_DONE = "com.masterr.app.DONE"
        const val ACTION_SNOOZE = "com.masterr.app.SNOOZE"
        const val ACTION_NOTHING_NEW = "com.masterr.app.NOTHING_NEW"
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        val pending = goAsync()
        val action = intent.action
        val taskId = intent.getStringExtra("taskId")
        val minutes = intent.getLongExtra("minutes", 0L)
        val notifId = intent.getIntExtra("notifId", -1)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cfg = Prefs.readConfig(ctx)
                when (action) {
                    ACTION_DONE -> if (!taskId.isNullOrBlank()) {
                        Repo.markDone(ctx, cfg, taskId); Prefs.clearReminderHistory(ctx, taskId)
                    }
                    ACTION_SNOOZE -> if (!taskId.isNullOrBlank()) {
                        Repo.snooze(ctx, cfg, taskId, minutes); Prefs.clearReminderHistory(ctx, taskId)
                    }
                    ACTION_NOTHING_NEW -> Prefs.markNothingNew(ctx)
                }
                Prefs.markInteract(ctx)
                Widget.refresh(ctx)
            } catch (e: Exception) {
                // ignore; nothing we can surface from a receiver
            } finally {
                if (notifId >= 0) Notifier.cancel(ctx, notifId)
                pending.finish()
            }
        }
    }
}
