package com.example.fuelify.statistics

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

private const val BASE_URL = "http://192.168.1.3:8080/"

// ── Water Data Classes ────────────────────────────────────────────────────────
data class WaterLog(
    val amountMl: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val timeFormatted: String = ""
)

data class ReminderItem(
    val id: String,
    val timeLabel: String,
    var isEnabled: Boolean,
    val hour: Int = 0,
    val minute: Int = 0
)

data class ApiResponse<T>(val success: Boolean, val data: T? = null, val message: String? = null)
data class GoalData(val goalMl: Int)
data class HomeData(
    val dailyGoalMl: Int, val todayTotalMl: Int, val todayProgressPercent: Int,
    val todayRemainingMl: Int, val weeklyTotalMl: Int, val monthlyTotalMl: Int,
    val goalCompletionPercent7Days: Int, val dailyAverageMl7Days: Int, val dailyAverageLiters7Days: Float
)
data class GoalRequest(val goalMl: Int)
data class IntakeRequest(val amountMl: Int)
data class AutoReminderData(val isEnabled: Boolean)
data class AutoReminderRequest(val isEnabled: Boolean)
data class ToggleReminderRequest(val isEnabled: Boolean)
data class ReminderRequest(val id: String, val timeLabel: String, val isEnabled: Boolean, val hour: Int, val minute: Int)

// ── Statistics Data Classes ───────────────────────────────────────────────────
data class HourlyEntry(val label: String, val amountMl: Int)
data class DayEntry(val label: String, val amountMl: Int)
data class MonthDayEntry(val day: Int, val amountMl: Int)

data class DailyStats(
    val totalMl: Int,
    val goalMl: Int,
    val progressPercent: Int,
    val hourlyBreakdown: List<HourlyEntry>
)

data class WeeklyStats(
    val totalMl: Int,
    val goalMl: Int,
    val goalCompletionPercent: Int,
    val dailyAverageMl: Int,
    val days: List<DayEntry>
)

data class MonthlyStats(
    val totalMl: Int,
    val goalMl: Int,
    val goalCompletionPercent: Int,
    val dailyAverageMl: Int,
    val days: List<MonthDayEntry>
)

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

data class QuarterlyStats(val months: List<QuarterlyMonthData>)
data class MonthPair(val year: Int, val month: Int)
data class QuarterlyRequest(val months: List<MonthPair>)

// ── Sleep Data Classes ────────────────────────────────────────────────────────
data class SleepScheduleData(
    val dayOfWeek        : Int,
    val bedtimeFormatted : String,
    val alarmFormatted   : String,
    val sleepDuration    : String,
    val sleepQualityPct  : Int,
    val bedtimeHour      : Int,
    val bedtimeMinute    : Int,
    val wakeHour         : Int,
    val wakeMinute       : Int,
    val bedtimeEnabled   : Boolean,
    val alarmEnabled     : Boolean,
    val countdownBedtime : String,
    val countdownAlarm   : String
)

data class UpdateSleepRequest(
    val bedtimeHour    : Int,
    val bedtimeMinute  : Int,
    val hoursOfSleep   : Int,
    val minutesOfSleep : Int,
    val repeatDays     : List<Int> = listOf(1, 2, 3, 4, 5),
    val vibrateEnabled : Boolean   = true,
    val bedtimeEnabled : Boolean   = true,
    val alarmEnabled   : Boolean   = true
)

data class SleepToggleRequest(val enabled: Boolean)

// ── Mood Data Classes ─────────────────────────────────────────────────────────
data class MoodEntry(
    val id        : String,
    val mood      : String,
    val dateKey   : String,
    val timestamp : Long
)

data class AddMoodRequest(val mood: String)

data class MoodHomeData(
    val dayStreak : Int,
    val totalLogs : Int,
    val todayMood : String?
)

data class MoodStatsData(
    val totalLogs       : Int,
    val mostCommonMood  : String?,
    val mostCommonEmoji : String?,
    val moodCounts      : Map<String, Int>,
    val calendarMonth   : String,
    val calendarLabel   : String,
    val calendarEntries : Map<String, String>
)

data class MoodBreakdownItem(
    val mood    : String,
    val label   : String,
    val emoji   : String,
    val count   : Int,
    val percent : Int
)

// ── Blood Pressure Data Classes ───────────────────────────────────────────────
data class BloodPressureReadingData(
    val id            : Long,
    val systolic      : Int,
    val diastolic     : Int,
    val pulse         : Int,
    val notes         : String,
    val timestamp     : Long,
    val formattedTime : String,
    val category      : String,
    val categoryLabel : String
)

data class BloodSugarReadingData(
    val id            : Long,
    val glucose       : Int,
    val mealType      : String,
    val notes         : String,
    val timestamp     : Long,
    val formattedTime : String,
    val category      : String,
    val categoryLabel : String
)

