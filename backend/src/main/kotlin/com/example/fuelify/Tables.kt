package com.example.fuelify.models

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.datetime

object Meals : Table("meal") {
    val mealId          = integer("meal_id")
    val mealName        = varchar("meal_name", 200)
    val calories        = integer("calories")
    val protein         = double("protein")
    val carbs           = double("carbs")
    val fat             = double("fat")
    val ecoScore        = double("eco_score")
    val hasVideo        = bool("has_video")
    val mealCategory    = varchar("meal_category", 50)
    val imageUrl        = text("image_url").nullable()
    val prepTimeMinutes = integer("prep_time_minutes")
    val difficulty      = varchar("difficulty", 20)
    val mealTime        = varchar("meal_time", 20)
    val dietType        = varchar("diet_type", 30)
    val price           = double("price")
    val isAvailable     = bool("is_available")
    override val primaryKey = PrimaryKey(mealId)
}

object AllergyTypes : Table("allergy_type") {
    val allergyTypeId = integer("allergy_type_id")
    val name          = varchar("name", 50)
    override val primaryKey = PrimaryKey(allergyTypeId)
}

object UserAllergyTypes : Table("user_allergy_type") {
    val id            = integer("id")
    val userId        = integer("user_id")
    val allergyTypeId = integer("allergy_type_id")
    override val primaryKey = PrimaryKey(id)
}

object DailyLogs : Table("daily_logs") {
    val id             = integer("id").autoIncrement()
    val userId         = integer("user_id").references(Users.id)
    val logDate        = date("log_date")
    val caloriesEaten  = integer("calories_eaten").default(0)
    val waterGlasses   = integer("water_glasses").default(0)
    val workoutsDone   = integer("workouts_done").default(0)
    val workoutsGoal   = integer("workouts_goal").default(1)
    val streakDays     = integer("streak_days").default(0)
    val updatedAt      = datetime("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object MealPlans : Table("meal_plans") {
    val id              = integer("id").autoIncrement()
    val userId          = integer("user_id").references(Users.id)
    val planDate        = date("plan_date")
    val mealId          = integer("meal_id")
    val mealType        = varchar("meal_type", 20)
    val scheduledTime   = varchar("scheduled_time", 10).default("")
    val isCompleted     = bool("is_completed").default(false)
    val scaledCalories  = integer("scaled_calories").default(0)  // ← stores portion-adjusted calories
    override val primaryKey = PrimaryKey(id)
}
