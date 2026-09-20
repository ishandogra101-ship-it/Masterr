package com.masterr.app

import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Assistant {

    private fun prioRank(p: String) = when (p) { "Critical" -> 0; "High" -> 1; "Medium" -> 2; else -> 3 }

    /** Higher = more pressing. Blends deadline, priority and remaining work. */
    fun urgency(t: Task, now: Long): Double {
        val due = t.dueMillis
        val hoursLeft = if (due == null) 24 * 30.0 else (due - now) / 3_600_000.0
        // deadline pressure: overdue and near-due score highest
        val deadline = when {
            due == null -> 5.0
            hoursLeft <= 0 -> 100.0 + (-hoursLeft)          // overdue climbs further
            hoursLeft < 6 -> 90.0
            hoursLeft < 24 -> 70.0
            hoursLeft < 72 -> 45.0
            hoursLeft < 24 * 7 -> 25.0
            else -> 10.0
        }
        val prio = (3 - prioRank(t.priority)) * 6.0            // 0..18
        val remaining = (100 - t.progress) / 100.0 * 8.0        // more left = a bit more urgent
        return deadline + prio + remaining
    }

    fun sortedForFocus(tasks: List<Task>, now: Long = System.currentTimeMillis()): List<Task> =
        tasks.filter { !it.done }.sortedByDescending { urgency(it, now) }

    fun recommend(tasks: List<Task>): String {
        val now = System.currentTimeMillis()
        val open = tasks.filter { !it.done }
        if (open.isEmpty()) return "You're all clear — nothing open right now. 🎉"
        val t = open.maxByOrNull { urgency(it, now) }!!
        val due = t.dueMillis
        val hoursLeft = if (due == null) null else (due - now) / 3_600_000.0
        val head = when {
            hoursLeft != null && hoursLeft <= 0 -> "⚠️ Work on ${t.title} — it's overdue."
            hoursLeft != null && hoursLeft < 24 -> "🔴 Work on ${t.title}."
            hoursLeft != null && hoursLeft < 72 -> "🟠 Work on ${t.title}."
            else -> "🟢 Work on ${t.title}."
        }
        val lines = ArrayList<String>()
        lines.add(head)
        if (due != null) {
            val whenStr = LocalDateTime.ofInstant(Instant.ofEpochMilli(due), ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("EEE d MMM, h:mm a", Locale.getDefault()))
            lines.add("Due ${relLabel(due)} · $whenStr.")
        }
        if (t.estimateMin != null) lines.add("Estimated effort: ${fmtDur(t.estimateMin)}.")
        lines.add("Progress: ${t.progress}%. Category: ${t.catName}.")
        val reason = when {
            hoursLeft != null && hoursLeft <= 0 -> "It's past due and still open — clear it first."
            hoursLeft != null && hoursLeft < 24 -> "It's the nearest deadline and needs attention today."
            t.priority == "Critical" || t.priority == "High" -> "It's high priority with the tightest timeline."
            else -> "It's the most pressing thing on balance of deadline and effort."
        }
        lines.add(reason)
        val runnerUp = open.filter { it.id != t.id }.maxByOrNull { urgency(it, now) }
        if (runnerUp != null) lines.add("Then: ${runnerUp.title}${runnerUp.dueMillis?.let { " (" + relLabel(it) + ")" } ?: ""}.")
        return lines.joinToString("\n")
    }

    fun planDay(tasks: List<Task>): String {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val open = tasks.filter { !it.done }
        // today's candidates: overdue, due today, or high-urgency with no date
        val startOfTomorrow = LocalDateTime.now(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val candidates = open.filter {
            val d = it.dueMillis
            d == null || d < startOfTomorrow || urgency(it, now) >= 60
        }.sortedByDescending { urgency(it, now) }.take(6)
        if (candidates.isEmpty()) return "Nothing urgent for today. Enjoy the breathing room, or ask me to add something."

        var cursor = LocalDateTime.now(zone).plusMinutes(10)
        // round up to next 15 minutes, and not before 9am
        cursor = cursor.withSecond(0).withNano(0)
        val addM = (15 - cursor.minute % 15) % 15
        cursor = cursor.plusMinutes(addM.toLong())
        if (cursor.toLocalTime().isBefore(LocalTime.of(9, 0))) cursor = cursor.toLocalDate().atTime(9, 0)

        val fmt = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
        val sb = StringBuilder("🗓️  TODAY\n")
        var total = 0
        for (t in candidates) {
            val mins = t.estimateMin ?: 45
            val end = cursor.plusMinutes(mins.toLong())
            sb.append("\n${cursor.format(fmt)}–${end.format(fmt)}  ·  ${t.title}")
            total += mins
            cursor = end.plusMinutes(10) // short break
        }
        sb.append("\n\nTotal focused time: ${fmtDur(total)}.")
        return sb.toString()
    }
}
