package com.example.fuelify.routes

import com.example.fuelify.db.DatabaseFactory.dbQuery
import com.example.fuelify.models.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime
import java.time.LocalDate
import kotlin.random.Random

// ─── Table ────────────────────────────────────────────────────────────────────

object SpinDiscounts : Table("spin_discounts") {
    val id          = integer("id").autoIncrement()
    val userId      = integer("user_id")
    val mealId      = integer("meal_id")
    val mealName    = varchar("meal_name", 255)
    val code        = varchar("code", 32)
    val discountPct = integer("discount_pct")          // 10, 15, or 20
    val discountEgp = double("discount_egp")           // actual EGP off
    val finalPrice  = double("final_price")            // price after discount
    val isUsed      = bool("is_used").default(false)
    val expiresAt   = datetime("expires_at")           // 24h from spin
    val createdAt   = datetime("created_at")
    override val primaryKey = PrimaryKey(id)
}

// ─── DTOs ─────────────────────────────────────────────────────────────────────

@Serializable
data class SpinWheelMeal(
    val meal_id:       Int,
    val meal_name:     String,
    val image_url:     String,
    val calories:      Int,
    val protein:       Int,
    val carbs:         Int,
    val fat:           Int,
    val meal_time:     String,
    val diet_type:     String,
    val difficulty:    String,
    val eco_score:     Double,
    val base_price:    Double,   // calculated from calories
    val discount_pct:  Int,      // 10, 15, or 20
    val discount_egp:  Double,
    val final_price:   Double,
    val discount_code: String,
    val expires_at:    String,   // ISO string, 24h from now
    val spin_reason:   String    // "Perfect for your goal" etc
)

// ─── Price logic ──────────────────────────────────────────────────────────────
// Base price: 8.99 + (calories / 100) — same formula used in MealDeliveryActivity
// Discount: random 10/15/20% weighted by eco_score
//   eco_score >= 9.0 → can get 20%
//   eco_score >= 8.0 → can get 15%
//   else             → 10%

private fun basePrice(calories: Int): Double =
    Math.round((8.99 + calories / 100.0) * 100.0) / 100.0

private fun discountPct(ecoScore: Double): Int = when {
    ecoScore >= 9.0 -> listOf(15, 20, 20).random()   // high eco = better chance at 20%
    ecoScore >= 8.0 -> listOf(10, 15, 15).random()
    else            -> 10
}

private fun generateCode(mealName: String): String {
    val tag = mealName.uppercase().replace(" ", "-").take(8)
    val rand = (1000..9999).random()
    return "FUELIFY-$tag-$rand"
}

// ─── Route ────────────────────────────────────────────────────────────────────

