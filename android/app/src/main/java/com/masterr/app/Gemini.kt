package com.masterr.app

import android.content.Context
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

    private val PREFERRED = listOf(
        "gemini-2.5-flash", "gemini-2.0-flash", "gemini-2.0-flash-001",
        "gemini-1.5-flash", "gemini-1.5-flash-latest", "gemini-flash-latest"
    )

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

    private val BASES = listOf("v1beta", "v1")

    /** Returns (responseBody, httpCode). */
    private fun post(base: String, model: String, key: String, bodyJson: String): Pair<String, Int> {
        val url = "https://generativelanguage.googleapis.com/$base/models/$model:generateContent?key=$key"
        val req = Request.Builder().url(url).post(bodyJson.toRequestBody("application/json".toMediaType())).build()
        client.newCall(req).execute().use { resp ->
            return Pair(resp.body?.string() ?: "", resp.code)
        }
    }

    /** Ask the API which models this key can actually use for generateContent. */
    private fun listModels(base: String, key: String): List<String> {
        return try {
            val url = "https://generativelanguage.googleapis.com/$base/models?key=$key&pageSize=200"
            client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val arr = JSONObject(resp.body?.string() ?: "").optJSONArray("models") ?: return emptyList()
                val usable = ArrayList<String>()
                for (i in 0 until arr.length()) {
                    val m = arr.optJSONObject(i) ?: continue
                    val methods = m.optJSONArray("supportedGenerationMethods")
                    var ok = false
                    if (methods != null) for (j in 0 until methods.length()) if (methods.optString(j) == "generateContent") ok = true
                    if (!ok) continue
                    val name = m.optString("name").removePrefix("models/")
                    if (name.isNotBlank()) usable.add(name)
                }
                usable
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun pick(usable: List<String>): String? =
        PREFERRED.firstOrNull { usable.contains(it) }
            ?: usable.firstOrNull { it.contains("flash") && !it.contains("thinking") }
            ?: usable.firstOrNull { it.startsWith("gemini") }
            ?: usable.firstOrNull()

    private fun errorSnippet(body: String): String {
        return try { JSONObject(body).optJSONObject("error")?.optString("message")?.take(180) ?: body.take(160) }
        catch (e: Exception) { body.take(160) }
    }

    suspend fun chat(ctx: Context, cfg: Config, categories: List<String>, history: List<Pair<String, String>>): AiResult =
        withContext(Dispatchers.IO) {
            if (cfg.geminiKey.isBlank()) return@withContext AiResult(
                "Add your Gemini API key in Settings so I can understand tasks.", "smalltalk", false, null
            )
            try {
                val contents = JSONArray()
                for ((role, text) in history) {
                    contents.put(JSONObject().put("role", role).put("parts", JSONArray().put(JSONObject().put("text", text))))
                }
                val bodyJson = JSONObject()
                    .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt(categories)))))
                    .put("contents", contents)
                    .put("generationConfig", JSONObject().put("temperature", 0.2).put("responseMimeType", "application/json"))
                    .toString()

                var lastCode = 0
                var lastBody = ""
                for (base in BASES) {
                    var model = cfg.geminiModel.ifBlank { "gemini-2.0-flash" }
                    var (txt, code) = post(base, model, cfg.geminiKey, bodyJson)
                    if (code == 404) {
                        val alt = pick(listModels(base, cfg.geminiKey))
                        if (!alt.isNullOrBlank() && alt != model) {
                            val retry = post(base, alt, cfg.geminiKey, bodyJson)
                            if (retry.second == 200) { try { Prefs.saveConfig(ctx, cfg.copy(geminiModel = alt)) } catch (e: Exception) {} }
                            txt = retry.first; code = retry.second; model = alt
                        }
                    }
                    if (code == 200) {
                        val root = JSONObject(txt)
                        val cand = root.optJSONArray("candidates")?.optJSONObject(0)
                        val part = cand?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)
                        val out = part?.optString("text") ?: return@withContext AiResult("I didn't catch that — try again?", "smalltalk", false, null)
                        return@withContext parse(out)
                    }
                    lastCode = code; lastBody = txt
                    if (code == 400 || code == 403) break   // auth/key problems won't change across versions
                }
                AiResult("Assistant error ($lastCode): ${errorSnippet(lastBody)}", "smalltalk", false, null)
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
