package com.example.fuelify.routes

import com.example.fuelify.db.DatabaseFactory.dbQuery
import com.example.fuelify.routes.ApiResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// ─── Tables ───────────────────────────────────────────────────────────────────

private object DoctorConsultation : Table("doctor_consultation") {
    val caseId              = integer("case_id").autoIncrement()
    val userId              = integer("user_id")
    val conditionName       = varchar("condition_name", 255).nullable()
    val affectedArea        = varchar("affected_area", 255).nullable()
    val symptoms            = text("symptoms").nullable()
    val limitations         = text("limitations").nullable()
    val status              = varchar("status", 30).default("open")
    val specialtyNeeded     = varchar("specialty_needed", 50).default("general")  // ← NEW
    val adjustmentsApplied  = bool("adjustments_applied").default(false)
    val createdAt           = datetime("created_at").clientDefault { LocalDateTime.now() }
    val updatedAt           = datetime("updated_at").clientDefault { LocalDateTime.now() }
    override val primaryKey = PrimaryKey(caseId)
}

private object ConsultationMessage : Table("consultation_message") {
    val messageId   = integer("message_id").autoIncrement()
    val caseId      = integer("case_id")
    val senderType  = varchar("sender_type", 20)
    val senderName  = varchar("sender_name", 100).default("Medical Team")
    val content     = text("content")
    val fileNames   = text("file_names").default("")
    val sentAt      = datetime("sent_at").clientDefault { LocalDateTime.now() }
    override val primaryKey = PrimaryKey(messageId)
}

private object ConsultMedInfo : Table("user_medical_info") {
    val userId     = integer("user_id")
    val conditions = text("conditions").default("[]")
    val allergies  = text("allergies").default("[]")
    val medications = text("medications").default("[]")
    override val primaryKey = PrimaryKey(userId)
}

// ─── DTOs ─────────────────────────────────────────────────────────────────────

@Serializable
data class ConsultationMessageDto(
    val messageId:  Int,
    val senderType: String,
    val senderName: String,
    val content:    String,
    val fileNames:  List<String>,
    val sentAt:     String
)

@Serializable
data class ConsultationCaseDto(
    val caseId:             Int,
    val conditionName:      String,
    val affectedArea:       String,
    val symptoms:           String,
    val limitations:        String,
    val status:             String,
    val specialtyNeeded:    String,   // ← NEW
    val adjustmentsApplied: Boolean,
    val createdAt:          String,
    val messages:           List<ConsultationMessageDto>
)

// NEW: lightweight summary for case list
@Serializable
data class ConsultationCaseSummaryDto(
    val caseId:        Int,
    val conditionName: String,
    val affectedArea:  String,
    val status:        String,
    val createdAt:     String,
    val messageCount:  Int
)

@Serializable
data class CreateConsultationRequest(
    val conditionName: String,
    val affectedArea:  String,
    val symptoms:      String,
    val limitations:   String = "",
    val fileNames:     List<String> = emptyList()
)

@Serializable
data class SendMessageRequest(
    val content:   String,
    val fileNames: List<String> = emptyList()
)

@Serializable
data class UpdateCaseRequest(
    val status: String
)

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun parseJsonList(json: String): List<String> = try {
    json.trim().removePrefix("[").removeSuffix("]")
        .split(",").map { it.trim().removeSurrounding("\"") }.filter { it.isNotEmpty() }
} catch (e: Exception) { emptyList() }

private val fmt = DateTimeFormatter.ofPattern("dd MMM yyyy 'at' hh:mm a")

// ─── Specialty Auto-Detection ─────────────────────────────────────────────────
//
// Scans conditionName + affectedArea + symptoms for keyword matches.
// Whichever category scores higher wins. Ties fall back to "general".

