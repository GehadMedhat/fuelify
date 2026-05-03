package com.example.fuelify.routes

import com.example.fuelify.db.DatabaseFactory.dbQuery
import com.example.fuelify.models.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import java.time.LocalDate
import java.time.LocalDateTime

// Local reference to workout table for image lookup and plan auto-populate
private object WorkoutTable : Table("workout") {
    val workoutId   = integer("workout_id")
    val workoutName = varchar("workout_name", 255)
    val imageUrl    = varchar("image_url", 500).nullable()
    override val primaryKey = PrimaryKey(workoutId)
}

// Alias for selectAll usage in auto-populate
private val Workouts = WorkoutTable

@Serializable
data class MacroData(
    val protein_g: Int, val protein_goal: Int,
    val carbs_g: Int,   val carbs_goal: Int,
    val fat_g: Int,     val fat_goal: Int
)

@Serializable
data class MealItem(
    val id: Int,
    val meal_id: Int,
    val meal_type: String,
    val meal_name: String,
    val calories: Int,
    val protein_g: Int,
    val carbs_g: Int,
    val fat_g: Int,
    val eco_score: Double,
    val has_video: Boolean,
    val image_url: String,
    val difficulty: String,
    val prep_time_minutes: Int,
    val scheduled_time: String,
    val is_completed: Boolean
)

@Serializable
data class DashboardData(
    val user_id: Int,
    val name: String,
    val daily_calories_goal: Int,
    val calories_eaten: Int,
    val calories_remaining: Int,
    val bmi: Double,
    val macros: MacroData,
    val water_glasses: Int,
    val water_goal: Int,
    val workouts_done: Int,
    val workouts_goal: Int,
    val streak_days: Int,
    val today_meals: List<MealItem>,
    val recommended_meals: List<MealItem>,
    // Week-level progress
    val week_meals_eaten: Int,
    val week_meals_total: Int,
    val week_workouts_done: Int,
    val week_workouts_goal: Int,
    val today_workout_name: String,
    val today_workout_image: String,
    val today_workout_id: Int       // so Android can open WorkoutDetailActivity directly
)

@Serializable data class LogWaterRequest(val glasses: Int)
@Serializable data class LogMealRequest(val meal_id: Int)

