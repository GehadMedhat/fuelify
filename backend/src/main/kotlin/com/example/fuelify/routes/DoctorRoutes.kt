package com.example.fuelify.routes

import com.example.fuelify.db.DatabaseFactory.dbQuery
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.datetime
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
// ─── Tables ───────────────────────────────────────────────────────────────────

private object DoctorProfile : Table("doctor_profile") {
    val doctorId      = integer("doctor_id").autoIncrement()
    val email         = varchar("email", 255).uniqueIndex()
    val passwordHash  = varchar("password_hash", 255)
    val fullName      = varchar("full_name", 255)
    val specialty     = varchar("specialty", 50)
    val qualification = varchar("qualification", 255).nullable()
    val hospital      = varchar("hospital", 255).nullable()
    val yearsExp      = integer("years_exp").default(0)
    val bio           = text("bio").nullable()
    val documentName  = varchar("document_name", 500).nullable()
    val isApproved    = bool("is_approved").default(false)
    val isActive      = bool("is_active").default(true)
    val pricePerCase  = integer("price_per_case").default(25)
    val createdAt     = datetime("created_at").clientDefault { LocalDateTime.now() }
    override val primaryKey = PrimaryKey(doctorId)
}

private object DoctorWallet : Table("doctor_wallet") {
    val walletId  = integer("wallet_id").autoIncrement()
    val doctorId  = integer("doctor_id").uniqueIndex()
    val balance   = decimal("balance", 10, 2).default(BigDecimal.ZERO)
    val updatedAt = datetime("updated_at").clientDefault { LocalDateTime.now() }
    override val primaryKey = PrimaryKey(walletId)
}

private object DoctorWalletTransaction : Table("doctor_wallet_transaction") {
    val txId        = integer("tx_id").autoIncrement()
    val doctorId    = integer("doctor_id")
    val caseId      = integer("case_id").nullable()
    val patientName = varchar("patient_name", 255).nullable()
    val amount      = decimal("amount", 10, 2)
    val type        = varchar("type", 20).default("credit")
    val description = text("description").nullable()
    val createdAt   = datetime("created_at").clientDefault { LocalDateTime.now() }
    override val primaryKey = PrimaryKey(txId)
}

// Reuse consultation tables
private object DoctorCase : Table("doctor_consultation") {
    val caseId           = integer("case_id").autoIncrement()
    val userId           = integer("user_id")
    val conditionName    = varchar("condition_name", 255).nullable()
    val affectedArea     = varchar("affected_area", 255).nullable()
    val symptoms         = text("symptoms").nullable()
    val limitations      = text("limitations").nullable()
    val status           = varchar("status", 30).default("open")
    val doctorId         = integer("doctor_id").nullable()
    val doctorResponded  = bool("doctor_responded").default(false)
    val responsePending  = bool("response_pending").default(true)
    val specialtyNeeded  = varchar("specialty_needed", 50).default("general")
    val createdAt        = datetime("created_at").clientDefault { LocalDateTime.now() }
    override val primaryKey = PrimaryKey(caseId)
}

private object DoctorCaseMessage : Table("consultation_message") {
    val messageId   = integer("message_id").autoIncrement()
    val caseId      = integer("case_id")
    val senderType  = varchar("sender_type", 20)
    val senderName  = varchar("sender_name", 100).default("Medical Team")
    val content     = text("content")
    val fileNames   = text("file_names").default("")
    val sentAt      = datetime("sent_at").clientDefault { LocalDateTime.now() }
    override val primaryKey = PrimaryKey(messageId)
}

// User table for patient names
private object DoctorPatientUsers : Table("users") {
    val id        = integer("id")
    val name      = varchar("name", 100).default("")
    val firstName = varchar("first_name", 100).nullable()
    val lastName  = varchar("last_name", 100).nullable()
    override val primaryKey = PrimaryKey(id)
}

