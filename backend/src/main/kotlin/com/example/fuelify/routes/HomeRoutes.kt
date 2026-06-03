package com.example.fuelify.routes



import com.example.fuelify.models.*
import com.example.fuelify.repository.*

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * /api/water  — top-level Water Tracker routes
 *
 * GET /api/water/home          → HomeSummary (mirrors WaterHomeActivity progress UI)
 * GET /api/water/goal          → current daily goal in ml
 * PUT /api/water/goal          → update daily goal { goalMl }
 */
fun Route.homeRoutes() {

    // ── Home summary (mirrors WaterHomeActivity updateUI()) ──────────────────
    get("/home") {
        val goal      = WaterTrackerRepository.getDailyGoal()
        val total     = WaterTrackerRepository.getTodayTotal()
        val percent   = WaterTrackerRepository.getTodayProgressPercent()
        val remaining = (goal - total).coerceAtLeast(0)

        val summary = HomeSummary(
            dailyGoalMl               = goal,
            todayTotalMl              = total,
            todayProgressPercent      = percent,
            todayRemainingMl          = remaining,
            weeklyTotalMl             = WaterTrackerRepository.getWeeklyTotal(),
            monthlyTotalMl            = WaterTrackerRepository.getMonthlyTotal(),
            goalCompletionPercent7Days = WaterTrackerRepository.getTotalGoalCompletionPercent(7),
            dailyAverageMl7Days       = WaterTrackerRepository.getDailyAverageMl(7),
            dailyAverageLiters7Days   = WaterTrackerRepository.getDailyAverageLiters(7)
        )
        call.respond(ApiResponse(success = true, data = summary))
    }

    // ── Daily goal ────────────────────────────────────────────────────────────
    get("/goal") {
        call.respond(
            ApiResponse(
                success = true,
                data    = mapOf("goalMl" to WaterTrackerRepository.getDailyGoal())
            )
        )
    }

    put("/goal") {
        val req = call.receive<DailyGoalRequest>()
        if (req.goalMl <= 0) {
            call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(success = false, message = "Goal must be > 0 ml")
            )
            return@put
        }
        WaterTrackerRepository.setDailyGoal(req.goalMl)
        call.respond(
            ApiResponse(
                success = true,
                data    = mapOf("goalMl" to req.goalMl),
                message = "Daily goal updated to ${req.goalMl} ml"
            )
        )
    }
}
