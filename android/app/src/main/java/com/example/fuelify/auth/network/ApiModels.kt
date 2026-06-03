package com.example.fuelify.auth.network

import com.google.gson.annotations.SerializedName

// ─── Auth ─────────────────────────────────────────────────────────────────────

data class LoginRequest(val email: String, val password: String)

data class SignUpRequest(
    @SerializedName("first_name")       val firstName: String,
    @SerializedName("last_name")        val lastName: String,
    val email: String,
    val username: String,
    val password: String,
    @SerializedName("confirm_password") val confirmPassword: String
)

data class VerifyEmailRequest(val email: String, val code: String)
data class ResendVerificationRequest(val email: String)
data class ForgotPasswordRequest(val email: String)
data class VerifyResetCodeRequest(val email: String, val code: String)
data class UploadResponse(val url: String)

data class ResetPasswordRequest(
    @SerializedName("reset_token")      val resetToken: String,
    @SerializedName("new_password")     val newPassword: String,
    @SerializedName("confirm_password") val confirmPassword: String
)

data class UpdateProfileRequest(
    @SerializedName("first_name")      val firstName: String? = null,
    @SerializedName("last_name")       val lastName: String? = null,
    val username: String? = null,
    val email: String? = null,
    @SerializedName("profile_picture") val profilePicture: String? = null,
    val visibility: String? = null
)

data class DeleteAccountRequest(val password: String)

// ── Email Change ──────────────────────────────────────────────────────────────

data class ChangeEmailRequest(
    @SerializedName("new_email") val newEmail: String
)

data class VerifyEmailChangeRequest(
    val code: String
)

// ─── Auth Responses ───────────────────────────────────────────────────────────

data class UserResponse(
    val id: Int,
    val username: String?,
    val email: String?,
    @SerializedName("pendingEmail")        val pendingEmail: String? = null,
    @SerializedName("first_name")          val firstName: String?,
    @SerializedName("last_name")           val lastName: String?,
    @SerializedName("profile_picture")     val profilePicture: String?,
    val visibility: String = "PRIVATE",
    @SerializedName("is_verified")         val isVerified: Boolean,
    @SerializedName("is_admin")            val isAdmin: Boolean = false,
    @SerializedName("saved_recipes_count") val savedRecipesCount: Int = 0,
    @SerializedName("grocery_lists_count") val groceryListsCount: Int = 0
)

data class FuelifyUserData(
    @SerializedName("user_id")         val userId: Int,
    val name: String,
    @SerializedName("profile_complete") val profileComplete: Boolean
)


data class AuthResponse(
    @SerializedName("access_token")  val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    val user: UserResponse
)

data class ApiResponse<T>(
    val success: Boolean,
    val message: String?,
    val data: T? = null
)

// ─── Marketplace ──────────────────────────────────────────────────────────────

data class RewardResponse(
    @SerializedName("reward_id")            val rewardId: Int,
    @SerializedName("reward_name")          val rewardName: String,
    @SerializedName("points_required")      val pointsRequired: Int,
    val category: String,
    @SerializedName("image_url")            val imageUrl: String?,
    val description: String?,
    @SerializedName("terms_and_conditions") val termsAndConditions: String?,
)

data class MarketplaceListResponse(
    val rewards: List<RewardResponse>,
    val total: Int,
    @SerializedName("user_points") val userPoints: Int
)

data class UserPointsResponse(
    @SerializedName("user_id") val userId: Int,
    val balance: Int
)

data class RedeemRewardRequest(
    @SerializedName("reward_id") val rewardId: Int
)

data class UserRewardResponse(
    @SerializedName("user_reward_id") val userRewardId: Int,
    @SerializedName("reward_id")      val rewardId: Int,
    @SerializedName("reward_name")    val rewardName: String,
    val category: String,
    @SerializedName("earned_date")    val earnedDate: String
)

// ─── Notifications ────────────────────────────────────────────────────────────

