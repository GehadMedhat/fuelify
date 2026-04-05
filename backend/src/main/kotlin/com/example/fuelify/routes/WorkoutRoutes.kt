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

data class WorkoutSuggestionParams(
    val categories: List<String>,
    val difficulty: String,
    val maxDurationMinutes: Int,
    val reason: String
)

private fun suggestWorkout(
    goal: String?,
    fitnessLevel: String?,
    trainingPlace: String?,
    activityLevel: String?,
    exerciseDays: Int?
): WorkoutSuggestionParams {

    // Categories based on goal
    val goalCategories = when {
        goal?.contains("build muscle", true) == true ||
        goal?.contains("gain", true) == true ->
            listOf("Gym", "Upper Body", "Personal Training")
        goal?.contains("lose", true) == true ||
        goal?.contains("weight", true) == true ->
            listOf("Running", "Boxing", "Gym")
        goal?.contains("maintain", true) == true ->
            listOf("Yoga", "Running", "Upper Body", "Stretch")
        else -> listOf("Gym", "Running", "Yoga")
    }

    // Filter categories by training place
    val placeFilteredCategories = when {
        trainingPlace?.contains("home", true) == true ->
            goalCategories.filter { it in listOf("Yoga", "Stretch", "Upper Body", "Running") }
                .ifEmpty { listOf("Yoga", "Stretch", "Upper Body") }
        trainingPlace?.contains("gym", true) == true ->
            goalCategories.filter { it in listOf("Gym", "Upper Body", "Personal Training", "Boxing") }
                .ifEmpty { listOf("Gym", "Upper Body") }
        else -> goalCategories  // Hybrid or unknown — keep all
    }

    // Difficulty from fitness level
    val difficulty = when {
        fitnessLevel?.contains("beginner", true) == true ||
        fitnessLevel?.contains("irregular", true) == true -> "Beginner"
        fitnessLevel?.contains("advanced", true) == true ||
        fitnessLevel?.contains("athlete", true) == true   -> "Advanced"
        else -> "Medium"
    }

    // Max duration based on exercise days (more days = shorter sessions)
    val maxDuration = when {
        exerciseDays != null && exerciseDays >= 5 -> 45   // high frequency = shorter
        exerciseDays != null && exerciseDays >= 3 -> 60
        else -> 75                                        // fewer days = longer sessions
    }

    // Reason string for UI
    val reason = buildString {
        if (!goal.isNullOrBlank()) append("Based on your goal: $goal")
        if (!trainingPlace.isNullOrBlank()) append(" · ${trainingPlace} training")
        if (exerciseDays != null) append(" · ${exerciseDays} days/week")
    }

    return WorkoutSuggestionParams(
        categories = placeFilteredCategories,
        difficulty = difficulty,
        maxDurationMinutes = maxDuration,
        reason = reason.ifBlank { "Personalized for you" }
    )
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
    // Returns personalized workouts that ROTATE DAILY based on dayOfYear seed
    // Also auto-populates workout_plan for the full week on first call
    get("/workouts/suggested/{userId}") {
        val userId = call.parameters["userId"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user id", null))

        val user = dbQuery {
            WorkoutUsers.select { WorkoutUsers.id eq userId }.firstOrNull()
        }

        val goal          = user?.getOrNull(WorkoutUsers.goal)          ?: ""
        val fitnessLevel  = user?.getOrNull(WorkoutUsers.fitnessLevel)  ?: ""
        val trainingPlace = user?.getOrNull(WorkoutUsers.trainingPlace) ?: ""
        val activityLevel = user?.getOrNull(WorkoutUsers.activityLevel) ?: ""
        val exerciseDays  = user?.getOrNull(WorkoutUsers.exerciseDays)  ?: 4

        val params = suggestWorkout(goal, fitnessLevel, trainingPlace, activityLevel, exerciseDays)

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

        // Auto-populate workout_plan for the full week if it's empty
        dbQuery {
            val weekStart = today.minusDays(today.dayOfWeek.value.toLong() - 1)
            val weekEnd   = weekStart.plusDays(6)

            val existingPlanCount = WorkoutPlan.select {
                (WorkoutPlan.userId eq userId) and
                (WorkoutPlan.scheduledDate greaterEq weekStart) and
                (WorkoutPlan.scheduledDate lessEq weekEnd)
            }.count()

            if (existingPlanCount == 0L) {
                // Assign workouts across the user's exercise days this week
                // Spread exerciseDays sessions evenly: e.g. 5 days → Mon,Tue,Wed,Thu,Fri
                val workoutDayIndices = when (exerciseDays) {
                    1    -> listOf(0)                          // Monday
                    2    -> listOf(0, 3)                       // Mon, Thu
                    3    -> listOf(0, 2, 4)                    // Mon, Wed, Fri
                    4    -> listOf(0, 1, 3, 4)                 // Mon, Tue, Thu, Fri
                    5    -> listOf(0, 1, 2, 3, 4)              // Mon–Fri
                    6    -> listOf(0, 1, 2, 3, 4, 5)           // Mon–Sat
                    else -> listOf(0, 1, 2, 3, 4, 5, 6)        // every day
                }

                workoutDayIndices.forEachIndexed { idx, dayOffset ->
                    val planDate = weekStart.plusDays(dayOffset.toLong())
                    val workout  = allMatchingWorkouts.shuffled(
                        kotlin.random.Random((daySeed + dayOffset).toLong())
                    ).firstOrNull() ?: return@forEachIndexed

                    // Avoid duplicates on same date
                    val alreadyExists = WorkoutPlan.select {
                        (WorkoutPlan.userId eq userId) and
                        (WorkoutPlan.scheduledDate eq planDate)
                    }.count() > 0L

                    if (!alreadyExists) {
                        WorkoutPlan.insert {
                            it[WorkoutPlan.userId]        = userId
                            it[WorkoutPlan.workoutId]     = workout[Workouts.workoutId]
                            it[WorkoutPlan.workoutName]   = workout[Workouts.workoutName]
                            it[WorkoutPlan.scheduledDate] = planDate
                            it[WorkoutPlan.status]        = "planned"
                        }
                    }
                }
            }
        }

        @kotlinx.serialization.Serializable
        data class SuggestedWorkoutsDto(
            val workouts: List<WorkoutDto>,
            val reason: String,
            val basedOn: String,
            val exerciseDays: Int
        )

        call.respond(ApiResponse(success = true, message = "OK", data = SuggestedWorkoutsDto(
            workouts    = workouts,
            reason      = params.reason,
            basedOn     = "goal=${goal}, place=${trainingPlace}, days=${exerciseDays}, level=${fitnessLevel}",
            exerciseDays = exerciseDays
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
            WorkoutUsers.select { WorkoutUsers.id eq userId }.firstOrNull()
        }
        val goal          = user?.getOrNull(WorkoutUsers.goal)          ?: ""
        val fitnessLevel  = user?.getOrNull(WorkoutUsers.fitnessLevel)  ?: ""
        val trainingPlace = user?.getOrNull(WorkoutUsers.trainingPlace) ?: ""
        val activityLevel = user?.getOrNull(WorkoutUsers.activityLevel) ?: ""
        val exerciseDays  = user?.getOrNull(WorkoutUsers.exerciseDays)  ?: 4

        val params = suggestWorkout(goal, fitnessLevel, trainingPlace, activityLevel, exerciseDays)

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
