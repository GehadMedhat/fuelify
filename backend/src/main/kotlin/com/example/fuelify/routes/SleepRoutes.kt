package com.example.fuelify.routes

import com.example.fuelify.models.*
import com.example.fuelify.repository.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.sleepRoutes() {

    // ── GET /api/sleep/today ──────────────────────────────────────────────────
    get("/today") {
        val s = SleepRepository.getTodaySchedule()
        call.respond(ApiResponse(
            success = true,
            data    = s.toResponse()
        ))
    }

    // ── GET /api/sleep/schedules ──────────────────────────────────────────────
    get("/schedules") {
        val list = SleepRepository.getAllSchedules().map { it.toResponse() }
        call.respond(ApiResponse(success = true, data = list))
    }

    // ── GET /api/sleep/schedules/{day} ────────────────────────────────────────
    get("/schedules/{day}") {
        val day = call.parameters["day"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Unit>(success = false, message = "Invalid day"))
        if (day !in 1..7) return@get call.respond(HttpStatusCode.BadRequest,
            ApiResponse<Unit>(success = false, message = "Day must be 1-7"))

        call.respond(ApiResponse(success = true, data = SleepRepository.getScheduleForDay(day).toResponse()))
    }

    // ── PUT /api/sleep/schedules/{day} ────────────────────────────────────────
    put("/schedules/{day}") {
        val day = call.parameters["day"]?.toIntOrNull()
            ?: return@put call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Unit>(success = false, message = "Invalid day"))
        if (day !in 1..7) return@put call.respond(HttpStatusCode.BadRequest,
            ApiResponse<Unit>(success = false, message = "Day must be 1-7"))

        val req = call.receive<UpdateScheduleRequest>()
        val updated = DaySchedule(
            dayOfWeek      = day,
            bedtimeHour    = req.bedtimeHour,
            bedtimeMinute  = req.bedtimeMinute,
            hoursOfSleep   = req.hoursOfSleep,
            minutesOfSleep = req.minutesOfSleep,
            repeatDays     = req.repeatDays,
            vibrateEnabled = req.vibrateEnabled,
            bedtimeEnabled = req.bedtimeEnabled,
            alarmEnabled   = req.alarmEnabled
        )
        SleepRepository.updateScheduleForDay(updated)
        call.respond(ApiResponse(success = true, data = updated.toResponse(), message = "Schedule updated"))
    }

    // ── PATCH /api/sleep/schedules/{day}/bedtime-toggle ───────────────────────
    patch("/schedules/{day}/bedtime-toggle") {
        val day = call.parameters["day"]?.toIntOrNull()
            ?: return@patch call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Unit>(success = false, message = "Invalid day"))
        val req = call.receive<ToggleRequest>()
        SleepRepository.toggleBedtime(day, req.enabled)
        call.respond(ApiResponse<Unit>(success = true, message = "Bedtime toggled"))
    }

    // ── PATCH /api/sleep/schedules/{day}/alarm-toggle ─────────────────────────
    patch("/schedules/{day}/alarm-toggle") {
        val day = call.parameters["day"]?.toIntOrNull()
            ?: return@patch call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Unit>(success = false, message = "Invalid day"))
        val req = call.receive<ToggleRequest>()
        SleepRepository.toggleAlarm(day, req.enabled)
        call.respond(ApiResponse<Unit>(success = true, message = "Alarm toggled"))
    }
}

// ── Extension: DaySchedule → TodayScheduleResponse ───────────────────────────
private fun DaySchedule.toResponse() = TodayScheduleResponse(
    dayOfWeek        = dayOfWeek,
    bedtimeFormatted = bedtimeFormatted(),
    alarmFormatted   = alarmFormatted(),
    sleepDuration    = sleepDurationLabel(),
    sleepQualityPct  = sleepQualityPercent(),
    bedtimeHour      = bedtimeHour,
    bedtimeMinute    = bedtimeMinute,
    wakeHour         = wakeHour,
    wakeMinute       = wakeMinute,
    bedtimeEnabled   = bedtimeEnabled,
    alarmEnabled     = alarmEnabled,
    countdownBedtime = com.example.fuelify.repository.SleepRepository.countdownTo(bedtimeHour, bedtimeMinute),
    countdownAlarm   = com.example.fuelify.repository.SleepRepository.countdownTo(wakeHour, wakeMinute)
)
