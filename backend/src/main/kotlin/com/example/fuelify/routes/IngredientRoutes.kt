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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.date
import java.time.LocalDate

// ─── Tables ───────────────────────────────────────────────────────────────────

private object GroceryCatalog : Table("grocery_catalog") {
    val catalogId     = integer("catalog_id").autoIncrement()
    val ingredientId  = integer("ingredient_id").nullable()
    val displayName   = varchar("display_name", 255)
    val description   = varchar("description", 255).nullable()
    val price         = double("price")
    val category      = varchar("category", 100).nullable()
    val isAvailable   = bool("is_available").default(true)
    val isRecommended = bool("is_recommended").default(false)
    val imageUrl      = varchar("image_url", 500).nullable()
    override val primaryKey = PrimaryKey(catalogId)
}

private object GroceryListTable : Table("grocery_list") {
    val listId    = integer("list_id").autoIncrement()
    val userId    = integer("user_id")
    val createdAt = date("created_at")
    override val primaryKey = PrimaryKey(listId)
}

private object GroceryItemTable : Table("grocery_item") {
    val groceryItemId = integer("grocery_item_id").autoIncrement()
    val listId        = integer("list_id")
    val itemName      = varchar("item_name", 255)
    val checked       = bool("checked").default(false)
    val category      = varchar("category", 100).nullable()
    val quantity      = double("quantity").nullable()
    val unit          = varchar("unit", 50).nullable()
    val price         = double("price").nullable()
    val ingredientId  = integer("ingredient_id").nullable()
    val isRecommended = bool("is_recommended").default(false)
    override val primaryKey = PrimaryKey(groceryItemId)
}

private object PantryItems : Table("pantry_item") {
    val pantryItemId = integer("pantry_item_id").autoIncrement()
    val userId       = integer("user_id")
    val ingredientId = integer("ingredient_id")
    val quantity     = double("quantity")
    val expiryDate   = date("expiry_date")
    override val primaryKey = PrimaryKey(pantryItemId)
}

private object Ingredients : Table("ingredient") {
    val ingredientId    = integer("ingredient_id")
    val name            = varchar("name", 100)
    val unit            = varchar("unit", 20)
    val caloriesPerUnit = double("calories_per_unit")
    val proteinPerUnit  = double("protein_per_unit")
    val carbsPerUnit    = double("carbs_per_unit")
    val fatPerUnit      = double("fat_per_unit")
    val ecoScore        = double("eco_score")
    val allergenFlag    = bool("allergen_flag")
    val foodCategory    = varchar("food_category", 50)
    override val primaryKey = PrimaryKey(ingredientId)
}

private object MealIngredients3 : Table("meal_ingredient") {
    val mealIngredientId = integer("meal_ingredient_id")
    val mealId           = integer("meal_id")
    val ingredientId     = integer("ingredient_id")
    val quantity         = double("quantity")
    override val primaryKey = PrimaryKey(mealIngredientId)
}

private object Users : Table("users") {
    val id         = integer("id")
    val goal       = varchar("goal", 100).nullable()
    val likedFoods = text("liked_foods").nullable()   // JSON e.g. ["Eggs","Chicken"]
    val allergies  = text("allergies").nullable()      // JSON e.g. ["Nuts","Dairy"]
    override val primaryKey = PrimaryKey(id)
}

// ─── DTOs ─────────────────────────────────────────────────────────────────────

@Serializable
data class CatalogItemResponse(
    val catalogId: Int,
    val ingredientId: Int?,
    val displayName: String,
    val description: String,
    val price: Double,
    val category: String,
    val isAvailable: Boolean,
    val isRecommended: Boolean,
    val imageUrl: String,
    val caloriesPerUnit: Double?,
    val proteinPerUnit: Double?,
    val carbsPerUnit: Double?,
    val fatPerUnit: Double?,
    val unit: String?,
    val allergenFlag: Boolean?
)

@Serializable
data class GroceryListItemResponse(
    val groceryItemId: Int,
    val listId: Int,
    val itemName: String,
    val checked: Boolean,
    val category: String,
    val quantity: Double,
    val unit: String,
    val price: Double,
    val ingredientId: Int?,
    val isRecommended: Boolean
)

