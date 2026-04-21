package com.example.fuelify.routes

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.datetime

object WorkoutPlan : Table("workout_plan") {
    val planId        = integer("plan_id").autoIncrement()
    val userId        = integer("user_id")
    val workoutId     = integer("workout_id").nullable()
    val workoutName   = varchar("workout_name", 255).nullable()
    val scheduledDate = date("scheduled_date").nullable()
    val status        = varchar("status", 20).default("planned")
    val sessionNumber = integer("session_number").default(1)    // 1, 2, 3... per day
    val sessionLabel  = varchar("session_label", 50).default("") // "Upper Body", "Cardio"
    override val primaryKey = PrimaryKey(planId)
}

object WorkoutPlanUsers : Table("users") {
    val id             = integer("id")
    val goal           = varchar("goal", 100).nullable()
    val fitnessLevel   = varchar("fitness_level", 50).nullable()
    val trainingPlace  = varchar("training_place", 50).nullable()
    val activityLevel  = varchar("activity_level", 100).nullable()
    val exerciseDays   = integer("exercise_days").nullable()
    val weightKg       = integer("weight_kg").nullable()
    val heightCm       = integer("height_cm").nullable()
    val age            = integer("age").nullable()
    val gender         = varchar("gender", 20).nullable()
    val motivation     = varchar("motivation", 255).nullable()
    override val primaryKey = PrimaryKey(id)
}

