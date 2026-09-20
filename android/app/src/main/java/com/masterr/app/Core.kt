package com.masterr.app

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Runtime Firebase + Gemini config, pasted by the user (mirrors the web app). */
data class Config(
    val apiKey: String = "",
    val appId: String = "",
    val projectId: String = "",
    val senderId: String = "",
    val webClientId: String = "",
    val geminiKey: String = "",
    val geminiModel: String = "gemini-3.6-flash",
    val dashboardUrl: String = "https://ishandogra101-ship-it.github.io/Masterr/"
) {
    val firebaseReady get() = apiKey.isNotBlank() && appId.isNotBlank() && projectId.isNotBlank()
    val signInReady get() = firebaseReady && webClientId.isNotBlank()
}

data class Cat(val id: String, val name: String, val color: String)
data class Stat(val id: String, val name: String, val color: String, val done: Boolean)
data class Sub(val title: String, val done: Boolean, val due: String)

data class Task(
    val id: String,
    val title: String,
    val categoryId: String,
    val catName: String,
    val catColor: String,
    val statusId: String,
    val statusName: String,
    val done: Boolean,
    val priority: String,
    val due: String,
    val dueTime: String,
    val estimateMin: Int?,
    val progress: Int,
    val subtasks: List<Sub>,
    val people: List<String>,
    val archived: Boolean,
    val updatedAt: Long
) {
    val dueMillis: Long? get() = dueToMillis(due, dueTime)
}

object Model {
    fun parse(state: JSONObject): List<Task> {
        val cats = HashMap<String, Cat>()
        state.optJSONArray("categories")?.let { arr ->
            for (i in 0 until arr.length()) {
                val c = arr.optJSONObject(i) ?: continue
                cats[c.optString("id")] = Cat(c.optString("id"), c.optString("name", "—"), c.optString("color", "#7B5CFF"))
            }
        }
        val stats = HashMap<String, Stat>()
        state.optJSONArray("statuses")?.let { arr ->
            for (i in 0 until arr.length()) {
                val s = arr.optJSONObject(i) ?: continue
                stats[s.optString("id")] = Stat(s.optString("id"), s.optString("name", "—"), s.optString("color", "#999"), s.optBoolean("done", false))
            }
        }
        val out = ArrayList<Task>()
        val items = state.optJSONArray("items") ?: return out
        for (i in 0 until items.length()) {
            val it = items.optJSONObject(i) ?: continue
            if (it.optBoolean("archived", false)) continue
            val cat = cats[it.optString("categoryId")] ?: Cat("", "—", "#7B5CFF")
            val st = stats[it.optString("statusId")] ?: Stat("", "—", "#999", false)
            val subs = ArrayList<Sub>()
            it.optJSONArray("subtasks")?.let { sa ->
                for (j in 0 until sa.length()) {
                    val s = sa.optJSONObject(j) ?: continue
                    subs.add(Sub(s.optString("title"), s.optBoolean("done", false), s.optString("due", "")))
                }
            }
            val ppl = ArrayList<String>()
            it.optJSONArray("people")?.let { pa ->
                for (j in 0 until pa.length()) pa.optJSONObject(j)?.optString("name")?.takeIf { n -> n.isNotBlank() }?.let(ppl::add)
            }
            val est = if (it.isNull("estimateMin")) null else it.optInt("estimateMin").takeIf { it > 0 }
            val prog = progressOf(it, subs, st.done)
            out.add(
                Task(
                    id = it.optString("id"),
                    title = it.optString("title", "Untitled"),
                    categoryId = it.optString("categoryId"),
                    catName = cat.name, catColor = cat.color,
                    statusId = it.optString("statusId"),
                    statusName = st.name, done = st.done,
                    priority = it.optString("priority", "Medium"),
                    due = it.optString("due", ""),
                    dueTime = it.optString("dueTime", ""),
                    estimateMin = est,
                    progress = prog,
                    subtasks = subs, people = ppl,
                    archived = false,
                    updatedAt = it.optLong("updatedAt", 0L)
                )
            )
        }
        return out
    }

    private fun progressOf(it: JSONObject, subs: List<Sub>, done: Boolean): Int {
        if (!it.isNull("progress")) {
            val p = it.optInt("progress", -1)
            if (p in 0..100) return p
        }
        if (subs.isNotEmpty()) return Math.round(subs.count { it.done } * 100f / subs.size)
        return if (done) 100 else 0
    }

    fun categories(state: JSONObject): List<Cat> {
        val out = ArrayList<Cat>()
        state.optJSONArray("categories")?.let { arr ->
            for (i in 0 until arr.length()) {
                val c = arr.optJSONObject(i) ?: continue
                out.add(Cat(c.optString("id"), c.optString("name", "—"), c.optString("color", "#7B5CFF")))
            }
        }
        return out
    }
}

private val ZONE: ZoneId get() = ZoneId.systemDefault()

fun dueToMillis(due: String?, dueTime: String?): Long? {
    if (due.isNullOrBlank()) return null
    return try {
        val d = LocalDate.parse(due)
        val t = if (!dueTime.isNullOrBlank()) LocalTime.parse(dueTime) else LocalTime.of(23, 59)
        LocalDateTime.of(d, t).atZone(ZONE).toInstant().toEpochMilli()
    } catch (e: Exception) { null }
}

fun todayIso(): String = LocalDate.now(ZONE).format(DateTimeFormatter.ISO_LOCAL_DATE)

fun relLabel(millis: Long): String {
    val now = System.currentTimeMillis()
    val diff = millis - now
    val day = 86_400_000L
    val hr = 3_600_000L
    return when {
        diff < 0 -> {
            val ad = Math.abs(diff)
            if (ad < hr) "just now" else if (ad < day) "${ad / hr}h ago" else "${ad / day}d ago"
        }
        diff < hr -> "in ${Math.max(1, diff / 60000)}m"
        diff < day -> "in ${diff / hr}h"
        diff < 2 * day -> "tomorrow"
        diff < 7 * day -> "${diff / day}d"
        else -> LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), ZONE)
            .format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
    }
}

fun fmtDur(min: Int?): String {
    val m = min ?: return ""
    val h = m / 60; val r = m % 60
    return if (h > 0) (if (r > 0) "${h}h ${r}m" else "${h}h") else "${r}m"
}

fun fmt12(hm: String?): String {
    if (hm.isNullOrBlank()) return ""
    return try {
        val t = LocalTime.parse(hm)
        t.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
    } catch (e: Exception) { hm }
}

fun genId(): String = (System.currentTimeMillis().toString(36) + (1000 + (Math.random() * 9000).toInt()).toString(36))
