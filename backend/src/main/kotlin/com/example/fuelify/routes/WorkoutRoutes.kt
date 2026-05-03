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
import java.time.LocalDate

// ─── Tables ───────────────────────────────────────────────────────────────────

private object Workouts : Table("workout") {
    val workoutId            = integer("workout_id")
    val workoutName          = varchar("workout_name", 255)
    val category             = varchar("category", 100)
    val difficulty           = varchar("difficulty", 50)
    val durationMinutes      = integer("duration_minutes")
    val isPremium            = bool("is_premium").default(false)
    val equipment            = varchar("equipment", 255).nullable()
    val imageUrl             = varchar("image_url", 500).nullable()
    val caloriesBurnedEstimate = integer("calories_burned_estimate").default(0)
    override val primaryKey  = PrimaryKey(workoutId)
}

private object Exercises : Table("exercise") {
    val exerciseId      = integer("exercise_id")
    val exerciseName    = varchar("exercise_name", 255)
    val description     = text("description")
    val equipmentNeeded = varchar("equipment_needed", 100).nullable()
    val muscleGroup     = varchar("muscle_group", 100).nullable()
    val imageUrl        = varchar("image_url", 500).nullable()
    override val primaryKey = PrimaryKey(exerciseId)
}

private object WorkoutExercises : Table("workout_exercise") {
    val workoutExerciseId = integer("workout_exercise_id")
    val workoutId         = integer("workout_id")
    val exerciseId        = integer("exercise_id")
    val reps              = integer("reps").default(0)
    val sets              = integer("sets").default(1)
    val restSeconds       = integer("rest_seconds").default(0)
    override val primaryKey = PrimaryKey(workoutExerciseId)
}

// WorkoutUsers and WorkoutPlan are defined in WorkoutPlanTable.kt

// ─── DTOs ─────────────────────────────────────────────────────────────────────

@Serializable
data class WorkoutDto(
    val workoutId: Int,
    val workoutName: String,
    val category: String,
    val difficulty: String,
    val durationMinutes: Int,
    val isPremium: Boolean,
    val equipment: String,
    val imageUrl: String,
    val caloriesBurnedEstimate: Int,
    val exerciseCount: Int
)

@Serializable
data class ExerciseDto(
    val exerciseId: Int,
    val exerciseName: String,
    val description: String,
    val equipmentNeeded: String,
    val muscleGroup: String,
    val imageUrl: String
)

@Serializable
data class WorkoutExerciseDto(
    val exerciseId: Int,
    val exerciseName: String,
    val description: String,
    val muscleGroup: String,
    val equipmentNeeded: String,
    val imageUrl: String,
    val reps: Int,
    val sets: Int,
    val restSeconds: Int
)

@Serializable
data class WorkoutDetailDto(
    val workoutId: Int,
    val workoutName: String,
    val category: String,
    val difficulty: String,
    val durationMinutes: Int,
    val isPremium: Boolean,
    val equipment: String,
    val imageUrl: String,
    val caloriesBurnedEstimate: Int,
    val exercises: List<WorkoutExerciseDto>
)

@Serializable
data class CategoryDto(
    val category: String,
    val workoutCount: Int,
    val emoji: String,
    val color: String
)

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun categoryEmoji(category: String) = when (category) {
    "Yoga"             -> "🧘"
    "Stretch"          -> "🤸"
    "Boxing"           -> "🥊"
    "Running"          -> "🏃"
    "Upper Body"       -> "💪"
    "Gym"              -> "🏋️"
    "Personal Training"-> "📦"
    else               -> "⚡"
}

private fun categoryColor(category: String) = when (category) {
    "Yoga"             -> "#FEF9C3"
    "Stretch"          -> "#DCFCE7"
    "Boxing"           -> "#FFE4E6"
    "Running"          -> "#FEF9C3"
    "Upper Body"       -> "#FEF9C3"
    "Gym"              -> "#F3F4F6"
    "Personal Training"-> "#FEF9C3"
    else               -> "#F3F4F6"
}

private fun exerciseImageUrl(muscleGroup: String): String {
    return when {
        muscleGroup.contains("Chest", true)     -> "https://images.unsplash.com/photo-1571019613454-1cb2f99b2d8b?w=200"
        muscleGroup.contains("Back", true)      -> "https://images.unsplash.com/photo-1526506118085-60ce8714f8c5?w=200"
        muscleGroup.contains("Legs", true) ||
        muscleGroup.contains("Quad", true)      -> "https://images.unsplash.com/photo-1574680096145-d05b474e2155?w=200"
        muscleGroup.contains("Shoulder", true)  -> "https://images.unsplash.com/photo-1532029837206-abbe2b7620e3?w=200"
        muscleGroup.contains("Core", true) ||
        muscleGroup.contains("Full", true)      -> "https://images.unsplash.com/photo-1571019613576-2b22c76fd955?w=200"
        muscleGroup.contains("Glute", true)     -> "https://images.unsplash.com/photo-1517836357463-d25dfeac3438?w=200"
        muscleGroup.contains("Yoga", true) ||
        muscleGroup.contains("Hip", true)       -> "https://images.unsplash.com/photo-1544367567-0f2fcb009e0b?w=200"
        else                                    -> "https://images.unsplash.com/photo-1517960413843-0aee8e2b3285?w=200"
    }
}

