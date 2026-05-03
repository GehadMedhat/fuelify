package com.example.fuelify.routes

import com.example.fuelify.db.DatabaseFactory.dbQuery
import com.example.fuelify.models.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*

@Serializable
data class SearchMealItem(
    val meal_id: Int,
    val meal_name: String,
    val image_url: String,
    val calories: Int,
    val prep_time_minutes: Int,
    val difficulty: String,
    val meal_time: String,
    val diet_type: String,
    val eco_score: Double,
    val is_suitable: Boolean,      // based on user's allergies + goal
    val suitability_reason: String // why it is/isn't suitable
)

@Serializable
data class SwitchMealRequest(
    val plan_id: Int,           // meal_plans.id to replace
    val new_meal_id: Int        // meal.meal_id to switch to
)

fun Route.mealSearchRoutes() {

    // GET /api/users/{id}/search-meals?q=chicken
    get("/users/{id}/search-meals") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user ID", null))
        val query = call.request.queryParameters["q"]?.trim() ?: ""

        val user = dbQuery { Users.select { Users.id eq userId }.singleOrNull() }
            ?: return@get call.respond(HttpStatusCode.NotFound,
                ApiResponse<Nothing>(false, "User not found", null))

        // Get user allergies
        val onboardingAllergies = try {
            Json.decodeFromString<List<String>>(user[Users.allergies]).map { it.lowercase() }.toSet()
        } catch (e: Exception) { emptySet() }

        val excludeKeywords = onboardingAllergies.flatMap { name ->
            NutritionEngine.allergyKeywords[name] ?: emptyList()
        }.toSet()

        val preferredDietTypes = NutritionEngine.preferredDietTypes(
            goal = user[Users.goal], bmi = NutritionEngine.bmi(user[Users.weightKg], user[Users.heightCm]),
            activityLevel = user[Users.activityLevel],
            motivation = user[Users.motivation],
            fitnessLevel = user[Users.fitnessLevel]
        )

        // Fetch meals matching query
        val meals = dbQuery {
            if (query.isEmpty()) {
                Meals.selectAll().toList()
            } else {
                Meals.select {
                    Meals.mealName.lowerCase() like "%${query.lowercase()}%"
                }.toList()
            }
        }

        val results = meals.map { m ->
            val name  = m[Meals.mealName]
            val lower = name.lowercase()

            // Check suitability
            val allergyHit = excludeKeywords.firstOrNull { lower.contains(it) }
            val dietMatch  = preferredDietTypes.indexOf(m[Meals.dietType])

            val isSuitable: Boolean
            val reason: String

            when {
                allergyHit != null -> {
                    isSuitable = false
                    reason = "Contains $allergyHit (allergen)"
                }
                m[Meals.calories] > 800 && user[Users.goal].lowercase().contains("lose") -> {
                    isSuitable = false
                    reason = "Too high in calories for your goal"
                }
                dietMatch == -1 -> {
                    isSuitable = true
                    reason = "Not in your preferred diet type"
                }
                dietMatch == 0 -> {
                    isSuitable = true
                    reason = "Perfect for your ${user[Users.goal]} goal"
                }
                else -> {
                    isSuitable = true
                    reason = "Good match for your profile"
                }
            }

            SearchMealItem(
                meal_id           = m[Meals.mealId],
                meal_name         = name,
                image_url         = m[Meals.imageUrl] ?: "",
                calories          = m[Meals.calories],
                prep_time_minutes = m[Meals.prepTimeMinutes],
                difficulty        = m[Meals.difficulty],
                meal_time         = m[Meals.mealTime],
                diet_type         = m[Meals.dietType],
                eco_score         = m[Meals.ecoScore],
                is_suitable       = isSuitable,
                suitability_reason= reason
            )
        }

        call.respond(ApiResponse(success = true, message = "OK", data = results))
    }

    // POST /api/users/{id}/switch-meal — swap a meal in today's plan
post("/users/{id}/switch-meal") {
    val userId = call.parameters["id"]?.toIntOrNull()
        ?: return@post call.respond(HttpStatusCode.BadRequest,
            ApiResponse<Nothing>(false, "Invalid user ID", null))
    val req = call.receive<SwitchMealRequest>()

    // Get the plan date for this entry
    val planRow = dbQuery {
        MealPlans.select { MealPlans.id eq req.plan_id }.firstOrNull()
    }
    val planDate = planRow?.get(MealPlans.planDate)

    // Check if the new meal is already planned for the same day
    if (planDate != null) {
        val duplicate = dbQuery {
            MealPlans.select {
                (MealPlans.userId eq userId) and
                (MealPlans.mealId eq req.new_meal_id) and
                (MealPlans.planDate eq planDate) and
                (MealPlans.id neq req.plan_id)
            }.count() > 0
        }
        if (duplicate) {
            return@post call.respond(HttpStatusCode.Conflict,
                ApiResponse<Nothing>(false, "That meal is already in your plan for today", null))
        }
    }

    dbQuery {
        MealPlans.update({ MealPlans.id eq req.plan_id }) {
            it[MealPlans.mealId]      = req.new_meal_id
            it[MealPlans.isCompleted] = false
        }
    }
    call.respond(ApiResponse(success = true, message = "Meal switched", data = req.new_meal_id))
}
}
