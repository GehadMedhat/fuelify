package com.authapi.routes

import com.authapi.models.*
import com.authapi.services.NotFoundException
import com.authapi.services.NotificationService
import com.authapi.services.UnauthorizedException
import com.authapi.services.ValidationException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Notification routes — all require JWT auth.
 *
 * Mount in Application.module() alongside your existing auth routes:
 *   notificationRoutes(notificationService)
 *
 * Endpoints:
 *   GET  /api/notifications/settings          → get settings
 *   PUT  /api/notifications/settings          → update settings
 *   GET  /api/notifications                   → notification center list
 *   PUT  /api/notifications/read-all          → mark all as read
 *   PUT  /api/notifications/{id}/read         → mark one as read
 */
fun Application.notificationRoutes(service: NotificationService) {
    routing {
        authenticate("jwt-auth") {   // <-- match your existing auth block name
            route("/api/notifications") {

                // ── Settings ──────────────────────────────────────────────────

                get("/settings") {
                    val userId = call.userId()
                    call.respond(HttpStatusCode.OK, service.getSettings(userId))
                }

                put("/settings") {
                    val userId = call.userId()
                    val req = runCatching { call.receive<UpdateSettingsRequest>() }.getOrElse {
                        call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(false, "Invalid request body"))
                        return@put
                    }
                    runCatching {
                        call.respond(HttpStatusCode.OK, service.updateSettings(userId, req))
                    }.onFailure { e ->
                        val status = when (e) {
                            is ValidationException -> HttpStatusCode.BadRequest
                            else                   -> HttpStatusCode.InternalServerError
                        }
                        call.respond(status, ApiResponse<Unit>(false, e.message))
                    }
                }

                // ── Notification Center ───────────────────────────────────────

                get {
                    val userId = call.userId()
                    val limit  = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 50
                    val offset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0L
                    call.respond(HttpStatusCode.OK, service.getNotifications(userId, limit, offset))
                }

                put("/read-all") {
                    val userId = call.userId()
                    call.respond(HttpStatusCode.OK, service.markAllRead(userId))
                }

                put("/{id}/read") {
                    val userId  = call.userId()
                    val notifId = call.parameters["id"]?.toIntOrNull() ?: run {
                        call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(false, "Invalid notification ID"))
                        return@put
                    }
                    runCatching {
                        call.respond(HttpStatusCode.OK, service.markRead(notifId, userId))
                    }.onFailure { e ->
                        val status = if (e is NotFoundException) HttpStatusCode.NotFound
                                     else HttpStatusCode.InternalServerError
                        call.respond(status, ApiResponse<Unit>(false, e.message))
                    }
                }
            }
        }
    }
}

// ── Extract userId (Int) from JWT — same pattern as your existing AuthService ─

private fun ApplicationCall.userId(): Int {
    val principal = principal<JWTPrincipal>()
        ?: throw UnauthorizedException("Missing JWT principal")
    return principal.payload.getClaim("userId").asInt()
        ?: throw UnauthorizedException("JWT missing userId claim")
}
