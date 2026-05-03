package com.example.fuelify.models

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object Users : Table("users") {
    val id             = integer("id").autoIncrement()

    val email          = varchar("email", 255).uniqueIndex()
    val username       = varchar("username", 100).nullable()
    val passwordHash   = varchar("password_hash", 255).nullable()
    val isVerified     = bool("is_verified").default(false)
    val isActive       = bool("is_active").default(true)
    val firstName      = varchar("first_name", 100).nullable()
    val lastName       = varchar("last_name", 100).nullable()
    val profilePicture = varchar("profile_picture", 500).nullable()
    val isAdmin        = bool("is_admin").default(false)

    val name           = varchar("name", 100).default("")
    val gender         = varchar("gender", 20).default("")
    val age            = integer("age").default(0)
    val heightCm       = integer("height_cm").default(0)
    val weightKg       = integer("weight_kg").default(0)
    val goal           = varchar("goal", 100).default("")
    val activityLevel  = varchar("activity_level", 100).default("")
    val motivation     = varchar("motivation", 255).default("")
    val fitnessLevel   = varchar("fitness_level", 50).default("")
    val exerciseDays   = integer("exercise_days").default(0)
    val trainingPlace  = varchar("training_place", 50).default("")
    val mealsPerDay    = integer("meals_per_day").default(3)
    val likedFoods     = text("liked_foods").default("[]")
    val allergies      = text("allergies").default("[]")
    val budget         = varchar("budget", 50).default("")
    val profileComplete = bool("profile_complete").default(false)
    val onboardingStep = integer("onboarding_step").default(0)

    val createdAt      = datetime("created_at").nullable()
    val updatedAt      = datetime("updated_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

data class User(
    val id: Int,
    val name: String,
    val gender: String,
    val age: Int,
    val heightCm: Int,
    val weightKg: Int,
    val goal: String,
    val activityLevel: String,
    val motivation: String,
    val fitnessLevel: String,
    val exerciseDays: Int,
    val trainingPlace: String,
    val mealsPerDay: Int,
    val likedFoods: List<String>,
    val allergies: List<String>,
    val budget: String,
    val profileComplete: Boolean
)
