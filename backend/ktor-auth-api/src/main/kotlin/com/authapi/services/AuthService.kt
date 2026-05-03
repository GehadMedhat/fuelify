package com.authapi.services

import com.authapi.models.*
import com.authapi.utils.JwtUtils
import com.authapi.utils.ValidationUtils
import com.authapi.utils.sanitize
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*

class AuthService(
    private val application: Application,
    private val emailService: EmailService,
    private val jwtUtils: JwtUtils,
    private val notificationService: NotificationService
) {
    private val logger         = LoggerFactory.getLogger(AuthService::class.java)
    private val maxAttempts    = application.environment.config.propertyOrNull("security.maxLoginAttempts")?.getString()?.toInt() ?: 5
    private val lockoutMinutes = application.environment.config.propertyOrNull("security.lockoutDurationMinutes")?.getString()?.toInt() ?: 15

    // ── Sign Up ───────────────────────────────────────────────────────────────
    fun signUp(request: SignUpRequest): ApiResponse<UserResponse> {
        val errors = ValidationUtils.validateSignUp(request)
        if (errors.isNotEmpty()) throw ValidationException(errors)

        val email    = request.email.lowercase().sanitize()
        val username = request.username.lowercase().sanitize()

        transaction {
            val orphanedUserIds = VerificationTokens
                .selectAll()
                .map { it[VerificationTokens.userId].value }
                .toSet()
                .filter { uid ->
                    Users.selectAll().where { Users.id eq uid }.count() == 0L
                }
            if (orphanedUserIds.isNotEmpty()) {
                VerificationTokens.deleteWhere {
                    VerificationTokens.userId inList orphanedUserIds
                }
            }
        }

        return transaction {
            if (Users.selectAll().where { Users.email eq email }.count() > 0)
                throw ConflictException("Email is already registered")
            if (Users.selectAll().where { Users.username eq username }.count() > 0)
                throw ConflictException("Username is already taken")

            val now    = LocalDateTime.now()
            val userId = Users.insertAndGetId {
                it[Users.email]        = email
                it[Users.username]     = username
                it[Users.passwordHash] = BCrypt.hashpw(request.password, BCrypt.gensalt(12))
                it[Users.firstName]    = request.firstName?.sanitize()
                it[Users.lastName]     = request.lastName?.sanitize()
                it[Users.isVerified]   = false
                it[Users.isActive]     = true
                it[Users.createdAt]    = now
                it[Users.updatedAt]    = now
            }.value

            val code  = generateOtpCode()
            val token = generateToken()
            VerificationTokens.insert {
                it[VerificationTokens.userId]    = userId
                it[VerificationTokens.token]     = token
                it[VerificationTokens.code]      = code
                it[VerificationTokens.type]      = "EMAIL_VERIFY"
                it[VerificationTokens.expiresAt] = now.plusMinutes(15)
                it[VerificationTokens.createdAt] = now
            }

            try { emailService.sendVerificationCode(email, code) }
            catch (e: Exception) { logger.warn("Email send failed: ${e.message}") }

            ApiResponse(
                success = true,
                message = "Account created! Please check your email for your 6-digit verification code.",
                data    = UserResponse(
                    id             = userId,
                    username       = username,
                    email          = email,
                    firstName      = request.firstName,
                    lastName       = request.lastName,
                    profilePicture = null,
                    isVerified     = false,
                    visibility     = "PRIVATE",
                    isAdmin        = false
                )
            )
        }
    }

    // ── Login ─────────────────────────────────────────────────────────────────
    fun login(request: LoginRequest, ip: String? = null): ApiResponse<AuthResponse> {
        if (request.email.isBlank() || request.password.isBlank())
            throw ValidationException(listOf("Email and password are required"))

        val email = request.email.lowercase().sanitize()
        checkLockout(email)

        return transaction {
            val user = Users.selectAll().where { Users.email eq email }.singleOrNull()

            if (user == null || !BCrypt.checkpw(request.password, user[Users.passwordHash])) {
                recordAttempt(email, ip, false)
                val left = remainingAttempts(email)
                if (left <= 0) throw UnauthorizedException("Account locked. Try again in $lockoutMinutes minutes.")
                throw UnauthorizedException("Invalid email or password. $left attempts remaining.")
            }

            if (!user[Users.isActive])   throw UnauthorizedException("Account is disabled.")
            if (!user[Users.isVerified]) throw UnauthorizedException("Please verify your email before logging in.")

            recordAttempt(email, ip, true)
            clearAttempts(email)

            val userId       = user[Users.id].value
            val accessToken  = jwtUtils.generateAccessToken(userId, email, user[Users.isAdmin])
            val refreshToken = jwtUtils.generateRefreshToken(userId, email)

            runCatching { notificationService.sendDailyLoginNotification(userId) }
                .onFailure { logger.warn("Failed to send daily login notification: ${it.message}") }

            ApiResponse(
                success = true,
                message = "Login successful",
                data    = AuthResponse(
                    accessToken  = accessToken,
                    refreshToken = refreshToken,
                    user         = UserResponse(
                        id             = userId,
                        username       = user[Users.username],
                        email          = email,
                        firstName      = user[Users.firstName],
                        lastName       = user[Users.lastName],
                        profilePicture = user[Users.profilePicture],
                        isVerified     = user[Users.isVerified],
                        visibility     = user[Users.visibility],
                        isAdmin        = user[Users.isAdmin]
                    )
                )
            )
        }
    }

    // ── Verify Email ──────────────────────────────────────────────────────────
    fun verifyEmail(email: String, code: String): ApiResponse<Nothing> {
        if (email.isBlank()) throw ValidationException(listOf("Email is required"))
        if (code.isBlank())  throw ValidationException(listOf("Verification code is required"))
        if (code.length != 6 || !code.all { it.isDigit() })
            throw ValidationException(listOf("Invalid code format"))

        val emailClean = email.lowercase().sanitize()
        var verifiedUserId: Int? = null

        transaction {
            val user = Users.selectAll().where { Users.email eq emailClean }.singleOrNull()
                ?: throw NotFoundException("No account found with that email")

            if (user[Users.isVerified]) throw ConflictException("Email is already verified")

            val row = VerificationTokens.selectAll().where {
                (VerificationTokens.userId eq user[Users.id]) and
                        (VerificationTokens.type   eq "EMAIL_VERIFY") and
                        (VerificationTokens.usedAt.isNull())
            }.orderBy(VerificationTokens.createdAt, SortOrder.DESC).firstOrNull()
                ?: throw NotFoundException("No active verification code found. Please request a new one.")

            if (LocalDateTime.now().isAfter(row[VerificationTokens.expiresAt]))
                throw ValidationException(listOf("Verification code expired. Please request a new one."))

            if (row[VerificationTokens.code] != code)
                throw ValidationException(listOf("Incorrect verification code."))

            val userId = row[VerificationTokens.userId].value
            Users.update({ Users.id eq userId }) {
                it[isVerified] = true
                it[updatedAt]  = LocalDateTime.now()
            }
            VerificationTokens.update({ VerificationTokens.id eq row[VerificationTokens.id] }) {
                it[usedAt] = LocalDateTime.now()
            }

            verifiedUserId = userId
        }

        verifiedUserId?.let { userId ->
            runCatching { notificationService.sendWelcomeNotifications(userId) }
                .onFailure { logger.warn("Failed to send welcome notifications: ${it.message}") }
        }

        return ApiResponse(true, "Email verified successfully! You can now log in.")
    }

    // ── Logout ────────────────────────────────────────────────────────────────
    // ── Logout ────────────────────────────────────────────────────────────────
    fun logout(token: String, userId: Int): ApiResponse<Nothing> {
        transaction {
            // Only insert if not already blacklisted — prevents duplicate key crash
            val alreadyBlacklisted = TokenBlacklist.selectAll()
                .where { TokenBlacklist.token eq token }
                .count() > 0

            if (!alreadyBlacklisted) {
                TokenBlacklist.insert {
                    it[TokenBlacklist.token]     = token
                    it[TokenBlacklist.userId]    = userId
                    it[TokenBlacklist.expiresAt] = jwtUtils.getTokenExpiry(token)
                    it[TokenBlacklist.createdAt] = LocalDateTime.now()
                }
            }
        }
        return ApiResponse(true, "Logged out successfully.")
    }

    // ── Refresh Token ─────────────────────────────────────────────────────────
    fun refreshToken(request: RefreshTokenRequest): ApiResponse<TokenResponse> {
        val payload = jwtUtils.validateRefreshToken(request.refreshToken)
            ?: throw UnauthorizedException("Invalid or expired refresh token")

        val blacklisted = transaction {
            TokenBlacklist.selectAll().where { TokenBlacklist.token eq request.refreshToken }.count() > 0
        }
        if (blacklisted) throw UnauthorizedException("Token revoked. Please login again.")

        val userId = payload.getClaim("userId").asInt()
        val email  = payload.getClaim("email").asString()

        val user = transaction { Users.selectAll().where { Users.id eq userId }.singleOrNull() }
            ?: throw UnauthorizedException("User not found")
        if (!user[Users.isActive]) throw UnauthorizedException("Account is disabled")

        transaction {
            TokenBlacklist.insert {
                it[TokenBlacklist.token]     = request.refreshToken
                it[TokenBlacklist.userId]    = userId
                it[TokenBlacklist.expiresAt] = jwtUtils.getTokenExpiry(request.refreshToken)
                it[TokenBlacklist.createdAt] = LocalDateTime.now()
            }
        }

        return ApiResponse(
            success = true,
            message = "Token refreshed",
            data    = TokenResponse(
                accessToken  = jwtUtils.generateAccessToken(userId, email),
                refreshToken = jwtUtils.generateRefreshToken(userId, email)
            )
        )
    }

    // ── Resend Verification ───────────────────────────────────────────────────
    fun resendVerification(email: String): ApiResponse<Nothing> {
        if (email.isBlank()) throw ValidationException(listOf("Email is required"))
        val emailClean = email.lowercase().sanitize()

        transaction {
            val user = Users.selectAll().where { Users.email eq emailClean }.singleOrNull()
                ?: return@transaction
            if (user[Users.isVerified]) throw ConflictException("Email already verified")

            val now  = LocalDateTime.now()
            val code = generateOtpCode()

            VerificationTokens.update({
                (VerificationTokens.userId eq user[Users.id]) and
                        (VerificationTokens.type   eq "EMAIL_VERIFY")
            }) { it[usedAt] = now }

            VerificationTokens.insert {
                it[VerificationTokens.userId]    = user[Users.id]
                it[VerificationTokens.token]     = generateToken()
                it[VerificationTokens.code]      = code
                it[VerificationTokens.type]      = "EMAIL_VERIFY"
                it[VerificationTokens.expiresAt] = now.plusMinutes(15)
                it[VerificationTokens.createdAt] = now
            }

            try { emailService.sendVerificationCode(emailClean, code) }
            catch (e: Exception) { logger.warn("Email send failed: ${e.message}") }
        }
        return ApiResponse(true, "If your email is unverified, a new verification code has been sent.")
    }

    // ── Forgot Password ───────────────────────────────────────────────────────
    fun forgotPassword(email: String): ApiResponse<Nothing> {
        if (email.isBlank()) throw ValidationException(listOf("Email is required"))
        val emailClean = email.lowercase().sanitize()

        transaction {
            val user = Users.selectAll().where { Users.email eq emailClean }.singleOrNull()
                ?: return@transaction

            val now  = LocalDateTime.now()
            val code = generateOtpCode()

            VerificationTokens.update({
                (VerificationTokens.userId eq user[Users.id]) and
                        (VerificationTokens.type   eq "PASSWORD_RESET")
            }) { it[usedAt] = now }
            VerificationTokens.update({
                (VerificationTokens.userId eq user[Users.id]) and
                        (VerificationTokens.type   eq "PASSWORD_RESET_VERIFIED")
            }) { it[usedAt] = now }

            VerificationTokens.insert {
                it[VerificationTokens.userId]    = user[Users.id]
                it[VerificationTokens.token]     = generateToken()
                it[VerificationTokens.code]      = code
                it[VerificationTokens.type]      = "PASSWORD_RESET"
                it[VerificationTokens.expiresAt] = now.plusMinutes(15)
                it[VerificationTokens.createdAt] = now
            }

            try { emailService.sendPasswordResetCode(emailClean, code) }
            catch (e: Exception) { logger.warn("Email send failed: ${e.message}") }
        }
        return ApiResponse(true, "If an account with that email exists, a reset code has been sent.")
    }

    // ── Verify Reset Code ─────────────────────────────────────────────────────
    fun verifyResetCode(email: String, code: String): ApiResponse<Map<String, String>> {
        if (email.isBlank()) throw ValidationException(listOf("Email is required"))
        if (code.isBlank() || code.length != 6 || !code.all { it.isDigit() })
            throw ValidationException(listOf("Invalid code format"))

        val emailClean = email.lowercase().sanitize()

        return transaction {
            val user = Users.selectAll().where { Users.email eq emailClean }.singleOrNull()
                ?: throw NotFoundException("No account found with that email")

            val row = VerificationTokens.selectAll().where {
                (VerificationTokens.userId eq user[Users.id]) and
                        (VerificationTokens.type   eq "PASSWORD_RESET") and
                        (VerificationTokens.usedAt.isNull())
            }.orderBy(VerificationTokens.createdAt, SortOrder.DESC).firstOrNull()
                ?: throw NotFoundException("No active reset code found. Please request a new one.")

            if (LocalDateTime.now().isAfter(row[VerificationTokens.expiresAt]))
                throw ValidationException(listOf("Reset code expired. Please request a new one."))

            if (row[VerificationTokens.code] != code)
                throw ValidationException(listOf("Incorrect reset code."))

            VerificationTokens.update({ VerificationTokens.id eq row[VerificationTokens.id] }) {
                it[usedAt] = LocalDateTime.now()
            }

            val now        = LocalDateTime.now()
            val resetToken = generateToken()
            VerificationTokens.insert {
                it[VerificationTokens.userId]    = user[Users.id]
                it[VerificationTokens.token]     = resetToken
                it[VerificationTokens.type]      = "PASSWORD_RESET_VERIFIED"
                it[VerificationTokens.expiresAt] = now.plusMinutes(10)
                it[VerificationTokens.createdAt] = now
            }

            ApiResponse(true, "Code verified. Use the resetToken to set your new password.",
                mapOf("resetToken" to resetToken))
        }
    }

    // ── Set New Password ──────────────────────────────────────────────────────
    fun setNewPassword(request: SetNewPasswordRequest): ApiResponse<Nothing> {
        if (request.newPassword.isBlank() || request.confirmPassword.isBlank())
            throw ValidationException(listOf("Password fields are required"))
        if (request.newPassword != request.confirmPassword)
            throw ValidationException(listOf("Passwords do not match"))
        val errors = ValidationUtils.validatePassword(request.newPassword)
        if (errors.isNotEmpty()) throw ValidationException(errors)

        transaction {
            val row = VerificationTokens.selectAll().where {
                (VerificationTokens.token eq request.resetToken) and
                        (VerificationTokens.type  eq "PASSWORD_RESET_VERIFIED")
            }.singleOrNull() ?: throw NotFoundException("Invalid or expired reset token")

            if (row[VerificationTokens.usedAt] != null)
                throw ConflictException("Reset token already used")
            if (LocalDateTime.now().isAfter(row[VerificationTokens.expiresAt]))
                throw ValidationException(listOf("Reset token expired. Please start over."))

            val userId = row[VerificationTokens.userId].value
            Users.update({ Users.id eq userId }) {
                it[passwordHash] = BCrypt.hashpw(request.newPassword, BCrypt.gensalt(12))
                it[updatedAt]    = LocalDateTime.now()
            }
            VerificationTokens.update({ VerificationTokens.token eq request.resetToken }) {
                it[usedAt] = LocalDateTime.now()
            }
        }
        return ApiResponse(true, "Password reset successfully. You can now log in.")
    }

    // ── Change Password ───────────────────────────────────────────────────────
    fun changePassword(userId: Int, request: ChangePasswordRequest): ApiResponse<Nothing> {
        if (request.newPassword != request.confirmPassword)
            throw ValidationException(listOf("Passwords do not match"))
        val errors = ValidationUtils.validatePassword(request.newPassword)
        if (errors.isNotEmpty()) throw ValidationException(errors)

        transaction {
            val user = Users.selectAll().where { Users.id eq userId }.singleOrNull()
                ?: throw NotFoundException("User not found")
            if (!BCrypt.checkpw(request.currentPassword, user[Users.passwordHash]))
                throw UnauthorizedException("Current password is incorrect")
            Users.update({ Users.id eq userId }) {
                it[passwordHash] = BCrypt.hashpw(request.newPassword, BCrypt.gensalt(12))
                it[updatedAt]    = LocalDateTime.now()
            }
        }
        return ApiResponse(true, "Password changed successfully.")
    }

    // ── Update Profile ────────────────────────────────────────────────────────
    fun updateProfile(userId: Int, request: UpdateProfileRequest): ApiResponse<UserResponse> {
        if (request.visibility != null && !ValidationUtils.isValidVisibility(request.visibility))
            throw ValidationException(listOf("Visibility must be PUBLIC, PRIVATE, or FRIENDS"))

        return transaction {
            Users.selectAll().where { Users.id eq userId }.singleOrNull()
                ?: throw NotFoundException("User not found")

            if (request.username != null) {
                val taken = Users.selectAll().where {
                    (Users.username eq request.username.lowercase()) and (Users.id neq userId)
                }.count() > 0
                if (taken) throw ConflictException("Username already taken")
            }

            Users.update({ Users.id eq userId }) {
                if (request.username       != null) it[username]       = request.username.lowercase().sanitize()
                if (request.firstName      != null) it[firstName]      = request.firstName.sanitize()
                if (request.lastName       != null) it[lastName]       = request.lastName.sanitize()
                if (request.profilePicture != null) it[profilePicture] = request.profilePicture
                if (request.visibility     != null) it[visibility]     = request.visibility.uppercase()
                it[updatedAt] = LocalDateTime.now()
            }

            if (request.email != null) {
                val emailClean = request.email.lowercase().sanitize()
                val user       = Users.selectAll().where { Users.id eq userId }.single()

                if (emailClean != user[Users.email]) {
                    if (Users.selectAll().where { Users.email eq emailClean }.count() > 0)
                        throw ConflictException("Email is already registered to another account")

                    val now  = LocalDateTime.now()
                    val code = generateOtpCode()

                    VerificationTokens.update({
                        (VerificationTokens.userId eq userId) and
                                (VerificationTokens.type   eq "EMAIL_CHANGE")
                    }) { it[usedAt] = now }

                    Users.update({ Users.id eq userId }) { it[pendingEmail] = emailClean }

                    VerificationTokens.insert {
                        it[VerificationTokens.userId]    = userId
                        it[VerificationTokens.token]     = generateToken()
                        it[VerificationTokens.code]      = code
                        it[VerificationTokens.type]      = "EMAIL_CHANGE"
                        it[VerificationTokens.expiresAt] = now.plusMinutes(15)
                        it[VerificationTokens.createdAt] = now
                    }

                    try { emailService.sendEmailChangeOtp(emailClean, code) }
                    catch (e: Exception) { logger.warn("Email send failed: ${e.message}") }
                }
            }

            val u            = Users.selectAll().where { Users.id eq userId }.single()
            val savedCount   = SavedRecipes.selectAll().where { SavedRecipes.userId eq userId }.count().toInt()
            val groceryCount = GroceryLists.selectAll().where { GroceryLists.userId eq userId }.count().toInt()

            ApiResponse(
                success = true,
                message = if (request.email != null)
                    "Profile updated. A verification code has been sent to ${request.email} to confirm your new email."
                else "Profile updated successfully.",
                data = UserResponse(
                    id                = userId,
                    username          = u[Users.username],
                    email             = u[Users.email],
                    pendingEmail      = u[Users.pendingEmail],
                    firstName         = u[Users.firstName],
                    lastName          = u[Users.lastName],
                    profilePicture    = u[Users.profilePicture],
                    isVerified        = u[Users.isVerified],
                    visibility        = u[Users.visibility],
                    savedRecipesCount = savedCount,
                    groceryListsCount = groceryCount,
                    isAdmin        = u[Users.isAdmin]
                )
            )
        }
    }

    // ── Verify Email Change ───────────────────────────────────────────────────
    fun verifyEmailChange(userId: Int, request: VerifyEmailChangeRequest): ApiResponse<UserResponse> {
        if (request.code.isBlank()) throw ValidationException(listOf("Code is required"))
        if (request.code.length != 6 || !request.code.all { it.isDigit() })
            throw ValidationException(listOf("Invalid code format"))

        return transaction {
            val user = Users.selectAll().where { Users.id eq userId }.singleOrNull()
                ?: throw NotFoundException("User not found")

            val pendingEmailValue = user[Users.pendingEmail]
                ?: throw ValidationException(listOf("No pending email change found. Request a new one."))

            val tokenRow = VerificationTokens.selectAll().where {
                (VerificationTokens.userId eq userId) and
                        (VerificationTokens.type   eq "EMAIL_CHANGE") and
                        (VerificationTokens.code   eq request.code) and
                        (VerificationTokens.usedAt.isNull())
            }.singleOrNull() ?: throw ValidationException(listOf("Invalid verification code"))

            if (LocalDateTime.now().isAfter(tokenRow[VerificationTokens.expiresAt]))
                throw ValidationException(listOf("Code has expired. Please request a new one."))

            val now = LocalDateTime.now()
            Users.update({ Users.id eq userId }) {
                it[email]        = pendingEmailValue
                it[pendingEmail] = null
                it[updatedAt]    = now
            }
            VerificationTokens.update({ VerificationTokens.id eq tokenRow[VerificationTokens.id] }) {
                it[usedAt] = now
            }

            val u            = Users.selectAll().where { Users.id eq userId }.single()
            val savedCount   = SavedRecipes.selectAll().where { SavedRecipes.userId eq userId }.count().toInt()
            val groceryCount = GroceryLists.selectAll().where { GroceryLists.userId eq userId }.count().toInt()

            ApiResponse(
                success = true,
                message = "Email updated successfully to $pendingEmailValue",
                data    = UserResponse(
                    id                = userId,
                    username          = u[Users.username],
                    email             = u[Users.email],
                    pendingEmail      = u[Users.pendingEmail],
                    firstName         = u[Users.firstName],
                    lastName          = u[Users.lastName],
                    profilePicture    = u[Users.profilePicture],
                    isVerified        = u[Users.isVerified],
                    visibility        = u[Users.visibility],
                    savedRecipesCount = savedCount,
                    groceryListsCount = groceryCount,
                    isAdmin        = u[Users.isAdmin]
                )
            )
        }
    }

    // ── Delete Account ────────────────────────────────────────────────────────
    fun deleteAccount(userId: Int, password: String): ApiResponse<Nothing> {
        if (password.isBlank()) throw ValidationException(listOf("Password is required"))

        val email = transaction {
            val user = Users.selectAll().where { Users.id eq userId }.singleOrNull()
                ?: throw NotFoundException("User not found")
            if (!BCrypt.checkpw(password, user[Users.passwordHash]))
                throw UnauthorizedException("Incorrect password")
            user[Users.email].toString()
        }

        transaction {
            AiChat.deleteWhere               { AiChat.userId eq userId }
            Notifications.deleteWhere        { Notifications.userId eq userId }
            NotificationSettings.deleteWhere { NotificationSettings.userId eq userId }
            UserReward.deleteWhere           { UserReward.userId eq userId }
            SavedRecipes.deleteWhere         { SavedRecipes.userId eq userId }
            GroceryLists.deleteWhere         { GroceryLists.userId eq userId }
            VerificationTokens.deleteWhere   { VerificationTokens.userId eq userId }
            TokenBlacklist.deleteWhere       { TokenBlacklist.userId eq userId }
            LoginAttempts.deleteWhere        { LoginAttempts.email eq email }
        }

        val extraTables = listOf(
            "DELETE FROM user_email WHERE user_id = $userId",
            "DELETE FROM user_medical_info WHERE user_id = $userId",
            "DELETE FROM user_allergy WHERE user_id = $userId",
            "DELETE FROM user_allergy_type WHERE user_id = $userId",
            "DELETE FROM user_food_preference WHERE user_id = $userId",
            "DELETE FROM user_settings WHERE user_id = $userId",
            "DELETE FROM user_reward WHERE user_id = $userId",
            "DELETE FROM medical_alert WHERE user_id = $userId",
            "DELETE FROM medical_profile WHERE user_id = $userId",
            "DELETE FROM health_log WHERE user_id = $userId",
            "DELETE FROM daily_logs WHERE user_id = $userId",
            "DELETE FROM progress_stats WHERE user_id = $userId",
            "DELETE FROM workout_session WHERE user_id = $userId",
            "DELETE FROM workout_plan WHERE user_id = $userId",
            "DELETE FROM meal_log WHERE user_id = $userId",
            "DELETE FROM meal_order WHERE user_id = $userId",
            "DELETE FROM meal_plans WHERE user_id = $userId",
            "DELETE FROM meal_plan_item WHERE plan_id IN (SELECT plan_id FROM meal_plan WHERE user_id = $userId)",
            "DELETE FROM meal_plan WHERE user_id = $userId",
            "DELETE FROM pantry_item WHERE user_id = $userId",
            "DELETE FROM scanned_pantry_item WHERE user_id = $userId",
            "DELETE FROM kitchen_order WHERE user_id = $userId",
            "DELETE FROM grocery_lists WHERE user_id = $userId",
            "DELETE FROM grocery_list WHERE user_id = $userId",
            "DELETE FROM notification WHERE user_id = $userId",
            "DELETE FROM doctor_consultation WHERE user_id = $userId",
            "DELETE FROM family_member WHERE user_id = $userId"
        )

        extraTables.forEach { sql ->
            runCatching {
                transaction { exec(sql) }
            }.onFailure { e ->
                logger.warn("Could not execute: $sql — ${e.message}")
            }
        }

        transaction {
            Users.deleteWhere { Users.id eq userId }
        }

        return ApiResponse(true, "Account deleted permanently.")
    }

    // ── Get Current User ──────────────────────────────────────────────────────
    fun getCurrentUser(userId: Int): ApiResponse<UserResponse> {
        return transaction {
            val u = Users.selectAll().where { Users.id eq userId }.singleOrNull()
                ?: throw NotFoundException("User not found")
            val savedCount   = SavedRecipes.selectAll().where { SavedRecipes.userId eq userId }.count().toInt()
            val groceryCount = GroceryLists.selectAll().where { GroceryLists.userId eq userId }.count().toInt()
            ApiResponse(
                success = true,
                message = "User retrieved",
                data    = UserResponse(
                    id                = u[Users.id].value,
                    username          = u[Users.username],
                    email             = u[Users.email],
                    pendingEmail      = u[Users.pendingEmail],
                    firstName         = u[Users.firstName],
                    lastName          = u[Users.lastName],
                    profilePicture    = u[Users.profilePicture],
                    isVerified        = u[Users.isVerified],
                    visibility        = u[Users.visibility],
                    savedRecipesCount = savedCount,
                    groceryListsCount = groceryCount,
                    isAdmin        = u[Users.isAdmin]
                )
            )
        }
    }

    // ── Token Blacklist Check ─────────────────────────────────────────────────
    fun isTokenBlacklisted(token: String): Boolean = transaction {
        TokenBlacklist.selectAll().where { TokenBlacklist.token eq token }.count() > 0
    }

    // ── Lockout Helpers ───────────────────────────────────────────────────────
    private fun checkLockout(email: String) {
        val since    = LocalDateTime.now().minusMinutes(lockoutMinutes.toLong())
        val failures = transaction {
            LoginAttempts.selectAll().where {
                (LoginAttempts.email     eq email) and
                        (LoginAttempts.success   eq false) and
                        (LoginAttempts.createdAt greaterEq since)
            }.count()
        }
        if (failures >= maxAttempts)
            throw UnauthorizedException("Account locked due to too many failed attempts. Try again in $lockoutMinutes minutes.")
    }

    private fun remainingAttempts(email: String): Int {
        val since = LocalDateTime.now().minusMinutes(lockoutMinutes.toLong())
        return LoginAttempts.selectAll().where {
            (LoginAttempts.email     eq email) and
                    (LoginAttempts.success   eq false) and
                    (LoginAttempts.createdAt greaterEq since)
        }.count().toInt().let { maxAttempts - it }
    }

    private fun recordAttempt(email: String, ip: String?, success: Boolean) {
        LoginAttempts.insert {
            it[LoginAttempts.email]     = email
            it[LoginAttempts.ipAddress] = ip
            it[LoginAttempts.success]   = success
            it[LoginAttempts.createdAt] = LocalDateTime.now()
        }
    }

    private fun clearAttempts(email: String) {
        LoginAttempts.deleteWhere { LoginAttempts.email eq email }
    }

    private fun generateOtpCode(): String = (100000..999999).random().toString()

    private fun generateToken(): String =
        UUID.randomUUID().toString().replace("-", "") +
                UUID.randomUUID().toString().replace("-", "")
}

class ValidationException(val errors: List<String>) : Exception(errors.firstOrNull() ?: "Validation failed")
class UnauthorizedException(message: String) : Exception(message)
class ConflictException(message: String) : Exception(message)
class NotFoundException(message: String) : Exception(message)