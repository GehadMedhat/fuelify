package com.authapi

import com.authapi.database.DatabaseFactory
import com.authapi.plugins.*
import com.authapi.services.AuthService
import com.authapi.services.EmailService
import com.authapi.services.NotificationService
import com.authapi.scheduler.NotificationScheduler
import com.authapi.services.ChatService
import com.authapi.services.MarketplaceService
import com.authapi.services.GuestService
import com.authapi.services.SupabaseStorageService
import com.authapi.utils.JwtUtils
import io.ktor.server.application.*
import io.ktor.server.netty.*

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
    DatabaseFactory.init(this)

    val jwtUtils              = JwtUtils(this)
    val emailService          = EmailService(this)
    val notificationService   = NotificationService()
    val authService           = AuthService(this, emailService, jwtUtils, notificationService)
    val notificationScheduler = NotificationScheduler(notificationService)
    val marketplaceService    = MarketplaceService()
    val guestService = GuestService(this, emailService, jwtUtils)
    val groqApiKey = environment.config.property("groq.apiKey").getString()
    val chatService = ChatService(groqApiKey)
    val supabaseStorageService = SupabaseStorageService(
        supabaseUrl = environment.config.property("supabase.url").getString(),
        supabaseKey = environment.config.property("supabase.key").getString(),
        bucket = environment.config.property("supabase.bucket").getString()
    )

    configureSerialization()
    configureCORS()
    configureStatusPages()
    configureAuthentication(authService)
    configureRouting(authService, notificationService, marketplaceService, guestService, chatService, supabaseStorageService)

    notificationScheduler.start()
    environment.monitor.subscribe(ApplicationStopped) {
        notificationScheduler.stop()
    }
}