private val dietKeywords = listOf(
    "weight", "diet", "nutrition", "eating", "food", "calorie", "caloric",
    "fat", "carbs", "carbohydrate", "protein", "meal", "hunger", "appetite",
    "bloating", "digestion", "stomach", "cholesterol", "sugar", "diabetes",
    "obesity", "overweight", "underweight", "vitamin", "mineral", "supplement",
    "gluten", "lactose", "allergy", "intolerance", "gut", "bowel", "ibs",
    "constipation", "diarrhea", "acid reflux", "heartburn", "nausea",
    "vomiting", "metabolism", "insulin", "blood sugar", "lose weight",
    "gain weight", "binge", "anorexia", "bulimia", "portion", "macro",
    "fiber", "hydration", "dehydration", "thyroid", "hormones", "fatty liver"
)

private val workoutKeywords = listOf(
    "workout", "exercise", "muscle", "pain", "injury", "knee", "back",
    "shoulder", "joint", "sprain", "strain", "fitness", "training",
    "running", "lifting", "gym", "cardio", "endurance", "stamina",
    "flexibility", "posture", "recovery", "soreness", "fatigue", "cramp",
    "tendon", "ligament", "physical therapy", "rehab", "sport", "shin",
    "hamstring", "quad", "glute", "calf", "elbow", "wrist", "ankle",
    "neck", "hip", "groin", "bruise", "swelling", "inflammation",
    "tear", "fracture", "stress fracture", "overtraining", "deadlift",
    "squat", "bench", "pull up", "push up", "crossfit", "hiit", "cycling",
    "swimming", "stretching", "mobility", "range of motion", "physiotherapy"
)

private fun detectSpecialty(conditionName: String, affectedArea: String, symptoms: String): String {
    // Combine all text the patient submitted into one searchable string
    val combined = "$conditionName $affectedArea $symptoms".lowercase()

    val dietScore    = dietKeywords.count    { combined.contains(it) }
    val workoutScore = workoutKeywords.count { combined.contains(it) }

    return when {
        dietScore > workoutScore  -> "diet"
        workoutScore > dietScore  -> "workout"
        else                      -> "general"   // tie or no matches → general doctor
    }
}

// ─── Auto-response generator ──────────────────────────────────────────────────

