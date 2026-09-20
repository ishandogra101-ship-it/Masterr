package com.masterr.app

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

val Context.dataStore by preferencesDataStore(name = "masterr")

data class Settings(
    val assistantOn: Boolean = true,
    val intensity: String = "Medium",
    val morningOn: Boolean = true, val morningTime: String = "08:30",
    val middayOn: Boolean = true, val middayTime: String = "14:00",
    val eveningOn: Boolean = true, val eveningTime: String = "20:00",
    val inactivityOn: Boolean = true, val inactivityDays: Int = 3,
    val bugMe: Boolean = false, val bugEveryHours: Int = 4,
    val remindersOn: Boolean = true,
    // minutes-before the deadline, descending. 3d, 24h, 3h, 1h
    val reminderOffsets: List<Int> = listOf(4320, 1440, 180, 60),
    val overdueOn: Boolean = true, val overdueEveryHours: Int = 6,
    val quietOn: Boolean = true, val quietStart: String = "22:00", val quietEnd: String = "07:30"
)

object Prefs {
    private object K {
        val apiKey = stringPreferencesKey("apiKey")
        val appId = stringPreferencesKey("appId")
        val projectId = stringPreferencesKey("projectId")
        val senderId = stringPreferencesKey("senderId")
        val webClientId = stringPreferencesKey("webClientId")
        val geminiKey = stringPreferencesKey("geminiKey")
        val geminiModel = stringPreferencesKey("geminiModel")
        val dashboardUrl = stringPreferencesKey("dashboardUrl")

        val assistantOn = booleanPreferencesKey("assistantOn")
        val intensity = stringPreferencesKey("intensity")
        val morningOn = booleanPreferencesKey("morningOn"); val morningTime = stringPreferencesKey("morningTime")
        val middayOn = booleanPreferencesKey("middayOn"); val middayTime = stringPreferencesKey("middayTime")
        val eveningOn = booleanPreferencesKey("eveningOn"); val eveningTime = stringPreferencesKey("eveningTime")
        val inactivityOn = booleanPreferencesKey("inactivityOn"); val inactivityDays = intPreferencesKey("inactivityDays")
        val bugMe = booleanPreferencesKey("bugMe"); val bugEveryHours = intPreferencesKey("bugEveryHours")
        val remindersOn = booleanPreferencesKey("remindersOn"); val reminderOffsets = stringPreferencesKey("reminderOffsets")
        val overdueOn = booleanPreferencesKey("overdueOn"); val overdueEveryHours = intPreferencesKey("overdueEveryHours")
        val quietOn = booleanPreferencesKey("quietOn"); val quietStart = stringPreferencesKey("quietStart"); val quietEnd = stringPreferencesKey("quietEnd")

        val lastCheckin = longPreferencesKey("lastCheckin")
        val lastNothingNew = longPreferencesKey("lastNothingNew")
        val lastInteract = longPreferencesKey("lastInteract")
        val lastSeenDataAt = longPreferencesKey("lastSeenDataAt")
        val history = stringPreferencesKey("history")
    }

    fun configFlow(ctx: Context): Flow<Config> = ctx.dataStore.data.map { p ->
        Config(
            apiKey = p[K.apiKey] ?: "",
            appId = p[K.appId] ?: "",
            projectId = p[K.projectId] ?: "",
            senderId = p[K.senderId] ?: "",
            webClientId = p[K.webClientId] ?: "",
            geminiKey = p[K.geminiKey] ?: "",
            geminiModel = p[K.geminiModel] ?: "gemini-3.6-flash",
            dashboardUrl = p[K.dashboardUrl] ?: "https://ishandogra101-ship-it.github.io/Masterr/"
        )
    }

    fun readConfig(ctx: Context): Config = runBlocking { configFlow(ctx).first() }

    suspend fun saveConfig(ctx: Context, c: Config) {
        ctx.dataStore.edit { p ->
            p[K.apiKey] = c.apiKey; p[K.appId] = c.appId; p[K.projectId] = c.projectId
            p[K.senderId] = c.senderId; p[K.webClientId] = c.webClientId
            p[K.geminiKey] = c.geminiKey; p[K.geminiModel] = c.geminiModel; p[K.dashboardUrl] = c.dashboardUrl
        }
    }

    fun settingsFlow(ctx: Context): Flow<Settings> = ctx.dataStore.data.map { p -> toSettings(p) }

    fun readSettings(ctx: Context): Settings = runBlocking { settingsFlow(ctx).first() }