fun Route.dashboardRoutes() {

    get("/users/{id}/dashboard") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user ID", null))

        val user = dbQuery { Users.select { Users.id eq userId }.singleOrNull() }
            ?: return@get call.respond(HttpStatusCode.NotFound,
                ApiResponse<Nothing>(false, "User not found", null))

        val today = LocalDate.now()

        val bmr        = NutritionEngine.bmr(user[Users.weightKg], user[Users.heightCm], user[Users.age], user[Users.gender])
        val tdee       = NutritionEngine.tdee(bmr, user[Users.activityLevel])
        val dailyCal   = NutritionEngine.dailyCalories(tdee, user[Users.goal])
        val macroGoals = NutritionEngine.macros(dailyCal, user[Users.goal])
        val bmi        = NutritionEngine.bmi(user[Users.weightKg], user[Users.heightCm])

        val userAllergyNames: Set<String> = try {
            dbQuery {
                UserAllergyTypes
                    .join(AllergyTypes, JoinType.INNER, additionalConstraint = {
                        UserAllergyTypes.allergyTypeId eq AllergyTypes.allergyTypeId
                    })
                    .select { UserAllergyTypes.userId eq userId }
                    .map { it[AllergyTypes.name].lowercase() }
                    .toSet()
            }
        } catch (e: Exception) { emptySet() }

        val onboardingAllergies: Set<String> = try {
            Json.decodeFromString<List<String>>(user[Users.allergies]).map { it.lowercase() }.toSet()
        } catch (e: Exception) { emptySet() }

        val excludeKeywords: Set<String> = (userAllergyNames + onboardingAllergies).flatMap { name ->
            NutritionEngine.allergyKeywords[name] ?: emptyList()
        }.toSet()

        val preferredDietTypes: List<String> = NutritionEngine.preferredDietTypes(
            goal = user[Users.goal], bmi = bmi,
            activityLevel = user[Users.activityLevel],
            motivation = user[Users.motivation],
            fitnessLevel = user[Users.fitnessLevel]
        )

        val slots = NutritionEngine.mealSlots(user[Users.mealsPerDay])

        val allMeals: List<ResultRow> = dbQuery { Meals.selectAll().toList() }

        fun isSafe(name: String) = excludeKeywords.none { kw -> name.lowercase().contains(kw) }
        val safeMeals: List<ResultRow> = allMeals.filter { isSafe(it[Meals.mealName]) }

        fun scoreMeal(row: ResultRow, slot: NutritionEngine.MealSlot, targetCal: Int): Double {
            var s = 0.0
            val dtIdx = preferredDietTypes.indexOf(row[Meals.dietType])
            s += when (dtIdx) { 0 -> 30.0; 1 -> 20.0; 2 -> 10.0; 3 -> 5.0; else -> 0.0 }
            if (row[Meals.mealTime].equals(slot.mealTime, ignoreCase = true)) s += 25.0
            if (user[Users.fitnessLevel].lowercase().contains("beginner") && row[Meals.difficulty] == "Easy") s += 10.0
            s -= (Math.abs(row[Meals.calories] - targetCal).toDouble() / 100.0) * 8.0
            val mot = user[Users.motivation].lowercase()
            if ((mot.contains("health") || mot.contains("wellness")) && row[Meals.ecoScore] > 8.5) s += 5.0
            return s
        }

        val existingPlan: List<ResultRow> = dbQuery {
            MealPlans.select { (MealPlans.userId eq userId) and (MealPlans.planDate eq today) }.toList()
        }

        if (existingPlan.isEmpty()) {
            try {
                dbQuery {
                    val dayOff = today.dayOfYear
                   slots.forEach { slot ->
    val targetCal = (dailyCal * slot.caloriePct).toInt()
    val pool: List<ResultRow> = safeMeals
        .filter { it[Meals.mealTime].equals(slot.mealTime, ignoreCase = true) }
        .ifEmpty { safeMeals }
    if (pool.isEmpty()) return@forEach
    val scored = pool.map { row -> Pair(row, scoreMeal(row, slot, targetCal)) }
        .sortedByDescending { p -> p.second }
    val topN = scored.take(6) // take more candidates to avoid duplicates

    // Pick a meal not already inserted today
    val alreadyInsertedIds = MealPlans.select {
        (MealPlans.userId eq userId) and (MealPlans.planDate eq today)
    }.map { it[MealPlans.mealId] }.toSet()

    val pick = topN.map { it.first }
        .firstOrNull { it[Meals.mealId] !in alreadyInsertedIds }
        ?: topN[dayOff % topN.size].first // fallback if all duplicates

    MealPlans.insert {
        it[MealPlans.userId]        = userId
        it[MealPlans.planDate]      = today
        it[MealPlans.mealId]        = pick[Meals.mealId]
        it[MealPlans.mealType]      = slot.mealTime
        it[MealPlans.scheduledTime] = slot.scheduledTime
        it[MealPlans.isCompleted]   = false
    }
}
                }
            } catch (e: Exception) {
                println("Meal generation error: ${e.message}")
                e.printStackTrace()
            }
        }

        dbQuery {
            val logExists = DailyLogs.select {
                (DailyLogs.userId eq userId) and (DailyLogs.logDate eq today)
            }.singleOrNull()
            if (logExists == null) {
                DailyLogs.insert {
                    it[DailyLogs.userId]        = userId
                    it[DailyLogs.logDate]       = today
                    it[DailyLogs.caloriesEaten] = 0
                    it[DailyLogs.waterGlasses]  = 0
                    it[DailyLogs.workoutsDone]  = 0
                    it[DailyLogs.workoutsGoal]  = if (user[Users.exerciseDays] >= 5) 2 else 1
                    it[DailyLogs.streakDays]    = 0
                    it[DailyLogs.updatedAt]     = LocalDateTime.now()
                }
            }
        }

        val todayPlan: List<ResultRow> = dbQuery {
            MealPlans.select { (MealPlans.userId eq userId) and (MealPlans.planDate eq today) }.toList()
        }
        val mealMap: Map<Int, ResultRow> = dbQuery {
            Meals.selectAll().toList().associateBy { it[Meals.mealId] }
        }
        val todayMeals: List<MealItem> = todayPlan.mapNotNull { plan ->
            val m = mealMap[plan[MealPlans.mealId]] ?: return@mapNotNull null
            val sc = plan[MealPlans.scaledCalories].let { if (it > 0) it else m[Meals.calories] }
            val sf = sc.toDouble() / m[Meals.calories].toDouble().coerceAtLeast(1.0)
            MealItem(
                id = plan[MealPlans.id], meal_id = m[Meals.mealId],
                meal_type = plan[MealPlans.mealType], meal_name = m[Meals.mealName],
                calories = sc, protein_g = (m[Meals.protein] * sf).toInt(),
                carbs_g = (m[Meals.carbs] * sf).toInt(), fat_g = (m[Meals.fat] * sf).toInt(),
                eco_score = m[Meals.ecoScore], has_video = m[Meals.hasVideo],
                image_url = m[Meals.imageUrl] ?: "", difficulty = m[Meals.difficulty],
                prep_time_minutes = m[Meals.prepTimeMinutes],
                scheduled_time = plan[MealPlans.scheduledTime],
                is_completed = plan[MealPlans.isCompleted]
            )
        }

        val todayIds: Set<Int> = todayMeals.map { it.meal_id }.toSet()
        val tOff = today.dayOfYear + 1
        val dummySlot = NutritionEngine.MealSlot("Lunch", "12:30 PM", 0.35)
        val recommendedMeals: List<MealItem> = safeMeals
            .filter { it[Meals.mealId] !in todayIds }
            .map { row -> Pair(row, scoreMeal(row, dummySlot, dailyCal / 2)) }
            .sortedByDescending { p -> p.second }
            .take(6)
            .filterIndexed { idx, _ -> idx % 2 == tOff % 2 }
            .take(2)
            .map { pair ->
                val m = pair.first
                MealItem(
                    id = 0, meal_id = m[Meals.mealId],
                    meal_type = m[Meals.mealTime], meal_name = m[Meals.mealName],
                    calories = m[Meals.calories], protein_g = m[Meals.protein].toInt(),
                    carbs_g = m[Meals.carbs].toInt(), fat_g = m[Meals.fat].toInt(),
                    eco_score = m[Meals.ecoScore], has_video = m[Meals.hasVideo],
                    image_url = m[Meals.imageUrl] ?: "", difficulty = m[Meals.difficulty],
                    prep_time_minutes = m[Meals.prepTimeMinutes],
                    scheduled_time = m[Meals.mealTime], is_completed = false
                )
            }

      val log: ResultRow = dbQuery {
    DailyLogs.select { (DailyLogs.userId eq userId) and (DailyLogs.logDate eq today) }
        .firstOrNull()
} ?: return@get call.respond(HttpStatusCode.InternalServerError,
    ApiResponse<Nothing>(false, "Daily log not found", null))

        // ── Week-level stats ──────────────────────────────────────────────────
        val weekStart = today.minusDays(today.dayOfWeek.value.toLong() - 1)
        val weekEnd   = weekStart.plusDays(6)

        // Meals eaten this week = completed meal_plan entries
        val weekMealsEaten = dbQuery {
            MealPlans.select {
                (MealPlans.userId eq userId) and
                (MealPlans.planDate greaterEq weekStart) and
                (MealPlans.planDate lessEq weekEnd) and
                (MealPlans.isCompleted eq true)
            }.count().toInt()
        }

        // Meals total this week = mealsPerDay × 7 (the full weekly goal, not just DB rows)
        val mealsPerDay   = try { user[Users.mealsPerDay].coerceAtLeast(1) } catch (e: Exception) { 3 }
        val weekMealsTotal = mealsPerDay * 7

        // Workouts goal this week = exerciseDays (how many days user plans to work out)
        val exerciseDaysPerWeek = try { user[Users.exerciseDays].coerceAtLeast(1) } catch (e: Exception) { 4 }

        // Workouts done this week from daily_log (updated by workout session save)
        val weekWorkoutsDoneCount = log[DailyLogs.workoutsDone]

        // ── Read today's workout from workout_plan (populated by WorkoutRoutes) ──
        // NOTE: workout_plan is populated exclusively by GET /api/workouts/suggested/{userId}
        // Dashboard only reads it — never writes — to avoid seed conflicts
        val todayWorkoutRow = dbQuery {
            WorkoutPlan.select {
                (WorkoutPlan.userId eq userId) and (WorkoutPlan.scheduledDate eq today)
            }.firstOrNull()
        }
        val todayWorkoutName  = todayWorkoutRow?.getOrNull(WorkoutPlan.workoutName) ?: ""
        val todayWorkoutIdVal = todayWorkoutRow?.getOrNull(WorkoutPlan.workoutId)
        val todayWorkoutImage = if (todayWorkoutIdVal != null) {
            dbQuery {
                WorkoutTable.select { WorkoutTable.workoutId eq todayWorkoutIdVal }
                    .firstOrNull()?.getOrNull(WorkoutTable.imageUrl) ?: ""
            }
        } else ""

        // ── Streak: compute on each dashboard load ────────────────────────────
        val mealsEatenToday = todayMeals.count { it.is_completed }
        val currentStreak   = log[DailyLogs.streakDays]
        val updatedStreak = when {
            mealsEatenToday > 0 && currentStreak == 0 -> {
                // Check yesterday for continuity
                val yesterday     = today.minusDays(1)
                val yesterdayMeals = dbQuery {
                    MealPlans.select {
                        (MealPlans.userId eq userId) and
                        (MealPlans.planDate eq yesterday) and
                        (MealPlans.isCompleted eq true)
                    }.count().toInt()
                }
                val yesterdayLog = dbQuery {
                    DailyLogs.select {
                        (DailyLogs.userId eq userId) and (DailyLogs.logDate eq yesterday)
                    }.firstOrNull()
                }
                val prevStreak = if (yesterdayMeals > 0) (yesterdayLog?.get(DailyLogs.streakDays) ?: 0) else 0
                val newStreak  = prevStreak + 1
                dbQuery {
                    DailyLogs.update({
                        (DailyLogs.userId eq userId) and (DailyLogs.logDate eq today)
                    }) { it[DailyLogs.streakDays] = newStreak }
                }
                newStreak
            }
            mealsEatenToday == 0 -> 0
            else -> currentStreak
        }

        call.respond(ApiResponse(success = true, message = "OK", data = DashboardData(
            user_id = userId, name = user[Users.name],
            daily_calories_goal = dailyCal,
            calories_eaten = log[DailyLogs.caloriesEaten],
            calories_remaining = (dailyCal - log[DailyLogs.caloriesEaten]).coerceAtLeast(0),
            bmi = Math.round(bmi * 10.0) / 10.0,
            macros = MacroData(
                protein_g = todayMeals.filter { it.is_completed }.sumOf { it.protein_g },
                protein_goal = macroGoals.proteinG,
                carbs_g = todayMeals.filter { it.is_completed }.sumOf { it.carbs_g },
                carbs_goal = macroGoals.carbsG,
                fat_g = todayMeals.filter { it.is_completed }.sumOf { it.fat_g },
                fat_goal = macroGoals.fatG
            ),
            water_glasses = log[DailyLogs.waterGlasses], water_goal = 8,
            workouts_done = log[DailyLogs.workoutsDone], workouts_goal = log[DailyLogs.workoutsGoal],
            streak_days        = updatedStreak,
            today_meals        = todayMeals,
            recommended_meals  = recommendedMeals,
            week_meals_eaten   = weekMealsEaten,
            week_meals_total   = weekMealsTotal,
            week_workouts_done = weekWorkoutsDoneCount,
            week_workouts_goal = exerciseDaysPerWeek,
            today_workout_name  = todayWorkoutName,
            today_workout_image = todayWorkoutImage,
            today_workout_id    = todayWorkoutIdVal ?: 0
        )))
    }

    post("/users/{id}/log-water") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user ID", null))
        val req = call.receive<LogWaterRequest>()
        val today = LocalDate.now()
        dbQuery {
            DailyLogs.update({ (DailyLogs.userId eq userId) and (DailyLogs.logDate eq today) }) {
                it[DailyLogs.waterGlasses] = req.glasses
                it[DailyLogs.updatedAt]    = LocalDateTime.now()
            }
        }
        call.respond(ApiResponse(success = true, message = "Water logged", data = req.glasses))
    }

    post("/users/{id}/log-meal") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user ID", null))
        val req = call.receive<LogMealRequest>()
        val today = LocalDate.now()

        val planEntry = dbQuery { MealPlans.select { MealPlans.id eq req.meal_id }.singleOrNull() }
            ?: return@post call.respond(HttpStatusCode.NotFound,
                ApiResponse<Nothing>(false, "Meal plan entry not found", null))

        val meal = dbQuery { Meals.select { Meals.mealId eq planEntry[MealPlans.mealId] }.singleOrNull() }
            ?: return@post call.respond(HttpStatusCode.NotFound,
                ApiResponse<Nothing>(false, "Meal not found", null))

        val currentCals = dbQuery {
            DailyLogs.select { (DailyLogs.userId eq userId) and (DailyLogs.logDate eq today) }
                .singleOrNull()?.get(DailyLogs.caloriesEaten) ?: 0
        }

        dbQuery {
            MealPlans.update({ MealPlans.id eq req.meal_id }) { it[MealPlans.isCompleted] = true }
            DailyLogs.update({ (DailyLogs.userId eq userId) and (DailyLogs.logDate eq today) }) {
                // Use scaled calories from meal_plans, not original meal calories
                val calsToAdd = planEntry[MealPlans.scaledCalories].let { if (it > 0) it else meal[Meals.calories] }
                it[DailyLogs.caloriesEaten] = currentCals + calsToAdd
                it[DailyLogs.updatedAt]     = LocalDateTime.now()
            }
        }
        // Update streak when meal is logged
        dbQuery {
            val todayLog = DailyLogs.select {
                (DailyLogs.userId eq userId) and (DailyLogs.logDate eq today)
            }.firstOrNull()
            val currentStreak = todayLog?.get(DailyLogs.streakDays) ?: 0
            if (currentStreak == 0) {
                // Check yesterday
                val yesterday = today.minusDays(1)
                val yesterdayLog = DailyLogs.select {
                    (DailyLogs.userId eq userId) and (DailyLogs.logDate eq yesterday)
                }.firstOrNull()
                val prevStreak = yesterdayLog?.get(DailyLogs.streakDays) ?: 0
                val newStreak  = if ((yesterdayLog?.get(DailyLogs.caloriesEaten) ?: 0) > 0) prevStreak + 1 else 1
                DailyLogs.update({
                    (DailyLogs.userId eq userId) and (DailyLogs.logDate eq today)
                }) { it[DailyLogs.streakDays] = newStreak }
            }
        }

        call.respond(ApiResponse(success = true, message = "Meal logged", data = meal[Meals.calories]))
    }
}
