package com.example.fuelify.routes

import com.example.fuelify.db.DatabaseFactory.dbQuery
import com.example.fuelify.models.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*

// ─── Tables needed ────────────────────────────────────────────────────────────

object Recipes : Table("recipe") {
    val recipeId           = integer("recipe_id")
    val mealId             = integer("meal_id")
    val instructions       = text("instructions")
    val videoUrl           = varchar("video_url", 255).nullable()
    val prepTimeMinutes    = integer("prep_time_minutes")
    val difficulty         = varchar("difficulty", 30)
    val calorieQualityScore= double("calorie_quality_score")
    override val primaryKey = PrimaryKey(recipeId)
}

// Using MealIngredients and Ingredients from Tables.kt
// (defined as MealIngredients and Ingredients objects)

private object MealIngredients2 : Table("meal_ingredient") {
    val mealIngredientId = integer("meal_ingredient_id")
    val mealId           = integer("meal_id")
    val ingredientId     = integer("ingredient_id")
    val quantity         = double("quantity")
    override val primaryKey = PrimaryKey(mealIngredientId)
}

private object Ingredients2 : Table("ingredient") {
    val ingredientId    = integer("ingredient_id")
    val name            = varchar("name", 100)
    val unit            = varchar("unit", 20)
    val caloriesPerUnit = double("calories_per_unit").nullable()
    val proteinPerUnit  = double("protein_per_unit").nullable()
    val carbsPerUnit    = double("carbs_per_unit").nullable()
    val fatPerUnit      = double("fat_per_unit").nullable()
    override val primaryKey = PrimaryKey(ingredientId)
}

// ─── DTOs ─────────────────────────────────────────────────────────────────────

@Serializable
data class IngredientItem(
    val name: String,
    val quantity: String   // e.g. "500g", "2 cups"
)

@Serializable
data class MealDetailData(
    val meal_id: Int,
    val meal_name: String,
    val image_url: String,
    val calories: Int,
    val protein_g: Double,
    val carbs_g: Double,
    val fat_g: Double,
    val eco_score: Double,
    val difficulty: String,
    val prep_time_minutes: Int,
    val meal_time: String,
    val diet_type: String,
    val calorie_quality_score: Double,
    val eco_grade: String,        // A / B / C based on eco_score
    val has_video: Boolean,
    val video_url: String,
    val instructions: List<String>,   // split by ". " into steps
    val ingredients: List<IngredientItem>,
    val fiber_g: Double,
    val sugar_g: Double
)

// ─── Route ────────────────────────────────────────────────────────────────────

fun Route.mealDetailRoutes() {

    get("/meals/{id}/details") {
        val mealId = call.parameters["id"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid meal ID", null))

        // Fetch meal
        val meal = dbQuery {
            Meals.select { Meals.mealId eq mealId }.singleOrNull()
        } ?: return@get call.respond(HttpStatusCode.NotFound,
            ApiResponse<Nothing>(false, "Meal not found", null))

        // Fetch recipe (instructions + video)
        val recipe = dbQuery {
            Recipes.select { Recipes.mealId eq mealId }.singleOrNull()
        }

        // Fetch ingredients
        val ingredients: List<IngredientItem> = try {
            dbQuery {
                MealIngredients2
                    .join(Ingredients2, JoinType.INNER, additionalConstraint = {
                        MealIngredients2.ingredientId eq Ingredients2.ingredientId
                    })
                    .select { MealIngredients2.mealId eq mealId }
                    .map { row ->
                        val qty    = row[MealIngredients2.quantity]
                        val unit   = row[Ingredients2.unit]
                        val name   = row[Ingredients2.name]
                        // Format: "500 g" or "2 piece" → clean display
                        val qtyNum = if (qty == qty.toLong().toDouble()) qty.toLong().toString() else qty.toString()
                        val qtyStr = when (unit.lowercase()) {
                            "g"     -> "${qtyNum}g"
                            "kg"    -> "${qtyNum}kg"
                            "ml"    -> "${qtyNum}ml"
                            "l"     -> "${qtyNum}L"
                            "piece" -> if (qty == 1.0) "1 piece" else "${qtyNum} pieces"
                            else    -> "$qtyNum $unit"
                        }
                        IngredientItem(name = name, quantity = qtyStr.trim())
                    }
            }
        } catch (e: Exception) {
            println("Ingredient fetch error: ${e.message}")
            emptyList()
        }

        // Parse instructions into numbered steps
        val rawInstructions = recipe?.get(Recipes.instructions) ?: ""
        val steps = rawInstructions
            .split(Regex("(?<=\\.)|(?<=\\!)"))
            .map { it.trim() }
            .filter { it.length > 5 }

        // Eco grade
        val ecoScore = meal[Meals.ecoScore]
        val ecoGrade = when {
            ecoScore >= 9.0 -> "A"
            ecoScore >= 8.0 -> "B"
            ecoScore >= 7.0 -> "C"
            else            -> "D"
        }

        // Estimate fiber/sugar from carbs
        val carbs  = meal[Meals.carbs]
        val fiber  = carbs * 0.12
        val sugar  = carbs * 0.18

        call.respond(ApiResponse(
            success = true,
            message = "OK",
            data = MealDetailData(
                meal_id               = meal[Meals.mealId],
                meal_name             = meal[Meals.mealName],
                image_url             = meal[Meals.imageUrl] ?: "",
                calories              = meal[Meals.calories],
                protein_g             = meal[Meals.protein],
                carbs_g               = meal[Meals.carbs],
                fat_g                 = meal[Meals.fat],
                eco_score             = ecoScore,
                difficulty            = meal[Meals.difficulty],
                prep_time_minutes     = meal[Meals.prepTimeMinutes],
                meal_time             = meal[Meals.mealTime],
                diet_type             = meal[Meals.dietType],
                calorie_quality_score = recipe?.get(Recipes.calorieQualityScore) ?: (ecoScore - 0.5),
                eco_grade             = ecoGrade,
                has_video             = meal[Meals.hasVideo] || (recipe?.get(Recipes.videoUrl)?.isNotEmpty() == true),
                video_url             = recipe?.get(Recipes.videoUrl) ?: "",
                instructions          = steps,
                ingredients           = ingredients,
                fiber_g               = Math.round(fiber * 10.0) / 10.0,
                sugar_g               = Math.round(sugar * 10.0) / 10.0
            )
        ))
    }
}
