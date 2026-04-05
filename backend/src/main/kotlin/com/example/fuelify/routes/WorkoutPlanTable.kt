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
    override val primaryKey = PrimaryKey(planId)
}

object WorkoutUsers : Table("users") {
    val id            = integer("id")
    val goal          = varchar("goal", 100).nullable()
    val fitnessLevel  = varchar("fitness_level", 50).nullable()
    val trainingPlace = varchar("training_place", 50).nullable()
    val activityLevel = varchar("activity_level", 100).nullable()
    val exerciseDays  = integer("exercise_days").nullable()
    override val primaryKey = PrimaryKey(id)
}