// ─── Plan Builder ─────────────────────────────────────────────────────────────

data class WorkoutSuggestionParams(
    val categories:        List<String>,
    val difficulty:        String,
    val maxDurationMinutes: Int,
    val reason:            String,
    val sessionsPerDay:    Int         = 1,
    val sessionLabels:     List<String> = listOf("Full Body")
)

/**
 * Pure (non-suspend) function that derives workout plan parameters from user profile.
 * Used by the suggested route to both show cards AND populate workout_plan.
 */
private fun buildPlanParams(
    goal: String?,
    fitnessLevel: String?,
    trainingPlace: String?,
    activityLevel: String?,
    exerciseDays: Int?,
    weightKg: Int?,
    heightCm: Int?,
    age: Int?,
    gender: String?,
    motivation: String?
): WorkoutSuggestionParams {
    val g  = goal?.lowercase() ?: ""
    val fp = trainingPlace?.lowercase() ?: ""
    val fl = fitnessLevel?.lowercase() ?: ""
    val al = activityLevel?.lowercase() ?: ""
    val days = exerciseDays ?: 4

    // ── Categories: goal + place, NO cross-contamination ──────────────────────
    val categories: List<String> = when {
        fp.contains("gym") -> when {
            g.contains("muscle") || g.contains("gain") || g.contains("bulk") ->
                listOf("Gym", "Upper Body", "Personal Training")
            g.contains("lose") || g.contains("fat") || g.contains("weight") ->
                listOf("Running", "Boxing", "Gym")
            else -> listOf("Gym", "Upper Body", "Running")
        }
        fp.contains("home") -> when {
            g.contains("muscle") || g.contains("gain") ->
                listOf("Upper Body", "Stretch")
            g.contains("lose") || g.contains("fat") ->
                listOf("Running", "Upper Body", "Stretch")
            else -> listOf("Upper Body", "Running", "Stretch", "Yoga")
        }
        else -> when {  // Hybrid
            g.contains("muscle") || g.contains("gain") ->
                listOf("Gym", "Upper Body", "Personal Training")
            g.contains("lose") || g.contains("fat") ->
                listOf("Running", "Boxing", "Gym")
            else -> listOf("Gym", "Running", "Upper Body")
        }
    }

    // ── Difficulty ────────────────────────────────────────────────────────────
    val difficulty = when {
        fl.contains("beginner") || fl.contains("irregular") -> "Beginner"
        fl.contains("advanced") || fl.contains("athlete")   -> "Advanced"
        else -> "Medium"
    }

    // ── Sessions per day via BMR/TDEE ─────────────────────────────────────────
    val wKg  = (weightKg ?: 70).toDouble()
    val hCm  = (heightCm ?: 170).toDouble()
    val aYrs = (age ?: 25).toDouble()
    val bmr = if (gender?.lowercase() == "female")
        10.0 * wKg + 6.25 * hCm - 5.0 * aYrs - 161
    else
        10.0 * wKg + 6.25 * hCm - 5.0 * aYrs + 5

    val tdee = bmr * when {
        al.contains("sedentary")   -> 1.2
        al.contains("light")       -> 1.375
        al.contains("moderate")    -> 1.55
        al.contains("very")        -> 1.725
        al.contains("extra") || al.contains("athlete") -> 1.9
        else -> 1.375
    }

    val dailyBurnTarget = (tdee * when {
        g.contains("lose") || g.contains("fat")   -> 0.30
        g.contains("muscle") || g.contains("gain") -> 0.20
        else -> 0.22
    }).toInt().coerceAtLeast(200)

    val avgCalPerSession = when {
        categories.any { it in listOf("Running", "Boxing") } -> 350
        categories.any { it in listOf("Yoga", "Stretch") }   -> 180
        else -> 320
    }

    val sessionsPerDay = (dailyBurnTarget.toDouble() / avgCalPerSession.toDouble())
        .let { kotlin.math.ceil(it).toInt() }
        .coerceIn(1, 3)

    // ── Session labels per slot ───────────────────────────────────────────────
    val sessionLabels: List<String> = when {
        g.contains("muscle") || g.contains("gain") -> when (sessionsPerDay) {
            1    -> listOf("Full Body Strength")
            2    -> listOf("Upper Body", "Lower Body")
            else -> listOf("Chest & Triceps", "Back & Biceps", "Legs & Core")
        }
        g.contains("lose") || g.contains("fat") -> when (sessionsPerDay) {
            1    -> listOf("Cardio Burn")
            2    -> listOf("Cardio", "HIIT")
            else -> listOf("Cardio", "HIIT", "Full Body")
        }
        else -> when (sessionsPerDay) {
            1    -> listOf("Full Body")
            2    -> listOf("Strength", "Cardio")
            else -> listOf("Upper Body", "Cardio", "Lower Body")
        }
    }

    val maxDuration = if (sessionsPerDay >= 2) 45 else if (days >= 5) 50 else 70

    val reason = buildString {
        val goalStr = g.replaceFirstChar { it.uppercase() }.ifBlank { "Fitness" }
        append("$goalStr · ${if (fp.contains("gym")) "Gym" else if (fp.contains("home")) "Home" else "Hybrid"}")
        append(" · $days days/week")
        if (sessionsPerDay > 1) append(" · $sessionsPerDay sessions/day")
    }

    return WorkoutSuggestionParams(
        categories         = categories,
        difficulty         = difficulty,
        maxDurationMinutes = maxDuration,
        reason             = reason.ifBlank { "Personalized for you" },
        sessionsPerDay     = sessionsPerDay,
        sessionLabels      = sessionLabels
    )
}

