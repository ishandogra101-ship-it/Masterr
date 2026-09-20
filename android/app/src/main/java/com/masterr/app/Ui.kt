package com.masterr.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private val Violet = Color(0xFF7B5CFF)
private val VioletInk = Color(0xFF5B3EE0)
private val Bg = Color(0xFFF3F2FC)
private val Ink = Color(0xFF241B36)
private val Panel = Color(0xFFFFFFFF)

@Composable
fun MasterrTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Violet, onPrimary = Color.White,
            background = Bg, onBackground = Ink,
            surface = Panel, onSurface = Ink,
            secondary = VioletInk
        ),
        content = content
    )
}

class ChatMsg(val fromUser: Boolean, val text: String)

private fun greeting(): String = listOf(
    "Hey 👋 What do you need to remember? Dump anything — \"OB quiz Thursday 10am\".",
    "What's on your plate? Tell me a task and I'll file it. Try \"Finance assignment due Monday\".",
    "Ready when you are. Tell me about new college work, or ask \"what should I work on?\""
).random()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    cfg: Config,
    initialText: String?,
    onSettings: () -> Unit,
    onDashboard: () -> Unit
) {
    val ctx = LocalContextX()
    val scope = rememberCoroutineScope()
    val msgs = remember { mutableStateListOf<ChatMsg>() }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var cats by remember { mutableStateOf(listOf<String>()) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    fun send(textArg: String? = null) {
        val text = (textArg ?: input).trim()
        if (text.isBlank() || busy) return
        input = ""
        msgs.add(ChatMsg(true, text))
        busy = true
        scope.launch {
            Prefs.markInteract(ctx)
            try {
                val low = text.lowercase()
                if (low.contains("what should i") || low.contains("work on")) {
                    val t = withContext(Dispatchers.IO) { Repo.tasks(ctx, cfg) }
                    msgs.add(ChatMsg(false, Assistant.recommend(t)))
                } else if (low.startsWith("plan ") || low == "plan" || low.contains("plan my day")) {
                    val t = withContext(Dispatchers.IO) { Repo.tasks(ctx, cfg) }
                    msgs.add(ChatMsg(false, Assistant.planDay(t)))
                } else {
                    val history = msgs.takeLast(16).dropWhile { !it.fromUser }
                        .map { (if (it.fromUser) "user" else "model") to it.text }
                    val r = Gemini.chat(cfg, cats, history)
                    when (r.intent) {
                        "recommend" -> { val t = withContext(Dispatchers.IO) { Repo.tasks(ctx, cfg) }; msgs.add(ChatMsg(false, Assistant.recommend(t))) }
                        "plan" -> { val t = withContext(Dispatchers.IO) { Repo.tasks(ctx, cfg) }; msgs.add(ChatMsg(false, Assistant.planDay(t))) }
                        "create" -> {
                            if (r.ready && r.draft != null) {
                                withContext(Dispatchers.IO) { Repo.addTask(ctx, cfg, r.draft) }
                                msgs.add(ChatMsg(false, confirmText(r.draft)))
                                Scheduler.runNow(ctx); Widget.refresh(ctx)
                            } else msgs.add(ChatMsg(false, r.reply))
                        }
                        else -> msgs.add(ChatMsg(false, r.reply))
                    }
                }
            } catch (e: Exception) {
                msgs.add(ChatMsg(false, "Hmm, that didn't go through. Try again?"))
            }
            busy = false
        }
    }

    LaunchedEffect(Unit) {
        if (msgs.isEmpty()) msgs.add(ChatMsg(false, greeting()))
        cats = withContext(Dispatchers.IO) { try { Model.categories(Repo.loadState(ctx, cfg)).map { it.name } } catch (e: Exception) { emptyList() } }
        if (!initialText.isNullOrBlank()) send(initialText)
    }
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) listState.animateScrollToItem(msgs.size - 1) }

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = { Text("Masterr", fontWeight = FontWeight.ExtraBold, color = Violet) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg),
                actions = {
                    IconButton(onClick = onDashboard) { Icon(Icons.Filled.Language, "Web dashboard", tint = Ink) }
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings", tint = Ink) }
                }
            )
        },
        bottomBar = {
            Surface(color = Bg) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = input, onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type a task or ask…") },
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send() })
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(onClick = { send() }, enabled = !busy,
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Violet)) {
                        Icon(Icons.Filled.Send, "Send", tint = Color.White)
                    }
                }
            }
        }
    ) { pad ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(pad).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            items(msgs) { m -> Bubble(m) }
            if (busy) item { Text("…", color = Color(0xFF8A83A6), modifier = Modifier.padding(6.dp)) }
        }
    }
}

