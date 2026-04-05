package com.example.fuelify.plugins

import com.example.fuelify.routes.dashboardRoutes
import com.example.fuelify.routes.mealDetailRoutes
import com.example.fuelify.routes.mealSearchRoutes
import com.example.fuelify.routes.kitchenOrderRoutes
import com.example.fuelify.routes.userRoutes
import com.example.fuelify.routes.ingredientRoutes
import com.example.fuelify.routes.familyRoutes
import com.example.fuelify.routes.ecoRoutes
import com.example.fuelify.routes.scannedPantryRoutes
import com.example.fuelify.routes.workoutRoutes
import com.example.fuelify.routes.workoutSessionRoutes
import com.example.fuelify.routes.medicalRoutes
import com.example.fuelify.routes.doctorConsultationRoutes
import com.example.fuelify.routes.doctorRoutes
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

fun Application.configureCORS() {
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
    }
}

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(Json { prettyPrint = true; isLenient = true; ignoreUnknownKeys = true })
    }
}

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondText(
                text = """{"success":false,"message":"${cause.message}","data":null}""",
                contentType = ContentType.Application.Json,
                status = HttpStatusCode.InternalServerError
            )
        }
        status(HttpStatusCode.NotFound) { call, _ ->
            call.respondText(
                text = """{"success":false,"message":"Route not found","data":null}""",
                contentType = ContentType.Application.Json
            )
        }
    }
}

fun Application.configureRouting() {
    routing {
        get("/health") { call.respondText("OK") }
        route("/api") {
            userRoutes()
            dashboardRoutes()
            mealDetailRoutes()
            mealSearchRoutes()
            kitchenOrderRoutes()
            ingredientRoutes()
            familyRoutes()
            ecoRoutes()
            scannedPantryRoutes()
            workoutRoutes()
            workoutSessionRoutes()
            medicalRoutes()
            doctorConsultationRoutes()
            doctorRoutes()
        }
    }
}
