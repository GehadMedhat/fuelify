package com.example.fuelify.repository

import com.example.fuelify.db.DatabaseFactory.dbQuery
import com.example.fuelify.db.SleepSchedulesTable
import com.example.fuelify.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.util.Calendar

object SleepRepository {

    // ── Read ──────────────────────────────────────────────────────────────────

    suspend fun getAllSchedules(): List<DaySchedule> = dbQuery {
        SleepSchedulesTable.selectAll()
            .orderBy(SleepSchedulesTable.dayOfWeek, SortOrder.ASC)
            .map { toModel(it) }
    }

    suspend fun getScheduleForDay(dayOfWeek: Int): DaySchedule = dbQuery {
        SleepSchedulesTable.select(SleepSchedulesTable.dayOfWeek eq dayOfWeek)
            .firstOrNull()
            ?.let { toModel(it) }
            ?: DaySchedule(dayOfWeek)
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    suspend fun updateScheduleForDay(schedule: DaySchedule) = dbQuery {
        SleepSchedulesTable.upsert(SleepSchedulesTable.dayOfWeek) {
            it[dayOfWeek]      = schedule.dayOfWeek
            it[bedtimeHour]    = schedule.bedtimeHour
            it[bedtimeMinute]  = schedule.bedtimeMinute
            it[hoursOfSleep]   = schedule.hoursOfSleep
            it[minutesOfSleep] = schedule.minutesOfSleep
            it[repeatDays]     = schedule.repeatDays.joinToString(",")
            it[vibrateEnabled] = schedule.vibrateEnabled
            it[bedtimeEnabled] = schedule.bedtimeEnabled
            it[alarmEnabled]   = schedule.alarmEnabled
        }
    }

    suspend fun toggleBedtime(dayOfWeek: Int, enabled: Boolean) = dbQuery {
        SleepSchedulesTable.update({ SleepSchedulesTable.dayOfWeek eq dayOfWeek }) {
            it[bedtimeEnabled] = enabled
        }
    }

    suspend fun toggleAlarm(dayOfWeek: Int, enabled: Boolean) = dbQuery {
        SleepSchedulesTable.update({ SleepSchedulesTable.dayOfWeek eq dayOfWeek }) {
            it[alarmEnabled] = enabled
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    fun getTodayDayOfWeek(): Int {
        val dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        return if (dow == Calendar.SUNDAY) 7 else dow - 1
    }

    suspend fun getTodaySchedule(): DaySchedule = getScheduleForDay(getTodayDayOfWeek())

    fun countdownTo(targetHour: Int, targetMinute: Int): String {
        val now    = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMinute)
            set(Calendar.SECOND, 0)
            if (before(now)) add(Calendar.DATE, 1)
        }
        val diffMins = ((target.timeInMillis - now.timeInMillis) / 60000).toInt()
        return "in ${diffMins / 60}h ${diffMins % 60}m"
    }

    private fun toModel(row: ResultRow): DaySchedule {
        val repeatDaysList = row[SleepSchedulesTable.repeatDays]
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }
        return DaySchedule(
            dayOfWeek      = row[SleepSchedulesTable.dayOfWeek],
            bedtimeHour    = row[SleepSchedulesTable.bedtimeHour],
            bedtimeMinute  = row[SleepSchedulesTable.bedtimeMinute],
            hoursOfSleep   = row[SleepSchedulesTable.hoursOfSleep],
            minutesOfSleep = row[SleepSchedulesTable.minutesOfSleep],
            repeatDays     = repeatDaysList,
            vibrateEnabled = row[SleepSchedulesTable.vibrateEnabled],
            bedtimeEnabled = row[SleepSchedulesTable.bedtimeEnabled],
            alarmEnabled   = row[SleepSchedulesTable.alarmEnabled]
        )
    }
}
