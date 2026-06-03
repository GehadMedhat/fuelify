package com.example.fuelify.routes

import com.example.fuelify.models.*
import com.example.fuelify.repository.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

/**
 * /api/water/reminders  — mirrors WaterRemindersActivity
 *
 * GET    /api/water/reminders                 → list all reminders
 * POST   /api/water/reminders                 → add a new reminder
 * PUT    /api/water/reminders/{id}            → edit an existing reminder (time/label)
 * PATCH  /api/water/reminders/{id}/toggle     → enable/disable { isEnabled }
 * DELETE /api/water/reminders/{id}            → delete a reminder
 * GET    /api/water/reminders/auto            → get auto-reminder state
 * PUT    /api/water/reminders/auto            → set auto-reminder state { isEnabled }
 */
fun Route.remindersRoutes() {

    route("/reminders") {

        // ── Auto-reminder switch ───────────────────────────────────────────────
        route("/auto") {
            get {
                call.respond(
                    ApiResponse(
                        success = true,
                        data    = mapOf("isEnabled" to WaterTrackerRepository.isAutoReminderEnabled())
                    )
                )
            }
            put {
                val req = call.receive<ReminderToggleRequest>()
                WaterTrackerRepository.setAutoReminderEnabled(req.isEnabled)
                val msg = if (req.isEnabled) "Auto reminders enabled ✅" else "Auto reminders disabled"
                call.respond(ApiResponse<Unit>(success = true, message = msg))
            }
        }

        // ── List all reminders ────────────────────────────────────────────────
        get {
            val reminders = WaterTrackerRepository.getReminders()
            call.respond(ApiResponse(success = true, data = reminders))
        }

        // ── Add new reminder ──────────────────────────────────────────────────
        post {
            val req = call.receive<ReminderItem>()

            // Validate hour/minute range
            if (req.hour !in 0..23 || req.minute !in 0..59) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<ReminderItem>(success = false, message = "Invalid time values")
                )
                return@post
            }

            val newItem = req.copy(id = UUID.randomUUID().toString())
            WaterTrackerRepository.addReminder(newItem)

            val amPm  = if (newItem.hour < 12) "AM" else "PM"
            val h12   = when {
                newItem.hour == 0  -> 12
                newItem.hour > 12  -> newItem.hour - 12
                else               -> newItem.hour
            }
            val label = String.format("%02d:%02d %s", h12, newItem.minute, amPm)
            call.respond(
                HttpStatusCode.Created,
                ApiResponse(
                    success = true,
                    data    = newItem,
                    message = "Reminder added for $label ✅"
                )
            )
        }

        // ── Edit reminder (update time/label) ─────────────────────────────────
        put("/{id}") {
            val id  = call.parameters["id"] ?: return@put call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<ReminderItem>(success = false, message = "Missing id")
            )
            val req = call.receive<ReminderItem>()

            if (req.hour !in 0..23 || req.minute !in 0..59) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<ReminderItem>(success = false, message = "Invalid time values")
                )
                return@put
            }

            val amPm  = if (req.hour < 12) "AM" else "PM"
            val h12   = when {
                req.hour == 0  -> 12
                req.hour > 12  -> req.hour - 12
                else           -> req.hour
            }
            val label   = String.format("%02d:%02d %s", h12, req.minute, amPm)
            val updated = req.copy(id = id, timeLabel = label)

            val ok = WaterTrackerRepository.updateReminder(id, updated)
            if (ok) {
                call.respond(
                    ApiResponse(
                        success = true,
                        data    = updated,
                        message = "Reminder updated to $label ✅"
                    )
                )
            } else {
                call.respond(
                    HttpStatusCode.NotFound,
                    ApiResponse<ReminderItem>(success = false, message = "Reminder not found")
                )
            }
        }

        // ── Toggle enable/disable ─────────────────────────────────────────────
        patch("/{id}/toggle") {
            val id  = call.parameters["id"] ?: return@patch call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(success = false, message = "Missing id")
            )
            val req = call.receive<ReminderToggleRequest>()
            val ok  = WaterTrackerRepository.toggleReminder(id, req.isEnabled)
            if (ok) {
                call.respond(ApiResponse<Unit>(success = true, message = "Reminder updated"))
            } else {
                call.respond(
                    HttpStatusCode.NotFound,
                    ApiResponse<Unit>(success = false, message = "Reminder not found")
                )
            }
        }

        // ── Delete reminder ───────────────────────────────────────────────────
        delete("/{id}") {
            val id = call.parameters["id"] ?: return@delete call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(success = false, message = "Missing id")
            )
            val ok = WaterTrackerRepository.deleteReminder(id)
            if (ok) {
                call.respond(ApiResponse<Unit>(success = true, message = "Reminder deleted"))
            } else {
                call.respond(
                    HttpStatusCode.NotFound,
                    ApiResponse<Unit>(success = false, message = "Reminder not found")
                )
            }
        }
    }
}
