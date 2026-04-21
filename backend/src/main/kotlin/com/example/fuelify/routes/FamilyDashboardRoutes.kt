package com.example.fuelify.routes

import com.example.fuelify.db.DatabaseFactory.dbQuery
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
import java.time.format.DateTimeFormatter

// ─── Tables ───────────────────────────────────────────────────────────────────

private object FDB_FamilyGroup : Table("family_group") {
    val groupId   = integer("group_id").autoIncrement()
    val name      = varchar("name", 100)
    val createdBy = integer("created_by")
    override val primaryKey = PrimaryKey(groupId)
}

private object FDB_FamilyMember : Table("family_member") {
    val memberId = integer("member_id").autoIncrement()
    val groupId  = integer("group_id")
    val userId   = integer("user_id")
    val role     = varchar("role", 20).default("member")
    override val primaryKey = PrimaryKey(memberId)
}

private object FDB_Users : Table("users") {
    val id           = integer("id")
    val name         = varchar("name", 100).default("")
    val firstName    = varchar("first_name", 100).nullable()
    val lastName     = varchar("last_name", 100).nullable()
    val goal         = varchar("goal", 100).nullable()
    val gender       = varchar("gender", 20).nullable()
    val age          = integer("age").nullable()
    val weightKg     = integer("weight_kg").nullable()
    val heightCm     = integer("height_cm").nullable()
    val activityLevel = varchar("activity_level", 50).nullable()
    val exerciseDays = integer("exercise_days").nullable()
    override val primaryKey = PrimaryKey(id)
}

private object FDB_DailyLogs : Table("daily_logs") {
    val logId         = integer("id").autoIncrement()
    val userId        = integer("user_id")
    val logDate       = date("log_date")
    val caloriesEaten = integer("calories_eaten").default(0)
    val waterGlasses  = integer("water_glasses").default(0)
    val workoutsDone  = integer("workouts_done").default(0)
    val streakDays    = integer("streak_days").default(0)
    override val primaryKey = PrimaryKey(logId)
}

private object FDB_MealPlans : Table("meal_plans") {
    val userId      = integer("user_id")
    val mealId      = integer("meal_id")
    val planDate    = date("plan_date")
    val isCompleted = bool("is_completed").default(false)
}

private object FDB_WorkoutSession : Table("workout_session") {
    val sessionId      = integer("session_id").autoIncrement()
    val userId         = integer("user_id")
    val sessionDate    = date("session_date")
    val caloriesBurned = integer("calories_burned").default(0)
    val durationSeconds = integer("duration_seconds").default(0)
    override val primaryKey = PrimaryKey(sessionId)
}

// ─── DTOs ─────────────────────────────────────────────────────────────────────

@Serializable
data class FamilyMemberDashboard(
    val memberId:          Int,
    val userId:            Int,
    val name:              String,
    val role:              String,
    val goal:              String,
    val streakDays:        Int,
    val caloriesEaten:     Int,
    val caloriesGoal:      Int,
    val caloriePct:        Int,            // 0–100
    val waterGlasses:      Int,
    val workoutsDoneWeek:  Int,
    val workoutsGoalWeek:  Int,
    val mealsEatenWeek:    Int,
    val mealsTotalWeek:    Int,
    val caloriesBurnedWeek: Int,
    val isOnline:          Boolean,        // logged something today
    val weekDots:          List<Boolean>   // Mon–Sun: true = workout done that day
)

@Serializable
data class FamilyLeaderboardEntry(
    val rank:       Int,
    val userId:     Int,
    val name:       String,
    val streakDays: Int,
    val points:     Int,   // streak * 10 + workoutsWeek * 5 + mealsEatenWeek * 2
    val medal:      String // 🥇 🥈 🥉 or ""
)

@Serializable
data class FamilyDashboardDto(
    val groupId:     Int,
    val groupName:   String,
    val members:     List<FamilyMemberDashboard>,
    val leaderboard: List<FamilyLeaderboardEntry>,
    val totalCaloriesBurnedWeek: Int,
    val totalMealsEatenWeek:     Int,
    val groupStreakAvg:           Int,
    val weekLabel:               String   // "Apr 1 – Apr 7"
)

