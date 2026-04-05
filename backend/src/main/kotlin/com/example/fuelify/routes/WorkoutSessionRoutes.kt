package com.example.fuelify.routes

import com.example.fuelify.db.DatabaseFactory.dbQuery
import com.example.fuelify.models.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDate
import java.time.LocalDateTime

// ─── Tables ───────────────────────────────────────────────────────────────────

private object WorkoutSession : Table("workout_session") {
    val sessionId       = integer("session_id").autoIncrement()
    val userId          = integer("user_id")
    val workoutId       = integer("workout_id").nullable()
    val workoutName     = varchar("workout_name", 255).nullable()
    val sessionDate     = date("session_date")
    val startedAt       = datetime("started_at").nullable()
    val finishedAt      = datetime("finished_at").nullable()
    val durationSeconds = integer("duration_seconds").default(0)
    val caloriesBurned  = integer("calories_burned").default(0)
    val exercisesDone   = integer("exercises_done").default(0)
    val isCustom        = bool("is_custom").default(false)
    val notes           = text("notes").nullable()
    override val primaryKey = PrimaryKey(sessionId)
}

// ─── DTOs ─────────────────────────────────────────────────────────────────────

@Serializable
data class SaveWorkoutSessionRequest(
    val workoutId: Int?,
    val workoutName: String,
    val durationSeconds: Int,
    val caloriesBurned: Int,
    val exercisesDone: Int,
    val isCustom: Boolean = false,
    val notes: String = ""
)

@Serializable
data class WorkoutSessionDto(
    val sessionId: Int,
    val workoutId: Int?,
    val workoutName: String,
    val startedAt: String,
    val finishedAt: String,
    val durationSeconds: Int,
    val caloriesBurned: Int,
    val exercisesDone: Int,
    val isCustom: Boolean
)

@Serializable
data class SaveWorkoutPlanRequest(
    val workoutId: Int,
    val workoutName: String,
    val scheduledDate: String   // "2026-03-25"
)

// ─── Routes ───────────────────────────────────────────────────────────────────