@Serializable
data class AddGroceryItemRequest(
    val itemName: String,
    val category: String,
    val quantity: Double,
    val unit: String,
    val price: Double,
    val ingredientId: Int?,
    val isRecommended: Boolean
)

@Serializable
data class PantryItemResponse(
    val pantryItemId: Int,
    val ingredientId: Int,
    val name: String,
    val unit: String,
    val foodCategory: String,
    val quantity: Double,
    val expiryDate: String,
    val daysUntilExpiry: Int
)

@Serializable
data class AddPantryRequest(
    val ingredientId: Int,
    val quantity: Double,
    val expiryDate: String
)

@Serializable
data class PantryRecipeSuggestion(
    val mealId: Int,
    val mealName: String,
    val imageUrl: String,
    val matchCount: Int,
    val totalIngredients: Int
)

// ─── Helpers ──────────────────────────────────────────────────────────────────

private suspend fun getOrCreateListId(userId: Int): Int = dbQuery {
    val existing = GroceryListTable
        .select { GroceryListTable.userId eq userId }
        .firstOrNull()
    if (existing != null) {
        existing[GroceryListTable.listId]
    } else {
        GroceryListTable.insert {
            it[GroceryListTable.userId]    = userId
            it[GroceryListTable.createdAt] = LocalDate.now()
        }[GroceryListTable.listId]
    }
}

private fun parseJsonArray(raw: String?): List<String> = try {
    if (raw.isNullOrBlank()) emptyList()
    else Json.parseToJsonElement(raw).jsonArray
        .map { it.jsonPrimitive.content.lowercase().trim() }
} catch (e: Exception) { emptyList() }

// ─── Routes ───────────────────────────────────────────────────────────────────