@Composable
private fun Bubble(m: ChatMsg) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start) {
        Surface(
            color = if (m.fromUser) Violet else Panel,
            shape = RoundedCornerShape(18.dp),
            shadowElevation = 1.dp,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                m.text,
                color = if (m.fromUser) Color.White else Ink,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                fontSize = 15.sp
            )
        }
    }
}

private fun confirmText(d: TaskDraft): String {
    val sb = StringBuilder("✅ Added to Masterr\n")
    sb.append(d.title)
    if (d.due.isNotBlank()) sb.append("\n").append(d.due).append(if (d.dueTime.isNotBlank()) " · " + fmt12(d.dueTime) else "")
    if (d.estimateMin != null) sb.append("\nEst. effort: ").append(fmtDur(d.estimateMin))
    sb.append("\nIt's on your web dashboard and I'll remind you.")
    return sb.toString()
}

/* ------------------------------------------------------------------ Setup */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(current: Config, onSave: (Config) -> Unit) {
    var apiKey by remember { mutableStateOf(current.apiKey) }
    var appId by remember { mutableStateOf(current.appId) }
    var projectId by remember { mutableStateOf(current.projectId) }
    var senderId by remember { mutableStateOf(current.senderId) }
    var webClientId by remember { mutableStateOf(current.webClientId) }
    var geminiKey by remember { mutableStateOf(current.geminiKey) }
    var pasteJson by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Connect Masterr", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Violet)
        Text("Paste the same Firebase config you used on the web, plus a Web client ID and a Gemini key. See the in-repo guide for exact steps.",
            color = Color(0xFF5C5478), fontSize = 14.sp)

        OutlinedTextField(pasteJson, { pasteJson = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text("Paste firebaseConfig { … } (optional shortcut)") }, minLines = 3)
        Button(onClick = {
            val o = parseFirebaseConfig(pasteJson)
            if (o != null) {
                apiKey = o.optString("apiKey", apiKey)
                appId = o.optString("appId", appId)
                projectId = o.optString("projectId", projectId)
                senderId = o.optString("messagingSenderId", senderId)
            }
        }, colors = ButtonDefaults.buttonColors(containerColor = Violet)) { Text("Fill from pasted config") }

        Field("API key", apiKey) { apiKey = it }
        Field("App ID (appId)", appId) { appId = it }
        Field("Project ID", projectId) { projectId = it }
        Field("Messaging sender ID", senderId) { senderId = it }
        Field("Web client ID (for Google sign-in)", webClientId) { webClientId = it }
        Field("Gemini API key", geminiKey) { geminiKey = it }

        Button(
            onClick = { onSave(current.copy(apiKey = apiKey.trim(), appId = appId.trim(), projectId = projectId.trim(),
                senderId = senderId.trim(), webClientId = webClientId.trim(), geminiKey = geminiKey.trim())) },
            enabled = apiKey.isNotBlank() && appId.isNotBlank() && projectId.isNotBlank() && webClientId.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Violet)
        ) { Text("Save & continue") }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, modifier = Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true)
}

private fun parseFirebaseConfig(text: String): JSONObject? {
    return try {
        val s = text.indexOf('{'); val e = text.lastIndexOf('}')
        if (s < 0 || e < 0) return null
        var body = text.substring(s, e + 1)
        // convert {apiKey: "x"} -> {"apiKey":"x"} best-effort
        body = body.replace(Regex("([\\{,]\\s*)([A-Za-z0-9_]+)(\\s*:)"), "$1\"$2\"$3")
        JSONObject(body)
    } catch (e: Exception) { null }
}

