package com.example.fuelify.utils

import android.content.Context

/**
 * Thin wrapper around SharedPreferences.
 * Stores the logged-in user's id and basic cached profile so the app
 * can decide at launch whether to show onboarding or go straight home.
 */
object UserPreferences {

    private const val PREF_FILE = "fuelify_prefs"

    // Keys
    private const val KEY_USER_ID      = "user_id"
    private const val KEY_NAME         = "name"
    private const val KEY_GOAL         = "goal"
    private const val KEY_WEIGHT       = "weight_kg"
    private const val KEY_HEIGHT       = "height_cm"
    private const val KEY_AGE          = "age"
    private const val KEY_GENDER       = "gender"
    private const val KEY_ACTIVITY     = "activity_level"
    private const val KEY_MEALS        = "meals_per_day"
    private const val KEY_WORKOUT_DAYS = "exercise_days"
    private const val KEY_CALORIES     = "daily_calories"

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)

    // ── Write ────────────────────────────────────────────────────────────────

    fun saveUserId(ctx: Context, id: Int) =
        prefs(ctx).edit().putInt(KEY_USER_ID, id).apply()

    fun saveProfile(
        ctx: Context,
        name: String,
        goal: String,
        weightKg: Int,
        heightCm: Int,
        age: Int,
        gender: String,
        activityLevel: String,
        mealsPerDay: Int,
        exerciseDays: Int
    ) {
        val calories = calculateDailyCalories(weightKg, heightCm, age, gender, activityLevel, goal)
        prefs(ctx).edit()
            .putString(KEY_NAME, name)
            .putString(KEY_GOAL, goal)
            .putInt(KEY_WEIGHT, weightKg)
            .putInt(KEY_HEIGHT, heightCm)
            .putInt(KEY_AGE, age)
            .putString(KEY_GENDER, gender)
            .putString(KEY_ACTIVITY, activityLevel)
            .putInt(KEY_MEALS, mealsPerDay)
            .putInt(KEY_WORKOUT_DAYS, exerciseDays)
            .putInt(KEY_CALORIES, calories)
            .apply()
    }

    // ── Read ─────────────────────────────────────────────────────────────────

    fun getUserId(ctx: Context): Int = prefs(ctx).getInt(KEY_USER_ID, -1)
    fun isLoggedIn(ctx: Context): Boolean = getUserId(ctx) != -1

    fun getName(ctx: Context): String = prefs(ctx).getString(KEY_NAME, "User") ?: "User"
    fun getGoal(ctx: Context): String = prefs(ctx).getString(KEY_GOAL, "") ?: ""
    fun getWeightKg(ctx: Context): Int = prefs(ctx).getInt(KEY_WEIGHT, 70)
    fun getHeightCm(ctx: Context): Int = prefs(ctx).getInt(KEY_HEIGHT, 170)
    fun getAge(ctx: Context): Int = prefs(ctx).getInt(KEY_AGE, 25)
    fun getGender(ctx: Context): String = prefs(ctx).getString(KEY_GENDER, "male") ?: "male"
    fun getActivityLevel(ctx: Context): String = prefs(ctx).getString(KEY_ACTIVITY, "") ?: ""
    fun getMealsPerDay(ctx: Context): Int = prefs(ctx).getInt(KEY_MEALS, 3)
    fun getExerciseDays(ctx: Context): Int = prefs(ctx).getInt(KEY_WORKOUT_DAYS, 3)
    fun getDailyCalories(ctx: Context): Int = prefs(ctx).getInt(KEY_CALORIES, 2000)

    // ── Clear (logout) ───────────────────────────────────────────────────────

    fun clear(ctx: Context) = prefs(ctx).edit().clear().apply()

    fun saveWeekTotal(ctx: Context, total: Int) =
        ctx.getSharedPreferences("fuelify_prefs", Context.MODE_PRIVATE)
            .edit().putInt("week_total", total).apply()

    fun getWeekTotal(ctx: Context): Int =
        ctx.getSharedPreferences("fuelify_prefs", Context.MODE_PRIVATE)
            .getInt("week_total", 4)  // default 4

    fun saveSessionsPerDay(ctx: Context, count: Int) =
        ctx.getSharedPreferences("fuelify_prefs", Context.MODE_PRIVATE)
            .edit().putInt("sessions_per_day", count).apply()

    fun getSessionsPerDay(ctx: Context): Int =
        ctx.getSharedPreferences("fuelify_prefs", Context.MODE_PRIVATE)
            .getInt("sessions_per_day", 1)


    // ── BMR + TDEE Calculation (Mifflin-St Jeor) ─────────────────────────────

    fun calculateDailyCalories(
        weightKg: Int,
        heightCm: Int,
        age: Int,
        gender: String,
        activityLevel: String,
        goal: String
    ): Int {
        // BMR
        val bmr = if (gender.lowercase() == "female") {
            (10 * weightKg) + (6.25 * heightCm) - (5 * age) - 161
        } else {
            (10 * weightKg) + (6.25 * heightCm) - (5 * age) + 5
        }

        // Activity multiplier
        val multiplier = when (activityLevel.lowercase()) {
            "sedentary"         -> 1.2
            "lightly active"    -> 1.375
            "moderately active" -> 1.55
            "very active"       -> 1.725
            "extra active"      -> 1.9
            else                -> 1.55
        }

        val tdee = bmr * multiplier

        // Goal adjustment
        return when (goal.lowercase()) {
            "lose weight"   -> (tdee - 500).toInt()
            "gain muscle"   -> (tdee + 300).toInt()
            "get fit"       -> tdee.toInt()
            else            -> tdee.toInt()
        }.coerceAtLeast(1200) // never below 1200
    }
    
         fun getAllergies(context: Context): String =
    context.getSharedPreferences("fuelify_prefs", Context.MODE_PRIVATE)
        .getString("allergies", "") ?: ""


}
