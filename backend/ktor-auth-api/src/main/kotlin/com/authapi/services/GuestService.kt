package com.authapi.services

import com.authapi.models.*
import com.authapi.utils.JwtUtils
import com.authapi.utils.ValidationUtils
import com.authapi.utils.sanitize
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*

class GuestService(
    private val application: Application,
    private val emailService: EmailService,
    private val jwtUtils: JwtUtils
) {
    private val logger = LoggerFactory.getLogger(GuestService::class.java)

    // ── Continue as Guest ───────────────────────────────────────────────
    fun continueAsGuest(): ApiResponse<GuestResponse> {
        val guestId = "guest_${UUID.randomUUID().toString().replace("-", "")}"
        val token   = jwtUtils.generateGuestToken(guestId)
        val now     = LocalDateTime.now()
        transaction {
            GuestSessions.insert {
                it[GuestSessions.guestId]   = guestId
                it[GuestSessions.token]     = token
                it[GuestSessions.expiresAt] = now.plusDays(30)
                it[GuestSessions.createdAt] = now
            }
        }
        logger.info("👤 New guest session: $guestId")
        return ApiResponse(true, "Continuing as guest", GuestResponse(guestId = guestId, accessToken = token))
    }

    // ── Convert Guest to User ─────────────────────────────────────────────
    fun convertGuestToUser(request: GuestConvertRequest): ApiResponse<UserResponse> {
        val errors = ValidationUtils.validateSignUp(
            SignUpRequest(request.username, request.email, request.password, request.firstName, request.lastName)
        )
        if (errors.isNotEmpty()) throw ValidationException(errors)

        val guestPayload = jwtUtils.validateGuestToken(request.guestToken)
            ?: throw UnauthorizedException("Invalid or expired guest session")
        val guestId = guestPayload.getClaim("guestId").asString()

        return transaction {
            val email    = request.email.lowercase().sanitize()
            val username = request.username.lowercase().sanitize()

            val guest = GuestSessions.selectAll().where { GuestSessions.guestId eq guestId }.singleOrNull()
                ?: throw NotFoundException("Guest session not found")
            if (guest[GuestSessions.convertedToUserId] != null)
                throw ConflictException("Guest session already converted")
            if (Users.selectAll().where { Users.email eq email }.count() > 0)
                throw ConflictException("Email already registered")
            if (Users.selectAll().where { Users.username eq username }.count() > 0)
                throw ConflictException("Username already taken")

            val now    = LocalDateTime.now()
            val userId = Users.insertAndGetId {
                it[Users.email]        = email
                it[Users.username]     = username
                it[Users.passwordHash] = BCrypt.hashpw(request.password, BCrypt.gensalt(12))
                it[Users.firstName]    = request.firstName?.sanitize()
                it[Users.lastName]     = request.lastName?.sanitize()
                it[Users.isVerified]   = false
                it[Users.isActive]     = true
                it[Users.visibility]   = "PUBLIC"
                it[Users.createdAt]    = now
                it[Users.updatedAt]    = now
            }.value

            GuestSessions.update({ GuestSessions.guestId eq guestId }) {
                it[convertedToUserId] = userId
            }

            val code  = generateOtpCode()
            val token = UUID.randomUUID().toString().replace("-", "") +
                    UUID.randomUUID().toString().replace("-", "")
            VerificationTokens.insert {
                it[VerificationTokens.userId]    = userId
                it[VerificationTokens.token]     = token
                it[VerificationTokens.code]      = code
                it[VerificationTokens.type]      = "EMAIL_VERIFY"
                it[VerificationTokens.expiresAt] = now.plusHours(24)
                it[VerificationTokens.createdAt] = now
            }
            try { emailService.sendVerificationCode(email, code) }
            catch (e: Exception) { logger.warn("Email failed: ${e.message}") }

            logger.info("✅ Guest $guestId converted to user $userId")
            ApiResponse(true, "Account created! Please verify your email.",
                UserResponse(userId, username, email, request.firstName, request.lastName, request.username, null, "PRIVATE", false))
        }
    }

    private fun generateOtpCode(): String = (100000..999999).random().toString()
}