// ─── DTOs ─────────────────────────────────────────────────────────────────────

@Serializable
data class DoctorOnboardRequest(
    val email:         String,
    val password:      String,          // plain — hash on server
    val fullName:      String,
    val specialty:     String,          // "diet" | "workout" | "general"
    val qualification: String = "",
    val hospital:      String = "",
    val yearsExp:      Int    = 0,
    val bio:           String = "",
    val documentName:  String = ""      // file name only (user uploads separately)
)

@Serializable
data class DoctorLoginRequest(
    val email:    String,
    val password: String
)

@Serializable
data class DoctorProfileDto(
    val doctorId:     Int,
    val fullName:     String,
    val specialty:    String,
    val qualification: String,
    val hospital:     String,
    val yearsExp:     Int,
    val bio:          String,
    val isApproved:   Boolean,
    val pricePerCase: Int
)

@Serializable
data class InboxCaseDto(
    val caseId:         Int,
    val patientName:    String,
    val conditionName:  String,
    val symptoms:       String,
    val specialtyNeeded: String,
    val status:         String,
    val doctorResponded: Boolean,
    val createdAt:      String,
    val messageCount:   Int
)

@Serializable
data class CaseDetailDto(
    val caseId:       Int,
    val patientName:  String,
    val conditionName: String,
    val affectedArea: String,
    val symptoms:     String,
    val limitations:  String,
    val status:       String,
    val messages:     List<DoctorMessageDto>
)

@Serializable
data class DoctorMessageDto(
    val messageId:  Int,
    val senderType: String,
    val senderName: String,
    val content:    String,
    val fileNames:  List<String>,
    val sentAt:     String
)

@Serializable
data class DoctorRespondRequest(
    val content: String
)

@Serializable
data class WalletTransactionDto(
    val txId:        Int,
    val caseId:      Int?,
    val patientName: String,
    val amount:      Double,
    val type:        String,
    val description: String,
    val createdAt:   String
)

@Serializable
data class WalletDto(
    val balance:      Double,
    val transactions: List<WalletTransactionDto>
)

// ─── Simple hash (replace with bcrypt when your friend adds auth) ─────────────
private fun hashPassword(password: String): String {
    val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}

private val fmt = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")

// ─── Email helper ─────────────────────────────────────────────────────────────

private fun sendApprovalEmail(toEmail: String, doctorName: String) {
    try {
        val props = java.util.Properties().apply {
            put("mail.smtp.host", "smtp.gmail.com")
            put("mail.smtp.port", "587")
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
        }
        val FROM     = "gehad.medhat1900@gmail.com"
        val PASSWORD = "znus edqr rkah mncn"  // ← 16-char App Password from Google
        val session  = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() =
                PasswordAuthentication(FROM, PASSWORD)
        })
        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(FROM, "Fuelify Team"))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail))
            subject = "✅ Your Fuelify Doctor Account is Approved!"
            setText("""
                Hello Dr. $doctorName,
                Your Fuelify doctor account has been approved.
                You can now log in and start receiving patient cases.
                — The Fuelify Team
            """.trimIndent())
        }
        Transport.send(message)
    } catch (e: Exception) {
        println("Email send failed: ${e.message}")
        e.printStackTrace()
    }
}

// ─── Routes ───────────────────────────────────────────────────────────────────

