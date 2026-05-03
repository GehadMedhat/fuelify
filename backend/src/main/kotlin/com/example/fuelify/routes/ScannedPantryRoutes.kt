package com.example.fuelify.routes

import com.example.fuelify.db.DatabaseFactory.dbQuery
import com.example.fuelify.models.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.date
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// ─── Table ────────────────────────────────────────────────────────────────────

private object ScannedPantry : Table("scanned_pantry_item") {
    val itemId      = integer("item_id").autoIncrement()
    val userId      = integer("user_id")
    val productName = varchar("product_name", 255)
    val barcode     = varchar("barcode", 100).nullable()
    val quantity    = double("quantity").default(100.0)
    val unit        = varchar("unit", 20).default("g")
    val expiryDate  = date("expiry_date")
    val calories    = double("calories").default(0.0)
    val protein     = double("protein").default(0.0)
    val carbs       = double("carbs").default(0.0)
    val fat         = double("fat").default(0.0)
    val nutriScore  = varchar("nutri_score", 5).nullable()
    override val primaryKey = PrimaryKey(itemId)
}

// ─── DTOs ─────────────────────────────────────────────────────────────────────

@Serializable
data class ScannedPantryItemDto(
    val itemId: Int,
    val productName: String,
    val barcode: String,
    val quantity: Double,
    val unit: String,
    val expiryDate: String,
    val daysUntilExpiry: Int,
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val nutriScore: String
)

@Serializable
data class AddScannedPantryRequest(
    val productName: String,
    val barcode: String,
    val quantity: Double,
    val unit: String,
    val expiryDate: String,
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val nutriScore: String
)

// ─── Routes ───────────────────────────────────────────────────────────────────

fun Route.scannedPantryRoutes() {

    // ── GET /api/users/{id}/scanned-pantry ────────────────────────────────────
    get("/users/{id}/scanned-pantry") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user id", null))

        val today = LocalDate.now()
        val items = dbQuery {
            ScannedPantry
                .select { ScannedPantry.userId eq userId }
                .orderBy(ScannedPantry.expiryDate to SortOrder.ASC)
                .map { row ->
                    val expiry = row[ScannedPantry.expiryDate]
                    val days = ChronoUnit.DAYS.between(today, expiry).toInt()
                    ScannedPantryItemDto(
                        itemId          = row[ScannedPantry.itemId],
                        productName     = row[ScannedPantry.productName],
                        barcode         = row.getOrNull(ScannedPantry.barcode) ?: "",
                        quantity        = row[ScannedPantry.quantity],
                        unit            = row[ScannedPantry.unit],
                        expiryDate      = expiry.toString(),
                        daysUntilExpiry = days,
                        calories        = row[ScannedPantry.calories],
                        protein         = row[ScannedPantry.protein],
                        carbs           = row[ScannedPantry.carbs],
                        fat             = row[ScannedPantry.fat],
                        nutriScore      = row.getOrNull(ScannedPantry.nutriScore) ?: "?"
                    )
                }
        }
        call.respond(ApiResponse(success = true, message = "OK", data = items))
    }

    // ── POST /api/users/{id}/scanned-pantry ───────────────────────────────────
    post("/users/{id}/scanned-pantry") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user id", null))
        val body = call.receive<AddScannedPantryRequest>()

        dbQuery {
            ScannedPantry.insert {
                it[ScannedPantry.userId]      = userId
                it[ScannedPantry.productName] = body.productName
                it[ScannedPantry.barcode]     = body.barcode
                it[ScannedPantry.quantity]    = body.quantity
                it[ScannedPantry.unit]        = body.unit
                it[ScannedPantry.expiryDate]  = LocalDate.parse(body.expiryDate)
                it[ScannedPantry.calories]    = body.calories
                it[ScannedPantry.protein]     = body.protein
                it[ScannedPantry.carbs]       = body.carbs
                it[ScannedPantry.fat]         = body.fat
                it[ScannedPantry.nutriScore]  = body.nutriScore
            }
        }
        call.respond(ApiResponse(success = true, message = "Added to pantry", data = null))
    }

    // ── DELETE /api/users/{id}/scanned-pantry/{itemId} ────────────────────────
    delete("/users/{id}/scanned-pantry/{itemId}") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@delete call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user id", null))
        val itemId = call.parameters["itemId"]?.toIntOrNull()
            ?: return@delete call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid item id", null))

        dbQuery {
            ScannedPantry.deleteWhere {
                (ScannedPantry.itemId eq itemId) and (ScannedPantry.userId eq userId)
            }
        }
        call.respond(ApiResponse(success = true, message = "Deleted", data = null))
    }
}
