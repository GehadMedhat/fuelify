package com.authapi.plugins

import com.authapi.models.ApiResponse
import com.authapi.routes.authRoutes
import com.authapi.routes.chatRoutes
import com.authapi.routes.guestRoutes
import com.authapi.routes.marketplaceRoutes
import com.authapi.routes.notificationRoutes
import com.authapi.services.AuthService
import com.authapi.services.ChatService
import com.authapi.services.GuestService
import com.authapi.services.MarketplaceService
import com.authapi.services.NotificationService
import com.authapi.services.SupabaseStorageService
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting(
    authService: AuthService,
    notificationService: NotificationService,
    marketplaceService: MarketplaceService,
    guestService: GuestService,
    chatService: ChatService,
    supabaseStorageService: SupabaseStorageService
) {
    routing {
        get("/health") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "OK", "service" to "Auth API", "version" to "4.0.0"))
        }
        authRoutes(authService)
        notificationRoutes(notificationService)
        marketplaceRoutes(marketplaceService)
        guestRoutes(guestService)
        chatRoutes(chatService)

        // ── Image Upload ──────────────────────────────────────────────────────
        post("/upload-image") {
            val multipart = call.receiveMultipart()
            var imageUrl: String? = null

            multipart.forEachPart { part ->
                if (part is PartData.FileItem) {
                    val bytes = part.streamProvider().readBytes()
                    val folder = call.request.queryParameters["folder"] ?: "general"
                    imageUrl = supabaseStorageService.uploadImage(
                        imageBytes = bytes,
                        folder = folder
                    )
                }
                part.dispose()
            }

            if (imageUrl == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<Unit>(false, "No image provided", null)
                )
                return@post
            }

            call.respond(ApiResponse(true, "Image uploaded successfully", mapOf("url" to imageUrl!!)))
        }
    }
}