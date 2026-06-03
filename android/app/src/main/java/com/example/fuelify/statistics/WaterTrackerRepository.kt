package com.example.fuelify.statistics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.fuelify.data.api.RetrofitClient


object WaterTrackerRepository {

private val api = RetrofitClient.healthApi


    // ── Goal ──────────────────────────────────────────────────────────────────

    suspend fun getDailyGoal(): Int = withContext(Dispatchers.IO) {
        try { api.getGoal().data?.goalMl ?: 2500 } catch (e: Exception) { 2500 }
    }

    suspend fun setDailyGoal(ml: Int) = withContext(Dispatchers.IO) {
        try { api.setGoal(GoalRequest(ml)) } catch (e: Exception) { }
    }

    // ── Home ──────────────────────────────────────────────────────────────────

    suspend fun getHomeData(): HomeData? = withContext(Dispatchers.IO) {
        try { api.getHome().data } catch (e: Exception) { null }
    }

    // ── Logs ──────────────────────────────────────────────────────────────────

    suspend fun getTodayLogs(): List<WaterLog> = withContext(Dispatchers.IO) {
        try { api.getLogs().data ?: emptyList() } catch (e: Exception) { emptyList() }
    }

    suspend fun addLog(amountMl: Int): WaterLog? = withContext(Dispatchers.IO) {
        try { api.addLog(IntakeRequest(amountMl)).data } catch (e: Exception) { null }
    }

    suspend fun deleteLog(timestamp: Long) = withContext(Dispatchers.IO) {
        try { api.deleteLog(timestamp) } catch (e: Exception) { }
    }

    suspend fun getTodayTotal(): Int = withContext(Dispatchers.IO) {
        try { api.getHome().data?.todayTotalMl ?: 0 } catch (e: Exception) { 0 }
    }

    suspend fun getTodayProgress(): Float = withContext(Dispatchers.IO) {
        try { (api.getHome().data?.todayProgressPercent ?: 0) / 100f } catch (e: Exception) { 0f }
    }

    // ── Statistics ────────────────────────────────────────────────────────────

    suspend fun getDailyStats(): DailyStats? = withContext(Dispatchers.IO) {
        try { api.getDailyStats().data } catch (e: Exception) { null }
    }

    suspend fun getWeeklyStats(): WeeklyStats? = withContext(Dispatchers.IO) {
        try { api.getWeeklyStats().data } catch (e: Exception) { null }
    }

    suspend fun getMonthlyStats(): MonthlyStats? = withContext(Dispatchers.IO) {
        try { api.getMonthlyStats().data } catch (e: Exception) { null }
    }

    suspend fun getQuarterlyStats(): QuarterlyStats? = withContext(Dispatchers.IO) {
        try { api.getQuarterlyStats().data } catch (e: Exception) { null }
    }

    suspend fun getCustomQuarterlyStats(months: List<MonthPair>): QuarterlyStats? = withContext(Dispatchers.IO) {
        try { api.getCustomQuarterlyStats(QuarterlyRequest(months)).data } catch (e: Exception) { null }
    }

    // ── Reminders ─────────────────────────────────────────────────────────────

    suspend fun getReminders(): List<ReminderItem> = withContext(Dispatchers.IO) {
        try { api.getReminders().data ?: emptyList() } catch (e: Exception) { emptyList() }
    }

    suspend fun addReminder(item: ReminderItem) = withContext(Dispatchers.IO) {
        try { api.addReminder(ReminderRequest(item.id, item.timeLabel, item.isEnabled, item.hour, item.minute)) }
        catch (e: Exception) { }
    }

    suspend fun editReminder(item: ReminderItem) = withContext(Dispatchers.IO) {
        try { api.editReminder(item.id, ReminderRequest(item.id, item.timeLabel, item.isEnabled, item.hour, item.minute)) }
        catch (e: Exception) { }
    }

    suspend fun toggleReminder(id: String, isEnabled: Boolean) = withContext(Dispatchers.IO) {
        try { api.toggleReminder(id, ToggleReminderRequest(isEnabled)) } catch (e: Exception) { }
    }

    suspend fun deleteReminder(id: String) = withContext(Dispatchers.IO) {
        try { api.deleteReminder(id) } catch (e: Exception) { }
    }

    suspend fun isAutoReminderEnabled(): Boolean = withContext(Dispatchers.IO) {
        try { api.getAutoReminder().data?.isEnabled ?: true } catch (e: Exception) { true }
    }

    suspend fun setAutoReminderEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        try { api.setAutoReminder(AutoReminderRequest(enabled)) } catch (e: Exception) { }
    }
}