private suspend fun generateDoctorResponse(
    userId: Int,
    conditionName: String,
    symptoms: String
): String {
    val medInfo = dbQuery {
        ConsultMedInfo.select { ConsultMedInfo.userId eq userId }.firstOrNull()
    }
    val conditions  = if (medInfo != null) parseJsonList(medInfo[ConsultMedInfo.conditions])  else emptyList()
    val allergies   = if (medInfo != null) parseJsonList(medInfo[ConsultMedInfo.allergies])   else emptyList()
    val medications = if (medInfo != null) parseJsonList(medInfo[ConsultMedInfo.medications]) else emptyList()

    val sb = StringBuilder()
    sb.appendLine("Thank you for reaching out. Based on your description of \"$conditionName\" and the symptoms you've provided, here is our initial assessment:")
    sb.appendLine()

    val cond = conditionName.lowercase()
    val symp = symptoms.lowercase()
    when {
        cond.contains("back") || symp.contains("back") -> {
            sb.appendLine("• Avoid heavy compound lifts (deadlifts, squats, bent-over rows) until further assessed.")
            sb.appendLine("• Apply ice for the first 48 hours, then switch to heat.")
            sb.appendLine("• Gentle stretching and walking (flat surface) are encouraged.")
            sb.appendLine("• If pain radiates down the leg, this may indicate nerve involvement — seek in-person evaluation.")
        }
        cond.contains("knee") || symp.contains("knee") -> {
            sb.appendLine("• Rest and elevate the knee. Avoid high-impact activities.")
            sb.appendLine("• Use the RICE protocol: Rest, Ice, Compression, Elevation.")
            sb.appendLine("• Strengthen surrounding muscles (quads, hamstrings) with low-impact exercises.")
            sb.appendLine("• Ice 15–20 minutes every 2–3 hours for the first 2 days.")
        }
        cond.contains("shoulder") || symp.contains("shoulder") -> {
            sb.appendLine("• Avoid overhead pressing and heavy pulling movements.")
            sb.appendLine("• Rotator cuff exercises (band pull-aparts, external rotations) recommended.")
            sb.appendLine("• If pain is sharp with certain movements, consult a physiotherapist.")
        }
        cond.contains("fatigue") || symp.contains("tired") || symp.contains("fatigue") -> {
            sb.appendLine("• Ensure 7–9 hours of quality sleep per night.")
            sb.appendLine("• Check iron, B12, and Vitamin D levels if fatigue persists beyond 2 weeks.")
            sb.appendLine("• Reduce training intensity by 30% for the next week.")
            sb.appendLine("• Stay well hydrated — aim for at least 2.5L of water daily.")
        }
        cond.contains("wrist") || symp.contains("wrist") -> {
            sb.appendLine("• Avoid gripping or wrist-loading exercises (push-ups, bench press).")
            sb.appendLine("• Use a wrist brace during daily activities if helpful.")
            sb.appendLine("• Ice 10–15 minutes, 3 times daily for the first 48 hours.")
        }
        cond.contains("ankle") || symp.contains("ankle") -> {
            sb.appendLine("• RICE protocol: Rest, Ice, Compression, Elevation.")
            sb.appendLine("• Avoid weight-bearing activity for 24–48 hours minimum.")
            sb.appendLine("• Once swelling reduces, gentle range-of-motion exercises are encouraged.")
        }
        cond.contains("headache") || symp.contains("headache") || symp.contains("migraine") -> {
            sb.appendLine("• Rest in a quiet, dark room. Stay well hydrated.")
            sb.appendLine("• Avoid screens and bright lights during episodes.")
            sb.appendLine("• If headaches are frequent or severe, please consult a physician.")
        }
        else -> {
            sb.appendLine("• Rest the affected area and avoid aggravating activities.")
            sb.appendLine("• Stay well hydrated and maintain your nutrition plan.")
            sb.appendLine("• Monitor symptoms closely over the next 48–72 hours.")
            sb.appendLine("• Apply ice if there is swelling or inflammation.")
        }
    }

    sb.appendLine()

    if (conditions.isNotEmpty()) {
        sb.appendLine("⚠️ Considering your medical conditions (${conditions.joinToString(", ")}):")
        conditions.map { it.lowercase() }.forEach { c ->
            when {
                c.contains("diabetes")     -> sb.appendLine("• Monitor blood sugar closely during recovery — inactivity can affect glucose levels.")
                c.contains("hypertension") -> sb.appendLine("• Avoid holding your breath during any exercise. Keep blood pressure medication consistent.")
                c.contains("thyroid")      -> sb.appendLine("• Thyroid conditions can slow recovery. Extra rest is recommended.")
                c.contains("asthma")       -> sb.appendLine("• Keep your rescue inhaler accessible during any light activity.")
            }
        }
        sb.appendLine()
    }

    if (allergies.isNotEmpty()) {
        sb.appendLine("• Noted allergies: ${allergies.joinToString(", ")} — ensure any pain medication prescribed avoids these.")
    }

    if (medications.isNotEmpty()) {
        sb.appendLine("• Current medications noted: ${medications.joinToString(", ")} — inform any treating physician of these.")
    }

    sb.appendLine()
    sb.appendLine("Your diet and workout plans have been adjusted based on this consultation. Tap 'View Smart Adjustments' to review.")
    sb.appendLine()
    sb.appendLine("Please reply if symptoms worsen or you have additional questions. This is not a substitute for emergency care.")

    return sb.toString()
}

