package com.example.fuelify.statistics

import kotlinx.coroutines.Dispatchers
import com.example.fuelify.data.api.RetrofitClient
import kotlinx.coroutines.withContext

enum class MoodType(val label: String, val emoji: String, val colorKey: String) {
    AMAZING("Amazing", "😍", "amazing"),

    //    GOOD("Good",       "😊", "amazing"),   // ← add this (maps visually to AMAZING)
    OKAY("Okay",       "😐", "okay"),
    SAD("Sad",         "😢", "sad"),
    ANGRY("Angry",     "😠", "angry")
}

object MoodRepository {

    private val api = RetrofitClient.healthApi


    // ── Write ─────────────────────────────────────────────────────────────────

suspend fun addEntry(mood: MoodType): MoodEntry? = withContext(Dispatchers.IO) {
    try {
        api.addMoodEntry(AddMoodRequest(mood.name)).data
    } catch (e: Exception) {
        android.util.Log.e("MoodRepository", "addEntry failed", e)
        null
    }
}

    // ── Read ──────────────────────────────────────────────────────────────────

    suspend fun getAllEntries(): List<MoodEntry> = withContext(Dispatchers.IO) {
        try { api.getMoodEntries().data ?: emptyList() } catch (e: Exception) { emptyList() }
    }

    suspend fun getTotalLogs(): Int = withContext(Dispatchers.IO) {
        try { api.getMoodHome().data?.totalLogs ?: 0 } catch (e: Exception) { 0 }
    }

    suspend fun getDayStreak(): Int = withContext(Dispatchers.IO) {
        try { api.getMoodHome().data?.dayStreak ?: 0 } catch (e: Exception) { 0 }
    }

    suspend fun getTodayMood(): MoodType? = withContext(Dispatchers.IO) {
        try {
            val name = api.getMoodHome().data?.todayMood ?: return@withContext null
            MoodType.valueOf(name)
        } catch (e: Exception) { null }
    }

    suspend fun getMostCommonMood(): MoodType? = withContext(Dispatchers.IO) {
        try {
            api.getMoodStats().data?.mostCommonMood
                ?.let { runCatching { MoodType.valueOf(it) }.getOrNull() }
        } catch (e: Exception) { null }
    }

    suspend fun getEntriesForMonth(monthKey: String): Map<String, MoodType> = withContext(Dispatchers.IO) {
        try {
            api.getMoodStats().data?.calendarEntries
                ?.filter { it.key.startsWith(monthKey) }
                ?.mapValues { runCatching { MoodType.valueOf(it.value) }.getOrElse { MoodType.OKAY } }
                ?: emptyMap()
        } catch (e: Exception) { emptyMap() }
    }

    suspend fun getMoodCounts(): Map<MoodType, Int> = withContext(Dispatchers.IO) {
        try {
            api.getMoodStats().data?.moodCounts?.entries
                ?.associate { (k, v) -> runCatching { MoodType.valueOf(k) }.getOrElse { MoodType.OKAY } to v }
                ?: emptyMap()
        } catch (e: Exception) { emptyMap() }
    }

    suspend fun getStatsData(): MoodStatsData? = withContext(Dispatchers.IO) {
        try { api.getMoodStats().data } catch (e: Exception) { null }
    }
}