fun Route.spinWheelRoutes() {
get("/users/{id}/spin-wheel") {
    val userId = call.parameters["id"]?.toIntOrNull()
        ?: return@get call.respond(HttpStatusCode.BadRequest,
            ApiResponse<Nothing>(false, "Invalid user ID", null))

    // ── Check daily spin limit ────────────────────────────────────────────
    val today = LocalDate.now()
    val spinsToday = dbQuery {
        SpinDiscounts.select {
            (SpinDiscounts.userId eq userId) and
            (SpinDiscounts.createdAt greaterEq today.atStartOfDay()) and
            (SpinDiscounts.createdAt less today.plusDays(1).atStartOfDay())
        }.count().toInt()
    }

    if (spinsToday >= 2) {
        return@get call.respond(HttpStatusCode.TooManyRequests,
            ApiResponse<Nothing>(false, "You've used both spins for today! Come back tomorrow 🎰", null))
    }

 }

    // GET /api/users/{id}/spin-wheel
    // Picks a random allergy-safe meal, generates a discount code, saves to DB
    get("/users/{id}/spin-wheel") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user ID", null)
            )

        val user = dbQuery { Users.select { Users.id eq userId }.singleOrNull() }
            ?: return@get call.respond(
                HttpStatusCode.NotFound,
                ApiResponse<Nothing>(false, "User not found", null)
            )

        // ── Build allergy exclusion keywords (same as dashboard) ──────────────
        val onboardingAllergies: Set<String> = try {
            Json.decodeFromString<List<String>>(user[Users.allergies])
                .map { it.lowercase() }.toSet()
        } catch (e: Exception) { emptySet() }

        val userAllergyNames: Set<String> = try {
            dbQuery {
                UserAllergyTypes
                    .join(AllergyTypes, JoinType.INNER, additionalConstraint = {
                        UserAllergyTypes.allergyTypeId eq AllergyTypes.allergyTypeId
                    })
                    .select { UserAllergyTypes.userId eq userId }
                    .map { it[AllergyTypes.name].lowercase() }
                    .toSet()
            }
        } catch (e: Exception) { emptySet() }

        val excludeKeywords: Set<String> = (onboardingAllergies + userAllergyNames).flatMap { name ->
            NutritionEngine.allergyKeywords[name] ?: emptyList()
        }.toSet()

        // ── Get preferred diet types for this user ────────────────────────────
        val bmi = NutritionEngine.bmi(user[Users.weightKg], user[Users.heightCm])
        val preferredDietTypes = NutritionEngine.preferredDietTypes(
            goal          = user[Users.goal],
            bmi           = bmi,
            activityLevel = user[Users.activityLevel],
            motivation    = user[Users.motivation],
            fitnessLevel  = user[Users.fitnessLevel]
        )

        // ── Fetch all meals, filter safe ones ────────────────────────────────
        val allMeals = dbQuery { Meals.selectAll().toList() }

        fun isSafe(name: String) =
            excludeKeywords.none { kw -> name.lowercase().contains(kw) }

        // Also filter out very high calorie if user wants to lose weight
        val isLoseWeight = user[Users.goal].lowercase().contains("lose")

        val safeMeals = allMeals.filter { m ->
            isSafe(m[Meals.mealName]) &&
            !(isLoseWeight && m[Meals.calories] > 800)
        }

        if (safeMeals.isEmpty()) {
            return@get call.respond(
                HttpStatusCode.NotFound,
                ApiResponse<Nothing>(false, "No suitable meals found", null)
            )
        }

        // ── Score meals (prefer user's diet type, high eco score) ────────────
        val scored = safeMeals.map { m ->
            val dietIdx = preferredDietTypes.indexOf(m[Meals.dietType])
            val dietScore = when (dietIdx) { 0 -> 30.0; 1 -> 20.0; 2 -> 10.0; else -> 0.0 }
            val ecoBonus  = m[Meals.ecoScore] * 2.0
            Pair(m, dietScore + ecoBonus)
        }.sortedByDescending { it.second }

        // Pick from top 10 randomly (not always the top 1 — keeps it fun)
        val pool = scored.take(10)
        val pick = pool.random().first

        // ── Discount logic ────────────────────────────────────────────────────
        val price   = basePrice(pick[Meals.calories])
        val pct     = discountPct(pick[Meals.ecoScore])
        val off     = Math.round(price * pct / 100.0 * 100.0) / 100.0
        val final   = Math.round((price - off) * 100.0) / 100.0
        val code    = generateCode(pick[Meals.mealName])
        val expires = LocalDateTime.now().plusHours(24)
        val now     = LocalDateTime.now()

        // ── Save to DB ────────────────────────────────────────────────────────
        dbQuery {
            SpinDiscounts.insert {
                it[SpinDiscounts.userId]      = userId
                it[SpinDiscounts.mealId]      = pick[Meals.mealId]
                it[SpinDiscounts.mealName]    = pick[Meals.mealName]
                it[SpinDiscounts.code]        = code
                it[SpinDiscounts.discountPct] = pct
                it[SpinDiscounts.discountEgp] = off
                it[SpinDiscounts.finalPrice]  = final
                it[SpinDiscounts.isUsed]      = false
                it[SpinDiscounts.expiresAt]   = expires
                it[SpinDiscounts.createdAt]   = now
            }
        }

        // ── Build spin reason string ──────────────────────────────────────────
        val dietIdx = preferredDietTypes.indexOf(pick[Meals.dietType])
        val spinReason = when {
            dietIdx == 0 -> "Perfect for your ${user[Users.goal]} goal 🎯"
            pick[Meals.ecoScore] >= 9.0 -> "Top eco-score — great for you & the planet 🌱"
            pick[Meals.difficulty] == "Easy" -> "Quick & easy — ready in ${pick[Meals.prepTimeMinutes]} min ⚡"
            else -> "A great match for your nutrition profile 💪"
        }

        call.respond(
            ApiResponse(
                success = true,
                message = "You spun the wheel!",
                data = SpinWheelMeal(
                    meal_id       = pick[Meals.mealId],
                    meal_name     = pick[Meals.mealName],
                    image_url     = pick[Meals.imageUrl] ?: "",
                    calories      = pick[Meals.calories],
                    protein       = pick[Meals.protein].toInt(),
                    carbs         = pick[Meals.carbs].toInt(),
                    fat           = pick[Meals.fat].toInt(),
                    meal_time     = pick[Meals.mealTime],
                    diet_type     = pick[Meals.dietType],
                    difficulty    = pick[Meals.difficulty],
                    eco_score     = pick[Meals.ecoScore],
                    base_price    = price,
                    discount_pct  = pct,
                    discount_egp  = off,
                    final_price   = final,
                    discount_code = code,
                    expires_at    = expires.toString(),
                    spin_reason   = spinReason
                )
            )
        )
    }

    // POST /api/users/{id}/spin-wheel/redeem
    // Called when user places a Cloud Kitchen order using the code
    post("/users/{id}/spin-wheel/redeem/{code}") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@post call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user ID", null)
            )
        val code = call.parameters["code"]
            ?: return@post call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Missing code", null)
            )

        val row = dbQuery {
            SpinDiscounts.select {
                (SpinDiscounts.userId eq userId) and
                (SpinDiscounts.code eq code)
            }.firstOrNull()
        } ?: return@post call.respond(
            HttpStatusCode.NotFound,
            ApiResponse<Nothing>(false, "Code not found", null)
        )

        if (row[SpinDiscounts.isUsed]) {
            return@post call.respond(
                HttpStatusCode.Conflict,
                ApiResponse<Nothing>(false, "Code already used", null)
            )
        }

        if (LocalDateTime.now().isAfter(row[SpinDiscounts.expiresAt])) {
            return@post call.respond(
                HttpStatusCode.Gone,
                ApiResponse<Nothing>(false, "Code expired", null)
            )
        }

        dbQuery {
            SpinDiscounts.update({
                (SpinDiscounts.userId eq userId) and (SpinDiscounts.code eq code)
            }) {
                it[SpinDiscounts.isUsed] = true
            }
        }

        @Serializable
        data class RedeemResult(
            val meal_name:    String,
            val discount_pct: Int,
            val discount_egp: Double,
            val final_price:  Double
        )

        call.respond(ApiResponse(success = true, message = "Code redeemed! Discount applied ✅", data = RedeemResult(
            meal_name    = row[SpinDiscounts.mealName],
            discount_pct = row[SpinDiscounts.discountPct],
            discount_egp = row[SpinDiscounts.discountEgp],
            final_price  = row[SpinDiscounts.finalPrice]
        )))
    }
}
