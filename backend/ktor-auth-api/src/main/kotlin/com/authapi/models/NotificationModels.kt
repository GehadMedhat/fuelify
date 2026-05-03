package com.authapi.models

import kotlinx.serialization.Serializable

// ─── Enums ────────────────────────────────────────────────────────────────────

enum class NotificationType {
    HYDRATION,
    STEPS_GOAL,
    SLEEP_SCHEDULE,
    WORKOUT_REMINDER
}

enum class WorkoutTiming(val label: String) {
    EVERY_30_MIN("Every 30 Minutes"),
    EVERY_HOUR("Every Hour"),
    EVERY_2_HOURS("Every 2 Hours"),
    EVERY_3_HOURS("Every 3 Hours")
}

// ─── Request DTOs ─────────────────────────────────────────────────────────────

@Serializable
data class UpdateSettingsRequest(
    val hydrationEnabled: Boolean,
    val stepsEnabled: Boolean,
    val sleepEnabled: Boolean,
    val workoutEnabled: Boolean,
    val workoutTiming: String,        // WorkoutTiming enum name, e.g. "EVERY_HOUR"
    val workoutFreqStart: String,     // "HH:mm", e.g. "08:00"
    val workoutFreqEnd: String,       // "HH:mm", e.g. "20:00"
    val dndEnabled: Boolean,
    val dndStartTime: String,         // "HH:mm", e.g. "22:00"
    val dndEndTime: String            // "HH:mm", e.g. "07:00"
)

// ─── Response DTOs ────────────────────────────────────────────────────────────

@Serializable
data class NotificationSettingsResponse(
    val hydrationEnabled: Boolean,
    val stepsEnabled: Boolean,
    val sleepEnabled: Boolean,
    val workoutEnabled: Boolean,
    val workoutTiming: String,
    val workoutFreqStart: String,
    val workoutFreqEnd: String,
    val dndEnabled: Boolean,
    val dndStartTime: String,
    val dndEndTime: String
)

@Serializable
data class NotificationItemResponse(
    val id: Int,
    val type: String,
    val title: String,
    val body: String,
    val isRead: Boolean,
    val sentAt: String
)

@Serializable
data class NotificationListResponse(
    val notifications: List<NotificationItemResponse>,
    val total: Int,
    val unreadCount: Int
)
