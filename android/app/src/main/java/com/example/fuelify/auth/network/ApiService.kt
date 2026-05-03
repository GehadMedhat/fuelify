package com.example.fuelify.auth.network

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // ── Auth ──────────────────────────────────────────────────────────────────

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<AuthResponse>>

    @POST("api/auth/signup")
    suspend fun signUp(@Body request: SignUpRequest): Response<ApiResponse<UserResponse>>

    @POST("api/auth/verify-email")
    suspend fun verifyEmail(@Body request: VerifyEmailRequest): Response<ApiResponse<Nothing>>

    @POST("api/auth/resend-verification")
    suspend fun resendVerification(@Body request: ResendVerificationRequest): Response<ApiResponse<Nothing>>

    @POST("api/auth/forgot-password")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequest): Response<ApiResponse<Nothing>>

    @POST("api/auth/verify-reset-code")
    suspend fun verifyResetCode(@Body request: VerifyResetCodeRequest): Response<ApiResponse<Map<String, String>>>

    @POST("api/auth/reset-password")
    suspend fun resetPassword(@Body request: ResetPasswordRequest): Response<ApiResponse<Nothing>>

    @POST("api/auth/logout")
    suspend fun logout(@Header("Authorization") token: String): Response<ApiResponse<Nothing>>

    @GET("api/auth/me")
    suspend fun getProfile(@Header("Authorization") token: String): Response<ApiResponse<UserResponse>>

    @PUT("api/auth/profile")
    suspend fun updateProfile(
        @Header("Authorization") token: String,
        @Body request: UpdateProfileRequest
    ): Response<ApiResponse<UserResponse>>

    @POST("api/auth/change-email")
    suspend fun changeEmail(
        @Header("Authorization") token: String,
        @Body request: ChangeEmailRequest
    ): Response<ApiResponse<Nothing>>

    @POST("api/auth/profile/verify-email-change")
    suspend fun verifyEmailChange(
        @Header("Authorization") token: String,
        @Body request: VerifyEmailChangeRequest
    ): Response<ApiResponse<UserResponse>>

    @HTTP(method = "DELETE", path = "api/auth/account", hasBody = true)
    suspend fun deleteAccount(
        @Header("Authorization") token: String,
        @Body request: DeleteAccountRequest
    ): Response<ApiResponse<Nothing>>

    // ── Image Upload ──────────────────────────────────────────────────────────

    @Multipart
    @POST("upload-image")
    suspend fun uploadImage(
        @Header("Authorization") token: String,
        @Query("folder") folder: String,
        @Part image: MultipartBody.Part
    ): Response<ApiResponse<UploadResponse>>

    // ── Marketplace ───────────────────────────────────────────────────────────

    @GET("api/marketplace")
    suspend fun getMarketplaceItems(
        @Header("Authorization") token: String,
        @Query("category") category: String?
    ): Response<ApiResponse<MarketplaceListResponse>>

    @GET("api/marketplace/{id}")
    suspend fun getRewardById(
        @Header("Authorization") token: String,
        @Path("id") id: Int
    ): Response<ApiResponse<RewardResponse>>

    @GET("api/marketplace/points")
    suspend fun getUserPoints(
        @Header("Authorization") token: String
    ): Response<ApiResponse<UserPointsResponse>>

    @POST("api/marketplace/redeem")
    suspend fun redeemReward(
        @Header("Authorization") token: String,
        @Body request: RedeemRewardRequest
    ): Response<ApiResponse<UserRewardResponse>>

    @GET("api/marketplace/my-rewards")
    suspend fun getUserRewards(
        @Header("Authorization") token: String
    ): Response<ApiResponse<List<UserRewardResponse>>>

    // ── Notifications ─────────────────────────────────────────────────────────

    @GET("api/notifications")
    suspend fun getNotifications(
        @Header("Authorization") token: String
    ): Response<ApiResponse<NotificationListResponse>>

    @GET("api/notifications/settings")
    suspend fun getNotificationSettings(
        @Header("Authorization") token: String
    ): Response<ApiResponse<NotificationSettingsResponse>>

    @PUT("api/notifications/settings")
    suspend fun updateNotificationSettings(
        @Header("Authorization") token: String,
        @Body request: NotificationSettingsRequest
    ): Response<ApiResponse<NotificationSettingsResponse>>

    @PUT("api/notifications/{id}/read")
    suspend fun markNotificationRead(
        @Header("Authorization") token: String,
        @Path("id") id: Int
    ): Response<ApiResponse<Nothing>>

    @PUT("api/notifications/read-all")
    suspend fun markAllNotificationsRead(
        @Header("Authorization") token: String
    ): Response<ApiResponse<Nothing>>

    // ── Chat ──────────────────────────────────────────────────────────────────

    @POST("api/chat")
    suspend fun sendMessage(
        @Header("Authorization") token: String,
        @Body request: SendMessageRequest
    ): Response<ApiResponse<ChatResponse>>

    @GET("api/chat/history")
    suspend fun getChatHistory(
        @Header("Authorization") token: String
    ): Response<ApiResponse<ChatHistoryResponse>>

    @DELETE("api/chat/history")
    suspend fun clearChatHistory(
        @Header("Authorization") token: String
    ): Response<ApiResponse<Nothing>>

    @GET("api/chat/quick-questions")
    suspend fun getQuickQuestions(
        @Header("Authorization") token: String
    ): Response<ApiResponse<Any>>

    // ── Admin ─────────────────────────────────────────────────────────────────

    @POST("api/admin/marketplace")
    suspend fun createReward(
        @Header("Authorization") token: String,
        @Body request: AdminCreateRewardRequest
    ): Response<ApiResponse<Any>>

    @PUT("api/admin/marketplace/{id}")
    suspend fun updateReward(
        @Header("Authorization") token: String,
        @Path("id") rewardId: Int,
        @Body request: AdminUpdateRewardRequest
    ): Response<ApiResponse<Any>>

    @DELETE("api/admin/marketplace/{id}")
    suspend fun deleteReward(
        @Header("Authorization") token: String,
        @Path("id") rewardId: Int
    ): Response<ApiResponse<Any>>
}