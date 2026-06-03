package com.example.fuelify.models

import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.*

// ── Generic API wrapper ───────────────────────────────────────────────────────

@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val message: String? = null,
    val data: T? = null
)

// ── Water Tracker ─────────────────────────────────────────────────────────────

@Serializable
data class WaterLog(
    val amountMl: Int,
    val timestamp: Long,
    val timeFormatted: String
)

@Serializable
data class AddWaterRequest(val amountMl: Int)

@Serializable
data class DailyGoalRequest(val goalMl: Int)

@Serializable
data class HomeSummary(
    val dailyGoalMl: Int,
    val todayTotalMl: Int,
    val todayProgressPercent: Int,
    val todayRemainingMl: Int,
    val weeklyTotalMl: Int,
    val monthlyTotalMl: Int,
    val goalCompletionPercent7Days: Int,
    val dailyAverageMl7Days: Int,
    val dailyAverageLiters7Days: Float
)

// ── Statistics ────────────────────────────────────────────────────────────────

@Serializable
data class HourlyEntry(val label: String, val totalMl: Int)

@Serializable
data class DayEntry(val label: String, val totalMl: Int)

@Serializable
data class MonthDayEntry(val day: Int, val totalMl: Int)

@Serializable
data class DailyStats(
    val totalMl: Int,
    val goalMl: Int,
    val progressPercent: Int,
    val hourlyBreakdown: List<HourlyEntry>
)

@Serializable
data class WeeklyStats(
    val totalMl: Int,
    val goalMl: Int,
    val goalCompletionPercent: Int,
    val dailyAverageMl: Int,
    val days: List<DayEntry>
)

@Serializable
data class MonthlyStats(
    val totalMl: Int,
    val goalMl: Int,
    val goalCompletionPercent: Int,
    val dailyAverageMl: Int,
    val days: List<MonthDayEntry>
)

@Serializable
data class QuarterlyMonthData(
    val monthLabel: String,
    val year: Int,
    val month: Int,
    val totalMl: Int,
    val daysTracked: Int,
    val avgPerDay: Int,
    val daysMetGoal: Int,
    val totalDays: Int
)

@Serializable
data class QuarterlyStats(val months: List<QuarterlyMonthData>)

@Serializable
data class MonthSelection(val year: Int, val month: Int)

@Serializable
data class QuarterlyRequest(val months: List<MonthSelection>)

// ── Reminders ─────────────────────────────────────────────────────────────────

@Serializable
data class ReminderItem(
    val id: String = "",
    val timeLabel: String = "",
    val isEnabled: Boolean = true,
    val hour: Int = 0,
    val minute: Int = 0
)

@Serializable
data class ReminderToggleRequest(val isEnabled: Boolean)

// ── Sleep ─────────────────────────────────────────────────────────────────────

@Serializable
data class DaySchedule(
    val dayOfWeek: Int,
    val bedtimeHour: Int = 21,
    val bedtimeMinute: Int = 0,
    val hoursOfSleep: Int = 8,
    val minutesOfSleep: Int = 0,
    val repeatDays: List<Int> = listOf(1, 2, 3, 4, 5),
    val vibrateEnabled: Boolean = true,
    val bedtimeEnabled: Boolean = true,
    val alarmEnabled: Boolean = true
) {
    val wakeHour: Int get() {
        val totalMins = bedtimeHour * 60 + bedtimeMinute + hoursOfSleep * 60 + minutesOfSleep
        return (totalMins / 60) % 24
    }
    val wakeMinute: Int get() {
        val totalMins = bedtimeHour * 60 + bedtimeMinute + hoursOfSleep * 60 + minutesOfSleep
        return totalMins % 60
    }

    fun bedtimeFormatted(): String = formatTime(bedtimeHour, bedtimeMinute)
    fun alarmFormatted(): String   = formatTime(wakeHour, wakeMinute)
    fun sleepDurationLabel(): String = "${hoursOfSleep}h ${minutesOfSleep}m"
    fun sleepQualityPercent(): Int {
        val totalMins = hoursOfSleep * 60 + minutesOfSleep
        return (totalMins * 100 / (8 * 60)).coerceAtMost(100)
    }

    private fun formatTime(h: Int, m: Int): String {
        val amPm = if (h < 12) "AM" else "PM"
        val h12  = when { h == 0 -> 12; h > 12 -> h - 12; else -> h }
        return String.format("%02d:%02d %s", h12, m, amPm)
    }
}

@Serializable
data class UpdateScheduleRequest(
    val bedtimeHour: Int,
    val bedtimeMinute: Int,
    val hoursOfSleep: Int,
    val minutesOfSleep: Int,
    val repeatDays: List<Int>,
    val vibrateEnabled: Boolean,
    val bedtimeEnabled: Boolean,
    val alarmEnabled: Boolean
)

@Serializable
data class ToggleRequest(val enabled: Boolean)

@Serializable
data class TodayScheduleResponse(
    val dayOfWeek: Int,
    val bedtimeFormatted: String,
    val alarmFormatted: String,
    val sleepDuration: String,
    val sleepQualityPct: Int,
    val bedtimeHour: Int,
    val bedtimeMinute: Int,
    val wakeHour: Int,
    val wakeMinute: Int,
    val bedtimeEnabled: Boolean,
    val alarmEnabled: Boolean,
    val countdownBedtime: String,
    val countdownAlarm: String
)

// ── Mood ──────────────────────────────────────────────────────────────────────

enum class MoodType(val label: String, val emoji: String) {
    AMAZING("Amazing", "😄"),
    GOOD("Good", "🙂"),
    OKAY("Okay", "😐"),
    BAD("Bad", "😞")
}

