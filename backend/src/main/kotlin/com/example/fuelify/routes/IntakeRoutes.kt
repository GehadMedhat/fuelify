package com.example.fuelify.routes


import com.example.fuelify.models.*
import com.example.fuelify.repository.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * /api/water/intake  — mirrors WaterIntakeActivity
 *
 * GET    /api/water/intake/logs       → today's logs (newest first)
 * POST   /api/water/intake/add        → add a water log { amountMl }
 * DELETE /api/water/intake/{timestamp}→ delete a specific log entry
 */
fun Route.intakeRoutes() {

    route("/intake") {

        // GET today's logs
        get("/logs") {
            val logs = WaterTrackerRepository.getTodayLogs()
            call.respond(ApiResponse(success = true, data = logs))
        }

        // POST add water
        post("/add") {
            val req = call.receive<AddWaterRequest>()
            if (req.amountMl <= 0) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<WaterLog>(success = false, message = "Amount must be > 0")
                )
                return@post
            }
            val entry = WaterTrackerRepository.addLog(req.amountMl)
            call.respond(
                HttpStatusCode.Created,
                ApiResponse(success = true, data = entry, message = "+${req.amountMl} ml added!")
            )
        }

        // DELETE a log by timestamp
        delete("/{timestamp}") {
            val ts = call.parameters["timestamp"]?.toLongOrNull()
            if (ts == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<Unit>(success = false, message = "Invalid timestamp")
                )
                return@delete
            }
            val removed = WaterTrackerRepository.deleteLog(ts)
            if (removed) {
                call.respond(ApiResponse<Unit>(success = true, message = "Log deleted"))
            } else {
                call.respond(
                    HttpStatusCode.NotFound,
                    ApiResponse<Unit>(success = false, message = "Log not found")
                )
            }
        }
    }
}