data class AddBpReadingRequest(
    val systolic  : Int,
    val diastolic : Int,
    val pulse     : Int,
    val notes     : String = ""
)

data class AddBsReadingRequest(
    val glucose  : Int,
    val mealType : String,
    val notes    : String = ""
)

data class BpTrendInfo(
    val direction : String,
    val delta     : String,
    val color     : String
)

data class BpStatsData(
    val monthlyAvgSystolic  : Int?,
    val monthlyAvgDiastolic : Int?,
    val systolicTrend       : BpTrendInfo,
    val diastolicTrend      : BpTrendInfo,
    val latestReading       : BloodPressureReadingData?,
    val weeklyAvgSystolic   : Int?,
    val weeklyAvgDiastolic  : Int?,
    val highestReading      : BloodPressureReadingData?,
    val lowestReading       : BloodPressureReadingData?
)

data class BsStatsData(
    val monthlyAvg     : Int?,
    val fastingTrend   : BpTrendInfo,
    val afterMealTrend : BpTrendInfo,
    val latestReading  : BloodSugarReadingData?,
    val weeklyAvg      : Int?,
    val highestReading : BloodSugarReadingData?,
    val lowestReading  : BloodSugarReadingData?
)

// ── Body Scan Data Classes ────────────────────────────────────────────────────
data class BodyScanRecordData(
    val timestamp         : Long,
    val bodyFatPercent    : Double,
    val muscleMassPercent : Double,
    val waterPercent      : Double,
    val bmi               : Double,
    val bodyType          : String,
    val photoUri          : String,
    val formattedTime     : String = ""
)

data class AddBodyScanRequest(
    val bodyFatPercent    : Double,
    val muscleMassPercent : Double,
    val waterPercent      : Double,
    val bmi               : Double,
    val bodyType          : String,
    val photoUri          : String = ""
)

data class MonthlyBodyFatEntryData(
    val monthLabel     : String,
    val bodyFatAvg     : Double,
    val changeFromPrev : Double
)

data class BodyScanStatsData(
    val latestRecord    : BodyScanRecordData?,
    val bodyFatChange   : Double,
    val monthlyHistory  : List<MonthlyBodyFatEntryData>
)

data class TodayBodyScanData(
    val records      : List<BodyScanRecordData>,
    val latestRecord : BodyScanRecordData?
)

// ── Retrofit Interface ────────────────────────────────────────────────────────
interface HealthApiService {

    // Home
    @GET("api/water/home")
    suspend fun getHome(): ApiResponse<HomeData>

    @GET("api/water/goal")
    suspend fun getGoal(): ApiResponse<GoalData>

    @PUT("api/water/goal")
    suspend fun setGoal(@Body body: GoalRequest): ApiResponse<Unit>

    // Intake
    @GET("api/water/intake/logs")
    suspend fun getLogs(): ApiResponse<List<WaterLog>>

    @POST("api/water/intake/add")
    suspend fun addLog(@Body body: IntakeRequest): ApiResponse<WaterLog>

    @DELETE("api/water/intake/{timestamp}")
    suspend fun deleteLog(@Path("timestamp") timestamp: Long): ApiResponse<Unit>

    // Statistics
    @GET("api/water/statistics/daily")
    suspend fun getDailyStats(): ApiResponse<DailyStats>

    @GET("api/water/statistics/weekly")
    suspend fun getWeeklyStats(): ApiResponse<WeeklyStats>

    @GET("api/water/statistics/monthly")
    suspend fun getMonthlyStats(): ApiResponse<MonthlyStats>

    @GET("api/water/statistics/quarterly")
    suspend fun getQuarterlyStats(): ApiResponse<QuarterlyStats>

    @POST("api/water/statistics/quarterly/custom")
    suspend fun getCustomQuarterlyStats(@Body body: QuarterlyRequest): ApiResponse<QuarterlyStats>

    // Reminders
    @GET("api/water/reminders")
    suspend fun getReminders(): ApiResponse<List<ReminderItem>>

    @POST("api/water/reminders")
    suspend fun addReminder(@Body body: ReminderRequest): ApiResponse<ReminderItem>

    @PUT("api/water/reminders/{id}")
    suspend fun editReminder(@Path("id") id: String, @Body body: ReminderRequest): ApiResponse<ReminderItem>

    @PATCH("api/water/reminders/{id}/toggle")
    suspend fun toggleReminder(@Path("id") id: String, @Body body: ToggleReminderRequest): ApiResponse<Unit>

    @DELETE("api/water/reminders/{id}")
    suspend fun deleteReminder(@Path("id") id: String): ApiResponse<Unit>

