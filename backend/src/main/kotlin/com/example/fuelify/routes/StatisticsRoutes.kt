package com.example.fuelify.routes

import com.example.fuelify.models.*
import com.example.fuelify.repository.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * /api/water/statistics  — mirrors WaterStatisticsActivity (Daily / Weekly / Monthly tabs)
 *
 * GET  /api/water/statistics/daily            → today's stats + hourly chart data
 * GET  /api/water/statistics/weekly           → last-7-days stats + per-day chart data
 * GET  /api/water/statistics/monthly          → current-month stats + per-day chart data
 * GET  /api/water/statistics/quarterly        → last 3 months (default)
 * POST /api/water/statistics/quarterly/custom → user-selected months { months:[{year,month}] }
 */
fun Route.statisticsRoutes() {

    route("/statistics") {

        // ── DAILY ─────────────────────────────────────────────────────────────
        get("/daily") {
            val total   = WaterTrackerRepository.getTodayTotal()
            val goal    = WaterTrackerRepository.getDailyGoal()
            val percent = WaterTrackerRepository.getTodayProgressPercent()
            val hourly  = WaterTrackerRepository.getTodayHourlyData()
                .map { (label, ml) -> HourlyEntry(label, ml) }

            call.respond(
                ApiResponse(
                    success = true,
                    data = DailyStats(
                        totalMl       = total,
                        goalMl        = goal,
                        progressPercent = percent,
                        hourlyBreakdown = hourly
                    )
                )
            )
        }

        // ── WEEKLY ────────────────────────────────────────────────────────────
        get("/weekly") {
            val goal        = WaterTrackerRepository.getDailyGoal()
            val weeklyData  = WaterTrackerRepository.getWeeklyData()
            val weeklyTotal = WaterTrackerRepository.getWeeklyTotal()
            val completion  = WaterTrackerRepository.getTotalGoalCompletionPercent(7)
            val avgMl       = WaterTrackerRepository.getDailyAverageMl(7)
            val days        = weeklyData.map { (label, ml) -> DayEntry(label, ml) }

            call.respond(
                ApiResponse(
                    success = true,
                    data = WeeklyStats(
                        totalMl               = weeklyTotal,
                        goalMl                = goal,
                        goalCompletionPercent = completion,
                        dailyAverageMl        = avgMl,
                        days                  = days
                    )
                )
            )
        }

        // ── MONTHLY ───────────────────────────────────────────────────────────
        get("/monthly") {
            val goal         = WaterTrackerRepository.getDailyGoal()
            val monthlyData  = WaterTrackerRepository.getMonthlyData()
            val monthlyTotal = WaterTrackerRepository.getMonthlyTotal()
            val daysElapsed  = monthlyData.size  // عدد الأيام الفعلية من أول الشهر
            val completion   = WaterTrackerRepository.getMonthlyGoalCompletionPercent()
            val avgMl        = WaterTrackerRepository.getDailyAverageMl(daysElapsed)
            val days         = monthlyData.map { (day, ml) -> MonthDayEntry(day, ml) }

            call.respond(
                ApiResponse(
                    success = true,
                    data = MonthlyStats(
                        totalMl               = monthlyTotal,
                        goalMl                = goal,
                        goalCompletionPercent = completion,
                        dailyAverageMl        = avgMl,
                        days                  = days
                    )
                )
            )
        }

        // ── QUARTERLY — default (last 3 months) ───────────────────────────────
        get("/quarterly") {
            val data = WaterTrackerRepository.getQuarterlyData()
            call.respond(ApiResponse(success = true, data = QuarterlyStats(months = data)))
        }

        // ── QUARTERLY — custom months selected by user ────────────────────────
        post("/quarterly/custom") {
            val req = call.receive<QuarterlyRequest>()
            if (req.months.isEmpty() || req.months.size > 3) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<QuarterlyStats>(
                        success = false,
                        message = "Select between 1 and 3 months"
                    )
                )
                return@post
            }
            val pairs = req.months.map { Pair(it.year, it.month) }
            val data  = WaterTrackerRepository.getQuarterlyDataForMonths(pairs)
            call.respond(ApiResponse(success = true, data = QuarterlyStats(months = data)))
        }
    }
}
