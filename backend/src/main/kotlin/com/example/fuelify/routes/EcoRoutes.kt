package com.example.fuelify.routes

import com.example.fuelify.db.DatabaseFactory.dbQuery
import com.example.fuelify.models.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.date
import java.time.LocalDate

// ─── Tables ───────────────────────────────────────────────────────────────────

private object EcoMealPlans : Table("meal_plans") {
    val planId   = integer("plan_id").autoIncrement()
    val userId   = integer("user_id")
    val mealId   = integer("meal_id")
    val planDate = date("plan_date")
    override val primaryKey = PrimaryKey(planId)
}

private object EcoMeals : Table("meal") {
    val mealId      = integer("meal_id")
    val mealName    = varchar("meal_name", 255)
    val imageUrl    = varchar("image_url", 500).nullable()
    val ecoScore    = double("eco_score")
    val dietType    = varchar("diet_type", 50).nullable()
    override val primaryKey = PrimaryKey(mealId)
}

private object EcoMealIngredients : Table("meal_ingredient") {
    val id           = integer("meal_ingredient_id")
    val mealId       = integer("meal_id")
    val ingredientId = integer("ingredient_id")
    override val primaryKey = PrimaryKey(id)
}

private object EcoIngredients : Table("ingredient") {
    val ingredientId = integer("ingredient_id")
    val name         = varchar("name", 100)
    val ecoScore     = double("eco_score")
    val foodCategory = varchar("food_category", 50)
    override val primaryKey = PrimaryKey(ingredientId)
}

// ─── DTOs ─────────────────────────────────────────────────────────────────────

@Serializable
data class EcoMealScoreDto(
    val mealId: Int,
    val mealName: String,
    val imageUrl: String,
    val ecoGrade: String,
    val ecoScore: Double,
    val carbonLevel: String,
    val originType: String,
    val packaging: String
)

@Serializable
data class EcoSustainabilityDto(
    val weeklyGrade: String,
    val weeklyScore: Double,
    val gradeMessage: String,
    val mealScores: List<EcoMealScoreDto>,
    val suggestions: List<String>
)

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun scoreToGrade(score: Double) = when {
    score >= 8.5 -> "A"
    score >= 7.0 -> "B"
    score >= 5.0 -> "C"
    else         -> "D"
}

private fun gradeToMessage(grade: String) = when (grade) {
    "A"  -> "Excellent! You're making very sustainable choices. 🌱"
    "B"  -> "Great job! You're making sustainable choices."
    "C"  -> "Good effort! There's room to improve your eco impact."
    else -> "Consider swapping some meals for greener options."
}

private fun scoreToCarbon(score: Double) = when {
    score >= 7.5 -> "Low"
    score >= 5.0 -> "Medium"
    else         -> "High"
}

private fun scoreToOrigin(score: Double, category: String) = when {
    score >= 8.0 || category.lowercase() in listOf("vegetables", "fruits") && score >= 6.5 -> "Local"
    score >= 5.5 -> "Regional"
    else         -> "Imported"
}

private fun scoreToPackaging(score: Double) = when {
    score >= 7.5 -> "Recyclable"
    score >= 5.0 -> "Mixed"
    else         -> "Non-recyclable"
}

private fun buildSuggestions(mealScores: List<EcoMealScoreDto>): List<String> {
    val suggestions = mutableListOf<String>()
    val avgScore = mealScores.map { it.ecoScore }.average().takeIf { !it.isNaN() } ?: 0.0

    val hasHighCarbon  = mealScores.any { it.carbonLevel == "High" }
    val hasImported    = mealScores.any { it.originType == "Imported" }
    val hasNonRecycle  = mealScores.any { it.packaging == "Non-recyclable" }
    val hasNoVeg       = mealScores.none { it.mealName.contains("salad", true) ||
                                          it.mealName.contains("veg", true) ||
                                          it.mealName.contains("bowl", true) }

    if (hasHighCarbon)
        suggestions.add("Choose more local ingredients to reduce carbon footprint")
    if (hasImported)
        suggestions.add("Opt for locally sourced produce when available")
    if (avgScore < 7.0)
        suggestions.add("Opt for plant-based proteins 2–3 times per week")
    if (hasNonRecycle)
        suggestions.add("Select meals with recyclable or minimal packaging")
    if (hasNoVeg)
        suggestions.add("Add more vegetable-based meals to your weekly plan")

    suggestions.add("Buy seasonal produce when possible")

    return suggestions.take(4)
}

