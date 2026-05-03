package com.authapi.routes

import com.authapi.models.*
import com.authapi.services.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.authRoutes(authService: AuthService) {

    route("/api/auth") {

        post("/signup") {
            val result = authService.signUp(call.receive())
            call.respond(HttpStatusCode.Created, result)
        }

        post("/login") {
            val ip = call.request.headers["X-Forwarded-For"] ?: call.request.local.remoteAddress
            val request = call.receive<LoginRequest>()
            val result = authService.login(request, ip)
            call.respond(HttpStatusCode.OK, result)
        }

        post("/refresh") {
            val result = authService.refreshToken(call.receive())
            call.respond(HttpStatusCode.OK, result)
        }

        // Email verification (code-based for mobile)
        post("/verify-email") {
            val request = call.receive<VerifyEmailRequest>()
            call.respond(HttpStatusCode.OK, authService.verifyEmail(request.email, request.code))
        }

        post("/resend-verification") {
            val request = call.receive<ResendVerificationRequest>()
            call.respond(HttpStatusCode.OK, authService.resendVerification(request.email))
        }

        // ── Forgot Password (3 steps) ─────────────────────────────────────────

        // Step 1: enter email → receive 6-digit code by email
        post("/forgot-password") {
            val request = call.receive<ForgotPasswordRequest>()
            call.respond(HttpStatusCode.OK, authService.forgotPassword(request.email))
        }

        // Step 2: enter code → receive resetToken
        post("/verify-reset-code") {
            val request = call.receive<VerifyResetCodeRequest>()
            call.respond(HttpStatusCode.OK, authService.verifyResetCode(request.email, request.code))
        }

        // Step 3: use resetToken + new password → done
        post("/reset-password") {
            call.respond(HttpStatusCode.OK, authService.setNewPassword(call.receive()))
        }

        authenticate("jwt-auth") {

            get("/me") {
                val userId = call.principal<JWTPrincipal>()?.getClaim("userId", Int::class)
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse(message = "Invalid token"))
                call.respond(HttpStatusCode.OK, authService.getCurrentUser(userId))
            }

            post("/logout") {
                val userId = call.principal<JWTPrincipal>()?.getClaim("userId", Int::class)
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse(message = "Invalid token"))
                val token = call.request.headers["Authorization"]?.removePrefix("Bearer ")?.trim()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(message = "Token not found"))
                call.respond(HttpStatusCode.OK, authService.logout(token, userId))
            }

            put("/profile") {
                val userId = call.principal<JWTPrincipal>()?.getClaim("userId", Int::class)
                    ?: return@put call.respond(HttpStatusCode.Unauthorized, ErrorResponse(message = "Invalid token"))
                call.respond(HttpStatusCode.OK, authService.updateProfile(userId, call.receive()))
            }

            post("/profile/verify-email-change") {
                val userId = call.principal<JWTPrincipal>()?.getClaim("userId", Int::class)
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse(message = "Invalid token"))
                call.respond(HttpStatusCode.OK, authService.verifyEmailChange(userId, call.receive()))
            }

            post("/change-password") {
                val userId = call.principal<JWTPrincipal>()?.getClaim("userId", Int::class)
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse(message = "Invalid token"))
                call.respond(HttpStatusCode.OK, authService.changePassword(userId, call.receive()))
            }

            delete("/account") {
                val userId = call.principal<JWTPrincipal>()?.getClaim("userId", Int::class)
                    ?: return@delete call.respond(HttpStatusCode.Unauthorized, ErrorResponse(message = "Invalid token"))
                val request = call.receive<DeleteAccountRequest>()
                call.respond(HttpStatusCode.OK, authService.deleteAccount(userId, request.password))
            }

        }
    }
}
