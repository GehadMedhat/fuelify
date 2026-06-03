package com.example.fuelify.routes

import com.example.fuelify.db.DatabaseFactory.dbQuery
import com.example.fuelify.models.Users
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime

// ─── Request / Response DTOs ──────────────────────────────────────────────────

@Serializable
data class RegisterUserRequest(
    val name: String,           // full name from onboarding — stored in `name` column
    val gender: String,
    val age: Int,
    val height_cm: Int,
    val weight_kg: Int,
    val goal: String,
    val activity_level: String,
    val motivation: String,
    val fitness_level: String,
    val exercise_days: Int,
    val training_place: String,
    val meals_per_day: Int,
    val liked_foods: List<String>,
    val allergies: List<String>,
    val budget: String,
    // optional new-schema fields (sent from new app version)
    val email: String = "",
    val password: String = ""
)

@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val message: String,
    val data: T?
)

@Serializable
data class UserData(
    val user_id: Int,
    val name: String,
    val profile_complete: Boolean
)

// ─── Routes ───────────────────────────────────────────────────────────────────

fun Route.userRoutes() {

    // ── POST /api/users/register ──────────────────────────────────────────────
    post("/users/register") {
        val req = call.receive<RegisterUserRequest>()

        // Split name into first/last for new schema
        val nameParts = req.name.trim().split(" ", limit = 2)
        val firstName = nameParts.getOrElse(0) { req.name }
        val lastName  = nameParts.getOrElse(1) { "" }

        val userId = dbQuery {
            val result = Users.insert {
                it[Users.name]           = req.name
                it[Users.firstName]      = firstName
                it[Users.lastName]       = lastName
                it[Users.gender]         = req.gender
                it[Users.age]            = req.age
                it[Users.heightCm]       = req.height_cm
                it[Users.weightKg]       = req.weight_kg
                it[Users.goal]           = req.goal
                it[Users.activityLevel]  = req.activity_level
                it[Users.motivation]     = req.motivation
                it[Users.fitnessLevel]   = req.fitness_level
                it[Users.exerciseDays]   = req.exercise_days
                it[Users.trainingPlace]  = req.training_place
                it[Users.mealsPerDay]    = req.meals_per_day
                it[Users.likedFoods]     = Json.encodeToString(req.liked_foods)
                it[Users.allergies]      = Json.encodeToString(req.allergies)
                it[Users.budget]         = req.budget
                it[Users.profileComplete] = true
                it[Users.onboardingStep] = 15
                it[Users.isActive]       = true
                it[Users.isVerified]     = true
                // email if provided
                if (req.email.isNotBlank()) {
                    it[Users.email]      = req.email
                    it[Users.username]   = req.email.substringBefore("@")
                } else {
                    // generate placeholder email so NOT NULL is satisfied
                    it[Users.email]      = "user_${System.currentTimeMillis()}@fuelify.app"
                    it[Users.username]   = firstName.lowercase()
                }
                it[Users.createdAt]      = LocalDateTime.now()
                it[Users.updatedAt]      = LocalDateTime.now()
            }
            result[Users.id]
        }

        call.respond(
            HttpStatusCode.Created,
            ApiResponse(
                success = true,
                message = "User registered successfully",
                data = UserData(user_id = userId, name = req.name, profile_complete = true)
            )
        )
    }

    // ── GET /api/users/{id} ───────────────────────────────────────────────────
    get("/users/{id}") {
        val id = call.parameters["id"]?.toIntOrNull()
            ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user ID", null)
            )

        val row = dbQuery { Users.select { Users.id eq id }.firstOrNull() }

        if (row == null) {
            call.respond(HttpStatusCode.NotFound, ApiResponse<Nothing>(false, "User not found", null))
        } else {
            // Prefer stored name; fall back to first+last
            val displayName = row[Users.name].ifBlank {
                listOfNotNull(row.getOrNull(Users.firstName), row.getOrNull(Users.lastName))
                    .joinToString(" ").ifBlank { "User" }
            }
            call.respond(
                ApiResponse(
                    success = true,
                    message = "OK",
                    data = UserData(
                        user_id = row[Users.id],
                        name = displayName,
                        profile_complete = row[Users.profileComplete]
                    )
                )
            )
        }
    }
    
    
