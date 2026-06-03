package com.example.fuelify.routes

import com.example.fuelify.models.*
import com.example.fuelify.repository.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.Calendar

/**
 * All Blood Pressure & Blood Sugar Tracker endpoints,
 * mounted under /api/bp in Application.kt.
 *
 * Blood Pressure:
 *   GET    /api/bp/readings                 → all BP readings, newest first
 *   GET    /api/bp/readings/latest          → latest BP reading (or null)
 *   POST   /api/bp/readings                 → add a new BP reading
 *   DELETE /api/bp/readings/{id}            → delete a BP reading by id
 *   GET    /api/bp/stats?year=&month=       → full BpStatsData for a given month
 *
 * Blood Sugar:
 *   GET    /api/bp/sugar/readings           → all BS readings, newest first
 *   GET    /api/bp/sugar/readings/latest    → latest BS reading (or null)
 *   POST   /api/bp/sugar/readings           → add a new BS reading
 *   DELETE /api/bp/sugar/readings/{id}      → delete a BS reading by id
 *   GET    /api/bp/sugar/stats?year=&month= → full BsStatsData for a given month
 */
fun Route.bloodPressureRoutes() {

    // ── Blood Pressure ────────────────────────────────────────────────────────

    route("/readings") {

        // GET all BP readings — newest first
        get {
            call.respond(
                ApiResponse(success = true, data = BloodPressureRepository.getAllBpReadings())
            )
        }

        // GET latest BP reading
        get("/latest") {
            call.respond(
                ApiResponse(success = true, data = BloodPressureRepository.getLatestBpReading())
            )
        }

        // POST — add a new BP reading
        post {
            val req = call.receive<AddBpReadingRequest>()

            // Validate ranges
            if (req.systolic !in 60..300) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<Unit>(success = false, message = "Systolic must be between 60 and 300 mmHg")
                )
                return@post
            }
            if (req.diastolic !in 40..200) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<Unit>(success = false, message = "Diastolic must be between 40 and 200 mmHg")
                )
                return@post
            }
            if (req.pulse !in 30..250) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<Unit>(success = false, message = "Pulse must be between 30 and 250 bpm")
                )
                return@post
            }
            if (req.systolic <= req.diastolic) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<Unit>(success = false, message = "Systolic must be greater than diastolic")
                )
                return@post
            }

            // FIX: pass the request directly; repository fills all fields including
            // formattedTime, category, categoryLabel so they are serialized correctly.
            val reading = BloodPressureRepository.addBloodPressureReading(req)

            call.respond(
                HttpStatusCode.Created,
                ApiResponse(
                    success = true,
                    data    = reading,
                    message = "Saved! Status: ${reading.categoryLabel} (${reading.systolic}/${reading.diastolic} mmHg)"
                )
            )
        }

        // DELETE a BP reading by id
        delete("/{id}") {
            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@delete call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<Unit>(success = false, message = "Invalid id")
                )
            val removed = BloodPressureRepository.deleteBpReading(id)
            if (removed) {
                call.respond(ApiResponse<Unit>(success = true, message = "Reading deleted"))
            } else {
                call.respond(
                    HttpStatusCode.NotFound,
                    ApiResponse<Unit>(success = false, message = "Reading not found")
                )
            }
        }
    }

    // GET /api/bp/stats?year=2026&month=3  (month is 0-based like Calendar.MONTH)
    get("/stats") {
        val now   = Calendar.getInstance()
        val year  = call.request.queryParameters["year"]?.toIntOrNull()  ?: now.get(Calendar.YEAR)
        val month = call.request.queryParameters["month"]?.toIntOrNull() ?: now.get(Calendar.MONTH)

        val monthReadings = BloodPressureRepository.getBpReadingsForMonth(year, month)

        val monthlyAvgSys = if (monthReadings.isNotEmpty())
            monthReadings.map { it.systolic }.average().toInt() else null
        val monthlyAvgDia = if (monthReadings.isNotEmpty())
            monthReadings.map { it.diastolic }.average().toInt() else null

        val prevCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year); set(Calendar.MONTH, month)
            add(Calendar.MONTH, -1)
        }
        val prevMonthReadings = BloodPressureRepository.getBpReadingsForMonth(
            prevCal.get(Calendar.YEAR), prevCal.get(Calendar.MONTH)
        )

        val systolicTrend  = buildBpTrend(
            curr = if (monthReadings.isNotEmpty()) monthReadings.map { it.systolic }.average() else null,
            prev = if (prevMonthReadings.isNotEmpty()) prevMonthReadings.map { it.systolic }.average() else null
        )
        val diastolicTrend = buildBpTrend(
            curr = if (monthReadings.isNotEmpty()) monthReadings.map { it.diastolic }.average() else null,
            prev = if (prevMonthReadings.isNotEmpty()) prevMonthReadings.map { it.diastolic }.average() else null
        )

        val weeklyAvg = BloodPressureRepository.getBpWeeklyAverageForMonth(year, month)

        call.respond(
            ApiResponse(
                success = true,
                data = BpStatsData(
                    monthlyAvgSystolic  = monthlyAvgSys,
                    monthlyAvgDiastolic = monthlyAvgDia,
                    systolicTrend       = systolicTrend,
                    diastolicTrend      = diastolicTrend,
                    latestReading       = BloodPressureRepository.getLatestBpReadingForMonth(year, month),
                    weeklyAvgSystolic   = weeklyAvg?.first,
                    weeklyAvgDiastolic  = weeklyAvg?.second,
                    highestReading      = monthReadings.maxByOrNull { it.systolic },
                    lowestReading       = monthReadings.minByOrNull { it.systolic }
                )
            )
        )
    }

    // ── Blood Sugar ───────────────────────────────────────────────────────────

    route("/sugar/readings") {

        // GET all BS readings — newest first
        get {
            call.respond(
                ApiResponse(success = true, data = BloodPressureRepository.getAllBsReadings())
            )
        }

        // GET latest BS reading
        get("/latest") {
            call.respond(
                ApiResponse(success = true, data = BloodPressureRepository.getLatestBsReading())
            )
        }

        // POST — add a new BS reading
        post {
            val req = call.receive<AddBsReadingRequest>()

            val validMealTypes = setOf("Fasting", "Before Meal", "After Meal", "Bedtime")
            if (req.mealType !in validMealTypes) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<Unit>(
                        success = false,
                        message = "Invalid mealType '${req.mealType}'. Valid values: ${validMealTypes.joinToString()}"
                    )
                )
                return@post
            }
            if (req.glucose <= 0) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<Unit>(success = false, message = "Glucose must be a positive value")
                )
                return@post
            }

            // FIX: pass the request directly; repository fills all fields.
            val reading = BloodPressureRepository.addBloodSugarReading(req)

            call.respond(
                HttpStatusCode.Created,
                ApiResponse(
                    success = true,
                    data    = reading,
                    message = "Saved! Status: ${reading.categoryLabel} (${reading.glucose} mg/dL)"
                )
            )
        }

        // DELETE a BS reading by id
        delete("/{id}") {
            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@delete call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<Unit>(success = false, message = "Invalid id")
                )
            val removed = BloodPressureRepository.deleteBsReading(id)
            if (removed) {
                call.respond(ApiResponse<Unit>(success = true, message = "Reading deleted"))
            } else {
                call.respond(
                    HttpStatusCode.NotFound,
                    ApiResponse<Unit>(success = false, message = "Reading not found")
                )
            }
        }
    }

    // GET /api/bp/sugar/stats?year=2026&month=3
    get("/sugar/stats") {
        val now   = Calendar.getInstance()
        val year  = call.request.queryParameters["year"]?.toIntOrNull()  ?: now.get(Calendar.YEAR)
        val month = call.request.queryParameters["month"]?.toIntOrNull() ?: now.get(Calendar.MONTH)

        val monthReadings     = BloodPressureRepository.getBsReadingsForMonth(year, month)
        val fastingReadings   = monthReadings.filter { it.mealType == "Fasting" }
        val afterMealReadings = monthReadings.filter { it.mealType == "After Meal" }

        val monthlyAvg = if (monthReadings.isNotEmpty())
            monthReadings.map { it.glucose }.average().toInt() else null

        val prevCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year); set(Calendar.MONTH, month)
            add(Calendar.MONTH, -1)
        }
        val prevMonthReadings = BloodPressureRepository.getBsReadingsForMonth(
            prevCal.get(Calendar.YEAR), prevCal.get(Calendar.MONTH)
        )
        val prevFasting   = prevMonthReadings.filter { it.mealType == "Fasting" }
        val prevAfterMeal = prevMonthReadings.filter { it.mealType == "After Meal" }

        val fastingTrend = buildBpTrend(
            curr = fastingReadings.map { it.glucose }.average().takeIf { fastingReadings.isNotEmpty() },
            prev = prevFasting.map { it.glucose }.average().takeIf { prevFasting.isNotEmpty() }
        )
        val afterMealTrend = buildBpTrend(
            curr = afterMealReadings.map { it.glucose }.average().takeIf { afterMealReadings.isNotEmpty() },
            prev = prevAfterMeal.map { it.glucose }.average().takeIf { prevAfterMeal.isNotEmpty() }
        )

        call.respond(
            ApiResponse(
                success = true,
                data = BsStatsData(
                    monthlyAvg     = monthlyAvg,
                    fastingTrend   = fastingTrend,
                    afterMealTrend = afterMealTrend,
                    latestReading  = BloodPressureRepository.getLatestBsReadingForMonth(year, month),
                    weeklyAvg      = BloodPressureRepository.getBsWeeklyAverageForMonth(year, month),
                    highestReading = monthReadings.maxByOrNull { it.glucose },
                    lowestReading  = monthReadings.minByOrNull { it.glucose }
                )
            )
        )
    }
}

// ── Trend helper ──────────────────────────────────────────────────────────────

private fun buildBpTrend(curr: Double?, prev: Double?): BpTrendInfo {
    if (curr == null && prev == null) {
        return BpTrendInfo(direction = TrendDirection.FLAT.name, delta = "No data", color = "#9E9E9E")
    }
    if (prev == null || curr == null) {
        val value = curr ?: prev!!
        return BpTrendInfo(
            direction = TrendDirection.FLAT.name,
            delta     = String.format("%.0f", value),
            color     = "#9E9E9E"
        )
    }
    val delta   = curr - prev
    val rounded = String.format("%.1f", Math.abs(delta))
    return when {
        delta > 0.05  -> BpTrendInfo(TrendDirection.UP.name,   "+$rounded", "#F44336")
        delta < -0.05 -> BpTrendInfo(TrendDirection.DOWN.name, "-$rounded", "#4CAF50")
        else          -> BpTrendInfo(TrendDirection.FLAT.name, "No change", "#9E9E9E")
    }
}
