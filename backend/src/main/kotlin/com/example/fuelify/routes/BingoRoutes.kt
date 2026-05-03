package com.example.fuelify.routes

import com.example.fuelify.db.DatabaseFactory.dbQuery
import com.example.fuelify.models.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.DayOfWeek

// ─── Tables ───────────────────────────────────────────────────────────────────

object BingoCards : Table("bingo_cards") {
    val id        = integer("id").autoIncrement()
    val userId    = integer("user_id")
    val weekStart = date("week_start")       // Monday of that week
    val cells     = text("cells")            // JSON array of 9 BingoCell objects
    val createdAt = datetime("created_at")
    override val primaryKey = PrimaryKey(id)
}

// ─── DTOs ─────────────────────────────────────────────────────────────────────

@Serializable
data class BingoCell(
    val id:          Int,       // 0–8 grid position
    val task:        String,    // "Drink 8 glasses"
    val emoji:       String,    // "💧"
    val category:    String,    // "water" | "workout" | "meals" | "calories" | "streak"
    val target:      Int,       // numeric target (8 glasses, 2 workouts, etc.)
    val current:     Int,       // how many done so far
    val completed:   Boolean
)

@Serializable
data class BingoCard(
    val week_start:    String,
    val week_end:      String,
    val cells:         List<BingoCell>,
    val rows_complete: Int,       // 0, 1, 2, 3 rows
    val cols_complete: Int,
    val has_bingo:     Boolean,   // any complete row OR column OR diagonal
    val total_done:    Int,       // how many cells checked
    val reward_msg:    String     // what they earn
)

fun Route.bingoRoutes() {

    // ── GET /api/users/{id}/bingo ──────────────────────────────────────────────
    // Returns (or creates) this week's bingo card with live progress
    get("/users/{id}/bingo") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user ID", null))

        val user = dbQuery { Users.select { Users.id eq userId }.singleOrNull() }
            ?: return@get call.respond(HttpStatusCode.NotFound,
                ApiResponse<Nothing>(false, "User not found", null))

        val today     = LocalDate.now()
        val weekStart = today.with(DayOfWeek.MONDAY)
        val weekEnd   = weekStart.plusDays(6)

        // ── Get or create bingo card for this week ────────────────────────────
        val existingCard = dbQuery {
            BingoCards.select {
                (BingoCards.userId eq userId) and (BingoCards.weekStart eq weekStart)
            }.firstOrNull()
        }

        val cellsJson: String = if (existingCard != null) {
            existingCard[BingoCards.cells]
        } else {
            // Generate a new 3×3 card based on user's goal
            val goal = user[Users.goal].lowercase()
            val exerciseDays = user[Users.exerciseDays]
            val mealsPerDay  = user[Users.mealsPerDay]
            val sessPerDay   = if (exerciseDays >= 5) 2 else 1
            val weekWorkouts = exerciseDays * sessPerDay

            val tasks = buildBingoTasks(goal, weekWorkouts, mealsPerDay)
            val json  = kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(BingoCell.serializer()), tasks)

       dbQuery {
    BingoCards.insert {
        it[BingoCards.userId]    = userId
        it[BingoCards.weekStart] = weekStart as java.time.LocalDate  // ← explicit cast
        it[BingoCards.cells]     = json
        it[BingoCards.createdAt] = LocalDateTime.now()
    }
}
            json
        }

        // ── Load live progress data ────────────────────────────────────────────
        // Water: max glasses in a single day this week (from daily_logs)
        val waterGlasses = dbQuery {
            DailyLogs.select {
                (DailyLogs.userId eq userId) and
                (DailyLogs.logDate greaterEq weekStart) and
                (DailyLogs.logDate lessEq today)
            }.maxOfOrNull { it[DailyLogs.waterGlasses] } ?: 0
        }

        // Workouts: total sessions this week (from workout_session)
      val workoutsDone = dbQuery {
    BingoWorkoutSession.select {
        (BingoWorkoutSession.userId eq userId) and
        (BingoWorkoutSession.sessionDate greaterEq weekStart) and
        (BingoWorkoutSession.sessionDate lessEq today)
    }.count().toInt()
}

        // Meals logged: completed meals this week (from meal_plans)
        val mealsLogged = dbQuery {
            MealPlans.select {
                (MealPlans.userId eq userId) and
                (MealPlans.planDate greaterEq weekStart) and
                (MealPlans.planDate lessEq today) and
                (MealPlans.isCompleted eq true)
            }.count().toInt()
        }

        // Streak: current streak days from daily_logs
        val streakDays = dbQuery {
            DailyLogs.select {
                (DailyLogs.userId eq userId) and (DailyLogs.logDate eq today)
            }.firstOrNull()?.get(DailyLogs.streakDays) ?: 0
        }

        // Calories: days this week where calories_eaten > 0
        val calorieDays = dbQuery {
            DailyLogs.select {
                (DailyLogs.userId eq userId) and
                (DailyLogs.logDate greaterEq weekStart) and
                (DailyLogs.logDate lessEq today) and
                (DailyLogs.caloriesEaten greater 0)
            }.count().toInt()
        }

        // ── Update cells with live progress ───────────────────────────────────
        val cells = kotlinx.serialization.json.Json.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(BingoCell.serializer()), cellsJson
        )

        val updatedCells = cells.map { cell ->
            val current = when (cell.category) {
                "water"    -> waterGlasses
                "workout"  -> workoutsDone
                "meals"    -> mealsLogged
                "streak"   -> streakDays
                "calories" -> calorieDays
                else       -> 0
            }
            cell.copy(current = current, completed = current >= cell.target)
        }

        // ── Bingo check ───────────────────────────────────────────────────────
        // 3×3 grid: check rows, cols, diagonals
        val g = updatedCells.map { it.completed }

        val rows = listOf(
            listOf(0,1,2), listOf(3,4,5), listOf(6,7,8)
        )
        val cols = listOf(
            listOf(0,3,6), listOf(1,4,7), listOf(2,5,8)
        )
        val diags = listOf(listOf(0,4,8), listOf(2,4,6))

        val rowsComplete = rows.count { r -> r.all { g[it] } }
        val colsComplete = cols.count { c -> c.all { g[it] } }
        val hasBingo     = (rows + cols + diags).any { line -> line.all { g[it] } }
        val totalDone    = updatedCells.count { it.completed }

        val rewardMsg = when {
            hasBingo && totalDone == 9 -> "🏆 FULL CARD! You're a Fuelify legend! 20% off next Cloud Kitchen order!"
            hasBingo                   -> "🎉 BINGO! You completed a line! 15% off your next Cloud Kitchen order!"
            totalDone >= 6             -> "🔥 Almost there! Complete a line for your reward!"
            totalDone >= 3             -> "💪 Great progress! Keep going!"
            else                       -> "🎯 Start checking off tasks to earn rewards!"
        }

        call.respond(ApiResponse(success = true, message = "OK", data = BingoCard(
            week_start    = weekStart.toString(),
            week_end      = weekEnd.toString(),
            cells         = updatedCells,
            rows_complete = rowsComplete,
            cols_complete = colsComplete,
            has_bingo     = hasBingo,
            total_done    = totalDone,
            reward_msg    = rewardMsg
        )))
    }
}