// GET /api/users/by-email?email=...
get("/users/by-email") {
    val email = call.request.queryParameters["email"]
        ?: return@get call.respond(
            HttpStatusCode.BadRequest,
            ApiResponse<Nothing>(false, "Email required", null)
        )

    val row = dbQuery { Users.select { Users.email eq email }.firstOrNull() }

    if (row == null) {
        call.respond(
            ApiResponse(success = true, message = "Not found",
                data = UserData(user_id = -1, name = "", profile_complete = false))
        )
    } else {
        call.respond(
            ApiResponse(
                success = true, message = "OK",
                data = UserData(
                    user_id = row[Users.id],
                    name = row[Users.name],
                    profile_complete = row[Users.profileComplete]
                )
            )
        )
    }
}

put("/users/{id}/onboarding") {
    val id = call.parameters["id"]?.toIntOrNull()
        ?: return@put call.respond(
            HttpStatusCode.BadRequest,
            ApiResponse<Nothing>(false, "Invalid user ID", null)
        )

    val req = call.receive<RegisterUserRequest>()
    val nameParts = req.name.trim().split(" ", limit = 2)

    // Check if user exists — if not, insert; if yes, update
    val existing = dbQuery { Users.select { Users.id eq id }.firstOrNull() }

    if (existing == null) {
        // First time: insert with the known id from auth backend
        dbQuery {
            Users.insert {
                it[Users.id]             = id
                it[Users.name]           = req.name
                it[Users.firstName]      = nameParts.getOrElse(0) { req.name }
                it[Users.lastName]       = nameParts.getOrElse(1) { "" }
                it[Users.email]          = req.email.ifBlank { "user_${id}@fuelify.app" }
                it[Users.username]       = req.email.substringBefore("@").ifBlank { req.name.lowercase() }
                it[Users.gender]         = req.gender
                it[Users.age]            = req.age
                it[Users.heightCm]       = req.height_cm
                it[Users.weightKg]       = req.weight_kg
                it[Users.goal]           = req.goal
                it[Users.activityLevel]  = req.activity_level
                it[Users.motivation]     = req.motivation
                it[Users.fitnessLevel]   = req.fitness_level
                it[Users.exerciseDays]   = req.exercise_days
                it[Users.trainingPlace]  = req.training_place
                it[Users.mealsPerDay]    = req.meals_per_day
                it[Users.likedFoods]     = Json.encodeToString(req.liked_foods)
                it[Users.allergies]      = Json.encodeToString(req.allergies)
                it[Users.budget]         = req.budget
                it[Users.profileComplete] = true
                it[Users.onboardingStep] = 15
                it[Users.isActive]       = true
                it[Users.isVerified]     = true
                it[Users.createdAt]      = LocalDateTime.now()
                it[Users.updatedAt]      = LocalDateTime.now()
            }
        }
    } else {
        // Already exists: just update onboarding fields
        dbQuery {
            Users.update({ Users.id eq id }) {
                it[Users.name]           = req.name
                it[Users.firstName]      = nameParts.getOrElse(0) { req.name }
                it[Users.lastName]       = nameParts.getOrElse(1) { "" }
                it[Users.gender]         = req.gender
                it[Users.age]            = req.age
                it[Users.heightCm]       = req.height_cm
                it[Users.weightKg]       = req.weight_kg
                it[Users.goal]           = req.goal
                it[Users.activityLevel]  = req.activity_level
                it[Users.motivation]     = req.motivation
                it[Users.fitnessLevel]   = req.fitness_level
                it[Users.exerciseDays]   = req.exercise_days
                it[Users.trainingPlace]  = req.training_place
                it[Users.mealsPerDay]    = req.meals_per_day
                it[Users.likedFoods]     = Json.encodeToString(req.liked_foods)
                it[Users.allergies]      = Json.encodeToString(req.allergies)
                it[Users.budget]         = req.budget
                it[Users.profileComplete] = true
                it[Users.onboardingStep] = 15
                it[Users.updatedAt]      = LocalDateTime.now()
            }
        }
    }

    call.respond(
        ApiResponse(
            success = true,
            message = "Onboarding complete",
            data = UserData(user_id = id, name = req.name, profile_complete = true)
        )
    )
}


    // ── PATCH /api/users/{id} — update onboarding step ───────────────────────
    patch("/users/{id}") {
        val id = call.parameters["id"]?.toIntOrNull()
            ?: return@patch call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user ID", null))

        @Serializable
        data class UpdateStepRequest(val onboarding_step: Int? = null)
        val req = call.receive<UpdateStepRequest>()

        dbQuery {
            Users.update({ Users.id eq id }) {
                req.onboarding_step?.let { step -> it[Users.onboardingStep] = step }
                it[Users.updatedAt] = LocalDateTime.now()
            }
        }
        call.respond(ApiResponse(success = true, message = "Updated", data = null))
    }
}