/**
 * Build a PROPER weekly workout plan based on:
 *  - Goal       → which muscle groups / workout types
 *  - Training place → Gym vs Home vs Hybrid categories
 *  - TDEE / calorie deficit → how many sessions per day to hit burn target
 *  - Fitness level → workout difficulty
 *  - Exercise days → which days of the week to schedule
 *
 * Returns: list of (dayOffset, sessionNumber, workoutId, workoutName, muscleFocus, targetCal)
 */
data class PlannedSession(
    val dayOffset:     Int,
    val sessionNumber: Int,
    val workoutId:     Int,
    val workoutName:   String,
    val muscleFocus:   String,
    val targetCal:     Int
)

private suspend fun buildWeeklyPlan(
    userId: Int,
    goal: String,
    trainingPlace: String,
    fitnessLevel: String,
    exerciseDays: Int,
    tdee: Int,                    // total daily energy expenditure
    weightKg: Int
): List<PlannedSession> {

    // ── 1. Determine which categories match this user ──────────────────────────
    val isGym  = trainingPlace.contains("gym", true) || trainingPlace.contains("hybrid", true)
    val isHome = trainingPlace.contains("home", true) && !isGym

    val goalCategories: List<String> = when {
        goal.contains("build muscle", true) || goal.contains("gain", true) ->
            if (isHome) listOf("Upper Body","Stretch") else listOf("Gym","Upper Body","Personal Training")
        goal.contains("lose", true) || goal.contains("weight", true) ->
            if (isHome) listOf("Running","Upper Body","Stretch") else listOf("Running","Boxing","Gym")
        goal.contains("maintain", true) ->
            if (isHome) listOf("Yoga","Running","Stretch","Upper Body") else listOf("Running","Gym","Yoga","Upper Body")
        else ->
            if (isHome) listOf("Upper Body","Stretch","Running") else listOf("Gym","Upper Body","Running")
    }

    val difficulty = when {
        fitnessLevel.contains("beginner", true) -> "Beginner"
        fitnessLevel.contains("advanced", true) ||
        fitnessLevel.contains("athlete", true)  -> "Advanced"
        else                                     -> "Medium"
    }

    // ── 2. Calorie burn target per workout day ─────────────────────────────────
    // Daily exercise calories = TDEE × 20-30% depending on goal
    val burnPctOfTdee = when {
        goal.contains("lose", true) || goal.contains("weight", true) -> 0.30
        goal.contains("build muscle", true) || goal.contains("gain", true) -> 0.20
        else -> 0.22
    }
    val dailyBurnTarget = (tdee * burnPctOfTdee).toInt().coerceAtLeast(200)

    // ── 3. How many sessions per workout day ──────────────────────────────────
    // Each gym session burns on average ~300–500 kcal
    // Each running/cardio session burns ~300–400 kcal
    val avgCalPerSession = when {
        goalCategories.any { it in listOf("Running","Boxing") } -> 350
        goalCategories.contains("Yoga") || goalCategories.contains("Stretch") -> 180
        else -> 320  // Gym / Upper Body
    }
    // Sessions per day = ceil(dailyBurnTarget / avgCalPerSession), max 3
    val sessionsPerDay = (dailyBurnTarget / avgCalPerSession).coerceIn(1, 3)
    val targetCalPerSession = dailyBurnTarget / sessionsPerDay

    // ── 4. Which days of the week ─────────────────────────────────────────────
    val workoutDayOffsets = when (exerciseDays) {
        1    -> listOf(0)
        2    -> listOf(0, 3)
        3    -> listOf(0, 2, 4)
        4    -> listOf(0, 1, 3, 4)
        5    -> listOf(0, 1, 2, 3, 4)
        6    -> listOf(0, 1, 2, 3, 4, 5)
        else -> listOf(0, 1, 2, 3, 4, 5, 6)
    }

    // ── 5. Muscle group split across workout days ──────────────────────────────
    // For muscle-building: rotate muscle groups so same muscle isn't hit twice in a row
    val muscleSplits: List<List<String>> = when {
        goal.contains("build muscle", true) || goal.contains("gain", true) -> when (exerciseDays) {
            1    -> listOf(listOf("Full Body"))
            2    -> listOf(listOf("Upper Body"), listOf("Lower Body"))
            3    -> listOf(listOf("Push","Chest","Shoulders","Triceps"),
                           listOf("Pull","Back","Biceps"),
                           listOf("Legs","Glutes"))
            4    -> listOf(listOf("Chest","Shoulders"),
                           listOf("Back","Biceps"),
                           listOf("Legs","Glutes"),
                           listOf("Arms","Core"))
            else -> listOf(listOf("Chest"), listOf("Back"), listOf("Legs"),
                           listOf("Shoulders","Triceps"), listOf("Biceps","Core"),
                           listOf("Full Body"))
        }
        goal.contains("lose", true) || goal.contains("weight", true) ->
            workoutDayOffsets.map { listOf("Cardio","Full Body") }
        else ->
            workoutDayOffsets.map { listOf("Full Body") }
    }

    // ── 6. Fetch workout pool from DB ─────────────────────────────────────────
    // Get workouts in the user's preferred categories, ordered by calorie burn desc
    val workoutPool = com.example.fuelify.db.DatabaseFactory.dbQuery {
        var results = Workouts.select {
            Workouts.category inList goalCategories
        }.orderBy(Workouts.caloriesBurnedEstimate, SortOrder.DESC).toList()

        // If difficulty matched pool exists, prefer it
        val diffResults = results.filter {
            it[Workouts.difficulty].equals(difficulty, true)
        }
        if (diffResults.isNotEmpty()) results = diffResults

        // Fallback: any workout
        if (results.isEmpty()) results = Workouts.selectAll().toList()
        results
    }

    // ── 7. Build the plan entries ─────────────────────────────────────────────
    val sessions = mutableListOf<PlannedSession>()
    val usedWorkoutIds = mutableSetOf<Int>()  // avoid repeating same workout

    workoutDayOffsets.forEachIndexed { dayIdx, dayOffset ->
        val splitFocus = muscleSplits.getOrElse(dayIdx) { listOf("Full Body") }
        val focusLabel = splitFocus.first()

        repeat(sessionsPerDay) { sessionIdx ->
            // Find the best matching workout for this session that hasn't been used
            val candidate = workoutPool
                .filter { row ->
                    val wId = row[Workouts.workoutId]
                    // Prefer workouts targeting this day's muscle focus
                    val catMatches = splitFocus.any { focus ->
                        row[Workouts.category].contains(focus, true) ||
                        row[Workouts.workoutName].contains(focus, true)
                    }
                    wId !in usedWorkoutIds || catMatches  // allow reuse only if category matches
                }
                .firstOrNull()
                ?: workoutPool.firstOrNull()
                ?: return@repeat

            val wId = candidate[Workouts.workoutId]
            usedWorkoutIds.add(wId)

            sessions.add(PlannedSession(
                dayOffset     = dayOffset,
                sessionNumber = sessionIdx + 1,
                workoutId     = wId,
                workoutName   = candidate[Workouts.workoutName],
                muscleFocus   = focusLabel,
                targetCal     = targetCalPerSession
            ))
        }
    }

    return sessions
}

