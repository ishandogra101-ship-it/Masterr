package com.masterr.app

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

data class TaskDraft(
    val title: String,
    val categoryName: String? = null,
    val due: String = "",
    val dueTime: String = "",
    val estimateMin: Int? = null,
    val priority: String = "Medium",
    val notes: String = "",
    val subtasks: List<String> = emptyList()
)

object Repo {
    private const val APP = "masterr"

    private fun app(ctx: Context, cfg: Config): FirebaseApp {
        return try {
            FirebaseApp.getInstance(APP)
        } catch (e: IllegalStateException) {
            val opts = FirebaseOptions.Builder()
                .setApiKey(cfg.apiKey)
                .setApplicationId(cfg.appId)
                .setProjectId(cfg.projectId)
                .apply { if (cfg.senderId.isNotBlank()) setGcmSenderId(cfg.senderId) }
                .build()
            FirebaseApp.initializeApp(ctx, opts, APP)
        }
    }

    fun auth(ctx: Context, cfg: Config): FirebaseAuth = FirebaseAuth.getInstance(app(ctx, cfg))
    fun db(ctx: Context, cfg: Config): FirebaseFirestore = FirebaseFirestore.getInstance(app(ctx, cfg))
    fun uid(ctx: Context, cfg: Config): String? = try { auth(ctx, cfg).currentUser?.uid } catch (e: Exception) { null }
    fun signedIn(ctx: Context, cfg: Config): Boolean = uid(ctx, cfg) != null

    suspend fun loadState(ctx: Context, cfg: Config): JSONObject {
        val uid = uid(ctx, cfg) ?: return emptyState()
        return try {
            val snap = db(ctx, cfg).collection("masterr").document(uid).get().await()
            val blob = snap.getString("blob")
            if (blob.isNullOrBlank()) emptyState() else JSONObject(blob)
        } catch (e: Exception) { emptyState() }
    }

    suspend fun tasks(ctx: Context, cfg: Config): List<Task> = Model.parse(loadState(ctx, cfg))

    private suspend fun saveState(ctx: Context, cfg: Config, state: JSONObject) {
        val uid = uid(ctx, cfg) ?: return
        val now = System.currentTimeMillis()
        state.put("updatedAt", now)
        val data = hashMapOf<String, Any>("blob" to state.toString(), "updatedAt" to now)
        db(ctx, cfg).collection("masterr").document(uid).set(data).await()
    }

    private fun emptyState(): JSONObject {
        val s = JSONObject()
        s.put("version", 1)
        s.put("updatedAt", System.currentTimeMillis())
        s.put("meta", JSONObject().put("title", "Masterr").put("subtitle", "Command Ledger"))
        s.put("categories", JSONArray().apply {
            put(JSONObject().put("id", "acad").put("name", "Academics").put("color", "#2BA8FF").put("milestones", JSONArray()))
            put(JSONObject().put("id", "case").put("name", "Case Comps").put("color", "#FF4E8A").put("milestones", JSONArray()))
            put(JSONObject().put("id", "admin").put("name", "Admin").put("color", "#FF7A4D").put("milestones", JSONArray()))
            put(JSONObject().put("id", "pers").put("name", "Personal").put("color", "#FFB020").put("milestones", JSONArray()))
        })
        s.put("statuses", JSONArray().apply {
            put(JSONObject().put("id", "todo").put("name", "Not started").put("color", "#9A93B4").put("done", false))
            put(JSONObject().put("id", "prog").put("name", "In progress").put("color", "#2BA8FF").put("done", false))
            put(JSONObject().put("id", "done").put("name", "Done").put("color", "#12B981").put("done", true))
        })
        s.put("items", JSONArray())
        s.put("settings", JSONObject())
        return s
    }

    private fun doneStatusId(state: JSONObject): String {
        state.optJSONArray("statuses")?.let { arr ->
            for (i in 0 until arr.length()) { val st = arr.optJSONObject(i); if (st != null && st.optBoolean("done")) return st.optString("id") }
        }
        return "done"
    }

