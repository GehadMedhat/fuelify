package com.example.fuelify.routes

import com.example.fuelify.models.*
import com.example.fuelify.repository.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * All Mood Tracker endpoints, mounted under /api/mood in Application.kt.
 *
 * GET    /api/mood/home                        → MoodHomeData  (MoodHomeActivity)
 * GET    /api/mood/entries                     → all entries, newest first
 * POST   /api/mood/entries                     → log / replace today's mood  { "mood": "AMAZING" }
 * GET    /api/mood/entries/today               → today's entry or null
 * DELETE /api/mood/entries/today               → remove today's entry
 * GET    /api/mood/stats                       → full MoodStatsData  (MoodStatsActivity)
 * GET    /api/mood/stats/breakdown             → per-mood count + percentage
 * GET    /api/mood/stats/calendar?month=yyyy-MM→ calendar entries for a month
 */
fun Route.moodRoutes() {

    // ── Home ──────────────────────────────────────────────────────────────────
    get("/home") {
        call.respond(
            ApiResponse(
                success = true,
                data = MoodHomeData(
                    dayStreak = MoodRepository.getDayStreak(),
                    totalLogs = MoodRepository.getTotalLogs(),
                    todayMood = MoodRepository.getTodayMood()?.name
                )
            )
        )
    }

    // ── Entries ───────────────────────────────────────────────────────────────
    route("/entries") {

        // GET all entries — newest first
        get {
            val all = MoodRepository.getAllEntries().sortedByDescending { it.timestamp }
            call.respond(ApiResponse(success = true, data = all))
        }

        // POST — add / replace today's mood
        post {
            val req = call.receive<AddMoodRequest>()
            val moodType = try {
                MoodType.valueOf(req.mood.uppercase())
            } catch (_: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<MoodEntry>(
                        success = false,
                        message = "Invalid mood '${req.mood}'. Valid values: ${MoodType.values().joinToString { it.name }}"
                    )
                )
                return@post
            }
            val entry = MoodRepository.addEntry(moodType)
            call.respond(
                HttpStatusCode.Created,
                ApiResponse(
                    success = true,
                    data    = entry,
                    message = "Mood logged: ${moodType.emoji} ${moodType.label}"
                )
            )
        }

        // GET today's entry (null if none logged yet)
        get("/today") {
            val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val entry    = MoodRepository.getAllEntries().firstOrNull { it.dateKey == todayKey }
            call.respond(ApiResponse(success = true, data = entry))
        }

        // DELETE today's entry
        delete("/today") {
            val removed = MoodRepository.deleteToday()
            if (removed) {
                call.respond(ApiResponse<Unit>(success = true, message = "Today's mood entry deleted"))
            } else {
                call.respond(
                    HttpStatusCode.NotFound,
                    ApiResponse<Unit>(success = false, message = "No mood entry found for today")
                )
            }
        }
    }

    // ── Stats ─────────────────────────────────────────────────────────────────
    route("/stats") {

        // Full stats snapshot — everything MoodStatsActivity needs in one call
        get {
            val monthKey = MoodRepository.currentMonthKey()
            call.respond(
                ApiResponse(
                    success = true,
                    data = MoodStatsData(
                        totalLogs       = MoodRepository.getTotalLogs(),
                        mostCommonMood  = MoodRepository.getMostCommonMood()?.name,
                        mostCommonEmoji = MoodRepository.getMostCommonMood()?.emoji,
                        moodCounts      = MoodRepository.getMoodCounts(),
                        calendarMonth   = monthKey,
                        calendarLabel   = MoodRepository.currentMonthLabel(),
                        calendarEntries = MoodRepository.getEntriesForMonth(monthKey)
                    )
                )
            )
        }

        // Mood breakdown — percentage per mood (non-zero moods only)
        get("/breakdown") {
            val counts = MoodRepository.getMoodCounts()
            val total  = counts.values.sum().coerceAtLeast(1)
            val breakdown = MoodType.values()
                .filter { (counts[it.name] ?: 0) > 0 }
                .map { mood ->
                    val count = counts[mood.name] ?: 0
                    MoodBreakdownItem(
                        mood    = mood.name,
                        label   = mood.label,
                        emoji   = mood.emoji,
                        count   = count,
                        percent = count * 100 / total
                    )
                }
            call.respond(ApiResponse(success = true, data = breakdown))
        }

        // Calendar for a given month; defaults to current month if omitted
        get("/calendar") {
            val monthKey = call.request.queryParameters["month"]
                ?: MoodRepository.currentMonthKey()
            call.respond(
                ApiResponse(success = true, data = MoodRepository.getEntriesForMonth(monthKey))
            )
        }
    }
}
