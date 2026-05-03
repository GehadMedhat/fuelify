package com.authapi.services

import com.authapi.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class NotificationService {

    private val logger = LoggerFactory.getLogger(NotificationService::class.java)

    // ── Settings ──────────────────────────────────────────────────────────────

    fun getSettings(userId: Int): ApiResponse<NotificationSettingsResponse> {
        val row = transaction {
            NotificationSettings.selectAll()
                .where { NotificationSettings.userId eq userId }
                .singleOrNull()
        }
        val response = if (row == null) defaultSettings() else rowToSettingsResponse(row)
        return ApiResponse(true, "Settings retrieved", response)
    }

    fun updateSettings(userId: Int, req: UpdateSettingsRequest): ApiResponse<NotificationSettingsResponse> {
        validateSettingsRequest(req)
        val now = LocalDateTime.now()

        transaction {
            val updatedRows = NotificationSettings.update(
                where = { NotificationSettings.userId eq userId }
            ) {
                it[hydrationEnabled]  = req.hydrationEnabled
                it[stepsEnabled]      = req.stepsEnabled
                it[sleepEnabled]      = req.sleepEnabled
                it[workoutEnabled]    = req.workoutEnabled
                it[workoutTiming]     = req.workoutTiming
                it[workoutFreqStart]  = req.workoutFreqStart
                it[workoutFreqEnd]    = req.workoutFreqEnd
                it[dndEnabled]        = req.dndEnabled
                it[dndStartTime]      = req.dndStartTime
                it[dndEndTime]        = req.dndEndTime
                it[updatedAt]         = now
            }

            if (updatedRows == 0) {
                NotificationSettings.insert {
                    it[NotificationSettings.userId] = userId
                    it[hydrationEnabled]            = req.hydrationEnabled
                    it[stepsEnabled]                = req.stepsEnabled
                    it[sleepEnabled]                = req.sleepEnabled
                    it[workoutEnabled]              = req.workoutEnabled
                    it[workoutTiming]               = req.workoutTiming
                    it[workoutFreqStart]            = req.workoutFreqStart
                    it[workoutFreqEnd]              = req.workoutFreqEnd
                    it[dndEnabled]                  = req.dndEnabled
                    it[dndStartTime]                = req.dndStartTime
                    it[dndEndTime]                  = req.dndEndTime
                    it[updatedAt]                   = now
                }
            }
        }

        val saved = transaction {
            NotificationSettings.selectAll()
                .where { NotificationSettings.userId eq userId }
                .single()
                .let { rowToSettingsResponse(it) }
        }

        return ApiResponse(true, "Settings updated", saved)
    }

    // ── Notification Center ───────────────────────────────────────────────────

    fun getNotifications(
        userId: Int,
        limit: Int = 50,
        offset: Long = 0L
    ): ApiResponse<NotificationListResponse> {
        val (items, unread) = transaction {
            val rows = Notifications.selectAll()
                .where { Notifications.userId eq userId }
                .orderBy(Notifications.sentAt, SortOrder.DESC)
                .limit(limit, offset)
                .map { rowToNotificationItem(it) }

            val unreadCount = Notifications.selectAll()
                .where { (Notifications.userId eq userId) and (Notifications.isRead eq false) }
                .count().toInt()

            rows to unreadCount
        }

        return ApiResponse(
            true, "Notifications retrieved",
            NotificationListResponse(
                notifications = items,
                total         = items.size,
                unreadCount   = unread
            )
        )
    }

    fun markRead(notificationId: Int, userId: Int): ApiResponse<Nothing> {
        val updated = transaction {
            Notifications.update({
                (Notifications.id eq notificationId) and (Notifications.userId eq userId)
            }) {
                it[isRead] = true
            }
        }
        if (updated == 0) throw NotFoundException("Notification not found")
        return ApiResponse(true, "Notification marked as read")
    }

    fun markAllRead(userId: Int): ApiResponse<Nothing> {
        transaction {
            Notifications.update({ Notifications.userId eq userId }) {
                it[isRead] = true
            }
        }
        return ApiResponse(true, "All notifications marked as read")
    }

    // ── Welcome Notifications (sent after email verification / first login) ───

    fun sendWelcomeNotifications(userId: Int) {
        transaction {
            // Verify user still exists
            val userExists = Users.selectAll()
                .where { (Users.id eq userId) and (Users.isActive eq true) }
                .count() > 0
            if (!userExists) return@transaction

            val now = LocalDateTime.now()

            // 1. Welcome message
            Notifications.insert {
                it[Notifications.userId] = userId
                it[Notifications.type]   = "WELCOME"
                it[Notifications.title]  = "🎉 Welcome to Fuelify!"
                it[Notifications.body]   = "Your health journey starts now. Set your goals and let's get started!"
                it[isRead]               = false
                it[sentAt]               = now
            }

            // 2. Hydration tip
            Notifications.insert {
                it[Notifications.userId] = userId
                it[Notifications.type]   = "HYDRATION"
                it[Notifications.title]  = "💧 Stay Hydrated!"
                it[Notifications.body]   = "Remember to drink at least 8 glasses of water every day for optimal health."
                it[isRead]               = false
                it[sentAt]               = now.plusSeconds(1)
            }

            // 3. Steps motivation
            Notifications.insert {
                it[Notifications.userId] = userId
                it[Notifications.type]   = "STEPS_GOAL"
                it[Notifications.title]  = "🏃 Set Your Daily Steps Goal!"
                it[Notifications.body]   = "Walking 10,000 steps a day keeps the doctor away. Set your goal and start moving!"
                it[isRead]               = false
                it[sentAt]               = now.plusSeconds(2)
            }

            // 4. Explore the app
            Notifications.insert {
                it[Notifications.userId] = userId
                it[Notifications.type]   = "WELCOME"
                it[Notifications.title]  = "🌟 Explore Fuelify!"
                it[Notifications.body]   = "Check out the Marketplace to redeem rewards, or chat with Aura your AI health assistant!"
                it[isRead]               = false
                it[sentAt]               = now.plusSeconds(3)
            }
        }
        logger.info("Welcome notifications sent to user $userId")
    }

    // ── Daily Login Notification (sent once per day on login) ────────────────

    fun sendDailyLoginNotification(userId: Int) {
        transaction {
            val userExists = Users.selectAll()
                .where { (Users.id eq userId) and (Users.isActive eq true) }
                .count() > 0
            if (!userExists) return@transaction

            // Only send once per day
            val todayStart = LocalDate.now().atStartOfDay()
            val alreadySentToday = Notifications.selectAll()
                .where {
                    (Notifications.userId eq userId) and
                            (Notifications.type   eq "DAILY_LOGIN") and
                            (Notifications.sentAt greaterEq todayStart)
                }
                .count() > 0

            if (alreadySentToday) return@transaction

            val now = LocalDateTime.now()
            val hour = now.hour

            // Personalize greeting based on time of day
            val greeting = when {
                hour < 12 -> "Good morning"
                hour < 17 -> "Good afternoon"
                else      -> "Good evening"
            }

            Notifications.insert {
                it[Notifications.userId] = userId
                it[Notifications.type]   = "DAILY_LOGIN"
                it[Notifications.title]  = "👋 $greeting! Welcome Back!"
                it[Notifications.body]   = "Great to see you again. Ready to crush your health goals today? You've got this!"
                it[isRead]               = false
                it[sentAt]               = now
            }
        }
        logger.info("Daily login notification sent to user $userId")
    }

    // ── Scheduler: Deliver Notification ──────────────────────────────────────

    fun deliverNotification(
        userId: Int,
        type: NotificationType,
        title: String,
        body: String
    ) {
        transaction {
            val userExists = Users.selectAll()
                .where { (Users.id eq userId) and (Users.isActive eq true) }
                .count() > 0
            if (!userExists) {
                logger.debug("Skipping notification for user $userId — user no longer exists")
                return@transaction
            }

            val settings = NotificationSettings.selectAll()
                .where { NotificationSettings.userId eq userId }
                .singleOrNull()

            val enabled = when (type) {
                NotificationType.HYDRATION        -> settings?.get(NotificationSettings.hydrationEnabled) ?: true
                NotificationType.STEPS_GOAL       -> settings?.get(NotificationSettings.stepsEnabled) ?: true
                NotificationType.SLEEP_SCHEDULE   -> settings?.get(NotificationSettings.sleepEnabled) ?: true
                NotificationType.WORKOUT_REMINDER -> settings?.get(NotificationSettings.workoutEnabled) ?: true
            }
            if (!enabled) return@transaction

            if (settings != null && settings[NotificationSettings.dndEnabled]) {
                val dndStart = LocalTime.parse(settings[NotificationSettings.dndStartTime])
                val dndEnd   = LocalTime.parse(settings[NotificationSettings.dndEndTime])
                if (isInDndWindow(dndStart, dndEnd)) {
                    logger.debug("Suppressed notification for user $userId — DND active")
                    return@transaction
                }
            }

            Notifications.insert {
                it[Notifications.userId] = userId
                it[Notifications.type]   = type.name
                it[Notifications.title]  = title
                it[Notifications.body]   = body
                it[isRead]               = false
                it[sentAt]               = LocalDateTime.now()
            }

            logger.debug("Delivered [$type] notification to user $userId")
        }
    }

    fun broadcastToAllUsers(type: NotificationType, title: String, body: String) {
        val userIds = transaction {
            Users.selectAll()
                .where { Users.isActive eq true }
                .map { it[Users.id].value }
        }
        userIds.forEach { userId ->
            runCatching { deliverNotification(userId, type, title, body) }
                .onFailure { e ->
                    logger.error("Failed to deliver [$type] to user $userId: ${e.message}")
                }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun isInDndWindow(start: LocalTime, end: LocalTime): Boolean {
        val now = LocalTime.now()
        return if (start.isBefore(end)) {
            now.isAfter(start) && now.isBefore(end)
        } else {
            now.isAfter(start) || now.isBefore(end)
        }
    }

    private fun validateSettingsRequest(req: UpdateSettingsRequest) {
        runCatching { WorkoutTiming.valueOf(req.workoutTiming) }
            .getOrElse { throw ValidationException(listOf("Invalid workoutTiming: ${req.workoutTiming}. Valid values: ${WorkoutTiming.values().map { it.name }}")) }
        runCatching { LocalTime.parse(req.workoutFreqStart) }
            .getOrElse { throw ValidationException(listOf("Invalid workoutFreqStart. Use HH:mm format")) }
        runCatching { LocalTime.parse(req.workoutFreqEnd) }
            .getOrElse { throw ValidationException(listOf("Invalid workoutFreqEnd. Use HH:mm format")) }
        runCatching { LocalTime.parse(req.dndStartTime) }
            .getOrElse { throw ValidationException(listOf("Invalid dndStartTime. Use HH:mm format")) }
        runCatching { LocalTime.parse(req.dndEndTime) }
            .getOrElse { throw ValidationException(listOf("Invalid dndEndTime. Use HH:mm format")) }
    }

    private fun defaultSettings() = NotificationSettingsResponse(
        hydrationEnabled = true,
        stepsEnabled     = true,
        sleepEnabled     = true,
        workoutEnabled   = true,
        workoutTiming    = WorkoutTiming.EVERY_HOUR.name,
        workoutFreqStart = "08:00",
        workoutFreqEnd   = "20:00",
        dndEnabled       = false,
        dndStartTime     = "22:00",
        dndEndTime       = "07:00"
    )

    private fun rowToSettingsResponse(row: ResultRow) = NotificationSettingsResponse(
        hydrationEnabled = row[NotificationSettings.hydrationEnabled],
        stepsEnabled     = row[NotificationSettings.stepsEnabled],
        sleepEnabled     = row[NotificationSettings.sleepEnabled],
        workoutEnabled   = row[NotificationSettings.workoutEnabled],
        workoutTiming    = row[NotificationSettings.workoutTiming],
        workoutFreqStart = row[NotificationSettings.workoutFreqStart],
        workoutFreqEnd   = row[NotificationSettings.workoutFreqEnd],
        dndEnabled       = row[NotificationSettings.dndEnabled],
        dndStartTime     = row[NotificationSettings.dndStartTime],
        dndEndTime       = row[NotificationSettings.dndEndTime]
    )

    private fun rowToNotificationItem(row: ResultRow) = NotificationItemResponse(
        id     = row[Notifications.id].value,
        type   = row[Notifications.type],
        title  = row[Notifications.title],
        body   = row[Notifications.body],
        isRead = row[Notifications.isRead],
        sentAt = row[Notifications.sentAt].toString()
    )
}