/* ------------------------------------------------------------------ Settings */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onDashboard: () -> Unit, onReconfigure: () -> Unit, onSignOut: () -> Unit) {
    val ctx = LocalContextX()
    val scope = rememberCoroutineScope()
    val s by Prefs.settingsFlow(ctx).collectAsState(initial = Settings())
    fun save(n: Settings) { scope.launch { Prefs.saveSettings(ctx, n) } }

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {

            SectionTitle("Proactive assistant")
            SwitchRow("Assistant on", s.assistantOn) { save(s.copy(assistantOn = it)) }
            SwitchRow("Morning check-in", s.morningOn) { save(s.copy(morningOn = it)) }
            TimeRow("Morning time", s.morningTime) { save(s.copy(morningTime = it)) }
            SwitchRow("Midday check-in", s.middayOn) { save(s.copy(middayOn = it)) }
            TimeRow("Midday time", s.middayTime) { save(s.copy(middayTime = it)) }
            SwitchRow("Evening check-in", s.eveningOn) { save(s.copy(eveningOn = it)) }
            TimeRow("Evening time", s.eveningTime) { save(s.copy(eveningTime = it)) }

            SectionTitle("Bug me")
            SwitchRow("Bug Me (persistent nudges)", s.bugMe) { save(s.copy(bugMe = it)) }
            NumRow("Bug every N hours", s.bugEveryHours) { save(s.copy(bugEveryHours = it)) }
            SwitchRow("Inactivity catch-up", s.inactivityOn) { save(s.copy(inactivityOn = it)) }
            NumRow("Inactive after N days", s.inactivityDays) { save(s.copy(inactivityDays = it)) }

            SectionTitle("Deadline reminders")
            SwitchRow("Reminders on", s.remindersOn) { save(s.copy(remindersOn = it)) }
            CsvRow("Remind before (minutes, comma-sep)", s.reminderOffsets.joinToString(",")) {
                val list = it.split(",").mapNotNull { x -> x.trim().toIntOrNull() }
                if (list.isNotEmpty()) save(s.copy(reminderOffsets = list.sortedDescending()))
            }
            Text("Presets: 4320=3d · 1440=24h · 720=12h · 360=6h · 180=3h · 60=1h", fontSize = 12.sp, color = Color(0xFF8A83A6))
            SwitchRow("Overdue reminders", s.overdueOn) { save(s.copy(overdueOn = it)) }
            NumRow("Overdue every N hours", s.overdueEveryHours) { save(s.copy(overdueEveryHours = it)) }

            SectionTitle("Quiet hours")
            SwitchRow("Quiet hours on", s.quietOn) { save(s.copy(quietOn = it)) }
            TimeRow("Quiet start", s.quietStart) { save(s.copy(quietStart = it)) }
            TimeRow("Quiet end", s.quietEnd) { save(s.copy(quietEnd = it)) }

            SectionTitle("Account")
            OutlinedButton(onClick = onDashboard, modifier = Modifier.fillMaxWidth()) { Text("Open web dashboard") }
            OutlinedButton(onClick = onReconfigure, modifier = Modifier.fillMaxWidth()) { Text("Edit Firebase / Gemini config") }
            OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable private fun SectionTitle(t: String) {
    Text(t, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = VioletInk, modifier = Modifier.padding(top = 12.dp))
}
@Composable private fun SwitchRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = Ink)
        Switch(checked = value, onCheckedChange = onChange)
    }
}
@Composable private fun TimeRow(label: String, value: String, onChange: (String) -> Unit) {
    var v by remember(value) { mutableStateOf(value) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = Ink)
        OutlinedTextField(v, { v = it; if (Regex("^\\d{1,2}:\\d{2}$").matches(it)) onChange(it) },
            modifier = Modifier.width(110.dp), singleLine = true, placeholder = { Text("HH:MM") })
    }
}
@Composable private fun NumRow(label: String, value: Int, onChange: (Int) -> Unit) {
    var v by remember(value) { mutableStateOf(value.toString()) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = Ink)
        OutlinedTextField(v, { v = it; it.trim().toIntOrNull()?.let(onChange) },
            modifier = Modifier.width(90.dp), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
    }
}
@Composable private fun CsvRow(label: String, value: String, onChange: (String) -> Unit) {
    var v by remember(value) { mutableStateOf(value) }
    Column(Modifier.fillMaxWidth()) {
        Text(label, color = Ink, fontSize = 13.sp)
        OutlinedTextField(v, { v = it; onChange(it) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    }
}

@Composable
fun LocalContextX() = androidx.compose.ui.platform.LocalContext.current
