package com.authapi.routes

import com.authapi.models.*
import com.authapi.services.MarketplaceService
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

fun Route.marketplaceRoutes(service: MarketplaceService) {

    // ── User Routes ───────────────────────────────────────────────────────────
    authenticate("jwt-auth") {
        route("/api/marketplace") {

            get {
                val userId   = call.userId()
                val category = call.request.queryParameters["category"]
                call.respond(HttpStatusCode.OK, service.getRewards(userId, category))
            }

            get("/points") {
                val userId = call.userId()
                call.respond(HttpStatusCode.OK, service.getUserPoints(userId))
            }

            get("/my-rewards") {
                val userId = call.userId()
                call.respond(HttpStatusCode.OK, service.getUserRewards(userId))
            }

            post("/redeem") {
                val userId = call.userId()
                val req = runCatching { call.receive<RedeemRewardRequest>() }.getOrElse {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Unit>(false, "Invalid request body")
                    )
                }
                handleServiceCall(call) { service.redeemReward(userId, req) }
            }

            get("/{id}") {
                val rewardId = call.parameters["id"]?.toIntOrNull()
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Unit>(false, "Invalid reward ID")
                    )
                handleServiceCall(call) { service.getRewardById(rewardId) }
            }


        }
    }

    // ── Admin Routes ──────────────────────────────────────────────────────────
    authenticate("jwt-auth") {
        route("/api/admin/marketplace") {

            get {
                call.requireAdmin()
                call.respond(HttpStatusCode.OK, service.adminGetAllRewards())
            }

            post {
                call.requireAdmin()
                val req = runCatching { call.receive<CreateRewardRequest>() }.getOrElse {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Unit>(false, "Invalid request body")
                    )
                }
                handleServiceCall(call) { service.adminCreateReward(req) }
            }

            put("/{id}") {
                call.requireAdmin()
                val rewardId = call.parameters["id"]?.toIntOrNull()
                    ?: return@put call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Unit>(false, "Invalid reward ID")
                    )
                val req = runCatching { call.receive<UpdateRewardRequest>() }.getOrElse {
                    return@put call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Unit>(false, "Invalid request body")
                    )
                }
                handleServiceCall(call) { service.adminUpdateReward(rewardId, req) }
            }

            delete("/{id}") {
                call.requireAdmin()
                val rewardId = call.parameters["id"]?.toIntOrNull()
                    ?: return@delete call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Unit>(false, "Invalid reward ID")
                    )
                handleServiceCall<Unit>(call = call) { service.adminDeleteReward(rewardId) }
            }
        }
    }

    // ── Internal Route ────────────────────────────────────────────────────────
    post("/api/internal/marketplace/earn-points") {
        val req = runCatching { call.receive<EarnPointsRequest>() }.getOrElse {
            return@post call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(false, "Invalid request body")
            )
        }
        handleServiceCall(call) { service.earnPointsFromOrder(req) }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun ApplicationCall.userId(): Int {
    val principal = principal<JWTPrincipal>()
        ?: throw UnauthorizedException("Missing JWT principal")
    return principal.payload.getClaim("userId").asInt()
        ?: throw UnauthorizedException("JWT missing userId claim")
}

private fun ApplicationCall.requireAdmin() {
    val principal = principal<JWTPrincipal>()
        ?: throw UnauthorizedException("Missing JWT principal")
    val isAdmin = principal.payload.getClaim("isAdmin")?.asBoolean() ?: false
    if (!isAdmin) throw UnauthorizedException("Admin access required")
}

private suspend inline fun <reified T> handleServiceCall(
    call: ApplicationCall,
    crossinline block: () -> ApiResponse<T>
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