fun Route.workoutSessionRoutes() {

    // ── POST /api/users/{id}/workout-session ─────────────────────────────────
    post("/users/{id}/workout-session") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user id", null))
        val body = call.receive<SaveWorkoutSessionRequest>()

        val now       = LocalDateTime.now()
        val startedAt = now.minusSeconds(body.durationSeconds.toLong())

        val sessionId = dbQuery {
            WorkoutSession.insert {
                it[WorkoutSession.userId]          = userId
                it[WorkoutSession.workoutId]       = body.workoutId
                it[WorkoutSession.workoutName]     = body.workoutName
                it[WorkoutSession.sessionDate]     = LocalDate.now()
                it[WorkoutSession.startedAt]       = startedAt
                it[WorkoutSession.finishedAt]      = now
                it[WorkoutSession.durationSeconds] = body.durationSeconds
                it[WorkoutSession.caloriesBurned]  = body.caloriesBurned
                it[WorkoutSession.exercisesDone]   = body.exercisesDone
                it[WorkoutSession.isCustom]        = body.isCustom
                it[WorkoutSession.notes]           = body.notes.ifBlank { null }
            }[WorkoutSession.sessionId]
        }

        // Also update workout_plan if it exists for today
        dbQuery {
            if (body.workoutId != null) {
                WorkoutPlan.update({
                    (WorkoutPlan.userId eq userId) and
                    (WorkoutPlan.workoutId eq body.workoutId) and
                    (WorkoutPlan.scheduledDate eq LocalDate.now()) and
                    (WorkoutPlan.status eq "planned")
                }) {
                    it[WorkoutPlan.status] = "completed"
                }
            }
        }

        call.respond(ApiResponse(success = true, message = "Workout session saved!", data = sessionId))
    }

    // ── GET /api/users/{id}/workout-sessions ─────────────────────────────────
    get("/users/{id}/workout-sessions") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user id", null))

        val sessions = dbQuery {
            WorkoutSession
                .select { WorkoutSession.userId eq userId }
                .orderBy(WorkoutSession.finishedAt, SortOrder.DESC)
                .limit(20)
                .map { row ->
                    WorkoutSessionDto(
                        sessionId       = row[WorkoutSession.sessionId],
                        workoutId       = row.getOrNull(WorkoutSession.workoutId),
                        workoutName     = row.getOrNull(WorkoutSession.workoutName) ?: "Custom Workout",
                        startedAt       = row.getOrNull(WorkoutSession.startedAt)?.toString() ?: "",
                        finishedAt      = row.getOrNull(WorkoutSession.finishedAt)?.toString() ?: "",
                        durationSeconds = row[WorkoutSession.durationSeconds],
                        caloriesBurned  = row[WorkoutSession.caloriesBurned],
                        exercisesDone   = row[WorkoutSession.exercisesDone],
                        isCustom        = row[WorkoutSession.isCustom]
                    )
                }
        }
        call.respond(ApiResponse(success = true, message = "OK", data = sessions))
    }

    // ── GET /api/users/{id}/workout-progress ─────────────────────────────────
    // Returns today's session + this week's summary for home screen
    get("/users/{id}/workout-progress") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user id", null))

        val today     = LocalDate.now()
        val weekStart = today.minusDays(today.dayOfWeek.value.toLong() - 1)

        // Fetch user's exerciseDays to determine suggested workout count
        val exerciseDays = dbQuery {
            WorkoutUsers.select { WorkoutUsers.id eq userId }
                .firstOrNull()
                ?.getOrNull(WorkoutUsers.exerciseDays) ?: 4
        }

        // Same logic as WorkoutRoutes.kt suggestedCount
        // e.g. exerciseDays=6+ → 4 suggested, 4-5 → 3 suggested, <4 → 2 suggested
        val suggestedCount = when {
            exerciseDays >= 6 -> 4
            exerciseDays >= 4 -> 3
            else              -> 2
        }

        // Today's sessions
        val todaySessions = dbQuery {
            WorkoutSession.select {
                (WorkoutSession.userId eq userId) and
                (WorkoutSession.sessionDate eq today)
            }.toList()
        }

        // This week's sessions
        val weekSessions = dbQuery {
            WorkoutSession.select {
                (WorkoutSession.userId eq userId) and
                (WorkoutSession.sessionDate greaterEq weekStart) and
                (WorkoutSession.sessionDate lessEq today)
            }.toList()
        }

        val todayCalories    = todaySessions.sumOf { it[WorkoutSession.caloriesBurned] }
        val todayMinutes     = todaySessions.sumOf { it[WorkoutSession.durationSeconds] } / 60
        val todayDone        = todaySessions.isNotEmpty()

        val weekSessionCount = weekSessions.size
        val weekCalories     = weekSessions.sumOf { it[WorkoutSession.caloriesBurned] }
        val weekMinutes      = weekSessions.sumOf { it[WorkoutSession.durationSeconds] } / 60

        // Week progress: sessions done this week vs exerciseDays goal
        val weekProgressPct = minOf((weekSessionCount * 100) / exerciseDays, 100)

        // Today progress: sessions done today vs suggested count for today
        // e.g. done=1, suggested=3 → 33%  |  done=3, suggested=3 → 100%
        val todayProgressPct = minOf((todaySessions.size * 100) / suggestedCount, 100)

        val lastWorkout = weekSessions.lastOrNull()?.getOrNull(WorkoutSession.workoutName) ?: ""

        @Serializable
        data class WorkoutProgressDto(
            val todayDone:        Boolean,
            val todaySessions:    Int,
            val todayCalories:    Int,
            val todayMinutes:     Int,
            val weekSessions:     Int,
            val weekCalories:     Int,
            val weekMinutes:      Int,
            val weekProgressPct:  Int,
            val todayProgressPct: Int,
            val lastWorkoutName:  String
        )

        call.respond(ApiResponse(success = true, message = "OK", data = WorkoutProgressDto(
            todayDone        = todayDone,
            todaySessions    = todaySessions.size,
            todayCalories    = todayCalories,
            todayMinutes     = todayMinutes,
            weekSessions     = weekSessionCount,
            weekCalories     = weekCalories,
            weekMinutes      = weekMinutes,
            weekProgressPct  = weekProgressPct,
            todayProgressPct = todayProgressPct,
            lastWorkoutName  = lastWorkout
        )))
    }

    // ── POST /api/users/{id}/workout-plan ────────────────────────────────────
    post("/users/{id}/workout-plan") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user id", null))
        val body = call.receive<SaveWorkoutPlanRequest>()

        val planId = dbQuery {
            val existing = WorkoutPlan.select {
                (WorkoutPlan.userId eq userId) and
                (WorkoutPlan.workoutId eq body.workoutId) and
                (WorkoutPlan.scheduledDate eq LocalDate.parse(body.scheduledDate))
            }.firstOrNull()

            if (existing != null) {
                existing[WorkoutPlan.planId]
            } else {
                WorkoutPlan.insert {
                    it[WorkoutPlan.userId]        = userId
                    it[WorkoutPlan.workoutId]     = body.workoutId
                    it[WorkoutPlan.workoutName]   = body.workoutName
                    it[WorkoutPlan.scheduledDate] = LocalDate.parse(body.scheduledDate)
                    it[WorkoutPlan.status]        = "planned"
                }[WorkoutPlan.planId]
            }
        }

        call.respond(ApiResponse(success = true, message = "Workout planned!", data = planId))
    }

    // ── GET /api/users/{id}/workout-plan ─────────────────────────────────────
    get("/users/{id}/workout-plan") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user id", null))

        val plans = dbQuery {
            WorkoutPlan
                .select {
                    (WorkoutPlan.userId eq userId) and
                    (WorkoutPlan.scheduledDate greaterEq LocalDate.now())
                }
                .orderBy(WorkoutPlan.scheduledDate, SortOrder.ASC)
                .limit(7)
                .map { row ->
                    mapOf(
                        "planId"        to row[WorkoutPlan.planId],
                        "workoutId"     to (row.getOrNull(WorkoutPlan.workoutId) ?: 0),
                        "workoutName"   to (row.getOrNull(WorkoutPlan.workoutName) ?: ""),
                        "scheduledDate" to (row.getOrNull(WorkoutPlan.scheduledDate)?.toString() ?: ""),
                        "status"        to row[WorkoutPlan.status]
                    )
                }
        }
        call.respond(ApiResponse(success = true, message = "OK", data = plans))
    }
}