    @GET("api/water/reminders/auto")
    suspend fun getAutoReminder(): ApiResponse<AutoReminderData>

    @PUT("api/water/reminders/auto")
    suspend fun setAutoReminder(@Body body: AutoReminderRequest): ApiResponse<Unit>

    // Sleep
    @GET("api/sleep/today")
    suspend fun getSleepToday(): ApiResponse<SleepScheduleData>

    @GET("api/sleep/schedules")
    suspend fun getAllSleepSchedules(): ApiResponse<List<SleepScheduleData>>

    @GET("api/sleep/schedules/{day}")
    suspend fun getSleepSchedule(@Path("day") day: Int): ApiResponse<SleepScheduleData>

    @PUT("api/sleep/schedules/{day}")
    suspend fun updateSleepSchedule(@Path("day") day: Int, @Body body: UpdateSleepRequest): ApiResponse<SleepScheduleData>

    @PATCH("api/sleep/schedules/{day}/bedtime-toggle")
    suspend fun toggleBedtime(@Path("day") day: Int, @Body body: SleepToggleRequest): ApiResponse<Unit>

    @PATCH("api/sleep/schedules/{day}/alarm-toggle")
    suspend fun toggleAlarm(@Path("day") day: Int, @Body body: SleepToggleRequest): ApiResponse<Unit>

    // Mood
    @GET("api/mood/home")
    suspend fun getMoodHome(): ApiResponse<MoodHomeData>

    @GET("api/mood/entries")
    suspend fun getMoodEntries(): ApiResponse<List<MoodEntry>>

    @POST("api/mood/entries")
    suspend fun addMoodEntry(@Body body: AddMoodRequest): ApiResponse<MoodEntry>

    @GET("api/mood/entries/today")
    suspend fun getTodayMoodEntry(): ApiResponse<MoodEntry?>

    @DELETE("api/mood/entries/today")
    suspend fun deleteTodayMoodEntry(): ApiResponse<Unit>

    @GET("api/mood/stats")
    suspend fun getMoodStats(): ApiResponse<MoodStatsData>

    @GET("api/mood/stats/breakdown")
    suspend fun getMoodBreakdown(): ApiResponse<List<MoodBreakdownItem>>

    // Blood Pressure
    @GET("api/bp/readings")
    suspend fun getBpReadings(): ApiResponse<List<BloodPressureReadingData>>

    @GET("api/bp/readings/latest")
    suspend fun getLatestBpReading(): ApiResponse<BloodPressureReadingData?>

    @POST("api/bp/readings")
    suspend fun addBpReading(@Body body: AddBpReadingRequest): ApiResponse<BloodPressureReadingData>

    @DELETE("api/bp/readings/{id}")
    suspend fun deleteBpReading(@Path("id") id: Long): ApiResponse<Unit>

    @GET("api/bp/stats")
    suspend fun getBpStats(
        @Query("year") year: Int,
        @Query("month") month: Int
    ): ApiResponse<BpStatsData>

    // Blood Sugar
    @GET("api/bp/sugar/readings")
    suspend fun getBsReadings(): ApiResponse<List<BloodSugarReadingData>>

    @GET("api/bp/sugar/readings/latest")
    suspend fun getLatestBsReading(): ApiResponse<BloodSugarReadingData?>

    @POST("api/bp/sugar/readings")
    suspend fun addBsReading(@Body body: AddBsReadingRequest): ApiResponse<BloodSugarReadingData>

    @DELETE("api/bp/sugar/readings/{id}")
    suspend fun deleteBsReading(@Path("id") id: Long): ApiResponse<Unit>

    @GET("api/bp/sugar/stats")
    suspend fun getBsStats(
        @Query("year") year: Int,
        @Query("month") month: Int
    ): ApiResponse<BsStatsData>

    // ── Body Scan ─────────────────────────────────────────────────────────────

    @GET("api/bodyscan/records")
    suspend fun getBodyScanRecords(): ApiResponse<List<BodyScanRecordData>>

    @GET("api/bodyscan/records/latest")
    suspend fun getLatestBodyScanRecord(): ApiResponse<BodyScanRecordData?>

    @GET("api/bodyscan/records/today")
    suspend fun getTodayBodyScanRecords(): ApiResponse<TodayBodyScanData>

    @POST("api/bodyscan/records")
    suspend fun saveBodyScanRecord(@Body body: AddBodyScanRequest): ApiResponse<BodyScanRecordData>

    @DELETE("api/bodyscan/records/{timestamp}")
    suspend fun deleteBodyScanRecord(@Path("timestamp") timestamp: Long): ApiResponse<Unit>

    @GET("api/bodyscan/stats")
    suspend fun getBodyScanStats(): ApiResponse<BodyScanStatsData>
}

// ── Singleton ─────────────────────────────────────────────────────────────────