fun Route.doctorRoutes() {

    // ── POST /api/doctor/register ─────────────────────────────────────────────
    post("/doctor/register") {
        val body = call.receive<DoctorOnboardRequest>()

        // Check duplicate email
        val existing = dbQuery {
            DoctorProfile.select { DoctorProfile.email eq body.email }.firstOrNull()
        }
        if (existing != null) {
            return@post call.respond(HttpStatusCode.Conflict,
                ApiResponse<Nothing>(false, "Email already registered", null))
        }

        val doctorId = dbQuery {
            DoctorProfile.insert {
                it[DoctorProfile.email]         = body.email
                it[DoctorProfile.passwordHash]  = hashPassword(body.password)
                it[DoctorProfile.fullName]      = body.fullName
                it[DoctorProfile.specialty]     = body.specialty.lowercase()
                it[DoctorProfile.qualification] = body.qualification.ifBlank { null }
                it[DoctorProfile.hospital]      = body.hospital.ifBlank { null }
                it[DoctorProfile.yearsExp]      = body.yearsExp
                it[DoctorProfile.bio]           = body.bio.ifBlank { null }
                it[DoctorProfile.documentName]  = body.documentName.ifBlank { null }
                it[DoctorProfile.isApproved]    = false   // admin reviews
            }[DoctorProfile.doctorId]
        }

        // Create wallet
        dbQuery {
            DoctorWallet.insert {
                it[DoctorWallet.doctorId] = doctorId
                it[DoctorWallet.balance]  = BigDecimal.ZERO
            }
        }

        call.respond(HttpStatusCode.Created, ApiResponse(
            success = true,
            message = "Registration received! We will review your profile within 24–48 hours. You'll be notified by email once approved.",
            data = mapOf("doctorId" to doctorId, "isApproved" to false)
        ))
    }

    // ── POST /api/doctor/login ────────────────────────────────────────────────
    post("/doctor/login") {
        val body = call.receive<DoctorLoginRequest>()

        val doctor = dbQuery {
            DoctorProfile.select { DoctorProfile.email eq body.email }.firstOrNull()
        } ?: return@post call.respond(HttpStatusCode.NotFound,
            ApiResponse<Nothing>(false, "No account found with this email", null))

        if (doctor[DoctorProfile.passwordHash] != hashPassword(body.password)) {
            return@post call.respond(HttpStatusCode.Unauthorized,
                ApiResponse<Nothing>(false, "Incorrect password", null))
        }

        val dto = DoctorProfileDto(
            doctorId      = doctor[DoctorProfile.doctorId],
            fullName      = doctor[DoctorProfile.fullName],
            specialty     = doctor[DoctorProfile.specialty],
            qualification = doctor.getOrNull(DoctorProfile.qualification) ?: "",
            hospital      = doctor.getOrNull(DoctorProfile.hospital) ?: "",
            yearsExp      = doctor[DoctorProfile.yearsExp],
            bio           = doctor.getOrNull(DoctorProfile.bio) ?: "",
            isApproved    = doctor[DoctorProfile.isApproved],
            pricePerCase  = doctor[DoctorProfile.pricePerCase]
        )
        call.respond(ApiResponse(success = true, message = "Login successful", data = dto))
    }

// ── POST /api/doctor/{id}/approve  (call this from your admin panel) ──────────
post("/doctor/{id}/approve") {
    val doctorId = call.parameters["id"]?.toIntOrNull()
        ?: return@post call.respond(HttpStatusCode.BadRequest,
            ApiResponse<Nothing>(false, "Invalid doctor id", null))

    val doctor = dbQuery {
        DoctorProfile.select { DoctorProfile.doctorId eq doctorId }.firstOrNull()
    } ?: return@post call.respond(HttpStatusCode.NotFound,
        ApiResponse<Nothing>(false, "Doctor not found", null))

    dbQuery {
        DoctorProfile.update({ DoctorProfile.doctorId eq doctorId }) {
            it[DoctorProfile.isApproved] = true
        }
    }

    // Send approval email on a background thread so it doesn't block the response
kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
    sendApprovalEmail(
        toEmail    = doctor[DoctorProfile.email],
        doctorName = doctor[DoctorProfile.fullName]
    )
}

    call.respond(ApiResponse(success = true, message = "Doctor approved and notified by email", data = null))
}

    // ── GET /api/doctor/{id}/profile ──────────────────────────────────────────
    get("/doctor/{id}/profile") {
        val doctorId = call.parameters["id"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid doctor id", null))

        val row = dbQuery {
            DoctorProfile.select { DoctorProfile.doctorId eq doctorId }.firstOrNull()
        } ?: return@get call.respond(HttpStatusCode.NotFound,
            ApiResponse<Nothing>(false, "Doctor not found", null))

        call.respond(ApiResponse(success = true, message = "OK", data = DoctorProfileDto(
            doctorId      = row[DoctorProfile.doctorId],
            fullName      = row[DoctorProfile.fullName],
            specialty     = row[DoctorProfile.specialty],
            qualification = row.getOrNull(DoctorProfile.qualification) ?: "",
            hospital      = row.getOrNull(DoctorProfile.hospital) ?: "",
            yearsExp      = row[DoctorProfile.yearsExp],
            bio           = row.getOrNull(DoctorProfile.bio) ?: "",
            isApproved    = row[DoctorProfile.isApproved],
            pricePerCase  = row[DoctorProfile.pricePerCase]
        )))
    }

    // ── GET /api/doctor/{id}/inbox ────────────────────────────────────────────
    // Returns all open cases matching doctor's specialty + unanswered ones
    get("/doctor/{id}/inbox") {
        val doctorId = call.parameters["id"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid doctor id", null))

        val doctor = dbQuery {
            DoctorProfile.select { DoctorProfile.doctorId eq doctorId }.firstOrNull()
        } ?: return@get call.respond(HttpStatusCode.NotFound,
            ApiResponse<Nothing>(false, "Doctor not found", null))

        if (!doctor[DoctorProfile.isApproved]) {
            return@get call.respond(ApiResponse(
                success = false,
                message = "Your account is pending approval. We'll notify you by email once approved.",
                data = null as List<InboxCaseDto>?
            ))
        }

        val specialty = doctor[DoctorProfile.specialty]

        // Match cases: general doctors see all, diet → diet cases, workout → workout cases
        val cases = dbQuery {
            val query = when (specialty) {
                "diet"    -> DoctorCase.select {
                    (DoctorCase.status neq "closed") and
                    (DoctorCase.specialtyNeeded inList listOf("diet","general","")) }
                "workout" -> DoctorCase.select {
                    (DoctorCase.status neq "closed") and
                    (DoctorCase.specialtyNeeded inList listOf("workout","general","")) }
                else      -> DoctorCase.select { DoctorCase.status neq "closed" }
            }

            query.orderBy(DoctorCase.createdAt, SortOrder.DESC).map { caseRow ->
                val caseId = caseRow[DoctorCase.caseId]
                val userId = caseRow[DoctorCase.userId]

                // Get patient name
                val patient = DoctorPatientUsers.select { DoctorPatientUsers.id eq userId }.firstOrNull()
                val patientName = patient?.get(DoctorPatientUsers.name)?.ifBlank { null }
                    ?: listOfNotNull(
                        patient?.getOrNull(DoctorPatientUsers.firstName),
                        patient?.getOrNull(DoctorPatientUsers.lastName)
                    ).joinToString(" ").ifBlank { "Patient #$userId" }

                val msgCount = DoctorCaseMessage.select { DoctorCaseMessage.caseId eq caseId }.count().toInt()

                InboxCaseDto(
                    caseId          = caseId,
                    patientName     = patientName,
                    conditionName   = caseRow.getOrNull(DoctorCase.conditionName) ?: "",
                    symptoms        = (caseRow.getOrNull(DoctorCase.symptoms) ?: "").take(120),
                    specialtyNeeded = caseRow.getOrNull(DoctorCase.specialtyNeeded) ?: "general",
                    status          = caseRow[DoctorCase.status],
                    doctorResponded = caseRow.getOrNull(DoctorCase.doctorResponded) ?: false,
                    createdAt       = caseRow[DoctorCase.createdAt].format(fmt),
                    messageCount    = msgCount
                )
            }
        }
        call.respond(ApiResponse(success = true, message = "OK", data = cases))
    }

    // ── GET /api/doctor/{id}/cases/{caseId} ───────────────────────────────────
    get("/doctor/{id}/cases/{caseId}") {
        val doctorId = call.parameters["id"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid doctor id", null))
        val caseId = call.parameters["caseId"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid case id", null))

        val caseRow = dbQuery {
            DoctorCase.select { DoctorCase.caseId eq caseId }.firstOrNull()
        } ?: return@get call.respond(HttpStatusCode.NotFound,
            ApiResponse<Nothing>(false, "Case not found", null))

        val userId = caseRow[DoctorCase.userId]
        val patient = dbQuery {
            DoctorPatientUsers.select { DoctorPatientUsers.id eq userId }.firstOrNull()
        }
        val patientName = patient?.get(DoctorPatientUsers.name)?.ifBlank { null }
            ?: listOfNotNull(
                patient?.getOrNull(DoctorPatientUsers.firstName),
                patient?.getOrNull(DoctorPatientUsers.lastName)
            ).joinToString(" ").ifBlank { "Patient #$userId" }

        val messages = dbQuery {
            DoctorCaseMessage.select { DoctorCaseMessage.caseId eq caseId }
                .orderBy(DoctorCaseMessage.sentAt, SortOrder.ASC)
                .map { row ->
                    DoctorMessageDto(
                        messageId  = row[DoctorCaseMessage.messageId],
                        senderType = row[DoctorCaseMessage.senderType],
                        senderName = row[DoctorCaseMessage.senderName],
                        content    = row[DoctorCaseMessage.content],
                        fileNames  = row[DoctorCaseMessage.fileNames]
                            .split(",").map { it.trim() }.filter { it.isNotEmpty() },
                        sentAt     = row[DoctorCaseMessage.sentAt].format(fmt)
                    )
                }
        }

        call.respond(ApiResponse(success = true, message = "OK", data = CaseDetailDto(
            caseId        = caseId,
            patientName   = patientName,
            conditionName = caseRow.getOrNull(DoctorCase.conditionName) ?: "",
            affectedArea  = caseRow.getOrNull(DoctorCase.affectedArea)  ?: "",
            symptoms      = caseRow.getOrNull(DoctorCase.symptoms)      ?: "",
            limitations   = caseRow.getOrNull(DoctorCase.limitations)   ?: "",
            status        = caseRow[DoctorCase.status],
            messages      = messages
        )))
    }

    // ── POST /api/doctor/{id}/cases/{caseId}/respond ──────────────────────────
    // Doctor sends response → credits wallet → marks case as doctor_responded
    post("/doctor/{id}/cases/{caseId}/respond") {
        val doctorId = call.parameters["id"]?.toIntOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid doctor id", null))
        val caseId = call.parameters["caseId"]?.toIntOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid case id", null))
        val body = call.receive<DoctorRespondRequest>()

        if (body.content.isBlank()) {
            return@post call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Response cannot be empty", null))
        }

        // Get doctor name + price
        val doctor = dbQuery {
            DoctorProfile.select { DoctorProfile.doctorId eq doctorId }.firstOrNull()
        } ?: return@post call.respond(HttpStatusCode.NotFound,
            ApiResponse<Nothing>(false, "Doctor not found", null))

        val doctorName    = "Dr. ${doctor[DoctorProfile.fullName]}"
        val pricePerCase  = doctor[DoctorProfile.pricePerCase]

        // Get case + patient name
        val caseRow = dbQuery {
            DoctorCase.select { DoctorCase.caseId eq caseId }.firstOrNull()
        } ?: return@post call.respond(HttpStatusCode.NotFound,
            ApiResponse<Nothing>(false, "Case not found", null))

        val userId = caseRow[DoctorCase.userId]
        val patient = dbQuery {
            DoctorPatientUsers.select { DoctorPatientUsers.id eq userId }.firstOrNull()
        }
        val patientName = patient?.get(DoctorPatientUsers.name)?.ifBlank { null }
            ?: "Patient #$userId"

        // Check if doctor already responded before (only credit wallet once per case)
        val alreadyResponded = dbQuery {
            caseRow.getOrNull(DoctorCase.doctorResponded) == true
        }

        dbQuery {
            // 1. Save doctor's message (always allowed)
            DoctorCaseMessage.insert {
                it[DoctorCaseMessage.caseId]     = caseId
                it[DoctorCaseMessage.senderType] = "doctor"
                it[DoctorCaseMessage.senderName] = doctorName
                it[DoctorCaseMessage.content]    = body.content
                it[DoctorCaseMessage.fileNames]  = ""
            }

            // 2. Mark case as responded (only update flags on first response)
            if (!alreadyResponded) {
                DoctorCase.update({ DoctorCase.caseId eq caseId }) {
                    it[DoctorCase.status]          = "responded"
                    it[DoctorCase.doctorId]        = doctorId
                    it[DoctorCase.doctorResponded] = true
                    it[DoctorCase.responsePending] = false
                }

                // 3. Credit wallet only on first response
                val wallet = DoctorWallet.select { DoctorWallet.doctorId eq doctorId }.firstOrNull()
                if (wallet != null) {
                    val newBalance = wallet[DoctorWallet.balance] + BigDecimal(pricePerCase)
                    DoctorWallet.update({ DoctorWallet.doctorId eq doctorId }) {
                        it[DoctorWallet.balance]   = newBalance
                        it[DoctorWallet.updatedAt] = LocalDateTime.now()
                    }
                }

                // 4. Log transaction (only on first response)
                DoctorWalletTransaction.insert {
                    it[DoctorWalletTransaction.doctorId]    = doctorId
                    it[DoctorWalletTransaction.caseId]      = caseId
                    it[DoctorWalletTransaction.patientName] = patientName
                    it[DoctorWalletTransaction.amount]      = BigDecimal(pricePerCase)
                    it[DoctorWalletTransaction.type]        = "credit"
                    it[DoctorWalletTransaction.description] = "Response to $patientName — Case #$caseId"
                }
            }
        }

        val msg = if (alreadyResponded) "Follow-up sent!" else "Response sent! +$pricePerCase EGP added to your wallet."
        call.respond(ApiResponse(success = true, message = msg, data = null))
    }

    // ── GET /api/doctor/{id}/wallet ───────────────────────────────────────────
    get("/doctor/{id}/wallet") {
        val doctorId = call.parameters["id"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid doctor id", null))

        val wallet = dbQuery {
            DoctorWallet.select { DoctorWallet.doctorId eq doctorId }.firstOrNull()
        } ?: return@get call.respond(HttpStatusCode.NotFound,
            ApiResponse<Nothing>(false, "Wallet not found", null))

        val transactions = dbQuery {
            DoctorWalletTransaction
                .select { DoctorWalletTransaction.doctorId eq doctorId }
                .orderBy(DoctorWalletTransaction.createdAt, SortOrder.DESC)
                .limit(50)
                .map { row ->
                    WalletTransactionDto(
                        txId        = row[DoctorWalletTransaction.txId],
                        caseId      = row.getOrNull(DoctorWalletTransaction.caseId),
                        patientName = row.getOrNull(DoctorWalletTransaction.patientName) ?: "",
                        amount      = row[DoctorWalletTransaction.amount].toDouble(),
                        type        = row[DoctorWalletTransaction.type],
                        description = row.getOrNull(DoctorWalletTransaction.description) ?: "",
                        createdAt   = row[DoctorWalletTransaction.createdAt].format(fmt)
                    )
                }
        }

        call.respond(ApiResponse(success = true, message = "OK", data = WalletDto(
            balance      = wallet[DoctorWallet.balance].toDouble(),
            transactions = transactions
        )))
    }
}
