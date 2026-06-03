package com.example.fuelify.models

import kotlin.math.ceil

/**
 * Pure (non-suspend, no DB) functions extracted from WorkoutRoutes.buildPlanParams.
 *
 * SETUP INSTRUCTIONS:
 * 1. Create this file at:
 *    backend/src/main/kotlin/com/example/fuelify/models/WorkoutPlanEngine.kt
 *
 * 2. In WorkoutRoutes.kt, replace the body of buildPlanParams() with:
 *       return WorkoutPlanEngine.buildPlanParams(goal, fitnessLevel, trainingPlace,
 *           activityLevel, exerciseDays, weightKg, heightCm, age, gender, motivation)
 *
 *    This keeps the route working exactly as before, but makes the logic testable.
 */
object WorkoutPlanEngine {

    data class WorkoutSuggestionParams(
        val categories:         List<String>,
        val difficulty:         String,
        val maxDurationMinutes: Int,
        val reason:             String,
        val sessionsPerDay:     Int,
        val sessionLabels:      List<String>
    )

    fun buildPlanParams(
        goal:          String?,
        fitnessLevel:  String?,
        trainingPlace: String?,
        activityLevel: String?,
        exerciseDays:  Int?,
        weightKg:      Int?,
        heightCm:      Int?,
        age:           Int?,
        gender:        String?,
        motivation:    String?
    ): WorkoutSuggestionParams {
        val g    = goal?.lowercase()          ?: ""
        val fp   = trainingPlace?.lowercase() ?: ""
        val fl   = fitnessLevel?.lowercase()  ?: ""
        val al   = activityLevel?.lowercase() ?: ""
        val days = exerciseDays ?: 4

        // ── Categories ────────────────────────────────────────────────────────
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
            else -> when {
                g.contains("muscle") || g.contains("gain") ->
                    listOf("Gym", "Upper Body", "Personal Training")
                g.contains("lose") || g.contains("fat") ->
                    listOf("Running", "Boxing", "Gym")
                else -> listOf("Gym", "Running", "Upper Body")
            }
        }

        // ── Difficulty ────────────────────────────────────────────────────────
        val difficulty = when {
            fl.contains("beginner") || fl.contains("irregular") -> "Beginner"
            fl.contains("advanced") || fl.contains("athlete")   -> "Advanced"
            else -> "Medium"
        }

        // ── Sessions per day via BMR/TDEE ─────────────────────────────────────
        val wKg  = (weightKg ?: 70).toDouble()
        val hCm  = (heightCm ?: 170).toDouble()
        val aYrs = (age ?: 25).toDouble()
        val bmr = if (gender?.lowercase() == "female")
            10.0 * wKg + 6.25 * hCm - 5.0 * aYrs - 161
        else
            10.0 * wKg + 6.25 * hCm - 5.0 * aYrs + 5

        val tdee = bmr * when {
            al.contains("sedentary") -> 1.2
            al.contains("light")     -> 1.375
            al.contains("moderate")  -> 1.55
            al.contains("very")      -> 1.725
            al.contains("extra") || al.contains("athlete") -> 1.9
            else -> 1.375
        }

        val dailyBurnTarget = (tdee * when {
            g.contains("lose") || g.contains("fat")    -> 0.30
            g.contains("muscle") || g.contains("gain") -> 0.20
            else -> 0.22
        }).toInt().coerceAtLeast(200)

        val avgCalPerSession = when {
            categories.any { it in listOf("Running", "Boxing") } -> 350
            categories.any { it in listOf("Yoga", "Stretch") }   -> 180
            else -> 320
        }

        val sessionsPerDay = ceil(dailyBurnTarget.toDouble() / avgCalPerSession.toDouble())
            .toInt()
            .coerceIn(1, 3)

        // ── Session labels ────────────────────────────────────────────────────
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

    // ── Suggested workout count (from WorkoutSessionRoutes) ───────────────────
    fun suggestedCount(exerciseDays: Int): Int = when {
        exerciseDays >= 6 -> 4
        exerciseDays >= 4 -> 3
        else              -> 2
    }

    // ── Workout day offsets (from buildWeeklyPlan) ────────────────────────────
    fun workoutDayOffsets(exerciseDays: Int): List<Int> = when (exerciseDays) {
        1    -> listOf(0)
        2    -> listOf(0, 3)
        3    -> listOf(0, 2, 4)
        4    -> listOf(0, 1, 3, 4)
        5    -> listOf(0, 1, 2, 3, 4)
        6    -> listOf(0, 1, 2, 3, 4, 5)
        else -> listOf(0, 1, 2, 3, 4, 5, 6)
    }
}
