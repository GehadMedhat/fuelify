package com.example.fuelify.repository

import com.example.fuelify.db.WaterAutoReminderTable
import com.example.fuelify.db.WaterGoalTable
import com.example.fuelify.db.WaterLogsTable
import com.example.fuelify.db.WaterRemindersTable
import com.example.fuelify.db.DatabaseFactory.dbQuery
import com.example.fuelify.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.slf4j.LoggerFactory
import java.text.SimpleDateFormat
import java.util.*

object WaterTrackerRepository {

    private val log = LoggerFactory.getLogger("WaterTrackerRepo")

    private fun todayKey(): String =
        SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())

    private fun calKey(cal: Calendar): String =
        SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(cal.time)

    // ── Goal ──────────────────────────────────────────────────────────────────

    suspend fun getDailyGoal(): Int {
        log.debug("getDailyGoal() called")
        return try {
            val result = dbQuery {
                WaterGoalTable.selectAll().firstOrNull()
                    ?.get(WaterGoalTable.goalMl) ?: 2500
            }
            log.debug("getDailyGoal() success → $result ml")
            result
        } catch (e: Exception) {
            log.error("getDailyGoal() FAILED: ${e::class.simpleName}: ${e.message}", e)
            throw e
        }
    }

    suspend fun setDailyGoal(ml: Int) {
        log.debug("setDailyGoal() called with ml=$ml")
        try {
            dbQuery {
                WaterGoalTable.upsert(WaterGoalTable.id) {
                    it[id]     = 1
                    it[goalMl] = ml
                }
            }
            log.debug("setDailyGoal() success → $ml ml saved")
        } catch (e: Exception) {
            log.error("setDailyGoal() FAILED with ml=$ml: ${e::class.simpleName}: ${e.message}", e)
            throw e
        }
    }

    // ── Logs ──────────────────────────────────────────────────────────────────

    suspend fun getTodayLogs(): List<WaterLog> {
        val key = todayKey()
        log.debug("getTodayLogs() called, dateKey=$key")
        return try {
            val result = dbQuery {
                WaterLogsTable.select { WaterLogsTable.dateKey eq key }
                    .orderBy(WaterLogsTable.timestamp, SortOrder.DESC)
                    .map { row ->
                        WaterLog(
                            amountMl      = row[WaterLogsTable.amountMl],
                            timestamp     = row[WaterLogsTable.timestamp],
                            timeFormatted = row[WaterLogsTable.timeFormatted]
                        )
                    }
            }
            log.debug("getTodayLogs() success → ${result.size} entries, totaling ${result.sumOf { it.amountMl }} ml")
            result
        } catch (e: Exception) {
            log.error("getTodayLogs() FAILED for dateKey=$key: ${e::class.simpleName}: ${e.message}", e)
            throw e
        }
    }

    suspend fun addLog(amountMl: Int): WaterLog {
        log.debug("addLog() called with amountMl=$amountMl")
        val now           = System.currentTimeMillis()
        val timeFormatted = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(now))
        val dateKey       = todayKey()
        log.debug("addLog() → timestamp=$now, timeFormatted=$timeFormatted, dateKey=$dateKey")
        return try {
            dbQuery {
                log.debug("addLog() → inside dbQuery, about to insert into WaterLogsTable")
                WaterLogsTable.insert {
                    it[WaterLogsTable.timestamp]     = now
                    it[WaterLogsTable.amountMl]      = amountMl
                    it[WaterLogsTable.timeFormatted] = timeFormatted
                    it[WaterLogsTable.dateKey]       = dateKey
                }
                log.debug("addLog() → insert statement executed successfully")
            }
            val waterLog = WaterLog(amountMl, now, timeFormatted)
            log.debug("addLog() SUCCESS → $waterLog")
            waterLog
        } catch (e: Exception) {
            log.error("addLog() FAILED for amountMl=$amountMl, dateKey=$dateKey: ${e::class.simpleName}: ${e.message}", e)
            throw e
        }
    }

    suspend fun deleteLog(timestamp: Long): Boolean {
        log.debug("deleteLog() called with timestamp=$timestamp")
        return try {
            val result = dbQuery {
                WaterLogsTable.deleteWhere { WaterLogsTable.timestamp eq timestamp } > 0
            }
            log.debug("deleteLog() → timestamp=$timestamp, deleted=$result")
            result
        } catch (e: Exception) {
            log.error("deleteLog() FAILED for timestamp=$timestamp: ${e::class.simpleName}: ${e.message}", e)
            throw e
        }
    }

    suspend fun getTodayTotal(): Int {
        val key = todayKey()
        log.debug("getTodayTotal() called, dateKey=$key")
        return try {
            val result = dbQuery {
                WaterLogsTable.select { WaterLogsTable.dateKey eq key }
                    .toList()
                    .sumOf { it[WaterLogsTable.amountMl] }
            }
            log.debug("getTodayTotal() success → $result ml")
            result
        } catch (e: Exception) {
            log.error("getTodayTotal() FAILED for dateKey=$key: ${e::class.simpleName}: ${e.message}", e)
            throw e
        }
    }

    suspend fun getTodayProgressPercent(): Int {
        log.debug("getTodayProgressPercent() called")
        val goal  = getDailyGoal()
        val total = getTodayTotal()
        if (goal == 0) {
            log.warn("getTodayProgressPercent() → goal is 0, returning 0")
            return 0
        }
        val percent = ((total.toFloat() / goal) * 100).toInt().coerceAtMost(100)
        log.debug("getTodayProgressPercent() → total=$total ml, goal=$goal ml, percent=$percent%")
        return percent
    }

    // ── Hourly breakdown (today) ──────────────────────────────────────────────

    suspend fun getTodayHourlyData(): List<Pair<String, Int>> {
        log.debug("getTodayHourlyData() called")
        val logs = getTodayLogs()
        if (logs.isEmpty()) {
            log.debug("getTodayHourlyData() → no logs for today, returning empty list")
            return emptyList()
        }
        val cal     = Calendar.getInstance()
        val grouped = LinkedHashMap<Int, Int>()
        for (l in logs.sortedBy { it.timestamp }) {
            cal.timeInMillis = l.timestamp
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            grouped[hour] = (grouped[hour] ?: 0) + l.amountMl
        }
        val result = grouped.map { (hour, total) ->
            val label = when {
                hour == 0  -> "12am"
                hour < 12  -> "${hour}am"
                hour == 12 -> "12pm"
                else       -> "${hour - 12}pm"
            }
            Pair(label, total)
        }
        log.debug("getTodayHourlyData() → ${result.size} hours with data: $result")
        return result
    }

    // ── Day total helper ──────────────────────────────────────────────────────

    private suspend fun getDayTotal(cal: Calendar): Int {
        val key = calKey(cal)
        return try {
            val result = dbQuery {
                WaterLogsTable.select { WaterLogsTable.dateKey eq key }
                    .toList()
                    .sumOf { it[WaterLogsTable.amountMl] }
            }
            log.debug("getDayTotal() → dateKey=$key, total=$result ml")
            result
        } catch (e: Exception) {
            log.error("getDayTotal() FAILED for dateKey=$key: ${e::class.simpleName}: ${e.message}", e)
            throw e
        }
    }

    // ── Weekly ────────────────────────────────────────────────────────────────

    suspend fun getWeeklyData(): List<Pair<String, Int>> {
        log.debug("getWeeklyData() called")
        val labelFmt = SimpleDateFormat("EEE", Locale.getDefault())
        val result = (6 downTo 0).map { offset ->
            val c     = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -offset) }
            val label = labelFmt.format(c.time)
            Pair(label, getDayTotal(c))
        }
        log.debug("getWeeklyData() → $result")
        return result
    }

    suspend fun getWeeklyTotal(): Int {
        val total = getWeeklyData().sumOf { it.second }
        log.debug("getWeeklyTotal() → $total ml")
        return total
    }

    // ── Monthly ───────────────────────────────────────────────────────────────

    suspend fun getMonthlyData(): List<Pair<Int, Int>> {
        log.debug("getMonthlyData() called")
        val todayCal = Calendar.getInstance()
        val todayDay = todayCal.get(Calendar.DAY_OF_MONTH)
        val startCal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val result = mutableListOf<Pair<Int, Int>>()
        for (day in 1..todayDay) {
            result.add(Pair(day, getDayTotal(startCal)))
            startCal.add(Calendar.DAY_OF_MONTH, 1)
        }
        log.debug("getMonthlyData() → ${result.size} days, total=${result.sumOf { it.second }} ml")
        return result
    }

    suspend fun getMonthlyTotal(): Int {
        val total = getMonthlyData().sumOf { it.second }
        log.debug("getMonthlyTotal() → $total ml")
        return total
    }

    // ── Quarterly / custom stats ──────────────────────────────────────────────

    suspend fun getMonthStats(year: Int, month: Int): QuarterlyMonthData {
        log.debug("getMonthStats() called for year=$year, month=$month")
        val goal        = getDailyGoal()
        val today       = Calendar.getInstance()
        val monthLblFmt = SimpleDateFormat("MMM yyyy", Locale.getDefault())
        val monthCal    = Calendar.getInstance().apply {
            set(Calendar.YEAR, year); set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val monthLabel  = monthLblFmt.format(monthCal.time)
        val isThisMonth = (year == today.get(Calendar.YEAR) && month == today.get(Calendar.MONTH))
        val maxDay      = if (isThisMonth) today.get(Calendar.DAY_OF_MONTH)
                          else monthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        log.debug("getMonthStats() → label=$monthLabel, isThisMonth=$isThisMonth, maxDay=$maxDay, goal=$goal ml")
        var totalMl = 0; var daysTracked = 0; var daysMetGoal = 0
        val dayCal = monthCal.clone() as Calendar
        for (day in 1..maxDay) {
            val dayTotal = getDayTotal(dayCal)
            totalMl += dayTotal
            if (dayTotal > 0)     daysTracked++
            if (dayTotal >= goal) daysMetGoal++
            dayCal.add(Calendar.DAY_OF_MONTH, 1)
        }
        val data = QuarterlyMonthData(
            monthLabel  = monthLabel, year = year, month = month,
            totalMl     = totalMl,   daysTracked = daysTracked,
            avgPerDay   = if (maxDay > 0) totalMl / maxDay else 0,
            daysMetGoal = daysMetGoal, totalDays = maxDay
        )
        log.debug("getMonthStats() result → $data")
        return data
    }

    suspend fun getQuarterlyData(): List<QuarterlyMonthData> {
        log.debug("getQuarterlyData() called")
        val result = (2 downTo 0).map { offset ->
            val cal = Calendar.getInstance().apply { add(Calendar.MONTH, -offset) }
            getMonthStats(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
        }
        log.debug("getQuarterlyData() → ${result.size} months returned")
        return result
    }

    suspend fun getQuarterlyDataForMonths(months: List<Pair<Int, Int>>): List<QuarterlyMonthData> {
        log.debug("getQuarterlyDataForMonths() called for months=$months")
        val result = months.map { (y, m) -> getMonthStats(y, m) }
        log.debug("getQuarterlyDataForMonths() → ${result.size} entries returned")
        return result
    }

    // ── Goal completion / average helpers ─────────────────────────────────────

    suspend fun getTotalGoalCompletionPercent(days: Int = 7): Int {
        log.debug("getTotalGoalCompletionPercent() called with days=$days")
        if (days == 0) { log.warn("getTotalGoalCompletionPercent() → days=0, returning 0"); return 0 }
        val goal      = getDailyGoal()
        val totalGoal = goal * days
        if (totalGoal == 0) { log.warn("getTotalGoalCompletionPercent() → totalGoal=0, returning 0"); return 0 }
        var totalDrank = 0
        for (offset in 0 until days) {
            val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -offset) }
            totalDrank += getDayTotal(c)
        }
        val percent = (totalDrank * 100L / totalGoal).toInt().coerceAtMost(100)
        log.debug("getTotalGoalCompletionPercent() → days=$days, goal=$goal ml/day, totalDrank=$totalDrank ml, percent=$percent%")
        return percent
    }

    suspend fun getMonthlyGoalCompletionPercent(): Int {
        val daysElapsed = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        log.debug("getMonthlyGoalCompletionPercent() → daysElapsed=$daysElapsed")
        return getTotalGoalCompletionPercent(daysElapsed)
    }

    suspend fun getDailyAverageMl(days: Int = 7): Int {
        log.debug("getDailyAverageMl() called with days=$days")
        if (days == 0) { log.warn("getDailyAverageMl() → days=0, returning 0"); return 0 }
        var total = 0
        for (offset in 0 until days) {
            val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -offset) }
            total += getDayTotal(c)
        }
        val avg = total / days
        log.debug("getDailyAverageMl() → days=$days, total=$total ml, avg=$avg ml/day")
        return avg
    }

    suspend fun getDailyAverageLiters(days: Int = 7): Float {
        val liters = getDailyAverageMl(days) / 1000f
        log.debug("getDailyAverageLiters() → $liters L/day over $days days")
        return liters
    }

    // ── Reminders ─────────────────────────────────────────────────────────────

    suspend fun getReminders(): List<ReminderItem> {
        log.debug("getReminders() called")
        return try {
            val result = dbQuery {
                WaterRemindersTable.selectAll()
                    .orderBy(WaterRemindersTable.hour, SortOrder.ASC)
                    .map { row ->
                        ReminderItem(
                            id        = row[WaterRemindersTable.id],
                            timeLabel = row[WaterRemindersTable.timeLabel],
                            isEnabled = row[WaterRemindersTable.isEnabled],
                            hour      = row[WaterRemindersTable.hour],
                            minute    = row[WaterRemindersTable.minute]
                        )
                    }
            }
            log.debug("getReminders() success → ${result.size} reminders")
            result
        } catch (e: Exception) {
            log.error("getReminders() FAILED: ${e::class.simpleName}: ${e.message}", e)
            throw e
        }
    }

    suspend fun addReminder(item: ReminderItem): ReminderItem {
        log.debug("addReminder() called → id=${item.id}, timeLabel=${item.timeLabel}, hour=${item.hour}, minute=${item.minute}, isEnabled=${item.isEnabled}")
        return try {
            dbQuery {
                WaterRemindersTable.insert {
                    it[id]        = item.id
                    it[timeLabel] = item.timeLabel
                    it[isEnabled] = item.isEnabled
                    it[hour]      = item.hour
                    it[minute]    = item.minute
                }
            }
            log.debug("addReminder() success → $item")
            item
        } catch (e: Exception) {
            log.error("addReminder() FAILED for id=${item.id}: ${e::class.simpleName}: ${e.message}", e)
            throw e
        }
    }

    suspend fun updateReminder(id: String, updated: ReminderItem): Boolean {
        log.debug("updateReminder() called for id=$id")
        return try {
            val result = dbQuery {
                WaterRemindersTable.update({ WaterRemindersTable.id eq id }) {
                    it[timeLabel]                     = updated.timeLabel
                    it[WaterRemindersTable.isEnabled] = updated.isEnabled
                    it[hour]                          = updated.hour
                    it[minute]                        = updated.minute
                } > 0
            }
            log.debug("updateReminder() → id=$id, success=$result")
            result
        } catch (e: Exception) {
            log.error("updateReminder() FAILED for id=$id: ${e::class.simpleName}: ${e.message}", e)
            throw e
        }
    }

    suspend fun deleteReminder(id: String): Boolean {
        log.debug("deleteReminder() called for id=$id")
        return try {
            val result = dbQuery {
                WaterRemindersTable.deleteWhere { WaterRemindersTable.id eq id } > 0
            }
            log.debug("deleteReminder() → id=$id, deleted=$result")
            result
        } catch (e: Exception) {
            log.error("deleteReminder() FAILED for id=$id: ${e::class.simpleName}: ${e.message}", e)
            throw e
        }
    }

    suspend fun toggleReminder(id: String, enabled: Boolean): Boolean {
        log.debug("toggleReminder() called → id=$id, enabled=$enabled")
        return try {
            val result = dbQuery {
                WaterRemindersTable.update({ WaterRemindersTable.id eq id }) {
                    it[isEnabled] = enabled
                } > 0
            }
            log.debug("toggleReminder() → id=$id, enabled=$enabled, success=$result")
            result
        } catch (e: Exception) {
            log.error("toggleReminder() FAILED for id=$id: ${e::class.simpleName}: ${e.message}", e)
            throw e
        }
    }

    suspend fun isAutoReminderEnabled(): Boolean {
        log.debug("isAutoReminderEnabled() called")
        return try {
            val result = dbQuery {
                WaterAutoReminderTable.selectAll().firstOrNull()
                    ?.get(WaterAutoReminderTable.isEnabled) ?: true
            }
            log.debug("isAutoReminderEnabled() → $result")
            result
        } catch (e: Exception) {
            log.error("isAutoReminderEnabled() FAILED: ${e::class.simpleName}: ${e.message}", e)
            throw e
        }
    }

    suspend fun setAutoReminderEnabled(enabled: Boolean) {
        log.debug("setAutoReminderEnabled() called with enabled=$enabled")
        try {
            dbQuery {
                WaterAutoReminderTable.upsert(WaterAutoReminderTable.id) {
                    it[WaterAutoReminderTable.id]        = 1
                    it[WaterAutoReminderTable.isEnabled] = enabled
                }
            }
            log.debug("setAutoReminderEnabled() success → enabled=$enabled")
        } catch (e: Exception) {
            log.error("setAutoReminderEnabled() FAILED with enabled=$enabled: ${e::class.simpleName}: ${e.message}", e)
            throw e
        }
    }
}
