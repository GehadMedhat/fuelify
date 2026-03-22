package com.example.fuelify.data.api

import com.example.fuelify.data.api.models.*
import retrofit2.Response
import retrofit2.http.*

interface FuelifyApi {

    @POST("api/users/register")
    suspend fun registerUser(@Body request: RegisterUserRequest): Response<ApiResponse<UserResponse>>

    @GET("api/users/{id}/dashboard")
    suspend fun getDashboard(@Path("id") userId: Int): Response<ApiResponse<DashboardData>>

    @POST("api/users/{id}/kitchen-order")
    suspend fun createKitchenOrder(
        @Path("id") userId: Int,
        @Body request: KitchenOrderRequest
    ): Response<ApiResponse<KitchenOrderResponse>>

    @POST("api/users/{id}/log-water")
    suspend fun logWater(@Path("id") userId: Int, @Body request: LogWaterRequest): Response<ApiResponse<Int>>

    @POST("api/users/{id}/log-meal")
    suspend fun logMeal(@Path("id") userId: Int, @Body request: LogMealRequest): Response<ApiResponse<Int>>

    @GET("api/meals/{id}/details")
    suspend fun getMealDetail(@Path("id") mealId: Int): Response<ApiResponse<MealDetailData>>

    @GET("api/users/{id}/search-meals")
    suspend fun searchMeals(
        @Path("id") userId: Int,
        @Query("q") query: String = ""
    ): Response<ApiResponse<List<SearchMealItem>>>

    @POST("api/users/{id}/switch-meal")
    suspend fun switchMeal(
        @Path("id") userId: Int,
        @Body request: SwitchMealRequest
    ): Response<ApiResponse<Int>>

    // ── Grocery Catalog ───────────────────────────────────────────────────────
    @GET("api/grocery/catalog")
    suspend fun getGroceryCatalog(
        @Query("category") category: String? = null
    ): Response<CatalogResponse>

    @GET("api/users/{id}/grocery/recommended")
    suspend fun getPersonalizedCatalog(
        @Path("id") userId: Int,
        @Query("category") category: String? = null
    ): Response<CatalogResponse>

    // ── User Grocery List ─────────────────────────────────────────────────────
    @GET("api/users/{id}/grocery")
    suspend fun getGroceryList(@Path("id") userId: Int): Response<GroceryListResponse>

    @POST("api/users/{id}/grocery")
    suspend fun addToGroceryList(
        @Path("id") userId: Int,
        @Body request: AddGroceryItemRequest
    ): Response<ApiResponse<Nothing>>

    @PATCH("api/users/{id}/grocery/{itemId}/check")
    suspend fun checkGroceryItem(
        @Path("id") userId: Int,
        @Path("itemId") itemId: Int,
        @Query("checked") checked: Boolean
    ): Response<ApiResponse<Nothing>>

    @DELETE("api/users/{id}/grocery/clear-checked")
    suspend fun clearCheckedItems(@Path("id") userId: Int): Response<ApiResponse<Nothing>>

    @DELETE("api/users/{id}/grocery/{itemId}")
    suspend fun deleteGroceryItem(
        @Path("id") userId: Int,
        @Path("itemId") itemId: Int
    ): Response<ApiResponse<Nothing>>

    // ── Pantry ────────────────────────────────────────────────────────────────
    @GET("api/users/{id}/pantry")
    suspend fun getPantry(@Path("id") userId: Int): Response<PantryResponse>

    @POST("api/users/{id}/pantry")
    suspend fun addPantryItem(
        @Path("id") userId: Int,
        @Body request: AddPantryRequest
    ): Response<ApiResponse<Nothing>>

    @DELETE("api/users/{id}/pantry/{itemId}")
    suspend fun deletePantryItem(
        @Path("id") userId: Int,
        @Path("itemId") itemId: Int
    ): Response<ApiResponse<Nothing>>

    @GET("api/users/{id}/pantry/suggestions")
    suspend fun getPantrySuggestions(@Path("id") userId: Int): Response<SuggestionsResponse>

    // ── Eco Sustainability ────────────────────────────────────────────────────
    @GET("api/users/{id}/eco")
    suspend fun getEcoSustainability(@Path("id") userId: Int): Response<EcoResponse>

    // ── Scanned Pantry ────────────────────────────────────────────────────────
    @GET("api/users/{id}/scanned-pantry")
    suspend fun getScannedPantry(@Path("id") userId: Int): Response<ApiResponse<List<ScannedPantryItem>>>

    @POST("api/users/{id}/scanned-pantry")
    suspend fun addScannedPantryItem(
        @Path("id") userId: Int,
        @Body request: AddScannedPantryRequest
    ): Response<ApiResponse<Nothing>>

    @DELETE("api/users/{id}/scanned-pantry/{itemId}")
    suspend fun deleteScannedPantryItem(
        @Path("id") userId: Int,
        @Path("itemId") itemId: Int
    ): Response<ApiResponse<Nothing>>

    // ── Family ────────────────────────────────────────────────────────────────
    @GET("api/users/{id}/family/lookup")
    suspend fun lookupFamilyMember(
        @Path("id") userId: Int,
        @Query("email") email: String
    ): Response<MemberPreviewResponse>

    @GET("api/users/{id}/family")
    suspend fun getFamily(@Path("id") userId: Int): Response<FamilyGroupResponse>

    @POST("api/users/{id}/family/invite")
    suspend fun inviteFamilyMember(
        @Path("id") userId: Int,
        @Body request: InviteMemberRequest
    ): Response<ApiResponse<Nothing>>

    @DELETE("api/users/{id}/family/member/{memberId}")
    suspend fun removeFamilyMember(
        @Path("id") userId: Int,
        @Path("memberId") memberId: Int
    ): Response<ApiResponse<Nothing>>

    @PATCH("api/users/{id}/family/name")
    suspend fun renameFamilyGroup(
        @Path("id") userId: Int,
        @Body request: RenameFamilyRequest
    ): Response<ApiResponse<Nothing>>

    @GET("api/users/{id}/family/grocery")
    suspend fun getFamilyGrocery(@Path("id") userId: Int): Response<FamilyGroceryResponse>

    @POST("api/users/{id}/family/grocery")
    suspend fun addFamilyGroceryItem(
        @Path("id") userId: Int,
        @Body request: AddFamilyGroceryRequest
    ): Response<ApiResponse<Nothing>>

    @PATCH("api/users/{id}/family/grocery/{itemId}/check")
    suspend fun checkFamilyGroceryItem(
        @Path("id") userId: Int,
        @Path("itemId") itemId: Int,
        @Body request: CheckFamilyGroceryRequest
    ): Response<ApiResponse<Nothing>>

    @DELETE("api/users/{id}/family/grocery/clear-checked")
    suspend fun clearCheckedFamilyItems(@Path("id") userId: Int): Response<ApiResponse<Nothing>>

    @DELETE("api/users/{id}/family/grocery/{itemId}")
    suspend fun deleteFamilyGroceryItem(
        @Path("id") userId: Int,
        @Path("itemId") itemId: Int
    ): Response<ApiResponse<Nothing>>
}
