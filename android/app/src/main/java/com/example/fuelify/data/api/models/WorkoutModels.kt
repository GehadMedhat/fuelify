package com.example.fuelify.data.api.models

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class WorkoutItem(
    @SerializedName("workoutId")              val workoutId: Int,
    @SerializedName("workoutName")            val workoutName: String,
    @SerializedName("category")               val category: String,
    @SerializedName("difficulty")             val difficulty: String,
    @SerializedName("durationMinutes")        val durationMinutes: Int,
    @SerializedName("isPremium")              val isPremium: Boolean,
    @SerializedName("equipment")              val equipment: String,
    @SerializedName("imageUrl")               val imageUrl: String,
    @SerializedName("caloriesBurnedEstimate") val caloriesBurnedEstimate: Int,
    @SerializedName("exerciseCount")          val exerciseCount: Int
)

data class ExerciseItem(
    @SerializedName("exerciseId")      val exerciseId: Int,
    @SerializedName("exerciseName")    val exerciseName: String,
    @SerializedName("description")     val description: String,
    @SerializedName("equipmentNeeded") val equipmentNeeded: String,
    @SerializedName("muscleGroup")     val muscleGroup: String,
    @SerializedName("imageUrl")        val imageUrl: String
)

data class WorkoutExerciseItem(
    @SerializedName("exerciseId")      val exerciseId: Int,
    @SerializedName("exerciseName")    val exerciseName: String,
    @SerializedName("description")     val description: String,
    @SerializedName("muscleGroup")     val muscleGroup: String,
    @SerializedName("equipmentNeeded") val equipmentNeeded: String,
    @SerializedName("imageUrl")        val imageUrl: String,
    @SerializedName("reps")            val reps: Int,
    @SerializedName("sets")            val sets: Int,
    @SerializedName("restSeconds")     val restSeconds: Int
) : Serializable

// ── Session models ────────────────────────────────────────────────────────────

data class SaveWorkoutSessionRequest(
    @SerializedName("workoutId")       val workoutId: Int?,
    @SerializedName("workoutName")     val workoutName: String,
    @SerializedName("durationSeconds") val durationSeconds: Int,
    @SerializedName("caloriesBurned")  val caloriesBurned: Int,
    @SerializedName("exercisesDone")   val exercisesDone: Int,
    @SerializedName("isCustom")        val isCustom: Boolean = false,
    @SerializedName("notes")           val notes: String = ""
)

data class WorkoutDetail(
    @SerializedName("workoutId")              val workoutId: Int,
    @SerializedName("workoutName")            val workoutName: String,
    @SerializedName("category")               val category: String,
    @SerializedName("difficulty")             val difficulty: String,
    @SerializedName("durationMinutes")        val durationMinutes: Int,
    @SerializedName("isPremium")              val isPremium: Boolean,
    @SerializedName("equipment")              val equipment: String,
    @SerializedName("imageUrl")               val imageUrl: String,
    @SerializedName("caloriesBurnedEstimate") val caloriesBurnedEstimate: Int,
    @SerializedName("exercises")              val exercises: List<WorkoutExerciseItem>
)

data class CategoryItem(
    @SerializedName("category")     val category: String,
    @SerializedName("workoutCount") val workoutCount: Int,
    @SerializedName("emoji")        val emoji: String,
    @SerializedName("color")        val color: String
)

// Response wrappers
data class WorkoutListResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data")    val data: List<WorkoutItem>?
)

data class WorkoutDetailResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data")    val data: WorkoutDetail?
)

data class ExerciseListResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data")    val data: List<ExerciseItem>?
)

data class ExerciseDetailResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data")    val data: ExerciseItem?
)

data class CategoryListResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data")    val data: List<CategoryItem>?
)

data class SuggestedWorkoutResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data")    val data: WorkoutItem?
)

data class SuggestedWorkoutsData(
    @SerializedName("workouts")     val workouts:     List<WorkoutItem>,
    @SerializedName("reason")       val reason:       String,
    @SerializedName("basedOn")      val basedOn:      String,
    @SerializedName("exerciseDays") val exerciseDays: Int = 4
)

data class SuggestedWorkoutsResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data")    val data:    SuggestedWorkoutsData?
)

data class SaveWorkoutPlanRequest(
    @SerializedName("workoutId")     val workoutId: Int,
    @SerializedName("workoutName")   val workoutName: String,
    @SerializedName("scheduledDate") val scheduledDate: String   // "2026-03-25"
)

data class WorkoutProgress(
    @SerializedName("todayDone")       val todayDone:       Boolean,
    @SerializedName("todaySessions")   val todaySessions:   Int,
    @SerializedName("todayCalories")   val todayCalories:   Int,
    @SerializedName("todayMinutes")    val todayMinutes:    Int,
    @SerializedName("weekSessions")    val weekSessions:    Int,
    @SerializedName("weekCalories")    val weekCalories:    Int,
    @SerializedName("weekMinutes")     val weekMinutes:     Int,
    @SerializedName("weekProgressPct") val weekProgressPct: Int,
    @SerializedName("todayProgressPct") val todayProgressPct: Int,
    @SerializedName("lastWorkoutName") val lastWorkoutName: String
)