private fun generateFollowUpResponse(userMessage: String, conditionName: String): String {
    val msg = userMessage.lowercase()
    return when {
        msg.contains("worse") || msg.contains("pain increased") || msg.contains("getting worse") ->
            "Thank you for the update. If your pain has worsened significantly, please visit your nearest clinic or emergency room. In the meantime:\n\n• Rest completely — avoid ALL physical activity\n• Continue applying ice if there is swelling (15 min on, 15 min off)\n• Do not take more pain medication than the recommended dose\n\nWe have escalated this note in your case. Please seek in-person care if pain becomes severe."

        msg.contains("better") || msg.contains("improving") || msg.contains("feeling good") ->
            "That's great to hear! Here's your gradual return plan:\n\n• Days 1–3: Continue light activity only — 10 to 15 minute walks\n• Days 4–7: Add gentle stretching and low-impact movement\n• Week 2: Slowly reintroduce normal training at 50% intensity\n• Week 3+: Resume full activity if pain-free\n\nCome back if symptoms return at any stage."

        msg.contains("medication") || msg.contains("medicine") || msg.contains("drug") || msg.contains("pill") ->
            "Regarding medication for $conditionName:\n\n• Common OTC options: Ibuprofen (400mg with food) for inflammation, Paracetamol for pain\n• Do NOT combine ibuprofen and aspirin\n• If you are on prescription medication, consult your physician before adding anything new\n\nPlease do not self-medicate beyond recommended doses. If you need a prescription, visit your local clinic."

        msg.contains("exercise") || msg.contains("workout") || msg.contains("training") || msg.contains("gym") ->
            "For $conditionName, here is what is safe right now:\n\n✅ Allowed: Walking, light stretching, swimming (if available), breathing exercises\n❌ Avoid: Heavy resistance training, high-impact cardio, movements that cause pain\n\nYour workout plan has been adjusted. Focus on mobility and recovery — strength will return faster with proper rest."

        msg.contains("diet") || msg.contains("food") || msg.contains("eat") || msg.contains("nutrition") ->
            "For optimal recovery from $conditionName, focus on:\n\n• Anti-inflammatory foods: fatty fish (salmon, sardines), leafy greens, turmeric, ginger\n• Protein: lean meat, eggs, legumes — supports tissue repair\n• Hydration: minimum 2.5L water daily\n• Avoid: processed foods, excess sugar, alcohol — these increase inflammation\n\nYour meal plan has been updated to support your recovery."

        msg.contains("sleep") || msg.contains("rest") || msg.contains("tired") ->
            "Sleep is critical for recovery from $conditionName:\n\n• Aim for 8–9 hours of quality sleep during recovery\n• Avoid screens 1 hour before bed\n• A slightly elevated pillow position can help reduce pain for back/shoulder issues\n• Short naps (20 min) during the day are fine if needed\n\nYour body does most of its healing during sleep — protect it."

        msg.contains("how long") || msg.contains("when can i") || msg.contains("recovery time") ->
            "Recovery time for $conditionName varies by severity:\n\n• Mild strain: 1–2 weeks with rest and proper care\n• Moderate injury: 3–6 weeks with gradual return\n• Severe or recurring: 6–12 weeks, may require physiotherapy\n\nListen to your body — if pain returns when you increase activity, step back. Rushing recovery is the most common cause of re-injury."

        else ->
            "Thank you for your message regarding $conditionName. We have reviewed your update.\n\n• Continue following the adjusted recovery plan\n• Rest as needed and avoid aggravating the area\n• Stay hydrated and maintain your nutrition\n\nWe typically respond within 24 hours. Please reach out immediately if you experience significant pain, swelling, or any new symptoms."
    }
}

// ─── Routes ───────────────────────────────────────────────────────────────────

