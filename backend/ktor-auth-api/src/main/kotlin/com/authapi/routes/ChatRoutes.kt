package com.authapi.routes

import com.authapi.models.*
import com.authapi.services.ChatService
import com.authapi.services.UnauthorizedException
import com.authapi.services.ValidationException
import com.authapi.services.NotFoundException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Chat endpoints (all require JWT):
 *
 *   POST   /api/chat                  → send message to Aura, get reply
 *   GET    /api/chat/history          → get chat history
 *   DELETE /api/chat/history          → clear chat history
 *   GET    /api/chat/quick-questions  → get quick question categories
 */
fun Route.chatRoutes(service: ChatService) {
    authenticate("jwt-auth") {
        route("/api/chat") {

            // ── Send Message ──────────────────────────────────────────────────
            post {
                val userId = call.userId()
                val req = runCatching { call.receive<SendMessageRequest>() }.getOrElse {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Unit>(false, "Invalid request body")
                    )
                }
                handleChatCall(call) { service.sendMessage(userId, req) }
            }

            // ── Chat History ──────────────────────────────────────────────────
            get("/history") {
                val userId = call.userId()
                val limit  = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 50
                val offset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0L
                call.respond(HttpStatusCode.OK, service.getChatHistory(userId, limit, offset))
            }

            // ── Clear History ─────────────────────────────────────────────────
            delete("/history") {
                val userId = call.userId()
                call.respond(HttpStatusCode.OK, service.clearHistory(userId))
            }

            // ── Quick Questions ───────────────────────────────────────────────
            get("/quick-questions") {
                call.respond(HttpStatusCode.OK, service.getQuickQuestions())
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun ApplicationCall.userId(): Int {
    val principal = principal<JWTPrincipal>()
        ?: throw UnauthorizedException("Missing JWT principal")
    return principal.payload.getClaim("userId").asInt()
        ?: throw UnauthorizedException("JWT missing userId claim")
}

private suspend inline fun <reified T> handleChatCall(
    call: ApplicationCall,
    crossinline block: suspend () -> ApiResponse<T>
) {
    runCatching { block() }
        .onSuccess { call.respond(HttpStatusCode.OK, it) }
        .onFailure { e ->
            val (status, message) = when (e) {
                is ValidationException   -> HttpStatusCode.BadRequest to e.message
                is NotFoundException     -> HttpStatusCode.NotFound to e.message
                is UnauthorizedException -> HttpStatusCode.Unauthorized to e.message
                else                     -> HttpStatusCode.InternalServerError to "Something went wrong"
            }
            call.respond(status, ApiResponse<Unit>(false, message))
        }
}
