package com.example.fuelify.data.api.models

import com.google.gson.annotations.SerializedName

// ── Grocery Catalog ───────────────────────────────────────────────────────────

data class CatalogItem(
    @SerializedName("catalogId")       val catalogId: Int,
    @SerializedName("ingredientId")    val ingredientId: Int?,
    @SerializedName("displayName")     val displayName: String,
    @SerializedName("description")     val description: String,
    @SerializedName("price")           val price: Double,
    @SerializedName("category")        val category: String,
    @SerializedName("isAvailable")     val isAvailable: Boolean,
    @SerializedName("isRecommended")   val isRecommended: Boolean,
    @SerializedName("imageUrl")        val imageUrl: String,
    @SerializedName("caloriesPerUnit") val caloriesPerUnit: Double?,
    @SerializedName("proteinPerUnit")  val proteinPerUnit: Double?,
    @SerializedName("carbsPerUnit")    val carbsPerUnit: Double?,
    @SerializedName("fatPerUnit")      val fatPerUnit: Double?,
    @SerializedName("unit")            val unit: String?,
    @SerializedName("allergenFlag")    val allergenFlag: Boolean?
)

data class CatalogResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data")    val data: List<CatalogItem>?
)

// ── Grocery List ──────────────────────────────────────────────────────────────

data class GroceryListItem(
    @SerializedName("groceryItemId") val groceryItemId: Int,
    @SerializedName("listId")        val listId: Int,
    @SerializedName("itemName")      val itemName: String,
    @SerializedName("checked")       val checked: Boolean,
    @SerializedName("category")      val category: String,
    @SerializedName("quantity")      val quantity: Double,
    @SerializedName("unit")          val unit: String,
    @SerializedName("price")         val price: Double,
    @SerializedName("ingredientId")  val ingredientId: Int?,
    @SerializedName("isRecommended") val isRecommended: Boolean
)

data class GroceryListResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data")    val data: List<GroceryListItem>?
)

data class AddGroceryItemRequest(
    @SerializedName("itemName")      val itemName: String,
    @SerializedName("category")      val category: String,
    @SerializedName("quantity")      val quantity: Double,
    @SerializedName("unit")          val unit: String,
    @SerializedName("price")         val price: Double,
    @SerializedName("ingredientId")  val ingredientId: Int?,
    @SerializedName("isRecommended") val isRecommended: Boolean
)

// ── Pantry ────────────────────────────────────────────────────────────────────

data class PantryItemModel(
    @SerializedName("pantryItemId")    val pantryItemId: Int,
    @SerializedName("ingredientId")    val ingredientId: Int,
    @SerializedName("name")            val name: String,
    @SerializedName("unit")            val unit: String,
    @SerializedName("foodCategory")    val foodCategory: String,
    @SerializedName("quantity")        val quantity: Double,
    @SerializedName("expiryDate")      val expiryDate: String,
    @SerializedName("daysUntilExpiry") val daysUntilExpiry: Int
)

data class PantryResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data")    val data: List<PantryItemModel>?
)

data class AddPantryRequest(
    @SerializedName("ingredientId") val ingredientId: Int,
    @SerializedName("quantity")     val quantity: Double,
    @SerializedName("expiryDate")   val expiryDate: String  // "YYYY-MM-DD"
)

// ── Recipe Suggestions ────────────────────────────────────────────────────────

data class PantryRecipeSuggestion(
    @SerializedName("mealId")           val mealId: Int,
    @SerializedName("mealName")         val mealName: String,
    @SerializedName("imageUrl")         val imageUrl: String,
    @SerializedName("matchCount")       val matchCount: Int,
    @SerializedName("totalIngredients") val totalIngredients: Int
)

data class SuggestionsResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data")    val data: List<PantryRecipeSuggestion>?
)