fun Route.doctorConsultationRoutes() {

    // ── POST /api/users/{id}/consultation ─────────────────────────────────────
    // Create a new consultation case (always allowed, even if one exists)
    post("/users/{id}/consultation") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user id", null))
        val body = call.receive<CreateConsultationRequest>()

        // Auto-detect which doctor specialty this case needs
        val specialty = detectSpecialty(body.conditionName, body.affectedArea, body.symptoms)

        val caseId = dbQuery {
            DoctorConsultation.insert {
                it[DoctorConsultation.userId]           = userId
                it[DoctorConsultation.conditionName]    = body.conditionName
                it[DoctorConsultation.affectedArea]     = body.affectedArea
                it[DoctorConsultation.symptoms]         = body.symptoms
                it[DoctorConsultation.limitations]      = body.limitations
                it[DoctorConsultation.status]           = "open"
                it[DoctorConsultation.specialtyNeeded]  = specialty   // ← set here
            }[DoctorConsultation.caseId]
        }

        // Save user's first message
        dbQuery {
            ConsultationMessage.insert {
                it[ConsultationMessage.caseId]     = caseId
                it[ConsultationMessage.senderType] = "user"
                it[ConsultationMessage.senderName] = "You"
                it[ConsultationMessage.content]    = "Condition: ${body.conditionName}\n\nSymptoms: ${body.symptoms}" +
                    if (body.limitations.isNotBlank()) "\n\nLimitations: ${body.limitations}" else ""
                it[ConsultationMessage.fileNames]  = body.fileNames.joinToString(",")
            }
        }

        // Auto-generate doctor response
        val doctorResponse = generateDoctorResponse(userId, body.conditionName, body.symptoms)

        dbQuery {
            ConsultationMessage.insert {
                it[ConsultationMessage.caseId]     = caseId
                it[ConsultationMessage.senderType] = "doctor"
                it[ConsultationMessage.senderName] = "Medical Team"
                it[ConsultationMessage.content]    = doctorResponse
            }
            DoctorConsultation.update({ DoctorConsultation.caseId eq caseId }) {
                it[DoctorConsultation.status] = "responded"
            }
        }

        call.respond(ApiResponse(
            success = true,
            message = "Case opened! Assigned to $specialty specialist.",
            data    = caseId
        ))
    }

    // ── GET /api/users/{id}/consultation ─────────────────────────────────────
    // Get ALL consultation cases as a list (summaries only)
    get("/users/{id}/consultation") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user id", null))

        val cases = dbQuery {
            DoctorConsultation.select { DoctorConsultation.userId eq userId }
                .orderBy(DoctorConsultation.caseId, SortOrder.DESC)
                .map { row ->
                    val caseId = row[DoctorConsultation.caseId]
                    val msgCount = ConsultationMessage.select { ConsultationMessage.caseId eq caseId }.count().toInt()
                    ConsultationCaseSummaryDto(
                        caseId        = caseId,
                        conditionName = row.getOrNull(DoctorConsultation.conditionName) ?: "",
                        affectedArea  = row.getOrNull(DoctorConsultation.affectedArea)  ?: "",
                        status        = row[DoctorConsultation.status],
                        createdAt     = row[DoctorConsultation.createdAt].format(fmt),
                        messageCount  = msgCount
                    )
                }
        }

        call.respond(ApiResponse(success = true, message = "OK", data = cases))
    }

    // ── GET /api/users/{id}/consultation/{caseId} ────────────────────────────
    // Get a single case with all messages (for chat view)
    get("/users/{id}/consultation/{caseId}") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user id", null))
        val caseId = call.parameters["caseId"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid case id", null))

        val caseRow = dbQuery {
            DoctorConsultation.select {
                (DoctorConsultation.caseId eq caseId) and (DoctorConsultation.userId eq userId)
            }.firstOrNull()
        } ?: return@get call.respond(HttpStatusCode.NotFound,
            ApiResponse<Nothing>(false, "Case not found", null))

        val messages = dbQuery {
            ConsultationMessage.select { ConsultationMessage.caseId eq caseId }
                .orderBy(ConsultationMessage.sentAt, SortOrder.ASC)
                .map { row ->
                    ConsultationMessageDto(
                        messageId  = row[ConsultationMessage.messageId],
                        senderType = row[ConsultationMessage.senderType],
                        senderName = row[ConsultationMessage.senderName],
                        content    = row[ConsultationMessage.content],
                        fileNames  = row[ConsultationMessage.fileNames]
                            .split(",").map { it.trim() }.filter { it.isNotEmpty() },
                        sentAt     = row[ConsultationMessage.sentAt].format(fmt)
                    )
                }
        }

        val dto = ConsultationCaseDto(
            caseId             = caseId,
            conditionName      = caseRow.getOrNull(DoctorConsultation.conditionName) ?: "",
            affectedArea       = caseRow.getOrNull(DoctorConsultation.affectedArea)  ?: "",
            symptoms           = caseRow.getOrNull(DoctorConsultation.symptoms)      ?: "",
            limitations        = caseRow.getOrNull(DoctorConsultation.limitations)   ?: "",
            status             = caseRow[DoctorConsultation.status],
            specialtyNeeded    = caseRow[DoctorConsultation.specialtyNeeded],          // ← NEW
            adjustmentsApplied = caseRow[DoctorConsultation.adjustmentsApplied],
            createdAt          = caseRow[DoctorConsultation.createdAt].format(fmt),
            messages           = messages
        )
        call.respond(ApiResponse(success = true, message = "OK", data = dto))
    }

    // ── POST /api/users/{id}/consultation/{caseId}/message ───────────────────
    post("/users/{id}/consultation/{caseId}/message") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user id", null))
        val caseId = call.parameters["caseId"]?.toIntOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid case id", null))
        val body = call.receive<SendMessageRequest>()

        dbQuery {
            ConsultationMessage.insert {
                it[ConsultationMessage.caseId]     = caseId
                it[ConsultationMessage.senderType] = "user"
                it[ConsultationMessage.senderName] = "You"
                it[ConsultationMessage.content]    = body.content
                it[ConsultationMessage.fileNames]  = body.fileNames.joinToString(",")
            }
        }

        val caseRow = dbQuery {
            DoctorConsultation.select { DoctorConsultation.caseId eq caseId }.firstOrNull()
        }
        val conditionName = caseRow?.getOrNull(DoctorConsultation.conditionName) ?: ""

        val followUp = generateFollowUpResponse(body.content, conditionName)

        dbQuery {
            ConsultationMessage.insert {
                it[ConsultationMessage.caseId]     = caseId
                it[ConsultationMessage.senderType] = "doctor"
                it[ConsultationMessage.senderName] = "Medical Team"
                it[ConsultationMessage.content]    = followUp
            }
            DoctorConsultation.update({ DoctorConsultation.caseId eq caseId }) {
                it[DoctorConsultation.status]    = "responded"
                it[DoctorConsultation.updatedAt] = LocalDateTime.now()
            }
        }

        call.respond(ApiResponse(success = true, message = "Message sent", data = null))
    }

    // ── PATCH /api/users/{id}/consultation/{caseId} ───────────────────────────
    patch("/users/{id}/consultation/{caseId}") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@patch call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user id", null))
        val caseId = call.parameters["caseId"]?.toIntOrNull()
            ?: return@patch call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid case id", null))
        val body = call.receive<UpdateCaseRequest>()

        dbQuery {
            DoctorConsultation.update({
                (DoctorConsultation.caseId eq caseId) and
                (DoctorConsultation.userId eq userId)
            }) {
                it[DoctorConsultation.status]    = body.status
                it[DoctorConsultation.updatedAt] = LocalDateTime.now()
                if (body.status == "acknowledged") {
                    it[DoctorConsultation.adjustmentsApplied] = true
                }
            }
        }
        call.respond(ApiResponse(success = true, message = "Case updated to ${body.status}", data = null))
    }
}