// ─── Route ────────────────────────────────────────────────────────────────────

fun Route.ecoRoutes() {

    get("/users/{id}/eco") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user id", null))

        val data = dbQuery {
            // Get this week's planned meals using plan_date
            val today     = LocalDate.now()
            val weekStart = today.minusDays(today.dayOfWeek.value.toLong() - 1)
            val weekEnd   = weekStart.plusDays(6)

            // Weekly meal IDs for grade calculation
            val weeklyMealIds = EcoMealPlans
                .slice(EcoMealPlans.mealId)
                .select {
                    (EcoMealPlans.userId eq userId) and
                    (EcoMealPlans.planDate.between(weekStart, weekEnd))
                }
                .map { it[EcoMealPlans.mealId] }
                .distinct()

            // Today's meal IDs for display in the list
            val todayMealIds = EcoMealPlans
                .slice(EcoMealPlans.mealId)
                .select {
                    (EcoMealPlans.userId eq userId) and
                    (EcoMealPlans.planDate eq today)
                }
                .map { it[EcoMealPlans.mealId] }
                .distinct()

            val plannedMealIds = weeklyMealIds

            if (weeklyMealIds.isEmpty()) {
                return@dbQuery EcoSustainabilityDto(
                    weeklyGrade  = "N/A",
                    weeklyScore  = 0.0,
                    gradeMessage = "No meals planned this week yet.",
                    mealScores   = emptyList(),
                    suggestions  = listOf(
                        "Plan your meals to track your eco impact",
                        "Choose plant-based meals for a lower footprint",
                        "Opt for local and seasonal ingredients",
                        "Select meals with recyclable packaging"
                    )
                )
            }

            // Build meal eco scores using TODAY's meals for display
            val mealScores = EcoMeals
                .select { EcoMeals.mealId inList todayMealIds.ifEmpty { weeklyMealIds } }
                .map { row ->
                    val score    = row[EcoMeals.ecoScore]
                    val grade    = scoreToGrade(score)
                    // Get dominant ingredient category for this meal
                    val topCategory = EcoMealIngredients
                        .join(EcoIngredients, JoinType.INNER,
                            additionalConstraint = { EcoMealIngredients.ingredientId eq EcoIngredients.ingredientId })
                        .slice(EcoIngredients.foodCategory)
                        .select { EcoMealIngredients.mealId eq row[EcoMeals.mealId] }
                        .groupBy { it[EcoIngredients.foodCategory] }
                        .maxByOrNull { it.value.size }
                        ?.key ?: "Protein"

                    EcoMealScoreDto(
                        mealId      = row[EcoMeals.mealId],
                        mealName    = row[EcoMeals.mealName],
                        imageUrl    = row.getOrNull(EcoMeals.imageUrl) ?: "",
                        ecoGrade    = grade,
                        ecoScore    = score,
                        carbonLevel = scoreToCarbon(score),
                        originType  = scoreToOrigin(score, topCategory),
                        packaging   = scoreToPackaging(score)
                    )
                }
                .sortedByDescending { it.ecoScore }

            val avgScore = mealScores.map { it.ecoScore }.average()
            val weeklyGrade = scoreToGrade(avgScore)

            EcoSustainabilityDto(
                weeklyGrade  = weeklyGrade,
                weeklyScore  = Math.round(avgScore * 10.0) / 10.0,
                gradeMessage = gradeToMessage(weeklyGrade),
                mealScores   = mealScores,
                suggestions  = buildSuggestions(mealScores)
            )
        }

        call.respond(ApiResponse(success = true, message = "OK", data = data))
    }
}