@Serializable
data class MoodEntry(
    val id: String,
    val mood: String,
    val dateKey: String,
    val timestamp: Long
)

@Serializable
data class AddMoodRequest(val mood: String)

@Serializable
data class MoodHomeData(
    val dayStreak: Int,
    val totalLogs: Int,
    val todayMood: String?
)

@Serializable
data class MoodStatsData(
    val totalLogs: Int,
    val mostCommonMood: String?,
    val mostCommonEmoji: String?,
    val moodCounts: Map<String, Int>,
    val calendarMonth: String,
    val calendarLabel: String,
    val calendarEntries: Map<String, String>
)

@Serializable
data class MoodBreakdownItem(
    val mood: String,
    val label: String,
    val emoji: String,
    val count: Int,
    val percent: Int
)

// ── Blood Pressure ────────────────────────────────────────────────────────────

@Serializable
data class BloodPressureReading(
    val id: Long,
    val systolic: Int,
    val diastolic: Int,
    val pulse: Int,
    val notes: String,
    val timestamp: Long,
    val formattedTime: String,
    val category: String,
    val categoryLabel: String
)

@Serializable
data class AddBpReadingRequest(
    val systolic: Int,
    val diastolic: Int,
    val pulse: Int,
    val notes: String = ""
)

enum class BpCategory(val label: String) {
    NORMAL("Normal"),
    ELEVATED("Elevated"),
    HIGH_STAGE_1("High Stage 1"),
    HIGH_STAGE_2("High Stage 2"),
    HYPERTENSIVE_CRISIS("Hypertensive Crisis"),
    LOW("Low")
}

fun computeBpCategory(systolic: Int, diastolic: Int): String = when {
    systolic < 90 || diastolic < 60  -> BpCategory.LOW.name
    systolic < 120 && diastolic < 80  -> BpCategory.NORMAL.name
    systolic < 130 && diastolic < 80  -> BpCategory.ELEVATED.name
    systolic < 140 || diastolic < 90  -> BpCategory.HIGH_STAGE_1.name
    systolic < 180 || diastolic < 120 -> BpCategory.HIGH_STAGE_2.name
    else                               -> BpCategory.HYPERTENSIVE_CRISIS.name
}

@Serializable
data class BpStatsData(
    val monthlyAvgSystolic: Int?,
    val monthlyAvgDiastolic: Int?,
    val systolicTrend: BpTrendInfo,
    val diastolicTrend: BpTrendInfo,
    val latestReading: BloodPressureReading?,
    val weeklyAvgSystolic: Int?,
    val weeklyAvgDiastolic: Int?,
    val highestReading: BloodPressureReading?,
    val lowestReading: BloodPressureReading?
)

enum class TrendDirection { UP, DOWN, FLAT }

@Serializable
data class BpTrendInfo(
    val direction: String,
    val delta: String,
    val color: String
)

// ── Blood Sugar ───────────────────────────────────────────────────────────────

@Serializable
data class BloodSugarReading(
    val id: Long,
    val glucose: Int,
    val mealType: String,
    val notes: String,
    val timestamp: Long,
    val formattedTime: String,
    val category: String,
    val categoryLabel: String
)

@Serializable
data class AddBsReadingRequest(
    val glucose: Int,
    val mealType: String,
    val notes: String = ""
)

enum class BsCategory(val label: String) {
    NORMAL("Normal"),
    PREDIABETES("Prediabetes"),
    DIABETES("Diabetes"),
    HYPOGLYCEMIA("Hypoglycemia")
}

fun computeBsCategory(glucose: Int, mealType: String): String = when (mealType) {
    "Fasting" -> when {
        glucose < 70  -> BsCategory.HYPOGLYCEMIA.name
        glucose < 100 -> BsCategory.NORMAL.name
        glucose < 126 -> BsCategory.PREDIABETES.name
        else          -> BsCategory.DIABETES.name
    }
    else -> when {
        glucose < 70  -> BsCategory.HYPOGLYCEMIA.name
        glucose < 140 -> BsCategory.NORMAL.name
        glucose < 200 -> BsCategory.PREDIABETES.name
        else          -> BsCategory.DIABETES.name
    }
}

@Serializable
data class BsStatsData(
    val monthlyAvg: Int?,
    val fastingTrend: BpTrendInfo,
    val afterMealTrend: BpTrendInfo,
    val latestReading: BloodSugarReading?,
    val weeklyAvg: Int?,
    val highestReading: BloodSugarReading?,
    val lowestReading: BloodSugarReading?
)

// ── Body Scan ─────────────────────────────────────────────────────────────────

@Serializable
data class BodyScanRecord(
    val timestamp: Long,
    val bodyFatPercent: Double,
    val muscleMassPercent: Double,
    val waterPercent: Double,
    val bmi: Double,
    val bodyType: String,
    val photoUri: String
)

@Serializable
data class AddBodyScanRequest(
    val bodyFatPercent: Double,
    val muscleMassPercent: Double,
    val waterPercent: Double,
    val bmi: Double,
    val bodyType: String,
    val photoUri: String = ""
)

@Serializable
data class TodayBodyScanData(
    val records: List<BodyScanRecord>,
    val latestRecord: BodyScanRecord?
)

@Serializable
data class MonthlyBodyFatEntry(
    val monthLabel: String,
    val bodyFatAvg: Double,
    val changeFromPrev: Double
)

@Serializable
data class BodyScanStatsData(
    val latestRecord: BodyScanRecord?,
    val bodyFatChange: Double,
    val monthlyHistory: List<MonthlyBodyFatEntry>
)

// ── Shared helpers ────────────────────────────────────────────────────────────

fun formatTimestamp(ts: Long): String =
    SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(ts))