// ─── Task builder ─────────────────────────────────────────────────────────────
// Always 9 tasks for the 3×3 grid, tailored to user's goal

private fun buildBingoTasks(goal: String, weekWorkouts: Int, mealsPerDay: Int): List<BingoCell> {
    // Core tasks that everyone gets
    val coreTasks = mutableListOf(
        BingoCell(0, "Drink 8 glasses of water in a day",  "💧", "water",   8,  0, false),
        BingoCell(1, "Log all meals for 3 days",           "🍽", "meals",   mealsPerDay * 3, 0, false),
        BingoCell(2, "Complete ${weekWorkouts/2 + 1} workouts this week", "💪", "workout", weekWorkouts/2 + 1, 0, false),
        BingoCell(3, "Log calories for 5 days",            "🔥", "calories", 5, 0, false),
        BingoCell(4, "Keep a 3-day streak",                "⚡", "streak",  3,  0, false),  // center
        BingoCell(5, "Complete ${weekWorkouts} workouts",  "🏋", "workout", weekWorkouts, 0, false),
        BingoCell(6, "Log all meals for 5 days",           "🥗", "meals",   mealsPerDay * 5, 0, false),
        BingoCell(7, "Drink 8 glasses for 3 days",         "🌊", "water",   8,  0, false),
        BingoCell(8, "Log calories every day this week",   "📊", "calories", 7, 0, false),
    )

    // Swap cell 2 and 5 targets based on goal
    return when {
        goal.contains("lose") -> coreTasks.map { cell ->
            when (cell.id) {
                2 -> cell.copy(task = "Complete ${weekWorkouts/2 + 1} cardio workouts", emoji = "🏃")
                5 -> cell.copy(task = "Hit calorie goal for 4 days", emoji = "🎯", category = "calories", target = 4)
                else -> cell
            }
        }
        goal.contains("muscle") || goal.contains("gain") -> coreTasks.map { cell ->
            when (cell.id) {
                2 -> cell.copy(task = "Complete ${weekWorkouts/2 + 1} strength sessions", emoji = "🏋")
                5 -> cell.copy(task = "Log protein every day", emoji = "🥩", category = "meals", target = mealsPerDay * 7)
                else -> cell
            }
        }
        else -> coreTasks
    }
}

// ─── Reuse existing table objects (declared in other route files) ─────────────
// These reference the same DB tables — no redeclaration needed
// Add this in BingoRoutes.kt under the ─── Tables ─── section

private object BingoWorkoutSession : Table("workout_session") {
    val userId      = integer("user_id")
    val sessionDate = date("session_date")
}


