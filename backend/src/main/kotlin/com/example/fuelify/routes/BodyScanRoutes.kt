package com.example.fuelify.routes

import com.example.fuelify.models.*
import com.example.fuelify.repository.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * All Body Scan endpoints, mounted under /api/bodyscan in Application.kt.
 *
 * Records
 *   GET    /api/bodyscan/records                → all records, newest-first
 *   GET    /api/bodyscan/records/latest         → latest record (or null)
 *   GET    /api/bodyscan/records/today          → today's records + latest
 *   POST   /api/bodyscan/records                → save a new scan result
 *   DELETE /api/bodyscan/records/{timestamp}    → delete a record by timestamp
 *
 * Statistics
 *   GET    /api/bodyscan/stats                  → latest record, body-fat change,
 *                                                 monthly history
 */
fun Route.bodyScanRoutes() {

    // ── Records ───────────────────────────────────────────────────────────────

    route("/records") {

        // GET all records — newest-first
        get {
            call.respond(
                ApiResponse(success = true, data = BodyScanRepository.getAllRecords())
            )
        }

        // GET latest record (null-safe)
        get("/latest") {
            call.respond(
                ApiResponse(success = true, data = BodyScanRepository.getLatestRecord())
            )
        }

        // GET today's records + the latest of today
        get("/today") {
            val todayRecords = BodyScanRepository.getTodayRecords()
            call.respond(
                ApiResponse(
                    success = true,
                    data = TodayBodyScanData(
                        records      = todayRecords,
                        latestRecord = todayRecords.firstOrNull()
                    )
                )
            )
        }

        // POST — save a new scan result
        post {
            val req = call.receive<AddBodyScanRequest>()

            // Validate fields (mirrors Android's analysePhoto() output ranges)
            if (req.bodyFatPercent !in 0.0..100.0) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<Unit>(success = false, message = "bodyFatPercent must be between 0 and 100")
                )
                return@post
            }
            if (req.muscleMassPercent !in 0.0..100.0) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<Unit>(success = false, message = "muscleMassPercent must be between 0 and 100")
                )
                return@post
            }
            if (req.waterPercent !in 0.0..100.0) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<Unit>(success = false, message = "waterPercent must be between 0 and 100")
                )
                return@post
            }
            if (req.bmi <= 0.0) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<Unit>(success = false, message = "bmi must be a positive value")
                )
                return@post
            }
            if (req.bodyType.isBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<Unit>(success = false, message = "bodyType must not be empty")
                )
                return@post
            }

            val saved = BodyScanRepository.saveRecord(req)
            call.respond(
                HttpStatusCode.Created,
                ApiResponse(success = true, data = saved, message = "Body scan result saved successfully")
            )
        }

        // DELETE — remove a record by its timestamp (unique identifier)
        delete("/{timestamp}") {
            val timestamp = call.parameters["timestamp"]?.toLongOrNull()
            if (timestamp == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<Unit>(success = false, message = "Invalid or missing timestamp parameter")
                )
                return@delete
            }

            val removed = BodyScanRepository.deleteRecord(timestamp)
            if (removed) {
                call.respond(
                    ApiResponse<Unit>(success = true, message = "Record deleted successfully")
                )
            } else {
                call.respond(
                    HttpStatusCode.NotFound,
                    ApiResponse<Unit>(success = false, message = "No record found with timestamp $timestamp")
                )
            }
        }
    }

    // ── Statistics ────────────────────────────────────────────────────────────

    // GET full stats — mirrors BodyStatisticsActivity data needs
    get("/stats") {
        call.respond(
            ApiResponse(
                success = true,
                data = BodyScanStatsData(
                    latestRecord   = BodyScanRepository.getLatestRecord(),
                    bodyFatChange  = BodyScanRepository.getBodyFatChange(),
                    monthlyHistory = BodyScanRepository.getMonthlyBodyFatHistory()
                )
            )
        )
    }
}
