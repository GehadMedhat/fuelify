package com.example.fuelify.data.api.models

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class ApiResponse<T>(
    val success: Boolean,
    val message: String,
    val data: T?
)

data class RegisterUserRequest(
    val name: String,
    val gender: String,
    val age: Int,
    @SerializedName("height_cm")      val heightCm: Int,
    @SerializedName("weight_kg")      val weightKg: Int,
    val goal: String,
    @SerializedName("activity_level") val activityLevel: String,
    val motivation: String,
    @SerializedName("fitness_level")  val fitnessLevel: String,
    @SerializedName("exercise_days")  val exerciseDays: Int,
    @SerializedName("training_place") val trainingPlace: String,
    @SerializedName("meals_per_day")  val mealsPerDay: Int,
    @SerializedName("liked_foods")    val likedFoods: List<String>,
    val allergies: List<String>,
    val budget: String
)

data class UserResponse(
    @SerializedName("user_id")          val userId: Int,
    val name: String,
    val email: String?,
    @SerializedName("profile_complete") val profileComplete: Boolean
)

// Serializable so it can be passed between Activities via Intent
data class MealItem(
    val id: Int,
    @SerializedName("meal_id")           val mealId: Int,
    @SerializedName("meal_type")         val mealType: String,
    @SerializedName("meal_name")         val mealName: String,
    val calories: Int,
    @SerializedName("protein_g")         val proteinG: Int,
    @SerializedName("carbs_g")           val carbsG: Int,
    @SerializedName("fat_g")             val fatG: Int,
    @SerializedName("eco_score")         val ecoScore: Double,
    @SerializedName("has_video")         val hasVideo: Boolean,
    @SerializedName("image_url")         val imageUrl: String,
    val difficulty: String,
    @SerializedName("prep_time_minutes") val prepTimeMinutes: Int,
    @SerializedName("scheduled_time")    val scheduledTime: String,
    @SerializedName("is_completed")      val isCompleted: Boolean
) : Serializable

data class MacroData(
    @SerializedName("protein_g")    val proteinG: Int,
    @SerializedName("protein_goal") val proteinGoal: Int,
    @SerializedName("carbs_g")      val carbsG: Int,
    @SerializedName("carbs_goal")   val carbsGoal: Int,
    @SerializedName("fat_g")        val fatG: Int,
    @SerializedName("fat_goal")     val fatGoal: Int
)

data class DashboardData(
    @SerializedName("user_id")             val userId: Int,
    val name: String,
    @SerializedName("daily_calories_goal") val dailyCaloriesGoal: Int,
    @SerializedName("calories_eaten")      val caloriesEaten: Int,
    @SerializedName("calories_remaining")  val caloriesRemaining: Int,
    val bmi: Double,
    val macros: MacroData,
    @SerializedName("water_glasses")       val waterGlasses: Int,
    @SerializedName("water_goal")          val waterGoal: Int,
    @SerializedName("workouts_done")       val workoutsDone: Int,
    @SerializedName("workouts_goal")       val workoutsGoal: Int,
    @SerializedName("streak_days")         val streakDays: Int,
    @SerializedName("today_meals")         val todayMeals: List<MealItem>,
    @SerializedName("recommended_meals")   val recommendedMeals: List<MealItem>,
    // Week-level progress
    @SerializedName("week_meals_eaten")    val weekMealsEaten: Int = 0,
    @SerializedName("week_meals_total")    val weekMealsTotal: Int = 0,
    @SerializedName("week_workouts_done")  val weekWorkoutsDone: Int = 0,
    @SerializedName("week_workouts_goal")  val weekWorkoutsGoal: Int = 4,
    @SerializedName("today_workout_name")  val todayWorkoutName: String = "",
    @SerializedName("today_workout_image") val todayWorkoutImage: String = "",
    @SerializedName("today_workout_id")    val todayWorkoutId:    Int    = 0
)