    private fun toSettings(p: Preferences): Settings {
        val d = Settings()
        return Settings(
            assistantOn = p[K.assistantOn] ?: d.assistantOn,
            intensity = p[K.intensity] ?: d.intensity,
            morningOn = p[K.morningOn] ?: d.morningOn, morningTime = p[K.morningTime] ?: d.morningTime,
            middayOn = p[K.middayOn] ?: d.middayOn, middayTime = p[K.middayTime] ?: d.middayTime,
            eveningOn = p[K.eveningOn] ?: d.eveningOn, eveningTime = p[K.eveningTime] ?: d.eveningTime,
            inactivityOn = p[K.inactivityOn] ?: d.inactivityOn, inactivityDays = p[K.inactivityDays] ?: d.inactivityDays,
            bugMe = p[K.bugMe] ?: d.bugMe, bugEveryHours = p[K.bugEveryHours] ?: d.bugEveryHours,
            remindersOn = p[K.remindersOn] ?: d.remindersOn,
            reminderOffsets = (p[K.reminderOffsets] ?: d.reminderOffsets.joinToString(","))
                .split(",").mapNotNull { it.trim().toIntOrNull() }.ifEmpty { d.reminderOffsets },
            overdueOn = p[K.overdueOn] ?: d.overdueOn, overdueEveryHours = p[K.overdueEveryHours] ?: d.overdueEveryHours,
            quietOn = p[K.quietOn] ?: d.quietOn, quietStart = p[K.quietStart] ?: d.quietStart, quietEnd = p[K.quietEnd] ?: d.quietEnd
        )
    }

    suspend fun saveSettings(ctx: Context, s: Settings) {
        ctx.dataStore.edit { p ->
            p[K.assistantOn] = s.assistantOn; p[K.intensity] = s.intensity
            p[K.morningOn] = s.morningOn; p[K.morningTime] = s.morningTime
            p[K.middayOn] = s.middayOn; p[K.middayTime] = s.middayTime
            p[K.eveningOn] = s.eveningOn; p[K.eveningTime] = s.eveningTime
            p[K.inactivityOn] = s.inactivityOn; p[K.inactivityDays] = s.inactivityDays
            p[K.bugMe] = s.bugMe; p[K.bugEveryHours] = s.bugEveryHours
            p[K.remindersOn] = s.remindersOn; p[K.reminderOffsets] = s.reminderOffsets.joinToString(",")
            p[K.overdueOn] = s.overdueOn; p[K.overdueEveryHours] = s.overdueEveryHours
            p[K.quietOn] = s.quietOn; p[K.quietStart] = s.quietStart; p[K.quietEnd] = s.quietEnd
        }
    }

    // --- interaction timestamps ---
    fun markInteract(ctx: Context) = runBlocking { ctx.dataStore.edit { it[K.lastInteract] = System.currentTimeMillis() } }
    fun markCheckin(ctx: Context) = runBlocking { ctx.dataStore.edit { it[K.lastCheckin] = System.currentTimeMillis() } }
    fun markNothingNew(ctx: Context) = runBlocking {
        val now = System.currentTimeMillis()
        ctx.dataStore.edit { it[K.lastNothingNew] = now; it[K.lastCheckin] = now; it[K.lastInteract] = now }
    }
    fun lastInteract(ctx: Context): Long = runBlocking { ctx.dataStore.data.first()[K.lastInteract] ?: 0L }
    fun lastCheckin(ctx: Context): Long = runBlocking { ctx.dataStore.data.first()[K.lastCheckin] ?: 0L }
    fun lastNothingNew(ctx: Context): Long = runBlocking { ctx.dataStore.data.first()[K.lastNothingNew] ?: 0L }
    fun lastSeenDataAt(ctx: Context): Long = runBlocking { ctx.dataStore.data.first()[K.lastSeenDataAt] ?: 0L }
    fun setLastSeenDataAt(ctx: Context, v: Long) = runBlocking { ctx.dataStore.edit { it[K.lastSeenDataAt] = v } }

    // --- notification history (dedupe / suppression) ---
    private fun historyObj(p: Preferences): JSONObject = try { JSONObject(p[K.history] ?: "{}") } catch (e: Exception) { JSONObject() }

    /** True if this exact notification key was already sent within [withinMs]. */
    fun wasSent(ctx: Context, key: String, withinMs: Long): Boolean = runBlocking {
        val h = historyObj(ctx.dataStore.data.first())
        val t = h.optLong(key, 0L)
        t > 0 && (System.currentTimeMillis() - t) < withinMs
    }

    fun markSent(ctx: Context, key: String) = runBlocking {
        ctx.dataStore.edit { p ->
            val h = historyObj(p)
            h.put(key, System.currentTimeMillis())
            // prune entries older than 30 days to keep it small
            val cutoff = System.currentTimeMillis() - 30L * 86_400_000L
            val it = h.keys(); val stale = ArrayList<String>()
            while (it.hasNext()) { val k = it.next(); if (h.optLong(k) < cutoff) stale.add(k) }
            stale.forEach { h.remove(it) }
            p[K.history] = h.toString()
        }
    }

    fun clearReminderHistory(ctx: Context, taskId: String) = runBlocking {
        ctx.dataStore.edit { p ->
            val h = historyObj(p)
            val it = h.keys(); val rm = ArrayList<String>()
            while (it.hasNext()) { val k = it.next(); if (k.contains(taskId)) rm.add(k) }
            rm.forEach { h.remove(it) }
            p[K.history] = h.toString()
        }
    }
}
