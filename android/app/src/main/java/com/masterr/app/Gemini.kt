package com.masterr.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

data class AiResult(
    val reply: String,
    val intent: String,      // create | recommend | plan | smalltalk | clarify
    val ready: Boolean,
    val draft: TaskDraft?
)

object Gemini {
    private val client = OkHttpClient.Builder()
        .callTimeout(45, TimeUnit.SECONDS)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    private fun systemPrompt(categories: List<String>): String {
        val now = LocalDateTime.now(ZoneId.systemDefault())
        val today = now.format(DateTimeFormatter.ofPattern("EEEE, yyyy-MM-dd HH:mm", Locale.getDefault()))
        val cats = if (categories.isEmpty()) "Academics, Case Comps, Admin, Personal" else categories.joinToString(", ")
        return """
You are Masterr, a warm, concise academic assistant that captures a student's tasks.
Current date/time (user's local): $today. Resolve relative dates ("Monday", "tomorrow", "28th") against this.
Existing categories: $cats. Map each task to the closest one by name.

You ALWAYS reply with a single JSON object, no markdown, matching:
{
  "intent": "create" | "recommend" | "plan" | "smalltalk" | "clarify",
  "reply": "one short friendly sentence to the user",
  "ready": true | false,
  "task": {
    "title": "short task title",
    "categoryName": "one of the existing categories",
    "due": "YYYY-MM-DD or empty",
    "dueTime": "HH:MM 24h or empty",
    "estimateMin": integer minutes or null,
    "priority": "Low" | "Medium" | "High" | "Critical",
    "notes": "",
    "subtasks": ["optional", "milestone", "strings"]
  }
}

Rules:
- If the user is describing academic work (assignment, quiz, presentation, reading, submission, exam, deadline), intent = "create".
- Extract everything already stated. Ask for only ONE genuinely missing important thing at a time in "reply" (prefer due date, then due time if it matters, then rough effort). Never re-ask something already known.
- Set "ready": true once you have at least a title and a due date; time and effort are optional bonuses. When ready, "reply" should confirm briefly (e.g. "Got it, added.").
- Do NOT ask about fields the user clearly doesn't care about. Accept "skip"/"not sure" and move on with sensible defaults.
- "what should I work on" -> intent "recommend". "plan my day" -> intent "plan". Small talk/greetings -> "smalltalk". For recommend/plan/smalltalk, "task" may be null and "ready" false.
- Keep replies human and varied, never robotic. One or two short sentences max.
""".trim()
    }

    suspend fun chat(cfg: Config, categories: List<String>, history: List<Pair<String, String>>): AiResult =
        withContext(Dispatchers.IO) {
            if (cfg.geminiKey.isBlank()) return@withContext AiResult(
                "Add your Gemini API key in Settings so I can understand tasks.", "smalltalk", false, null
            )
            try {
                val contents = JSONArray()
                for ((role, text) in history) {
                    contents.put(JSONObject().put("role", role).put("parts", JSONArray().put(JSONObject().put("text", text))))
                }
                val body = JSONObject()
                    .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt(categories)))))
                    .put("contents", contents)
                    .put("generationConfig", JSONObject().put("temperature", 0.2).put("responseMimeType", "application/json"))
                    .toString()

                val url = "https://generativelanguage.googleapis.com/v1beta/models/${cfg.geminiModel}:generateContent?key=${cfg.geminiKey}"
                val req = Request.Builder().url(url)
                    .post(body.toRequestBody("application/json".toMediaType())).build()
                client.newCall(req).execute().use { resp ->
                    val txt = resp.body?.string() ?: ""
                    if (!resp.isSuccessful) return@withContext AiResult(
                        "Assistant error (${resp.code}). Check your Gemini key/model in Settings.", "smalltalk", false, null
                    )
                    val root = JSONObject(txt)
                    val cand = root.optJSONArray("candidates")?.optJSONObject(0)
                    val part = cand?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)
                    val out = part?.optString("text") ?: return@withContext AiResult(
                        "I didn't catch that — try again?", "smalltalk", false, null
                    )
                    parse(out)
                }
            } catch (e: Exception) {
                AiResult("Couldn't reach the assistant. You can still add tasks on the web dashboard.", "smalltalk", false, null)
            }
        }

    private fun parse(text: String): AiResult {
        return try {
            val o = JSONObject(text.trim())
            val intent = o.optString("intent", "smalltalk")
            val reply = o.optString("reply", "Okay.")
            val ready = o.optBoolean("ready", false)
            val t = o.optJSONObject("task")
            var draft: TaskDraft? = null
            if (t != null && t.optString("title").isNotBlank()) {
                val subs = ArrayList<String>()
                t.optJSONArray("subtasks")?.let { for (i in 0 until it.length()) it.optString(i).takeIf { s -> s.isNotBlank() }?.let(subs::add) }
                val est = if (t.isNull("estimateMin")) null else t.optInt("estimateMin").takeIf { it > 0 }
                draft = TaskDraft(
                    title = t.optString("title"),
                    categoryName = t.optString("categoryName").ifBlank { null },
                    due = t.optString("due"),
                    dueTime = t.optString("dueTime"),
                    estimateMin = est,
                    priority = t.optString("priority", "Medium").ifBlank { "Medium" },
                    notes = t.optString("notes"),
                    subtasks = subs
                )
            }
            AiResult(reply, intent, ready, draft)
        } catch (e: Exception) {
            AiResult(text.take(300), "smalltalk", false, null)
        }
    }
}