// ─── Route helpers ────────────────────────────────────────────────────────────

private fun rowToWorkout(row: ResultRow, exerciseCount: Int) = WorkoutDto(
    workoutId             = row[Workouts.workoutId],
    workoutName           = row[Workouts.workoutName],
    category              = row[Workouts.category],
    difficulty            = row[Workouts.difficulty],
    durationMinutes       = row[Workouts.durationMinutes],
    isPremium             = row[Workouts.isPremium],
    equipment             = row.getOrNull(Workouts.equipment) ?: "None",
    imageUrl              = row.getOrNull(Workouts.imageUrl) ?: "",
    caloriesBurnedEstimate = row[Workouts.caloriesBurnedEstimate],
    exerciseCount         = exerciseCount
)

// ─── Routes ───────────────────────────────────────────────────────────────────

fun Route.workoutRoutes() {

    // ── GET /api/workouts/categories ─────────────────────────────────────────
    get("/workouts/categories") {
        val categories = dbQuery {
            Workouts
                .slice(Workouts.category, Workouts.category.count())
                .selectAll()
                .groupBy(Workouts.category)
                .orderBy(Workouts.category.count(), SortOrder.DESC)
                .map { row ->
                    CategoryDto(
                        category     = row[Workouts.category],
                        workoutCount = row[Workouts.category.count()].toInt(),
                        emoji        = categoryEmoji(row[Workouts.category]),
                        color        = categoryColor(row[Workouts.category])
                    )
                }
        }
        call.respond(ApiResponse(success = true, message = "OK", data = categories))
    }

    // ── GET /api/workouts/suggested/{userId} ─────────────────────────────────
    // Returns personalized workouts based on ALL user data (goal, place, fitness, body, etc.)
    // Auto-populates workout_plan with MULTIPLE sessions per day where appropriate
    get("/workouts/suggested/{userId}") {
        val userId = call.parameters["userId"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user id", null))

        val user = dbQuery {
            WorkoutPlanUsers.select { WorkoutPlanUsers.id eq userId }.firstOrNull()
        }

        val goal          = user?.getOrNull(WorkoutPlanUsers.goal)          ?: ""
        val fitnessLevel  = user?.getOrNull(WorkoutPlanUsers.fitnessLevel)  ?: ""
        val trainingPlace = user?.getOrNull(WorkoutPlanUsers.trainingPlace) ?: ""
        val activityLevel = user?.getOrNull(WorkoutPlanUsers.activityLevel) ?: ""
        val exerciseDays  = user?.getOrNull(WorkoutPlanUsers.exerciseDays)  ?: 4
        val weightKg      = user?.getOrNull(WorkoutPlanUsers.weightKg)
        val heightCm      = user?.getOrNull(WorkoutPlanUsers.heightCm)
        val age           = user?.getOrNull(WorkoutPlanUsers.age)
        val gender        = user?.getOrNull(WorkoutPlanUsers.gender)
        val motivation    = user?.getOrNull(WorkoutPlanUsers.motivation)

        val params = buildPlanParams(goal, fitnessLevel, trainingPlace, activityLevel,
            exerciseDays, weightKg, heightCm, age, gender, motivation)

        val today = LocalDate.now()
        val daySeed = today.dayOfYear + userId  // changes every day, unique per user

        // How many workouts to return: scale with exerciseDays (more days = show more variety)
        val countToReturn = when {
            exerciseDays >= 6 -> 4
            exerciseDays >= 4 -> 3
            else              -> 2
        }

        val allMatchingWorkouts = dbQuery {
            var results = Workouts.select {
                (Workouts.category inList params.categories) and
                (Workouts.isPremium eq false) and
                (Workouts.durationMinutes lessEq params.maxDurationMinutes)
            }.orderBy(Workouts.workoutId, SortOrder.ASC).toList()

            if (results.isEmpty()) results = Workouts.select {
                Workouts.category inList params.categories
            }.toList()

            if (results.isEmpty()) results = Workouts.selectAll().toList()
            results
        }

        // Rotate: shuffle pool deterministically using daySeed, pick countToReturn
        val rotatedPool = allMatchingWorkouts.shuffled(kotlin.random.Random(daySeed.toLong()))
        val picked = rotatedPool.take(countToReturn)

        val workouts = dbQuery {
            picked.map { row ->
                val exCount = WorkoutExercises
                    .select { WorkoutExercises.workoutId eq row[Workouts.workoutId] }
                    .count().toInt()
                rowToWorkout(row, exCount)
            }
        }

        // ── Auto-populate workout_plan with multiple sessions per day ────────────
        dbQuery {
            val weekStart = today.minusDays(today.dayOfWeek.value.toLong() - 1)
            val weekEnd   = weekStart.plusDays(6)

            val existingPlanCount = WorkoutPlan.select {
                (WorkoutPlan.userId eq userId) and
                (WorkoutPlan.scheduledDate greaterEq weekStart) and
                (WorkoutPlan.scheduledDate lessEq weekEnd)
            }.count()

            if (existingPlanCount == 0L) {
                val workoutDayIndices: List<Int> = when (exerciseDays) {
                    1    -> listOf(0)
                    2    -> listOf(0, 3)
                    3    -> listOf(0, 2, 4)
                    4    -> listOf(0, 1, 3, 4)
                    5    -> listOf(0, 1, 2, 3, 4)
                    6    -> listOf(0, 1, 2, 3, 4, 5)
                    else -> listOf(0, 1, 2, 3, 4, 5, 6)
                }

                val sessPerDay    = params.sessionsPerDay
                val sessionLabels = params.sessionLabels
                val totalSlots    = (exerciseDays as Int) * sessPerDay

                // Shuffle entire matching pool once, cycle through for all slots
                val weekShuffled = allMatchingWorkouts.shuffled(
                    kotlin.random.Random(daySeed.toLong())
                )
                val slotWorkouts = (0 until totalSlots).map { slotIdx ->
                    weekShuffled.getOrElse(slotIdx % weekShuffled.size.coerceAtLeast(1)) {
                        weekShuffled.first()
                    }
                }

                var slotIdx = 0
                workoutDayIndices.forEach { dayOffset ->
                    val planDate = weekStart.plusDays(dayOffset.toLong())

                    repeat(sessPerDay) { sessionN ->
                        val sessionNum = sessionN + 1

                        // Check per (user, date, session_number) — matches the unique constraint
                        val alreadyExists = WorkoutPlan.select {
                            (WorkoutPlan.userId eq userId) and
                            (WorkoutPlan.scheduledDate eq planDate) and
                            (WorkoutPlan.sessionNumber eq sessionNum)
                        }.count() > 0L

                        if (!alreadyExists) {
                            val workout = slotWorkouts.getOrNull(slotIdx)
                                ?: run { slotIdx++; return@repeat }
                            val label = sessionLabels.getOrElse(sessionN) {
                                sessionLabels.lastOrNull() ?: "Session $sessionNum"
                            }
                            try {
                                WorkoutPlan.insert {
                                    it[WorkoutPlan.userId]        = userId
                                    it[WorkoutPlan.workoutId]     = workout[Workouts.workoutId]
                                    it[WorkoutPlan.workoutName]   = workout[Workouts.workoutName]
                                    it[WorkoutPlan.scheduledDate] = planDate
                                    it[WorkoutPlan.status]        = "planned"
                                    it[WorkoutPlan.sessionNumber] = sessionNum
                                    it[WorkoutPlan.sessionLabel]  = label
                                }
                            } catch (e: Exception) {
                                // Silently skip on duplicate (race condition safety)
                            }
                        }
                        slotIdx++
                    }
                }
            }
        }

        @kotlinx.serialization.Serializable
        data class SuggestedWorkoutsDto(
            val workouts:       List<WorkoutDto>,
            val reason:         String,
            val basedOn:        String,
            val exerciseDays:   Int,
            val sessionsPerDay: Int,
            val weekTotal:      Int   // exerciseDays × sessionsPerDay = total sessions this week
        )

        call.respond(ApiResponse(success = true, message = "OK", data = SuggestedWorkoutsDto(
            workouts       = workouts,
            reason         = params.reason,
            basedOn        = "goal=${goal}, place=${trainingPlace}, days=${exerciseDays}, level=${fitnessLevel}",
            exerciseDays   = exerciseDays,
            sessionsPerDay = params.sessionsPerDay,
            weekTotal      = (exerciseDays as Int) * params.sessionsPerDay
        )))
    }

    // ── GET /api/workouts?category=&difficulty=&limit= ───────────────────────
    get("/workouts") {
        val category   = call.request.queryParameters["category"]
        val difficulty = call.request.queryParameters["difficulty"]
        val limit      = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100

        val workouts = dbQuery {
            var query = Workouts.selectAll()
            if (!category.isNullOrBlank())   query = query.andWhere { Workouts.category eq category }
            if (!difficulty.isNullOrBlank())  query = query.andWhere { Workouts.difficulty eq difficulty }
            query.limit(limit).map { row ->
                val exCount = WorkoutExercises.select { WorkoutExercises.workoutId eq row[Workouts.workoutId] }.count().toInt()
                rowToWorkout(row, exCount)
            }
        }
        call.respond(ApiResponse(success = true, message = "OK", data = workouts))
    }

    // ── GET /api/workouts/recommended/{userId} ───────────────────────────────
    // Extra recommendations for home screen — different from the weekly plan,
    // rotates daily, mix of categories the user would benefit from
    get("/workouts/recommended/{userId}") {
        val userId = call.parameters["userId"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user id", null))

        val user = dbQuery {
            WorkoutPlanUsers.select { WorkoutPlanUsers.id eq userId }.firstOrNull()
        }
        val goal          = user?.getOrNull(WorkoutPlanUsers.goal)          ?: ""
        val fitnessLevel  = user?.getOrNull(WorkoutPlanUsers.fitnessLevel)  ?: ""
        val trainingPlace = user?.getOrNull(WorkoutPlanUsers.trainingPlace) ?: ""
        val activityLevel = user?.getOrNull(WorkoutPlanUsers.activityLevel) ?: ""
        val exerciseDays  = user?.getOrNull(WorkoutPlanUsers.exerciseDays)  ?: 4

val params = buildPlanParams(
    goal          = goal,
    fitnessLevel  = fitnessLevel,
    trainingPlace = trainingPlace,
    activityLevel = activityLevel,
    exerciseDays  = exerciseDays,
    weightKg      = user?.getOrNull(WorkoutPlanUsers.weightKg),
    heightCm      = user?.getOrNull(WorkoutPlanUsers.heightCm),
    age           = user?.getOrNull(WorkoutPlanUsers.age),
    gender        = user?.getOrNull(WorkoutPlanUsers.gender),
    motivation    = user?.getOrNull(WorkoutPlanUsers.motivation)
)

        val today    = LocalDate.now()
        // Different seed from the main suggested endpoint — offset by 999
        val recSeed  = today.dayOfYear + userId + 999

        // Get workout IDs already in the plan this week so we exclude them
        val weekStart = today.minusDays(today.dayOfWeek.value.toLong() - 1)
        val weekEnd   = weekStart.plusDays(6)
        val planWorkoutIds: Set<Int> = dbQuery {
            WorkoutPlan.select {
                (WorkoutPlan.userId eq userId) and
                (WorkoutPlan.scheduledDate greaterEq weekStart) and
                (WorkoutPlan.scheduledDate lessEq weekEnd)
            }.mapNotNull { it.getOrNull(WorkoutPlan.workoutId) }.toSet()
        }

        // Pick from categories, exclude already-planned workouts
        val allWorkouts = dbQuery {
            var results = Workouts.select {
                (Workouts.category inList params.categories) and
                (Workouts.isPremium eq false)
            }.toList()
            if (results.isEmpty()) results = Workouts.selectAll().toList()
            results
        }

        val notInPlan = allWorkouts.filter { it[Workouts.workoutId] !in planWorkoutIds }
        val pool      = notInPlan.ifEmpty { allWorkouts }  // fallback if all are in plan

        val count = when {
            exerciseDays >= 6 -> 3
            exerciseDays >= 3 -> 2
            else              -> 2
        }

        val picked = pool.shuffled(kotlin.random.Random(recSeed.toLong())).take(count)

        val workouts = dbQuery {
            picked.map { row ->
                val exCount = WorkoutExercises
                    .select { WorkoutExercises.workoutId eq row[Workouts.workoutId] }
                    .count().toInt()
                rowToWorkout(row, exCount)
            }
        }

        @kotlinx.serialization.Serializable
        data class RecommendedWorkoutsDto(
            val workouts: List<WorkoutDto>,
            val reason:   String
        )
        call.respond(ApiResponse(success = true, message = "OK", data = RecommendedWorkoutsDto(
            workouts = workouts,
            reason   = params.reason
        )))
    }

    // ── GET /api/users/{id}/workout-plan/week ────────────────────────────────
    // Returns this week's full plan, grouped with multiple sessions per day
    get("/users/{id}/workout-plan/week") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user id", null))

        val today     = LocalDate.now()
        val weekStart = today.minusDays(today.dayOfWeek.value.toLong() - 1)
        val weekEnd   = weekStart.plusDays(6)
        val dayLabels = listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun")

        @kotlinx.serialization.Serializable
        data class WeekPlanEntry(
            val planId:                  Int,
            val workoutId:               Int,
            val workoutName:             String,
            val scheduledDate:           String,
            val dayLabel:                String,
            val dayNumber:               Int,
            val isToday:                 Boolean,
            val isPast:                  Boolean,
            val status:                  String,
            val sessionNumber:           Int,
            val sessionLabel:            String,
            val imageUrl:                String,
            val category:                String,
            val difficulty:              String,
            val durationMinutes:         Int,
            val caloriesBurnedEstimate:  Int
        )

        val planRows = dbQuery {
            WorkoutPlan.select {
                (WorkoutPlan.userId eq userId) and
                (WorkoutPlan.scheduledDate greaterEq weekStart) and
                (WorkoutPlan.scheduledDate lessEq weekEnd)
            }.orderBy(WorkoutPlan.scheduledDate, SortOrder.ASC)
             .orderBy(WorkoutPlan.sessionNumber, SortOrder.ASC)
             .toList()
        }

        val entries = planRows.mapNotNull { row ->
            val wId   = row.getOrNull(WorkoutPlan.workoutId) ?: return@mapNotNull null
            val wDate = row.getOrNull(WorkoutPlan.scheduledDate) ?: return@mapNotNull null
            val dayIdx = (wDate.dayOfWeek.value - 1).coerceIn(0, 6)

            val workoutRow = dbQuery {
                Workouts.select { Workouts.workoutId eq wId }.firstOrNull()
            }

            WeekPlanEntry(
                planId                 = row[WorkoutPlan.planId],
                workoutId              = wId,
                workoutName            = row.getOrNull(WorkoutPlan.workoutName) ?: "",
                scheduledDate          = wDate.toString(),
                dayLabel               = dayLabels[dayIdx],
                dayNumber              = wDate.dayOfMonth,
                isToday                = wDate == today,
                isPast                 = wDate.isBefore(today),
                status                 = row[WorkoutPlan.status],
                sessionNumber          = row.getOrNull(WorkoutPlan.sessionNumber) ?: 1,
                sessionLabel           = row.getOrNull(WorkoutPlan.sessionLabel) ?: "",
                imageUrl               = workoutRow?.getOrNull(Workouts.imageUrl) ?: "",
                category               = workoutRow?.get(Workouts.category) ?: "",
                difficulty             = workoutRow?.get(Workouts.difficulty) ?: "",
                durationMinutes        = workoutRow?.get(Workouts.durationMinutes) ?: 0,
                caloriesBurnedEstimate = workoutRow?.get(Workouts.caloriesBurnedEstimate) ?: 0
            )
        }

        call.respond(ApiResponse(success = true, message = "OK", data = entries))
    }

    // ── GET /api/workouts/{id} ───────────────────────────────────────────────
    get("/workouts/{id}") {
        val workoutId = call.parameters["id"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid workout id", null))

        val detail = dbQuery {
            val workout = Workouts.select { Workouts.workoutId eq workoutId }.firstOrNull()
                ?: return@dbQuery null

            val exercises = WorkoutExercises
                .join(Exercises, JoinType.INNER,
                    additionalConstraint = { WorkoutExercises.exerciseId eq Exercises.exerciseId })
                .select { WorkoutExercises.workoutId eq workoutId }
                .map { row ->
                    val muscle = row.getOrNull(Exercises.muscleGroup) ?: ""
                    WorkoutExerciseDto(
                        exerciseId      = row[Exercises.exerciseId],
                        exerciseName    = row[Exercises.exerciseName],
                        description     = row[Exercises.description],
                        muscleGroup     = muscle,
                        equipmentNeeded = row.getOrNull(Exercises.equipmentNeeded) ?: "None",
                        imageUrl        = row.getOrNull(Exercises.imageUrl) ?: exerciseImageUrl(muscle),
                        reps            = row[WorkoutExercises.reps],
                        sets            = row[WorkoutExercises.sets],
                        restSeconds     = row[WorkoutExercises.restSeconds]
                    )
                }

            WorkoutDetailDto(
                workoutId             = workout[Workouts.workoutId],
                workoutName           = workout[Workouts.workoutName],
                category              = workout[Workouts.category],
                difficulty            = workout[Workouts.difficulty],
                durationMinutes       = workout[Workouts.durationMinutes],
                isPremium             = workout[Workouts.isPremium],
                equipment             = workout.getOrNull(Workouts.equipment) ?: "None",
                imageUrl              = workout.getOrNull(Workouts.imageUrl) ?: "",
                caloriesBurnedEstimate = workout[Workouts.caloriesBurnedEstimate],
                exercises             = exercises
            )
        }

        if (detail == null)
            call.respond(HttpStatusCode.NotFound, ApiResponse<Nothing>(false, "Workout not found", null))
        else
            call.respond(ApiResponse(success = true, message = "OK", data = detail))
    }

    // ── GET /api/exercises?muscle_group=&limit= ──────────────────────────────
    get("/exercises") {
        val muscleGroup = call.request.queryParameters["muscle_group"]
        val limit       = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100

        val exercises = dbQuery {
            var query = Exercises.selectAll()
            if (!muscleGroup.isNullOrBlank())
                query = query.andWhere { Exercises.muscleGroup like "%$muscleGroup%" }
            query.limit(limit).map { row ->
                val muscle = row.getOrNull(Exercises.muscleGroup) ?: ""
                ExerciseDto(
                    exerciseId      = row[Exercises.exerciseId],
                    exerciseName    = row[Exercises.exerciseName],
                    description     = row[Exercises.description],
                    equipmentNeeded = row.getOrNull(Exercises.equipmentNeeded) ?: "None",
                    muscleGroup     = muscle,
                    imageUrl        = row.getOrNull(Exercises.imageUrl) ?: exerciseImageUrl(muscle)
                )
            }
        }
        call.respond(ApiResponse(success = true, message = "OK", data = exercises))
    }

    // ── GET /api/exercises/{id} ──────────────────────────────────────────────
    get("/exercises/{id}") {
        val exerciseId = call.parameters["id"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid exercise id", null))

        val exercise = dbQuery {
            Exercises.select { Exercises.exerciseId eq exerciseId }.firstOrNull()?.let { row ->
                val muscle = row.getOrNull(Exercises.muscleGroup) ?: ""
                ExerciseDto(
                    exerciseId      = row[Exercises.exerciseId],
                    exerciseName    = row[Exercises.exerciseName],
                    description     = row[Exercises.description],
                    equipmentNeeded = row.getOrNull(Exercises.equipmentNeeded) ?: "None",
                    muscleGroup     = muscle,
                    imageUrl        = row.getOrNull(Exercises.imageUrl) ?: exerciseImageUrl(muscle)
                )
            }
        }

        if (exercise == null)
            call.respond(HttpStatusCode.NotFound, ApiResponse<Nothing>(false, "Exercise not found", null))
        else
            call.respond(ApiResponse(success = true, message = "OK", data = exercise))
    }
}
