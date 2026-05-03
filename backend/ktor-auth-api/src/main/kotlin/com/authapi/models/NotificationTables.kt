package com.authapi.models

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

// ─── Notification Settings ────────────────────────────────────────────────────
// One row per user — upserted when user changes their settings.

object NotificationSettings : Table("notification_settings") {
    val userId           = integer("user_id").references(Users.id).uniqueIndex()
    val hydrationEnabled = bool("hydration_enabled").default(true)
    val stepsEnabled     = bool("steps_enabled").default(true)
    val sleepEnabled     = bool("sleep_enabled").default(true)
    val workoutEnabled   = bool("workout_enabled").default(true)
    val workoutTiming    = varchar("workout_timing", 50).default("EVERY_HOUR")
    val workoutFreqStart = varchar("workout_freq_start", 10).default("08:00")
    val workoutFreqEnd   = varchar("workout_freq_end", 10).default("20:00")
    val dndEnabled       = bool("dnd_enabled").default(false)
    val dndStartTime     = varchar("dnd_start_time", 10).default("22:00")
    val dndEndTime       = varchar("dnd_end_time", 10).default("07:00")
    val updatedAt        = datetime("updated_at")

    override val primaryKey = PrimaryKey(userId)
}

// ─── Notification Center ──────────────────────────────────────────────────────
// Stores every notification delivered to a user (acts as the inbox).

object Notifications : IntIdTable("notifications") {
    val userId  = integer("user_id").references(Users.id)
    val type    = varchar("type", 50)       // NotificationType enum name
    val title   = varchar("title", 200)
    val body    = text("body")
    val isRead  = bool("is_read").default(false)
    val sentAt  = datetime("sent_at")
}