fun Route.ingredientRoutes() {

    // ── GET /api/users/{id}/grocery/recommended?category=Protein ─────────────
    // Personalized catalog: recommended flag is dynamic per user goal/allergies
    get("/users/{id}/grocery/recommended") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user id", null))

        val filterCategory = call.request.queryParameters["category"]

        val items = dbQuery {
            // 1. Fetch user profile
            val user = Users.select { Users.id eq userId }.firstOrNull()
            val goal = user?.getOrNull(Users.goal)?.lowercase() ?: ""
            val likedFoods   = parseJsonArray(user?.getOrNull(Users.likedFoods))
            val userAllergies = parseJsonArray(user?.getOrNull(Users.allergies))

            // 2. Recommended categories based on goal
            val recommendedCategories: Set<String> = when {
                "muscle" in goal || "build" in goal || "gain" in goal ->
                    setOf("Protein")
                "weight" in goal || "lose" in goal || "cut" in goal ->
                    setOf("Vegetables", "Fruits", "Protein")
                "maintain" in goal || "balance" in goal ->
                    setOf("Protein", "Carbs", "Vegetables", "Fruits")
                "endurance" in goal || "cardio" in goal ->
                    setOf("Carbs", "Fruits", "Protein")
                else ->
                    setOf("Protein", "Vegetables", "Fruits")
            }

            // 3. Build exclude list from allergies
            val allergenMap: Map<String, List<String>> = mapOf(
                "nuts"     to listOf("almond", "walnut", "peanut", "cashew", "pistachio"),
                "dairy"    to listOf("milk", "cheese", "cheddar", "mozzarella", "feta", "yogurt", "cottage cheese", "whey"),
                "eggs"     to listOf("egg"),
                "gluten"   to listOf("wheat", "bread", "pasta", "oats"),
                "seafood"  to listOf("shrimp", "salmon", "tuna", "fish"),
                "shellfish" to listOf("shrimp", "crab", "lobster"),
                "soy"      to listOf("soy sauce", "tofu"),
                "peanuts"  to listOf("peanut butter", "peanut")
            )
            val excludeKeywords: Set<String> = userAllergies
                .flatMap { allergenMap[it] ?: listOf(it) }
                .toSet()

            // 4. Query catalog with optional category filter
            GroceryCatalog
                .join(Ingredients, JoinType.LEFT, additionalConstraint = {
                    GroceryCatalog.ingredientId eq Ingredients.ingredientId
                })
                .select { GroceryCatalog.isAvailable eq true }
                .let { q ->
                    if (!filterCategory.isNullOrBlank() && filterCategory != "All")
                        q.andWhere { GroceryCatalog.category eq filterCategory }
                    else q
                }
                .orderBy(GroceryCatalog.displayName to SortOrder.ASC)
                .mapNotNull { row ->
                    val name     = row[GroceryCatalog.displayName].lowercase()
                    val category = row.getOrNull(GroceryCatalog.category) ?: ""

                    // Exclude allergen items completely
                    if (excludeKeywords.any { it in name }) return@mapNotNull null

                    // Compute dynamic recommendation
                    val isRecommended =
                        category in recommendedCategories ||
                        likedFoods.any { liked -> liked in name || name in liked }

                    CatalogItemResponse(
                        catalogId       = row[GroceryCatalog.catalogId],
                        ingredientId    = row.getOrNull(GroceryCatalog.ingredientId),
                        displayName     = row[GroceryCatalog.displayName],
                        description     = row.getOrNull(GroceryCatalog.description) ?: "",
                        price           = row[GroceryCatalog.price],
                        category        = category,
                        isAvailable     = row[GroceryCatalog.isAvailable],
                        isRecommended   = isRecommended,
                        imageUrl        = row.getOrNull(GroceryCatalog.imageUrl) ?: "",
                        caloriesPerUnit = row.getOrNull(Ingredients.caloriesPerUnit),
                        proteinPerUnit  = row.getOrNull(Ingredients.proteinPerUnit),
                        carbsPerUnit    = row.getOrNull(Ingredients.carbsPerUnit),
                        fatPerUnit      = row.getOrNull(Ingredients.fatPerUnit),
                        unit            = row.getOrNull(Ingredients.unit),
                        allergenFlag    = row.getOrNull(Ingredients.allergenFlag)
                    )
                }
        }

        // Sort: recommended first, then alphabetical
        val sorted = items.sortedWith(compareByDescending<CatalogItemResponse> { it.isRecommended }
            .thenBy { it.displayName })
        call.respond(ApiResponse(success = true, message = "OK", data = sorted))
    }

    // ── GET /api/grocery/catalog?category=Protein (generic, non-personalized) ─
    get("/grocery/catalog") {
        val category = call.request.queryParameters["category"]
        val items = dbQuery {
            GroceryCatalog
                .join(Ingredients, JoinType.LEFT, additionalConstraint = {
                    GroceryCatalog.ingredientId eq Ingredients.ingredientId
                })
                .select { GroceryCatalog.isAvailable eq true }
                .let { q ->
                    if (!category.isNullOrBlank() && category != "All")
                        q.andWhere { GroceryCatalog.category eq category }
                    else q
                }
                .orderBy(
                    GroceryCatalog.isRecommended to SortOrder.DESC,
                    GroceryCatalog.displayName   to SortOrder.ASC
                )
                .map { row ->
                    CatalogItemResponse(
                        catalogId       = row[GroceryCatalog.catalogId],
                        ingredientId    = row.getOrNull(GroceryCatalog.ingredientId),
                        displayName     = row[GroceryCatalog.displayName],
                        description     = row.getOrNull(GroceryCatalog.description) ?: "",
                        price           = row[GroceryCatalog.price],
                        category        = row.getOrNull(GroceryCatalog.category) ?: "",
                        isAvailable     = row[GroceryCatalog.isAvailable],
                        isRecommended   = row[GroceryCatalog.isRecommended],
                        imageUrl        = row.getOrNull(GroceryCatalog.imageUrl) ?: "",
                        caloriesPerUnit = row.getOrNull(Ingredients.caloriesPerUnit),
                        proteinPerUnit  = row.getOrNull(Ingredients.proteinPerUnit),
                        carbsPerUnit    = row.getOrNull(Ingredients.carbsPerUnit),
                        fatPerUnit      = row.getOrNull(Ingredients.fatPerUnit),
                        unit            = row.getOrNull(Ingredients.unit),
                        allergenFlag    = row.getOrNull(Ingredients.allergenFlag)
                    )
                }
        }
        call.respond(ApiResponse(success = true, message = "OK", data = items))
    }

    // ── GET /api/users/{id}/grocery ───────────────────────────────────────────
    get("/users/{id}/grocery") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user id", null))

        val listId = getOrCreateListId(userId)
        val items = dbQuery {
            GroceryItemTable
                .select { GroceryItemTable.listId eq listId }
                .orderBy(GroceryItemTable.groceryItemId to SortOrder.ASC)
                .map { row ->
                    GroceryListItemResponse(
                        groceryItemId = row[GroceryItemTable.groceryItemId],
                        listId        = row[GroceryItemTable.listId],
                        itemName      = row[GroceryItemTable.itemName],
                        checked       = row[GroceryItemTable.checked],
                        category      = row.getOrNull(GroceryItemTable.category) ?: "",
                        quantity      = row.getOrNull(GroceryItemTable.quantity) ?: 1.0,
                        unit          = row.getOrNull(GroceryItemTable.unit) ?: "",
                        price         = row.getOrNull(GroceryItemTable.price) ?: 0.0,
                        ingredientId  = row.getOrNull(GroceryItemTable.ingredientId),
                        isRecommended = row[GroceryItemTable.isRecommended]
                    )
                }
        }
        call.respond(ApiResponse(success = true, message = "OK", data = items))
    }

    // ── POST /api/users/{id}/grocery ──────────────────────────────────────────
    post("/users/{id}/grocery") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user id", null))
        val body = call.receive<AddGroceryItemRequest>()
        val listId = getOrCreateListId(userId)

        dbQuery {
            val alreadyExists = if (body.ingredientId != null) {
                GroceryItemTable.select {
                    (GroceryItemTable.listId eq listId) and
                    (GroceryItemTable.ingredientId eq body.ingredientId)
                }.count() > 0
            } else {
                GroceryItemTable.select {
                    (GroceryItemTable.listId eq listId) and
                    (GroceryItemTable.itemName eq body.itemName)
                }.count() > 0
            }
            if (!alreadyExists) {
                GroceryItemTable.insert {
                    it[GroceryItemTable.listId]        = listId
                    it[GroceryItemTable.itemName]      = body.itemName
                    it[GroceryItemTable.checked]       = false
                    it[GroceryItemTable.category]      = body.category
                    it[GroceryItemTable.quantity]      = body.quantity
                    it[GroceryItemTable.unit]          = body.unit
                    it[GroceryItemTable.price]         = body.price
                    it[GroceryItemTable.ingredientId]  = body.ingredientId
                    it[GroceryItemTable.isRecommended] = body.isRecommended
                }
            }
        }
        call.respond(ApiResponse(success = true, message = "OK", data = null))
    }

    // ── PATCH /api/users/{id}/grocery/{itemId}/check ──────────────────────────
    patch("/users/{id}/grocery/{itemId}/check") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@patch call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user id", null))
        val itemId = call.parameters["itemId"]?.toIntOrNull()
            ?: return@patch call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid item id", null))
        val isChecked = call.request.queryParameters["checked"]?.toBooleanStrictOrNull() ?: true
        val listId = getOrCreateListId(userId)
        dbQuery {
            GroceryItemTable.update({
                (GroceryItemTable.groceryItemId eq itemId) and
                (GroceryItemTable.listId eq listId)
            }) {
                it[GroceryItemTable.checked] = isChecked
            }
        }
        call.respond(ApiResponse(success = true, message = "OK", data = null))
    }

    // ── DELETE /api/users/{id}/grocery/clear-checked ──────────────────────────
    delete("/users/{id}/grocery/clear-checked") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@delete call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user id", null))
        val listId = getOrCreateListId(userId)
        dbQuery {
            GroceryItemTable.deleteWhere {
                (GroceryItemTable.listId eq listId) and
                (GroceryItemTable.checked eq true)
            }
        }
        call.respond(ApiResponse(success = true, message = "OK", data = null))
    }

    // ── DELETE /api/users/{id}/grocery/{itemId} ───────────────────────────────
    delete("/users/{id}/grocery/{itemId}") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@delete call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user id", null))
        val itemId = call.parameters["itemId"]?.toIntOrNull()
            ?: return@delete call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid item id", null))
        val listId = getOrCreateListId(userId)
        dbQuery {
            GroceryItemTable.deleteWhere {
                (GroceryItemTable.groceryItemId eq itemId) and
                (GroceryItemTable.listId eq listId)
            }
        }
        call.respond(ApiResponse(success = true, message = "OK", data = null))
    }

    // ── GET /api/users/{id}/pantry ────────────────────────────────────────────
    get("/users/{id}/pantry") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user id", null))

        val today = LocalDate.now()
        val items = dbQuery {
            PantryItems
                .join(Ingredients, JoinType.INNER, additionalConstraint = {
                    PantryItems.ingredientId eq Ingredients.ingredientId
                })
                .select { PantryItems.userId eq userId }
                .orderBy(PantryItems.expiryDate to SortOrder.ASC)
                .map { row ->
                    val expiryDate = row[PantryItems.expiryDate]
                    val days = java.time.temporal.ChronoUnit.DAYS.between(today, expiryDate).toInt()
                    PantryItemResponse(
                        pantryItemId    = row[PantryItems.pantryItemId],
                        ingredientId    = row[Ingredients.ingredientId],
                        name            = row[Ingredients.name],
                        unit            = row[Ingredients.unit],
                        foodCategory    = row[Ingredients.foodCategory],
                        quantity        = row[PantryItems.quantity],
                        expiryDate      = expiryDate.toString(),
                        daysUntilExpiry = days
                    )
                }
        }
        call.respond(ApiResponse(success = true, message = "OK", data = items))
    }

    // ── POST /api/users/{id}/pantry ───────────────────────────────────────────
    post("/users/{id}/pantry") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user id", null))
        val body = call.receive<AddPantryRequest>()

        dbQuery {
            val exists = PantryItems.select {
                (PantryItems.userId eq userId) and
                (PantryItems.ingredientId eq body.ingredientId)
            }.count() > 0
            if (!exists) {
                PantryItems.insert {
                    it[PantryItems.userId]       = userId
                    it[PantryItems.ingredientId] = body.ingredientId
                    it[PantryItems.quantity]     = body.quantity
                    it[PantryItems.expiryDate]   = LocalDate.parse(body.expiryDate)
                }
            }
        }
        call.respond(ApiResponse(success = true, message = "OK", data = null))
    }

    // ── DELETE /api/users/{id}/pantry/{itemId} ────────────────────────────────
    delete("/users/{id}/pantry/{itemId}") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@delete call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user id", null))
        val itemId = call.parameters["itemId"]?.toIntOrNull()
            ?: return@delete call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid item id", null))
        dbQuery {
            PantryItems.deleteWhere {
                (PantryItems.pantryItemId eq itemId) and (PantryItems.userId eq userId)
            }
        }
        call.respond(ApiResponse(success = true, message = "OK", data = null))
    }

    // ── GET /api/users/{id}/pantry/suggestions ────────────────────────────────
    get("/users/{id}/pantry/suggestions") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user id", null))

        val suggestions = dbQuery {
            val pantryIngredientIds = PantryItems
                .slice(PantryItems.ingredientId)
                .select { PantryItems.userId eq userId }
                .map { it[PantryItems.ingredientId] }
                .toSet()

            if (pantryIngredientIds.isEmpty()) return@dbQuery emptyList<PantryRecipeSuggestion>()

            val mealMatchCounts: Map<Int, Int> = MealIngredients3
                .select { MealIngredients3.ingredientId inList pantryIngredientIds }
                .groupBy { it[MealIngredients3.mealId] }
                .mapValues { it.value.size }

            val mealTotals: Map<Int, Int> = MealIngredients3
                .selectAll()
                .groupBy { it[MealIngredients3.mealId] }
                .mapValues { it.value.size }

            mealMatchCounts.entries
                .sortedByDescending { it.value }
                .take(5)
                .mapNotNull { (mealId, matchCount) ->
                    Meals.select { Meals.mealId eq mealId }.singleOrNull()?.let { row ->
                        PantryRecipeSuggestion(
                            mealId           = mealId,
                            mealName         = row[Meals.mealName],
                            imageUrl         = row[Meals.imageUrl] ?: "",
                            matchCount       = matchCount,
                            totalIngredients = mealTotals[mealId] ?: 0
                        )
                    }
                }
        }
        call.respond(ApiResponse(success = true, message = "OK", data = suggestions))
    }
}
