package com.example.fuelify.db

import org.jetbrains.exposed.sql.Table

object WaterLogsTable : Table("health_water_logs") {
    val id            = long("id").autoIncrement()
    val timestamp     = long("timestamp")
    val amountMl      = integer("amount_ml")
    val timeFormatted = text("time_formatted").default("")
    val dateKey       = char("date_key", 8)
    override val primaryKey = PrimaryKey(id)
}

object WaterGoalTable : Table("health_water_goal") {
    val id        = integer("id").default(1)
    val goalMl    = integer("goal_ml").default(2500)
    val updatedAt = long("updated_at").default(0L)
    override val primaryKey = PrimaryKey(id)
}

object WaterRemindersTable : Table("health_water_reminders") {
    val id        = text("id")
    val timeLabel = text("time_label")
    val isEnabled = bool("is_enabled").default(true)
    val hour      = integer("hour")
    val minute    = integer("minute")
    override val primaryKey = PrimaryKey(id)
}

object WaterAutoReminderTable : Table("health_water_auto_reminder") {
    val id        = integer("id").default(1)
    val isEnabled = bool("is_enabled").default(true)
    val updatedAt = long("updated_at").default(0L)
    override val primaryKey = PrimaryKey(id)
}

object SleepSchedulesTable : Table("health_sleep_schedules") {
    val dayOfWeek      = integer("day_of_week")
    val bedtimeHour    = integer("bedtime_hour").default(21)
    val bedtimeMinute  = integer("bedtime_minute").default(0)
    val hoursOfSleep   = integer("hours_of_sleep").default(8)
    val minutesOfSleep = integer("minutes_of_sleep").default(0)
    val repeatDays     = text("repeat_days_csv").default("1,2,3,4,5")
    val vibrateEnabled = bool("vibrate_enabled").default(true)
    val bedtimeEnabled = bool("bedtime_enabled").default(true)
    val alarmEnabled   = bool("alarm_enabled").default(true)
    val updatedAt      = long("updated_at").default(0L)
    override val primaryKey = PrimaryKey(dayOfWeek)
}

object MoodEntriesTable : Table("health_mood_entries") {
    val id        = text("id")
    val mood      = text("mood")
    val dateKey   = char("date_key", 10)
    val timestamp = long("timestamp")
    override val primaryKey = PrimaryKey(id)
}

object BpReadingsTable : Table("health_bp_readings") {
    val id            = long("id")
    val systolic      = integer("systolic")
    val diastolic     = integer("diastolic")
    val pulse         = integer("pulse")
    val notes         = text("notes").default("")
    val timestamp     = long("timestamp")
    val formattedTime = text("formatted_time").default("")
    val category      = text("category")
    val categoryLabel = text("category_label")
    override val primaryKey = PrimaryKey(id)
}

object BsReadingsTable : Table("health_bs_readings") {
    val id            = long("id")
    val glucose       = integer("glucose")
    val mealType      = text("meal_type")
    val notes         = text("notes").default("")
    val timestamp     = long("timestamp")
    val formattedTime = text("formatted_time").default("")
    val category      = text("category")
    val categoryLabel = text("category_label")
    override val primaryKey = PrimaryKey(id)
}

object BodyScanRecordsTable : Table("health_body_scan_records") {
    val id                = long("id").autoIncrement()
    val timestamp         = long("timestamp")
    val bodyFatPercent    = double("body_fat_percent")
    val muscleMassPercent = double("muscle_mass_percent")
    val waterPercent      = double("water_percent")
    val bmi               = double("bmi")
    val bodyType          = text("body_type").default("")
    val photoUri          = text("photo_uri").default("")
    override val primaryKey = PrimaryKey(id)
}