    private fun openStatusId(state: JSONObject): String {
        state.optJSONArray("statuses")?.let { arr ->
            for (i in 0 until arr.length()) { val st = arr.optJSONObject(i); if (st != null && !st.optBoolean("done")) return st.optString("id") }
        }
        return "todo"
    }

    private fun resolveCategoryId(state: JSONObject, hint: String?): String {
        var cats = state.optJSONArray("categories")
        if (cats == null || cats.length() == 0) {
            cats = JSONArray().apply { put(JSONObject().put("id", "acad").put("name", "Academics").put("color", "#2BA8FF").put("milestones", JSONArray())) }
            state.put("categories", cats)
        }
        if (!hint.isNullOrBlank()) {
            val h = hint.lowercase()
            for (i in 0 until cats.length()) {
                val c = cats.optJSONObject(i) ?: continue
                val n = c.optString("name").lowercase()
                if (n.isNotBlank() && (n.contains(h) || h.contains(n))) return c.optString("id")
            }
        }
        return cats.getJSONObject(0).optString("id")
    }

    private fun addTaskTo(state: JSONObject, d: TaskDraft): String {
        val items = state.optJSONArray("items") ?: JSONArray().also { state.put("items", it) }
        val now = System.currentTimeMillis()
        val subs = JSONArray()
        d.subtasks.filter { it.isNotBlank() }.forEach {
            subs.put(JSONObject().put("id", genId()).put("title", it).put("done", false).put("due", ""))
        }
        val o = JSONObject()
        o.put("id", genId())
        o.put("title", d.title.ifBlank { "Untitled" })
        o.put("categoryId", resolveCategoryId(state, d.categoryName))
        o.put("statusId", openStatusId(state))
        o.put("priority", d.priority)
        o.put("due", d.due)
        o.put("dueTime", d.dueTime)
        o.put("estimateMin", if (d.estimateMin != null && d.estimateMin > 0) d.estimateMin else JSONObject.NULL)
        o.put("notes", d.notes)
        o.put("tags", JSONArray())
        o.put("people", JSONArray())
        o.put("subtasks", subs)
        o.put("fields", JSONArray())
        o.put("progress", JSONObject.NULL)
        o.put("createdAt", now)
        o.put("updatedAt", now)
        o.put("archived", false)
        items.put(o)
        return o.getString("id")
    }

    /** Loads latest, appends the task, saves the whole blob. Returns the new id. */
    suspend fun addTask(ctx: Context, cfg: Config, d: TaskDraft): String {
        val state = loadState(ctx, cfg)
        val id = addTaskTo(state, d)
        saveState(ctx, cfg, state)
        return id
    }

    private fun findItem(state: JSONObject, id: String): JSONObject? {
        val items = state.optJSONArray("items") ?: return null
        for (i in 0 until items.length()) { val o = items.optJSONObject(i); if (o != null && o.optString("id") == id) return o }
        return null
    }

    suspend fun markDone(ctx: Context, cfg: Config, id: String) {
        val state = loadState(ctx, cfg)
        val o = findItem(state, id) ?: return
        o.put("statusId", doneStatusId(state))
        o.put("progress", 100)
        o.put("updatedAt", System.currentTimeMillis())
        saveState(ctx, cfg, state)
    }

    /** Push the due date/time forward by [addMinutes]. */
    suspend fun snooze(ctx: Context, cfg: Config, id: String, addMinutes: Long) {
        val state = loadState(ctx, cfg)
        val o = findItem(state, id) ?: return
        val base = dueToMillis(o.optString("due"), o.optString("dueTime")) ?: System.currentTimeMillis()
        val nt = java.time.LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(base + addMinutes * 60000L), java.time.ZoneId.systemDefault()
        )
        o.put("due", nt.toLocalDate().toString())
        o.put("dueTime", String.format("%02d:%02d", nt.hour, nt.minute))
        o.put("updatedAt", System.currentTimeMillis())
        saveState(ctx, cfg, state)
    }
}