data class NotificationItem(
    val id: Int,
    val type: String,
    val title: String,
    val body: String,
    @SerializedName("is_read") val isRead: Boolean,
    @SerializedName("sent_at") val sentAt: String
)

data class NotificationListResponse(
    val notifications: List<NotificationItem>,
    val total: Int,
    @SerializedName("unread_count") val unreadCount: Int
)

data class NotificationSettingsResponse(
    @SerializedName("hydration_enabled")  val hydrationEnabled: Boolean,
    @SerializedName("steps_enabled")      val stepsEnabled: Boolean,
    @SerializedName("sleep_enabled")      val sleepEnabled: Boolean,
    @SerializedName("workout_enabled")    val workoutEnabled: Boolean,
    @SerializedName("workout_timing")     val workoutTiming: String,
    @SerializedName("workout_freq_start") val workoutFreqStart: String,
    @SerializedName("workout_freq_end")   val workoutFreqEnd: String,
    @SerializedName("dnd_enabled")        val dndEnabled: Boolean,
    @SerializedName("dnd_start_time")     val dndStartTime: String,
    @SerializedName("dnd_end_time")       val dndEndTime: String
)

data class NotificationSettingsRequest(
    @SerializedName("hydration_enabled")  val hydrationEnabled: Boolean,
    @SerializedName("steps_enabled")      val stepsEnabled: Boolean,
    @SerializedName("sleep_enabled")      val sleepEnabled: Boolean,
    @SerializedName("workout_enabled")    val workoutEnabled: Boolean,
    @SerializedName("workout_timing")     val workoutTiming: String,
    @SerializedName("workout_freq_start") val workoutFreqStart: String,
    @SerializedName("workout_freq_end")   val workoutFreqEnd: String,
    @SerializedName("dnd_enabled")        val dndEnabled: Boolean,
    @SerializedName("dnd_start_time")     val dndStartTime: String,
    @SerializedName("dnd_end_time")       val dndEndTime: String
)

// ─── Chat ─────────────────────────────────────────────────────────────────────

data class SendMessageRequest(val message: String)

data class ChatMessageResponse(
    @SerializedName("chat_id") val chatId: Int,
    val message: String,
    val sender: String,
    val timestamp: String
)

data class ChatResponse(
    @SerializedName("user_message") val userMessage: ChatMessageResponse,
    @SerializedName("aura_reply")   val auraReply: ChatMessageResponse
)

data class ChatHistoryResponse(
    val messages: List<ChatMessageResponse>,
    val total: Int
)

// ─── Admin / Rewards Management ───────────────────────────────────────────────
data class RefreshRequest(
    @SerializedName("refresh_token") val refreshToken: String
)

// Keep old ones untouched in case used elsewhere
data class CreateRewardRequest(
    @SerializedName("reward_name")          val rewardName: String,
    val description: String?,
    @SerializedName("points_required")      val pointsRequired: Int,
    @SerializedName("image_url")            val imageUrl: String?,
    @SerializedName("terms_and_conditions") val termsAndConditions: String?
)

data class UpdateRewardRequest(
    @SerializedName("reward_name")          val rewardName: String,
    val description: String?,
    @SerializedName("points_required")      val pointsRequired: Int,
    @SerializedName("image_url")            val imageUrl: String?,
    @SerializedName("terms_and_conditions") val termsAndConditions: String?
)

data class AdminCreateRewardRequest(
    @SerializedName("reward_name")          val rewardName: String,
    @SerializedName("description")          val description: String?,
    @SerializedName("points_required")      val pointsRequired: Int,
    @SerializedName("category")             val category: String,
    @SerializedName("image_url")            val imageUrl: String?,
    @SerializedName("terms_and_conditions") val termsAndConditions: String?
)

data class AdminUpdateRewardRequest(
    val rewardName: String,
    val description: String?,
    val pointsRequired: Int,
    val category: String,
    val imageUrl: String?,
    val termsAndConditions: String?
)