// ─── Helpers ──────────────────────────────────────────────────────────────────

// Matches DashboardRoutes/NutritionEngine formula exactly so all screens agree
private fun calcCalorieGoal(
    gender: String?, age: Int?, weight: Int?, height: Int?,
    activityLevel: String?, goal: String?
): Int {
    if (gender == null || age == null || weight == null || height == null || activityLevel == null || goal == null) {
        return 2000
    }

    val bmr = if (gender.lowercase().trim() == "female")
        10.0 * weight + 6.25 * height - 5.0 * age - 161
    else
        10.0 * weight + 6.25 * height - 5.0 * age + 5

    val tdee = bmr * when (activityLevel.lowercase().trim()) {
        "sedentary"                   -> 1.2
        "lightly active", "light"     -> 1.375
        "moderately active", "moderate" -> 1.55
        "very active"                 -> 1.725
        "extra active", "athlete"     -> 1.9
        else                          -> 1.55
    }

    return when (goal.lowercase().trim()) {
        "lose weight" -> (tdee - 500).toInt()
        "gain muscle", "gain weight" -> (tdee + 300).toInt()
        "get fit", "maintain", "maintain weight" -> tdee.toInt()
        else -> tdee.toInt()
    }.coerceAtLeast(1200)
}

// ─── Route ────────────────────────────────────────────────────────────────────

