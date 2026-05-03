package com.authapi.routes

import com.authapi.models.GuestConvertRequest
import com.authapi.services.GuestService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.guestRoutes(guestService: GuestService) {
    route("/api/auth") {

        // POST /api/auth/guest
        // Tap "Continue as Guest" → call this
        post("/guest") {
            val response = guestService.continueAsGuest()

            call.response.headers.append("X-Guest-Token", response.data?.accessToken ?: "")

            call.respond(HttpStatusCode.OK, response)
        }

        // POST /api/auth/guest/convert
        // When guest wants to sign up → call this
        post("/guest/convert") {
            val request = call.receive<GuestConvertRequest>()
            val response = guestService.convertGuestToUser(request)
            call.respond(response)
        }
    }
}