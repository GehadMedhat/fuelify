package com.authapi.scheduler

import com.authapi.models.NotificationType
import com.authapi.services.NotificationService
import org.slf4j.LoggerFactory
import java.time.LocalTime
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class NotificationScheduler(private val notificationService: NotificationService) {

    private val logger: org.slf4j.Logger = LoggerFactory.getLogger(NotificationScheduler::class.java)
    private val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(1)

    fun start() {
        // Run every 30 minutes — checks each user's settings and sends if applicable
        scheduler.scheduleAtFixedRate({
            runCatching { tick() }
                .onFailure { logger.error("Scheduler tick error", it) }
        }, 1, 30, TimeUnit.MINUTES)

        logger.info("NotificationScheduler started — running every 30 minutes")
    }

    fun stop() {
        scheduler.shutdown()
        logger.info("NotificationScheduler stopped")
    }

    private fun tick() {
        val now = LocalTime.now()
        logger.info("Scheduler tick at $now")

        broadcastIfApplicable(
            type         = NotificationType.HYDRATION,
            title        = "💧 Stay Hydrated!",
            body         = "Time to drink some water. Staying hydrated keeps you energized!",
            defaultEvery = 60
        )

        broadcastIfApplicable(
            type         = NotificationType.STEPS_GOAL,
            title        = "👟 Steps Reminder",
            body         = "Keep moving! Check your daily steps progress.",
            defaultEvery = 120
        )

        broadcastIfApplicable(
            type         = NotificationType.SLEEP_SCHEDULE,
            title        = "😴 Sleep Reminder",
            body         = "It's almost bedtime. Wind down and get a good night's rest!",
            defaultEvery = 1440 // once a day
        )

        broadcastIfApplicable(
            type         = NotificationType.WORKOUT_REMINDER,
            title        = "💪 Workout Time!",
            body         = "Don't skip your workout today. Your body will thank you!",
            defaultEvery = 60
        )
    }

    private fun broadcastIfApplicable(
        type: NotificationType,
        title: String,
        body: String,
        defaultEvery: Int
    ) {
        runCatching {
            notificationService.broadcastToAllUsers(type, title, body)
            logger.info("✅ Broadcast [$type]")
        }.onFailure {
            logger.error("❌ Failed to broadcast [$type]: ${it.message}")
        }
    }
}