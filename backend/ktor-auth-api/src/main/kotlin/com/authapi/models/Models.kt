package com.authapi.models

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.datetime

// ── Database Tables ───────────────────────────────────────────────────────────

object Users : IntIdTable("users") {
    val email          = varchar("email", 255).uniqueIndex().nullable()
    val pendingEmail   = varchar("pending_email", 255).nullable()
    val username       = varchar("username", 100).uniqueIndex().nullable()
    val passwordHash   = varchar("password_hash", 255).nullable()
    val isVerified     = bool("is_verified").default(false)
    val isActive       = bool("is_active").default(true)
    val isAdmin        = bool("is_admin").default(false)
    val visibility     = varchar("visibility", 20).default("PRIVATE")
    val firstName      = varchar("first_name", 100).nullable()
    val lastName       = varchar("last_name", 100).nullable()
    val profilePicture = varchar("profile_picture", 500).nullable()
    val points         = integer("points").default(0)
    val createdAt      = datetime("created_at").nullable()
    val updatedAt      = datetime("updated_at").nullable()
}

object VerificationTokens : IntIdTable("verification_tokens") {
    val userId    = reference("user_id", Users)
    val token     = varchar("token", 255).uniqueIndex()
    val code      = varchar("code", 6).nullable()
    val type      = varchar("type", 50)
    val expiresAt = datetime("expires_at")
    val usedAt    = datetime("used_at").nullable()
    val createdAt = datetime("created_at")
}

object TokenBlacklist : IntIdTable("token_blacklist") {
    val token     = varchar("token", 1000).uniqueIndex()
    val userId    = integer("user_id")
    val expiresAt = datetime("expires_at")
    val createdAt = datetime("created_at")
}

object LoginAttempts : IntIdTable("login_attempts") {
    val email     = varchar("email", 255).index()
    val ipAddress = varchar("ip_address", 45).nullable()
    val success   = bool("success").default(false)
    val createdAt = datetime("created_at")
}

object GuestSessions : IntIdTable("guest_sessions") {
    val guestId           = varchar("guest_id", 100).uniqueIndex()
    val token             = varchar("token", 1000).uniqueIndex()
    val expiresAt         = datetime("expires_at")
    val createdAt         = datetime("created_at")
    val convertedToUserId = integer("converted_to_user_id").nullable()
}

object SavedRecipes : IntIdTable("saved_recipes") {
    val userId   = reference("user_id", Users)
    val recipeId = integer("recipe_id")
    val savedAt  = datetime("saved_at")
}

object GroceryLists : IntIdTable("grocery_lists") {
    val userId    = reference("user_id", Users)
    val name      = varchar("name", 255)
    val createdAt = datetime("created_at")
}

object GroceryItems : IntIdTable("grocery_items") {
    val listId    = reference("list_id", GroceryLists)
    val name      = varchar("name", 255)
    val quantity  = varchar("quantity", 100).nullable()
    val isChecked = bool("is_checked").default(false)
    val createdAt = datetime("created_at")
}

// ── Request DTOs ──────────────────────────────────────────────────────────────

@Serializable data class SignUpRequest(
    val username: String,
    val email: String,
    val password: String,
    val firstName: String? = null,
    val lastName: String? = null
)

@Serializable data class LoginRequest(val email: String, val password: String)
@Serializable data class ForgotPasswordRequest(val email: String)
@Serializable data class VerifyResetCodeRequest(val email: String, val code: String)

@Serializable data class SetNewPasswordRequest(
    val resetToken: String,
    val newPassword: String,
    val confirmPassword: String
)

@Serializable data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String,
    val confirmPassword: String
)

@Serializable data class UpdateProfileRequest(
    val username: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null,
    val profilePicture: String? = null,
    val visibility: String? = null
)

@Serializable data class VerifyEmailChangeRequest(val code: String)
@Serializable data class VerifyEmailRequest(val email: String, val code: String)
@Serializable data class ResendVerificationRequest(val email: String)
@Serializable data class RefreshTokenRequest(val refreshToken: String)
@Serializable data class DeleteAccountRequest(val password: String)

@Serializable data class GuestConvertRequest(
    val guestToken: String,
    val username: String,
    val email: String,
    val password: String,
    val firstName: String? = null,
    val lastName: String? = null
)

// ── Response DTOs ─────────────────────────────────────────────────────────────

@Serializable data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: UserResponse
)

@Serializable data class TokenResponse(
    val accessToken: String,
    val refreshToken: String
)

@Serializable data class UserResponse(
    val id: Int,
    val username: String?,
    val email: String?,
    val pendingEmail: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val profilePicture: String? = null,
    val visibility: String = "PRIVATE",
    val isVerified: Boolean,
    val isAdmin: Boolean = false,          // ← added
    val savedRecipesCount: Int = 0,
    val groceryListsCount: Int = 0
)

@Serializable data class GuestResponse(
    val guestId: String,
    val accessToken: String,
    val expiresIn: String = "30 days",
    val message: String = "Browsing as guest. Sign up to save your data!"
)

@Serializable data class ApiResponse<T>(
    val success: Boolean,
    val message: String?,
    val data: T? = null
)

@Serializable data class ErrorResponse(
    val success: Boolean = false,
    val message: String,
    val errors: List<String> = emptyList()
)