fun Route.familyDashboardRoutes() {

    // GET /api/users/{id}/family/dashboard
    get("/users/{id}/family/dashboard") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user id", null))

        // Find which family group this user belongs to
        val memberRow = dbQuery {
            FDB_FamilyMember.select { FDB_FamilyMember.userId eq userId }.firstOrNull()
        } ?: return@get call.respond(ApiResponse(
            success = false, message = "You're not in a family group yet", data = null
        ))

        val groupId = memberRow[FDB_FamilyMember.groupId]

        val groupRow = dbQuery {
            FDB_FamilyGroup.select { FDB_FamilyGroup.groupId eq groupId }.firstOrNull()
        } ?: return@get call.respond(HttpStatusCode.NotFound,
            ApiResponse<Nothing>(false, "Group not found", null))

        val today     = LocalDate.now()
        val weekStart = today.minusDays(today.dayOfWeek.value.toLong() - 1)
        val weekEnd   = weekStart.plusDays(6)

        // Get all members of this group
        val memberRows = dbQuery {
            FDB_FamilyMember.select { FDB_FamilyMember.groupId eq groupId }.toList()
        }

        val fmt = DateTimeFormatter.ofPattern("MMM d")
        val weekLabel = "${weekStart.format(fmt)} – ${weekEnd.format(fmt)}"

        // Build dashboard per member
        val members = memberRows.mapNotNull { mRow ->
            val mUserId  = mRow[FDB_FamilyMember.userId]
            val mRole    = mRow[FDB_FamilyMember.role]
            val memberId = mRow[FDB_FamilyMember.memberId]

            val userRow = dbQuery {
                FDB_Users.select { FDB_Users.id eq mUserId }.firstOrNull()
            } ?: return@mapNotNull null

            val displayName = userRow[FDB_Users.name].ifBlank {
                listOfNotNull(
                    userRow.getOrNull(FDB_Users.firstName),
                    userRow.getOrNull(FDB_Users.lastName)
                ).joinToString(" ").ifBlank { "User #$mUserId" }
            }

            val calorieGoal = calcCalorieGoal(
                gender       = userRow.getOrNull(FDB_Users.gender),
                age          = userRow.getOrNull(FDB_Users.age),
                weight       = userRow.getOrNull(FDB_Users.weightKg),
                height       = userRow.getOrNull(FDB_Users.heightCm),
                activityLevel = userRow.getOrNull(FDB_Users.activityLevel),
                goal         = userRow.getOrNull(FDB_Users.goal)
            )

            // Today's log
            val todayLog = dbQuery {
                FDB_DailyLogs.select {
                    (FDB_DailyLogs.userId eq mUserId) and
                    (FDB_DailyLogs.logDate eq today)
                }.firstOrNull()
            }
            val caloriesEaten = todayLog?.get(FDB_DailyLogs.caloriesEaten) ?: 0
            val waterGlasses  = todayLog?.get(FDB_DailyLogs.waterGlasses)  ?: 0
            val streakDays    = todayLog?.get(FDB_DailyLogs.streakDays)    ?: 0
            val isOnline      = caloriesEaten > 0 || waterGlasses > 0

            // Week meal stats
            val weekMealsEaten = dbQuery {
                FDB_MealPlans.select {
                    (FDB_MealPlans.userId eq mUserId) and
                    (FDB_MealPlans.planDate greaterEq weekStart) and
                    (FDB_MealPlans.planDate lessEq weekEnd) and
                    (FDB_MealPlans.isCompleted eq true)
                }.count().toInt()
            }
            val weekMealsTotal = dbQuery {
                FDB_MealPlans.select {
                    (FDB_MealPlans.userId eq mUserId) and
                    (FDB_MealPlans.planDate greaterEq weekStart) and
                    (FDB_MealPlans.planDate lessEq weekEnd)
                }.count().toInt()
            }.coerceAtLeast(1)

            // Week workout stats
            val weekSessions = dbQuery {
                FDB_WorkoutSession.select {
                    (FDB_WorkoutSession.userId eq mUserId) and
                    (FDB_WorkoutSession.sessionDate greaterEq weekStart) and
                    (FDB_WorkoutSession.sessionDate lessEq weekEnd)
                }.toList()
            }
            val workoutsDoneWeek   = weekSessions.size
            val caloriesBurnedWeek = weekSessions.sumOf { it[FDB_WorkoutSession.caloriesBurned] }
            val exerciseDays       = userRow.getOrNull(FDB_Users.exerciseDays) ?: 4

            // Week dots: Mon=0..Sun=6, true if user did workout that day
            val sessionDates = weekSessions.map { it[FDB_WorkoutSession.sessionDate] }.toSet()
            val weekDots = (0..6).map { offset ->
                weekStart.plusDays(offset.toLong()) in sessionDates
            }

            FamilyMemberDashboard(
                memberId           = memberId,
                userId             = mUserId,
                name               = displayName,
                role               = mRole,
                goal               = userRow.getOrNull(FDB_Users.goal) ?: "",
                streakDays         = streakDays,
                caloriesEaten      = caloriesEaten,
                caloriesGoal       = calorieGoal,
                caloriePct         = if (calorieGoal > 0) (caloriesEaten * 100 / calorieGoal).coerceIn(0, 100) else 0,
                waterGlasses       = waterGlasses,
                workoutsDoneWeek   = workoutsDoneWeek,
                workoutsGoalWeek   = exerciseDays,
                mealsEatenWeek     = weekMealsEaten,
                mealsTotalWeek     = weekMealsTotal,
                caloriesBurnedWeek = caloriesBurnedWeek,
                isOnline           = isOnline,
                weekDots           = weekDots
            )
        }

        // Leaderboard — ranked by points
        val leaderboard = members
            .map { m ->
                val points = m.streakDays * 10 + m.workoutsDoneWeek * 5 + m.mealsEatenWeek * 2
                Pair(m, points)
            }
            .sortedByDescending { it.second }
            .mapIndexed { idx, (m, pts) ->
                FamilyLeaderboardEntry(
                    rank       = idx + 1,
                    userId     = m.userId,
                    name       = m.name,
                    streakDays = m.streakDays,
                    points     = pts,
                    medal      = when (idx) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "" }
                )
            }

        val dto = FamilyDashboardDto(
            groupId                  = groupId,
            groupName                = groupRow[FDB_FamilyGroup.name],
            members                  = members,
            leaderboard              = leaderboard,
            totalCaloriesBurnedWeek  = members.sumOf { it.caloriesBurnedWeek },
            totalMealsEatenWeek      = members.sumOf { it.mealsEatenWeek },
            groupStreakAvg           = if (members.isNotEmpty()) members.sumOf { it.streakDays } / members.size else 0,
            weekLabel                = weekLabel
        )

        call.respond(ApiResponse(success = true, message = "OK", data = dto))
    }
}