data class LogWaterRequest(val glasses: Int)
data class LogMealRequest(@SerializedName("meal_id") val mealId: Int)

// ─── Meal Detail ──────────────────────────────────────────────────────────────

data class IngredientItem(
    val name: String,
    val quantity: String
) : Serializable

data class MealDetailData(
    @SerializedName("meal_id")               val mealId: Int,
    @SerializedName("meal_name")             val mealName: String,
    @SerializedName("image_url")             val imageUrl: String,
    val calories: Int,
    @SerializedName("protein_g")             val proteinG: Double,
    @SerializedName("carbs_g")              val carbsG: Double,
    @SerializedName("fat_g")               val fatG: Double,
    @SerializedName("eco_score")             val ecoScore: Double,
    val difficulty: String,
    @SerializedName("prep_time_minutes")     val prepTimeMinutes: Int,
    @SerializedName("meal_time")             val mealTime: String,
    @SerializedName("diet_type")             val dietType: String,
    @SerializedName("calorie_quality_score") val calorieQualityScore: Double,
    @SerializedName("eco_grade")             val ecoGrade: String,
    @SerializedName("has_video")             val hasVideo: Boolean,
    @SerializedName("video_url")             val videoUrl: String,
    val instructions: List<String>,
    val ingredients: List<IngredientItem>,
    @SerializedName("fiber_g")               val fiberG: Double,
    @SerializedName("sugar_g")               val sugarG: Double
) : Serializable

// ─── Search / Switch ──────────────────────────────────────────────────────────

data class SearchMealItem(
    @SerializedName("meal_id")            val mealId: Int,
    @SerializedName("meal_name")          val mealName: String,
    @SerializedName("image_url")          val imageUrl: String,
    val calories: Int,
    @SerializedName("prep_time_minutes")  val prepTimeMinutes: Int,
    val difficulty: String,
    @SerializedName("meal_time")          val mealTime: String,
    @SerializedName("diet_type")          val dietType: String,
    @SerializedName("eco_score")          val ecoScore: Double,
    @SerializedName("is_suitable")        val isSuitable: Boolean,
    @SerializedName("suitability_reason") val suitabilityReason: String
) : Serializable

data class SwitchMealRequest(
    @SerializedName("plan_id")    val planId: Int,
    @SerializedName("new_meal_id")val newMealId: Int
)

// ─── Kitchen Order ────────────────────────────────────────────────────────────

data class KitchenOrderRequest(
    @SerializedName("plan_type")    val planType: String,
    @SerializedName("portion_size") val portionSize: String,
    @SerializedName("spice_level")  val spiceLevel: String,
    val notes: String = ""
)

data class KitchenOrderResponse(
    @SerializedName("order_id")    val orderId: Int,
    val status: String,
    @SerializedName("total_price") val totalPrice: Double,
    val message: String
)

// ── Workout Week Plan ─────────────────────────────────────────────────────────

data class WeekPlanEntry(
    @SerializedName("planId")                  val planId:               Int,
    @SerializedName("workoutId")               val workoutId:            Int,
    @SerializedName("workoutName")             val workoutName:          String,
    @SerializedName("scheduledDate")           val scheduledDate:        String,
    @SerializedName("dayLabel")                val dayLabel:             String,
    @SerializedName("dayNumber")               val dayNumber:            Int,
    @SerializedName("isToday")                 val isToday:              Boolean,
    @SerializedName("isPast")                  val isPast:               Boolean,
    @SerializedName("status")                  val status:               String,
    @SerializedName("imageUrl")                val imageUrl:             String,
    @SerializedName("category")                val category:             String,
    @SerializedName("difficulty")              val difficulty:           String,
    @SerializedName("durationMinutes")         val durationMinutes:      Int,
    @SerializedName("caloriesBurnedEstimate")  val caloriesBurnedEstimate: Int
)

data class WeekPlanResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data")    val data:    List<WeekPlanEntry>?
)
